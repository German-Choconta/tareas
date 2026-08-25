package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V1ReliabilityPersistenceTest {
    private lateinit var db: GymTrackerDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = WorkoutRepository(db.workoutDao(), db.routineDao(), db.exerciseDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aggregateHydrationBatchesSetsWithoutChangingDeterministicOrder() = runTest {
        val firstExercise = ExerciseEntity("synthetic-batch-a", "Synthetic Batch A")
        val secondExercise = ExerciseEntity("synthetic-batch-b", "Synthetic Batch B")
        db.exerciseDao().upsert(firstExercise)
        db.exerciseDao().upsert(secondExercise)

        val workout = WorkoutEntity("synthetic-batch-workout", null, "Synthetic Batch", 10_000L)
        val first = WorkoutExerciseEntity(
            id = "synthetic-batch-we-a",
            workoutId = workout.id,
            exerciseId = firstExercise.id,
            position = 0,
        )
        val second = WorkoutExerciseEntity(
            id = "synthetic-batch-we-b",
            workoutId = workout.id,
            exerciseId = secondExercise.id,
            position = 1,
        )
        db.workoutDao().insertWorkoutAggregate(
            workout = workout,
            exercises = listOf(first, second),
            sets = listOf(
                WorkoutSetEntity("synthetic-batch-a-1", first.id, 1, SetTypes.WORK, 31_000L, 8),
                WorkoutSetEntity("synthetic-batch-b-0", second.id, 0, SetTypes.WORK, 42_000L, 6),
                WorkoutSetEntity("synthetic-batch-a-0", first.id, 0, SetTypes.WORK, 30_000L, 9),
                WorkoutSetEntity("synthetic-batch-b-1", second.id, 1, SetTypes.WORK, 43_000L, 5),
            ),
        )

        val aggregate = requireNotNull(repository.getAggregate(workout.id))
        assertEquals(listOf(first.id, second.id), aggregate.exercises.map { it.exercise.id })
        assertEquals(listOf(0, 1), aggregate.exercises[0].sets.map { it.position })
        assertEquals(listOf(0, 1), aggregate.exercises[1].sets.map { it.position })
        assertEquals(listOf(30_000L, 31_000L), aggregate.exercises[0].sets.map { it.loadGrams })
        assertEquals(listOf(42_000L, 43_000L), aggregate.exercises[1].sets.map { it.loadGrams })
    }

    @Test
    fun finishWorkoutIsAtomicAndIdempotentAgainstRepeatedConfirmation() = runTest {
        val workout = WorkoutEntity(
            id = "synthetic-finish-once",
            routineId = null,
            title = "Synthetic Finish Once",
            startedAt = 10_000L,
        )
        db.workoutDao().upsert(workout)

        assertTrue(repository.finishWorkout(workout.id, 20_000L))
        assertFalse(repository.finishWorkout(workout.id, 21_000L))
        assertEquals(20_000L, repository.getWorkout(workout.id)?.finishedAt)
    }
}
