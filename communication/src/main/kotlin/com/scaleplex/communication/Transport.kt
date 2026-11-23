package com.scaleplex.communication

import com.scaleplex.core.model.CommConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.Closeable

/**
 * A unidirectional request/response exchange over a transport.
 * Implementations hide TCP, UDP or serial specifics behind one contract that
 * [com.scaleplex.driver.api.IScaleDriver] depends on.
 */
interface Transport : Closeable {
    val remote: String
    val isConnected: Boolean

    /**
     * Open the transport (connect for TCP/serial; a no-op bind for UDP).
     * Safe to call repeatedly.
     */
    suspend fun connect()

    /**
     * Send [payload] and await a response, retrying up to [CommConfig.retries] on
     * timeout. Returns the raw response bytes, or null when no reply arrived.
     */
    suspend fun request(payload: ByteArray): ByteArray?

    /** Fire-and-forget send (no response awaited). */
    suspend fun send(payload: ByteArray)

    suspend fun disconnect()
}

/** Shared retry / timeout plumbing for all transports. */
abstract class BaseTransport(
    protected val config: CommConfig,
    protected val bus: CommEventBus,
) : Transport {
    protected val log = LoggerFactory.getLogger(javaClass)

    protected suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }

    /**
     * Run [attempt] up to [config.retries] times with the configured read timeout.
     * Returns the first non-null result, or null after exhausting retries.
     */
    protected suspend fun withRetry(action: suspend (attempt: Int) -> ByteArray?): ByteArray? {
        var lastErr: Throwable? = null
        for (attempt in 1..maxOf(1, config.retries)) {
            try {
                val result = action(attempt)
                if (result != null) return result
            } catch (t: Throwable) {
                lastErr = t
                log.warn("transport attempt $attempt failed: ${t.message}")
                bus.emitError(remote, t)
            }
        }
        return null
    }
}
