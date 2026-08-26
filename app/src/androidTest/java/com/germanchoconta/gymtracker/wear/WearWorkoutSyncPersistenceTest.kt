package com.germanchoconta.gymtracker.wear

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.wear.protocol.WearOperationStatus
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperation
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperationKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearWorkoutSyncPersistenceTest {
    private lateinit var db: GymTrackerDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var snapshotBuilder: WearWorkoutSnapshotBuilder
    private lateinit var applier: WearSetOperationApplier

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = WorkoutRepository(db.workoutDao(), db.routineDao(), db.exerciseDao())
        snapshotBuilder = WearWorkoutSnapshotBuilder(
            repository,
            ExerciseRepository(db.exerciseDao(), db.muscleDao()),
        )
        applier = WearSetOperationApplier(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun duplicateReplayIsIdempotentAndSameFieldConflictDoesNotOverwritePhone() = runTest {
        val workoutId = seedActiveWorkout(startedAt = 10_000L)
        val set = firstSet(workoutId)
        repository.updateSetLoad(set.id, 100_000L)
        repository.updateSetReps(set.id, 8)

        val first = WearSetOperation(
            operationId = "synthetic-op-reps",
            sequence = 1,
            workoutId = workoutId,
            setId = set.id,
            kind = WearSetOperationKind.EDIT_REPS,
            expectedValue = 8,
            desiredValue = 9,
        )
        assertEquals(WearOperationStatus.APPLIED, applier.apply(first).status)
        assertEquals(WearOperationStatus.APPLIED, applier.apply(first).status)
        assertEquals(9, db.workoutDao().getSet(set.id)?.reps)

        repository.updateSetReps(set.id, 10)
        val stale = first.copy(
            operationId = "synthetic-op-stale",
            sequence = 2,
            expectedValue = 9,
            desiredValue = 11,
        )
        assertEquals(WearOperationStatus.CONFLICT, applier.apply(stale).status)
        assertEquals(10, db.workoutDao().getSet(set.id)?.reps)
    }

    @Test
    fun differentFieldPhoneEditIsPreservedWhileWatchEditApplies() = runTest {
        val workoutId = seedActiveWorkout(startedAt = 10_000L)
        val set = firstSet(workoutId)
        repository.updateSetLoad(set.id, 100_000L)
        repository.updateSetReps(set.id, 8)

        repository.updateSetLoad(set.id, 110_000L)
        val repsOnly = WearSetOperation(
            operationId = "synthetic-op-independent",
            sequence = 1,
            workoutId = workoutId,
            setId = set.id,
            kind = WearSetOperationKind.EDIT_REPS,
            expectedValue = 8,
            desiredValue = 9,
        )
        assertEquals(WearOperationStatus.APPLIED, applier.apply(repsOnly).status)
        val persisted = db.workoutDao().getSet(set.id)
        assertEquals(110_000L, persisted?.loadGrams)
        assertEquals(9, persisted?.reps)
    }

    @Test
    fun completionReplayKeepsOriginalTimestampAndDoesNotRestartStoppedRestTimer() = runTest {
        val workoutId = seedActiveWorkout(startedAt = 10_000L)
        val set = firstSet(workoutId)
        repository.updateSetReps(set.id, 8)
        val completion = WearSetOperation(
            operationId = "synthetic-op-complete",
            sequence = 1,
            workoutId = workoutId,
            setId = set.id,
            kind = WearSetOperationKind.COMPLETE,
            expectedValue = null,
            desiredValue = 20_000L,
        )

        assertEquals(WearOperationStatus.APPLIED, applier.apply(completion).status)
        assertEquals(20_000L, db.workoutDao().getSet(set.id)?.completedAt)
        assertEquals(95_000L, repository.getWorkout(workoutId)?.restTimerEndsAt)

        repository.setRestTimer(workoutId, null, null)
        assertEquals(WearOperationStatus.APPLIED, applier.apply(completion).status)
        assertEquals(20_000L, db.workoutDao().getSet(set.id)?.completedAt)
        assertNull(repository.getWorkout(workoutId)?.restTimerEndsAt)
    }

    @Test
    fun snapshotKeepsPreviousAndTargetSemanticsAndNoActiveStateAfterFinish() = runTest {
        val firstWorkoutId = seedActiveWorkout(startedAt = 10_000L)
        val firstSet = firstSet(firstWorkoutId)
        repository.updateSetLoad(firstSet.id, 80_000L)
        repository.updateSetReps(firstSet.id, 8)
        repository.updateSetRir(firstSet.id, 15)
        repository.setCompleted(firstSet.id, 11_000L)
        repository.finishWorkout(firstWorkoutId, 12_000L)

        val secondWorkout = repository.startFromRoutine(SYNTHETIC_ROUTINE_ID, 20_000L)!!
        val snapshot = snapshotBuilder.build()
        val exercise = snapshot.activeWorkout!!.exercises.single()
        val currentSet = exercise.sets.first()
        assertEquals(6, exercise.repMin)
        assertEquals(10, exercise.repMax)
        assertEquals(15, exercise.targetRirTenths)
        assertEquals(75, exercise.restSeconds)
        assertEquals(80_000L, currentSet.previous?.loadGrams)
        assertEquals(8, currentSet.previous?.reps)
        assertEquals(15, currentSet.previous?.rirTenths)

        repository.finishWorkout(secondWorkout.id, 21_000L)
        assertNull(snapshotBuilder.build().activeWorkout)
    }

    private suspend fun seedActiveWorkout(startedAt: Long): String {
        if (db.exerciseDao().getById(SYNTHETIC_EXERCISE_ID) == null) {
            db.exerciseDao().upsert(ExerciseEntity(SYNTHETIC_EXERCISE_ID, "Synthetic Press"))
            db.routineDao().upsert(RoutineEntity(SYNTHETIC_ROUTINE_ID, "Synthetic Routine", 0))
            db.routineDao().upsertExercise(
                RoutineExerciseEntity(
                    id = "synthetic-template",
                    routineId = SYNTHETIC_ROUTINE_ID,
                    exerciseId = SYNTHETIC_EXERCISE_ID,
                    position = 0,
                    targetSetCount = 2,
                    repMin = 6,
                    repMax = 10,
                    targetRirTenths = 15,
                    restSeconds = 75,
                    loadIncrementGrams = 2_500L,
                    previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
                ),
            )
        }
        return requireNotNull(repository.startFromRoutine(SYNTHETIC_ROUTINE_ID, startedAt)).id
    }

    private suspend fun firstSet(workoutId: String) =
        repository.getSets(repository.getExercises(workoutId).single().id).first()

    private companion object {
        const val SYNTHETIC_EXERCISE_ID = "synthetic-exercise"
        const val SYNTHETIC_ROUTINE_ID = "synthetic-routine"
    }
}
