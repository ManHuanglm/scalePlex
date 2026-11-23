package com.scaleplex.driver.impl.digi

import kotlin.test.Test
import kotlin.test.assertEquals

/** 寺冈机型族识别测试（银豹文档 + LabelNet 机型列表）。 */
class DigiModelFamilyTest {

    @Test
    fun `8442 family models`() {
        assertEquals(DigiModelFamily.FAMILY_8442, DigiModelFamily.fromModel("SM-80"))
        assertEquals(DigiModelFamily.FAMILY_8442, DigiModelFamily.fromModel("SM100"))
        assertEquals(DigiModelFamily.FAMILY_8442, DigiModelFamily.fromModel("SM-100B"))
        assertEquals(DigiModelFamily.FAMILY_8442, DigiModelFamily.fromModel("SM-110"))
        assertEquals(DigiModelFamily.FAMILY_8442, DigiModelFamily.fromModel("8442"))
        assertEquals(DigiModelFamily.FAMILY_8442, DigiModelFamily.fromModel("sm-100 ste47"))
    }

    @Test
    fun `b family models`() {
        assertEquals(DigiModelFamily.FAMILY_B, DigiModelFamily.fromModel("SM-110N"))
        assertEquals(DigiModelFamily.FAMILY_B, DigiModelFamily.fromModel("SM-120"))
        assertEquals(DigiModelFamily.FAMILY_B, DigiModelFamily.fromModel("SM-5100"))
        assertEquals(DigiModelFamily.FAMILY_B, DigiModelFamily.fromModel("bCom"))
        assertEquals(DigiModelFamily.FAMILY_B, DigiModelFamily.fromModel("bPro"))
        assertEquals(DigiModelFamily.FAMILY_B, DigiModelFamily.fromModel("bTwin"))
        assertEquals(DigiModelFamily.FAMILY_B, DigiModelFamily.fromModel("SM110N STE57"))
    }

    @Test
    fun `unknown defaults to 8442`() {
        assertEquals(DigiModelFamily.UNKNOWN, DigiModelFamily.fromModel(""))
        assertEquals(DigiModelFamily.UNKNOWN, DigiModelFamily.fromModel("未知型号"))
        // SM-110 不带 N 后缀按 8442 族；SM-110N 命中 b 族关键词
        assertEquals(DigiModelFamily.FAMILY_8442, DigiModelFamily.fromModel("SM-110"))
    }
}
