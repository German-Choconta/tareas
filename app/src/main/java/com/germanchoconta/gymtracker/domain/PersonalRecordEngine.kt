package com.germanchoconta.gymtracker.domain

import java.math.BigInteger

enum class PersonalRecordKind {
    HEAVIEST_LOAD,
    REPS_AT_LOAD,
    ESTIMATED_ONE_REP_MAX,
    EXERCISE_SESSION_VOLUME,
}

data class PrSetFact(
    val workoutId: String,
    val workoutExerciseId: String,
    val workoutSetId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val workoutExercisePosition: Int,
    val setPosition: Int,
    val type: String,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
    val completedAt: Long?,
)

data class EstimatedOneRepMax(
    /** Exact Epley numerator. The denominator is always [DENOMINATOR]. */
    val numerator: BigInteger,
    val roundedGrams: Long?,
) : Comparable<EstimatedOneRepMax> {
    override fun compareTo(other: EstimatedOneRepMax): Int = numerator.compareTo(other.numerator)

    companion object {
        const val DENOMINATOR = 30
    }
}

data class SetRecord(
    val fact: PrSetFact,
    val value: BigInteger,
)

data class SessionVolumeRecord(
    val workoutId: String,
    val startedAt: Long,
    val volumeGramReps: BigInteger,
)

data class PersonalRecordEvent(
    val kind: PersonalRecordKind,
    val workoutId: String,
    val workoutSetId: String? = null,
    val exactLoadGrams: Long? = null,
    val value: BigInteger,
)

data class ExercisePersonalRecords(
    val heaviestLoad: SetRecord? = null,
    val repsAtExactLoad: Map<Long, SetRecord> = emptyMap(),
    val estimatedOneRepMax: SetRecord? = null,
    val highestSessionVolume: SessionVolumeRecord? = null,
    val events: List<PersonalRecordEvent> = emptyList(),
)

data class ExerciseSessionSummary(
    val workoutId: String,
    val startedAt: Long,
    val completedSetCount: Int,
    val heaviestLoadGrams: Long?,
    val estimatedOneRepMax: EstimatedOneRepMax?,
    val volumeGramReps: BigInteger,
)

data class PreviousSessionComparison(
    val latest: ExerciseSessionSummary,
    val previous: ExerciseSessionSummary?,
)

object PersonalRecordEngine {
    private const val E1RM_MIN_REPS = 2
    private const val E1RM_MAX_REPS = 10
    private const val DISPLAY_INCREMENT_GRAMS = 100L
    private val prSetTypes = setOf("WORK", "DROP", "FAILURE")

    private val chronologicalComparator = compareBy<PrSetFact>(
        { it.startedAt },
        { it.workoutId },
        { it.workoutExercisePosition },
        { it.workoutExerciseId },
        { it.setPosition },
        { it.workoutSetId },
    )

    fun isEligibleForRecords(fact: PrSetFact): Boolean =
        fact.finishedAt != null &&
            fact.completedAt != null &&
            fact.reps > 0 &&
            fact.loadGrams > 0L &&
            fact.type in prSetTypes

    fun estimatedOneRepMax(fact: PrSetFact): EstimatedOneRepMax? {
        if (!isEligibleForRecords(fact) || fact.reps !in E1RM_MIN_REPS..E1RM_MAX_REPS) return null
        val numerator = BigInteger.valueOf(fact.loadGrams)
            .multiply(BigInteger.valueOf((EstimatedOneRepMax.DENOMINATOR + fact.reps).toLong()))
        return EstimatedOneRepMax(
            numerator = numerator,
            roundedGrams = roundRationalGramsHalfUp(
                numerator = numerator,
                denominator = EstimatedOneRepMax.DENOMINATOR,
                incrementGrams = DISPLAY_INCREMENT_GRAMS,
            ),
        )
    }

