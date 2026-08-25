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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutLoggerPersistenceTest {
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
    fun startFromRoutinePersistsImmediatelyAndSnapshotsTargets() = runTest {
        val exercise = ExerciseEntity(id = "synthetic-exercise-press", name = "Synthetic Press")
        val routine = RoutineEntity(id = "synthetic-routine-a", name = "Synthetic A", position = 0)
        val template = RoutineExerciseEntity(
            id = "synthetic-template-press",
            routineId = routine.id,
            exerciseId = exercise.id,
            position = 0,
            targetSetCount = 2,
            repMin = 6,
            repMax = 10,
            targetRirTenths = 15,
            restSeconds = 75,
            loadIncrementGrams = 1_250,
            previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE,
        )
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)
        db.routineDao().upsertExercise(template)

        val started = repository.startFromRoutine(routine.id, 10_000L)
        assertNotNull(started)
        assertEquals(started?.id, repository.getActiveWorkout()?.id)

        val workoutExercise = repository.getExercises(requireNotNull(started).id).single()
        assertEquals(template.id, workoutExercise.routineExerciseId)
        assertEquals(2, workoutExercise.targetSetCount)
        assertEquals(6, workoutExercise.repMin)
        assertEquals(10, workoutExercise.repMax)
        assertEquals(15, workoutExercise.targetRirTenths)
        assertEquals(75, workoutExercise.restSeconds)
        assertEquals(1_250L, workoutExercise.loadIncrementGrams)
        assertEquals(PreviousReferenceModes.SAME_ROUTINE, workoutExercise.previousReferenceMode)
        assertEquals(listOf(0, 1), repository.getSets(workoutExercise.id).map { it.position })

        db.routineDao().upsertExercise(
            template.copy(
                targetSetCount = 5,
                repMin = 12,
                repMax = 15,
                targetRirTenths = 30,
                restSeconds = 180,
                loadIncrementGrams = 5_000,
                previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
            ),
        )
        val preserved = repository.getExercises(started.id).single()
        assertEquals(2, preserved.targetSetCount)
        assertEquals(6, preserved.repMin)
        assertEquals(10, preserved.repMax)
        assertEquals(15, preserved.targetRirTenths)
        assertEquals(75, preserved.restSeconds)
        assertEquals(1_250L, preserved.loadIncrementGrams)
        assertEquals(PreviousReferenceModes.SAME_ROUTINE, preserved.previousReferenceMode)
        assertEquals(2, repository.getSets(preserved.id).size)
    }

    @Test
    fun previousModesUseOnlyFinishedComparableWorkoutsAndAllowDifferentSetCounts() = runTest {
        val exercise = ExerciseEntity(id = "synthetic-exercise-row", name = "Synthetic Row")
        val routineA = RoutineEntity(id = "synthetic-routine-a", name = "A", position = 0)
        val routineB = RoutineEntity(id = "synthetic-routine-b", name = "B", position = 1)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routineA)
        db.routineDao().upsert(routineB)

        fun insertFinished(
            workoutId: String,
            workoutExerciseId: String,
            routineId: String,
            startedAt: Long,
            setCount: Int,
        ) {
            // populated below inside the suspend test body
        }

        val oldA = WorkoutEntity("synthetic-old-a", routineA.id, "Old A", 1_000L, 1_500L)
        val newB = WorkoutEntity("synthetic-new-b", routineB.id, "New B", 2_000L, 2_500L)
        db.workoutDao().upsert(oldA)
        db.workoutDao().upsert(newB)
        val oldAExercise = WorkoutExerciseEntity("synthetic-we-old-a", oldA.id, exercise.id, position = 0)
        val newBExercise = WorkoutExerciseEntity("synthetic-we-new-b", newB.id, exercise.id, position = 0)
        db.workoutDao().upsertExercise(oldAExercise)
        db.workoutDao().upsertExercise(newBExercise)
        repeat(2) { position ->
            db.workoutDao().upsertSet(
                WorkoutSetEntity(
                    id = "synthetic-old-a-set-$position",
                    workoutExerciseId = oldAExercise.id,
                    position = position,
                    type = SetTypes.WORK,
                    loadGrams = 40_000L + position * 1_000L,
                    reps = 10 - position,
                    completedAt = 1_100L + position,
                ),
            )
        }
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "synthetic-new-b-set-0",
                workoutExerciseId = newBExercise.id,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 45_000L,
                reps = 8,
                completedAt = 2_100L,
            ),
        )

        // Current unfinished workout must never become its own PREVIOUS reference.
        val current = WorkoutEntity("synthetic-current", routineA.id, "Current", 3_000L)
        db.workoutDao().upsert(current)
        db.workoutDao().upsertExercise(
            WorkoutExerciseEntity("synthetic-we-current", current.id, exercise.id, position = 0),
        )

        val any = repository.previousCompletedSets(
            exercise.id,
            PreviousReferenceModes.ANY_WORKOUT,
            routineA.id,
            current.startedAt,
        )
        val same = repository.previousCompletedSets(
            exercise.id,
            PreviousReferenceModes.SAME_ROUTINE,
            routineA.id,
            current.startedAt,
        )
        assertEquals(1, any.size)
        assertEquals(45_000L, any.single().loadGrams)
        assertEquals(2, same.size)
        assertEquals(listOf(0, 1), same.map { it.position })
    }

    @Test
    fun todayAutosaveCompleteUncompleteAndRestTimerStayExact() = runTest {
        val started = seedAndStartRoutine(restSeconds = 90)
        val workoutExercise = repository.getExercises(started.id).single()
        val set = repository.getSets(workoutExercise.id).first()

        assertTrue(repository.updateSetLoad(set.id, 42_500L))
        assertTrue(repository.updateSetReps(set.id, 9))
        assertTrue(repository.updateSetRir(set.id, 15))
        assertTrue(repository.updateSetType(set.id, SetTypes.FAILURE))
        assertTrue(repository.setCompleted(set.id, 20_000L))

        val completed = repository.getSets(workoutExercise.id).first()
        assertEquals(42_500L, completed.loadGrams)
        assertEquals(9, completed.reps)
        assertEquals(15, completed.rirTenths)
        assertEquals(SetTypes.FAILURE, completed.type)
        assertEquals(20_000L, completed.completedAt)
        val workoutWithTimer = repository.getWorkout(started.id)
        assertEquals(110_000L, workoutWithTimer?.restTimerEndsAt)
        assertEquals(workoutExercise.id, workoutWithTimer?.restTimerWorkoutExerciseId)

        assertTrue(repository.setCompleted(set.id, null))
        val corrected = repository.getSets(workoutExercise.id).first()
        assertNull(corrected.completedAt)
        assertEquals(42_500L, corrected.loadGrams)
        assertEquals(9, corrected.reps)
        assertEquals(15, corrected.rirTenths)
    }

    @Test
    fun addAndRemoveSetsKeepUniqueCompactPositionsAndProtectCompletedData() = runTest {
        val started = seedAndStartRoutine(targetSetCount = 2)
        val workoutExercise = repository.getExercises(started.id).single()
        val added = requireNotNull(repository.addSet(workoutExercise.id))
        assertEquals(listOf(0, 1, 2), repository.getSets(workoutExercise.id).map { it.position })

        assertTrue(repository.updateSetReps(added.id, 5))
        assertTrue(repository.setCompleted(added.id, 30_000L))
        assertFalse(repository.removeSet(added.id))
        assertTrue(repository.removeSet(added.id, allowCompleted = true))
        assertEquals(listOf(0, 1), repository.getSets(workoutExercise.id).map { it.position })

        val first = repository.getSets(workoutExercise.id).first()
        assertTrue(repository.removeSet(first.id))
        assertEquals(listOf(0), repository.getSets(workoutExercise.id).map { it.position })
    }

    @Test
    fun addAndReplaceExerciseNeverMutateRoutineAndCompletedSetsBlockReplacement() = runTest {
        val started = seedAndStartRoutine()
        val originalTemplate = db.routineDao().getExercises(requireNotNull(started.routineId)).single()
        val second = ExerciseEntity(
            id = "synthetic-exercise-second",
            name = "Synthetic Second",
            defaultRepMin = 10,
            defaultRepMax = 15,
            defaultTargetRirTenths = 20,
            defaultRestSeconds = 60,
            defaultLoadIncrementGrams = 1_000,
        )
        val third = ExerciseEntity(id = "synthetic-exercise-third", name = "Synthetic Third")
        db.exerciseDao().upsert(second)
        db.exerciseDao().upsert(third)

        val added = requireNotNull(repository.addExercise(started.id, second.id))
        assertNull(added.routineExerciseId)
        assertEquals(3, added.targetSetCount)
        assertEquals(10, added.repMin)
        assertEquals(15, added.repMax)
        assertEquals(listOf(originalTemplate), db.routineDao().getExercises(started.routineId!!))

        assertTrue(repository.replaceExercise(added.id, third.id))
        val replaced = requireNotNull(repository.getWorkoutExercise(added.id))
        assertEquals(third.id, replaced.exerciseId)
        assertNull(replaced.routineExerciseId)

        val firstSet = repository.getSets(replaced.id).first()
        assertTrue(repository.updateSetReps(firstSet.id, 7))
        assertTrue(repository.setCompleted(firstSet.id, 40_000L))
        assertFalse(repository.replaceExercise(replaced.id, second.id))
        assertEquals(third.id, repository.getWorkoutExercise(replaced.id)?.exerciseId)
        assertEquals(listOf(originalTemplate), db.routineDao().getExercises(started.routineId!!))
    }

    @Test
    fun notesRecoveryFinishAndHistoricalImmutabilitySurviveRepositoryRecreation() = runTest {
        val started = seedAndStartRoutine(targetSetCount = 1)
        val workoutExercise = repository.getExercises(started.id).single()
        val set = repository.getSets(workoutExercise.id).single()

        repository.updateWorkoutNotes(started.id, "Synthetic session note")
        assertTrue(repository.updateWorkoutExerciseNotes(workoutExercise.id, "Synthetic exercise note"))
        assertTrue(repository.updateSetLoad(set.id, 25_000L))
        assertTrue(repository.updateSetReps(set.id, 12))

        val recreatedRepository = WorkoutRepository(db.workoutDao(), db.routineDao(), db.exerciseDao())
        assertEquals(started.id, recreatedRepository.getActiveWorkout()?.id)
        assertEquals("Synthetic session note", recreatedRepository.getWorkout(started.id)?.notes)
        assertEquals("Synthetic exercise note", recreatedRepository.getWorkoutExercise(workoutExercise.id)?.notes)
        assertEquals(25_000L, recreatedRepository.getSets(workoutExercise.id).single().loadGrams)

        assertTrue(recreatedRepository.setCompleted(set.id, 50_000L))
        assertTrue(recreatedRepository.finishWorkout(started.id, 60_000L))
        assertNull(recreatedRepository.getActiveWorkout())
        assertFalse(recreatedRepository.updateSetLoad(set.id, 99_000L))
        assertFalse(recreatedRepository.updateWorkoutExerciseNotes(workoutExercise.id, "Should not persist"))

        val template = db.routineDao().getExercises(requireNotNull(started.routineId)).single()
        db.routineDao().upsertExercise(template.copy(repMin = 20, repMax = 25, restSeconds = 300))
        val historical = recreatedRepository.getWorkoutExercise(workoutExercise.id)
        assertEquals(8, historical?.repMin)
        assertEquals(12, historical?.repMax)
        assertEquals(90, historical?.restSeconds)
        assertEquals(25_000L, recreatedRepository.getSets(workoutExercise.id).single().loadGrams)
    }

    private suspend fun seedAndStartRoutine(
        targetSetCount: Int = 3,
        restSeconds: Int = 90,
    ): WorkoutEntity {
        val exercise = ExerciseEntity(id = "synthetic-exercise-seed", name = "Synthetic Seed")
        val routine = RoutineEntity(id = "synthetic-routine-seed", name = "Synthetic Routine", position = 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)
        db.routineDao().upsertExercise(
            RoutineExerciseEntity(
                id = "synthetic-template-seed",
                routineId = routine.id,
                exerciseId = exercise.id,
                position = 0,
                targetSetCount = targetSetCount,
                repMin = 8,
                repMax = 12,
                targetRirTenths = 20,
                restSeconds = restSeconds,
                loadIncrementGrams = 2_500,
                previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
            ),
        )
        return requireNotNull(repository.startFromRoutine(routine.id, 10_000L))
    }
}
