package com.germanchoconta.gymtracker.domain

import java.math.BigInteger
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ProgressMetric {
    LOAD,
    REPS_AT_EXACT_LOAD,
    ESTIMATED_ONE_REP_MAX,
    VOLUME,
    FREQUENCY,
}

enum class FrequencyBucketSize {
    WEEK,
    MONTH,
}

sealed interface AnalyticsDateRange {
    data object AllTime : AnalyticsDateRange

    data class Custom(
        val startDate: LocalDate,
        val endDate: LocalDate,
    ) : AnalyticsDateRange
}

data class AnalyticsEpochBounds(
    val startInclusive: Long,
    val endExclusive: Long,
)

data class AnalyticsRangeResolution(
    val isValid: Boolean,
    val bounds: AnalyticsEpochBounds?,
)

data class RepsAtExactLoadSessionValue(
    val reps: Int,
    val witnessSetId: String,
)

data class ExerciseAnalyticsSession(
    val workoutId: String,
    val startedAt: Long,
    val heaviestLoadGrams: Long,
    val heaviestLoadWitnessSetId: String,
    val repsAtExactLoad: Map<Long, RepsAtExactLoadSessionValue>,
    val estimatedOneRepMax: EstimatedOneRepMax?,
    val estimatedOneRepMaxWitnessSetId: String?,
    val volumeGramReps: BigInteger,
)

data class ExactLoadUsage(
    val loadGrams: Long,
    val sessionCount: Int,
)

data class ExerciseProgressAnalytics(
    val rangeValid: Boolean,
    val sessions: List<ExerciseAnalyticsSession>,
    val exactLoads: List<ExactLoadUsage>,
    val defaultExactLoadGrams: Long?,
)

data class AnalyticsPoint(
    val workoutId: String,
    val startedAt: Long,
    /** Exact numerator. [denominator] describes the scale and is constant within a series. */
    val exactValue: BigInteger,
    val denominator: Int = 1,
    val isRecordWitness: Boolean = false,
)

data class FrequencyPoint(
    val bucketStart: LocalDate,
    val sessionCount: Int,
)

object ProgressAnalyticsEngine {
    private val chronologicalComparator = compareBy<PrSetFact>(
        { it.startedAt },
        { it.workoutId },
        { it.workoutExercisePosition },
        { it.workoutExerciseId },
        { it.setPosition },
        { it.workoutSetId },
    )

    fun resolveRange(range: AnalyticsDateRange, zoneId: ZoneId): AnalyticsRangeResolution = when (range) {
        AnalyticsDateRange.AllTime -> AnalyticsRangeResolution(isValid = true, bounds = null)
        is AnalyticsDateRange.Custom -> {
            if (range.startDate > range.endDate) {
                AnalyticsRangeResolution(isValid = false, bounds = null)
            } else {
                val bounds = runCatching {
                    AnalyticsEpochBounds(
                        startInclusive = range.startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                        endExclusive = range.endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    )
                }.getOrNull()
                AnalyticsRangeResolution(isValid = bounds != null, bounds = bounds)
            }
        }
    }

    fun calculate(
        facts: List<PrSetFact>,
        range: AnalyticsDateRange = AnalyticsDateRange.AllTime,
        zoneId: ZoneId,
    ): ExerciseProgressAnalytics {
        val resolution = resolveRange(range, zoneId)
        if (!resolution.isValid) {
            return ExerciseProgressAnalytics(
                rangeValid = false,
                sessions = emptyList(),
                exactLoads = emptyList(),
                defaultExactLoadGrams = null,
            )
        }

        val bounds = resolution.bounds
        val orderedEligible = facts
            .asSequence()
            .filter(PersonalRecordEngine::isEligibleForRecords)
            .filter { fact ->
                bounds == null || (fact.startedAt >= bounds.startInclusive && fact.startedAt < bounds.endExclusive)
            }
            .sortedWith(chronologicalComparator)
            .toList()

        val sessions = orderedEligible
            .groupBy { it.workoutId }
            .map { (workoutId, sessionFacts) -> buildSession(workoutId, sessionFacts) }
            .sortedWith(compareBy<ExerciseAnalyticsSession>({ it.startedAt }, { it.workoutId }))

        val loadUsage = linkedMapOf<Long, Int>()
        sessions.forEach { session ->
            session.repsAtExactLoad.keys.forEach { load ->
                loadUsage[load] = loadUsage.getOrDefault(load, 0) + 1
            }
        }
        val exactLoads = loadUsage
            .map { (load, count) -> ExactLoadUsage(load, count) }
            .sortedWith(compareByDescending<ExactLoadUsage> { it.sessionCount }.thenByDescending { it.loadGrams })

        return ExerciseProgressAnalytics(
            rangeValid = true,
            sessions = sessions,
            exactLoads = exactLoads,
            defaultExactLoadGrams = exactLoads.firstOrNull()?.loadGrams,
        )
    }

