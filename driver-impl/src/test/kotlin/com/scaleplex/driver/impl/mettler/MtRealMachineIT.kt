package com.scaleplex.driver.impl.mettler

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 托利多（MTScaleKey 服务模式）真机集成测试。临时文件 —— 秤不可达时自动跳过。
 * 可通过系统属性指定目标秤（默认 RL00 @192.168.0.66）：
 * `-Dmt.it.id=4 -Dmt.it.model=bComS -Dmt.it.ip=192.168.0.223 -Dmt.it.name=bComS -Dmt.it.plu=952`
 */
class MtRealMachineIT {

    private val deviceId = System.getProperty("mt.it.id", "2").toLong()
    private val model = System.getProperty("mt.it.model", "RL00")
    private val ip = System.getProperty("mt.it.ip", "192.168.0.66")
    private val name = System.getProperty("mt.it.name", "3610")
    private val testPluNo = System.getProperty("mt.it.plu", "950").toInt()

    private fun device() = ScaleDevice(
        id = deviceId,
        name = name,
        brand = ScaleBrand.METTLER_TOLEDO,
        model = model,
        commType = CommType.TCP,
        ip = ip,
        port = 3001,
    )

    @Test
    fun `full driver workflow against real toledo`() = runBlocking {
        System.setProperty(
            MtScaleKeyBridge.DLL_PATH_PROPERTY,
            "D:\\Program Files\\ScalePlex\\data\\tools\\mettler\\MTScaleAPI.dll",
        )
        println("target: $model @$ip (deviceId=$deviceId, testPlu=$testPluNo)")
        val drv = MettlerToledoDriver(device())

        // 1. 在线探测（TaskType=3 CheckOnline）
        val status = drv.getStatus()
        println("status: $status")
        if (status.getOrNull()?.online != true) {
            org.junit.Assume.assumeTrue("真机不可达，跳过: $status", false)
        }

        // 2. 基线回收（自动清理上次运行遗留的测试 PLU）
        var baseline = drv.uploadPlu().getOrDefault(emptyList())
        if (baseline.any { it.pluNo == testPluNo }) {
            drv.deletePlu(testPluNo)
            baseline = drv.uploadPlu().getOrDefault(emptyList())
        }
        println("baseline PLUs: ${baseline.map { "${it.pluNo}/${it.unitPrice}/${it.displayName.trim()}" }}")
        val baselineNums = baseline.map { it.pluNo }.toSet()
        check(!baselineNums.contains(testPluNo)) { "秤上遗留测试 PLU $testPluNo 清理失败" }

        // 3. 下发测试 PLU
        val test = PluItem(
            pluNo = testPluNo, name = "MT测试品$testPluNo", unitPrice = 1550, eanCode = "0000000950",
            usedByDate = 3, label1Format = 1,
        )
        val down = drv.downloadPluBatch(listOf(test))
        val dr = down.getOrNull()
        println("downloadPluBatch: total=${dr?.total} success=${dr?.success} failed=${dr?.failed} err=${dr?.errors}")
        check(dr != null && dr.success == 1) { "下发失败: $down" }

        // 4. 回收校验
        val after = drv.uploadPlu().getOrDefault(emptyList())
        val pTest = after.firstOrNull { it.pluNo == testPluNo }
        println("after download: ${after.map { "${it.pluNo}/${it.unitPrice}/${it.displayName.trim()}" }}")
        check(pTest != null && pTest.unitPrice == 1550L && pTest.displayName.trim() == "MT测试品$testPluNo") { "$testPluNo 校验失败: $pTest" }

        // 5. 删除测试 PLU
        val del = drv.deletePlu(testPluNo)
        println("deletePlu($testPluNo): $del")
        check(del.isSuccess) { "删除失败: ${del.exceptionOrNull()?.message}" }
        val afterDel = drv.uploadPlu().getOrDefault(emptyList()).map { it.pluNo }
        println("after delete: $afterDel")
        check(!afterDel.contains(testPluNo)) { "$testPluNo 仍存在" }

        // 6. 时间同步
        val time = drv.syncTime()
        println("syncTime: $time")
        check(time.isSuccess) { "时间同步失败: ${time.exceptionOrNull()?.message}" }

        // 7. 基线一致
        val finalNums = drv.uploadPlu().getOrDefault(emptyList()).map { it.pluNo }.toSet()
        println("final PLUs: $finalNums")
        check(finalNums == baselineNums) { "基线不一致: was=$baselineNums now=$finalNums" }
        Unit
    }
}
