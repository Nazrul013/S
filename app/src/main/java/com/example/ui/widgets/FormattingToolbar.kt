package com.example.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CellFormatting

@Composable
fun FormattingToolbar(
    currentFormatting: CellFormatting,
    onFormatChanged: (CellFormatting) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPalette = listOf(
        "#000000" to "Black",
        "#FFFFFF" to "White",
        "#757575" to "Gray",
        "#E53935" to "Red",
        "#D81B60" to "Pink",
        "#8E24AA" to "Purple",
        "#1E88E5" to "Blue",
        "#43A047" to "Green",
        "#FDD835" to "Yellow",
        "#FB8C00" to "Orange"
    )

    var showTextColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Style Toggles Row
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Bold Button
                IconButton(
                    onClick = { onFormatChanged(currentFormatting.copy(bold = !currentFormatting.bold)) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (currentFormatting.bold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("B", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }

                // Italic Button
                IconButton(
                    onClick = { onFormatChanged(currentFormatting.copy(italic = !currentFormatting.italic)) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (currentFormatting.italic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("I", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                }

                // Underline Button
                IconButton(
                    onClick = { onFormatChanged(currentFormatting.copy(underline = !currentFormatting.underline)) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (currentFormatting.underline) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = "U",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    )
                }
            }

            // Text Alignment Toggles
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onFormatChanged(currentFormatting.copy(alignment = "LEFT")) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (currentFormatting.alignment == "LEFT") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("|<-", fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { onFormatChanged(currentFormatting.copy(alignment = "CENTER")) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (currentFormatting.alignment == "CENTER") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("||", fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { onFormatChanged(currentFormatting.copy(alignment = "RIGHT")) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (currentFormatting.alignment == "RIGHT") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("->|", fontWeight = FontWeight.Bold)
                }
            }

            // Font Size Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val newSize = maxOf(8f, currentFormatting.fontSize - 1f)
                        onFormatChanged(currentFormatting.copy(fontSize = newSize))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("-", style = MaterialTheme.typography.titleMedium)
                }

                Text(
                    text = "${currentFormatting.fontSize.toInt()}pt",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = {
                        val newSize = minOf(48f, currentFormatting.fontSize + 1f)
                        onFormatChanged(currentFormatting.copy(fontSize = newSize))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Text & Background Color Pickers Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    showTextColorPicker = true
                    showBgColorPicker = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Text Color", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(currentFormatting.textColor)))
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                )
            }

            Button(
                onClick = {
                    showBgColorPicker = true
                    showTextColorPicker = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Fill Cell", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                val colorValue = try {
                    Color(android.graphics.Color.parseColor(currentFormatting.backgroundColor))
                } catch (e: Exception) {
                    Color.White
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(colorValue)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                )
            }
        }

        // Color Palette Expanded Views
        if (showTextColorPicker) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text("Select Text Color:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(colorPalette) { (hex, _) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (currentFormatting.textColor == hex) 2.dp else 1.dp,
                                    color = if (currentFormatting.textColor == hex) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    onFormatChanged(currentFormatting.copy(textColor = hex))
                                    showTextColorPicker = false
                                }
                        )
                    }
                }
            }
        }

        if (showBgColorPicker) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text("Select Fill Color:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(colorPalette) { (hex, _) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (currentFormatting.backgroundColor == hex) 2.dp else 1.dp,
                                    color = if (currentFormatting.backgroundColor == hex) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    onFormatChanged(currentFormatting.copy(backgroundColor = hex))
                                    showBgColorPicker = false
                                }
                        )
                    }
                }
            }
        }
    }
}
