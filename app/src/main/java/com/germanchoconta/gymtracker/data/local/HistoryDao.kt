package com.germanchoconta.gymtracker.data.local

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import kotlinx.coroutines.flow.Flow

data class HistoryExerciseRow(
    val id: String,
    val name: String,
    val equipment: String?,
    val unilateral: Boolean,
    val archived: Boolean,
    val lastStartedAt: Long,
    val sessionCount: Long,
)

data class HistorySetRow(
    val workoutId: String,
    val workoutTitle: String,
    val workoutNotes: String?,
    val routineId: String?,
    val startedAt: Long,
    val finishedAt: Long,
    val workoutExerciseId: String,
    val workoutExercisePosition: Int,
    val workoutExerciseNotes: String?,
    val workoutSetId: String,
    val setPosition: Int,
    val type: String,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
    val completedAt: Long?,
)

data class PrFactRow(
    val workoutId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val workoutExerciseId: String,
    val workoutExercisePosition: Int,
    val workoutSetId: String,
    val setPosition: Int,
    val type: String,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
    val completedAt: Long?,
)

data class ProgressionObservationRow(
    val workoutId: String,
    val routineId: String?,
    val startedAt: Long,
    val workoutSetId: String,
    val setPosition: Int,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
)

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface HistoryDao {
    @Query(
        """
        SELECT
            e.id AS id,
            e.name AS name,
            e.equipment AS equipment,
            e.unilateral AS unilateral,
            e.archived AS archived,
            MAX(w.startedAt) AS lastStartedAt,
            COUNT(DISTINCT w.id) AS sessionCount
        FROM exercise e
        INNER JOIN workout_exercise we ON we.exerciseId = e.id
        INNER JOIN workout w ON w.id = we.workoutId
        WHERE w.finishedAt IS NOT NULL
        GROUP BY e.id, e.name, e.equipment, e.unilateral, e.archived
        ORDER BY lastStartedAt DESC, e.name COLLATE NOCASE ASC, e.id ASC
        """,
    )
    fun observeExercisesWithFinishedHistory(): Flow<List<HistoryExerciseRow>>

    @Query(
        """
        SELECT
            w.id AS workoutId,
            w.title AS workoutTitle,
            w.notes AS workoutNotes,
            w.routineId AS routineId,
            w.startedAt AS startedAt,
            w.finishedAt AS finishedAt,
            we.id AS workoutExerciseId,
            we.position AS workoutExercisePosition,
            we.notes AS workoutExerciseNotes,
            ws.id AS workoutSetId,
            ws.position AS setPosition,
            ws.type AS type,
            ws.loadGrams AS loadGrams,
            ws.reps AS reps,
            ws.rirTenths AS rirTenths,
            ws.completedAt AS completedAt
        FROM workout w
        INNER JOIN workout_exercise we ON we.workoutId = w.id
        INNER JOIN workout_set ws ON ws.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL
        ORDER BY
            w.startedAt DESC,
            w.id DESC,
            we.position ASC,
            we.id ASC,
            ws.position ASC,
            ws.id ASC
        """,
    )
    fun pageFinishedExerciseHistory(exerciseId: String): PagingSource<Int, HistorySetRow>

    @Query(
        """
        SELECT
            w.id AS workoutId,
            w.startedAt AS startedAt,
            w.finishedAt AS finishedAt,
            we.id AS workoutExerciseId,
            we.position AS workoutExercisePosition,
            ws.id AS workoutSetId,
            ws.position AS setPosition,
            ws.type AS type,
            ws.loadGrams AS loadGrams,
            ws.reps AS reps,
            ws.rirTenths AS rirTenths,
            ws.completedAt AS completedAt
        FROM workout w
        INNER JOIN workout_exercise we ON we.workoutId = w.id
        INNER JOIN workout_set ws ON ws.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId
        ORDER BY
            w.startedAt ASC,
            w.id ASC,
            we.position ASC,
            we.id ASC,
            ws.position ASC,
            ws.id ASC
        """,
    )
    suspend fun getExercisePrFacts(exerciseId: String): List<PrFactRow>

    @Query(
        """
        SELECT
            w.id AS workoutId,
            w.startedAt AS startedAt,
            w.finishedAt AS finishedAt,
            we.id AS workoutExerciseId,
            we.position AS workoutExercisePosition,
            ws.id AS workoutSetId,
            ws.position AS setPosition,
            ws.type AS type,
            ws.loadGrams AS loadGrams,
            ws.reps AS reps,
            ws.rirTenths AS rirTenths,
            ws.completedAt AS completedAt
        FROM workout_exercise we
        INNER JOIN workout w ON w.id = we.workoutId
        INNER JOIN workout_set ws ON ws.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId
          AND w.startedAt >= :startInclusive
          AND w.startedAt < :endExclusive
        ORDER BY
            w.startedAt ASC,
            w.id ASC,
            we.position ASC,
            we.id ASC,
            ws.position ASC,
            ws.id ASC
        """,
    )
    suspend fun getExercisePrFactsInRange(
        exerciseId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<PrFactRow>

    @Query(
        """
        SELECT
            w.id AS workoutId,
            w.routineId AS routineId,
            w.startedAt AS startedAt,
            ws.id AS workoutSetId,
            ws.position AS setPosition,
            ws.loadGrams AS loadGrams,
            ws.reps AS reps,
            ws.rirTenths AS rirTenths
        FROM workout w
        INNER JOIN workout_exercise we ON we.workoutId = w.id
        INNER JOIN workout_set ws ON ws.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL
          AND w.startedAt < :beforeStartedAt
          AND ws.completedAt IS NOT NULL
          AND ws.type = 'WORK'
          AND (:routineId IS NULL OR w.routineId = :routineId)
        ORDER BY
            w.startedAt DESC,
            w.id DESC,
            we.position ASC,
            we.id ASC,
            ws.position ASC,
            ws.id ASC
        """,
    )
    suspend fun getProgressionObservations(
        exerciseId: String,
        beforeStartedAt: Long,
        routineId: String?,
    ): List<ProgressionObservationRow>
}
