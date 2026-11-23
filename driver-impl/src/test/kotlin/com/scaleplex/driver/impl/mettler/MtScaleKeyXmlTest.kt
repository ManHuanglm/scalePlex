package com.scaleplex.driver.impl.mettler

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream

class MtScaleKeyXmlTest {

    private val device = ScaleDevice(
        id = 7, name = "托利多1号", brand = ScaleBrand.METTLER_TOLEDO,
        model = "bCom", commType = com.scaleplex.core.model.CommType.TCP,
        ip = "192.168.3.50", port = 3001,
    )

    private fun parseXml(xml: String) =
        DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun `task xml carries id type and device list file`() {
        val doc = parseXml(MtScaleKeyXml.buildTaskXml("T123", MtScaleKeyXml.TASK_TYPE_DOWNLOAD, "DeviceList.xml"))
        assertEquals("T123", doc.getElementsByTagName("TaskID").item(0).textContent)
        assertEquals("0", doc.getElementsByTagName("TaskType").item(0).textContent)
        assertEquals("DeviceList.xml", doc.getElementsByTagName("DataFile").item(0).textContent)
    }

    @Test
    fun `device list xml maps ip port model and command file`() {
        val doc = parseXml(MtScaleKeyXml.buildDeviceListXml(listOf(device), mapOf(7L to "Commands.xml")))
        assertEquals("7", doc.getElementsByTagName("DeviceID").item(0).textContent)
        assertEquals("bCom", doc.getElementsByTagName("ScaleType").item(0).textContent)
        val params = doc.getElementsByTagName("NetworkParams").item(0)
        assertEquals("192.168.3.50", params.attributes.getNamedItem("Address").nodeValue)
        assertEquals("3001", params.attributes.getNamedItem("Port").nodeValue)
        assertEquals("Commands.xml", doc.getElementsByTagName("DataFile").item(0).textContent)
    }

    @Test
    fun `item data xml encodes plu barcode name price and label format`() {
        val xml = MtScaleKeyXml.buildItemDataXml(
            listOf(PluItem(pluNo = 12, name = "苹果", nameEn = "Apple", unitPrice = 1130, usedByDate = 7, label1Format = 2)),
        )
        val doc = parseXml(xml)
        assertEquals("12", doc.getElementsByTagName("PLU").item(0).textContent)
        assertEquals("12", doc.getElementsByTagName("AlternativeItemID").item(0).textContent)
        val desc = doc.getElementsByTagName("Description").item(0)
        assertEquals("苹果", desc.textContent)
        assertEquals("ItemName", desc.attributes.getNamedItem("Type").nodeValue)
        val price = doc.getElementsByTagName("ItemPrice").item(0)
        assertEquals("11.30", price.textContent)
        assertEquals("KGM", price.attributes.getNamedItem("UnitOfMeasureCode").nodeValue)
        // 保质期 → Date BestBefore
        val date = doc.getElementsByTagName("Date").item(0)
        assertEquals("BestBefore", date.attributes.getNamedItem("Type").nodeValue)
        assertEquals("7", date.textContent)
        assertEquals("2", doc.getElementsByTagName("LabelFormatID").item(0).textContent)
    }

    @Test
    fun `count item uses PCS unit of measure`() {
        val xml = MtScaleKeyXml.buildItemDataXml(listOf(PluItem(pluNo = 1, name = "x", weightFlag = 1, unitPrice = 500)))
        val price = parseXml(xml).getElementsByTagName("ItemPrice").item(0)
        assertEquals("PCS", price.attributes.getNamedItem("UnitOfMeasureCode").nodeValue)
    }

    @Test
    fun `xml escapes special characters in names`() {
        val xml = MtScaleKeyXml.buildItemDataXml(listOf(PluItem(pluNo = 1, name = "A<B>\"C\"")))
        assertTrue(!xml.contains("<B>"))
        assertTrue(xml.contains("A&lt;B&gt;&quot;C&quot;"))
    }

    @Test
    fun `preset data xml maps hotkeys to plu destinations`() {
        val xml = MtScaleKeyXml.buildPresetDataXml(
            listOf(PluItem(pluNo = 9, name = "香蕉", hotKey = 2), PluItem(pluNo = 8, name = "苹果", hotKey = 1)),
        )
        val doc = parseXml(xml)
        assertEquals("PresetDefinition", doc.getElementsByTagName("PresetDefinition").item(0).nodeName)
        assertEquals(2, doc.getElementsByTagName("PresetKey").length)
        assertEquals("8", doc.getElementsByTagName("PLU").item(0).textContent)
    }

    @Test
    fun `task result parser maps scale status to results`() {
        val resultXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <MTTaskResult>
              <ScaleResult>
                <DeviceID>7</DeviceID>
                <ReturnCode>Success</ReturnCode>
                <Status>Complete</Status>
                <Percent>100</Percent>
                <CommandResults>
                  <CommandResult><CommandID>c1</CommandID><ReturnCode>Success</ReturnCode></CommandResult>
                </CommandResults>
              </ScaleResult>
              <ScaleResult>
                <DeviceID>8</DeviceID>
                <ReturnCode>ConnectError</ReturnCode>
                <Status>Error</Status>
                <Percent>0</Percent>
              </ScaleResult>
            </MTTaskResult>
        """.trimIndent()
        val results = MtScaleKeyXml.parseTaskResultXml(resultXml)
        assertEquals(2, results.size)
        assertEquals("Success", results[7L]?.returnCode)
        assertTrue(results[7L]!!.ok)
        assertTrue(!results[8L]!!.ok)
        assertEquals("ConnectError", results[8L]?.returnCode)
    }

    @Test
    fun `device time data xml carries adjust flag and iso time`() {
        val doc = parseXml(MtScaleKeyXml.buildDeviceTimeDataXml())
        assertEquals("True", doc.getElementsByTagName("DeviceTime").item(0).attributes.getNamedItem("AdjustTime").nodeValue)
        assertTrue(doc.getElementsByTagName("DeviceTime").item(1).textContent.matches(
            Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
        ))
    }
}
