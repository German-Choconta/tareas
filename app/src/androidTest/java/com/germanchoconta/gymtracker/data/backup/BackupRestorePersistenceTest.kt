package com.germanchoconta.gymtracker.data.backup

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseMuscleEntity
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase
import com.germanchoconta.gymtracker.data.local.MuscleEntity
import com.germanchoconta.gymtracker.data.local.MuscleRoles
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.data.local.WorkoutEntity
import com.germanchoconta.gymtracker.data.local.WorkoutExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestorePersistenceTest {
    private lateinit var sourceDb: GymTrackerDatabase
    private lateinit var targetDb: GymTrackerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDb = newDatabase(context)
        targetDb = newDatabase(context)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun dbExportFreshDbRestorePreservesComplexCanonicalHistoryExactly() = runTest {
        val sourceRepository = BackupRepository(sourceDb, sourceDb.backupDao())
        val targetRepository = BackupRepository(targetDb, targetDb.backupDao())
        val expected = complexSyntheticSnapshot().normalized()
        sourceRepository.replaceAll(expected)

        val encoded = PortableBackupCodec.encode(
            sourceRepository.snapshot(),
            generatedAtEpochMillis = 20_000,
            appVersion = "instrumented-synthetic",
        )
        val decoded = PortableBackupCodec.decode(encoded)
        val preview = BackupValidator.validate(decoded)
        targetRepository.replaceAll(decoded.snapshot)

        assertEquals(expected, targetRepository.snapshot().normalized())
        assertEquals(2, preview.workoutCount)
        assertEquals(4, preview.setCount)
        assertTrue(preview.hasActiveWorkout)
        assertTrue(targetRepository.snapshot().exercises.single { it.id == "exercise-archived" }.archived)
        assertEquals(
            null,
            targetRepository.snapshot().workouts.single { it.id == "workout-history" }.routineId,
        )
        assertEquals(
            2,
            targetRepository.snapshot().workoutExercises.count {
                it.workoutId == "workout-history" && it.exerciseId == "exercise-archived"
            },
        )
    }

    @Test
    fun failingRestoreRollsBackAndLeavesOriginalDatasetUntouched() = runTest {
        val repository = BackupRepository(sourceDb, sourceDb.backupDao())
        val original = complexSyntheticSnapshot().normalized()
        repository.replaceAll(original)

        val invalid = original.copy(
            workoutSets = original.workoutSets + WorkoutSetEntity(
                id = "set-broken-foreign-key",
                workoutExerciseId = "missing-workout-exercise",
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 1_000,
                reps = 1,
                completedAt = 5_100,
            ),
        )
        val result = runCatching { repository.replaceAll(invalid) }

        assertTrue(result.isFailure)
        assertEquals(original, repository.snapshot().normalized())
    }

    @Test
    fun invalidPortableBackupNeverMutatesRoom() = runTest {
        val repository = BackupRepository(sourceDb, sourceDb.backupDao())
        val original = complexSyntheticSnapshot().normalized()
        repository.replaceAll(original)
        val encoded = PortableBackupCodec.encode(original, 20_000, "instrumented-synthetic")
            .toString(Charsets.UTF_8)
            .replace("\"loadGrams\":42500", "\"loadGrams\":42501")

        val decode = runCatching { PortableBackupCodec.decode(encoded.toByteArray(Charsets.UTF_8)) }

        assertTrue(decode.isFailure)
        assertEquals(original, repository.snapshot().normalized())
    }

    private fun newDatabase(context: Context): GymTrackerDatabase =
        Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun complexSyntheticSnapshot(): BackupSnapshot {
        val activeExercise = ExerciseEntity(
            id = "exercise-active",
            name = "Synthetic Active Exercise",
            defaultRepMin = 6,
            defaultRepMax = 10,
            defaultTargetRirTenths = 10,
            defaultRestSeconds = 90,
            defaultLoadIncrementGrams = 1_250,
        )
        val archivedExercise = ExerciseEntity(
            id = "exercise-archived",
            name = "Synthetic Archived Exercise",
            archived = true,
        )
        val muscle = MuscleEntity("muscle-synthetic", "Synthetic Muscle")
        val routine = RoutineEntity("routine-current", "Synthetic Routine", 0)
        val routineExercise = RoutineExerciseEntity(
            id = "routine-exercise-current",
            routineId = routine.id,
            exerciseId = activeExercise.id,
            position = 0,
            targetSetCount = 2,
            repMin = 6,
            repMax = 10,
            targetRirTenths = 10,
            restSeconds = 90,
            loadIncrementGrams = 1_250,
            previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE,
        )
        val historyWorkout = WorkoutEntity(
            id = "workout-history",
            routineId = null,
            title = "Synthetic Deleted Routine Snapshot",
            startedAt = 1_000,
            finishedAt = 3_000,
        )
        val historyA = WorkoutExerciseEntity(
            id = "workout-exercise-history-a",
            workoutId = historyWorkout.id,
            exerciseId = archivedExercise.id,
            routineExerciseId = null,
            position = 0,
        )
        val historyB = WorkoutExerciseEntity(
            id = "workout-exercise-history-b",
            workoutId = historyWorkout.id,
            exerciseId = archivedExercise.id,
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
            id = "workout-active",
            routineId = routine.id,
            title = routine.name,
            startedAt = 5_000,
            restTimerEndsAt = 6_500,
            restTimerWorkoutExerciseId = "workout-exercise-active",
        )
        val activeOccurrence = WorkoutExerciseEntity(
            id = "workout-exercise-active",
            workoutId = activeWorkout.id,
            exerciseId = activeExercise.id,
            routineExerciseId = routineExercise.id,
            position = 0,
            targetSetCount = 2,
            repMin = 6,
            repMax = 10,
            targetRirTenths = 10,
            restSeconds = 90,
            loadIncrementGrams = 1_250,
            previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE,
        )
        return BackupSnapshot(
            exercises = listOf(activeExercise, archivedExercise),
            muscles = listOf(muscle),
            exerciseMuscles = listOf(
                ExerciseMuscleEntity(activeExercise.id, muscle.id, MuscleRoles.PRIMARY),
            ),
            routines = listOf(routine),
            routineExercises = listOf(routineExercise),
            workouts = listOf(historyWorkout, activeWorkout),
            workoutExercises = listOf(historyA, historyB, activeOccurrence),
            workoutSets = listOf(
                WorkoutSetEntity(
                    id = "set-history-a",
                    workoutExerciseId = historyA.id,
                    position = 0,
                    type = SetTypes.WORK,
                    loadGrams = 42_500,
                    reps = 10,
                    rirTenths = 15,
                    completedAt = 1_500,
                ),
                WorkoutSetEntity(
                    id = "set-history-b",
                    workoutExerciseId = historyB.id,
                    position = 0,
                    type = SetTypes.DROP,
                    loadGrams = 30_000,
                    reps = 8,
                    completedAt = 1_700,
                ),
                WorkoutSetEntity(
                    id = "set-active-complete",
                    workoutExerciseId = activeOccurrence.id,
                    position = 0,
                    type = SetTypes.FAILURE,
                    loadGrams = 123_456,
                    reps = 5,
                    rirTenths = 0,
                    completedAt = 5_500,
                ),
                WorkoutSetEntity(
                    id = "set-active-incomplete",
                    workoutExerciseId = activeOccurrence.id,
                    position = 1,
                    type = SetTypes.WARMUP,
                    loadGrams = 0,
                    reps = 0,
                ),
            ),
        )
    }
}
