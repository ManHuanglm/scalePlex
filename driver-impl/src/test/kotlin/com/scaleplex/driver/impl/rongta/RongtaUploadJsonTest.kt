package com.scaleplex.driver.impl.rongta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 容大桥接 NDJSON 解析单元测试。
 * 样本 = 真机回收（2026-11-27 @192.168.0.96，官方 rtslabelscale.dll 回调 JSON）。
 */
class RongtaUploadJsonTest {

    /** 真机样本行：大白菜 0.90 元 / 部门 24 / 保质期 15 天 / 条码 99。 */
    private val sample =
        """{ "PackageWeight": 0, "PackageType": 0, "VAT": 0, "Ice": 0, "Message2": 0, """ +
            """"Message1": 0, "BarCode": 99, "WeightUnit": 4, "PluName": "大白菜", "HotKey": 1, """ +
            """"Tolerance": 5, "UnitPrice": 90, "QtyUnit": 0, "LFCode": 1, "IsLock": false, """ +
            """"LabelId": 0, "Rebate": 0, "DisCountPrice": 0, "Deptment": 24, "Tare": 0, """ +
            """"Code": "1", "Recomdays": 0, "ShlefTime": 15, "AddInfoId": 0}"""

    @Test
    fun `parse plu json sample`() {
        val drv = driverForParse()
        val plu = drv.parsePluJson(sample)
        checkNotNull(plu) { "解析失败" }
        assertEquals(1, plu.pluNo)
        assertEquals("大白菜", plu.name)
        assertEquals(24, plu.mainGroup)
        assertEquals(90L, plu.unitPrice)
        assertEquals(15, plu.usedByDate)
        assertEquals("99", plu.eanCode)
        assertEquals(0, plu.weightFlag)
    }

    @Test
    fun `parse non-json line returns null`() {
        val drv = driverForParse()
        assertNull(drv.parsePluJson("LoadIniFile=0"))
        assertNull(drv.parsePluJson(""))
    }

    /** parsePluJson 为 internal（同模块可测），构造驱动实例仅需设备信息。 */
    private fun driverForParse(): RongtaUdpDriver {
        val device = com.scaleplex.core.model.ScaleDevice(
            id = 0,
            name = "test",
            brand = com.scaleplex.core.model.ScaleBrand.RONGTA,
            model = "RLS1000",
            commType = com.scaleplex.core.model.CommType.UDP,
            ip = "127.0.0.1",
            port = 5001,
        )
        return RongtaUdpDriver(device)
    }
}
