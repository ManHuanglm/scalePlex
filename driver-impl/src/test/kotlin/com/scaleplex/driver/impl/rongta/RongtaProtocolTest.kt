package com.scaleplex.driver.impl.rongta

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 容大协议帧布局单元测试：锁定抓包标定字节，防止偏移回归（2026-11-27 真机教训）。 */
class RongtaProtocolTest {

    private val driver = RongtaUdpDriver(
        ScaleDevice(
            id = 7,
            name = "容大 RLS1000",
            brand = ScaleBrand.RONGTA,
            model = "RLS1000",
            commType = CommType.UDP,
            ip = "192.168.0.96",
            port = 5001,
        ),
    )

    /** 会话开场帧（兼校时帧）= RTPLU 抓包模板：[0E 01 00 34 12 00 00] + BCD(年月日时分秒)@7..12 + 0 填充。 */
    @Test
    fun `session frame matches captured template byte layout`() {
        val f = driver.sessionFrame(LocalDateTime.of(2026, 11, 27, 14, 5, 9))
        assertTrue("帧长应为 257，实际 ${f.size}", f.size == 257)
        val expect = byteArrayOf(
            0x0E, 0x01, 0x00, 0x34, 0x12, 0x00, 0x00,
            0x26, 0x11, 0x27, 0x14, 0x05, 0x09,
        )
        for (i in expect.indices) {
            assertTrue("offset $i: 期望 ${expect[i]} 实际 ${f[i]}", expect[i] == f[i])
        }
        assertTrue("偏移 13 之后必须全 0 填充", f.drop(13).all { it == 0.toByte() })
    }
}
