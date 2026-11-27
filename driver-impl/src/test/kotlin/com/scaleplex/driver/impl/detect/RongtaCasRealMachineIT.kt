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

    /** 时间同步：容大未接入（抛 UnsupportedOperationException），验证驱动返回明确错误而非崩溃。 */
    @Test
    fun `rongta sync time not wired behavior`() = runBlocking {
        val rongta = RongtaUdpDriver(rongta()).syncTime()
        println("rongta syncTime: $rongta")
        check(rongta.isFailure) { "容大时间同步应返回失败结果（待接入）" }
        check(rongta.exceptionOrNull() is UnsupportedOperationException) { "容大应抛 UnsupportedOperationException" }
    }

    /** 凯士读取秤内时间（真机：UL 帧 R45F01,0001. → Y/M/D/h/m/s 回包）。 */
    @Test
    fun `cas read time`() = runBlocking {
        val drv = CasClDriver(cas())
        val t = drv.readTime()
        println("cas readTime: $t")
        check(t.isSuccess) { "凯士读时间失败: ${t.exceptionOrNull()?.message}" }
    }

    /** 凯士时间同步（真机：W45 帧 → ACK W45:O01 → 回读验证）。 */
    @Test
    fun `cas sync time`() = runBlocking {
        val drv = CasClDriver(cas())
        val synced = drv.syncTime()
        println("cas syncTime: $synced")
        check(synced.isSuccess) { "凯士时间同步失败: ${synced.exceptionOrNull()?.message}" }
        val t = drv.readTime()
        check(t.isSuccess) { "同步后读时间失败: ${t.exceptionOrNull()?.message}" }
        val now = java.time.LocalDateTime.now()
        val scaleTime = t.getOrThrow()
        val driftSec = java.time.Duration.between(scaleTime, now).abs().seconds
        println("cas scaleTime=$scaleTime pcTime=$now drift=${driftSec}s")
        check(driftSec <= 120) { "同步后时钟偏差过大: scale=$scaleTime pc=$now" }
    }
}
