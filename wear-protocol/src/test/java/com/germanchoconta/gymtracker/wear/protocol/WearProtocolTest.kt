package com.germanchoconta.gymtracker.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearProtocolTest {
    @Test
    fun journal_roundTripsInSequenceOrderWithoutLosingNullRir() {
        val journal = WearOperationJournal(
            deliveryNonce = "delivery-1",
            operations = listOf(
                WearSetOperation("op-1", 7, "workout-a", "set-a", WearSetOperationKind.EDIT_RIR, 20, null),
                WearSetOperation("op-2", 8, "workout-a", "set-a", WearSetOperationKind.COMPLETE, null, 1_700_000_000_000),
            ),
        )

        assertEquals(journal, WearProtocolCodec.decodeJournal(WearProtocolCodec.encodeJournal(journal)))
    }

    @Test
    fun projection_appliesPendingOperationsDeterministicallyWithoutChangingBase() {
        val baseSet = WearSetSnapshot(
            id = "set-a",
            position = 0,
            type = "WORK",
            loadGrams = 100_000,
            reps = 8,
            rirTenths = 20,
            completedAt = null,
        )
        val base = WearWorkoutSnapshot(
            snapshotNonce = "snapshot-1",
            activeWorkout = WearActiveWorkoutSnapshot(
                id = "workout-a",
                title = "Synthetic workout",
                startedAt = 1_699_999_000_000,
                restTimerEndsAt = null,
                restTimerWorkoutExerciseId = null,
                exercises = listOf(
                    WearExerciseSnapshot(
                        id = "workout-exercise-a",
                        exerciseId = "exercise-a",
                        name = "Synthetic press",
                        position = 0,
                        targetSetCount = 3,
                        repMin = 8,
                        repMax = 12,
                        targetRirTenths = 20,
                        restSeconds = 120,
                        loadIncrementGrams = 2_500,
                        previousReferenceMode = "ANY_WORKOUT",
                        sets = listOf(baseSet),
                    ),
                ),
            ),
        )
        val projected = base.project(
            listOf(
                WearSetOperation("op-2", 2, "workout-a", "set-a", WearSetOperationKind.EDIT_REPS, 9, 10),
                WearSetOperation("op-1", 1, "workout-a", "set-a", WearSetOperationKind.EDIT_REPS, 8, 9),
                WearSetOperation("op-3", 3, "workout-a", "set-a", WearSetOperationKind.EDIT_RIR, 20, null),
            ),
        )

        val projectedSet = projected.activeWorkout!!.exercises.single().sets.single()
        assertEquals(10, projectedSet.reps)
        assertNull(projectedSet.rirTenths)
        assertEquals(8, base.activeWorkout!!.exercises.single().sets.single().reps)
    }

    @Test
    fun pathsAndProtocolVersionAreStable() {
        assertEquals(1, WEAR_PROTOCOL_VERSION)
        assertEquals("/gymtracker/workout/request", WearDataPaths.REQUEST)
        assertEquals("/gymtracker/workout/journal", WearDataPaths.JOURNAL)
        assertEquals("/gymtracker/workout/snapshot", WearDataPaths.SNAPSHOT)
    }
}
