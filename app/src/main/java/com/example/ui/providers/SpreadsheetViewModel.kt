package com.example.ui.providers

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.SpreadsheetEntity
import com.example.data.models.CellData
import com.example.data.models.CellFormatting
import com.example.data.models.SpreadsheetData
import com.example.data.models.Worksheet
import com.example.data.repository.SpreadsheetRepository
import com.example.data.services.FileService
import com.example.data.services.FormulaEvaluator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.io.FileNotFoundException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class SpreadsheetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SpreadsheetRepository
    val allSpreadsheets: StateFlow<List<SpreadsheetEntity>>

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dataAdapter = moshi.adapter(SpreadsheetData::class.java)

    // Editor States
    private val _activeSpreadsheet = MutableStateFlow<SpreadsheetEntity?>(null)
    val activeSpreadsheet = _activeSpreadsheet.asStateFlow()

    private val _activeSpreadsheetData = MutableStateFlow(SpreadsheetData())
    val activeSpreadsheetData = _activeSpreadsheetData.asStateFlow()

    private val _computedValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val computedValues = _computedValues.asStateFlow()

    private val _selectedCell = MutableStateFlow<String?>(null)
    val selectedCell = _selectedCell.asStateFlow()

    private val _cellTextInput = MutableStateFlow("")
    val cellTextInput = _cellTextInput.asStateFlow()

    // Preferences / Dark Mode
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode = _isDarkMode.asStateFlow()

    // Undo/Redo Stacks
    private val undoStack = mutableListOf<SpreadsheetData>()
    private val redoStack = mutableListOf<SpreadsheetData>()

    // Copy / Cut Clipboard
    private var copyBuffer: CellData? = null
    private var isCutOperation = false

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SpreadsheetRepository(database.spreadsheetDao())
        allSpreadsheets = repository.allSpreadsheets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Load Dark Mode Preference
        val sharedPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        _isDarkMode.value = sharedPrefs.getBoolean("dark_mode", false)
    }

    fun toggleDarkMode() {
        val nextMode = !_isDarkMode.value
        _isDarkMode.value = nextMode
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("dark_mode", nextMode).apply()
    }

    // ==========================================
    // BACKUP & HISTORY (UNDO/REDO)
    // ==========================================

    private fun saveUndoState() {
        // Core snapshot to stack
        if (undoStack.size >= 30) {
            undoStack.removeAt(0)
        }
        undoStack.add(_activeSpreadsheetData.value.copy(
            sheets = _activeSpreadsheetData.value.sheets.map {
                it.copy(cells = it.cells.toMap())
            }
        ))
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_activeSpreadsheetData.value)
            _activeSpreadsheetData.value = previousState
            _selectedCell.value = null
            _cellTextInput.value = ""
            recalculateFormulas()
            autoSaveActiveSpreadsheet()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_activeSpreadsheetData.value)
            _activeSpreadsheetData.value = nextState
            _selectedCell.value = null
            _cellTextInput.value = ""
            recalculateFormulas()
            autoSaveActiveSpreadsheet()
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // ==========================================
    // DATA LOAD & AUTO-SAVE
    // ==========================================

    fun selectCell(cellKey: String?) {
        _selectedCell.value = cellKey
        if (cellKey != null) {
            val sheet = getActiveWorksheet()
            val cellData = sheet.cells[cellKey]
            _cellTextInput.value = cellData?.value ?: ""
        } else {
            _cellTextInput.value = ""
        }
    }

    fun updateSelectedCellValue(newValue: String) {
        val key = _selectedCell.value ?: return
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = sheet.cells.toMutableMap()
                if (newValue.isEmpty()) {
                    updatedCells.remove(key)
                } else {
                    val currentFormatting = updatedCells[key]?.formatting ?: CellFormatting()
                    updatedCells[key] = CellData(value = newValue, formatting = currentFormatting)
                }
                sheet.copy(cells = updatedCells)
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        _cellTextInput.value = newValue
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    fun updateSelectedCellFormatting(
        bold: Boolean? = null,
        italic: Boolean? = null,
        underline: Boolean? = null,
        fontSize: Float? = null,
        textColor: String? = null,
        backgroundColor: String? = null,
        alignment: String? = null
    ) {
        val key = _selectedCell.value ?: return
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = sheet.cells.toMutableMap()
                val currentCell = updatedCells[key] ?: CellData()
                val currentFormat = currentCell.formatting

                val nextFormat = currentFormat.copy(
                    bold = bold ?: currentFormat.bold,
                    italic = italic ?: currentFormat.italic,
                    underline = underline ?: currentFormat.underline,
                    fontSize = fontSize ?: currentFormat.fontSize,
                    textColor = textColor ?: currentFormat.textColor,
                    backgroundColor = backgroundColor ?: currentFormat.backgroundColor,
                    alignment = alignment ?: currentFormat.alignment
                )
                updatedCells[key] = currentCell.copy(formatting = nextFormat)
                sheet.copy(cells = updatedCells)
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        autoSaveActiveSpreadsheet()
    }

    private fun recalculateFormulas() {
        val sheet = getActiveWorksheet()
        _computedValues.value = FormulaEvaluator.evaluateWorksheet(sheet)
    }

    private fun getActiveWorksheet(): Worksheet {
        val data = _activeSpreadsheetData.value
        return if (data.sheets.indices.contains(data.activeSheetIndex)) {
            data.sheets[data.activeSheetIndex]
        } else {
            data.sheets.firstOrNull() ?: Worksheet(name = "Sheet1")
        }
    }

    // ==========================================
    // CRUDS (CREATE, OPEN, SAVE, RENAME, DELETE)
    // ==========================================

    fun createNewSpreadsheet(title: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val defaultData = SpreadsheetData(
                sheets = listOf(
                    Worksheet(name = "Sheet1", rowCount = 100, columnCount = 26)
                ),
                activeSheetIndex = 0
            )
            val json = dataAdapter.toJson(defaultData)
            val entity = SpreadsheetEntity(
                name = title,
                serializedData = json
            )
            val newId = repository.insert(entity)
            withContext(Dispatchers.Main) {
                openSpreadsheet(newId)
                onSuccess()
            }
        }
    }

    fun openSpreadsheet(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = repository.getSpreadsheetById(id)
            if (entity != null) {
                val data = try {
                    dataAdapter.fromJson(entity.serializedData) ?: SpreadsheetData()
                } catch (e: Exception) {
                    SpreadsheetData()
                }

                withContext(Dispatchers.Main) {
                    _activeSpreadsheet.value = entity
                    _activeSpreadsheetData.value = data
                    _selectedCell.value = null
                    _cellTextInput.value = ""
                    undoStack.clear()
                    redoStack.clear()
                    recalculateFormulas()
                }
            }
        }
    }

    fun closeActiveSpreadsheet() {
        _activeSpreadsheet.value = null
        _activeSpreadsheetData.value = SpreadsheetData()
        _selectedCell.value = null
        _cellTextInput.value = ""
        undoStack.clear()
        redoStack.clear()
    }

    fun renameSpreadsheet(id: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = repository.getSpreadsheetById(id)
            if (entity != null) {
                val updated = entity.copy(
                    name = newName,
                    lastModified = System.currentTimeMillis()
                )
                repository.update(updated)
                if (_activeSpreadsheet.value?.id == id) {
                    _activeSpreadsheet.value = updated
                }
            }
        }
    }

    fun deleteSpreadsheet(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(id)
            if (_activeSpreadsheet.value?.id == id) {
                withContext(Dispatchers.Main) {
                    closeActiveSpreadsheet()
                }
            }
        }
    }

    private fun autoSaveActiveSpreadsheet() {
        val active = _activeSpreadsheet.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val json = dataAdapter.toJson(_activeSpreadsheetData.value)
            val updated = active.copy(
                serializedData = json,
                lastModified = System.currentTimeMillis()
            )
            repository.update(updated)
            _activeSpreadsheet.value = updated
        }
    }

    // ==========================================
    // ROW & COLUMN INSERTION / DELETION
    // ==========================================

    fun insertRow() {
        val cell = _selectedCell.value ?: return
        val activeRow = cell.filter { it.isDigit() }.toIntOrNull() ?: return
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = mutableMapOf<String, CellData>()
                for ((key, cellData) in sheet.cells) {
                    val r = key.filter { it.isDigit() }.toIntOrNull() ?: continue
                    val colLetters = key.filter { it.isLetter() }
                    if (r >= activeRow) {
                        updatedCells["$colLetters${r + 1}"] = cellData
                    } else {
                        updatedCells[key] = cellData
                    }
                }
                sheet.copy(
                    rowCount = sheet.rowCount + 1,
                    cells = updatedCells
                )
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        _selectedCell.value = null
        _cellTextInput.value = ""
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    fun deleteRow() {
        val cell = _selectedCell.value ?: return
        val activeRow = cell.filter { it.isDigit() }.toIntOrNull() ?: return
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = mutableMapOf<String, CellData>()
                for ((key, cellData) in sheet.cells) {
                    val r = key.filter { it.isDigit() }.toIntOrNull() ?: continue
                    val colLetters = key.filter { it.isLetter() }
                    if (r > activeRow) {
                        updatedCells["$colLetters${r - 1}"] = cellData
                    } else if (r < activeRow) {
                        updatedCells[key] = cellData
                    }
                }
                sheet.copy(
                    rowCount = maxOf(1, sheet.rowCount - 1),
                    cells = updatedCells
                )
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        _selectedCell.value = null
        _cellTextInput.value = ""
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    fun insertColumn() {
        val cell = _selectedCell.value ?: return
        val activeColLetter = cell.filter { it.isLetter() }
        val activeColIdx = FormulaEvaluator.colLetterToIndex(activeColLetter)
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = mutableMapOf<String, CellData>()
                for ((key, cellData) in sheet.cells) {
                    val cLetter = key.filter { it.isLetter() }
                    val r = key.filter { it.isDigit() }
                    val cIdx = FormulaEvaluator.colLetterToIndex(cLetter)
                    if (cIdx >= activeColIdx) {
                        val nextColLetter = FormulaEvaluator.colIndexToLetter(cIdx + 1)
                        updatedCells["$nextColLetter$r"] = cellData
                    } else {
                        updatedCells[key] = cellData
                    }
                }
                sheet.copy(
                    columnCount = sheet.columnCount + 1,
                    cells = updatedCells
                )
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        _selectedCell.value = null
        _cellTextInput.value = ""
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    fun deleteColumn() {
        val cell = _selectedCell.value ?: return
        val activeColLetter = cell.filter { it.isLetter() }
        val activeColIdx = FormulaEvaluator.colLetterToIndex(activeColLetter)
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = mutableMapOf<String, CellData>()
                for ((key, cellData) in sheet.cells) {
                    val cLetter = key.filter { it.isLetter() }
                    val r = key.filter { it.isDigit() }
                    val cIdx = FormulaEvaluator.colLetterToIndex(cLetter)
                    if (cIdx > activeColIdx) {
                        val prevColLetter = FormulaEvaluator.colIndexToLetter(cIdx - 1)
                        updatedCells["$prevColLetter$r"] = cellData
                    } else if (cIdx < activeColIdx) {
                        updatedCells[key] = cellData
                    }
                }
                sheet.copy(
                    columnCount = maxOf(1, sheet.columnCount - 1),
                    cells = updatedCells
                )
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        _selectedCell.value = null
        _cellTextInput.value = ""
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    // ==========================================
    // MULTIPLE TAB / WORKSHEET SELECTION
    // ==========================================

    fun addWorksheet(name: String) {
        saveUndoState()
        val data = _activeSpreadsheetData.value
        val nameToUse = if (name.trim().isEmpty()) {
            "Sheet${data.sheets.size + 1}"
        } else name.trim()

        val newSheet = Worksheet(
            name = nameToUse,
            rowCount = 100,
            columnCount = 26
        )
        val updatedSheets = data.sheets + newSheet
        _activeSpreadsheetData.value = data.copy(
            sheets = updatedSheets,
            activeSheetIndex = updatedSheets.size - 1
        )
        _selectedCell.value = null
        _cellTextInput.value = ""
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    fun deleteWorksheet(index: Int) {
        val data = _activeSpreadsheetData.value
        if (data.sheets.size <= 1) return // Keep at least one sheet
        saveUndoState()

        val nextSheets = data.sheets.toMutableList().apply { removeAt(index) }
        val nextActiveIdx = if (data.activeSheetIndex >= nextSheets.size) {
            nextSheets.size - 1
        } else data.activeSheetIndex

        _activeSpreadsheetData.value = data.copy(
            sheets = nextSheets,
            activeSheetIndex = nextActiveIdx
        )
        _selectedCell.value = null
        _cellTextInput.value = ""
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    fun renameWorksheet(index: Int, newName: String) {
        if (newName.trim().isEmpty()) return
        saveUndoState()
        val data = _activeSpreadsheetData.value
        val updatedSheets = data.sheets.mapIndexed { idx, s ->
            if (idx == index) s.copy(name = newName.trim()) else s
        }
        _activeSpreadsheetData.value = data.copy(sheets = updatedSheets)
        autoSaveActiveSpreadsheet()
    }

    fun selectWorksheetTab(index: Int) {
        val data = _activeSpreadsheetData.value
        if (index in data.sheets.indices) {
            _activeSpreadsheetData.value = data.copy(activeSheetIndex = index)
            _selectedCell.value = null
            _cellTextInput.value = ""
            recalculateFormulas()
        }
    }

    // ==========================================
    // COPY - CUT - PASTE
    // ==========================================

    fun copyCell() {
        val key = _selectedCell.value ?: return
        val sheet = getActiveWorksheet()
        copyBuffer = sheet.cells[key]?.copy()
        isCutOperation = false
    }

    fun cutCell() {
        val key = _selectedCell.value ?: return
        val sheet = getActiveWorksheet()
        copyBuffer = sheet.cells[key]?.copy()
        isCutOperation = true
    }

    fun pasteCell() {
        val key = _selectedCell.value ?: return
        val buffer = copyBuffer ?: return
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = sheet.cells.toMutableMap()
                // Paste new content merging format
                updatedCells[key] = buffer

                // If cut, delete old target matching buffer value
                if (isCutOperation) {
                    val sourceKey = sheet.cells.entries.find { it.value == buffer }?.key
                    if (sourceKey != null) {
                        updatedCells.remove(sourceKey)
                    }
                }
                sheet.copy(cells = updatedCells)
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        _cellTextInput.value = buffer.value
        isCutOperation = false
        copyBuffer = null
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    // ==========================================
    // SEARCH & REPLACE
    // ==========================================

    fun searchAndReplace(searchStr: String, replaceStr: String, allTabs: Boolean = false) {
        if (searchStr.isEmpty()) return
        saveUndoState()

        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sheet ->
            val isTargetSheet = allTabs || (idx == _activeSpreadsheetData.value.activeSheetIndex)
            if (isTargetSheet) {
                val updatedCells = sheet.cells.mapValues { (_, cell) ->
                    val nextVal = cell.value.replace(searchStr, replaceStr)
                    cell.copy(value = nextVal)
                }
                sheet.copy(cells = updatedCells)
            } else {
                sheet
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        selectCell(_selectedCell.value) // Update formula input if target cell changed
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    // ==========================================
    // SORT DATA ASCENDING / DESCENDING
    // ==========================================

    fun sortSelectedColumn(ascending: Boolean) {
        val key = _selectedCell.value ?: return
        val colLetter = key.filter { it.isLetter() }
        saveUndoState()

        val sheet = getActiveWorksheet()
        val rowIndices = sheet.cells.keys
            .mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }
            .distinct()
            .sorted()

        if (rowIndices.size <= 1) return

        // Fetch values and rows mapping
        val valuesMap = rowIndices.associateWith { r ->
            sheet.cells["$colLetter$r"]?.value ?: ""
        }

        // Sort rows by those col values
        val sortedRows = valuesMap.entries.sortedWith(Comparator { e1, e2 ->
            val v1 = e1.value
            val v2 = e2.value
            val d1 = v1.toDoubleOrNull()
            val d2 = v2.toDoubleOrNull()

            if (d1 != null && d2 != null) {
                if (ascending) d1.compareTo(d2) else d2.compareTo(d1)
            } else {
                if (ascending) v1.lowercase().compareTo(v2.lowercase()) else v2.lowercase().compareTo(v1.lowercase())
            }
        })

        // Reassemble the worksheet rows with sorted details (swap cell values in rows)
        val updatedSheets = _activeSpreadsheetData.value.sheets.mapIndexed { idx, sh ->
            if (idx == _activeSpreadsheetData.value.activeSheetIndex) {
                val updatedCells = sh.cells.toMutableMap()
                for (i in rowIndices.indices) {
                    val originalRow = rowIndices[i]
                    val sortedSourceRow = sortedRows[i].key

                    // Replace entire row data elements across sorted keys
                    for (c in 0 until sh.columnCount) {
                        val cLetter = FormulaEvaluator.colIndexToLetter(c)
                        val originalCellKey = "$cLetter$originalRow"
                        val sortedSourceCellKey = "$cLetter$sortedSourceRow"

                        // Retrieve the cell data at sorted position
                        val dataToPlace = sheet.cells[sortedSourceCellKey]
                        if (dataToPlace != null) {
                            updatedCells[originalCellKey] = dataToPlace
                        } else {
                            updatedCells.remove(originalCellKey)
                        }
                    }
                }
                sh.copy(cells = updatedCells)
            } else {
                sh
            }
        }

        _activeSpreadsheetData.value = _activeSpreadsheetData.value.copy(sheets = updatedSheets)
        selectCell(_selectedCell.value)
        recalculateFormulas()
        autoSaveActiveSpreadsheet()
    }

    // ==========================================
    // IMPORTS (CSV AND XLSX)
    // ==========================================

    fun importCsv(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: throw FileNotFoundException("Could not open file.")
                }

                // Create a clear descriptive name
                val fileName = getFileNameFromUri(context, uri) ?: "Imported CSV"

                withContext(Dispatchers.IO) {
                    val worksheet = FileService.importFromCsv(content, "Sheet1")
                    val sheetData = SpreadsheetData(sheets = listOf(worksheet), activeSheetIndex = 0)
                    val json = dataAdapter.toJson(sheetData)
                    val entity = SpreadsheetEntity(name = fileName, serializedData = json)

                    val newId = repository.insert(entity)
                    withContext(Dispatchers.Main) {
                        openSpreadsheet(newId)
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown import error")
                }
            }
        }
    }

    fun importXlsx(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val sheetData = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        FileService.importFromXlsx(stream)
                    } ?: throw FileNotFoundException("Could not open file.")
                }

                val fileName = getFileNameFromUri(context, uri) ?: "Imported Excel"

                withContext(Dispatchers.IO) {
                    val json = dataAdapter.toJson(sheetData)
                    val entity = SpreadsheetEntity(name = fileName, serializedData = json)

                    val newId = repository.insert(entity)
                    withContext(Dispatchers.Main) {
                        openSpreadsheet(newId)
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown Excel import error")
                }
            }
        }
    }

    // ==========================================
    // EXPORTS (CSV AND XLSX)
    // ==========================================

    fun exportToCsv(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val activeSheet = getActiveWorksheet()
                val csvContent = withContext(Dispatchers.Default) {
                    FileService.exportToCsv(activeSheet)
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                            writer.write(csvContent)
                        }
                    } ?: throw IOException("Could not open export destination.")
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Export failed")
                }
            }
        }
    }

    fun exportToXlsx(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val activeData = _activeSpreadsheetData.value

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        FileService.exportToXlsx(activeData, outputStream)
                    } ?: throw IOException("Could not open export destination.")
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Export failed")
                }
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)?.substringBeforeLast(".")
                }
            }
        } catch (e: Exception) {
            // Ignore & fallback to null
        }
        return name
    }
}
