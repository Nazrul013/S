package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CellData
import com.example.data.models.CellFormatting
import com.example.ui.providers.SpreadsheetViewModel
import com.example.ui.widgets.CellGridView
import com.example.ui.widgets.FormulaBarLine
import com.example.ui.widgets.FormattingToolbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetEditorScreen(
    viewModel: SpreadsheetViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val activeSheetEntity by viewModel.activeSpreadsheet.collectAsState()
    val activeData by viewModel.activeSpreadsheetData.collectAsState()
    val computedValues by viewModel.computedValues.collectAsState()
    val selectedCell by viewModel.selectedCell.collectAsState()
    val cellTextInput by viewModel.cellTextInput.collectAsState()

    // Dialog states
    var showSearchReplaceDialog by remember { mutableStateOf(false) }
    var showAddTabDialog by remember { mutableStateOf(false) }
    var showRenameTabDialog by remember { mutableStateOf<Int?>(null) } // Worksheet index to rename

    // Dialog Textfields
    var searchStr by remember { mutableStateOf("") }
    var replaceStr by remember { mutableStateOf("") }
    var searchAllTabs by remember { mutableStateOf(false) }
    var addTabName by remember { mutableStateOf("") }
    var renameTabName by remember { mutableStateOf("") }

    // Toggle formatting details pane visibility
    var showFormattingDock by remember { mutableStateOf(false) }

    // Main top right contextual actions dropdown menu
    var showActionsMenu by remember { mutableStateOf(false) }

    // Storage Access Framework - SAX launchers for exporting
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportToCsv(context, it, {
                Toast.makeText(context, "CSV exported successfully!", Toast.LENGTH_SHORT).show()
            }, { err ->
                Toast.makeText(context, "Export error: $err", Toast.LENGTH_LONG).show()
            })
        }
    }

    val xlsxExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportToXlsx(context, it, {
                Toast.makeText(context, "Excel exported successfully!", Toast.LENGTH_SHORT).show()
            }, { err ->
                Toast.makeText(context, "Export error: $err", Toast.LENGTH_LONG).show()
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeSheetEntity?.name ?: "Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Saved to Device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.closeActiveSpreadsheet()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back & Save")
                    }
                },
                actions = {
                    // Undo
                    TextButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo()
                    ) {
                        Text(
                            text = "Undo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.canUndo()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Redo
                    TextButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo()
                    ) {
                        Text(
                            text = "Redo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.canRedo()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Formatting Toggle Button
                    IconButton(onClick = { showFormattingDock = !showFormattingDock }) {
                        Icon(Icons.Default.Edit, contentDescription = "Toggle Format tools", tint = if (showFormattingDock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }

                    // Options list
                    IconButton(onClick = { showActionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions menu")
                    }

                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false }
                    ) {
                        // Export CSV
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showActionsMenu = false
                                val defaultName = (activeSheetEntity?.name ?: "Spreadsheet") + ".csv"
                                csvExportLauncher.launch(defaultName)
                            }
                        )

                        // Export XLSX
                        DropdownMenuItem(
                            text = { Text("Export Excel (.xlsx)") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showActionsMenu = false
                                val defaultName = (activeSheetEntity?.name ?: "Spreadsheet") + ".xlsx"
                                xlsxExportLauncher.launch(defaultName)
                            }
                        )

                        DropdownMenuDivider()

                        // Search & Replace
                        DropdownMenuItem(
                            text = { Text("Search & Replace") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                showActionsMenu = false
                                showSearchReplaceDialog = true
                            }
                        )

                        // Sort Ascending
                        DropdownMenuItem(
                            text = { Text("Sort Ascending") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.sortSelectedColumn(true)
                                    Toast.makeText(context, "Sorted ascending", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Select a column cell to sort first", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // Sort Descending
                        DropdownMenuItem(
                            text = { Text("Sort Descending") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.sortSelectedColumn(false)
                                    Toast.makeText(context, "Sorted descending", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Select a column cell to sort first", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuDivider()

                        // Row & Col Actions
                        DropdownMenuItem(
                            text = { Text("Insert Row ABOVE") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.insertRow()
                                } else {
                                    Toast.makeText(context, "Select a cell to insert row", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Delete Active Row") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.deleteRow()
                                } else {
                                    Toast.makeText(context, "Select a cell in row to delete", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Insert Column LEFT") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.insertColumn()
                                } else {
                                    Toast.makeText(context, "Select a cell to insert column", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Delete Active Column") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.deleteColumn()
                                } else {
                                    Toast.makeText(context, "Select a cell in column to delete", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuDivider()

                        // Clipboard
                        DropdownMenuItem(
                            text = { Text("Copy Cell") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.copyCell()
                                    Toast.makeText(context, "Selected cell copied to clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Select a cell to copy", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Cut Cell") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.cutCell()
                                    Toast.makeText(context, "Selected cell cut to clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Select a cell to cut", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Paste Cell") },
                            onClick = {
                                showActionsMenu = false
                                if (selectedCell != null) {
                                    viewModel.pasteCell()
                                } else {
                                    Toast.makeText(context, "Select a target cell to paste", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Formula Input Editor Bar
            FormulaBarLine(
                selectedCell = selectedCell,
                textInput = cellTextInput,
                onTextInputChanged = { viewModel.updateSelectedCellValue(it) },
                onCommitValue = { viewModel.selectCell(null) },
                onDiscardChanges = {
                    val originalVal = activeData.sheets[activeData.activeSheetIndex].cells[selectedCell]?.value ?: ""
                    viewModel.updateSelectedCellValue(originalVal)
                    viewModel.selectCell(null)
                }
            )

            // Dynamic grid container viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeData.sheets.isNotEmpty()) {
                    val activeWorksheet = if (activeData.sheets.indices.contains(activeData.activeSheetIndex)) {
                        activeData.sheets[activeData.activeSheetIndex]
                    } else {
                        activeData.sheets.first()
                    }

                    CellGridView(
                        worksheet = activeWorksheet,
                        computedValues = computedValues,
                        selectedCell = selectedCell,
                        onCellSelected = { viewModel.selectCell(it) }
                    )
                }
            }

            // Worksheet Selector Bottom Tab bar (multiple worksheets)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab listings
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeData.sheets.forEachIndexed { index, sheet ->
                        val isSelected = index == activeData.activeSheetIndex
                        var showTabOptions by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .then(
                                    if (!isSelected) Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(20.dp)
                                    ) else Modifier
                                )
                                .clickable {
                                    viewModel.selectWorksheetTab(index)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = sheet.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Custom mini arrow for options menu on selected worksheet
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Worksheet options",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { showTabOptions = true },
                                        tint = Color.White
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showTabOptions,
                                onDismissRequest = { showTabOptions = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showTabOptions = false
                                        renameTabName = sheet.name
                                        showRenameTabDialog = index
                                    }
                                )
                                if (activeData.sheets.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Worksheet") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            showTabOptions = false
                                            viewModel.deleteWorksheet(index)
                                            Toast.makeText(context, "Worksheet deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Add Worksheet Tab Button
                IconButton(
                    onClick = {
                        addTabName = ""
                        showAddTabDialog = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add multiple worksheet tabs",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Bottom Drawer format dock
            if (showFormattingDock && selectedCell != null) {
                val sheet = activeData.sheets[activeData.activeSheetIndex]
                val selectedCellData = sheet.cells[selectedCell] ?: CellData()
                val format = selectedCellData.formatting

                FormattingToolbar(
                    currentFormatting = format,
                    onFormatChanged = { updatedFormat ->
                        viewModel.updateSelectedCellFormatting(
                            bold = updatedFormat.bold,
                            italic = updatedFormat.italic,
                            underline = updatedFormat.underline,
                            fontSize = updatedFormat.fontSize,
                            textColor = updatedFormat.textColor,
                            backgroundColor = updatedFormat.backgroundColor,
                            alignment = updatedFormat.alignment
                        )
                    }
                )
            }
        }
    }

    // ==========================================
    // MODULE DIALOGS (SEARCH/REPLACE, TAB MANAGERS)
    // ==========================================

    // 1. Search and Replace
    if (showSearchReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showSearchReplaceDialog = false },
            title = { Text("Search & Replace", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchStr,
                        onValueChange = { searchStr = it },
                        label = { Text("Find text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = replaceStr,
                        onValueChange = { replaceStr = it },
                        label = { Text("Replace with") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Checkbox for sheets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = searchAllTabs,
                            onCheckedChange = { searchAllTabs = it }
                        )
                        Text("Search across all worksheets")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.searchAndReplace(searchStr, replaceStr, searchAllTabs)
                        Toast.makeText(context, "Search and replace complete", Toast.LENGTH_SHORT).show()
                        showSearchReplaceDialog = false
                    }
                ) {
                    Text("Replace All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchReplaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Add sheet tab
    if (showAddTabDialog) {
        AlertDialog(
            onDismissRequest = { showAddTabDialog = false },
            title = { Text("New Worksheet", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = addTabName,
                    onValueChange = { addTabName = it },
                    label = { Text("Worksheet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Sheet ${activeData.sheets.size+1}") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addWorksheet(addTabName)
                        showAddTabDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTabDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Rename sheet tab
    if (showRenameTabDialog != null) {
        val targetIdx = showRenameTabDialog!!
        AlertDialog(
            onDismissRequest = { showRenameTabDialog = null },
            title = { Text("Rename Worksheet", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameTabName,
                    onValueChange = { renameTabName = it },
                    label = { Text("Worksheet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameWorksheet(targetIdx, renameTabName)
                        showRenameTabDialog = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameTabDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DropdownMenuDivider() {
    Divider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
