package com.scaleplex.communication

import com.scaleplex.core.model.CommConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Blocking TCP client with connect timeout, read timeout, auto-reconnect and
 * length-aware frame reads. Reused by Dahua / YouSheng / Toledo (TCP) drivers.
 *
 * The scale protocols are request/response; [request] serializes access so a single
 * connection can be shared safely by one driver instance.
 */
class TcpClient(
    private val host: String,
    private val port: Int,
    config: CommConfig,
    bus: CommEventBus,
) : BaseTransport(config, bus) {

    @Volatile
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val lock = Any()

    override val remote: String get() = "$host:$port"

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    override suspend fun connect() = io {
        synchronized(lock) {
            if (isConnected) return@io
            bus.emitState(remote, false)
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), config.connectTimeoutMs)
            s.soTimeout = config.readTimeoutMs
            socket = s
            input = DataInputStream(s.getInputStream())
            output = DataOutputStream(s.getOutputStream())
            bus.emitState(remote, true)
            log.info("TCP connected to $remote")
        }
    }

    override suspend fun request(payload: ByteArray): ByteArray? =
        withRetry {
            withContext(Dispatchers.IO) {
                synchronized(lock) {
                    val s = socket ?: return@synchronized null
                    val out = output ?: return@synchronized null
                    val inp = input ?: return@synchronized null
                    try {
                        bus.emitRaw(RawPacket(PacketDirection.TX, remote, payload))
                        out.write(payload)
                        out.flush()
                        // DIGI-style protocols reply with a terminator-framed block;
                        // read what's available until the peer closes or times out.
                        val buf = ByteArray(8192)
                        var total = 0
                        var read: Int
                        while (inp.read(buf, total, buf.size - total).also { read = it } > 0) {
                            total += read
                            if (total >= buf.size) break
                        }
                        if (total > 0) {
                            val resp = buf.copyOf(total)
                            bus.emitRaw(RawPacket(PacketDirection.RX, remote, resp))
                            resp
                        } else null
                    } catch (t: Throwable) {
                        reconnectQuietly()
                        throw t
                    }
                }
            }
        }

    override suspend fun send(payload: ByteArray) = io {
        synchronized(lock) {
            val out = output ?: return@io
            bus.emitRaw(RawPacket(PacketDirection.TX, remote, payload))
            out.write(payload)
            out.flush()
        }
    }

    private fun reconnectQuietly() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        bus.emitState(remote, false)
    }

    override suspend fun disconnect() = io {
        synchronized(lock) {
            runCatching { socket?.close() }
            socket = null
            input = null
            output = null
            bus.emitState(remote, false)
        }
    }

    override fun close() {
        runCatching { socket?.close() }
    }
}
