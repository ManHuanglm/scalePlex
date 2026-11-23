package com.scaleplex.driver.impl.yousheng

import org.junit.Assert.assertEquals
import org.junit.Test

class YouShengProtocolTest {

    @Test
    fun `frame layout with bcc xor checksum`() {
        // cmd='W'(0x57), data=[0x01, 0x02]:
        // len=2 -> BCC = 0x57 ^ 0x02 ^ 0x00 ^ 0x01 ^ 0x02 = 0x56
        val frame = YouShengProtocol.frame(YouShengProtocol.CMD_WRITE_PLU, byteArrayOf(0x01, 0x02))
        assertEquals(
            listOf(0x02, 0x57, 0x02, 0x00, 0x01, 0x02, 0x56, 0x03),
            frame.map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun `frame layout for empty body`() {
        // cmd='S'(0x53), len=0 -> BCC = 0x53 ^ 0x00 ^ 0x00 = 0x53
        val frame = YouShengProtocol.frame(YouShengProtocol.CMD_READ_STATUS)
        assertEquals(
            listOf(0x02, 0x53, 0x00, 0x00, 0x53, 0x03),
            frame.map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun `bcc is stable regardless of data byte order`() {
        val a = YouShengProtocol.frame(0x57, byteArrayOf(0x0F, 0xF0.toByte()))
        val b = YouShengProtocol.frame(0x57, byteArrayOf(0xF0.toByte(), 0x0F))
        // XOR 与顺序无关，两帧 BCC 相同
        assertEquals(a[a.size - 2], b[b.size - 2])
    }

    @Test
    fun `parseAck maps ok nak and no reply`() {
        assertEquals(YouShengProtocol.Ack.Ok, YouShengProtocol.parseAck(byteArrayOf(0x02, 0x06, 0x03)))
        assertEquals(
            YouShengProtocol.Ack.Err(0x15),
            YouShengProtocol.parseAck(byteArrayOf(0x02, 0x15, 0x03)),
        )
        assertEquals(YouShengProtocol.Ack.NoReply, YouShengProtocol.parseAck(null))
        assertEquals(YouShengProtocol.Ack.NoReply, YouShengProtocol.parseAck(byteArrayOf(0x02)))
    }
}
