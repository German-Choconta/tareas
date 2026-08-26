package com.germanchoconta.gymtracker.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.germanchoconta.gymtracker.wear.protocol.WEAR_PROTOCOL_VERSION
import com.germanchoconta.gymtracker.wear.protocol.WearDataPaths
import com.germanchoconta.gymtracker.wear.protocol.WearOperationJournal
import com.germanchoconta.gymtracker.wear.protocol.WearOperationStatus
import com.germanchoconta.gymtracker.wear.protocol.WearProtocolCodec
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperation
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperationKind
import com.germanchoconta.gymtracker.wear.protocol.WearSnapshotRequest
import com.germanchoconta.gymtracker.wear.protocol.WearWorkoutSnapshot
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val ACTIVE_REFRESH_MS = 8_000L

data class WearWorkoutUiState(
    val snapshot: WearWorkoutSnapshot? = null,
    val pendingOperations: List<WearSetOperation> = emptyList(),
    val phoneReachable: Boolean = false,
    val validationMessage: String? = null,
) {
    val current: WearCurrentSetContext? get() = currentSetContext(snapshot, pendingOperations)
    val restTimerEndsAt: Long? get() = projectedRestTimerEndsAt(snapshot, pendingOperations)
    val activeWorkout get() = snapshot?.activeWorkout
    val hasPending: Boolean get() = pendingOperations.isNotEmpty()
    val conflictMessage: String?
        get() = snapshot?.operationResults?.firstOrNull {
            it.status == WearOperationStatus.CONFLICT || it.status == WearOperationStatus.REJECTED
        }?.let {
            if (it.status == WearOperationStatus.CONFLICT) {
                "Phone value changed; refreshed"
            } else {
                "A saved change could not be applied"
            }
        }
}

class WearWorkoutViewModel(application: Application) : AndroidViewModel(application), DataClient.OnDataChangedListener {
    private val store = WearSyncStore(application.applicationContext)
    private val dataClient = Wearable.getDataClient(application.applicationContext)
    private val capabilityClient = Wearable.getCapabilityClient(application.applicationContext)
    private val _uiState = MutableStateFlow(WearWorkoutUiState())
    val uiState: StateFlow<WearWorkoutUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var listening = false

    init {
        viewModelScope.launch {
            store.state.collect { stored ->
                _uiState.update {
                    it.copy(snapshot = stored.snapshot, pendingOperations = stored.pendingOperations)
                }
            }
        }
    }

    fun startListening() {
        if (listening) return
        listening = true
        dataClient.addListener(this)
        refreshJob = viewModelScope.launch {
            while (listening) {
                refreshNow()
                delay(ACTIVE_REFRESH_MS)
            }
        }
    }

