package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** 临时：恢复 SM-110N（.55）重启后丢失的 4 条示例商品。 */
class Digi55RestoreTest {

    @Test
    fun `restore seed plus to sm110n`() = runBlocking {
        System.setProperty(DigiToolsBridge.TOOLS_PATH_PROPERTY, "D:\\Program Files\\ScalePlex\\data\\tools\\digi")
        val drv = DigiSmDriver(
            ScaleDevice(
                id = 1, name = "SM-110N", brand = ScaleBrand.DIGI, model = "SM-110N",
                commType = CommType.TCP, ip = "192.168.0.55", port = 55,
            ),
        )
        val plus = listOf(
            PluItem(
                pluNo = 2, name = "芥兰", unitPrice = 1180, eanCode = "0000200000",
                mainGroup = 997, usedByDate = 3, label1Format = 17, barcodeFormat = 5,
                commodityLines = listOf(PluTextLine(21, "芥兰")),
            ),
            PluItem(
                pluNo = 3, name = "红萝卜", unitPrice = 480, eanCode = "0000300000",
                mainGroup = 997, usedByDate = 3, label1Format = 17, barcodeFormat = 5,
                commodityLines = listOf(PluTextLine(21, "红萝卜")),
            ),
            PluItem(
                pluNo = 4, name = "金银甜玉米", unitPrice = 998, eanCode = "0000400000",
                mainGroup = 997, usedByDate = 3, label1Format = 17, barcodeFormat = 5,
                commodityLines = listOf(PluTextLine(21, "金银甜玉米")),
            ),
            PluItem(
                pluNo = 5, name = "西兰花", unitPrice = 1180, eanCode = "0000500000",
                mainGroup = 997, usedByDate = 3, label1Format = 17, barcodeFormat = 5,
                commodityLines = listOf(PluTextLine(21, "西兰花")),
            ),
        )
        val down = drv.downloadPluBatch(plus)
        val dr = down.getOrNull()
        println("download: total=${dr?.total} success=${dr?.success} failed=${dr?.failed} err=${dr?.errors?.map { it.message }}")
        println("download exception: ${down.exceptionOrNull()?.message}")
        val recallRes = drv.uploadPlu()
        val recall = recallRes.getOrDefault(emptyList())
        println("recalled: ${recall.map { "${it.pluNo}/${it.unitPrice}/${it.displayName}" }}")
        println("recall exception: ${recallRes.exceptionOrNull()?.message}")
        Unit
    }
}
