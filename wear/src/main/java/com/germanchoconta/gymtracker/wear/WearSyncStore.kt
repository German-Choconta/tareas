package com.germanchoconta.gymtracker.wear

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.germanchoconta.gymtracker.wear.protocol.WearOperationJournal
import com.germanchoconta.gymtracker.wear.protocol.WearProtocolCodec
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperation
import com.germanchoconta.gymtracker.wear.protocol.WearWorkoutSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.wearWorkoutDataStore by preferencesDataStore(name = "wear_workout_sync")

data class StoredWearSyncState(
    val snapshot: WearWorkoutSnapshot? = null,
    val pendingOperations: List<WearSetOperation> = emptyList(),
    val nextSequence: Long = 1L,
)

class WearSyncStore(private val context: Context) {
    private object Keys {
        val snapshot = stringPreferencesKey("snapshot_json")
        val pending = stringPreferencesKey("pending_journal_json")
        val nextSequence = longPreferencesKey("next_sequence")
    }

    val state: Flow<StoredWearSyncState> = context.wearWorkoutDataStore.data.map(::decodeState)

    suspend fun readState(): StoredWearSyncState = state.first()

    suspend fun enqueue(
        builder: (StoredWearSyncState, Long) -> WearSetOperation?,
    ): WearSetOperation? {
        var created: WearSetOperation? = null
        context.wearWorkoutDataStore.edit { preferences ->
            val current = decodeState(preferences)
            val sequence = current.nextSequence.coerceAtLeast(1L)
            val operation = builder(current, sequence) ?: return@edit
            created = operation
            preferences[Keys.nextSequence] = sequence + 1L
            preferences[Keys.pending] = WearProtocolCodec.encodeJournal(
                WearOperationJournal(
                    deliveryNonce = "local",
                    operations = current.pendingOperations + operation,
                ),
            )
        }
        return created
    }

    suspend fun applySnapshot(snapshot: WearWorkoutSnapshot) {
        context.wearWorkoutDataStore.edit { preferences ->
            val current = decodeState(preferences)
            val terminalIds = snapshot.operationResults.mapTo(hashSetOf()) { it.operationId }
            val remaining = current.pendingOperations.filterNot { it.operationId in terminalIds }
            preferences[Keys.snapshot] = WearProtocolCodec.encodeSnapshot(snapshot)
            preferences[Keys.pending] = WearProtocolCodec.encodeJournal(
                WearOperationJournal(deliveryNonce = "local", operations = remaining),
            )
        }
    }

    private fun decodeState(preferences: Preferences): StoredWearSyncState {
        val snapshot = preferences[Keys.snapshot]?.let { raw ->
            runCatching { WearProtocolCodec.decodeSnapshot(raw) }.getOrNull()
        }
        val pending = preferences[Keys.pending]?.let { raw ->
            runCatching { WearProtocolCodec.decodeJournal(raw).operations }.getOrNull()
        }.orEmpty()
        return StoredWearSyncState(
            snapshot = snapshot,
            pendingOperations = pending,
            nextSequence = preferences[Keys.nextSequence] ?: 1L,
        )
    }
}
