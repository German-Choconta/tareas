package com.germanchoconta.gymtracker.wear

import com.germanchoconta.gymtracker.wear.protocol.WearExerciseSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperation
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperationKind
import com.germanchoconta.gymtracker.wear.protocol.WearSetSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearWorkoutSnapshot
import com.germanchoconta.gymtracker.wear.protocol.project

data class WearCurrentSetContext(
    val exercise: WearExerciseSnapshot,
    val set: WearSetSnapshot,
)

fun currentSetContext(
    snapshot: WearWorkoutSnapshot?,
    pendingOperations: List<WearSetOperation>,
): WearCurrentSetContext? {
    val projected = snapshot?.project(pendingOperations)?.activeWorkout ?: return null
    projected.exercises
        .sortedWith(compareBy(WearExerciseSnapshot::position, WearExerciseSnapshot::id))
        .forEach { exercise ->
            exercise.sets
                .sortedWith(compareBy(WearSetSnapshot::position, WearSetSnapshot::id))
                .firstOrNull { it.completedAt == null }
                ?.let { set -> return WearCurrentSetContext(exercise, set) }
        }
    return null
}

fun projectedRestTimerEndsAt(
    snapshot: WearWorkoutSnapshot?,
    pendingOperations: List<WearSetOperation>,
): Long? {
    val active = snapshot?.activeWorkout ?: return null
    val latestCompletion = pendingOperations
        .asSequence()
        .filter { it.workoutId == active.id && it.kind == WearSetOperationKind.COMPLETE }
        .maxWithOrNull(compareBy<WearSetOperation>({ it.sequence }, { it.operationId }))
    if (latestCompletion != null) {
        val completedAt = latestCompletion.desiredValue ?: return active.restTimerEndsAt
        val owner = active.exercises.firstOrNull { exercise ->
            exercise.sets.any { it.id == latestCompletion.setId }
        }
        val restSeconds = owner?.restSeconds ?: 0
        if (restSeconds > 0) return completedAt + restSeconds * 1_000L
    }
    return active.restTimerEndsAt
}
