package com.germanchoconta.gymtracker.wear

import androidx.room3.withWriteTransaction
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.WorkoutDao
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity
import com.germanchoconta.gymtracker.wear.protocol.WearActiveWorkoutSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearExerciseSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearOperationResult
import com.germanchoconta.gymtracker.wear.protocol.WearOperationStatus
import com.germanchoconta.gymtracker.wear.protocol.WearPreviousSetSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperation
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperationKind
import com.germanchoconta.gymtracker.wear.protocol.WearSetSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearWorkoutSnapshot
import java.util.UUID

class WearWorkoutSnapshotBuilder(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    suspend fun build(operationResults: List<WearOperationResult> = emptyList()): WearWorkoutSnapshot {
        val active = workoutRepository.getActiveWorkout()
            ?: return WearWorkoutSnapshot(
                snapshotNonce = UUID.randomUUID().toString(),
                activeWorkout = null,
                operationResults = operationResults,
            )
        val aggregate = workoutRepository.getAggregate(active.id)
            ?: return WearWorkoutSnapshot(
                snapshotNonce = UUID.randomUUID().toString(),
                activeWorkout = null,
                operationResults = operationResults,
            )
        val namesById = exerciseRepository
            .getByIds(aggregate.exercises.map { it.exercise.exerciseId })
            .associateBy { it.id }
        val exerciseSnapshots = aggregate.exercises
            .sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))
            .map { item ->
                val exercise = item.exercise
                val referenceMode = exercise.previousReferenceMode ?: PreviousReferenceModes.ANY_WORKOUT
                val previousByPosition = workoutRepository.previousCompletedSets(
                    exerciseId = exercise.exerciseId,
                    referenceMode = referenceMode,
                    routineId = active.routineId,
                    beforeStartedAt = active.startedAt,
                ).associateBy(WorkoutSetEntity::position)
                WearExerciseSnapshot(
                    id = exercise.id,
                    exerciseId = exercise.exerciseId,
                    name = namesById[exercise.exerciseId]?.name ?: "Archived exercise",
                    position = exercise.position,
                    targetSetCount = exercise.targetSetCount,
                    repMin = exercise.repMin,
                    repMax = exercise.repMax,
                    targetRirTenths = exercise.targetRirTenths,
                    restSeconds = exercise.restSeconds,
                    loadIncrementGrams = exercise.loadIncrementGrams,
                    previousReferenceMode = referenceMode,
                    sets = item.sets
                        .sortedWith(compareBy(WorkoutSetEntity::position, WorkoutSetEntity::id))
                        .map { set ->
                            WearSetSnapshot(
                                id = set.id,
                                position = set.position,
                                type = set.type,
                                loadGrams = set.loadGrams,
                                reps = set.reps,
                                rirTenths = set.rirTenths,
                                completedAt = set.completedAt,
                                previous = previousByPosition[set.position]?.let { previous ->
                                    WearPreviousSetSnapshot(
                                        loadGrams = previous.loadGrams,
                                        reps = previous.reps,
                                        rirTenths = previous.rirTenths,
                                        type = previous.type,
                                    )
                                },
                            )
                        },
                )
            }
        return WearWorkoutSnapshot(
            snapshotNonce = UUID.randomUUID().toString(),
            activeWorkout = WearActiveWorkoutSnapshot(
                id = active.id,
                title = active.title,
                startedAt = active.startedAt,
                restTimerEndsAt = active.restTimerEndsAt,
                restTimerWorkoutExerciseId = active.restTimerWorkoutExerciseId,
                exercises = exerciseSnapshots,
            ),
            operationResults = operationResults,
        )
    }
}

