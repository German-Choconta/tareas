package com.germanchoconta.gymtracker.domain

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordEngineTest {
    @Test
    fun heaviestLoadUsesStrictExactGramImprovementsAndKeepsFirstTieWitness() {
        val records = PersonalRecordEngine.calculate(
            listOf(
                fact("set-a", 1_000, 100_000, 8),
                fact("set-b", 2_000, 105_000, 6),
                fact("set-c", 3_000, 105_000, 7),
            ),
        )

        assertEquals("set-b", records.heaviestLoad?.fact?.workoutSetId)
        assertEquals(BigInteger.valueOf(105_000), records.heaviestLoad?.value)
        assertEquals(
            listOf("set-a", "set-b"),
            records.events.filter { it.kind == PersonalRecordKind.HEAVIEST_LOAD }.map { it.workoutSetId },
        )
    }

    @Test
    fun repsAtLoadUsesExactLoadAndNeverBucketsNearbyGramValues() {
        val records = PersonalRecordEngine.calculate(
            listOf(
                fact("100kg-8", 1_000, 100_000, 8),
                fact("100001g-20", 2_000, 100_001, 20),
                fact("100kg-10", 3_000, 100_000, 10),
                fact("100kg-10-tie", 4_000, 100_000, 10),
            ),
        )

        assertEquals(10L, records.repsAtExactLoad.getValue(100_000).value.longValueExact())
        assertEquals(20L, records.repsAtExactLoad.getValue(100_001).value.longValueExact())
        assertEquals("100kg-10", records.repsAtExactLoad.getValue(100_000).fact.workoutSetId)
        assertFalse(
            records.events.any {
                it.kind == PersonalRecordKind.REPS_AT_LOAD && it.workoutSetId == "100kg-10-tie"
            },
        )
    }

    @Test
    fun multipleStrictImprovementsInsideOneSessionAreHistoricalEvents() {
        val records = PersonalRecordEngine.calculate(
            listOf(
                fact("set-1", 1_000, 80_000, 8, workoutId = "session"),
                fact("set-2", 1_000, 85_000, 8, workoutId = "session", setPosition = 1),
                fact("set-3", 1_000, 90_000, 8, workoutId = "session", setPosition = 2),
            ),
        )

        assertEquals(
            listOf("set-1", "set-2", "set-3"),
            records.events.filter { it.kind == PersonalRecordKind.HEAVIEST_LOAD }.map { it.workoutSetId },
        )
        assertEquals("set-3", records.heaviestLoad?.fact?.workoutSetId)
    }

    @Test
    fun estimatedOneRepMaxUsesEpleyExactRationalAndRoundsHalfUpToOneTenthKg() {
        val estimate = PersonalRecordEngine.estimatedOneRepMax(fact("e1rm", 1_000, 100_000, 8))

        assertEquals(BigInteger.valueOf(3_800_000), estimate?.numerator)
        assertEquals(126_700L, estimate?.roundedGrams)
    }

    @Test
    fun estimatedOneRepMaxExcludesOneRepAndAboveTenButOtherRecordsStillCount() {
        val one = fact("one", 1_000, 150_000, 1)
        val eleven = fact("eleven", 2_000, 80_000, 11)
        assertNull(PersonalRecordEngine.estimatedOneRepMax(one))
        assertNull(PersonalRecordEngine.estimatedOneRepMax(eleven))

        val records = PersonalRecordEngine.calculate(listOf(one, eleven))
        assertEquals("one", records.heaviestLoad?.fact?.workoutSetId)
        assertTrue(records.repsAtExactLoad.containsKey(80_000))
    }

    @Test
    fun warmupIncompleteZeroLoadAndActiveWorkoutFactsDoNotGenerateRecords() {
        val records = PersonalRecordEngine.calculate(
            listOf(
                fact("warmup", 1_000, 200_000, 10, type = "WARMUP"),
                fact("incomplete", 2_000, 210_000, 10, completedAt = null),
                fact("zero", 3_000, 0, 20),
                fact("active", 4_000, 220_000, 10, finishedAt = null),
            ),
        )

        assertNull(records.heaviestLoad)
        assertTrue(records.events.isEmpty())
    }

    @Test
    fun workDropAndFailureAreEligibleAndRirDoesNotChangeE1rm() {
        val work = fact("work", 1_000, 90_000, 8, type = "WORK", rirTenths = null)
        val drop = fact("drop", 2_000, 90_000, 8, type = "DROP", rirTenths = 0)
        val failure = fact("failure", 3_000, 90_000, 8, type = "FAILURE", rirTenths = 40)

        val estimates = listOf(work, drop, failure).map { requireNotNull(PersonalRecordEngine.estimatedOneRepMax(it)) }
        assertEquals(1, estimates.map { it.numerator }.distinct().size)
    }

    @Test
    fun highestVolumeCombinesDuplicateExerciseOccurrencesByWorkoutAndUsesBigInteger() {
        val hugeLoad = Long.MAX_VALUE / 4
        val facts = listOf(
            fact("a", 1_000, hugeLoad, 10, workoutId = "session-a", workoutExerciseId = "we-a"),
            fact("b", 1_000, hugeLoad, 10, workoutId = "session-a", workoutExerciseId = "we-b", workoutExercisePosition = 1),
            fact("c", 2_000, 100_000, 10, workoutId = "session-b"),
        )
        val records = PersonalRecordEngine.calculate(facts)
        val expected = BigInteger.valueOf(hugeLoad).multiply(BigInteger.TEN).multiply(BigInteger.TWO)

        assertEquals("session-a", records.highestSessionVolume?.workoutId)
        assertEquals(expected, records.highestSessionVolume?.volumeGramReps)
        assertTrue(expected > BigInteger.valueOf(Long.MAX_VALUE))
    }

    @Test
    fun volumeTieDoesNotReplaceFirstSessionWitness() {
        val records = PersonalRecordEngine.calculate(
            listOf(
                fact("a", 1_000, 100_000, 10, workoutId = "session-a"),
                fact("b", 2_000, 100_000, 10, workoutId = "session-b"),
            ),
        )

        assertEquals("session-a", records.highestSessionVolume?.workoutId)
        assertEquals(
            listOf("session-a"),
            records.events.filter { it.kind == PersonalRecordKind.EXERCISE_SESSION_VOLUME }.map { it.workoutId },
        )
    }

    @Test
    fun sameTimestampOrderingIsDeterministicEvenWhenInputIsReversed() {
        val facts = listOf(
            fact("set-z", 1_000, 110_000, 5, workoutId = "z-workout"),
            fact("set-a", 1_000, 100_000, 5, workoutId = "a-workout"),
        )
        val forward = PersonalRecordEngine.calculate(facts)
        val reverse = PersonalRecordEngine.calculate(facts.reversed())

        assertEquals(forward, reverse)
        assertEquals(
            listOf("set-a", "set-z"),
            forward.events.filter { it.kind == PersonalRecordKind.HEAVIEST_LOAD }.map { it.workoutSetId },
        )
    }

    @Test
    fun previousSessionComparisonUsesLatestTwoFinishedEligibleSessions() {
        val comparison = requireNotNull(
            PersonalRecordEngine.previousSessionComparison(
                listOf(
                    fact("old-1", 1_000, 80_000, 10, workoutId = "old"),
                    fact("new-1", 2_000, 90_000, 8, workoutId = "new"),
                    fact("new-2", 2_000, 85_000, 10, workoutId = "new", setPosition = 1),
                    fact("active", 3_000, 100_000, 5, workoutId = "active", finishedAt = null),
                ),
            ),
        )

        assertEquals("new", comparison.latest.workoutId)
        assertEquals("old", comparison.previous?.workoutId)
        assertEquals(2, comparison.latest.completedSetCount)
    }

    @Test
    fun largeSyntheticHistoryRemainsLinearAndDeterministic() {
        val facts = List(50_000) { index ->
            fact(
                setId = "synthetic-set-${index.toString().padStart(5, '0')}",
                startedAt = index.toLong(),
                loadGrams = 50_000L + index,
                reps = 10,
                workoutId = "synthetic-workout-${index.toString().padStart(5, '0')}",
            )
        }

        val records = PersonalRecordEngine.calculate(facts.reversed())
        assertEquals(99_999L, records.heaviestLoad?.fact?.loadGrams)
        assertEquals("synthetic-workout-49999", records.highestSessionVolume?.workoutId)
    }

    private fun fact(
        setId: String,
        startedAt: Long,
        loadGrams: Long,
        reps: Int,
        workoutId: String = "workout-$startedAt-$setId",
        workoutExerciseId: String = "we-$setId",
        workoutExercisePosition: Int = 0,
        setPosition: Int = 0,
        type: String = "WORK",
        rirTenths: Int? = 20,
        finishedAt: Long? = startedAt + 500,
        completedAt: Long? = startedAt + 100,
    ) = PrSetFact(
        workoutId = workoutId,
        workoutExerciseId = workoutExerciseId,
        workoutSetId = setId,
        startedAt = startedAt,
        finishedAt = finishedAt,
        workoutExercisePosition = workoutExercisePosition,
        setPosition = setPosition,
        type = type,
        loadGrams = loadGrams,
        reps = reps,
        rirTenths = rirTenths,
        completedAt = completedAt,
    )
}
