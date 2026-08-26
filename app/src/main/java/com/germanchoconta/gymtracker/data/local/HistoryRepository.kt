package com.germanchoconta.gymtracker.data.local

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.germanchoconta.gymtracker.domain.AnalyticsEpochBounds
import com.germanchoconta.gymtracker.domain.ExercisePersonalRecords
import com.germanchoconta.gymtracker.domain.PersonalRecordEngine
import com.germanchoconta.gymtracker.domain.PrSetFact
import com.germanchoconta.gymtracker.domain.PreviousSessionComparison
import com.germanchoconta.gymtracker.domain.ProgressionObservation

class HistoryRepository(private val historyDao: HistoryDao) {
    fun observeExercisesWithFinishedHistory() = historyDao.observeExercisesWithFinishedHistory()

    fun exerciseHistory(exerciseId: String) = Pager(
        config = PagingConfig(
            pageSize = 30,
            initialLoadSize = 30,
            prefetchDistance = 10,
            enablePlaceholders = false,
            maxSize = 150,
        ),
        pagingSourceFactory = { historyDao.pageFinishedExerciseHistory(exerciseId) },
    ).flow

    suspend fun prFacts(exerciseId: String): List<PrSetFact> =
        historyDao.getExercisePrFacts(exerciseId).map(PrFactRow::toDomain)

    suspend fun analyticsFacts(
        exerciseId: String,
        bounds: AnalyticsEpochBounds?,
    ): List<PrSetFact> = if (bounds == null) {
        prFacts(exerciseId)
    } else {
        historyDao.getExercisePrFactsInRange(
            exerciseId = exerciseId,
            startInclusive = bounds.startInclusive,
            endExclusive = bounds.endExclusive,
        ).map(PrFactRow::toDomain)
    }

    suspend fun records(exerciseId: String): ExercisePersonalRecords =
        PersonalRecordEngine.calculate(prFacts(exerciseId))

    suspend fun previousSessionComparison(exerciseId: String): PreviousSessionComparison? =
        PersonalRecordEngine.previousSessionComparison(prFacts(exerciseId))

    suspend fun progressionObservations(
        exerciseId: String,
        referenceMode: String,
        routineId: String?,
        beforeStartedAt: Long,
    ): List<ProgressionObservation> {
        val routineFilter = when (referenceMode) {
            PreviousReferenceModes.SAME_ROUTINE -> routineId ?: return emptyList()
            else -> null
        }
        return historyDao.getProgressionObservations(
            exerciseId = exerciseId,
            beforeStartedAt = beforeStartedAt,
            routineId = routineFilter,
        ).map(ProgressionObservationRow::toDomain)
    }
}

internal fun PrFactRow.toDomain() = PrSetFact(
    workoutId = workoutId,
    workoutExerciseId = workoutExerciseId,
    workoutSetId = workoutSetId,
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

internal fun ProgressionObservationRow.toDomain() = ProgressionObservation(
    workoutId = workoutId,
    startedAt = startedAt,
    workoutSetId = workoutSetId,
    setPosition = setPosition,
    loadGrams = loadGrams,
    reps = reps,
    rirTenths = rirTenths,
)
