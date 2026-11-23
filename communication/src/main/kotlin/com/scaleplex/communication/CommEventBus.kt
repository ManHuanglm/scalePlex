package com.scaleplex.communication

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direction of a raw packet on the wire, for the debug log window.
 */
enum class PacketDirection { TX, RX }

/**
 * One raw frame observed on the transport. Captured when raw-packet logging is
 * enabled (Settings -> 报文调试) so technicians can diagnose protocol issues.
 */
data class RawPacket(
    val direction: PacketDirection,
    val remote: String,
    val data: ByteArray,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    fun hex(): String = data.joinToString(" ") { "%02X".format(it) }
    fun ascii(): String = buildString {
        for (b in data) append(if (b in 0x20..0x7E) b.toInt().toChar() else '.')
    }

    override fun equals(other: Any?): Boolean = other is RawPacket && other.timestampMs == timestampMs &&
        other.direction == direction && other.data.contentEquals(data)
    override fun hashCode(): Int = (timestampMs * 31 + direction.ordinal).toInt() * 31 + data.contentHashCode()
}

/**
 * Central sink for communication events: raw packets, connection state changes and errors.
 * Drivers and the UI subscribe here; the packet debug window renders [RawPacket] history.
 */
class CommEventBus {
    private val log = LoggerFactory.getLogger(CommEventBus::class.java)
    private val rawListeners = CopyOnWriteArrayList<(RawPacket) -> Unit>()
    private val stateListeners = CopyOnWriteArrayList<(String, Boolean) -> Unit>()
    private val errorListeners = CopyOnWriteArrayList<(String, Throwable) -> Unit>()

    /** When false, raw packet capture is suppressed (production mode). */
    val rawCaptureEnabled = AtomicBoolean(false)

    private val rawHistory = ArrayDeque<RawPacket>()
    private val historyLock = Any()
    private val maxHistory = 2000

    fun onRaw(l: (RawPacket) -> Unit) { rawListeners.add(l) }
    fun onState(l: (String, Boolean) -> Unit) { stateListeners.add(l) }
    fun onError(l: (String, Throwable) -> Unit) { errorListeners.add(l) }

    fun emitRaw(packet: RawPacket) {
        if (!rawCaptureEnabled.get()) return
        synchronized(historyLock) {
            rawHistory.addLast(packet)
            while (rawHistory.size > maxHistory) rawHistory.removeFirst()
        }
        rawListeners.forEach { runCatching { it(packet) }.onFailure { log.warn("raw listener err", it) } }
    }

    fun emitState(remote: String, connected: Boolean) {
        stateListeners.forEach { runCatching { it(remote, connected) }.onFailure { log.warn("state listener err", it) } }
    }

    fun emitError(remote: String, t: Throwable) {
        errorListeners.forEach { runCatching { it(remote, t) }.onFailure { log.warn("error listener err", it) } }
    }

    fun snapshot(): List<RawPacket> = synchronized(historyLock) { rawHistory.toList() }

    fun clearHistory() = synchronized(historyLock) { rawHistory.clear() }
}
