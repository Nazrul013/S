package com.example.data.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CellFormatting(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSize: Float = 14f,
    val textColor: String = "#000000",
    val backgroundColor: String = "#FFFFFF",
    val alignment: String = "LEFT" // "LEFT", "CENTER", "RIGHT"
)

@JsonClass(generateAdapter = true)
data class CellData(
    val value: String = "",
    val formatting: CellFormatting = CellFormatting()
)

@JsonClass(generateAdapter = true)
data class Worksheet(
    val name: String,
    val rowCount: Int = 100,
    val columnCount: Int = 26,
    val cells: Map<String, CellData> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class SpreadsheetData(
    val sheets: List<Worksheet> = listOf(Worksheet(name = "Sheet1")),
    val activeSheetIndex: Int = 0
)
