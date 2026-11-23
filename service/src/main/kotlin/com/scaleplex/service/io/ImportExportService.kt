package com.scaleplex.service.io

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.SerialParams
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Excel (.xlsx) and CSV import/export for PLUs and the device inventory.
 * Provides template downloads matching the DIGI CSV layout plus a friendly Excel
 * template for users on WPS / Office.
 */
object ImportExportService {

    // ---------------- PLU Excel ----------------
    private val PLU_XLSX_HEADER = listOf(
        "PLU_No", "Name", "Name_EN", "Weight_Flag", "Main_Group", "Unit_Price", "Cost",
        "Barcode_Type", "Barcode_Format", "Flag_Data", "EAN_Code", "Tare", "Qty",
        "Label1_Format", "Label2_Format", "Place_No", "Tax_Rate_No", "Used_By_Date",
    )

    fun exportPluXlsx(file: File, items: List<PluItem>, priceDecimals: Int = 2) {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("PLU")
            val header = sheet.createRow(0)
            PLU_XLSX_HEADER.forEachIndexed { i, t -> header.createCell(i).setCellValue(t) }
            items.forEachIndexed { r, p ->
                val row = sheet.createRow(r + 1)
                row.cell(0).setCellValue(p.pluNo.toDouble())
                row.cell(1).setCellValue(p.name)
                row.cell(2).setCellValue(p.nameEn)
                row.cell(3).setCellValue(p.weightFlag.toDouble())
                row.cell(4).setCellValue(p.mainGroup.toDouble())
                row.cell(5).setCellValue(com.scaleplex.core.util.Numbers.minorToDisplay(p.unitPrice, priceDecimals))
                row.cell(6).setCellValue(com.scaleplex.core.util.Numbers.minorToDisplay(p.cost, priceDecimals))
                row.cell(7).setCellValue(p.barcodeType.toDouble())
                row.cell(8).setCellValue(p.barcodeFormat.toDouble())
                row.cell(9).setCellValue(p.flagData)
                row.cell(10).setCellValue(p.eanCode)
                row.cell(11).setCellValue(p.tare.toDouble())
                row.cell(12).setCellValue(p.qty.toDouble())
                row.cell(13).setCellValue(p.label1Format.toDouble())
                row.cell(14).setCellValue(p.label2Format.toDouble())
                row.cell(15).setCellValue(p.placeNo.toDouble())
                row.cell(16).setCellValue(p.taxRateNo.toDouble())
                row.cell(17).setCellValue(p.usedByDate.toDouble())
            }
            FileOutputStream(file).use { wb.write(it) }
        }
    }

    fun importPluXlsx(file: File, priceDecimals: Int = 2): List<PluItem> {
        val items = mutableListOf<PluItem>()
        FileInputStream(file).use { fis ->
            XSSFWorkbook(fis).use { wb ->
                val sheet = wb.getSheetAt(0)
                var headerSkipped = false
                for (row in sheet) {
                    if (!headerSkipped) { headerSkipped = true; continue }
                    val no = row.num(0)
                    if (no <= 0) continue
                    items += PluItem(
                        pluNo = no.toInt(),
                        name = row.str(1),
                        nameEn = row.str(2),
                        weightFlag = row.num(3).toInt(),
                        mainGroup = if (row.num(4) > 0) row.num(4).toInt() else 997,
                        unitPrice = com.scaleplex.core.util.Numbers.displayToMinor(row.str(5), priceDecimals),
                        cost = com.scaleplex.core.util.Numbers.displayToMinor(row.str(6), priceDecimals),
                        barcodeType = row.num(7).toInt(),
                        barcodeFormat = row.num(8).toInt(),
                        flagData = row.str(9),
                        eanCode = row.str(10),
                        tare = row.num(11).toInt(),
                        qty = row.num(12).toInt(),
                        label1Format = row.num(13).toInt(),
                        label2Format = row.num(14).toInt(),
                        placeNo = row.num(15).toInt(),
                        taxRateNo = row.num(16).toInt(),
                        usedByDate = row.num(17).toInt(),
                    )
                }
            }
        }
        return items
    }

    // ---------------- Device Excel ----------------
    private val DEV_HEADER = listOf(
        "Name", "Brand", "Model", "Comm_Type", "IP", "Port",
        "Serial_Port", "Baud", "Group_Id",
    )

    fun exportDeviceXlsx(file: File, devices: List<ScaleDevice>) {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Devices")
            DEV_HEADER.forEachIndexed { i, t -> sheet.createRow(0).createCell(i).setCellValue(t) }
            devices.forEachIndexed { r, d ->
                val row = sheet.createRow(r + 1)
                row.cell(0).setCellValue(d.name)
                row.cell(1).setCellValue(d.brand.id)
                row.cell(2).setCellValue(d.model)
                row.cell(3).setCellValue(d.commType.id)
                row.cell(4).setCellValue(d.ip)
                row.cell(5).setCellValue(d.port.toDouble())
                row.cell(6).setCellValue(d.serial.portName)
                row.cell(7).setCellValue(d.serial.baudRate.toDouble())
                row.cell(8).setCellValue(d.groupId.toDouble())
            }
            FileOutputStream(file).use { wb.write(it) }
        }
    }

    fun importDeviceXlsx(file: File): List<ScaleDevice> {
        val out = mutableListOf<ScaleDevice>()
        FileInputStream(file).use { fis ->
            XSSFWorkbook(fis).use { wb ->
                val sheet = wb.getSheetAt(0)
                var skipped = false
                for (row in sheet) {
                    if (!skipped) { skipped = true; continue }
                    val name = row.str(0)
                    if (name.isBlank()) continue
                    out += ScaleDevice(
                        name = name,
                        brand = ScaleBrand.fromId(row.str(1).ifBlank { "digi" }),
                        model = row.str(2),
                        commType = CommType.fromId(row.str(3).ifBlank { "udp" }),
                        ip = row.str(4),
                        port = row.num(5).toInt().let { if (it == 0) 1111 else it },
                        serial = SerialParams(portName = row.str(6).ifBlank { "COM1" },
                            baudRate = row.num(7).toInt().let { if (it == 0) 9600 else it }),
                        groupId = row.num(8).toLong(),
                    )
                }
            }
        }
        return out
    }

    /** Write the friendly PLU Excel template (header + one example row). */
    fun writePluTemplate(file: File) {
        val sample = PluItem(pluNo = 1, name = "示例商品", nameEn = "Sample", unitPrice = 999,
            mainGroup = 997, weightFlag = 0, eanCode = "2000000000000")
        exportPluXlsx(file, listOf(sample))
    }

    // ---------------- helpers ----------------
    private fun Row.cell(i: Int): Cell = getCell(i) ?: createCell(i)

    private fun Row.num(i: Int): Double =
        runCatching { getCell(i)?.numericCellValue ?: 0.0 }.getOrDefault(0.0)

    private fun Row.str(i: Int): String {
        val c = getCell(i) ?: return ""
        return when (c.cellType) {
            org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                if (c.numericCellValue == c.numericCellValue.toLong().toDouble())
                    c.numericCellValue.toLong().toString() else c.numericCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> c.booleanCellValue.toString()
            else -> c.stringCellValue ?: ""
        }
    }
}
