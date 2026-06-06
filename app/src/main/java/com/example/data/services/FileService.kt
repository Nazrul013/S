package com.example.data.services

import android.content.Context
import android.net.Uri
import com.example.data.models.CellData
import com.example.data.models.CellFormatting
import com.example.data.models.SpreadsheetData
import com.example.data.models.Worksheet
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileService {

    // ==========================================
    // CSV IMPORT / EXPORT (Single Worksheet)
    // ==========================================

    fun exportToCsv(worksheet: Worksheet): String {
        val writer = StringWriter()
        val maxRow = worksheet.cells.keys
            .mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }
            .maxOrNull() ?: 10
        val maxCol = worksheet.cells.keys
            .mapNotNull { it.filter { char -> char.isLetter() } }
            .map { FormulaEvaluator.colLetterToIndex(it) }
            .maxOrNull() ?: 5

        for (r in 1..maxOf(maxRow, worksheet.rowCount)) {
            val rowCells = mutableListOf<String>()
            for (c in 0..maxOf(maxCol, worksheet.columnCount - 1)) {
                val colLetter = FormulaEvaluator.colIndexToLetter(c)
                val cellKey = "$colLetter$r"
                val cellValue = worksheet.cells[cellKey]?.value ?: ""
                rowCells.add(escapeCsv(cellValue))
            }
            writer.write(rowCells.joinToString(","))
            writer.write("\n")
        }
        return writer.toString()
    }

    private fun escapeCsv(str: String): String {
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\""
        }
        return str
    }

    fun importFromCsv(csvText: String, sheetName: String = "Imported"): Worksheet {
        val cells = mutableMapOf<String, CellData>()
        val reader = BufferedReader(StringReader(csvText))
        var rowIdx = 1
        var maxColCount = 10

        var line = reader.readLine()
        while (line != null) {
            val parsedRow = parseCsvLine(line)
            if (parsedRow.size > maxColCount) {
                maxColCount = parsedRow.size
            }
            for (colIdx in parsedRow.indices) {
                val value = parsedRow[colIdx]
                if (value.isNotEmpty()) {
                    val colLetter = FormulaEvaluator.colIndexToLetter(colIdx)
                    cells["$colLetter$rowIdx"] = CellData(value = value)
                }
            }
            rowIdx++
            line = reader.readLine()
        }

        return Worksheet(
            name = sheetName,
            rowCount = maxOf(100, rowIdx + 10),
            columnCount = maxOf(26, maxColCount + 5),
            cells = cells
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var insideQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            if (char == '"') {
                if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    curVal.append('"')
                    i++
                } else {
                    insideQuotes = !insideQuotes
                }
            } else if (char == ',' && !insideQuotes) {
                result.add(curVal.toString())
                curVal = StringBuilder()
            } else {
                curVal.append(char)
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    // ==========================================
    // XML ESCAPING FOR XLSX GENERATOR
    // ==========================================

    private fun String.escapeXml(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // ==========================================
    // LIGHTWEIGHT XLSX EXPORTER
    // ==========================================

    fun exportToXlsx(data: SpreadsheetData, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        val contentTypesBuilder = StringBuilder()
        contentTypesBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        contentTypesBuilder.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n")
        contentTypesBuilder.append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n")
        contentTypesBuilder.append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n")
        contentTypesBuilder.append("  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n")
        contentTypesBuilder.append("  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n")
        for (i in data.sheets.indices) {
            contentTypesBuilder.append("  <Override PartName=\"/xl/worksheets/sheet${i+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n")
        }
        contentTypesBuilder.append("</Types>")
        zip.write(contentTypesBuilder.toString().toByteArray())
        zip.closeEntry()

        // 2. _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
        zip.write(relsXml.toByteArray())
        zip.closeEntry()

        // 3. xl/_rels/workbook.xml.rels
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        val workbookRelsBuilder = StringBuilder()
        workbookRelsBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        workbookRelsBuilder.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n")
        for (i in data.sheets.indices) {
            workbookRelsBuilder.append("  <Relationship Id=\"rId${i+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i+1}.xml\"/>\n")
        }
        workbookRelsBuilder.append("  <Relationship Id=\"rIdStyle\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n")
        workbookRelsBuilder.append("</Relationships>")
        zip.write(workbookRelsBuilder.toString().toByteArray())
        zip.closeEntry()

        // 4. xl/workbook.xml
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        val workbookBuilder = StringBuilder()
        workbookBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        workbookBuilder.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n")
        workbookBuilder.append("  <sheets>\n")
        for (i in data.sheets.indices) {
            val sheet = data.sheets[i]
            workbookBuilder.append("    <sheet name=\"${sheet.name.escapeXml()}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>\n")
        }
        workbookBuilder.append("  </sheets>\n")
        workbookBuilder.append("</workbook>")
        zip.write(workbookBuilder.toString().toByteArray())
        zip.closeEntry()

        // 5. xl/styles.xml (Include general formatting layout)
        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="1">
                <font><sz val="11"/><name val="Calibri"/></font>
              </fonts>
              <fills count="2">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
              </fills>
              <borders count="1">
                <border><left/><right/><top/><bottom/></border>
              </borders>
              <cellStyleXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
              </cellStyleXfs>
              <cellXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
              </cellXfs>
            </styleSheet>
        """.trimIndent()
        zip.write(stylesXml.toByteArray())
        zip.closeEntry()

        // 6. xl/worksheets/sheet${i+1}.xml
        for (idx in data.sheets.indices) {
            val sheet = data.sheets[idx]
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${idx+1}.xml"))
            val sheetWriter = BufferedWriter(OutputStreamWriter(zip, "UTF-8"))
            sheetWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            sheetWriter.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
            sheetWriter.write("  <sheetData>\n")

            // Grab cells and group by rows
            val cellsByRow = mutableMapOf<Int, MutableMap<String, CellData>>()
            for ((key, cell) in sheet.cells) {
                val rowNum = key.filter { it.isDigit() }.toIntOrNull() ?: continue
                val rowCells = cellsByRow.getOrPut(rowNum) { mutableMapOf() }
                rowCells[key] = cell
            }

            val maxRow = cellsByRow.keys.maxOrNull() ?: sheet.rowCount
            for (row in 1..maxOf(maxRow, 10)) {
                val rowCells = cellsByRow[row]
                if (rowCells != null && rowCells.isNotEmpty()) {
                    sheetWriter.write("    <row r=\"$row\">\n")
                    // Sort row cell keys (A1, B1, etc.)
                    val sortedKeys = rowCells.keys.sortedWith(Comparator { k1, k2 ->
                        val col1 = FormulaEvaluator.colLetterToIndex(k1.filter { it.isLetter() })
                        val col2 = FormulaEvaluator.colLetterToIndex(k2.filter { it.isLetter() })
                        col1.compareTo(col2)
                    })
                    for (cellKey in sortedKeys) {
                        val cell = rowCells[cellKey]!!
                        val valStr = cell.value
                        if (valStr.startsWith("=")) {
                            // Saving formulas in excel formatted sheets!
                            val formula = valStr.substring(1).escapeXml()
                            sheetWriter.write("      <c r=\"$cellKey\">\n")
                            sheetWriter.write("        <f>$formula</f>\n")
                            sheetWriter.write("      </c>\n")
                        } else {
                            val cleanVal = valStr.escapeXml()
                            // Write as inline string to make shared strings redundant
                            sheetWriter.write("      <c r=\"$cellKey\" t=\"inlineStr\">\n")
                            sheetWriter.write("        <is><t>$cleanVal</t></is>\n")
                            sheetWriter.write("      </c>\n")
                        }
                    }
                    sheetWriter.write("    </row>\n")
                }
            }

            sheetWriter.write("  </sheetData>\n")
            sheetWriter.write("</worksheet>")
            sheetWriter.flush()
            zip.closeEntry()
        }

        zip.close()
    }

    // ==========================================
    // XLSX IMPORTER
    // ==========================================

    fun importFromXlsx(inputStream: InputStream): SpreadsheetData {
        val sheets = mutableListOf<Worksheet>()
        val sharedStrings = mutableListOf<String>()
        val tempWorksheets = mutableListOf<Pair<String, String>>() // Key: rId, Value: XML string
        val sheetNames = mutableMapOf<String, String>() // Key: rId/sheetId, Value: Name

        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        while (entry != null) {
            val entryName = entry.name
            if (entryName == "xl/sharedStrings.xml") {
                val bytes = zip.readBytes()
                val xmlStr = String(bytes, Charsets.UTF_8)
                // Extract strings from sharedStrings table using regex matches
                val stringRegex = Regex("<t[^>]*>(.*?)</t>")
                stringRegex.findAll(xmlStr).forEach {
                    sharedStrings.add(it.groupValues[1].replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'"))
                }
            } else if (entryName.startsWith("xl/worksheets/sheet") && entryName.endsWith(".xml")) {
                val bytes = zip.readBytes()
                val xmlStr = String(bytes, Charsets.UTF_8)
                val sheetNumStr = entryName.substringAfter("sheet").substringBefore(".xml")
                tempWorksheets.add(Pair(sheetNumStr, xmlStr))
            } else if (entryName == "xl/workbook.xml") {
                val bytes = zip.readBytes()
                val xmlStr = String(bytes, Charsets.UTF_8)
                // Find sheet declarations like <sheet name="My Sheet" sheetId="1" r:id="rId1" />
                val sheetDeclRegex = Regex("<sheet[^>]+name=\"([^\"]+)\"[^>]+sheetId=\"([^\"]+)\"[^>]*>")
                sheetDeclRegex.findAll(xmlStr).forEach {
                    val name = it.groupValues[1]
                    val id = it.groupValues[2]
                    sheetNames[id] = name
                }
            }
            entry = zip.nextEntry
        }
        zip.close()

        // If sheet names was empty, define a default matching sheet name mappings
        if (sheetNames.isEmpty()) {
            for (i in tempWorksheets.indices) {
                sheetNames[(i+1).toString()] = "Sheet${i+1}"
            }
        }

        // Process sheet XML strings
        for (pair in tempWorksheets) {
            val sheetId = pair.first
            val xmlStr = pair.second
            val name = sheetNames[sheetId] ?: "Sheet$sheetId"

            val cells = mutableMapOf<String, CellData>()

            // Regex parsing cells: <c r="A1" t="s"><v>0</v></c> or <c r="A1"><f>SUM(B1:B2)</f></c>
            val cellRegex = Regex("<c r=\"([A-Z]+[0-9]+)\"[^>]*>(.*?)</c>")
            val vRegex = Regex("<v>(.*?)</v>")
            val tRegex = Regex("<t[^>]*>(.*?)</t>")
            val fRegex = Regex("<f>(.*?)</f>")

            cellRegex.findAll(xmlStr).forEach { mCell ->
                val coord = mCell.groupValues[1]
                val content = mCell.groupValues[2]

                var value = ""
                // Checks for Excel formulas
                val fMatch = fRegex.find(content)
                if (fMatch != null) {
                    value = "=" + fMatch.groupValues[1]
                } else {
                    val tagTypeMatch = Regex("<c[^>]+t=\"([^\"]+)\"").find(mCell.value)
                    val cellType = tagTypeMatch?.groupValues?.get(1) ?: ""

                    if (cellType == "s") {
                        // Shared Strings Dictionary Match
                        val indexVal = vRegex.find(content)?.groupValues?.get(1)?.toIntOrNull()
                        if (indexVal != null && indexVal in sharedStrings.indices) {
                            value = sharedStrings[indexVal]
                        }
                    } else if (cellType == "inlineStr") {
                        val inlineText = tRegex.find(content)?.groupValues?.get(1)
                        if (inlineText != null) {
                            value = inlineText
                        }
                    } else {
                        // Standard cell coordinate matching (Direct value / numbers)
                        val numVal = vRegex.find(content)?.groupValues?.get(1)
                        if (numVal != null) {
                            value = numVal
                        }
                    }
                }

                value = value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")

                if (value.isNotEmpty()) {
                    cells[coord] = CellData(value = value, formatting = CellFormatting())
                }
            }

            // Estimate sheet size dynamically
            val maxRow = cells.keys.mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }.maxOrNull() ?: 10
            val maxCol = cells.keys.mapNotNull { it.filter { c -> c.isLetter() } }.map { FormulaEvaluator.colLetterToIndex(it) }.maxOrNull() ?: 5

            sheets.add(
                Worksheet(
                    name = name,
                    rowCount = maxOf(100, maxRow + 10),
                    columnCount = maxOf(26, maxCol + 5),
                    cells = cells
                )
            )
        }

        if (sheets.isEmpty()) {
            sheets.add(Worksheet(name = "Sheet1"))
        }

        return SpreadsheetData(
            sheets = sheets,
            activeSheetIndex = 0
        )
    }
}
