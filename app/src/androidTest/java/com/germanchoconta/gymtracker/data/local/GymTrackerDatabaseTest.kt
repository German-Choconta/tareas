package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymTrackerDatabaseTest {
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
    fun completeWorkoutRoundTripPreservesExactLoadRirAndOrdering() = runTest {
        val exercise = ExerciseEntity(id = "exercise-bench", name = "Bench Press")
        val chest = MuscleEntity(id = "muscle-chest", name = "Chest")
        db.exerciseDao().upsert(exercise)
        db.muscleDao().upsert(chest)
        db.muscleDao().upsertLink(
            ExerciseMuscleEntity(exercise.id, chest.id, MuscleRoles.PRIMARY),
        )

        val routine = RoutineEntity(id = "routine-push", name = "Push", position = 0)
        db.routineDao().upsert(routine)
        val routineExercise = RoutineExerciseEntity(
            id = "routine-exercise-bench",
            routineId = routine.id,
            exerciseId = exercise.id,
            position = 0,
            targetSetCount = 2,
            repMin = 8,
            repMax = 12,
            targetRirTenths = 15,
            restSeconds = 120,
            loadIncrementGrams = 2500,
            previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
        )
        db.routineDao().upsertExercise(routineExercise)

        val workout = WorkoutEntity(
            id = "workout-1",
            routineId = routine.id,
            title = "Push",
            startedAt = 1_000,
            finishedAt = 2_000,
        )
        db.workoutDao().upsert(workout)
        val workoutExercise = WorkoutExerciseEntity(
            id = "workout-exercise-1",
            workoutId = workout.id,
            exerciseId = exercise.id,
            routineExerciseId = routineExercise.id,
            position = 0,
        )
        db.workoutDao().upsertExercise(workoutExercise)
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "set-2",
                workoutExerciseId = workoutExercise.id,
                position = 1,
                type = SetTypes.WORK,
                loadGrams = 42_500,
                reps = 9,
                rirTenths = 15,
                completedAt = 1_500,
            ),
        )
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "set-1",
                workoutExerciseId = workoutExercise.id,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 42_500,
                reps = 10,
                rirTenths = 15,
                completedAt = 1_400,
            ),
        )

        val sets = db.workoutDao().getSets(workoutExercise.id)
        assertEquals(listOf(0, 1), sets.map { it.position })
        assertEquals(42_500L, sets.first().loadGrams)
        assertEquals(15, sets.first().rirTenths)
        assertEquals(42.5, sets.first().loadGrams / 1000.0, 0.0)
        assertEquals(1.5, sets.first().rirTenths!! / 10.0, 0.0)

        val muscles = db.muscleDao().observeForExercise(exercise.id).first()
        assertEquals(listOf(chest), muscles)
        val history = db.workoutDao().observeExerciseHistory(exercise.id).first()
        assertEquals(2, history.size)
        assertEquals(listOf(0, 1), history.map { it.setPosition })
    }

    @Test
    fun routineExercisesAreReturnedInConfiguredOrder() = runTest {
        val routine = RoutineEntity(id = "routine-order", name = "Ordered", position = 0)
        val firstExercise = ExerciseEntity(id = "exercise-first", name = "First")
        val secondExercise = ExerciseEntity(id = "exercise-second", name = "Second")
        db.routineDao().upsert(routine)
        db.exerciseDao().upsert(firstExercise)
        db.exerciseDao().upsert(secondExercise)

        fun routineExercise(id: String, exerciseId: String, position: Int) =
            RoutineExerciseEntity(
                id = id,
                routineId = routine.id,
                exerciseId = exerciseId,
                position = position,
                targetSetCount = 3,
                repMin = 8,
                repMax = 12,
                restSeconds = 90,
                loadIncrementGrams = 2_500,
            )

        db.routineDao().upsertExercise(routineExercise("re-second", secondExercise.id, 1))
        db.routineDao().upsertExercise(routineExercise("re-first", firstExercise.id, 0))

        val ordered = db.routineDao().observeExercises(routine.id).first()
        assertEquals(listOf(firstExercise.id, secondExercise.id), ordered.map { it.exerciseId })
    }

    @Test
    fun previousAnyWorkoutAndSameRoutineResolveDifferentSessions() = runTest {
        val exercise = ExerciseEntity(id = "exercise-row", name = "Row")
        db.exerciseDao().upsert(exercise)
        val routineA = RoutineEntity(id = "routine-a", name = "A", position = 0)
        val routineB = RoutineEntity(id = "routine-b", name = "B", position = 1)
        db.routineDao().upsert(routineA)
        db.routineDao().upsert(routineB)

        fun workout(id: String, routineId: String, startedAt: Long) = WorkoutEntity(
            id = id,
            routineId = routineId,
            title = id,
            startedAt = startedAt,
            finishedAt = startedAt + 100,
        )

        val olderA = workout("workout-a-old", routineA.id, 1_000)
        val newerB = workout("workout-b-new", routineB.id, 2_000)
        db.workoutDao().upsert(olderA)
        db.workoutDao().upsert(newerB)
        db.workoutDao().upsertExercise(
            WorkoutExerciseEntity("we-a", olderA.id, exercise.id, position = 0),
        )
        db.workoutDao().upsertExercise(
            WorkoutExerciseEntity("we-b", newerB.id, exercise.id, position = 0),
        )

        val any = db.workoutDao().previousAnyWorkout(exercise.id, 3_000)
        val sameRoutine = db.workoutDao().previousSameRoutine(exercise.id, routineA.id, 3_000)
        assertEquals(newerB.id, any?.workoutId)
        assertEquals(olderA.id, sameRoutine?.workoutId)
    }

    @Test
    fun archivingExerciseAndRoutinePreservesCompletedWorkoutHistory() = runTest {
        val exercise = ExerciseEntity(id = "exercise-squat", name = "Squat")
        val routine = RoutineEntity(id = "routine-legs", name = "Legs", position = 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)
        val workout = WorkoutEntity(
            id = "workout-legs",
            routineId = routine.id,
            title = "Legs",
            startedAt = 1_000,
            finishedAt = 2_000,
        )
        db.workoutDao().upsert(workout)
        val workoutExercise = WorkoutExerciseEntity(
            id = "we-squat",
            workoutId = workout.id,
            exerciseId = exercise.id,
            position = 0,
        )
        db.workoutDao().upsertExercise(workoutExercise)
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "set-squat",
                workoutExerciseId = workoutExercise.id,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 100_000,
                reps = 8,
                completedAt = 1_500,
            ),
        )

        db.exerciseDao().archive(exercise.id)
        db.routineDao().archive(routine.id)

        assertTrue(db.exerciseDao().getById(exercise.id)!!.archived)
        assertNotNull(db.workoutDao().getWorkout(workout.id))
        assertEquals(1, db.workoutDao().getSets(workoutExercise.id).size)
    }

    @Test
    fun fileDatabaseSurvivesCloseAndReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(GymTrackerDatabase.DATABASE_NAME)

        var fileDb = GymTrackerDatabase.build(context)
        fileDb.exerciseDao().upsert(ExerciseEntity(id = "persisted", name = "Persisted exercise"))
        fileDb.close()

        fileDb = GymTrackerDatabase.build(context)
        val restored = fileDb.exerciseDao().getById("persisted")
        fileDb.close()
        context.deleteDatabase(GymTrackerDatabase.DATABASE_NAME)

        assertEquals("Persisted exercise", restored?.name)
    }
}
