package com.germanchoconta.gymtracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCsvExporterTest {
    @Test
    fun csvUsesStableHeadersExactMachineValuesAndUtc() {
        val text = WorkoutCsvExporter.encode(SyntheticBackupFixtures.complex()).toString(Charsets.UTF_8)
        val rows = text.split("\r\n")

        assertEquals(WorkoutCsvExporter.headers.joinToString(","), rows.first())
        assertTrue(text.contains("1970-01-01T00:00:01Z"))
        assertTrue(text.contains("1970-01-01T00:00:05Z"))
        assertTrue(text.contains(",42500,42.5,10,15,1.5\r\n"))
        assertTrue(text.contains(",123456,123.456,5,0,0\r\n"))
        assertTrue(text.contains("Synthetic Row \"\"Ω\"\""))
    }

    @Test
    fun csvQuotesCommasQuotesNewlinesAndPreservesUnicode() {
        assertEquals("plain", WorkoutCsvExporter.escape("plain"))
        assertEquals("\"a,b\"", WorkoutCsvExporter.escape("a,b"))
        assertEquals("\"a\"\"b\"", WorkoutCsvExporter.escape("a\"b"))
        assertEquals("\"a\nb\"", WorkoutCsvExporter.escape("a\nb"))
        assertEquals("Ω", WorkoutCsvExporter.escape("Ω"))
    }

    @Test
    fun readableDecimalsAreLocaleIndependentAndLosslessAlongsideMachineColumns() {
        assertEquals("42.5", WorkoutCsvExporter.formatGramsAsKg(42_500))
        assertEquals("123.456", WorkoutCsvExporter.formatGramsAsKg(123_456))
        assertEquals("1.5", WorkoutCsvExporter.formatTenths(15))
        assertEquals("0", WorkoutCsvExporter.formatTenths(0))
    }
}
