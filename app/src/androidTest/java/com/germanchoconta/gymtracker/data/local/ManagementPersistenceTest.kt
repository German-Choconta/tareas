package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagementPersistenceTest {
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
    fun createEditArchiveExerciseAndReplaceMuscleAssignments() = runTest {
        val exerciseRepository = ExerciseRepository(db.exerciseDao(), db.muscleDao())
        val chest = MuscleEntity("chest", "Chest")
        val triceps = MuscleEntity("triceps", "Triceps")
        exerciseRepository.saveWithMuscles(
            exercise = ExerciseEntity(
                id = "bench",
                name = "Bench Press",
                equipment = "Barbell",
                defaultRepMin = 8,
                defaultRepMax = 12,
                defaultTargetRirTenths = 15,
                defaultRestSeconds = 120,
                defaultLoadIncrementGrams = 2_500,
            ),
            muscles = listOf(chest, triceps),
            links = listOf(
                ExerciseMuscleEntity("bench", "chest", MuscleRoles.PRIMARY),
                ExerciseMuscleEntity("bench", "triceps", MuscleRoles.SECONDARY),
            ),
        )

        exerciseRepository.saveWithMuscles(
            exercise = requireNotNull(exerciseRepository.getById("bench")).copy(
                name = "Paused Bench Press",
                unilateral = false,
                notes = "Synthetic test fixture",
            ),
            muscles = listOf(chest, triceps),
            links = listOf(
                ExerciseMuscleEntity("bench", "triceps", MuscleRoles.PRIMARY),
            ),
        )

        val edited = requireNotNull(exerciseRepository.getById("bench"))
        val assignments = exerciseRepository.getAssignments("bench")
        assertEquals("Paused Bench Press", edited.name)
        assertEquals(2_500L, edited.defaultLoadIncrementGrams)
        assertEquals(15, edited.defaultTargetRirTenths)
        assertEquals(listOf("triceps"), assignments.map { it.muscleId })
        assertEquals(MuscleRoles.PRIMARY, assignments.single().role)

        exerciseRepository.archive("bench")
        assertTrue(exerciseRepository.observeActive().first().isEmpty())
        assertTrue(requireNotNull(exerciseRepository.getById("bench")).archived)
    }

    @Test
    fun routineCreateEditReorderAndRemovePersistsOrderedTargets() = runTest {
        val routineRepository = RoutineRepository(db.routineDao())
        db.exerciseDao().upsert(ExerciseEntity("bench", "Bench Press"))
        db.exerciseDao().upsert(ExerciseEntity("row", "Cable Row"))
        db.exerciseDao().upsert(ExerciseEntity("curl", "Curl"))

        val routine = RoutineEntity("upper", "Upper", 0)
        val bench = routineExercise("upper-bench", "upper", "bench", 0, 8, 12)
        val row = routineExercise("upper-row", "upper", "row", 1, 10, 15)
        routineRepository.saveWithExercises(routine, listOf(bench, row))

        val curl = routineExercise("upper-curl", "upper", "curl", 1, 10, 12).copy(
            targetSetCount = 2,
            targetRirTenths = 20,
            restSeconds = 75,
            loadIncrementGrams = 1_250,
            previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE,
        )
        routineRepository.saveWithExercises(
            routine.copy(name = "Upper A", notes = "Synthetic routine"),
            listOf(row.copy(position = 0), curl),
        )

        val saved = routineRepository.getExercises("upper")
        assertEquals(listOf("row", "curl"), saved.map { it.exerciseId })
        assertEquals(listOf(0, 1), saved.map { it.position })
        assertEquals(2, saved[1].targetSetCount)
        assertEquals(20, saved[1].targetRirTenths)
        assertEquals(75, saved[1].restSeconds)
        assertEquals(1_250L, saved[1].loadIncrementGrams)
        assertEquals(PreviousReferenceModes.SAME_ROUTINE, saved[1].previousReferenceMode)
        assertEquals("Upper A", routineRepository.getById("upper")?.name)
    }

    @Test
    fun removingRoutineExerciseKeepsCompletedWorkoutHistory() = runTest {
        val routineRepository = RoutineRepository(db.routineDao())
        val exercise = ExerciseEntity("bench", "Bench Press")
        db.exerciseDao().upsert(exercise)
        val routine = RoutineEntity("push", "Push", 0)
        val routineExercise = routineExercise("push-bench", "push", "bench", 0, 8, 12)
        routineRepository.saveWithExercises(routine, listOf(routineExercise))

        db.workoutDao().upsert(
            WorkoutEntity("workout", routine.id, "Push", startedAt = 1_000, finishedAt = 2_000),
        )
        db.workoutDao().upsertExercise(
            WorkoutExerciseEntity(
                id = "workout-bench",
                workoutId = "workout",
                exerciseId = exercise.id,
                routineExerciseId = routineExercise.id,
                position = 0,
            ),
        )
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "set",
                workoutExerciseId = "workout-bench",
                position = 0,
                loadGrams = 42_500,
                reps = 10,
                rirTenths = 15,
                completedAt = 1_500,
            ),
        )

        routineRepository.saveWithExercises(routine, emptyList())

        val workoutExercise = db.workoutDao().getExercises("workout").single()
        val set = db.workoutDao().getSets("workout-bench").single()
        assertNull(workoutExercise.routineExerciseId)
        assertEquals(42_500L, set.loadGrams)
        assertEquals(15, set.rirTenths)
    }

    @Test
    fun exerciseAndRoutineEditorDataSurvivesDatabaseReopen() = runTest {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "management-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)

        fun open(): GymTrackerDatabase = Room.databaseBuilder<GymTrackerDatabase>(context, databaseName)
            .setDriver(BundledSQLiteDriver())
            .build()

        var diskDb = open()
        diskDb.exerciseDao().upsert(
            ExerciseEntity(
                id = "squat",
                name = "Back Squat",
                equipment = "Barbell",
                defaultRepMin = 5,
                defaultRepMax = 8,
            ),
        )
        diskDb.routineDao().saveWithExercises(
            RoutineEntity("lower", "Lower", 0),
            listOf(routineExercise("lower-squat", "lower", "squat", 0, 5, 8)),
        )
        diskDb.close()

        diskDb = open()
        assertEquals("Back Squat", diskDb.exerciseDao().getById("squat")?.name)
        assertEquals("Lower", diskDb.routineDao().getById("lower")?.name)
        assertEquals("squat", diskDb.routineDao().getExercises("lower").single().exerciseId)
        diskDb.close()
        context.deleteDatabase(databaseName)

        db = Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    private fun routineExercise(
        id: String,
        routineId: String,
        exerciseId: String,
        position: Int,
        repMin: Int,
        repMax: Int,
    ) = RoutineExerciseEntity(
        id = id,
        routineId = routineId,
        exerciseId = exerciseId,
        position = position,
        targetSetCount = 3,
        repMin = repMin,
        repMax = repMax,
        targetRirTenths = 15,
        restSeconds = 120,
        loadIncrementGrams = 2_500,
        previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
    )
}
