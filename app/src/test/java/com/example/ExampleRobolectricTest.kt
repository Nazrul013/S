package com.example

import com.example.data.models.CellData
import com.example.data.models.Worksheet
import com.example.data.services.FormulaEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun testFormulaEvaluator_basicSums() {
        val cellsMap = HashMap<String, CellData>()
        cellsMap["A1"] = CellData(value = "15")
        cellsMap["A2"] = CellData(value = "25")
        cellsMap["A3"] = CellData(value = "=SUM(A1:A2)")

        val worksheet = Worksheet(name = "Sheet 1", cells = cellsMap)

        val computed = FormulaEvaluator.evaluateWorksheet(worksheet)
        assertEquals("40.0", computed["A3"])
    }

    @Test
    fun testFormulaEvaluator_complexOperators() {
        val cellsMap = HashMap<String, CellData>()
        cellsMap["B1"] = CellData(value = "100")
        cellsMap["B2"] = CellData(value = "=IF(B1>=50, \"Eligible\", \"Ineligible\")")

        val worksheet = Worksheet(name = "Sheet 1", cells = cellsMap)

        val computed = FormulaEvaluator.evaluateWorksheet(worksheet)
        assertEquals("Eligible", computed["B2"])
    }
}
