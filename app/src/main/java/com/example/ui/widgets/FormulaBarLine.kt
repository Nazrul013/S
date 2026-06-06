package com.example.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormulaBarLine(
    selectedCell: String?,
    textInput: String,
    onTextInputChanged: (String) -> Unit,
    onCommitValue: () -> Unit,
    onDiscardChanges: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Location box (e.g., "A1")
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(36.dp)
                .background(
                    color = if (selectedCell != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = selectedCell ?: "--",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = if (selectedCell != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Formula indicator sign f(x)
        Text(
            text = "fx",
            style = MaterialTheme.typography.titleMedium.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Text Field Input
        TextField(
            value = textInput,
            onValueChange = onTextInputChanged,
            placeholder = { Text(selectedCell?.let { "Enter text or formula (=...)" } ?: "Select a cell to edit", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(6.dp),
            enabled = selectedCell != null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
        )

        // Confirm / Discard Controls
        if (selectedCell != null && textInput.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onCommitValue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Commit cell text",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDiscardChanges,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Discard changes",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
