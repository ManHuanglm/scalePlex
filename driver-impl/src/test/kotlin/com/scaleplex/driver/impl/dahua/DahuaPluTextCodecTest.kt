package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 大华 PLU 文本编解码测试 —— 行格式用官方样例锁定：
 * `information/大华秤接口/上海大华电子秤接口2010.07.26/PLU样本文件与说明/`（dhbz5.txt/dhbz6.txt/tma07.txt/dhBZ5.PLU）
 * 与《TMA07-PLU 说明》完整解释的"测试一"样例。
 */
class DahuaPluTextCodecTest {

    // dhbz6.txt 样例行（6 位单价，行尾 0D0A，真实文件逐字符）
    private val bz6Line1 = "0001100001123100008921000001A紫草B草叶"
    private val bz6Line2 = "0002100002000034003421000001A时B面粉"

    // dhbz5.txt / dhBZ5.PLU 样例行（5 位单价，行尾 0D0D0A）
    private val bz5Line1 = "000110000123100008921000001A紫草B草叶"

    // tma07.txt 样例试试一（《TMA07-PLU 说明》，真实文件行1）
    private val tma07Line1 =
        "1P0001A2281080002000000000000803000000000000000100000020106100712201318301924400##测试一##测试1##测试"

    @Test
    fun parseBz6SampleLines() {
        val items = DahuaPluTextCodec.parseLines(listOf(bz6Line1, bz6Line2))
        assertEquals(2, items.size)
        // 行1：PLU 0001 / 代码 100001 / 单价 123100 分 / 称重 / 有效期 089 / 店名 21 / 皮重 0 / 特殊信息 1
        items[0].let {
            assertEquals(1, it.pluNo)
            assertEquals("100001", it.eanCode)
            assertEquals(123100L, it.unitPrice)
            assertEquals(0, it.weightFlag)
            assertEquals(89, it.usedByDate)
            assertEquals(21, it.placeNo)
            assertEquals(0, it.tare)
            assertEquals(1, it.specialMsgLines.size)
            assertEquals("紫草", it.name)
            assertEquals("草叶", it.ingredientLines.first().text)
        }
        // 行2：0002 / 100002 / 单价 000034 分 / 有效期 034
        items[1].let {
            assertEquals(2, it.pluNo)
            assertEquals("100002", it.eanCode)
            assertEquals(34L, it.unitPrice)
            assertEquals(34, it.usedByDate)
            assertEquals("时", it.name)
            assertEquals("面粉", it.ingredientLines.first().text)
        }
    }

    @Test
    fun parseBz5SampleLine() {
        val items = DahuaPluTextCodec.parseLines(listOf(bz5Line1))
        assertEquals(1, items.size)
        items[0].let {
            assertEquals(1, it.pluNo)
            assertEquals("100001", it.eanCode)
            assertEquals(23100L, it.unitPrice)
            assertEquals(89, it.usedByDate)
            assertEquals(21, it.placeNo)
            assertEquals("紫草", it.name)
        }
    }

    @Test
    fun parseTma07SampleLine() {
        val items = DahuaPluTextCodec.parseLines(listOf(tma07Line1))
        assertEquals(1, items.size)
        items[0].let {
            assertEquals(1, it.pluNo)
            // 单价 002000 分/kg = 20 元/kg（《TMA07-PLU 说明》）
            assertEquals(2000L, it.unitPrice)
            assertEquals(0, it.weightFlag)
            assertEquals(8, it.usedByDate) // 008
            assertEquals(3, it.placeNo) // 店名号 03
            assertEquals(0, it.mainGroup) // 部门号 00
            assertEquals("0000000000000", it.eanCode) // 13 位数字代号
            assertEquals(10000, it.tare) // 10000 g（样例数据）
            assertEquals(0, it.label1Format) // 标签号 00
            assertEquals("测试一", it.name)
            assertEquals(listOf("测试一", "测试1", "测试"), it.commodityLines.map { l -> l.text })
        }
    }

