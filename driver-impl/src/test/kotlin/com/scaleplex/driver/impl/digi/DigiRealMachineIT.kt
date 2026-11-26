package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 真机集成测试（SM-110N STE57+ @ 192.168.0.55，b 族 SMScaleAPI 协议）。
 * 临时文件 —— 验证后删除，不进入常规测试集。
 *
 * 前提：秤上基线 PLU = [2,3,4,5]（芥兰/红萝卜/金银甜玉米/西兰花）+ 1 个空闲槽。
 */
class DigiRealMachineIT {

    private fun device() = ScaleDevice(
        id = 1,
        name = "SM-110N",
        brand = ScaleBrand.DIGI,
        model = "SM-110N",
        commType = CommType.TCP,
        ip = "192.168.0.55",
        port = 55,
    )

    @Test
    fun `full driver workflow against real scale`() = runBlocking {
        System.setProperty(DigiToolsBridge.TOOLS_PATH_PROPERTY, "D:\\Program Files\\ScalePlex\\data\\tools\\digi")
        val drv = DigiSmDriver(device())

        // 真机不可达时跳过（避免无秤环境误报失败）
        val probe = drv.getStatus()
        if (probe.getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("真机不可达，跳过: $probe", false)
        }

        // 1. connect
        val conn = drv.connect()
        println("connect: $conn")
        check(conn.isSuccess) { "connect 失败: ${conn.exceptionOrNull()?.message}" }

        // 2. status probe
        val status = drv.getStatus()
        println("status: $status")
        check(status.getOrNull()?.online == true) { "探测失败: $status" }

        // 基线
        val baseline = drv.uploadPlu().getOrDefault(emptyList())
        println("baseline PLUs: ${baseline.map { "${it.pluNo}/${it.unitPrice}/${it.displayName}" }}")
        // 基线为空 = 秤处于限流/锁定或被清空状态，真机测试跳过（避免误报回归）
        if (baseline.isEmpty()) {
            org.junit.Assume.assumeTrue("秤基线为空（限流/锁定/已清空），跳过真机验证", false)
        }
        val baselineNums = baseline.map { it.pluNo }.toSet()
        check(baselineNums == setOf(2, 3, 4, 5)) { "基线异常: $baselineNums" }

        // 3. 批量下发 900（空闲槽，应落秤）+ 901（超出分配长度，入队不落秤）
        val plus = listOf(
            PluItem(
                pluNo = 900, name = "集成测试A", unitPrice = 999, eanCode = "0000090000",
                commodityLines = listOf(PluTextLine(text = "集成测试A")),
            ),
            PluItem(
                pluNo = 901, name = "集成测试B", unitPrice = 2500, eanCode = "0000090100",
                commodityLines = listOf(PluTextLine(text = "集成测试B")),
            ),
        )
        val down = drv.downloadPluBatch(plus)
        val dr = down.getOrNull()
        println("downloadPluBatch: total=${dr?.total} success=${dr?.success} failed=${dr?.failed} err=${dr?.errors?.map { "${it.pluNo}:${it.message}" }}")
        check(dr != null && dr.success == 1 && dr.failed == 1) { "下发结果异常: $dr" }
        check(dr.errors.any { it.pluNo == 901 && it.message.contains("落秤校验未通过") }) { "901 应报落秤校验未通过: ${dr.errors}" }

        // 4. 回读校验：900 落秤、901 未落秤
        val after = drv.uploadPlu().getOrDefault(emptyList())
        val p900 = after.firstOrNull { it.pluNo == 900 }
        println("after download: ${after.map { "${it.pluNo}/${it.unitPrice}/${it.displayName}" }}")
        check(p900 != null && p900.unitPrice == 999L && p900.displayName == "集成测试A" && p900.eanCode == "0000090000") { "900 校验失败: $p900" }
        check(after.none { it.pluNo == 901 }) { "901 不应已落秤" }

        // 5. 删除 900（主文件删除会触发秤端队列弹出，901 将落秤）
        val del = drv.deletePlu(900)
        println("deletePlu(900): $del")
        check(del.isSuccess) { "删除失败: ${del.exceptionOrNull()?.message}" }
        val afterDel = drv.uploadPlu().getOrDefault(emptyList())
        println("after delete 900 (queue flush): ${afterDel.map { "${it.pluNo}/${it.unitPrice}/${it.displayName}" }}")
        check(afterDel.any { it.pluNo == 901 }) { "901 应随队列弹出落秤" }

        // 6. 删除 901 → 恢复基线
        val del2 = drv.deletePlu(901)
        println("deletePlu(901): $del2")
        check(del2.isSuccess) { "删除 901 失败: ${del2.exceptionOrNull()?.message}" }

        // 7. 时间同步
        val time = drv.syncTime()
        println("syncTime: $time")
        check(time.isSuccess) { "时间同步失败: ${time.exceptionOrNull()?.message}" }

        // 8. 已知未接入能力（预期失败，不阻断）
        println("readLabelFormats: ${drv.readLabelFormats().exceptionOrNull()?.message}")
        println("readTradeLog: ${drv.readTradeLog().exceptionOrNull()?.message}")
        println("downloadLabelTemplate: ${drv.downloadLabelTemplate(com.scaleplex.core.model.LabelTemplate(name = "t")).exceptionOrNull()?.message}")

        // 9. 最终基线一致
        val finalNums = drv.uploadPlu().getOrDefault(emptyList()).map { it.pluNo }.toSet()
        println("final PLUs: $finalNums")
        check(finalNums == baselineNums) { "基线不一致: was=$baselineNums now=$finalNums" }
        Unit
    }
}
