package com.scaleplex.driver.impl.cas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 凯士 `W02A` 回帧解析单元测试。
 * 帧样本 = 真机抓包（2026-11-27 @192.168.0.111，PLU1 大白菜 20.00 元/保质期 15 天）。
 */
class CasUploadFrameTest {

    /** 真机 PLU1 回帧（300B，`W02A00001,01L011A:`，载荷 282B 含 1B BCC）。 */
    private val plu1Frame = hex(
        "57 30 32 41 30 30 30 30 31 2C 30 31 4C 30 31 31" +
            "41 3A 5E 3D 30 31 2E 2A 3D 30 31 2E 24 3D 30 2E" +
            "26 3D 43 30 41 38 30 30 36 46 2E 40 3D 34 46 35" +
            "30 2E 3F 3D 33 2E 4E 3D 30 30 30 31 2E 46 3D 30" +
            "31 2E 35 37 2C 30 32 3A 01 00 46 3D 30 32 2E 34" +
            "43 2C 30 34 3A 01 00 00 00 46 3D 30 34 2E 34 44" +
            "2C 30 31 3A 01 46 3D 30 41 2E 35 33 2C 30 36 3A" +
            "B4 F3 B0 D7 B2 CB 46 3D 31 45 2E 35 33 2C 30 30" +
            "3A 46 3D 31 46 2E 35 33 2C 30 30 3A 46 3D 30 39" +
            "2E 35 37 2C 30 32 3A 00 00 46 3D 35 30 2E 35 37" +
            "2C 30 32 3A 33 00 46 3D 30 33 2E 35 33 2C 30 30" +
            "3A 46 3D 30 42 2E 34 43 2C 30 34 3A 00 00 00 00" +
            "46 3D 30 45 2E 35 37 2C 30 32 3A 00 00 46 3D 30" +
            "46 2E 34 32 2C 30 31 3A 00 46 3D 30 36 2E 34 43" +
            "2C 30 34 3A D0 07 00 00 46 3D 30 44 2E 34 43 2C" +
            "30 34 3A 00 00 00 00 46 3D 35 35 2E 35 37 2C 30" +
            "32 3A 00 00 46 3D 31 30 2E 34 43 2C 30 34 3A 0F" +
            "00 00 00 46 3D 31 39 2E 35 37 2C 30 32 3A 01 00" +
            "46 3D 35 41 2E 34 32 2C 30 31 3A 00",
    )

    /** 真机空槽回帧（W02AF4240,00L002B）。 */
    private val emptyFrame = hex(
        "57 30 32 41 46 34 32 34 30 2C 30 30 4C 30 30 32 42 3A 5E 3D 30 31 2E 2A 3D 30 31 2E " +
            "24 3D 30 2E 26 3D 43 30 41 38 30 30 36 46 2E 40 3D 34 46 35 30 2E 3F 3D 33 2E 4E 3D " +
            "30 30 30 30 2E 5A",
    )

