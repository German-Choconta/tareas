package com.germanchoconta.gymtracker.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("UPDATE exercise SET archived = 1 WHERE id = :id")
    suspend fun archive(id: String)
}

data class ExerciseMuscleAssignmentRow(
    val muscleId: String,
    val name: String,
    val role: String,
)

@Dao
interface MuscleDao {
    @Upsert
    suspend fun upsert(muscle: MuscleEntity)

    @Upsert
    suspend fun upsertLink(link: ExerciseMuscleEntity)

    @Query("SELECT * FROM muscle ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MuscleEntity>>

    @Query(
        """
        SELECT m.* FROM muscle m
        INNER JOIN exercise_muscle em ON em.muscleId = m.id
        WHERE em.exerciseId = :exerciseId
        ORDER BY CASE em.role WHEN 'PRIMARY' THEN 0 ELSE 1 END, m.name COLLATE NOCASE
        """,
    )
    fun observeForExercise(exerciseId: String): Flow<List<MuscleEntity>>

    @Query(
        """
        SELECT m.id AS muscleId, m.name AS name, em.role AS role
        FROM muscle m
        INNER JOIN exercise_muscle em ON em.muscleId = m.id
        WHERE em.exerciseId = :exerciseId
        ORDER BY CASE em.role WHEN 'PRIMARY' THEN 0 ELSE 1 END, m.name COLLATE NOCASE
        """,
    )
    suspend fun getAssignments(exerciseId: String): List<ExerciseMuscleAssignmentRow>

    @Query("DELETE FROM exercise_muscle WHERE exerciseId = :exerciseId")
    suspend fun deleteLinksForExercise(exerciseId: String)

    @Transaction
    suspend fun replaceLinks(
        exerciseId: String,
        links: List<ExerciseMuscleEntity>,
    ) {
        deleteLinksForExercise(exerciseId)
        links.forEach { upsertLink(it) }
    }
}

@Dao
interface RoutineDao {
    @Upsert
    suspend fun upsert(routine: RoutineEntity)

    @Upsert
    suspend fun upsertExercise(routineExercise: RoutineExerciseEntity)

    @Query("SELECT * FROM routine WHERE archived = 0 ORDER BY position, name COLLATE NOCASE")
    fun observeActive(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RoutineEntity?

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY position")
    fun observeExercises(routineId: String): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY position")
    suspend fun getExercises(routineId: String): List<RoutineExerciseEntity>

    @Query("UPDATE routine SET archived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("DELETE FROM routine_exercise WHERE id = :id")
    suspend fun deleteExercise(id: String)

    @Query("UPDATE routine_exercise SET position = :position WHERE id = :id")
    suspend fun updateExercisePosition(id: String, position: Int)

    /**
     * Replaces a routine template atomically without violating the unique
     * (routineId, position) index while items are reordered.
     *
     * Existing rows are first moved to unique negative temporary positions,
     * then removed/upserted into their final non-negative order. Historical
     * WorkoutExercise rows remain safe because their routineExerciseId FK is SET_NULL.
     */
    @Transaction
    suspend fun saveWithExercises(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
    ) {
        upsert(routine)
        val existing = getExercises(routine.id)
        existing.forEachIndexed { index, item ->
            updateExercisePosition(item.id, -(index + 1))
        }

        val desiredIds = exercises.mapTo(hashSetOf()) { it.id }
        existing.filterNot { it.id in desiredIds }.forEach { deleteExercise(it.id) }
        exercises.forEach { upsertExercise(it) }
    }
}

data class PreviousWorkoutRow(
    val workoutId: String,
    val workoutExerciseId: String,
    val routineId: String?,
    val startedAt: Long,
)

data class ExerciseHistoryRow(
    val workoutId: String,
    val workoutExerciseId: String,
    val workoutSetId: String,
    val routineId: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val setPosition: Int,
    val type: String,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
    val completedAt: Long?,
)

@Dao
interface WorkoutDao {
    @Upsert
    suspend fun upsert(workout: WorkoutEntity)

    @Upsert
    suspend fun upsertExercise(workoutExercise: WorkoutExerciseEntity)

    @Upsert
    suspend fun upsertSet(workoutSet: WorkoutSetEntity)

    @Query("SELECT * FROM workout WHERE id = :id LIMIT 1")
    suspend fun getWorkout(id: String): WorkoutEntity?

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getExercises(workoutId: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY position")
    suspend fun getSets(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query(
        """
        SELECT
            w.id AS workoutId,
            we.id AS workoutExerciseId,
            w.routineId AS routineId,
            w.startedAt AS startedAt
        FROM workout w
        INNER JOIN workout_exercise we ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL
          AND w.startedAt < :beforeStartedAt
        ORDER BY w.startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun previousAnyWorkout(
        exerciseId: String,
        beforeStartedAt: Long = Long.MAX_VALUE,
    ): PreviousWorkoutRow?

    @Query(
        """
        SELECT
            w.id AS workoutId,
            we.id AS workoutExerciseId,
            w.routineId AS routineId,
            w.startedAt AS startedAt
        FROM workout w
        INNER JOIN workout_exercise we ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId
          AND w.routineId = :routineId
          AND w.finishedAt IS NOT NULL
          AND w.startedAt < :beforeStartedAt
        ORDER BY w.startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun previousSameRoutine(
        exerciseId: String,
        routineId: String,
        beforeStartedAt: Long = Long.MAX_VALUE,
    ): PreviousWorkoutRow?

    @Query(
        """
        SELECT
            w.id AS workoutId,
            we.id AS workoutExerciseId,
            ws.id AS workoutSetId,
            w.routineId AS routineId,
            w.startedAt AS startedAt,
            w.finishedAt AS finishedAt,
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
        ORDER BY w.startedAt DESC, ws.position ASC
        """,
    )
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>>
}
