package com.scaleplex.core.model

/**
 * A group of scales. Batch "push to group" is a core feature: one PLU set fans out
 * to every scale in the group concurrently.
 */
data class ScaleGroup(
    val id: Long = 0,
    val name: String,
    val note: String = "",
)

/** Online / offline state of a device as seen by the connection heartbeat. */
enum class DeviceStatus { ONLINE, OFFLINE, CONNECTING, UNKNOWN }

/** Runtime view of a device: config + live status. */
data class DeviceRuntime(
    val device: ScaleDevice,
    val status: DeviceStatus = DeviceStatus.UNKNOWN,
    val model: String = "",
    val firmware: String = "",
    val lastSeen: Long = 0,
)
