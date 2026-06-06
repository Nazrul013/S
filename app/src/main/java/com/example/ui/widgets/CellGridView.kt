package com.example.ui.widgets

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CellData
import com.example.data.models.Worksheet
import com.example.data.services.FormulaEvaluator

@Composable
fun CellGridView(
    worksheet: Worksheet,
    computedValues: Map<String, String>,
    selectedCell: String?,
    onCellSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cellWidth = 92.dp
    val cellHeight = 38.dp
    val indexHeaderWidth = 46.dp

    val horScrollState = rememberScrollState()
    val verScrollState = rememberScrollState()

    val isDarkTheme = !MaterialTheme.colorScheme.surface.toString().contains("white", ignoreCase = true)

    // Inner scroll container containing both header rows and columns
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(horScrollState)
                .verticalScroll(verScrollState)
        ) {
            // ==========================================
            // HEADER ROW (A, B, C...)
            // ==========================================
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .height(cellHeight)
            ) {
                // Top-left Empty Header block
                Box(
                    modifier = Modifier
                        .width(indexHeaderWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Letters Header
                val selectedCol = selectedCell?.filter { it.isLetter() }
                for (c in 0 until worksheet.columnCount) {
                    val colLetter = FormulaEvaluator.colIndexToLetter(c)
                    val isColSelected = selectedCol == colLetter
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .fillMaxHeight()
                            .background(
                                if (isColSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = colLetter,
                            fontSize = 12.sp,
                            fontWeight = if (isColSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isColSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ==========================================
            // DATA ROWS (1, 2, 3...)
            // ==========================================
            val selectedRow = selectedCell?.filter { it.isDigit() }?.toIntOrNull()
            for (r in 1..worksheet.rowCount) {
                val isRowSelected = selectedRow == r
                Row(modifier = Modifier.height(cellHeight)) {
                    // Left Row index header
                    Box(
                        modifier = Modifier
                            .width(indexHeaderWidth)
                            .fillMaxHeight()
                            .background(
                                if (isRowSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = r.toString(),
                            fontSize = 11.sp,
                            fontWeight = if (isRowSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            color = if (isRowSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Columns Cells in Row
                    for (c in 0 until worksheet.columnCount) {
                        val colLetter = FormulaEvaluator.colIndexToLetter(c)
                        val cellKey = "$colLetter$r"
                        val isSelected = selectedCell == cellKey

                        val cellData = worksheet.cells[cellKey] ?: CellData()
                        val rawVal = cellData.value
                        val dispVal = if (rawVal.startsWith("=")) {
                            computedValues[cellKey] ?: rawVal
                        } else {
                            rawVal
                        }

                        val style = cellData.formatting

                        // Dynamic custom cell styles parsing
                        val textCol = if (style.textColor != "#000000") {
                            try { Colors.fromHex(style.textColor) } catch (e: Exception) { MaterialTheme.colorScheme.onSurface }
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }

                        val bgCol = if (style.backgroundColor != "#FFFFFF") {
                            try { Colors.fromHex(style.backgroundColor) } catch (e: Exception) { Color.Transparent }
                        } else {
                            Color.Transparent
                        }

                        val alignment = when (style.alignment) {
                            "LEFT" -> Alignment.CenterStart
                            "CENTER" -> Alignment.Center
                            "RIGHT" -> Alignment.CenterEnd
                            else -> Alignment.CenterStart
                        }

                        val textAlignment = when (style.alignment) {
                            "LEFT" -> TextAlign.Left
                            "CENTER" -> TextAlign.Center
                            "RIGHT" -> TextAlign.Right
                            else -> TextAlign.Left
                        }

                        // Cell container UI
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .background(bgCol)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.4.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                .testTag("cell_$cellKey")
                                .clickable { onCellSelected(cellKey) },
                            contentAlignment = alignment
                        ) {
                            Text(
                                text = dispVal,
                                fontSize = style.fontSize.sp,
                                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None,
                                color = textCol,
                                textAlign = textAlignment,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Utility object for converting hexadecimal representation to material.graphics Color
object Colors {
    fun fromHex(colorString: String): Color {
        return Color(android.graphics.Color.parseColor(colorString))
    }
}
