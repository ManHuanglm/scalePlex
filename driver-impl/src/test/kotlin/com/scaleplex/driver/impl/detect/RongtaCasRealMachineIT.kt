package com.scaleplex.driver.impl.detect

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.driver.impl.cas.CasClDriver
import com.scaleplex.driver.impl.rongta.RongtaUdpDriver
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 容大/凯士真机驱动在线探测测试（ScalePlex 已入库设备）。
 * 秤不可达时自动跳过；不进入常规测试集。
 */
class RongtaCasRealMachineIT {

    private fun rongta() = ScaleDevice(
        id = 7,
        name = "容大 RLS1000",
        brand = ScaleBrand.RONGTA,
        model = "RLS1000",
        commType = CommType.UDP,
        ip = "192.168.0.96",
        port = 5001,
    )

    private fun cas() = ScaleDevice(
        id = 8,
        name = "凯士 CL5200N",
        brand = ScaleBrand.CAS,
        model = "CL5200N",
        commType = CommType.TCP,
        ip = "192.168.0.111",
        port = 20304,
    )

    @Test
    fun `rongta udp alive probe`() = runBlocking {
        val drv = RongtaUdpDriver(rongta())
        val status = drv.getStatus()
        if (status.getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("容大秤 192.168.0.96 不可达，跳过: $status", false)
        }
        println("rongta status: $status")
    }

    @Test
    fun `cas probe via cl-works bridge`() = runBlocking {
        val drv = CasClDriver(cas())
        val status = drv.getStatus()
        println("cas status: $status")
        println("cas failure: ${status.exceptionOrNull()?.message}")
        if (status.getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("凯士秤 192.168.0.111 不可达，跳过", false)
        }
    }
}
