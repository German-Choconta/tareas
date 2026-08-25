package com.germanchoconta.gymtracker.ui.recovery

import com.germanchoconta.gymtracker.data.health.RawRecoveryRecords
import com.germanchoconta.gymtracker.data.health.RawRestingHeartRate
import com.germanchoconta.gymtracker.data.health.RecoveryAvailability
import com.germanchoconta.gymtracker.data.health.RecoveryHealthSource
import com.germanchoconta.gymtracker.data.health.RecoveryPermission
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryContextViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate.of(2026, 8, 25)
    private val utc = ZoneId.of("UTC")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unavailableDoesNotReadPermissionsOrHealthData() = runTest(dispatcher) {
        val source = FakeRecoverySource(availabilityValue = RecoveryAvailability.UNAVAILABLE)
        val viewModel = viewModel(source)

        advanceUntilIdle()

        assertEquals(RecoveryAvailability.UNAVAILABLE, viewModel.uiState.value.availability)
        assertEquals(0, source.permissionReads)
        assertEquals(0, source.dataReads)
    }

    @Test
    fun deniedPermissionsRemainOptionalAndDoNotReadData() = runTest(dispatcher) {
        val source = FakeRecoverySource(granted = emptySet())
        val viewModel = viewModel(source)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasAnyPermission)
        assertFalse(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.context)
        assertEquals(0, source.dataReads)
    }

    @Test
    fun partialPermissionReadsOnlyWithThatGrantAndCanReturnEmpty() = runTest(dispatcher) {
        val source = FakeRecoverySource(granted = setOf(RecoveryPermission.SLEEP))
        val viewModel = viewModel(source)

        advanceUntilIdle()

        assertEquals(setOf(RecoveryPermission.SLEEP), viewModel.uiState.value.grantedPermissions)
        assertFalse(viewModel.uiState.value.allPermissionsGranted)
        assertTrue(viewModel.uiState.value.context?.isEmpty == true)
        assertEquals(setOf(RecoveryPermission.SLEEP), source.lastReadPermissions)
    }

    @Test
    fun grantedDataLoadsNormalizedContext() = runTest(dispatcher) {
        val source = FakeRecoverySource(
            granted = RecoveryPermission.entries.toSet(),
            records = RawRecoveryRecords(
                restingHeartRates = listOf(
                    RawRestingHeartRate(
                        sourcePackage = "synthetic.source",
                        time = Instant.parse("2026-08-25T08:00:00Z"),
                        beatsPerMinute = 57,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(source)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.allPermissionsGranted)
        assertEquals(57L, viewModel.uiState.value.context?.restingHeartRates?.single()?.beatsPerMinute)
        assertFalse(viewModel.uiState.value.error)
    }

    @Test
    fun permissionRevokedDuringReadClearsContextAndReportsPermissionChange() = runTest(dispatcher) {
        val source = FakeRecoverySource(
            granted = setOf(RecoveryPermission.RESTING_HEART_RATE),
            readFailure = SecurityException("synthetic permission change"),
        )
        source.grantsAfterFailedRead = emptySet()
        val viewModel = viewModel(source)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.permissionChanged)
        assertTrue(viewModel.uiState.value.grantedPermissions.isEmpty())
        assertNull(viewModel.uiState.value.context)
        assertFalse(viewModel.uiState.value.error)
    }

    @Test
    fun readFailureIsRecoverableWithoutRetainingHealthContext() = runTest(dispatcher) {
        val source = FakeRecoverySource(
            granted = setOf(RecoveryPermission.RESTING_HEART_RATE),
            readFailure = IOException("synthetic provider failure"),
        )
        val viewModel = viewModel(source)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.context)

        source.readFailure = null
        source.records = RawRecoveryRecords(
            restingHeartRates = listOf(
                RawRestingHeartRate(
                    sourcePackage = "synthetic.retry.source",
                    time = Instant.parse("2026-08-25T09:00:00Z"),
                    beatsPerMinute = 60,
                ),
            ),
        )
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.error)
        assertEquals(60L, viewModel.uiState.value.context?.restingHeartRates?.single()?.beatsPerMinute)
    }

    @Test
    fun disconnectRevokesAndClearsEphemeralContext() = runTest(dispatcher) {
        val source = FakeRecoverySource(
            granted = setOf(RecoveryPermission.RESTING_HEART_RATE),
            records = RawRecoveryRecords(
                restingHeartRates = listOf(
                    RawRestingHeartRate("synthetic.source", Instant.parse("2026-08-25T09:00:00Z"), 60),
                ),
            ),
        )
        val viewModel = viewModel(source)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.context != null)

        viewModel.disconnect()
        advanceUntilIdle()

        assertEquals(1, source.disconnectCalls)
        assertNull(viewModel.uiState.value.context)
        assertTrue(viewModel.uiState.value.grantedPermissions.isEmpty())
    }

    private fun viewModel(source: RecoveryHealthSource) = RecoveryContextViewModel(
        source = source,
        today = { day },
        zoneId = { utc },
    ).also { it.refresh() }

    private class FakeRecoverySource(
        var availabilityValue: RecoveryAvailability = RecoveryAvailability.AVAILABLE,
        var granted: Set<RecoveryPermission> = emptySet(),
        var records: RawRecoveryRecords = RawRecoveryRecords(),
        var readFailure: Exception? = null,
    ) : RecoveryHealthSource {
        override val requestedPermissionStrings: Set<String> = setOf("synthetic.permission")
        var permissionReads = 0
        var dataReads = 0
        var disconnectCalls = 0
        var lastReadPermissions: Set<RecoveryPermission> = emptySet()
        var grantsAfterFailedRead: Set<RecoveryPermission>? = null
        private var failedRead = false

        override fun availability(): RecoveryAvailability = availabilityValue

        override suspend fun grantedPermissions(): Set<RecoveryPermission> {
            permissionReads += 1
            if (failedRead) return grantsAfterFailedRead ?: granted
            return granted
        }

        override suspend fun readRawContext(
            day: LocalDate,
            zoneId: ZoneId,
            grantedPermissions: Set<RecoveryPermission>,
        ): RawRecoveryRecords {
            dataReads += 1
            lastReadPermissions = grantedPermissions
            val failure = readFailure
            if (failure != null) {
                failedRead = true
                throw failure
            }
            return records
        }

        override suspend fun disconnect() {
            disconnectCalls += 1
            granted = emptySet()
        }
    }
}
