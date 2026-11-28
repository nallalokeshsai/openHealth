package app.openhealth.core.clinical

import kotlin.test.Test
import kotlin.test.assertEquals

class LabCsvTest {
    @Test
    fun aKnownAnalyteInItsOwnUnitImports() {
        val result = LabCsv.parse(
            "date,analyte,value,unit,lab,note\n" +
                "2026-03-01,HbA1c,5.4,%,City Lab,fasting\n" +
                "2026-03-02,4548-4,5.6,%,,\n",
        )
        assertEquals(2, result.rows.size)
        assertEquals(0, result.problems.size)
        assertEquals(LabAnalyte.HBA1C, result.rows[0].analyte)
        assertEquals("City Lab", result.rows[0].lab)
    }

    @Test
    fun anUnknownAnalyteOrWrongUnitIsAProblemNotAGuess() {
        val result = LabCsv.parse(
            "2026-03-01,Mystery,1,mg/dL\n" +
                "2026-03-01,HbA1c,5.4,mg/dL\n" +
                "not-a-date,HbA1c,5.4,%\n",
        )
        assertEquals(0, result.rows.size)
        assertEquals(3, result.problems.size)
    }
}
