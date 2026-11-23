package com.scaleplex.driver.impl.mettler

import com.scaleplex.communication.CommEventBus
import com.scaleplex.communication.Transport
import com.scaleplex.communication.TransportFactory
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.driver.api.AbstractScaleDriver

/**
 * 托利多 METTLER_TOLEDO 骨架驱动（适配 8442 / bCom / bPro / RL00 / FreshBase 等系列）。
 *
 * 托利多各系列使用官方 Scale Manager 及不同标签编辑器（VisEdit/bLabel/Smartlabel），
 * 通信协议私有且分型号差异大——全部操作当前返回"待实体秤联调"，仅完成品牌注册，
 * 使设备管理可选择托利多品牌而不崩溃。协议编解码待抓包资料补充后按
 * {brand}/XxxProtocol + XxxPluEncoder + XxxDriver 三件套模式落地。
 */
class MettlerToledoDriver(
    private val device: ScaleDevice,
    override val transport: Transport = TransportFactory.create(device, CommEventBus()),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.METTLER_TOLEDO

    private suspend fun pending(): Nothing =
        throw UnsupportedOperationException(
            "托利多协议待实体秤联调 (Mettler Toledo protocol pending field capture): ${device.name}",
        )

    override suspend fun getStatus() = pending()

    override suspend fun downloadPlu(plu: com.scaleplex.core.model.PluItem) = pending()

    override suspend fun downloadPluBatch(list: List<com.scaleplex.core.model.PluItem>) = pending()

    override suspend fun deletePlu(pluNo: Int) = pending()

    override suspend fun deleteAllPlu() = pending()

    override suspend fun syncTime() = pending()

    override suspend fun downloadLabelTemplate(template: com.scaleplex.core.model.LabelTemplate) = pending()

    override suspend fun readTradeLog() = pending()

    override suspend fun clearTradeLog() = pending()
}
