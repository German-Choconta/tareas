package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressionEvidencePersistenceTest {
    private lateinit var db: GymTrackerDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun progressionEvidenceKeepsOnlyFinishedCompletedWorkAndRespectsReferenceMode() = runTest {
        val exercise = ExerciseEntity("synthetic-progression-exercise", "Synthetic Progression Exercise")
        val routineA = RoutineEntity("synthetic-progression-routine-a", "Synthetic A", 0)
        val routineB = RoutineEntity("synthetic-progression-routine-b", "Synthetic B", 1)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routineA)
        db.routineDao().upsert(routineB)

        seedSet("old-a", routineA.id, exercise.id, 100L, SetTypes.WORK, completed = true, reps = 9)
        seedSet("new-b", routineB.id, exercise.id, 300L, SetTypes.WORK, completed = true, reps = 10)
        seedSet("warmup-a", routineA.id, exercise.id, 400L, SetTypes.WARMUP, completed = true, reps = 12)
        seedSet("drop-a", routineA.id, exercise.id, 410L, SetTypes.DROP, completed = true, reps = 12)
        seedSet("failure-a", routineA.id, exercise.id, 420L, SetTypes.FAILURE, completed = true, reps = 12)
        seedSet("incomplete-a", routineA.id, exercise.id, 430L, SetTypes.WORK, completed = false, reps = 12)
        seedSet("zero-reps-a", routineA.id, exercise.id, 440L, SetTypes.WORK, completed = true, reps = 0)
        seedSet("active-a", routineA.id, exercise.id, 500L, SetTypes.WORK, completed = true, reps = 12, finished = false)

        val any = repository.progressionObservations(
            exerciseId = exercise.id,
            referenceMode = PreviousReferenceModes.ANY_WORKOUT,
            routineId = routineA.id,
            beforeStartedAt = 1_000L,
        )
        val same = repository.progressionObservations(
            exerciseId = exercise.id,
            referenceMode = PreviousReferenceModes.SAME_ROUTINE,
            routineId = routineA.id,
            beforeStartedAt = 1_000L,
        )

        assertEquals(listOf("new-b", "old-a"), any.map { it.workoutId })
        assertEquals(listOf("old-a"), same.map { it.workoutId })
    }

    @Test
    fun sameRoutineWithoutRoutineIdSafelyReturnsNoEvidence() = runTest {
        val result = repository.progressionObservations(
            exerciseId = "synthetic-missing-routine-exercise",
            referenceMode = PreviousReferenceModes.SAME_ROUTINE,
            routineId = null,
            beforeStartedAt = Long.MAX_VALUE,
        )

        assertEquals(emptyList<com.germanchoconta.gymtracker.domain.ProgressionObservation>(), result)
    }

    private suspend fun seedSet(
        workoutId: String,
        routineId: String,
        exerciseId: String,
        startedAt: Long,
        type: String,
        completed: Boolean,
        reps: Int,
        finished: Boolean = true,
    ) {
        val workout = WorkoutEntity(
            id = workoutId,
            routineId = routineId,
            title = "Synthetic $workoutId",
            startedAt = startedAt,
            finishedAt = if (finished) startedAt + 50L else null,
        )
        val workoutExercise = WorkoutExerciseEntity(
            id = "we-$workoutId",
            workoutId = workoutId,
            exerciseId = exerciseId,
            position = 0,
        )
        val set = WorkoutSetEntity(
            id = "set-$workoutId",
            workoutExerciseId = workoutExercise.id,
            position = 0,
            type = type,
            loadGrams = 40_000L,
            reps = reps,
            rirTenths = 20,
            completedAt = if (completed) startedAt + 25L else null,
        )
        db.workoutDao().upsert(workout)
        db.workoutDao().upsertExercise(workoutExercise)
        db.workoutDao().upsertSet(set)
    }
}
