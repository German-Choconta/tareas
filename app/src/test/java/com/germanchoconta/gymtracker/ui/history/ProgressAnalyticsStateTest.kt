package com.germanchoconta.gymtracker.ui.history

import java.math.BigInteger
import java.time.LocalDate
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAnalyticsStateTest {
    @Test
    fun temporalAxisPreservesRelativeCalendarGapsInsteadOfEqualSessionSpacing() {
        val dates = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 7, 1),
        )
        val axis = buildProgressTemporalAxis(dates.mapIndexed(::point))

        val shortGap = axis.xValues[1] - axis.xValues[0]
        val longGap = axis.xValues[2] - axis.xValues[1]
        assertTrue(shortGap > 0.0)
        assertTrue(longGap > shortGap * 50.0)
        dates.forEachIndexed { index, date -> assertEquals(date, axis.dateAt(axis.xValues[index])) }
    }

    @Test
    fun sameDayPointsRemainDistinctDeterministicAndVicoPrecisionSafe() {
        val dates = listOf(
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 2, 11),
        )
        val points = dates.mapIndexed(::point)
        val first = buildProgressTemporalAxis(points)
        val second = buildProgressTemporalAxis(points)

        assertEquals(first, second)
        assertTrue(first.xValues.zipWithNext().all { (left, right) -> right > left })
        first.xValues.forEach { value ->
            assertEquals((value * 10_000.0).roundToLong() / 10_000.0, value, 0.0)
        }
        assertEquals(dates[0], first.dateAt(first.xValues[0]))
        assertEquals(dates[1], first.dateAt(first.xValues[1]))
        assertEquals(dates[2], first.dateAt(first.xValues[2]))
    }

    private fun point(index: Int, date: LocalDate) = ProgressChartPoint(
        stableId = "synthetic-axis-$index",
        localDate = date,
        exactValue = BigInteger.valueOf((index + 1).toLong()),
    )
}
