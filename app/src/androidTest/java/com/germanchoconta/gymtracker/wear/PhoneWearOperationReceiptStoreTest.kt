package com.germanchoconta.gymtracker.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.wear.protocol.WearOperationResult
import com.germanchoconta.gymtracker.wear.protocol.WearOperationStatus
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneWearOperationReceiptStoreTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.cacheDir, "synthetic-wear-receipt-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun receiptSurvivesStoreRecreationAndPreventsLostAckFromBecomingConflict() {
        val applied = WearOperationResult(
            operationId = "synthetic-operation-a",
            status = WearOperationStatus.APPLIED,
        )
        PhoneWearOperationReceiptStore(directory).put("synthetic-workout-a", applied)

        val recreated = PhoneWearOperationReceiptStore(directory)
        assertEquals(applied, recreated.get("synthetic-operation-a"))
    }

    @Test
    fun receiptsAreScopedToCurrentWorkoutAndContainNoWorkoutValues() {
        val store = PhoneWearOperationReceiptStore(directory)
        store.put(
            "synthetic-workout-a",
            WearOperationResult(
                operationId = "synthetic-operation-a",
                status = WearOperationStatus.CONFLICT,
                reason = "reps_changed",
            ),
        )

        store.retainOnly("synthetic-workout-b")
        assertNull(store.get("synthetic-operation-a"))
    }
}
