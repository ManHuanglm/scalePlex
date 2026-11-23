package com.scaleplex.service

import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.DeviceStatus
import com.scaleplex.core.model.DeviceRuntime
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.ScaleGroup
import com.scaleplex.db.Database
import com.scaleplex.db.dao.DeviceDao
import com.scaleplex.db.dao.GroupDao
import com.scaleplex.driver.api.IScaleDriver
import com.scaleplex.driver.api.ScaleDriverFactory
import com.scaleplex.driver.impl.DriverRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Device & group management plus multi-scale concurrent batch download.
 *
 * Driver instances are cached per device id and reused across operations so a single
 * connection serves consecutive requests. Batch download fans out to all scales in a
 * group concurrently (bounded by a semaphore) — the core selling point.
 */
class ScaleService(db: Database) {

    private val log = LoggerFactory.getLogger(ScaleService::class.java)
    private val deviceDao = DeviceDao(db)
    private val groupDao = GroupDao(db)
    private val drivers = ConcurrentHashMap<Long, IScaleDriver>()

    init { DriverRegistry.install() }

    // ---- groups ----
    fun groups(): List<ScaleGroup> = groupDao.list()
    fun addGroup(name: String, note: String = ""): Long = groupDao.insert(ScaleGroup(name = name, note = note))
    fun updateGroup(g: ScaleGroup) = groupDao.update(g)
    fun deleteGroup(id: Long) = groupDao.delete(id)

    // ---- devices ----
    fun devices(groupId: Long? = null): List<ScaleDevice> = deviceDao.list(groupId)
    fun addDevice(d: ScaleDevice): Long = deviceDao.insert(d)
    fun updateDevice(d: ScaleDevice) = deviceDao.update(d)
    suspend fun deleteDevice(id: Long) {
        drivers.remove(id)?.let { runCatching { it.disconnect() } }
        deviceDao.delete(id)
    }
    fun getDevice(id: Long) = deviceDao.get(id)
    fun devicesInGroup(groupId: Long) = deviceDao.list(groupId)

    /** Create a (or fetch the cached) driver for a device. */
    fun driverFor(device: ScaleDevice): IScaleDriver =
        drivers.getOrPut(device.id) { ScaleDriverFactory.create(device) }

    fun isBrandSupported(brand: com.scaleplex.core.model.ScaleBrand) = ScaleDriverFactory.isSupported(brand)

    /** Connect a single device and refresh its runtime status. */
    suspend fun connect(device: ScaleDevice): DeviceRuntime {
        val drv = driverFor(device)
        val res = drv.connect()
        return res.fold(
            onSuccess = { DeviceRuntime(device, DeviceStatus.ONLINE) },
            onFailure = { DeviceRuntime(device, DeviceStatus.OFFLINE) },
        )
    }

    suspend fun disconnect(device: ScaleDevice) {
        drivers.remove(device.id)?.let { runCatching { it.disconnect() } }
    }

    /**
     * Push PLUs to a set of devices concurrently. Returns one [BatchResult] per device.
     * [maxConcurrency] bounds simultaneous connections to avoid saturating the host.
     */
    suspend fun downloadToDevices(
        devices: Collection<ScaleDevice>,
        plus: List<PluItem>,
        maxConcurrency: Int = 4,
    ): Map<Long, BatchResult> = coroutineScope {
        val sem = Semaphore(maxConcurrency)
        devices.map { device ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val drv = driverFor(device)
                    val connected = drv.connect().isSuccess
                    if (!connected) {
                        device.id to BatchResult(total = plus.size, failed = plus.size,
                            errors = listOf(com.scaleplex.core.model.BatchError(device.id, null, "connect failed")))
                    } else {
                        val r = drv.downloadPluBatch(plus)
                        device.id to r.getOrElse {
                            BatchResult(total = plus.size, failed = plus.size,
                                errors = listOf(com.scaleplex.core.model.BatchError(device.id, null, it.message ?: "error")))
                        }
                    }
                }
            }
        }.awaitAll().toMap()
    }

    /** Push PLUs to every scale in a group. */
    suspend fun downloadToGroup(groupId: Long, plus: List<PluItem>): Map<Long, BatchResult> =
        downloadToDevices(devicesInGroup(groupId), plus)

    /**
     * Push a label template to a set of devices concurrently, mirroring [downloadToDevices]
     * result semantics (one [BatchResult] per device, single record).
     */
    suspend fun downloadLabelToDevices(
        devices: Collection<ScaleDevice>,
        template: LabelTemplate,
        maxConcurrency: Int = 4,
    ): Map<Long, BatchResult> = coroutineScope {
        val sem = Semaphore(maxConcurrency)
        devices.map { device ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val drv = driverFor(device)
                    val connected = drv.connect().isSuccess
                    if (!connected) {
                        device.id to BatchResult(total = 1, failed = 1,
                            errors = listOf(com.scaleplex.core.model.BatchError(device.id, null, "connect failed")))
                    } else {
                        val r = drv.downloadLabelTemplate(template).fold(
                            onSuccess = { BatchResult.ok(total = 1) },
                            onFailure = { BatchResult.single(com.scaleplex.core.model.BatchError(device.id, null, it.message ?: "error")) },
                        )
                        device.id to r
                    }
                }
            }
        }.awaitAll().toMap()
    }

    /** Push a label template to every scale in a group. */
    suspend fun downloadLabelToGroup(groupId: Long, template: LabelTemplate): Map<Long, BatchResult> =
        downloadLabelToDevices(devicesInGroup(groupId), template)

    suspend fun syncTime(device: ScaleDevice): Result<Unit> = driverFor(device).syncTime()
}
