package com.scaleplex.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scaleplex.communication.CommEventBus
import com.scaleplex.core.model.DeviceRuntime
import com.scaleplex.core.model.DeviceStatus
import com.scaleplex.core.model.ScaleConfig
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.db.Database
import com.scaleplex.service.ConfigService
import com.scaleplex.service.CategoryService
import com.scaleplex.service.LabelService
import com.scaleplex.service.PluService
import com.scaleplex.service.ScaleService
import com.scaleplex.service.TradeLogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Top-level application state shared across the UI. Holds the service singletons,
 * the live device-status map and UI-level flags (theme, selected tab).
 */
class AppState(
    val db: Database,
    val configService: ConfigService,
    config: ScaleConfig,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val commBus = CommEventBus()

    val pluService = PluService(db)
    val categoryService = CategoryService(db)
    val scaleService = ScaleService(db)
    val labelService = LabelService(db)
    val tradeLogService = TradeLogService(db)

    var config: ScaleConfig by mutableStateOf(config)
    var darkMode by mutableStateOf(false)
    val bgColor get() = if (darkMode) androidx.compose.ui.graphics.Color(0xFF1E1F22)
                        else androidx.compose.ui.graphics.Color(0xFFF5F6F8)

    /** Selected left-tab index. */
    var selectedTab by mutableStateOf(0)

    /** Live runtime status per device id (observable map), refreshed by the heartbeat poller. */
    val deviceRuntimes = mutableStateMapOf<Long, DeviceRuntime>()

    private var heartbeatJob: Job? = null

    /**
     * 周期心跳：按配置间隔对每台秤做协议级状态探测（getStatus 应答判定在线），
     * 避免以 UDP connect 误判在线。每批探测并行执行并等待全部完成后再进入下一轮，
     * 避免同设备探测重叠。
     */
    fun startHeartbeatPolling() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                val devices = scaleService.devices()
                syncRuntimes(devices)
                coroutineScope {
                    devices.forEach { dev ->
                        launch {
                            // 单台秤探测失败（离线/未注册驱动）不应中断整轮心跳
                            deviceRuntimes[dev.id] = runCatching { scaleService.probeStatus(dev) }
                                .getOrElse { DeviceRuntime(dev, DeviceStatus.OFFLINE) }
                        }
                    }
                }
                delay(config.heartbeatIntervalMs.coerceAtLeast(2000).toLong())
            }
        }
    }

    /**
     * 运行时状态表与设备表保持一致：移除已删除设备、为新增设备补默认状态，
     * 保证界面上的设备总数在首轮探测尚未返回时也准确。
     */
    private fun syncRuntimes(devices: List<ScaleDevice>) {
        val ids = devices.map { it.id }.toSet()
        deviceRuntimes.keys.toList().forEach { id ->
            if (id !in ids) deviceRuntimes.remove(id)
        }
        devices.forEach { dev ->
            if (deviceRuntimes[dev.id] == null) deviceRuntimes[dev.id] = DeviceRuntime(dev)
        }
    }

    fun applyConfig(cfg: ScaleConfig) {
        config = cfg
        configService.save(cfg)
        commBus.rawCaptureEnabled.set(cfg.rawPacketLog)
    }

    /** 立即执行一轮协议级在线探测（侧边栏手动刷新用，不等待心跳周期）。 */
    fun refreshDeviceStatus() {
        scope.launch {
            val devices = scaleService.devices()
            syncRuntimes(devices)
            coroutineScope {
                devices.forEach { dev ->
                    launch {
                        deviceRuntimes[dev.id] = runCatching { scaleService.probeStatus(dev) }
                            .getOrElse { DeviceRuntime(dev, DeviceStatus.OFFLINE) }
                    }
                }
            }
        }
    }

    /**
     * 启动自动下发（参考银豹"自动下发"）：配置开启时，稍等心跳首轮探测后
     * 向全部启用秤下发一次全量 PLU，结果仅记录日志，不打扰用户。
     */
    fun startAutoDownloadIfNeeded() {
        if (!config.autoDownload) return
        scope.launch {
            delay(5000)
            scaleService.downloadToDevices(
                scaleService.devices().filter { it.enabled },
                pluService.all(),
            )
        }
    }

    fun currentDevice(id: Long): ScaleDevice? = scaleService.getDevice(id)

    fun shutdown() {
        scope.cancel()
    }
}