    fun stopListening() {
        listening = false
        refreshJob?.cancel()
        refreshJob = null
        dataClient.removeListener(this)
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    fun adjustLoad(direction: Int) {
        if (direction == 0) return
        enqueueForCurrent { stored, sequence, current ->
            val increment = (current.exercise.loadIncrementGrams ?: 2_500L).coerceAtLeast(1L)
            val old = current.set.loadGrams
            val desired = if (direction > 0) old + increment else (old - increment).coerceAtLeast(0L)
            if (desired == old) null else operation(
                stored = stored,
                sequence = sequence,
                current = current,
                kind = WearSetOperationKind.EDIT_LOAD,
                expected = old,
                desired = desired,
            )
        }
    }

    fun adjustReps(direction: Int) {
        if (direction == 0) return
        enqueueForCurrent { stored, sequence, current ->
            val old = current.set.reps
            val desired = (old + direction).coerceIn(0, 1_000)
            if (desired == old) null else operation(
                stored = stored,
                sequence = sequence,
                current = current,
                kind = WearSetOperationKind.EDIT_REPS,
                expected = old.toLong(),
                desired = desired.toLong(),
            )
        }
    }

    fun adjustRir(direction: Int) {
        if (direction == 0) return
        enqueueForCurrent { stored, sequence, current ->
            val old = current.set.rirTenths
            val starting = old ?: current.exercise.targetRirTenths ?: 0
            val desired = (starting + direction * 5).coerceIn(0, 100)
            if (old == desired) null else operation(
                stored = stored,
                sequence = sequence,
                current = current,
                kind = WearSetOperationKind.EDIT_RIR,
                expected = old?.toLong(),
                desired = desired.toLong(),
            )
        }
    }

    fun clearRir() {
        enqueueForCurrent { stored, sequence, current ->
            val old = current.set.rirTenths ?: return@enqueueForCurrent null
            operation(
                stored = stored,
                sequence = sequence,
                current = current,
                kind = WearSetOperationKind.EDIT_RIR,
                expected = old.toLong(),
                desired = null,
            )
        }
    }

    fun completeSet() {
        enqueueForCurrent { stored, sequence, current ->
            if (current.set.reps <= 0) {
                _uiState.update { it.copy(validationMessage = "Add at least 1 rep") }
                return@enqueueForCurrent null
            }
            operation(
                stored = stored,
                sequence = sequence,
                current = current,
                kind = WearSetOperationKind.COMPLETE,
                expected = current.set.completedAt,
                desired = System.currentTimeMillis(),
            )
        }
    }

    fun clearValidationMessage() {
        _uiState.update { it.copy(validationMessage = null) }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val snapshots = dataEvents
            .filter { event ->
                event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearDataPaths.SNAPSHOT
            }
            .mapNotNull { event ->
                DataMapItem.fromDataItem(event.dataItem)
                    .dataMap
                    .getString(WearDataPaths.PAYLOAD_KEY)
            }
            .mapNotNull { raw -> runCatching { WearProtocolCodec.decodeSnapshot(raw) }.getOrNull() }
            .filter { it.protocolVersion == WEAR_PROTOCOL_VERSION }
        if (snapshots.isEmpty()) return
        viewModelScope.launch {
            snapshots.forEach { snapshot -> store.applySnapshot(snapshot) }
        }
    }

    override fun onCleared() {
        if (listening) dataClient.removeListener(this)
        super.onCleared()
    }

    private fun enqueueForCurrent(
        builder: (StoredWearSyncState, Long, WearCurrentSetContext) -> WearSetOperation?,
    ) {
        viewModelScope.launch {
            val created = store.enqueue { stored, sequence ->
                val current = currentSetContext(stored.snapshot, stored.pendingOperations)
                    ?: return@enqueue null
                builder(stored, sequence, current)
            }
            if (created != null) {
                _uiState.update { it.copy(validationMessage = null) }
                publishPending()
            }
        }
    }

    private fun operation(
        stored: StoredWearSyncState,
        sequence: Long,
        current: WearCurrentSetContext,
        kind: WearSetOperationKind,
        expected: Long?,
        desired: Long?,
    ): WearSetOperation? {
        val workoutId = stored.snapshot?.activeWorkout?.id ?: return null
        return WearSetOperation(
            operationId = UUID.randomUUID().toString(),
            sequence = sequence,
            workoutId = workoutId,
            setId = current.set.id,
            kind = kind,
            expectedValue = expected,
            desiredValue = desired,
        )
    }

    private suspend fun refreshNow() {
        updateReachability()
        runCatching {
            val request = PutDataMapRequest.create(WearDataPaths.REQUEST).apply {
                dataMap.putString(
                    WearDataPaths.PAYLOAD_KEY,
                    WearProtocolCodec.encodeRequest(WearSnapshotRequest(requestNonce = UUID.randomUUID().toString())),
                )
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }
        publishPending()
    }

    private suspend fun publishPending() {
        val pending = store.readState().pendingOperations
        if (pending.isEmpty()) return
        runCatching {
            val journal = WearOperationJournal(
                deliveryNonce = UUID.randomUUID().toString(),
                operations = pending,
            )
            val request = PutDataMapRequest.create(WearDataPaths.JOURNAL).apply {
                dataMap.putString(WearDataPaths.PAYLOAD_KEY, WearProtocolCodec.encodeJournal(journal))
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    private suspend fun updateReachability() {
        val reachable = runCatching {
            capabilityClient
                .getCapability(WearDataPaths.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .isNotEmpty()
        }.getOrDefault(false)
        _uiState.update { it.copy(phoneReachable = reachable) }
    }
}
