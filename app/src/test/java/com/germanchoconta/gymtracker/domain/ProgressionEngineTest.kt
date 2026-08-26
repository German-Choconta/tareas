package com.germanchoconta.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {
    private val target = ProgressionTarget(
        minReps = 8,
        maxReps = 12,
        targetRirTenths = 20,
        loadIncrementGrams = 2_500L,
    )

    @Test
    fun noHistoryProducesNoBaselineWithoutInventingTargets() {
        val result = ProgressionEngine.recommend(0, emptyList(), target)

        assertEquals(ProgressionAction.NO_BASELINE, result.action)
        assertNull(result.suggestedLoadGrams)
        assertNull(result.suggestedReps)
    }

    @Test
    fun topOfRangeUsesExactIntegerGramIncrement() {
        val result = ProgressionEngine.recommend(
            setPosition = 0,
            observations = listOf(observation(loadGrams = 40_000L, reps = 12, rirTenths = 20)),
            target = target,
        )

        assertEquals(ProgressionAction.INCREASE_LOAD, result.action)
        assertEquals(42_500L, result.suggestedLoadGrams)
        assertEquals(8, result.suggestedReps)
    }

    @Test
    fun topOfRangeWithoutActualRirStillIncreasesWithExplicitRepsOnlyRationale() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(observation(loadGrams = 40_000L, reps = 12, rirTenths = null)),
            target,
        )

        assertEquals(ProgressionAction.INCREASE_LOAD, result.action)
        assertTrue(result.reason.contains("solo reps"))
    }

    @Test
    fun harderThanTargetRirBlocksIncrease() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(observation(loadGrams = 40_000L, reps = 12, rirTenths = 10)),
            target,
        )

        assertEquals(ProgressionAction.HOLD_LOAD, result.action)
        assertEquals(40_000L, result.suggestedLoadGrams)
    }

    @Test
    fun targetRirAbsentDoesNotGateIncrease() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(observation(loadGrams = 40_000L, reps = 12, rirTenths = 0)),
            target.copy(targetRirTenths = null),
        )

        assertEquals(ProgressionAction.INCREASE_LOAD, result.action)
        assertEquals(42_500L, result.suggestedLoadGrams)
    }

    @Test
    fun insideRangeHoldsLoadAndAddsOneRepAim() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(observation(loadGrams = 40_000L, reps = 10)),
            target,
        )

        assertEquals(ProgressionAction.HOLD_LOAD, result.action)
        assertEquals(40_000L, result.suggestedLoadGrams)
        assertEquals(11, result.suggestedReps)
    }

    @Test
    fun oneBelowRangeSessionNeverReduces() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(observation(workoutId = "w2", startedAt = 200L, loadGrams = 40_000L, reps = 7)),
            target,
        )

        assertEquals(ProgressionAction.HOLD_LOAD, result.action)
        assertEquals(40_000L, result.suggestedLoadGrams)
    }

    @Test
    fun twoConsecutiveBelowRangeSessionsAtSameLoadReduceOneIncrement() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(
                observation(workoutId = "w1", startedAt = 100L, loadGrams = 40_000L, reps = 6),
                observation(workoutId = "w2", startedAt = 200L, loadGrams = 40_000L, reps = 7),
            ),
            target,
        )

        assertEquals(ProgressionAction.REDUCE_LOAD, result.action)
        assertEquals(37_500L, result.suggestedLoadGrams)
        assertEquals(8, result.suggestedReps)
    }

    @Test
    fun differentLoadsAcrossTwoBelowRangeSessionsRequireReview() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(
                observation(workoutId = "w1", startedAt = 100L, loadGrams = 42_500L, reps = 6),
                observation(workoutId = "w2", startedAt = 200L, loadGrams = 40_000L, reps = 7),
            ),
            target,
        )

        assertEquals(ProgressionAction.REVIEW, result.action)
        assertNull(result.suggestedLoadGrams)
    }

    @Test
    fun zeroLoadBaselineNeverInventsExternalLoad() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(observation(loadGrams = 0L, reps = 10)),
            target,
        )

        assertEquals(ProgressionAction.HOLD_LOAD, result.action)
        assertNull(result.suggestedLoadGrams)
        assertEquals(11, result.suggestedReps)
    }

    @Test
    fun samePositionAndDifferentWorkoutDeduplicationAreDeterministic() {
        val input = listOf(
            observation(workoutId = "older", startedAt = 100L, setPosition = 0, loadGrams = 30_000L, reps = 8),
            observation(workoutId = "latest", startedAt = 200L, workoutSetId = "b", setPosition = 0, loadGrams = 40_000L, reps = 10),
            observation(workoutId = "latest", startedAt = 200L, workoutSetId = "a", setPosition = 0, loadGrams = 99_000L, reps = 1),
            observation(workoutId = "latest", startedAt = 200L, workoutSetId = "z", setPosition = 1, loadGrams = 50_000L, reps = 12),
        )

        val first = ProgressionEngine.recommend(0, input, target)
        val shuffled = ProgressionEngine.recommend(0, input.reversed(), target)

        assertEquals(first, shuffled)
        assertEquals(ProgressionAction.HOLD_LOAD, first.action)
        assertEquals(40_000L, first.suggestedLoadGrams)
    }

    @Test
    fun differingNumberOfSetsDoesNotBorrowAnotherPosition() {
        val result = ProgressionEngine.recommend(
            setPosition = 2,
            observations = listOf(
                observation(setPosition = 0, loadGrams = 40_000L, reps = 12),
                observation(setPosition = 1, loadGrams = 40_000L, reps = 12),
            ),
            target = target,
        )

        assertEquals(ProgressionAction.NO_BASELINE, result.action)
    }

    @Test
    fun loadOverflowProducesReviewInsteadOfWrapping() {
        val result = ProgressionEngine.recommend(
            0,
            listOf(observation(loadGrams = Long.MAX_VALUE - 1L, reps = 12)),
            target.copy(loadIncrementGrams = 2L),
        )

        assertEquals(ProgressionAction.REVIEW, result.action)
        assertNull(result.suggestedLoadGrams)
    }

    @Test
    fun reductionFloorsAtZero() {
        val smallTarget = target.copy(loadIncrementGrams = 5_000L)
        val result = ProgressionEngine.recommend(
            0,
            listOf(
                observation(workoutId = "w1", startedAt = 100L, loadGrams = 2_500L, reps = 6),
                observation(workoutId = "w2", startedAt = 200L, loadGrams = 2_500L, reps = 7),
            ),
            smallTarget,
        )

        assertEquals(ProgressionAction.REDUCE_LOAD, result.action)
        assertEquals(0L, result.suggestedLoadGrams)
    }

    private fun observation(
        workoutId: String = "workout",
        startedAt: Long = 100L,
        workoutSetId: String = "set",
        setPosition: Int = 0,
        loadGrams: Long,
        reps: Int,
        rirTenths: Int? = null,
    ) = ProgressionObservation(
        workoutId = workoutId,
        startedAt = startedAt,
        workoutSetId = workoutSetId,
        setPosition = setPosition,
        loadGrams = loadGrams,
        reps = reps,
        rirTenths = rirTenths,
    )
}
