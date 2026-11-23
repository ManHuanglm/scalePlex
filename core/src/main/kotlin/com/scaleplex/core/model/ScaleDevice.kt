package com.scaleplex.core.model

/** Physical / network transport used to talk to a scale. */
enum class CommType(val id: String) {
    TCP("tcp"),
    UDP("udp"),
    SERIAL("serial");

    companion object {
        fun fromId(id: String): CommType = entries.firstOrNull { it.id.equals(id, true) } ?: TCP
    }
}

/** Serial (RS232) parameters for legacy scales. */
data class SerialParams(
    val portName: String = "COM1",
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    /** 0=None,1=Odd,2=Even */
    val parity: Int = 0,
    val stopBits: Int = 1,
)

/** Connection / protocol timing knobs shared by all transports. */
data class CommConfig(
    val connectTimeoutMs: Int = 3000,
    val readTimeoutMs: Int = 2000,
    val retries: Int = 3,
    val heartbeatIntervalMs: Int = 5000,
)

/** A logical scale device registered in the system. */
data class ScaleDevice(
    val id: Long = 0,
    val name: String,
    val brand: ScaleBrand,
    val model: String = "",
    val commType: CommType = CommType.UDP,
    val ip: String = "",
    val port: Int = 1111,
    val serial: SerialParams = SerialParams(),
    val comm: CommConfig = CommConfig(),
    val groupId: Long = 0,
    val enabled: Boolean = true,
    val note: String = "",
)
