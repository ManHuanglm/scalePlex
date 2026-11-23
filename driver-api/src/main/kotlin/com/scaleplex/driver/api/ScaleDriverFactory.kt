package com.scaleplex.driver.api

import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import java.util.concurrent.ConcurrentHashMap

/**
 * Instantiates the correct [IScaleDriver] for a [ScaleDevice] based on its brand.
 *
 * Lives in driver-api as a pure registry so services depend only on this module.
 * Concrete driver implementations register themselves via [register] from
 * `driver-impl` at startup (see `DriverRegistry`), keeping the dependency
 * direction clean: app -> service -> driver-api <- driver-impl.
 */
object ScaleDriverFactory {

    private val providers = ConcurrentHashMap<ScaleBrand, (ScaleDevice) -> IScaleDriver>()

    fun register(brand: ScaleBrand, provider: (ScaleDevice) -> IScaleDriver) {
        providers[brand] = provider
    }

    fun isSupported(brand: ScaleBrand): Boolean = providers.containsKey(brand)

    fun supportedBrands(): List<ScaleBrand> = providers.keys.toList()

    fun create(device: ScaleDevice): IScaleDriver {
        val provider = providers[device.brand]
            ?: throw IllegalArgumentException("No driver registered for brand ${device.brand}")
        return provider(device)
    }
}
