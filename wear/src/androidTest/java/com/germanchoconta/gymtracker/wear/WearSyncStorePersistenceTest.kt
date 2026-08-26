package com.germanchoconta.gymtracker.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.wear.protocol.WearOperationResult
import com.germanchoconta.gymtracker.wear.protocol.WearOperationStatus
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperation
import com.germanchoconta.gymtracker.wear.protocol.WearSetOperationKind
import com.germanchoconta.gymtracker.wear.protocol.WearWorkoutSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearSyncStorePersistenceTest {
    @Test
    fun pendingOfflineOperationSurvivesStoreRecreationUntilTerminalAck() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val operationId = "synthetic-watch-restart-operation"
        val initialStore = WearSyncStore(context)
        initialStore.applySnapshot(WearWorkoutSnapshot(snapshotNonce = "synthetic-before-restart"))
        initialStore.enqueue { _, sequence ->
            WearSetOperation(
                operationId = operationId,
                sequence = sequence,
                workoutId = "synthetic-workout",
                setId = "synthetic-set",
                kind = WearSetOperationKind.EDIT_REPS,
                expectedValue = 8,
                desiredValue = 9,
            )
        }

        val recreatedStore = WearSyncStore(context)
        val restored = recreatedStore.readState()
        assertTrue(restored.pendingOperations.any { it.operationId == operationId })

        recreatedStore.applySnapshot(
            WearWorkoutSnapshot(
                snapshotNonce = "synthetic-after-restart",
                operationResults = listOf(
                    WearOperationResult(operationId, WearOperationStatus.APPLIED),
                ),
            ),
        )
        val acknowledged = recreatedStore.readState()
        assertTrue(acknowledged.pendingOperations.none { it.operationId == operationId })
        assertEquals("synthetic-after-restart", acknowledged.snapshot?.snapshotNonce)
    }
}