    fun loadTrend(analytics: ExerciseProgressAnalytics): List<AnalyticsPoint> =
        markStrictImprovements(
            analytics.sessions.map { session ->
                AnalyticsPoint(
                    workoutId = session.workoutId,
                    startedAt = session.startedAt,
                    exactValue = BigInteger.valueOf(session.heaviestLoadGrams),
                )
            },
        )

    fun repsTrend(
        analytics: ExerciseProgressAnalytics,
        exactLoadGrams: Long,
    ): List<AnalyticsPoint> = markStrictImprovements(
        analytics.sessions.mapNotNull { session ->
            session.repsAtExactLoad[exactLoadGrams]?.let { value ->
                AnalyticsPoint(
                    workoutId = session.workoutId,
                    startedAt = session.startedAt,
                    exactValue = BigInteger.valueOf(value.reps.toLong()),
                )
            }
        },
    )

    fun estimatedOneRepMaxTrend(analytics: ExerciseProgressAnalytics): List<AnalyticsPoint> =
        markStrictImprovements(
            analytics.sessions.mapNotNull { session ->
                session.estimatedOneRepMax?.let { estimate ->
                    AnalyticsPoint(
                        workoutId = session.workoutId,
                        startedAt = session.startedAt,
                        exactValue = estimate.numerator,
                        denominator = EstimatedOneRepMax.DENOMINATOR,
                    )
                }
            },
        )

    fun volumeTrend(analytics: ExerciseProgressAnalytics): List<AnalyticsPoint> =
        markStrictImprovements(
            analytics.sessions.map { session ->
                AnalyticsPoint(
                    workoutId = session.workoutId,
                    startedAt = session.startedAt,
                    exactValue = session.volumeGramReps,
                )
            },
        )

    fun frequency(
        analytics: ExerciseProgressAnalytics,
        bucketSize: FrequencyBucketSize,
        range: AnalyticsDateRange,
        zoneId: ZoneId,
    ): List<FrequencyPoint> {
        if (!analytics.rangeValid) return emptyList()
        val observed = analytics.sessions
            .groupingBy { session -> bucketStart(toLocalDate(session.startedAt, zoneId), bucketSize) }
            .eachCount()

        val span = when (range) {
            AnalyticsDateRange.AllTime -> {
                val first = observed.keys.minOrNull() ?: return emptyList()
                val last = observed.keys.maxOrNull() ?: return emptyList()
                first to last
            }
            is AnalyticsDateRange.Custom -> {
                if (range.startDate > range.endDate) return emptyList()
                bucketStart(range.startDate, bucketSize) to bucketStart(range.endDate, bucketSize)
            }
        }

        return generateSequence(span.first) { current -> nextBucket(current, bucketSize) }
            .takeWhile { it <= span.second }
            .map { start -> FrequencyPoint(start, observed[start] ?: 0) }
            .toList()
    }

