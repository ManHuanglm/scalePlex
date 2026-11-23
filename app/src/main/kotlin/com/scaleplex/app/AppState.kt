package com.scaleplex.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scaleplex.communication.CommEventBus
import com.scaleplex.core.model.DeviceRuntime
import com.scaleplex.core.model.ScaleConfig
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.db.Database
import com.scaleplex.service.ConfigService
import com.scaleplex.service.LabelService
import com.scaleplex.service.PluService
import com.scaleplex.service.ScaleService
import com.scaleplex.service.TradeLogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
    val scaleService = ScaleService(db)
    val labelService = LabelService(db)
    val tradeLogService = TradeLogService(db)

    var config: ScaleConfig by mutableStateOf(config)
    var darkMode by mutableStateOf(false)
    val bgColor get() = if (darkMode) androidx.compose.ui.graphics.Color(0xFF1E1F22)
                        else androidx.compose.ui.graphics.Color(0xFFF5F6F8)

    /** Selected left-tab index. */
    var selectedTab by mutableStateOf(0)

    /** Live runtime status per device id, refreshed by the heartbeat poller. */
    val deviceRuntimes = mutableMapOf<Long, DeviceRuntime>()

    fun applyConfig(cfg: ScaleConfig) {
        config = cfg
        configService.save(cfg)
        commBus.rawCaptureEnabled.set(cfg.rawPacketLog)
    }

    fun currentDevice(id: Long): ScaleDevice? = scaleService.getDevice(id)

    fun shutdown() {
        scope.cancel()
    }
}
