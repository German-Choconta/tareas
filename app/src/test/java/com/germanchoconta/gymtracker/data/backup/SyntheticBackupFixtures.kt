package com.germanchoconta.gymtracker.data.backup

import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseMuscleEntity
import com.germanchoconta.gymtracker.data.local.MuscleEntity
import com.germanchoconta.gymtracker.data.local.MuscleRoles
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.data.local.WorkoutEntity
import com.germanchoconta.gymtracker.data.local.WorkoutExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity

internal object SyntheticBackupFixtures {
    fun complex(): BackupSnapshot {
        val exerciseA = ExerciseEntity(
            id = "exercise-synthetic-a",
            name = "Synthetic Press",
            equipment = "Synthetic machine",
            unilateral = false,
            notes = "Synthetic note, with comma",
            defaultRepMin = 6,
            defaultRepMax = 12,
            defaultTargetRirTenths = 15,
            defaultRestSeconds = 120,
            defaultLoadIncrementGrams = 1_250,
        )
        val exerciseB = ExerciseEntity(
            id = "exercise-synthetic-b",
            name = "Synthetic Row \"Ω\"",
            unilateral = true,
            notes = "Synthetic first line\nSynthetic second line",
            archived = true,
        )
        val muscleA = MuscleEntity("muscle-synthetic-a", "Synthetic Chest")
        val muscleB = MuscleEntity("muscle-synthetic-b", "Synthetic Back")
        val routine = RoutineEntity(
            id = "routine-synthetic-a",
            name = "Synthetic Routine",
            position = 0,
            notes = "Only synthetic fixture data",
        )
        val routineExercise = RoutineExerciseEntity(
            id = "routine-exercise-synthetic-a",
            routineId = routine.id,
            exerciseId = exerciseA.id,
            position = 0,
            targetSetCount = 2,
            repMin = 6,
            repMax = 10,
            targetRirTenths = 10,
            restSeconds = 90,
            loadIncrementGrams = 1_250,
            previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE,
        )

        val historicalWorkout = WorkoutEntity(
            id = "workout-synthetic-history",
            routineId = null,
            title = "Deleted routine snapshot, \"synthetic\"",
            startedAt = 1_000,
            finishedAt = 3_000,
            notes = "Synthetic workout\nwith newline",
        )
        val historicalOccurrenceA = WorkoutExerciseEntity(
            id = "workout-exercise-history-a",
            workoutId = historicalWorkout.id,
            exerciseId = exerciseB.id,
            routineExerciseId = null,
            position = 0,
            notes = "Archived synthetic occurrence",
            targetSetCount = null,
            repMin = null,
            repMax = null,
            targetRirTenths = null,
            restSeconds = null,
            loadIncrementGrams = null,
            previousReferenceMode = null,
        )
        val historicalOccurrenceB = WorkoutExerciseEntity(
            id = "workout-exercise-history-b",
            workoutId = historicalWorkout.id,
            exerciseId = exerciseB.id,
            routineExerciseId = null,
            position = 1,
            targetSetCount = 1,
            repMin = 8,
            repMax = 12,
            targetRirTenths = 20,
            restSeconds = 60,
            loadIncrementGrams = 500,
            previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
        )

        val activeWorkout = WorkoutEntity(
            id = "workout-synthetic-active",
            routineId = routine.id,
            title = "Synthetic Routine",
            startedAt = 5_000,
            finishedAt = null,
            notes = null,
            restTimerEndsAt = 6_500,
            restTimerWorkoutExerciseId = "workout-exercise-active",
        )
        val activeOccurrence = WorkoutExerciseEntity(
            id = "workout-exercise-active",
            workoutId = activeWorkout.id,
            exerciseId = exerciseA.id,
            routineExerciseId = routineExercise.id,
            position = 0,
            notes = null,
            targetSetCount = 2,
            repMin = 6,
            repMax = 10,
            targetRirTenths = 10,
            restSeconds = 90,
            loadIncrementGrams = 1_250,
            previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE,
        )

        return BackupSnapshot(
            exercises = listOf(exerciseB, exerciseA),
            muscles = listOf(muscleB, muscleA),
            exerciseMuscles = listOf(
                ExerciseMuscleEntity(exerciseB.id, muscleB.id, MuscleRoles.SECONDARY),
                ExerciseMuscleEntity(exerciseA.id, muscleA.id, MuscleRoles.PRIMARY),
            ),
            routines = listOf(routine),
            routineExercises = listOf(routineExercise),
            workouts = listOf(activeWorkout, historicalWorkout),
            workoutExercises = listOf(activeOccurrence, historicalOccurrenceB, historicalOccurrenceA),
            workoutSets = listOf(
                WorkoutSetEntity(
                    id = "set-synthetic-active-incomplete",
                    workoutExerciseId = activeOccurrence.id,
                    position = 1,
                    type = SetTypes.WARMUP,
                    loadGrams = 0,
                    reps = 0,
                    rirTenths = null,
                    completedAt = null,
                ),
                WorkoutSetEntity(
                    id = "set-synthetic-history-b",
                    workoutExerciseId = historicalOccurrenceB.id,
                    position = 0,
                    type = SetTypes.DROP,
                    loadGrams = 30_000,
                    reps = 8,
                    rirTenths = null,
                    completedAt = 1_700,
                ),
                WorkoutSetEntity(
                    id = "set-synthetic-active-complete",
                    workoutExerciseId = activeOccurrence.id,
                    position = 0,
                    type = SetTypes.FAILURE,
                    loadGrams = 123_456,
                    reps = 5,
                    rirTenths = 0,
                    completedAt = 5_500,
                ),
                WorkoutSetEntity(
                    id = "set-synthetic-history-a",
                    workoutExerciseId = historicalOccurrenceA.id,
                    position = 0,
                    type = SetTypes.WORK,
                    loadGrams = 42_500,
                    reps = 10,
                    rirTenths = 15,
                    completedAt = 1_500,
                ),
            ),
        )
    }

    fun decoded(snapshot: BackupSnapshot = complex()): DecodedBackup = DecodedBackup(
        metadata = BackupMetadata(
            formatVersion = BackupFormat.VERSION,
            generatedAtEpochMillis = 10_000,
            appVersion = "0.1.0-synthetic",
            databaseSchemaVersion = BackupFormat.DATABASE_SCHEMA_VERSION,
            payloadSha256 = "0".repeat(64),
        ),
        snapshot = snapshot.normalized(),
    )
}