    private fun buildSession(
        workoutId: String,
        sessionFacts: List<PrSetFact>,
    ): ExerciseAnalyticsSession {
        val ordered = sessionFacts.sortedWith(chronologicalComparator)
        require(ordered.isNotEmpty())

        var heaviest = ordered.first()
        val repsAtLoad = linkedMapOf<Long, RepsAtExactLoadSessionValue>()
        var bestE1rm: EstimatedOneRepMax? = null
        var bestE1rmWitness: String? = null
        var volume = BigInteger.ZERO

        ordered.forEach { fact ->
            if (fact.loadGrams > heaviest.loadGrams) heaviest = fact

            val priorReps = repsAtLoad[fact.loadGrams]
            if (priorReps == null || fact.reps > priorReps.reps) {
                repsAtLoad[fact.loadGrams] = RepsAtExactLoadSessionValue(
                    reps = fact.reps,
                    witnessSetId = fact.workoutSetId,
                )
            }

            PersonalRecordEngine.estimatedOneRepMax(fact)?.let { estimate ->
                val prior = bestE1rm
                if (prior == null || estimate > prior) {
                    bestE1rm = estimate
                    bestE1rmWitness = fact.workoutSetId
                }
            }

            volume += BigInteger.valueOf(fact.loadGrams)
                .multiply(BigInteger.valueOf(fact.reps.toLong()))
        }

        return ExerciseAnalyticsSession(
            workoutId = workoutId,
            startedAt = ordered.first().startedAt,
            heaviestLoadGrams = heaviest.loadGrams,
            heaviestLoadWitnessSetId = heaviest.workoutSetId,
            repsAtExactLoad = repsAtLoad.toMap(),
            estimatedOneRepMax = bestE1rm,
            estimatedOneRepMaxWitnessSetId = bestE1rmWitness,
            volumeGramReps = volume,
        )
    }

    private fun markStrictImprovements(points: List<AnalyticsPoint>): List<AnalyticsPoint> {
        var best: BigInteger? = null
        return points.map { point ->
            val isRecord = best == null || point.exactValue > requireNotNull(best)
            if (isRecord) best = point.exactValue
            point.copy(isRecordWitness = isRecord)
        }
    }

    private fun toLocalDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    private fun bucketStart(date: LocalDate, bucketSize: FrequencyBucketSize): LocalDate = when (bucketSize) {
        FrequencyBucketSize.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        FrequencyBucketSize.MONTH -> YearMonth.from(date).atDay(1)
    }

    private fun nextBucket(date: LocalDate, bucketSize: FrequencyBucketSize): LocalDate = when (bucketSize) {
        FrequencyBucketSize.WEEK -> date.plusWeeks(1)
        FrequencyBucketSize.MONTH -> date.plusMonths(1).withDayOfMonth(1)
    }
}

object AnalyticsPresentationSampler {
    const val DEFAULT_TARGET_POINTS = 480

    fun sample(
        points: List<AnalyticsPoint>,
        targetPoints: Int = DEFAULT_TARGET_POINTS,
    ): List<AnalyticsPoint> {
        require(targetPoints >= 2) { "targetPoints must be at least 2" }
        if (points.size <= targetPoints) return points

        val essential = linkedSetOf<Int>()
        essential += 0
        essential += points.lastIndex
        points.forEachIndexed { index, point -> if (point.isRecordWitness) essential += index }
        essential += points.indices.minWithOrNull(compareBy<Int>({ points[it].exactValue }, { it })) ?: 0
        essential += points.indices.maxWithOrNull(compareBy<Int>({ points[it].exactValue }, { -it })) ?: points.lastIndex

        if (essential.size >= targetPoints) {
            return essential.sorted().map(points::get)
        }

        val selected = essential.toMutableSet()
        var budget = targetPoints - selected.size
        val candidates = points.indices.filterNot(selected::contains)
        if (candidates.isEmpty() || budget == 0) return selected.sorted().map(points::get)

        val bucketCount = minOf(candidates.size, maxOf(1, (budget + 1) / 2))
        repeat(bucketCount) { bucketIndex ->
            if (budget == 0) return@repeat
            val from = bucketIndex * candidates.size / bucketCount
            val until = (bucketIndex + 1) * candidates.size / bucketCount
            val bucket = candidates.subList(from, until)
            if (bucket.isEmpty()) return@repeat

            val minIndex = bucket.minWithOrNull(compareBy<Int>({ points[it].exactValue }, { it }))
            val maxIndex = bucket.maxWithOrNull(compareBy<Int>({ points[it].exactValue }, { -it }))
            listOfNotNull(minIndex, maxIndex)
                .distinct()
                .forEach { index ->
                    if (budget > 0 && selected.add(index)) budget--
                }
        }

        if (budget > 0) {
            val leftovers = candidates.filterNot(selected::contains)
            repeat(minOf(budget, leftovers.size)) { slot ->
                val index = leftovers[(slot * leftovers.size) / minOf(budget, leftovers.size)]
                selected += index
            }
        }

        return selected.sorted().map(points::get)
    }
}
