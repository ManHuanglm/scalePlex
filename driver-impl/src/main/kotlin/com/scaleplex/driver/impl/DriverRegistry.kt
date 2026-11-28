package com.scaleplex.driver.impl

import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.driver.api.ScaleDriverFactory
import com.scaleplex.driver.impl.aclas.AclasDriver
import com.scaleplex.driver.impl.cas.CasClDriver
import com.scaleplex.driver.impl.dahua.DahuaTcpDriver
import com.scaleplex.driver.impl.digi.DigiSmDriver
import com.scaleplex.driver.impl.hihong.HiHongUdpDriver
import com.scaleplex.driver.impl.mettler.MettlerToledoDriver
import com.scaleplex.driver.impl.rongta.RongtaUdpDriver
import com.scaleplex.driver.impl.yousheng.YouShengDriver

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
            ScaleDriverFactory.register(ScaleBrand.YOUSHENG) { device -> YouShengDriver(device) }
            ScaleDriverFactory.register(ScaleBrand.HIHONG) { device -> HiHongUdpDriver(device) }
            ScaleDriverFactory.register(ScaleBrand.METTLER_TOLEDO) { device -> MettlerToledoDriver(device) }
            ScaleDriverFactory.register(ScaleBrand.DINGTOP) { device -> AclasDriver(device) }
            ScaleDriverFactory.register(ScaleBrand.CAS) { device -> CasClDriver(device) }
            ScaleDriverFactory.register(ScaleBrand.RONGTA) { device -> RongtaUdpDriver(device) }
            installed = true
        }
    }
}
