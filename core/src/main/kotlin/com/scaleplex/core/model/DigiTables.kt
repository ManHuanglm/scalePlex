package com.scaleplex.core.model

/**
 * Reference tables transcribed from the DIGI SM series `SM.ini` definition file.
 * These drive the dropdowns in the PLU editor and the DIGI driver's encoding of
 * barcode / label / font fields. They are data, not behavior.
 */
object DigiTables {

    /** Unit / quantity symbol table (`[Quantity_symbol_type_table_STD]`). */
    val QUANTITY_SYMBOLS: List<Pair<Int, String>> = listOf(
        0 to "No symbol", 1 to "Pcs", 2 to "For", 3 to "Kg",
        4 to "LB", 5 to "g", 6 to "OZ",
    )

    /** Barcode right-side data type (`[Right_Side_Data_of_Item_Type]`). */
    val BAR_RIGHT_DATA_TYPES: List<Pair<Int, String>> = listOf(
        0 to "默认 Default", 1 to "Quantity", 2 to "Weight", 3 to "Price",
    )

    /** Label format 1 table (`[Label1Format]`). */
    val LABEL1_FORMATS: List<Pair<Int, String>> = listOf(
        0 to "默认 Default", 1 to "T1", 2 to "T2", 3 to "T3", 4 to "T4", 5 to "T5",
        6 to "T6", 7 to "T7", 8 to "T8", 9 to "T9", 10 to "T10", 11 to "T11", 12 to "T12",
        13 to "S", 14 to "A", 15 to "B", 16 to "C", 17 to "F1", 18 to "F2", 19 to "F3",
        20 to "F4", 21 to "F5", 22 to "F6", 23 to "F7", 24 to "F8",
    )

    /** Label format 2 table (`[Label2Format]`). */
    val LABEL2_FORMATS: List<Pair<Int, String>> = listOf(
        0 to "不打印 None", 1 to "F1", 2 to "F2", 3 to "F3", 4 to "F4",
        5 to "F5", 6 to "F6", 7 to "F7", 8 to "F8",
    )

    /** EAN barcode format table (`[EAN_Barcode_format_table]`). */
    val EAN_BARCODE_FORMATS: List<Pair<Int, String>> = listOf(
        0 to "默认 (根据 SPEC 设置)",
        1 to "F1F2 5C XCD 4X CD (13)",
        2 to "F2 6C XCD 4X CD (13)",
        3 to "F1F2 5C 0 4X CD (13)",
        4 to "F1F2 6C 4X CD (13)",
        5 to "F1F2 5C 5X CD (13)",
        6 to "F2 5C 5X CD (13)",
        7 to "F2 5C 6X CD (13)",
        8 to "F1F2 10C CD (13)",
        9 to "F1F2 4C 6X CD (13)",
        10 to "F1F2 4C CD (8)",
        11 to "F2 2C 4X CD (8)",
        12 to "NON BARCODE (无条码)",
        13 to "F2 0 5C XCD 4X CD (13)",
        14 to "F2 0 6C 4X CD (13)",
        15 to "F1F2 4C XCD 5X CD (13)",
        16 to "F2 5C XCD 5X CD (13)",
        17 to "F1F2 3C 7X CD (13)",
        18 to "F1F2 2C 8X CD (13)",
        19 to "3C 4W 5P CD (13)",
        20 to "NON BARCODE (无条码)",
        21 to "F1F2 5C PCD 4X CD (13)",
        22 to "F1F2 5R 5X CD (13)",
        23 to "F2 5C 6X CD (13)",
        24 to "3F 4C 5P CD (13)",
        25 to "F1F2 5C 5W CD (13)",
        26 to "F2 5C 5W 0 CD (13)",
        27 to "F1F2 6C 4W CD (13)",
        28 to "NON BARCODE (无条码)",
        29 to "F1F2 3C 7X CD (13)",
        30 to "F2 7C 4W CD (13)",
        31 to "F1F2 2C 3N 5P CD (13)",
        32 to "F1F2 1C 4N 5P CD (13)",
    )

    /** China-font table (`[Fonts]`, CHINA group) used for commodity / ingredient text. */
    val CHINA_FONTS: List<Pair<Int, String>> = listOf(
        20 to "G3 16x16", 21 to "G4 24x24", 22 to "G1 32x32",
        23 to "G2 48x48", 24 to "G5 16x32", 25 to "G6 24x48",
    )
}