    /**
     * 真机 PLU4 回帧（311B，CL-Works 下发的称重商品，字段 len 含十六进制字母）：
     * `F=0A.53,11:`（0x11=17B 名称"鲜带肉尾叉骨 计重"）、`F=0B` 项目代码 169460、
     * `F=06` 价格 2000 分、`F=10` 保质期 5 天。回归锁定：len 必须按十六进制解析
     * （曾按十进制读 → 名称截断残字节 + 字段错位 → 价格 0）。
     */
    private val plu4Frame = hex(
        "57 30 32 41 30 30 30 30 34 2C 30 31 4C 30 31 32 35 3A 5E 3D " +
            "30 31 2E 2A 3D 30 31 2E 24 3D 30 2E 26 3D 43 30 41 38 30 30 " +
            "36 46 2E 40 3D 34 46 35 30 2E 3F 3D 33 2E 4E 3D 30 30 30 34 " +
            "2E 46 3D 30 31 2E 35 37 2C 30 32 3A 01 00 46 3D 30 32 2E 34 " +
            "43 2C 30 34 3A 04 00 00 00 46 3D 30 34 2E 34 44 2C 30 31 3A " +
            "01 46 3D 30 41 2E 35 33 2C 31 31 3A CF CA B4 F8 C8 E2 CE B2 " +
            "B2 E6 B9 C7 20 BC C6 D6 D8 46 3D 31 45 2E 35 33 2C 30 30 3A " +
            "46 3D 31 46 2E 35 33 2C 30 30 3A 46 3D 30 39 2E 35 37 2C 30 " +
            "32 3A 00 00 46 3D 35 30 2E 35 37 2C 30 32 3A 33 00 46 3D 30 " +
            "33 2E 35 33 2C 30 30 3A 46 3D 30 42 2E 34 43 2C 30 34 3A F4 " +
            "95 02 00 46 3D 30 45 2E 35 37 2C 30 32 3A 00 00 46 3D 30 46 " +
            "2E 34 32 2C 30 31 3A 00 46 3D 30 36 2E 34 43 2C 30 34 3A D0 " +
            "07 00 00 46 3D 30 44 2E 34 43 2C 30 34 3A 00 00 00 00 46 3D " +
            "35 35 2E 35 37 2C 30 32 3A 00 00 46 3D 31 30 2E 34 43 2C 30 " +
            "34 3A 05 00 00 00 46 3D 31 39 2E 35 37 2C 30 32 3A 04 00 46 " +
            "3D 35 41 2E 34 32 2C 30 31 3A 00 BB",
    )

    @Test
    fun `parse plu1 frame`() {
        val drv = driverForParse()
        val plu = drv.parseCasPluFrame(plu1Frame)
        checkNotNull(plu) { "解析失败" }
        assertEquals(1, plu.pluNo)
        assertEquals("大白菜", plu.name)
        assertEquals(1, plu.mainGroup)
        assertEquals(2000L, plu.unitPrice)
        assertEquals(15, plu.usedByDate)
        assertEquals(0, plu.weightFlag)
        assertEquals("", plu.eanCode)
    }

    @Test
    fun `parse plu4 frame with hex field lengths`() {
        val drv = driverForParse()
        val plu = drv.parseCasPluFrame(plu4Frame)
        checkNotNull(plu) { "解析失败" }
        assertEquals(4, plu.pluNo)
        assertEquals("鲜带肉尾叉骨 计重", plu.name)
        assertEquals(1, plu.mainGroup)
        assertEquals(2000L, plu.unitPrice)
        assertEquals(5, plu.usedByDate)
        assertEquals(0, plu.weightFlag)
        assertEquals("169460", plu.eanCode)
    }

    @Test
    fun `parse empty slot frame returns null`() {
        val drv = driverForParse()
        assertNull(drv.parseCasPluFrame(emptyFrame))
    }

    @Test
    fun `parse truncated frame returns null`() {
        val drv = driverForParse()
        assertNull(drv.parseCasPluFrame(plu1Frame.copyOf(20)))
    }

    /** parseCasPluFrame 为 internal（同模块可测），借助伴生默认设备构造驱动实例。 */
    private fun driverForParse(): CasClDriver {
        val device = com.scaleplex.core.model.ScaleDevice(
            id = 0,
            name = "test",
            brand = com.scaleplex.core.model.ScaleBrand.CAS,
            model = "CL5200N",
            commType = com.scaleplex.core.model.CommType.TCP,
            ip = "127.0.0.1",
            port = 20304,
        )
        return CasClDriver(device)
    }

    /** HEX 文本 → 字节（regex 提取，容忍多行拼接处丢失的分隔空格）。 */
    private fun hex(s: String): ByteArray =
        Regex("[0-9A-Fa-f]{2}").findAll(s).map { it.value.toInt(16).toByte() }.toList().toByteArray()
}