    fun calculate(facts: List<PrSetFact>): ExercisePersonalRecords {
        val ordered = facts.sortedWith(chronologicalComparator)
        var heaviest: SetRecord? = null
        var e1rm: SetRecord? = null
        val repsAtLoad = linkedMapOf<Long, SetRecord>()
        val events = mutableListOf<PersonalRecordEvent>()
        val sessionVolumes = linkedMapOf<String, MutableSessionVolume>()

        ordered.forEach { fact ->
            if (!isEligibleForRecords(fact)) return@forEach

            val load = BigInteger.valueOf(fact.loadGrams)
            val previousHeaviest = heaviest
            if (previousHeaviest == null || load > previousHeaviest.value) {
                heaviest = SetRecord(fact, load)
                events += PersonalRecordEvent(
                    kind = PersonalRecordKind.HEAVIEST_LOAD,
                    workoutId = fact.workoutId,
                    workoutSetId = fact.workoutSetId,
                    value = load,
                )
            }

            val reps = BigInteger.valueOf(fact.reps.toLong())
            val priorReps = repsAtLoad[fact.loadGrams]
            if (priorReps == null || reps > priorReps.value) {
                repsAtLoad[fact.loadGrams] = SetRecord(fact, reps)
                events += PersonalRecordEvent(
                    kind = PersonalRecordKind.REPS_AT_LOAD,
                    workoutId = fact.workoutId,
                    workoutSetId = fact.workoutSetId,
                    exactLoadGrams = fact.loadGrams,
                    value = reps,
                )
            }

            estimatedOneRepMax(fact)?.let { estimate ->
                val previousE1rm = e1rm
                if (previousE1rm == null || estimate.numerator > previousE1rm.value) {
                    e1rm = SetRecord(fact, estimate.numerator)
                    events += PersonalRecordEvent(
                        kind = PersonalRecordKind.ESTIMATED_ONE_REP_MAX,
                        workoutId = fact.workoutId,
                        workoutSetId = fact.workoutSetId,
                        value = estimate.numerator,
                    )
                }
            }

            val session = sessionVolumes.getOrPut(fact.workoutId) {
                MutableSessionVolume(fact.workoutId, fact.startedAt)
            }
            session.volume += BigInteger.valueOf(fact.loadGrams)
                .multiply(BigInteger.valueOf(fact.reps.toLong()))
        }

        var highestVolume: SessionVolumeRecord? = null
        sessionVolumes.values
            .sortedWith(compareBy<MutableSessionVolume>({ it.startedAt }, { it.workoutId }))
            .forEach { session ->
                val previousHighestVolume = highestVolume
                if (previousHighestVolume == null || session.volume > previousHighestVolume.volumeGramReps) {
                    highestVolume = SessionVolumeRecord(
                        workoutId = session.workoutId,
                        startedAt = session.startedAt,
                        volumeGramReps = session.volume,
                    )
                    events += PersonalRecordEvent(
                        kind = PersonalRecordKind.EXERCISE_SESSION_VOLUME,
                        workoutId = session.workoutId,
                        value = session.volume,
                    )
                }
            }

        return ExercisePersonalRecords(
            heaviestLoad = heaviest,
            repsAtExactLoad = repsAtLoad.toMap(),
            estimatedOneRepMax = e1rm,
            highestSessionVolume = highestVolume,
            events = events,
        )
    }

    fun previousSessionComparison(facts: List<PrSetFact>): PreviousSessionComparison? {
        val summaries = sessionSummaries(facts)
        val latest = summaries.firstOrNull() ?: return null
        return PreviousSessionComparison(latest = latest, previous = summaries.getOrNull(1))
    }

    fun sessionSummaries(facts: List<PrSetFact>): List<ExerciseSessionSummary> {
        val eligible = facts.filter(::isEligibleForRecords)
        return eligible
            .groupBy { it.workoutId }
            .map { (workoutId, sessionFacts) ->
                val ordered = sessionFacts.sortedWith(chronologicalComparator)
                val topE1rm = ordered.mapNotNull(::estimatedOneRepMax).maxOrNull()
                ExerciseSessionSummary(
                    workoutId = workoutId,
                    startedAt = ordered.first().startedAt,
                    completedSetCount = ordered.size,
                    heaviestLoadGrams = ordered.maxOfOrNull { it.loadGrams },
                    estimatedOneRepMax = topE1rm,
                    volumeGramReps = ordered.fold(BigInteger.ZERO) { total, fact ->
                        total + BigInteger.valueOf(fact.loadGrams)
                            .multiply(BigInteger.valueOf(fact.reps.toLong()))
                    },
                )
            }
            .sortedWith(compareByDescending<ExerciseSessionSummary> { it.startedAt }.thenByDescending { it.workoutId })
    }

    fun eventKindsBySet(records: ExercisePersonalRecords): Map<String, Set<PersonalRecordKind>> =
        records.events
            .mapNotNull { event -> event.workoutSetId?.let { it to event.kind } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, kinds) -> kinds.toSet() }

    fun volumePrWorkoutIds(records: ExercisePersonalRecords): Set<String> =
        records.events
            .asSequence()
            .filter { it.kind == PersonalRecordKind.EXERCISE_SESSION_VOLUME }
            .map { it.workoutId }
            .toSet()

    private fun roundRationalGramsHalfUp(
        numerator: BigInteger,
        denominator: Int,
        incrementGrams: Long,
    ): Long? {
        val bucketDenominator = BigInteger.valueOf(denominator.toLong())
            .multiply(BigInteger.valueOf(incrementGrams))
        val half = bucketDenominator.divide(BigInteger.valueOf(2L))
        val buckets = numerator.add(half).divide(bucketDenominator)
        return try {
            buckets.multiply(BigInteger.valueOf(incrementGrams)).longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private data class MutableSessionVolume(
        val workoutId: String,
        val startedAt: Long,
        var volume: BigInteger = BigInteger.ZERO,
    )
}
