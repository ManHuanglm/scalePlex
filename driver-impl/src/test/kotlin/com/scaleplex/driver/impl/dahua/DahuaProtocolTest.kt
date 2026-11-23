package com.scaleplex.driver.impl.dahua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DahuaProtocolTest {

    @Test
    fun `frame appends command fields and crlf`() {
        val frame = DahuaProtocol.frame(DahuaProtocol.CMD_SET_PLU, listOf("1", "苹果"))
        val text = frame.toString(charset("GBK"))
        assertEquals("SETPLU,1,苹果\r\n", text)
        assertEquals(0x0D.toByte(), frame[frame.size - 2])
        assertEquals(0x0A.toByte(), frame[frame.size - 1])
    }

    @Test
    fun `frame without fields sends bare command`() {
        val frame = DahuaProtocol.frame(DahuaProtocol.CMD_STATUS)
        assertEquals("STATUS\r\n", frame.toString(charset("GBK")))
    }

    @Test
    fun `parseAck maps ok err and no reply`() {
        val gbk = charset("GBK")
        assertEquals(DahuaProtocol.Ack.Ok, DahuaProtocol.parseAck("OK".toByteArray(gbk)))
        assertEquals(DahuaProtocol.Ack.Ok, DahuaProtocol.parseAck("OK,3".toByteArray(gbk)))
        assertEquals(
            DahuaProtocol.Ack.Err("bad price"),
            DahuaProtocol.parseAck("ERR,bad price".toByteArray(gbk)),
        )
        assertEquals(
            DahuaProtocol.Ack.Err("unknown"),
            DahuaProtocol.parseAck("unknown".toByteArray(gbk)),
        )
        assertEquals(DahuaProtocol.Ack.NoReply, DahuaProtocol.parseAck(null))
        assertEquals(DahuaProtocol.Ack.NoReply, DahuaProtocol.parseAck(ByteArray(0)))
    }

    @Test
    fun `decodeTradeLog skips invalid lines and maps fields`() {
        val text = "T,7,260915123001,3,苹果,250,1250,31250,cashier1\r\nBADLINE\r\n\r\n" +
            "T,8,260915123101,4,香蕉,0,500,1000,"
        val records = DahuaProtocol.decodeTradeLog(text.toByteArray(charset("GBK")), deviceId = 9L)
        assertEquals(2, records.size)
        val first = records[0]
        assertEquals(7L, first.recordNo)
        assertEquals(9L, first.deviceId)
        assertEquals(3, first.pluNo)
        assertEquals("苹果", first.pluName)
        assertEquals(250, first.weight)
        assertEquals(1250L, first.unitPrice)
        assertEquals(31250L, first.amount)
        assertEquals("cashier1", first.operator)
        assertEquals(2026, first.timestamp?.year)
        assertEquals(9, first.timestamp?.monthNumber)
        // 不合规行（缺字段）被跳过，短行 operator 为空
        assertEquals("", records[1].operator)
    }

    @Test
    fun `decodeTradeLog handles null and empty reply`() {
        assertTrue(DahuaProtocol.decodeTradeLog(null, 1L).isEmpty())
        assertTrue(DahuaProtocol.decodeTradeLog(ByteArray(0), 1L).isEmpty())
    }
}
