package com.germanchoconta.gymtracker.wear

import com.germanchoconta.gymtracker.wear.protocol.WearActiveWorkoutSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearExerciseSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperation
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperationKind
import com.germanchoconta.gymtracker.wear.protocol.WearSetSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearWorkoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class WearWorkoutStateTest {
    @Test
    fun pendingCompletionAdvancesCurrentSetAndDerivesOfflineRestEnd() {
        val snapshot = syntheticSnapshot()
        val operations = listOf(
            WearSetOperation(
                operationId = "synthetic-reps",
                sequence = 1,
                workoutId = "workout-a",
                setId = "set-1",
                kind = WearSetOperationKind.EDIT_REPS,
                expectedValue = 8,
                desiredValue = 9,
            ),
            WearSetOperation(
                operationId = "synthetic-complete",
                sequence = 2,
                workoutId = "workout-a",
                setId = "set-1",
                kind = WearSetOperationKind.COMPLETE,
                expectedValue = null,
                desiredValue = 20_000L,
            ),
        )

        assertEquals("set-2", currentSetContext(snapshot, operations)?.set?.id)
        assertEquals(95_000L, projectedRestTimerEndsAt(snapshot, operations))
    }

    @Test
    fun noActiveWorkoutHasNoCurrentSet() {
        val snapshot = WearWorkoutSnapshot(snapshotNonce = "none", activeWorkout = null)
        assertEquals(null, currentSetContext(snapshot, emptyList()))
    }

    private fun syntheticSnapshot() = WearWorkoutSnapshot(
        snapshotNonce = "snapshot-a",
        activeWorkout = WearActiveWorkoutSnapshot(
            id = "workout-a",
            title = "Synthetic Workout",
            startedAt = 10_000L,
            restTimerEndsAt = null,
            restTimerWorkoutExerciseId = null,
            exercises = listOf(
                WearExerciseSnapshot(
                    id = "workout-exercise-a",
                    exerciseId = "exercise-a",
                    name = "Synthetic Press",
                    position = 0,
                    targetSetCount = 2,
                    repMin = 6,
                    repMax = 10,
                    targetRirTenths = 20,
                    restSeconds = 75,
                    loadIncrementGrams = 2_500L,
                    previousReferenceMode = "ANY_WORKOUT",
                    sets = listOf(
                        WearSetSnapshot("set-1", 0, "WORK", 80_000L, 8, 20, null),
                        WearSetSnapshot("set-2", 1, "WORK", 80_000L, 8, 20, null),
                    ),
                ),
            ),
        ),
    )
}
