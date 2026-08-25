package com.germanchoconta.gymtracker.domain

import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAnalyticsEngineTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun seriesReusePr5EligibilityAndCombineDuplicateExerciseOccurrencesPerWorkout() {
        val facts = listOf(
            fact("w1", "we-a", "work-a", 1_000L, load = 60_000L, reps = 8, type = "WORK"),
            fact("w1", "we-b", "drop-b", 1_000L, load = 65_000L, reps = 6, type = "DROP", exercisePosition = 1),
            fact("w1", "we-b", "failure-b", 1_000L, load = 60_000L, reps = 10, type = "FAILURE", exercisePosition = 1, setPosition = 1),
            fact("w1", "we-a", "warmup", 1_000L, load = 100_000L, reps = 5, type = "WARMUP", setPosition = 2),
            fact("w1", "we-a", "incomplete", 1_000L, load = 110_000L, reps = 5, completed = false, setPosition = 3),
            fact("active", "we-active", "active-set", 2_000L, load = 200_000L, reps = 5, finished = false),
        )

        val analytics = ProgressAnalyticsEngine.calculate(facts, zoneId = utc)
        assertEquals(1, analytics.sessions.size)
        val session = analytics.sessions.single()
        assertEquals("w1", session.workoutId)
        assertEquals(65_000L, session.heaviestLoadGrams)
        assertEquals(10, session.repsAtExactLoad.getValue(60_000L).reps)
        assertEquals(
            BigInteger.valueOf(60_000L * 8L + 65_000L * 6L + 60_000L * 10L),
            session.volumeGramReps,
        )
        assertEquals(1, ProgressAnalyticsEngine.frequency(analytics, FrequencyBucketSize.WEEK, AnalyticsDateRange.AllTime, utc).sumOf { it.sessionCount })
    }

    @Test
    fun repetitionTrendUsesOneExactLoadAndDefaultLoadIsSessionFrequencyThenHigherLoad() {
        val facts = listOf(
            fact("w1", setId = "a", startedAt = 1_000L, load = 50_000L, reps = 8),
            fact("w1", setId = "b", startedAt = 1_000L, load = 60_000L, reps = 5, setPosition = 1),
            fact("w2", setId = "c", startedAt = 2_000L, load = 50_000L, reps = 9),
            fact("w2", setId = "d", startedAt = 2_000L, load = 60_000L, reps = 6, setPosition = 1),
            fact("w3", setId = "e", startedAt = 3_000L, load = 70_000L, reps = 4),
        )
        val analytics = ProgressAnalyticsEngine.calculate(facts, zoneId = utc)

        assertEquals(60_000L, analytics.defaultExactLoadGrams)
        assertEquals(listOf(5L, 6L), ProgressAnalyticsEngine.repsTrend(analytics, 60_000L).map { it.exactValue.toLong() })
        assertEquals(listOf("w1", "w2"), ProgressAnalyticsEngine.repsTrend(analytics, 50_000L).map { it.workoutId })
        assertTrue(ProgressAnalyticsEngine.repsTrend(analytics, 55_000L).isEmpty())
    }

    @Test
    fun e1rmTrendUsesExactPr5EpleyRulesTwoThroughTenAndIgnoresRir() {
        val facts = listOf(
            fact("one-rep", setId = "one", startedAt = 1_000L, load = 100_000L, reps = 1),
            fact("two-rep", setId = "two", startedAt = 2_000L, load = 100_000L, reps = 2, rir = 0),
            fact("ten-rep", setId = "ten", startedAt = 3_000L, load = 80_000L, reps = 10, rir = 50),
            fact("eleven-rep", setId = "eleven", startedAt = 4_000L, load = 120_000L, reps = 11),
        )
        val analytics = ProgressAnalyticsEngine.calculate(facts, zoneId = utc)
        val trend = ProgressAnalyticsEngine.estimatedOneRepMaxTrend(analytics)

        assertEquals(listOf("two-rep", "ten-rep"), trend.map { it.workoutId })
        assertEquals(BigInteger.valueOf(100_000L * 32L), trend[0].exactValue)
        assertEquals(BigInteger.valueOf(80_000L * 40L), trend[1].exactValue)
        assertEquals(30, trend[0].denominator)
        assertEquals(30, trend[1].denominator)

        val sameWithoutRir = PersonalRecordEngine.estimatedOneRepMax(facts[1].copy(rirTenths = null))
        assertEquals(analytics.sessions.first { it.workoutId == "two-rep" }.estimatedOneRepMax, sameWithoutRir)
    }

    @Test
    fun loadUsesExactGramsAndDeterministicFirstWitnessOnTie() {
        val facts = listOf(
            fact("w", setId = "b", startedAt = 1_000L, load = 100_001L, reps = 5, setPosition = 1),
            fact("w", setId = "a", startedAt = 1_000L, load = 100_001L, reps = 5, setPosition = 0),
            fact("w", setId = "nearby", startedAt = 1_000L, load = 100_000L, reps = 20, setPosition = 2),
        )
        val analytics = ProgressAnalyticsEngine.calculate(facts.reversed(), zoneId = utc)
        val session = analytics.sessions.single()
        assertEquals(100_001L, session.heaviestLoadGrams)
        assertEquals("a", session.heaviestLoadWitnessSetId)
        assertEquals(setOf(100_000L, 100_001L), session.repsAtExactLoad.keys)
    }

    @Test
    fun volumeIsOverflowSafeBigInteger() {
        val huge = Long.MAX_VALUE / 2
        val facts = listOf(
            fact("w", setId = "a", startedAt = 1_000L, load = huge, reps = 1000),
            fact("w", setId = "b", startedAt = 1_000L, load = huge, reps = 1000, setPosition = 1),
        )
        val expected = BigInteger.valueOf(huge).multiply(BigInteger.valueOf(2_000L))
        val analytics = ProgressAnalyticsEngine.calculate(facts, zoneId = utc)
        assertEquals(expected, analytics.sessions.single().volumeGramReps)
        assertEquals(expected, ProgressAnalyticsEngine.volumeTrend(analytics).single().exactValue)
    }

    @Test
    fun customRangeIsInclusiveByLocalDateAndSameDayWorks() {
        val bogota = ZoneId.of("America/Bogota")
        val day = LocalDate.of(2026, 8, 25)
        val insideStart = day.atStartOfDay(bogota).toInstant().toEpochMilli()
        val insideEnd = day.plusDays(1).atStartOfDay(bogota).toInstant().toEpochMilli() - 1
        val nextDay = day.plusDays(1).atStartOfDay(bogota).toInstant().toEpochMilli()
        val facts = listOf(
            fact("start", setId = "a", startedAt = insideStart, load = 50_000L, reps = 8),
            fact("end", setId = "b", startedAt = insideEnd, load = 50_000L, reps = 8),
            fact("next", setId = "c", startedAt = nextDay, load = 50_000L, reps = 8),
        )
        val range = AnalyticsDateRange.Custom(day, day)
        val analytics = ProgressAnalyticsEngine.calculate(facts, range, bogota)

        assertTrue(analytics.rangeValid)
        assertEquals(listOf("start", "end"), analytics.sessions.map { it.workoutId })
        val bounds = requireNotNull(ProgressAnalyticsEngine.resolveRange(range, bogota).bounds)
        assertEquals(insideStart, bounds.startInclusive)
        assertEquals(nextDay, bounds.endExclusive)
    }

    @Test
    fun reversedRangeIsInvalidAndProducesNoSeries() {
        val range = AnalyticsDateRange.Custom(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 25))
        val analytics = ProgressAnalyticsEngine.calculate(
            listOf(fact("w", startedAt = 1_000L, load = 50_000L, reps = 8)),
            range,
            utc,
        )
        assertFalse(analytics.rangeValid)
        assertTrue(analytics.sessions.isEmpty())
        assertNull(analytics.defaultExactLoadGrams)
        assertFalse(ProgressAnalyticsEngine.resolveRange(range, utc).isValid)
    }

    @Test
    fun allTimeAndEmptyRangeHaveNoArtificialHistoryWindow() {
        val early = LocalDate.of(2010, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli()
        val late = LocalDate.of(2026, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli()
        val facts = listOf(
            fact("early", startedAt = early, load = 40_000L, reps = 8),
            fact("late", startedAt = late, load = 80_000L, reps = 8),
        )
        val allTime = ProgressAnalyticsEngine.calculate(facts, AnalyticsDateRange.AllTime, utc)
        assertEquals(listOf("early", "late"), allTime.sessions.map { it.workoutId })

        val empty = ProgressAnalyticsEngine.calculate(
            facts,
            AnalyticsDateRange.Custom(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2)),
            utc,
        )
        assertTrue(empty.rangeValid)
        assertTrue(empty.sessions.isEmpty())
    }

    @Test
    fun frequencyDedupesWorkoutAndFillsFiniteWeekAndMonthBuckets() {
        val zone = ZoneId.of("UTC")
        fun epoch(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        val facts = listOf(
            fact("w1", "we-a", "a", epoch("2026-01-05"), 50_000L, 8),
            fact("w1", "we-b", "b", epoch("2026-01-05"), 55_000L, 6, exercisePosition = 1),
            fact("w2", setId = "c", startedAt = epoch("2026-01-20"), load = 50_000L, reps = 8),
        )
        val range = AnalyticsDateRange.Custom(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 2))
        val analytics = ProgressAnalyticsEngine.calculate(facts, range, zone)
        val weeks = ProgressAnalyticsEngine.frequency(analytics, FrequencyBucketSize.WEEK, range, zone)
        assertEquals(5, weeks.size)
        assertEquals(listOf(1, 0, 1, 0, 0), weeks.map { it.sessionCount })
        assertEquals(2, weeks.sumOf { it.sessionCount })

        val months = ProgressAnalyticsEngine.frequency(analytics, FrequencyBucketSize.MONTH, range, zone)
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)), months.map { it.bucketStart })
        assertEquals(listOf(2, 0), months.map { it.sessionCount })
    }

    @Test
    fun weekBoundaryUsesMondayAndTimezoneCanMoveSessionAcrossDateBoundary() {
        val bogota = ZoneId.of("America/Bogota")
        val instant = LocalDate.of(2026, 1, 5).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val analytics = ProgressAnalyticsEngine.calculate(
            listOf(fact("w", startedAt = instant, load = 50_000L, reps = 8)),
            zoneId = bogota,
        )
        val point = ProgressAnalyticsEngine.frequency(analytics, FrequencyBucketSize.WEEK, AnalyticsDateRange.AllTime, bogota).single()
        assertEquals(LocalDate.of(2025, 12, 29), point.bucketStart)
    }

    @Test
    fun shuffledInputAndTiesProduceDeterministicSeries() {
        val facts = (0 until 50).flatMap { index ->
            listOf(
                fact("w-${index.toString().padStart(2, '0')}", setId = "a-$index", startedAt = (index % 5).toLong() * 1_000L, load = 50_000L + index, reps = 5, setPosition = 0),
                fact("w-${index.toString().padStart(2, '0')}", setId = "b-$index", startedAt = (index % 5).toLong() * 1_000L, load = 50_000L + index, reps = 5, setPosition = 1),
            )
        }
        val normal = ProgressAnalyticsEngine.calculate(facts, zoneId = utc)
        val reversed = ProgressAnalyticsEngine.calculate(facts.reversed(), zoneId = utc)
        assertEquals(normal, reversed)
        assertEquals(ProgressAnalyticsEngine.loadTrend(normal), ProgressAnalyticsEngine.loadTrend(reversed))
    }

    @Test
    fun sparseDenseAndLongHistoryRemainDeterministicAndSamplerPreservesRequiredPoints() {
        val sparse = (0 until 20).map { index ->
            fact("s-$index", startedAt = index.toLong() * 86_400_000L * 90L, load = 40_000L + index * 1_000L, reps = 8)
        }
        assertEquals(20, ProgressAnalyticsEngine.calculate(sparse, zoneId = utc).sessions.size)

        val dense = (0 until 5_000).map { index ->
            fact(
                workoutId = "d-${index.toString().padStart(5, '0')}",
                startedAt = index.toLong() * 1_000L,
                load = when (index) {
                    2_500 -> 1L
                    3_000 -> 999_999L
                    else -> 50_000L + (index % 97)
                },
                reps = 8,
            )
        }
        val analytics = ProgressAnalyticsEngine.calculate(dense.shuffled(kotlin.random.Random(7)), zoneId = utc)
        val trend = ProgressAnalyticsEngine.loadTrend(analytics)
        val sampled = AnalyticsPresentationSampler.sample(trend, targetPoints = 120)
        val sampledAgain = AnalyticsPresentationSampler.sample(trend, targetPoints = 120)

        assertEquals(sampled, sampledAgain)
        assertEquals(trend.first(), sampled.first())
        assertEquals(trend.last(), sampled.last())
        assertTrue(sampled.any { it.exactValue == BigInteger.ONE })
        assertTrue(sampled.any { it.exactValue == BigInteger.valueOf(999_999L) })
        val witnesses = trend.filter { it.isRecordWitness }.toSet()
        assertTrue(sampled.containsAll(witnesses))
        assertTrue(sampled.size <= 120 || witnesses.size >= 120)
    }

    @Test
    fun strictImprovementFlagsDoNotTreatEqualityAsNewRecord() {
        val analytics = ProgressAnalyticsEngine.calculate(
            listOf(
                fact("a", startedAt = 1_000L, load = 50_000L, reps = 8),
                fact("b", startedAt = 2_000L, load = 50_000L, reps = 9),
                fact("c", startedAt = 3_000L, load = 60_000L, reps = 7),
            ),
            zoneId = utc,
        )
        assertEquals(listOf(true, false, true), ProgressAnalyticsEngine.loadTrend(analytics).map { it.isRecordWitness })
        assertEquals(listOf(true, true), ProgressAnalyticsEngine.repsTrend(analytics, 50_000L).map { it.isRecordWitness })
    }

    private fun fact(
        workoutId: String,
        workoutExerciseId: String = "we-$workoutId",
        setId: String = "set-$workoutId",
        startedAt: Long,
        load: Long,
        reps: Int,
        type: String = "WORK",
        completed: Boolean = true,
        finished: Boolean = true,
        rir: Int? = null,
        exercisePosition: Int = 0,
        setPosition: Int = 0,
    ) = PrSetFact(
        workoutId = workoutId,
        workoutExerciseId = workoutExerciseId,
        workoutSetId = setId,
        startedAt = startedAt,
        finishedAt = if (finished) startedAt + 500L else null,
        workoutExercisePosition = exercisePosition,
        setPosition = setPosition,
        type = type,
        loadGrams = load,
        reps = reps,
        rirTenths = rir,
        completedAt = if (completed) startedAt + 100L else null,
    )
}
