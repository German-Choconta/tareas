package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutLoggerConcurrencyTest {
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
    fun rapidStartRequestsResolveToOneActiveWorkout() = runTest {
        val routineId = seedRoutine(targetSetCount = 1)
        val started = coroutineScope {
            listOf(
                async { repository.startFromRoutine(routineId, 1_000L) },
                async { repository.startFromRoutine(routineId, 1_001L) },
            ).awaitAll()
        }

        assertNotNull(started[0])
        assertEquals(started[0]?.id, started[1]?.id)
        assertEquals(started[0]?.id, repository.getActiveWorkout()?.id)
    }

    @Test
    fun rapidAddSetRequestsUseUniqueDensePositions() = runTest {
        val routineId = seedRoutine(targetSetCount = 2)
        val workout = requireNotNull(repository.startFromRoutine(routineId, 2_000L))
        val workoutExercise = repository.getExercises(workout.id).single()

        coroutineScope {
            listOf(
                async { repository.addSet(workoutExercise.id) },
                async { repository.addSet(workoutExercise.id) },
                async { repository.addSet(workoutExercise.id) },
            ).awaitAll()
        }

        assertEquals(
            listOf(0, 1, 2, 3, 4),
            repository.getSets(workoutExercise.id).map { it.position },
        )
    }

    @Test
    fun rapidAddExerciseRequestsUseUniqueDensePositionsAndAtomicSets() = runTest {
        val routineId = seedRoutine(targetSetCount = 1)
        val workout = requireNotNull(repository.startFromRoutine(routineId, 3_000L))
        val extraA = ExerciseEntity(id = "synthetic-extra-a", name = "Synthetic Extra A")
        val extraB = ExerciseEntity(id = "synthetic-extra-b", name = "Synthetic Extra B")
        db.exerciseDao().upsert(extraA)
        db.exerciseDao().upsert(extraB)

        coroutineScope {
            listOf(
                async { repository.addExercise(workout.id, extraA.id) },
                async { repository.addExercise(workout.id, extraB.id) },
            ).awaitAll()
        }

        val exercises = repository.getExercises(workout.id)
        assertEquals(listOf(0, 1, 2), exercises.map { it.position })
        exercises.drop(1).forEach { added ->
            assertEquals(listOf(0, 1, 2), repository.getSets(added.id).map { it.position })
        }
    }

    private suspend fun seedRoutine(targetSetCount: Int): String {
        val exercise = ExerciseEntity(id = "synthetic-seed-exercise", name = "Synthetic Seed Exercise")
        val routine = RoutineEntity(id = "synthetic-seed-routine", name = "Synthetic Seed Routine", position = 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)
        db.routineDao().upsertExercise(
            RoutineExerciseEntity(
                id = "synthetic-seed-template",
                routineId = routine.id,
                exerciseId = exercise.id,
                position = 0,
                targetSetCount = targetSetCount,
                repMin = 8,
                repMax = 12,
                targetRirTenths = 20,
                restSeconds = 90,
                loadIncrementGrams = 2_500,
            ),
        )
        return routine.id
    }
}
