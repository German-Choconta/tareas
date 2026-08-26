package com.germanchoconta.gymtracker.wear.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val WEAR_PROTOCOL_VERSION = 1

object WearDataPaths {
    const val REQUEST = "/gymtracker/workout/request"
    const val JOURNAL = "/gymtracker/workout/journal"
    const val SNAPSHOT = "/gymtracker/workout/snapshot"
    const val PAYLOAD_KEY = "payload"
    const val PHONE_CAPABILITY = "gymtracker_phone_workout_sync"
}

@Serializable
data class WearSnapshotRequest(
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    val requestNonce: String,
)

@Serializable
enum class WearSetOperationKind {
    EDIT_LOAD,
    EDIT_REPS,
    EDIT_RIR,
    COMPLETE,
}

@Serializable
data class WearSetOperation(
    val operationId: String,
    val sequence: Long,
    val workoutId: String,
    val setId: String,
    val kind: WearSetOperationKind,
    /**
     * Scalar value before this operation. Units depend on [kind]: grams, reps,
     * RIR tenths, or completedAt epoch millis. Null is meaningful for RIR and
     * completion.
     */
    val expectedValue: Long?,
    /** Scalar value after this operation. Null is valid only when clearing RIR. */
    val desiredValue: Long?,
)

@Serializable
data class WearOperationJournal(
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    val deliveryNonce: String,
    val operations: List<WearSetOperation>,
)

@Serializable
enum class WearOperationStatus {
    APPLIED,
    CONFLICT,
    REJECTED,
}

@Serializable
data class WearOperationResult(
    val operationId: String,
    val status: WearOperationStatus,
    /** Stable machine-readable reason for CONFLICT/REJECTED; never private data. */
    val reason: String? = null,
)

@Serializable
data class WearPreviousSetSnapshot(
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
    val type: String,
)

@Serializable
data class WearSetSnapshot(
    val id: String,
    val position: Int,
    val type: String,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
    val completedAt: Long?,
    val previous: WearPreviousSetSnapshot? = null,
)

@Serializable
data class WearExerciseSnapshot(
    val id: String,
    val exerciseId: String,
    val name: String,
    val position: Int,
    val targetSetCount: Int?,
    val repMin: Int?,
    val repMax: Int?,
    val targetRirTenths: Int?,
    val restSeconds: Int?,
    val loadIncrementGrams: Long?,
    val previousReferenceMode: String,
    val sets: List<WearSetSnapshot>,
)

@Serializable
data class WearActiveWorkoutSnapshot(
    val id: String,
    val title: String,
    val startedAt: Long,
    val restTimerEndsAt: Long?,
    val restTimerWorkoutExerciseId: String?,
    val exercises: List<WearExerciseSnapshot>,
)

@Serializable
data class WearWorkoutSnapshot(
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    /** Changes on every phone publication so DataClient emits a fresh state. */
    val snapshotNonce: String,
    val activeWorkout: WearActiveWorkoutSnapshot? = null,
    val operationResults: List<WearOperationResult> = emptyList(),
)

object WearProtocolCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    fun encodeRequest(value: WearSnapshotRequest): String = json.encodeToString(value)
    fun decodeRequest(value: String): WearSnapshotRequest = json.decodeFromString(value)

    fun encodeJournal(value: WearOperationJournal): String = json.encodeToString(value)
    fun decodeJournal(value: String): WearOperationJournal = json.decodeFromString(value)

    fun encodeSnapshot(value: WearWorkoutSnapshot): String = json.encodeToString(value)
    fun decodeSnapshot(value: String): WearWorkoutSnapshot = json.decodeFromString(value)
}

fun WearWorkoutSnapshot.project(operations: List<WearSetOperation>): WearWorkoutSnapshot {
    val active = activeWorkout ?: return this
    if (operations.isEmpty()) return this
    val ordered = operations.sortedWith(compareBy<WearSetOperation> { it.sequence }.thenBy { it.operationId })
    val bySet = ordered.groupBy(WearSetOperation::setId)
    return copy(
        activeWorkout = active.copy(
            exercises = active.exercises.map { exercise ->
                exercise.copy(
                    sets = exercise.sets.map { set ->
                        bySet[set.id].orEmpty().fold(set, ::applyProjectedOperation)
                    },
                )
            },
        ),
    )
}

private fun applyProjectedOperation(
    set: WearSetSnapshot,
    operation: WearSetOperation,
): WearSetSnapshot = when (operation.kind) {
    WearSetOperationKind.EDIT_LOAD -> operation.desiredValue?.let { set.copy(loadGrams = it) } ?: set
    WearSetOperationKind.EDIT_REPS -> operation.desiredValue?.toInt()?.let { set.copy(reps = it) } ?: set
    WearSetOperationKind.EDIT_RIR -> set.copy(rirTenths = operation.desiredValue?.toInt())
    WearSetOperationKind.COMPLETE -> operation.desiredValue?.let { set.copy(completedAt = it) } ?: set
}
