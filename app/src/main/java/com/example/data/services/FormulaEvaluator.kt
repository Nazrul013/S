package com.example.data.services

import com.example.data.models.Worksheet
import com.example.data.models.CellData
import kotlin.math.max
import kotlin.math.min

object FormulaEvaluator {

    private val cellRefRegex = Regex("^([A-Z]+)([0-9]+)$", RegexOption.IGNORE_CASE)
    private val rangeRegex = Regex("^([A-Z]+)([0-9]+):([A-Z]+)([0-9]+)$", RegexOption.IGNORE_CASE)

    fun colLetterToIndex(col: String): Int {
        var result = 0
        val upperCol = col.uppercase()
        for (char in upperCol) {
            result = result * 26 + (char - 'A' + 1)
        }
        return result - 1
    }

    fun colIndexToLetter(index: Int): String {
        var temp = index
        val sb = StringBuilder()
        while (temp >= 0) {
            sb.append(('A' + (temp % 26)).toChar())
            temp = temp / 26 - 1
        }
        return sb.reverse().toString()
    }

    fun isCellReference(str: String): Boolean {
        return cellRefRegex.matches(str.trim())
    }

    /**
     * Evaluates all cells in a worksheet and returns a map of cell coordinate -> computed display value.
     */
    fun evaluateWorksheet(worksheet: Worksheet): Map<String, String> {
        val computedCache = mutableMapOf<String, String>()
        val visiting = mutableSetOf<String>()

        for (cellKey in worksheet.cells.keys) {
            evaluateCell(cellKey, worksheet, visiting, computedCache)
        }

        return computedCache
    }

    fun evaluateCell(
        cellKey: String,
        worksheet: Worksheet,
        visiting: MutableSet<String>,
        computedCache: MutableMap<String, String>
    ): String {
        val upperKey = cellKey.uppercase()
        if (computedCache.containsKey(upperKey)) {
            return computedCache[upperKey]!!
        }

        if (visiting.contains(upperKey)) {
            computedCache[upperKey] = "#REF!"
            return "#REF!"
        }

        visiting.add(upperKey)

        val cellData = worksheet.cells[upperKey]
        val rawValue = cellData?.value ?: ""

        val result = if (rawValue.startsWith("=")) {
            val formula = rawValue.substring(1).trim()
            try {
                evalFormula(formula, worksheet, visiting, computedCache)
            } catch (e: Exception) {
                "#VALUE!"
            }
        } else {
            rawValue
        }

        visiting.remove(upperKey)
        computedCache[upperKey] = result
        return result
    }

    private fun evalFormula(
        formula: String,
        worksheet: Worksheet,
        visiting: MutableSet<String>,
        computedCache: MutableMap<String, String>
    ): String {
        val upperFormula = formula.trim()

        // Handle IF formula
        if (upperFormula.startsWith("IF(", ignoreCase = true)) {
            return evalIf(upperFormula, worksheet, visiting, computedCache)
        }

        // Handle built-in analytical formulas
        val funcMatch = Regex("^([A-Z]+)\\((.*)\\)$", RegexOption.IGNORE_CASE).find(upperFormula)
        if (funcMatch != null) {
            val funcName = funcMatch.groupValues[1].uppercase()
            val argumentsStr = funcMatch.groupValues[2]

            val doubleValues = mutableListOf<Double>()
            var numberCellsParsed = 0

            // Split arguments by comma outside parenthetical regions
            val arguments = splitArguments(argumentsStr)

            for (arg in arguments) {
                val cleanArg = arg.trim()
                if (cleanArg.contains(":")) {
                    val rangeParts = expandRange(cleanArg)
                    for (rc in rangeParts) {
                        val evaluated = evaluateCell(rc, worksheet, visiting, computedCache)
                        evaluated.toDoubleOrNull()?.let {
                            doubleValues.add(it)
                            numberCellsParsed++
                        }
                    }
                } else if (isCellReference(cleanArg)) {
                    val evaluated = evaluateCell(cleanArg, worksheet, visiting, computedCache)
                    evaluated.toDoubleOrNull()?.let {
                        doubleValues.add(it)
                        numberCellsParsed++
                    }
                } else {
                    cleanArg.toDoubleOrNull()?.let {
                        doubleValues.add(it)
                        numberCellsParsed++
                    }
                }
            }

            return when (funcName) {
                "SUM" -> doubleValues.sum().toString()
                "AVERAGE" -> {
                    if (doubleValues.isEmpty()) "0"
                    else (doubleValues.sum() / doubleValues.size).toString()
                }
                "MIN" -> {
                    if (doubleValues.isEmpty()) "0"
                    else doubleValues.minOrNull().toString()
                }
                "MAX" -> {
                    if (doubleValues.isEmpty()) "0"
                    else doubleValues.maxOrNull().toString()
                }
                "COUNT" -> numberCellsParsed.toString()
                else -> "#NAME?"
            }
        }

        // Help parse basic arithmetic (=A1+B2, =10*5, etc) if not matching standard functions
        return evalBasicArithmetic(upperFormula, worksheet, visiting, computedCache)
    }

    private fun splitArguments(argsStr: String): List<String> {
        val result = mutableListOf<String>()
        var currentToken = StringBuilder()
        var parenCount = 0
        for (char in argsStr) {
            if (char == '(') parenCount++
            else if (char == ')') parenCount--

            if (char == ',' && parenCount == 0) {
                result.add(currentToken.toString())
                currentToken = StringBuilder()
            } else {
                currentToken.append(char)
            }
        }
        if (currentToken.isNotEmpty()) {
            result.add(currentToken.toString())
        }
        return result
    }

