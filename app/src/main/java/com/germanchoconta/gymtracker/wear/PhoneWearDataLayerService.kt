package com.germanchoconta.gymtracker.wear

import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.wear.protocol.WEAR_PROTOCOL_VERSION
import com.germanchoconta.gymtracker.wear.protocol.WearDataPaths
import com.germanchoconta.gymtracker.wear.protocol.WearOperationResult
import com.germanchoconta.gymtracker.wear.protocol.WearOperationStatus
import com.germanchoconta.gymtracker.wear.protocol.WearProtocolCodec
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class PhoneWearDataLayerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val changes = dataEvents
            .filter { event -> event.type == DataEvent.TYPE_CHANGED }
            .mapNotNull { event ->
                val path = event.dataItem.uri.path ?: return@mapNotNull null
                if (path != WearDataPaths.REQUEST && path != WearDataPaths.JOURNAL) return@mapNotNull null
                val payload = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap
                    .getString(WearDataPaths.PAYLOAD_KEY)
                    ?: return@mapNotNull null
                path to payload
            }
        if (changes.isEmpty()) return

        // WearableListenerService delivers callbacks on a background thread.
        // Keep the canonical Room mutation and reply together so process death
        // between them is recovered by idempotent journal replay.
        runBlocking {
            changes.forEach { (path, payload) ->
                runCatching {
                    when (path) {
                        WearDataPaths.REQUEST -> handleRequest(payload)
                        WearDataPaths.JOURNAL -> handleJournal(payload)
                    }
                }
            }
        }
    }

    private suspend fun handleRequest(payload: String) {
        val request = runCatching { WearProtocolCodec.decodeRequest(payload) }.getOrNull() ?: return
        if (request.protocolVersion != WEAR_PROTOCOL_VERSION) return
        publishSnapshot(emptyList())
    }

    private suspend fun handleJournal(payload: String) {
        val journal = runCatching { WearProtocolCodec.decodeJournal(payload) }.getOrNull() ?: return
        val results = if (journal.protocolVersion != WEAR_PROTOCOL_VERSION) {
            journal.operations.map { operation ->
                WearOperationResult(
                    operationId = operation.operationId,
                    status = WearOperationStatus.REJECTED,
                    reason = "protocol_version",
                )
            }
        } else {
            val database = GymTrackerDatabase.build(applicationContext)
            val applier = WearSetOperationApplier(database)
            journal.operations
                .sortedWith(compareBy({ it.sequence }, { it.operationId }))
                .map { operation -> applier.apply(operation) }
        }
        publishSnapshot(results)
    }

    private suspend fun publishSnapshot(results: List<WearOperationResult>) {
        val database = GymTrackerDatabase.build(applicationContext)
        val workoutRepository = WorkoutRepository(
            workoutDao = database.workoutDao(),
            routineDao = database.routineDao(),
            exerciseDao = database.exerciseDao(),
        )
        val exerciseRepository = ExerciseRepository(
            exerciseDao = database.exerciseDao(),
            muscleDao = database.muscleDao(),
        )
        val snapshot = WearWorkoutSnapshotBuilder(workoutRepository, exerciseRepository).build(results)
        val request = PutDataMapRequest.create(WearDataPaths.SNAPSHOT).apply {
            dataMap.putString(WearDataPaths.PAYLOAD_KEY, WearProtocolCodec.encodeSnapshot(snapshot))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request).await()
    }
}
