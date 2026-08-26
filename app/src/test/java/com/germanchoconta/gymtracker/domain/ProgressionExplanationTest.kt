package com.germanchoconta.gymtracker.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionExplanationTest {
    @Test
    fun recommendationReasonStartsWithConcreteCanonicalWorkEvidence() {
        val result = ProgressionEngine.recommend(
            setPosition = 0,
            observations = listOf(
                ProgressionObservation(
                    workoutId = "synthetic-workout",
                    startedAt = 1_000L,
                    workoutSetId = "synthetic-set",
                    setPosition = 0,
                    loadGrams = 40_000L,
                    reps = 12,
                    rirTenths = 20,
                ),
            ),
            target = ProgressionTarget(
                minReps = 8,
                maxReps = 12,
                targetRirTenths = 20,
                loadIncrementGrams = 2_500L,
            ),
        )

        assertTrue(result.reason.startsWith("BASE WORK • 40 kg × 12 · RIR 2\n"))
    }
}
