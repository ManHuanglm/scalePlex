package com.scaleplex.communication

import com.scaleplex.core.model.CommConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Connectionless UDP client implementing request/await-reply with timeout and retry,
 * matching DIGI / HuiHong / CAS scale protocols. A fresh socket is created per request
 * so the receive buffer never mixes replies from different calls.
 */
class UdpClient(
    private val host: String,
    private val port: Int,
    config: CommConfig,
    bus: CommEventBus,
) : BaseTransport(config, bus) {

    override val remote: String get() = "udp://$host:$port"
    override val isConnected: Boolean get() = true

    override suspend fun connect() {
        // UDP is connectionless; nothing to establish.
    }

    override suspend fun request(payload: ByteArray): ByteArray? = withRetry {
        withContext(Dispatchers.IO) { exchange(payload) }
    }

    private fun exchange(payload: ByteArray): ByteArray? {
        DatagramSocket().use { sock ->
            sock.soTimeout = config.readTimeoutMs
            val address = InetSocketAddress(InetAddress.getByName(host), port)
            bus.emitRaw(RawPacket(PacketDirection.TX, remote, payload))
            val out = DatagramPacket(payload, payload.size, address)
            sock.send(out)

            val buf = ByteArray(8192)
            val inPacket = DatagramPacket(buf, buf.size)
            return try {
                sock.receive(inPacket)
                val resp = buf.copyOf(inPacket.length)
                bus.emitRaw(RawPacket(PacketDirection.RX, remote, resp))
                resp
            } catch (_: SocketTimeoutException) {
                null
            }
        }
    }

    override suspend fun send(payload: ByteArray) = io {
        withContext(Dispatchers.IO) {
            DatagramSocket().use { sock ->
                val address = InetSocketAddress(InetAddress.getByName(host), port)
                bus.emitRaw(RawPacket(PacketDirection.TX, remote, payload))
                sock.send(DatagramPacket(payload, payload.size, address))
            }
        }
    }

    override suspend fun disconnect() { /* stateless */ }
    override fun close() { /* stateless */ }
}
