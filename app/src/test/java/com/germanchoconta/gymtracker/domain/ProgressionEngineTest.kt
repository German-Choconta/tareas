package com.germanchoconta.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionEngineTest {
    private val target = ProgressionTarget(
        minReps = 8,
        maxReps = 12,
        targetRir = 1,
        loadIncrementKg = 2.5,
    )

    @Test
    fun increasesLoadWhenEverySetHitsTopOfRange() {
        val result = ProgressionEngine.recommend(
            currentLoadKg = 40.0,
            lastSession = listOf(
                SetPerformance(40.0, 12, 2),
                SetPerformance(40.0, 12, 1),
                SetPerformance(40.0, 12, 1),
            ),
            target = target,
        )

        assertEquals(ProgressionAction.INCREASE_LOAD, result.action)
        assertEquals(42.5, result.suggestedLoadKg, 0.0)
    }

    @Test
    fun keepsLoadWhileUserCanStillAddReps() {
        val result = ProgressionEngine.recommend(
            currentLoadKg = 40.0,
            lastSession = listOf(
                SetPerformance(40.0, 11, 2),
                SetPerformance(40.0, 10, 1),
                SetPerformance(40.0, 9, 1),
            ),
            target = target,
        )

        assertEquals(ProgressionAction.KEEP_LOAD, result.action)
        assertEquals(40.0, result.suggestedLoadKg, 0.0)
    }

    @Test
    fun reducesLoadAfterRepeatedUnderPerformance() {
        val result = ProgressionEngine.recommend(
            currentLoadKg = 40.0,
            lastSession = listOf(
                SetPerformance(40.0, 7, 0),
                SetPerformance(40.0, 6, 0),
                SetPerformance(40.0, 8, 0),
            ),
            target = target,
            consecutiveUnderTargetSessions = 1,
        )

        assertEquals(ProgressionAction.REDUCE_LOAD, result.action)
        assertEquals(37.5, result.suggestedLoadKg, 0.0)
    }

    @Test
    fun calculatesEpleyEstimatedOneRepMax() {
        val e1rm = ProgressionEngine.estimatedOneRepMaxEpley(loadKg = 100.0, reps = 10)
        assertEquals(133.333, e1rm, 0.001)
    }
}
