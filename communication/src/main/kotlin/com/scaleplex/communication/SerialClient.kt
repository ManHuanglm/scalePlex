package com.scaleplex.communication

import com.fazecast.jSerialComm.SerialPort
import com.scaleplex.core.model.CommConfig
import com.scaleplex.core.model.SerialParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * RS232 serial client backed by jSerialComm. Covers legacy YouSheng scales that
 * lack Ethernet. Parity maps: 0=None, 1=Odd, 2=Even.
 */
class SerialClient(
    private val params: SerialParams,
    config: CommConfig,
    bus: CommEventBus,
) : BaseTransport(config, bus) {

    private var port: SerialPort? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val lock = Any()

    override val remote: String get() = "${params.portName}@${params.baudRate}"
    override val isConnected: Boolean get() = port?.isOpen == true

    override suspend fun connect() = io {
        synchronized(lock) {
            if (isConnected) return@io
            bus.emitState(remote, false)
            val p = SerialPort.getCommPort(params.portName)
            p.baudRate = params.baudRate
            p.numDataBits = params.dataBits
            p.numStopBits = if (params.stopBits == 2) SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT
            p.parity = when (params.parity) {
                1 -> SerialPort.ODD_PARITY
                2 -> SerialPort.EVEN_PARITY
                else -> SerialPort.NO_PARITY
            }
            check(p.openPort()) { "Failed to open serial port ${params.portName}" }
            p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, config.readTimeoutMs, 0)
            port = p
            input = p.inputStream
            output = p.outputStream
            bus.emitState(remote, true)
            log.info("Serial connected ${params.portName}@${params.baudRate}")
        }
    }

    override suspend fun request(payload: ByteArray): ByteArray? = withRetry {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val inp = input ?: return@synchronized null
                val out = output ?: return@synchronized null
                try {
                    bus.emitRaw(RawPacket(PacketDirection.TX, remote, payload))
                    out.write(payload)
                    out.flush()
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

    override suspend fun disconnect() = io {
        synchronized(lock) {
            runCatching { port?.closePort() }
            port = null
            input = null
            output = null
            bus.emitState(remote, false)
        }
    }

    override fun close() {
        runCatching { port?.closePort() }
    }
}
