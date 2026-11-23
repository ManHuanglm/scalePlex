package com.scaleplex.driver.impl

import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.driver.api.ScaleDriverFactory
import com.scaleplex.driver.impl.dahua.DahuaTcpDriver
import com.scaleplex.driver.impl.digi.DigiSmDriver

/**
 * Registers all concrete drivers with [ScaleDriverFactory]. Call [install] once at
 * application startup (from the app module's main) so `ScaleDriverFactory.create(device)`
 * can resolve every supported brand. Adding a phase-2/3 driver is one line here.
 */
object DriverRegistry {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            ScaleDriverFactory.register(ScaleBrand.DIGI) { device -> DigiSmDriver(device) }
            ScaleDriverFactory.register(ScaleBrand.DAHUA) { device -> DahuaTcpDriver(device) }
            // Phase 2/3 drivers:
            // ScaleDriverFactory.register(ScaleBrand.YOUSHENG) { YouShengDriver(it) }
            // ScaleDriverFactory.register(ScaleBrand.HIHONG) { HuiHongUdpDriver(it) }
            installed = true
        }
    }
}
