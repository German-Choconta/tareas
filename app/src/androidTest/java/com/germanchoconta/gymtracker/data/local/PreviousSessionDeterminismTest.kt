package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviousSessionDeterminismTest {
    private lateinit var db: GymTrackerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun anyWorkoutAndSameRoutineKeepPr4SemanticsWithDeterministicTies() = runTest {
        val exercise = ExerciseEntity("synthetic-previous-exercise", "Synthetic Previous Exercise")
        val sameRoutine = RoutineEntity("synthetic-routine-a", "Synthetic Routine A", 0)
        val otherRoutine = RoutineEntity("synthetic-routine-b", "Synthetic Routine B", 1)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(sameRoutine)
        db.routineDao().upsert(otherRoutine)

        seedFinished("a-same", sameRoutine.id, exercise.id, 1_000L)
        seedFinished("m-same", sameRoutine.id, exercise.id, 1_000L)
        seedFinished("z-other", otherRoutine.id, exercise.id, 1_000L)
        seedFinished("old-same", sameRoutine.id, exercise.id, 500L)
        seedActive("zz-active", sameRoutine.id, exercise.id, 1_500L)

        val any = db.workoutDao().previousAnyWorkout(exercise.id, beforeStartedAt = 2_000L)
        val same = db.workoutDao().previousSameRoutine(
            exerciseId = exercise.id,
            routineId = sameRoutine.id,
            beforeStartedAt = 2_000L,
        )
        val strictlyEarlier = db.workoutDao().previousAnyWorkout(exercise.id, beforeStartedAt = 1_000L)

        assertEquals("z-other", any?.workoutId)
        assertEquals("m-same", same?.workoutId)
        assertEquals("old-same", strictlyEarlier?.workoutId)
    }

    @Test
    fun sameRoutineWithoutHistoricalMatchReturnsNull() = runTest {
        val exercise = ExerciseEntity("synthetic-no-match-exercise", "Synthetic No Match Exercise")
        val routine = RoutineEntity("synthetic-no-match-routine", "Synthetic No Match Routine", 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)

        assertNull(
            db.workoutDao().previousSameRoutine(
                exerciseId = exercise.id,
                routineId = routine.id,
                beforeStartedAt = Long.MAX_VALUE,
            ),
        )
    }

    private suspend fun seedFinished(
        workoutId: String,
        routineId: String,
        exerciseId: String,
        startedAt: Long,
    ) {
        val workout = WorkoutEntity(
            id = workoutId,
            routineId = routineId,
            title = "Synthetic $workoutId",
            startedAt = startedAt,
            finishedAt = startedAt + 250L,
        )
        db.workoutDao().upsert(workout)
        db.workoutDao().upsertExercise(
            WorkoutExerciseEntity(
                id = "we-$workoutId",
                workoutId = workout.id,
                exerciseId = exerciseId,
                position = 0,
            ),
        )
    }

    private suspend fun seedActive(
        workoutId: String,
        routineId: String,
        exerciseId: String,
        startedAt: Long,
    ) {
        val workout = WorkoutEntity(
            id = workoutId,
            routineId = routineId,
            title = "Synthetic $workoutId",
            startedAt = startedAt,
            finishedAt = null,
        )
        db.workoutDao().upsert(workout)
        db.workoutDao().upsertExercise(
            WorkoutExerciseEntity(
                id = "we-$workoutId",
                workoutId = workout.id,
                exerciseId = exerciseId,
                position = 0,
            ),
        )
    }
}
