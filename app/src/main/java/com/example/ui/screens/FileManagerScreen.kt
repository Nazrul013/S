package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.SpreadsheetEntity
import com.example.ui.providers.SpreadsheetViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: SpreadsheetViewModel,
    onSpreadsheetOpened: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spreadsheets by viewModel.allSpreadsheets.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Dialog trigger states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<SpreadsheetEntity?>(null) }
    var deleteConfirmEntity by remember { mutableStateOf<SpreadsheetEntity?>(null) }

    // Textfields in dialogs
    var newSheetTitle by remember { mutableStateOf("") }
    var renameSheetTitle by remember { mutableStateOf("") }

    // Interactive File Picker Launchers for Imports
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importCsv(context, it, {
                Toast.makeText(context, "CSV spreadsheet imported successfully", Toast.LENGTH_SHORT).show()
                onSpreadsheetOpened()
            }, { err ->
                Toast.makeText(context, "Error: $err", Toast.LENGTH_LONG).show()
            })
        }
    }

    val xlsxImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importXlsx(context, it, {
                Toast.makeText(context, "Excel spreadsheet imported successfully", Toast.LENGTH_SHORT).show()
                onSpreadsheetOpened()
            }, { err ->
                Toast.makeText(context, "Error: $err", Toast.LENGTH_LONG).show()
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GridFlow Office", fontWeight = FontWeight.Bold) },
                actions = {
                    // Import Options menu
                    var showImportMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Import external file")
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import CSV file") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showImportMenu = false
                                csvImportLauncher.launch("text/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import XLSX (Excel)") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showImportMenu = false
                                xlsxImportLauncher.launch("application/*")
                            }
                        )
                    }

                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newSheetTitle = "New Spreadsheet"
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Spreadsheet")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Search Input Block
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search spreadsheets...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Filter sheets catalog
            val filteredSpreadsheets = spreadsheets.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }

            if (filteredSpreadsheets.isEmpty()) {
                // Empty view state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) "No spreadsheets saved yet" else "No matching results found",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) "Tap the '+' button down-right to create a sheet!" else "Try searching differently",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                Text(
                    text = "Recent Spreadsheets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSpreadsheets) { sheet ->
                        SpreadsheetListItem(
                            entity = sheet,
                            onClick = {
                                viewModel.openSpreadsheet(sheet.id)
                                onSpreadsheetOpened()
                            },
                            onRename = {
                                renameSheetTitle = sheet.name
                                showRenameDialog = sheet
                            },
                            onDelete = {
                                deleteConfirmEntity = sheet
                            }
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // ACTION DIALOGS (CREATE, RENAME, CONFIRM DELETE)
    // ==========================================

    // 1. Create File dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Spreadsheet", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newSheetTitle,
                    onValueChange = { newSheetTitle = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = newSheetTitle.trim().ifEmpty { "Workbook" }
                        viewModel.createNewSpreadsheet(title) {
                            onSpreadsheetOpened()
                        }
                        showCreateDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Rename File dialog
    if (showRenameDialog != null) {
        val target = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Spreadsheet", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameSheetTitle,
                    onValueChange = { renameSheetTitle = it },
                    label = { Text("New Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = renameSheetTitle.trim()
                        if (title.isNotEmpty()) {
                            viewModel.renameSpreadsheet(target.id, title)
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Confirm Delete Dialog
    if (deleteConfirmEntity != null) {
        val target = deleteConfirmEntity!!
        AlertDialog(
            onDismissRequest = { deleteConfirmEntity = null },
            title = { Text("Delete Spreadsheet?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${target.name}'? This process cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSpreadsheet(target.id)
                        deleteConfirmEntity = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmEntity = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SpreadsheetListItem(
    entity: SpreadsheetEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()) }
    val editTime = remember(entity.lastModified) { formatter.format(Date(entity.lastModified)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant Grid layout sheet icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text blocks
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Edited: $editTime",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Options trigger menu
            var showOptions by remember { mutableStateOf(false) }

            Box {
                IconButton(onClick = { showOptions = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options context menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showOptions,
                    onDismissRequest = { showOptions = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showOptions = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showOptions = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