class WearSetOperationApplier(
    private val database: GymTrackerDatabase,
    private val workoutDao: WorkoutDao = database.workoutDao(),
) {
    suspend fun apply(operation: WearSetOperation): WearOperationResult = database.withWriteTransaction {
        val set = workoutDao.getSet(operation.setId)
            ?: return@withWriteTransaction rejected(operation, "set_missing")
        val workoutExercise = workoutDao.getWorkoutExercise(set.workoutExerciseId)
            ?: return@withWriteTransaction rejected(operation, "exercise_missing")
        val workout = workoutDao.getWorkout(workoutExercise.workoutId)
            ?: return@withWriteTransaction rejected(operation, "workout_missing")
        if (workout.id != operation.workoutId || workout.finishedAt != null) {
            return@withWriteTransaction rejected(operation, "workout_not_active")
        }

        when (operation.kind) {
            WearSetOperationKind.EDIT_LOAD -> {
                val desired = operation.desiredValue
                    ?: return@withWriteTransaction rejected(operation, "load_missing")
                if (operation.expectedValue == null || desired < 0L) {
                    return@withWriteTransaction rejected(operation, "load_invalid")
                }
                if (set.loadGrams == desired) return@withWriteTransaction applied(operation)
                if (set.loadGrams != operation.expectedValue) {
                    return@withWriteTransaction conflict(operation, "load_changed")
                }
                if (workoutDao.updateSetLoad(set.id, desired) == 0) {
                    return@withWriteTransaction rejected(operation, "workout_not_active")
                }
                applied(operation)
            }

            WearSetOperationKind.EDIT_REPS -> {
                val desired = operation.desiredValue
                    ?: return@withWriteTransaction rejected(operation, "reps_missing")
                if (operation.expectedValue == null || desired !in 0L..1_000L) {
                    return@withWriteTransaction rejected(operation, "reps_invalid")
                }
                if (set.reps.toLong() == desired) return@withWriteTransaction applied(operation)
                if (set.reps.toLong() != operation.expectedValue) {
                    return@withWriteTransaction conflict(operation, "reps_changed")
                }
                if (workoutDao.updateSetReps(set.id, desired.toInt()) == 0) {
                    return@withWriteTransaction rejected(operation, "workout_not_active")
                }
                applied(operation)
            }

            WearSetOperationKind.EDIT_RIR -> {
                val desired = operation.desiredValue
                if (desired != null && desired !in 0L..100L) {
                    return@withWriteTransaction rejected(operation, "rir_invalid")
                }
                val current = set.rirTenths?.toLong()
                if (current == desired) return@withWriteTransaction applied(operation)
                if (current != operation.expectedValue) {
                    return@withWriteTransaction conflict(operation, "rir_changed")
                }
                if (workoutDao.updateSetRir(set.id, desired?.toInt()) == 0) {
                    return@withWriteTransaction rejected(operation, "workout_not_active")
                }
                applied(operation)
            }

            WearSetOperationKind.COMPLETE -> {
                val desired = operation.desiredValue
                    ?: return@withWriteTransaction rejected(operation, "completion_missing")
                if (desired < workout.startedAt || set.reps <= 0) {
                    return@withWriteTransaction rejected(operation, "completion_invalid")
                }
                if (set.completedAt == desired) return@withWriteTransaction applied(operation)
                if (set.completedAt != operation.expectedValue) {
                    return@withWriteTransaction conflict(operation, "completion_changed")
                }
                if (workoutDao.updateSetCompletedAt(set.id, desired) == 0) {
                    return@withWriteTransaction rejected(operation, "workout_not_active")
                }
                val restSeconds = workoutExercise.restSeconds ?: 0
                if (restSeconds > 0) {
                    workoutDao.setRestTimer(
                        workoutId = workout.id,
                        workoutExerciseId = workoutExercise.id,
                        endsAt = desired + restSeconds * 1_000L,
                    )
                }
                applied(operation)
            }
        }
    }

    private fun applied(operation: WearSetOperation) =
        WearOperationResult(operation.operationId, WearOperationStatus.APPLIED)

    private fun conflict(operation: WearSetOperation, reason: String) =
        WearOperationResult(operation.operationId, WearOperationStatus.CONFLICT, reason)

    private fun rejected(operation: WearSetOperation, reason: String) =
        WearOperationResult(operation.operationId, WearOperationStatus.REJECTED, reason)
}
