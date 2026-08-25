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

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    suspend fun getActive(): List<ExerciseEntity>

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

    @Query("SELECT * FROM workout WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveWorkout(): WorkoutEntity?

    @Query("SELECT * FROM workout_exercise WHERE id = :id LIMIT 1")
    suspend fun getWorkoutExercise(id: String): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getExercises(workoutId: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_set WHERE id = :id LIMIT 1")
    suspend fun getSet(id: String): WorkoutSetEntity?

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY position")
    suspend fun getSets(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query(
        "SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId " +
            "AND completedAt IS NOT NULL ORDER BY position",
    )
    suspend fun getCompletedSets(workoutExerciseId: String): List<WorkoutSetEntity>

    @Transaction
    suspend fun insertWorkoutAggregate(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) {
        upsert(workout)
        exercises.forEach { upsertExercise(it) }
        sets.forEach { upsertSet(it) }
    }

    @Query("UPDATE workout SET notes = :notes WHERE id = :workoutId AND finishedAt IS NULL")
    suspend fun updateWorkoutNotes(workoutId: String, notes: String?)

    @Query("UPDATE workout_exercise SET notes = :notes WHERE id = :workoutExerciseId")
    suspend fun updateWorkoutExerciseNotes(workoutExerciseId: String, notes: String?)

    @Query(
        "UPDATE workout SET restTimerEndsAt = :endsAt, restTimerWorkoutExerciseId = :workoutExerciseId " +
            "WHERE id = :workoutId AND finishedAt IS NULL",
    )
    suspend fun setRestTimer(workoutId: String, workoutExerciseId: String?, endsAt: Long?)

    @Query(
        "UPDATE workout SET finishedAt = :finishedAt, restTimerEndsAt = NULL, " +
            "restTimerWorkoutExerciseId = NULL WHERE id = :workoutId AND finishedAt IS NULL",
    )
    suspend fun finishWorkout(workoutId: String, finishedAt: Long)

    @Query("DELETE FROM workout_set WHERE id = :setId")
    suspend fun deleteSet(setId: String)

    @Query("UPDATE workout_set SET position = :position WHERE id = :setId")
    suspend fun updateSetPosition(setId: String, position: Int)

    @Transaction
    suspend fun deleteSetAndCompact(workoutExerciseId: String, setId: String) {
        val set = getSet(setId) ?: return
        if (set.workoutExerciseId != workoutExerciseId) return
        deleteSet(setId)
        val remaining = getSets(workoutExerciseId)
        remaining.forEachIndexed { index, item ->
            updateSetPosition(item.id, -(index + 1))
        }
        remaining.forEachIndexed { index, item ->
            updateSetPosition(item.id, index)
        }
    }

    @Query("DELETE FROM workout_exercise WHERE id = :workoutExerciseId")
    suspend fun deleteWorkoutExercise(workoutExerciseId: String)

    @Query("UPDATE workout_exercise SET position = :position WHERE id = :workoutExerciseId")
    suspend fun updateWorkoutExercisePosition(workoutExerciseId: String, position: Int)

    @Transaction
    suspend fun deleteWorkoutExerciseAndCompact(workoutId: String, workoutExerciseId: String) {
        val item = getWorkoutExercise(workoutExerciseId) ?: return
        if (item.workoutId != workoutId) return
        deleteWorkoutExercise(workoutExerciseId)
        val remaining = getExercises(workoutId)
        remaining.forEachIndexed { index, exercise ->
            updateWorkoutExercisePosition(exercise.id, -(index + 1))
        }
        remaining.forEachIndexed { index, exercise ->
            updateWorkoutExercisePosition(exercise.id, index)
        }
    }

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
