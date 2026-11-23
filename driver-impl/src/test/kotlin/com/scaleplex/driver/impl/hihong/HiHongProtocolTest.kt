package com.scaleplex.driver.impl.hihong

import org.junit.Assert.assertEquals
import org.junit.Test

class HiHongProtocolTest {

    @Test
    fun `frame layout for empty body`() {
        // len = op + body = 1 -> [02, 11, 01, 00, chk, 03]
        // sum = 0x11 + 0x01 + 0x00 = 0x12, 二补码低字节 = 0xEE
        val frame = HiHongProtocol.frame(HiHongProtocol.OP_DOWNLOAD_PLU)
        assertEquals(
            listOf(0x02, 0x11, 0x01, 0x00, 0xEE, 0x03),
            frame.map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun `frame checksum covers body bytes`() {
        // op=0x12, body=[0x01, 0x00]: len=3, sum=0x12+0x03+0x00+0x01+0x00=0x16 -> chk=0xEA
        val frame = HiHongProtocol.frame(HiHongProtocol.OP_DELETE_PLU, byteArrayOf(0x01, 0x00))
        assertEquals(
            listOf(0x02, 0x12, 0x03, 0x00, 0x01, 0x00, 0xEA, 0x03),
            frame.map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun `frame handles multi byte length little endian`() {
        val body = ByteArray(300) { 0x00 }
        val frame = HiHongProtocol.frame(HiHongProtocol.OP_DOWNLOAD_PLU, body)
        // len = 301 = 0x012D -> 低字节 0x2D, 高字节 0x01
        assertEquals(0x2D, frame[2].toInt() and 0xFF)
        assertEquals(0x01, frame[3].toInt() and 0xFF)
    }

    @Test
    fun `parseAck maps ok nak and no reply`() {
        assertEquals(HiHongProtocol.Ack.Ok, HiHongProtocol.parseAck(byteArrayOf(0x02, 0x06, 0x03)))
        assertEquals(
            HiHongProtocol.Ack.Err(0x15),
            HiHongProtocol.parseAck(byteArrayOf(0x02, 0x15, 0x03)),
        )
        assertEquals(HiHongProtocol.Ack.NoReply, HiHongProtocol.parseAck(null))
        assertEquals(HiHongProtocol.Ack.NoReply, HiHongProtocol.parseAck(byteArrayOf(0x02)))
    }
}