    @Test
    fun encodeBz6RoundTripAndSample() {
        val plu = PluItem(
            pluNo = 1,
            eanCode = "100001",
            unitPrice = 123100L,
            weightFlag = 0,
            usedByDate = 89,
            placeNo = 21,
            name = "紫草",
            ingredientLines = listOf(PluTextLine(text = "草叶")),
            specialMsgLines = listOf(PluTextLine(text = "")),
        )
        val line = DahuaPluTextCodec.encodeLines(listOf(plu), DahuaPluTextCodec.PluVersion.TMBZ_6).single()
        // 与官方样例 dhbz6.txt 行1 完全一致
        assertEquals(bz6Line1, line)
        // 互逆
        val parsed = DahuaPluTextCodec.parseLines(listOf(line)).single()
        assertEquals(plu.pluNo, parsed.pluNo)
        assertEquals(plu.unitPrice, parsed.unitPrice)
        assertEquals(plu.name, parsed.name)
    }

    @Test
    fun encodeBz5UsesPriceWidth5AndCrLfCr() {
        val plu = PluItem(
            pluNo = 1,
            eanCode = "100001",
            unitPrice = 23100L,
            usedByDate = 89,
            placeNo = 21,
            name = "紫草",
            ingredientLines = listOf(PluTextLine(text = "草叶")),
            specialMsgLines = listOf(PluTextLine(text = "")),
        )
        val line = DahuaPluTextCodec.encodeLines(listOf(plu), DahuaPluTextCodec.PluVersion.TMBZ_5).single()
        assertEquals(bz5Line1, line)
        // dhbz5.txt / dhBZ5.PLU 行尾为 0D 0D 0A
        assertEquals("\r\r\n", DahuaPluTextCodec.lineSeparator(DahuaPluTextCodec.PluVersion.TMBZ_5))
        assertEquals("\r\n", DahuaPluTextCodec.lineSeparator(DahuaPluTextCodec.PluVersion.TMBZ_6))
    }

    @Test
    fun encodeTma07HeadMatchesSample() {
        val plu = PluItem(
            pluNo = 1,
            eanCode = "2281080",
            unitPrice = 2000L,
            usedByDate = 8,
            placeNo = 3,
            mainGroup = 0,
            tare = 1000,
            commodityLines = listOf(
                PluTextLine(text = "测试一"),
                PluTextLine(text = "测试1"),
                PluTextLine(text = "测试"),
            ),
        )
        val line = DahuaPluTextCodec.encodeLines(listOf(plu), DahuaPluTextCodec.PluVersion.TMA_07).single()
        // 商品代码/单价/称重/特殊信息/有效期/店名/部门段与官方样例完全一致（[0:34]）
        assertEquals(tma07Line1.substring(0, 34), line.substring(0, 34))
        // 13 位数字代号 = eanCode 右对齐（样例该段为全 0，此处为我们的映射语义）
        assertEquals("0000002281080", line.substring(34, 47))
        // 皮重（01000=1kg）/标签号/打折（00）/时段（全0）段：样例试试一该段数据非零，按我们的编码语义断言
        assertEquals("01000" + "00" + "00" + "000000000000000000000000", line.substring(47, 80))
        assertEquals("##测试一##测试1##测试", line.substring(80))
    }

    @Test
    fun parseAutoDetectsVersions() {
        val items = DahuaPluTextCodec.parseLines(listOf(bz5Line1, bz6Line1, tma07Line1))
        assertEquals(3, items.size)
        assertEquals(23100L, items[0].unitPrice)
        assertEquals(123100L, items[1].unitPrice)
        assertEquals(2000L, items[2].unitPrice)
    }

    @Test
    fun versionFromModel() {
        assertEquals(DahuaPluTextCodec.PluVersion.TMA_07, DahuaPluTextCodec.PluVersion.fromModel("TMA-07"))
        assertEquals(DahuaPluTextCodec.PluVersion.TMBZ_6, DahuaPluTextCodec.PluVersion.fromModel("DHBZ6"))
        assertEquals(DahuaPluTextCodec.PluVersion.TMBZ_5, DahuaPluTextCodec.PluVersion.fromModel("TMBZ_5位"))
        assertEquals(DahuaPluTextCodec.PluVersion.TMA_07, DahuaPluTextCodec.PluVersion.fromModel(""))
    }

    @Test
    fun gbkTruncateKeepsWholeHan() {
        // BZ 品名上限 18 字节 = 9 汉字；11 汉字输入截到前 9 个，不切半个汉字
        val name9 = "家润多乳牛肝菌白参"
        val line = DahuaPluTextCodec.encodeLines(
            listOf(PluItem(pluNo = 1, eanCode = "100001", unitPrice = 100L, name = name9 + "红李")),
            DahuaPluTextCodec.PluVersion.TMBZ_6,
        ).single()
        assertTrue(line.endsWith(name9 + "B"))
    }
}
