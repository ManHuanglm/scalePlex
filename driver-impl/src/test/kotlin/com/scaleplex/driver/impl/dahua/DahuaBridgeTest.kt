package com.scaleplex.driver.impl.dahua

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 大华桥接纯函数测试 —— record.txt/Return.txt 行格式用官方样例锁定
 * （`information/大华秤接口/上海大华电子秤接口2010.07.26/record.txt`、`return.txt`）。
 */
class DahuaBridgeTest {

    // 官方 record.txt 样例行（GBK，Tab 分隔，9 列）
    private val recordLines = """
        192.168.1.199	0912161541	00	03	0001	30.000	20.00	600.00	600.00
        192.168.1.199	0912211310	00	03	0001	0.095	20.00	1.90	1.90
        192.168.1.199	1001081312	00	20	0002	0.175	10.50	1.80	1.80
    """.trimIndent()

    @Test
    fun parseRecordTxtSampleLines() {
        val records = DahuaBridge.parseRecordTxt(recordLines.toByteArray(charset("GBK")), deviceId = 7L)
        assertEquals(3, records.size)
        // 行1：2009-12-16 15:41 / 部门 03 / PLU 0001 / 30.000kg / 20.00 元 / 600.00 元
        records[0].let {
            assertEquals(7L, it.deviceId)
            assertEquals(200912161541L, it.recordNo) // 时间串作流水号
            assertEquals(2009, it.timestamp?.year)
            assertEquals(12, it.timestamp?.monthNumber)
            assertEquals(16, it.timestamp?.dayOfMonth)
            assertEquals(15, it.timestamp?.hour)
            assertEquals(41, it.timestamp?.minute)
            assertEquals(1, it.pluNo)
            assertEquals(30000, it.weight) // kg × 1000 → 克
            assertEquals(2000L, it.unitPrice) // 20.00 元 → 分
            assertEquals(60000L, it.amount)
            assertEquals("03", it.operator) // 部门号暂存 operator
        }
        // 行2：0.095 kg → 95 g
        assertEquals(95, records[1].weight)
        // 行3：PLU 0002 / 部门 20 / 10.50 元
        assertEquals(2, records[2].pluNo)
        assertEquals(1050L, records[2].unitPrice)
    }

    @Test
    fun parseRecordTxtSkipsInvalidLines() {
        val bytes = "bad line\n\n192.168.1.199\t0912161541\t00\t03\t0001\t30.000\t20.00\t600.00\t600.00"
            .toByteArray(charset("GBK"))
        val records = DahuaBridge.parseRecordTxt(bytes, 1L)
        assertEquals(1, records.size)
    }

    @Test
    fun readReturnTxtParsesIpCode() {
        // 官方 return.txt 样例：IP<Tab>码
        val file = kotlin.io.path.createTempFile("scaleplex-test-return", ".txt").toFile()
        try {
            file.writeText("192.168.1.199\t0\r\n", charset("GBK"))
            assertEquals(0, DahuaBridge.readReturnTxt(file, "192.168.1.199"))
            file.writeText("192.168.1.199\t-1\r\n", charset("GBK"))
            assertEquals(-1, DahuaBridge.readReturnTxt(file, "192.168.1.199"))
            assertEquals(null, DahuaBridge.readReturnTxt(file, "10.0.0.1"))
        } finally {
            file.delete()
        }
        assertNull(DahuaBridge.readReturnTxt(file, "192.168.1.199")) // 文件已删除
    }

    @Test
    fun describeCodes() {
        assertEquals("成功", DahuaBridge.describeSendCode(0))
        assertEquals("PLU 文本为空或与 PLU 版本不一致", DahuaBridge.describeSendCode(-5))
        assertEquals("通信存在异常（详见 Return.txt）", DahuaBridge.describeSendCode(-6))
        assertEquals("连接失败", DahuaBridge.describeReturnCode(-1))
        assertEquals("未收到正确的结束标志(0D 0A 03)，已重试 3 次", DahuaBridge.describeReturnCode(-10))
    }

    @Test
    fun iniKeys() {
        assertEquals("PLU_VERSION", DahuaPluTextCodec.VERSION_KEY)
        assertEquals("_PLU_PATH_", DahuaPluTextCodec.PATH_KEY)
        assertEquals(3, DahuaPluTextCodec.PluVersion.TMA_07.id)
        assertTrue(DahuaBridge.DLL_NAME == "DhScalePluNet.dll")
    }
}
