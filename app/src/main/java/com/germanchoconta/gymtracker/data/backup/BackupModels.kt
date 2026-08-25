package com.germanchoconta.gymtracker.data.backup

import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseMuscleEntity
import com.germanchoconta.gymtracker.data.local.MuscleEntity
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutEntity
import com.germanchoconta.gymtracker.data.local.WorkoutExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity

object BackupFormat {
    const val NAME = "gymtracker-backup"
    const val VERSION = 1
    const val DATABASE_SCHEMA_VERSION = 2
    const val MIME_TYPE = "application/json"
    const val CSV_MIME_TYPE = "text/csv"

    const val MAX_DOCUMENT_BYTES = 128 * 1024 * 1024
    const val MAX_EXERCISES = 100_000
    const val MAX_MUSCLES = 10_000
    const val MAX_EXERCISE_MUSCLES = 500_000
    const val MAX_ROUTINES = 100_000
    const val MAX_ROUTINE_EXERCISES = 500_000
    const val MAX_WORKOUTS = 250_000
    const val MAX_WORKOUT_EXERCISES = 1_000_000
    const val MAX_WORKOUT_SETS = 5_000_000

    const val MAX_ID_LENGTH = 256
    const val MAX_NAME_LENGTH = 4_096
    const val MAX_NOTES_LENGTH = 65_536
}

data class BackupSnapshot(
    val exercises: List<ExerciseEntity>,
    val muscles: List<MuscleEntity>,
    val exerciseMuscles: List<ExerciseMuscleEntity>,
    val routines: List<RoutineEntity>,
    val routineExercises: List<RoutineExerciseEntity>,
    val workouts: List<WorkoutEntity>,
    val workoutExercises: List<WorkoutExerciseEntity>,
    val workoutSets: List<WorkoutSetEntity>,
) {
    fun normalized(): BackupSnapshot = copy(
        exercises = exercises.sortedBy(ExerciseEntity::id),
        muscles = muscles.sortedBy(MuscleEntity::id),
        exerciseMuscles = exerciseMuscles.sortedWith(
            compareBy(ExerciseMuscleEntity::exerciseId, ExerciseMuscleEntity::muscleId),
        ),
        routines = routines.sortedBy(RoutineEntity::id),
        routineExercises = routineExercises.sortedBy(RoutineExerciseEntity::id),
        workouts = workouts.sortedBy(WorkoutEntity::id),
        workoutExercises = workoutExercises.sortedBy(WorkoutExerciseEntity::id),
        workoutSets = workoutSets.sortedBy(WorkoutSetEntity::id),
    )
}

data class BackupMetadata(
    val formatVersion: Int,
    val generatedAtEpochMillis: Long,
    val appVersion: String,
    val databaseSchemaVersion: Int,
    val payloadSha256: String,
)

data class DecodedBackup(
    val metadata: BackupMetadata,
    val snapshot: BackupSnapshot,
)

data class BackupPreview(
    val metadata: BackupMetadata,
    val exerciseCount: Int,
    val routineCount: Int,
    val workoutCount: Int,
    val setCount: Int,
    val earliestWorkoutStartedAt: Long?,
    val latestWorkoutStartedAt: Long?,
    val hasActiveWorkout: Boolean,
)

class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class BackupValidationException(message: String) : IllegalArgumentException(message)
