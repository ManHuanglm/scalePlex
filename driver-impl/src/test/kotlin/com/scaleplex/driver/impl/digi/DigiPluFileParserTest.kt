package com.scaleplex.driver.impl.digi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigiPluFileParserTest {

    /**
     * 《PLU_FILE.TXT》文档样例记录（按字段逐段程序化构造，record size 0x56=86）。
     * PLU=1661、价格 24.90、标签1=0x11、条码格式=0x21、EAN F1F2=23/ITF、部门 997、
     * 商品文本段 font15 size13（GBK），成分/特殊信息空段（font14 size0）。
     */
    private val docSample: String = buildString {
        append("00001661").append("0056").append("5D00").append("DFE801").append("00002490")
        append("11").append("00").append("21").append("23001661000090").append("0997")
        append("0000").append("0000").append("0000")
        repeat(30) { append("00") } // Discount
        append("150D").append("D1BCC8F3C0B0B3A6353030BFCB").append("0C")
        append("14000C").append("14000C").append("00") // BCC
    }

    /** 真机下载 `192.168.0.15.25.dat`（114 字节记录）：价格 31.00、部门 997、EAN、多段文本。 */
    private val realSample =
        "0000000100727C00FFE73F00003100000000000101680000000997000000000003000000000000000000000000000000000204BCA6A3B10D0101200C030BCDC1BCA6B5B020D6D8C7EC0D1801200D1801200D1801200D1801200C1704C9CFBAA30D1701200D1701200D1701200D1701200C00"

    @Test
    fun `parses doc sample plu record`() {
        val plu = DigiPluFileParser.parseLine(docSample)!!
        assertEquals(1661, plu.pluNo)
        assertEquals(2490L, plu.unitPrice) // BCD 00002490 = 24.90 元
        assertEquals(0x11, plu.label1Format)
        assertEquals(0x21, plu.barcodeFormat)
        // EAN 数据：F1F2=23、末字节高位 9 = ITF
        assertEquals("23", plu.flagData)
        assertEquals(9, plu.barcodeType)
        assertEquals(997, plu.mainGroup)
        // 商品文本段：GBK 可打印段提取
        assertEquals(1, plu.commodityLines.size)
        assertTrue(plu.commodityLines[0].text.isNotBlank())
    }

    @Test
    fun `parses real scale download record`() {
        val plu = DigiPluFileParser.parseLine(realSample)!!
        assertEquals(1, plu.pluNo)
        assertEquals(3100L, plu.unitPrice) // 31.00 元
        assertEquals(997, plu.mainGroup)
        // EAN：末字节高位 0 = EAN 条码
        assertEquals(0, plu.barcodeType)
        // 商品名称来自第一个文本段
        assertTrue(plu.commodityLines.isNotEmpty())
    }

    @Test
    fun `skips invalid lines`() {
        assertEquals(0, DigiPluFileParser.parseLines(listOf("", "NOTHEXLINE", "ABCDEF")).size)
        assertTrue(DigiPluFileParser.parseLines(listOf(docSample)).isNotEmpty())
    }

    @Test
    fun `indexed parse keeps tws record numbers`() {
        // 3 条记录（PLU 1661 / 1 / 1661），记录号 = 非空记录行的 1-based 行号
        val lines = listOf("", docSample, realSample, docSample)
        val tmp = java.io.File.createTempFile("digiplu", ".dat")
        tmp.writeText(lines.joinToString("\r\n"), Charsets.ISO_8859_1)
        try {
            val list = DigiPluFileParser.parseFileIndexed(tmp)
            assertEquals(3, list.size)
            assertEquals(1 to 1661, list[0].first to list[0].second.pluNo)
            assertEquals(2 to 1, list[1].first to list[1].second.pluNo)
            assertEquals(3, list[2].first)
        } finally {
            tmp.delete()
        }
    }
}
