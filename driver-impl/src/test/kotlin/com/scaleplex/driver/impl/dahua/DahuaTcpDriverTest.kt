package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * 大华校时帧格式回归（真机标定 2026-11-28：`!0P{yyMMddHHmm}\r\n`，ACK=`0p\r\n`+0x03）。
 */
class DahuaTcpDriverTest {

    private fun driver() = DahuaTcpDriver(
        ScaleDevice(name = "test", brand = ScaleBrand.DAHUA, commType = com.scaleplex.core.model.CommType.TCP),
    )

    @Test
    fun `time frame uses yyMMddHHmm without seconds`() {
        val cal: Calendar = GregorianCalendar(2026, Calendar.NOVEMBER, 28, 8, 47, 13)
        val frame = driver().timeFrame(cal.time)
        assertEquals("!0P2611280847\r\n", String(frame, Charsets.US_ASCII))
        assertEquals(15, frame.size)
    }

    @Test
    fun `time frame pads month day hour and minute`() {
        val cal: Calendar = GregorianCalendar(2026, Calendar.JANUARY, 5, 3, 7, 0)
        assertEquals("!0P2601050307\r\n", String(driver().timeFrame(cal.time), Charsets.US_ASCII))
    }
}
