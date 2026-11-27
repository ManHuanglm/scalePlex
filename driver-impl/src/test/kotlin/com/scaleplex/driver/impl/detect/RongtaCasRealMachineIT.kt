package com.scaleplex.driver.impl.detect

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.driver.impl.cas.CasClDriver
import com.scaleplex.driver.impl.rongta.RongtaUdpDriver
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * 容大/凯士真机驱动在线探测测试（ScalePlex 已入库设备）。
 * 秤不可达时自动跳过；不进入常规测试集。
 */
class RongtaCasRealMachineIT {

    init {
        // 帧生成回退需要数据目录（ plustruct.str 副本位于 <data>/tools/cas/config）；
        // gradle test 工作目录是模块目录，data 在项目根
        System.setProperty("scaleplex.data.dir", File("../data").canonicalPath)
    }

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

    /** 与用户经原厂软件下发过的数据一致（幂等，不破坏秤端现状）。 */
    private fun baiCai() = PluItem(
        pluNo = 1,
        name = "大白菜",
        mainGroup = 24,
        unitPrice = 2000,
        eanCode = "99",
        usedByDate = 15,
    )

    @Test
    fun `rongta plu download`() = runBlocking {
        val drv = RongtaUdpDriver(rongta())
        if (drv.getStatus().getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("容大秤 192.168.0.96 不可达，跳过", false)
        }
        val r = drv.downloadPluBatch(listOf(baiCai()))
        println("rongta download: $r")
        check(r.isSuccess) { "容大下发失败: ${r.exceptionOrNull()?.message}" }
    }

    @Test
    fun `cas plu download`() = runBlocking {
        val drv = CasClDriver(cas())
        if (drv.getStatus().getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("凯士秤 192.168.0.111 不可达，跳过", false)
        }
        val r = drv.downloadPluBatch(listOf(baiCai().copy(mainGroup = 1)))
        println("cas download: $r")
        check(r.isSuccess) { "凯士下发失败: ${r.exceptionOrNull()?.message}" }
    }

    /** 批量测试数据：PLU 1 幂等复跑，PLU 2/3 覆盖秤内基线同号商品（香菜/小白菜）。 */
    private fun batchPlus() = listOf(
        baiCai(),
        baiCai().copy(pluNo = 2, name = "香菜", unitPrice = 850),
        baiCai().copy(pluNo = 3, name = "小白菜", unitPrice = 500),
    )

    @Test
    fun `rongta multi plu download`() = runBlocking {
        val drv = RongtaUdpDriver(rongta())
        if (drv.getStatus().getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("容大秤 192.168.0.96 不可达，跳过", false)
        }
        val r = drv.downloadPluBatch(batchPlus())
        println("rongta multi download: $r")
        check(r.isSuccess) { "容大多条下发失败: ${r.exceptionOrNull()?.message}" }
        check(r.getOrThrow().success == 3) { "容大多条下发数量不符: ${r.getOrThrow()}" }
    }

    @Test
    fun `cas multi plu download`() = runBlocking {
        val drv = CasClDriver(cas())
        if (drv.getStatus().getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("凯士秤 192.168.0.111 不可达，跳过", false)
        }
        val r = drv.downloadPluBatch(batchPlus().map { it.copy(mainGroup = 1) })
        println("cas multi download: $r")
        check(r.isSuccess) { "凯士多条下发失败: ${r.exceptionOrNull()?.message}" }
        check(r.getOrThrow().success == 3) { "凯士多条下发数量不符: ${r.getOrThrow()}" }
    }

    /** 时间同步当前两品牌均未接入，验证驱动返回明确的 UnsupportedOperationException 而非崩溃。 */
    @Test
    fun `sync time not wired behavior`() = runBlocking {
        val rongta = RongtaUdpDriver(rongta()).syncTime()
        val cas = CasClDriver(cas()).syncTime()
        println("rongta syncTime: $rongta")
        println("cas syncTime: $cas")
        check(rongta.isFailure && cas.isFailure) { "时间同步应返回失败结果（待接入）" }
        check(rongta.exceptionOrNull() is UnsupportedOperationException) { "容大应抛 UnsupportedOperationException" }
        check(cas.exceptionOrNull() is UnsupportedOperationException) { "凯士应抛 UnsupportedOperationException" }
    }
}
