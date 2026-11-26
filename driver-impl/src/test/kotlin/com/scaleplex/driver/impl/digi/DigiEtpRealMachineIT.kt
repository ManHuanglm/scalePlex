package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 寺冈 SM-120（ETP 族，FTP+CSV）真机集成测试 @ 192.168.0.56:21。
 * 临时文件 —— 秤不可达时自动跳过；不进入常规测试集。
 *
 * 前提：秤上基线为厂商示例商品（PLU 1=特色小果苹果、PLU 2=apple）+ 空闲槽标记。
 */
class DigiEtpRealMachineIT {

    private fun device() = ScaleDevice(
        id = 3,
        name = "SM-120",
        brand = ScaleBrand.DIGI,
        model = "SM-120",
        commType = CommType.TCP,
        ip = "192.168.0.56",
        port = 21,
    )

    @Test
    fun `etp driver workflow against real sm120`() = runBlocking {
        val drv = DigiSmDriver(device())

        // 1. connect + 在线探测（FTP 可登录即在线）
        val conn = drv.connect()
        println("connect: $conn")
        check(conn.isSuccess) { "connect 失败: ${conn.exceptionOrNull()?.message}" }
        val status = drv.getStatus()
        println("status: $status")
        if (status.getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("真机不可达，跳过: $status", false)
        }

        // 2. 回收校验（厂商示例数据）
        val recall = drv.uploadPlu()
        val plus = recall.getOrDefault(emptyList())
        println("recalled: ${plus.map { "${it.pluNo}/${it.displayName}/${it.eanCode}" }}")
        check(recall.isSuccess) { "回收失败: ${recall.exceptionOrNull()?.message}" }
        val p1 = plus.firstOrNull { it.pluNo == 1 }
        check(p1 != null && p1.displayName.contains("特色小果苹果")) { "PLU1 回收校验失败: $p1" }
        check(plus.any { it.pluNo == 2 && it.displayName == "apple" }) { "PLU2 回收校验失败" }
        check(plus.none { it.pluNo >= 999990 }) { "空闲槽标记应被过滤" }

        // 3. 写操作（下发/删除/时间同步）预期提示未接入
        val down = drv.downloadPluBatch(
            listOf(com.scaleplex.core.model.PluItem(pluNo = 900, name = "测试", unitPrice = 100)),
        )
        println("downloadPluBatch (expected unsupported): ${down.exceptionOrNull()?.message}")
        check(down.isFailure) { "ETP 下发应明确提示未接入" }
        val time = drv.syncTime()
        println("syncTime (expected unsupported): ${time.exceptionOrNull()?.message}")
        check(time.isFailure) { "ETP 时间同步应明确提示未接入" }

        Unit
    }
}