    private fun expandRange(rangeStr: String): List<String> {
        val match = rangeRegex.matchEntire(rangeStr.trim()) ?: return emptyList()
        val startColStr = match.groupValues[1]
        val startRowStr = match.groupValues[2]
        val endColStr = match.groupValues[3]
        val endRowStr = match.groupValues[4]

        val startCol = colLetterToIndex(startColStr)
        val startRow = startRowStr.toInt() - 1
        val endCol = colLetterToIndex(endColStr)
        val endRow = endRowStr.toInt() - 1

        val colFrom = min(startCol, endCol)
        val colTo = max(startCol, endCol)
        val rowFrom = min(startRow, endRow)
        val rowTo = max(startRow, endRow)

        val keys = mutableListOf<String>()
        for (c in colFrom..colTo) {
            val colLetter = colIndexToLetter(c)
            for (r in rowFrom..rowTo) {
                keys.add("$colLetter${r + 1}")
            }
        }
        return keys
    }

    private fun evalIf(
        formula: String,
        worksheet: Worksheet,
        visiting: MutableSet<String>,
        computedCache: MutableMap<String, String>
    ): String {
        // IF(condition, trueVal, falseVal)
        val parsedContent = formula.substring(3, formula.length - 1)
        val args = splitArguments(parsedContent)
        if (args.size < 3) return "#VALUE!"

        val condition = args[0].trim()
        val trueVal = args[1].trim()
        val falseVal = args[2].trim()

        val isTrue = evaluateCondition(condition, worksheet, visiting, computedCache)

        val resultVal = if (isTrue) trueVal else falseVal
        // Evaluate result if it is also a cell or formula
        return if (isCellReference(resultVal)) {
            evaluateCell(resultVal, worksheet, visiting, computedCache)
        } else if (resultVal.startsWith("\"") && resultVal.endsWith("\"")) {
            resultVal.substring(1, resultVal.length - 1)
        } else {
            resultVal
        }
    }

    private fun evaluateCondition(
        cond: String,
        worksheet: Worksheet,
        visiting: MutableSet<String>,
        computedCache: MutableMap<String, String>
    ): Boolean {
        val operators = listOf(">=", "<=", "!=", ">", "<", "=")
        var matchedOperator: String? = null
        var splitIndex = -1

        for (op in operators) {
            val idx = cond.indexOf(op)
            if (idx != -1) {
                matchedOperator = op
                splitIndex = idx
                break
            }
        }

        if (matchedOperator == null || splitIndex == -1) {
            return false
        }

        var leftSymbol = cond.substring(0, splitIndex).trim()
        var rightSymbol = cond.substring(splitIndex + matchedOperator.length).trim()

        // Dereference if they are cells
        val leftVal = if (isCellReference(leftSymbol)) {
            evaluateCell(leftSymbol, worksheet, visiting, computedCache)
        } else {
            leftSymbol.replace("\"", "")
        }

        val rightVal = if (isCellReference(rightSymbol)) {
            evaluateCell(rightSymbol, worksheet, visiting, computedCache)
        } else {
            rightSymbol.replace("\"", "")
        }

        val leftD = leftVal.toDoubleOrNull()
        val rightD = rightVal.toDoubleOrNull()

        return if (leftD != null && rightD != null) {
            when (matchedOperator) {
                ">=" -> leftD >= rightD
                "<=" -> leftD <= rightD
                ">" -> leftD > rightD
                "<" -> leftD < rightD
                "=" -> leftD == rightD
                "!=" -> leftD != rightD
                else -> false
            }
        } else {
            // Compare as strings
            when (matchedOperator) {
                "=" -> leftVal.equals(rightVal, ignoreCase = true)
                "!=" -> !leftVal.equals(rightVal, ignoreCase = true)
                else -> false
            }
        }
    }

    private fun evalBasicArithmetic(
        expr: String,
        worksheet: Worksheet,
        visiting: MutableSet<String>,
        computedCache: MutableMap<String, String>
    ): String {
        // Direct basic operation parsing for two operands, e.g. A1+B2, A1*10
        val sumOp = expr.indexOf('+')
        val subOp = expr.indexOf('-')
        val mulOp = expr.indexOf('*')
        val divOp = expr.indexOf('/')

        val opIndex = when {
            sumOp != -1 -> sumOp
            subOp != -1 -> subOp
            mulOp != -1 -> mulOp
            divOp != -1 -> divOp
            else -> -1
        }

        if (opIndex == -1) {
            return if (isCellReference(expr)) {
                evaluateCell(expr, worksheet, visiting, computedCache)
            } else {
                expr
            }
        }

        val op = expr[opIndex]
        var left = expr.substring(0, opIndex).trim()
        var right = expr.substring(opIndex + 1).trim()

        val leftValStr = if (isCellReference(left)) evaluateCell(left, worksheet, visiting, computedCache) else left
        val rightValStr = if (isCellReference(right)) evaluateCell(right, worksheet, visiting, computedCache) else right

        val leftD = leftValStr.toDoubleOrNull() ?: 0.0
        val rightD = rightValStr.toDoubleOrNull() ?: 0.0

        return when (op) {
            '+' -> (leftD + rightD).toString()
            '-' -> (leftD - rightD).toString()
            '*' -> (leftD * rightD).toString()
            '/' -> if (rightD == 0.0) "#DIV/0!" else (leftD / rightD).toString()
            else -> "#VALUE!"
        }
    }
}
