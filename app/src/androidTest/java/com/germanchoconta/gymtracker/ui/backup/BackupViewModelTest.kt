package com.germanchoconta.gymtracker.ui.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.backup.BackupFormat
import com.germanchoconta.gymtracker.data.backup.BackupSnapshot
import com.germanchoconta.gymtracker.data.backup.PortableBackupCodec
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import java.io.IOException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importOnlyBuildsPreviewUntilExplicitConfirmationAndDoubleConfirmRestoresOnce() = runTest(dispatcher) {
        val snapshot = syntheticSnapshot()
        val uri = Uri.parse("content://synthetic/valid-backup")
        val data = FakeDataGateway(snapshot)
        val files = FakeFileGateway(
            reads = mutableMapOf(
                uri to PortableBackupCodec.encode(snapshot, 10_000, "synthetic-test"),
            ),
        )
        val viewModel = BackupViewModel(data, files, "synthetic-test", now = { 20_000 })

        viewModel.importBackup(uri)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.preview)
        assertEquals(0, data.replaceCount)
        assertFalse(viewModel.uiState.value.replaceConfirmationVisible)

        viewModel.requestReplaceConfirmation()
        assertTrue(viewModel.uiState.value.replaceConfirmationVisible)

        viewModel.confirmReplace()
        viewModel.confirmReplace()
        advanceUntilIdle()

        assertEquals(1, data.replaceCount)
        assertNull(viewModel.uiState.value.preview)
        assertEquals("Datos restaurados y verificados correctamente.", viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun corruptImportShowsSafeErrorAndDoesNotMutateData() = runTest(dispatcher) {
        val snapshot = syntheticSnapshot()
        val uri = Uri.parse("content://synthetic/corrupt-backup")
        val data = FakeDataGateway(snapshot)
        val files = FakeFileGateway(mutableMapOf(uri to "{bad".toByteArray()))
        val viewModel = BackupViewModel(data, files, "synthetic-test")

        viewModel.importBackup(uri)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.preview)
        assertEquals(0, data.replaceCount)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun ioFailureShowsSafeErrorAndKeepsPreviewEmpty() = runTest(dispatcher) {
        val data = FakeDataGateway(syntheticSnapshot())
        val files = FakeFileGateway(mutableMapOf(), failReads = true)
        val viewModel = BackupViewModel(data, files, "synthetic-test")

        viewModel.importBackup(Uri.parse("content://synthetic/io-failure"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.preview)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("leer o escribir"))
        assertEquals(0, data.replaceCount)
    }

    @Test
    fun exportsUsePortableAndCsvPipelines() = runTest(dispatcher) {
        val snapshot = syntheticSnapshot()
        val backupUri = Uri.parse("content://synthetic/export-backup")
        val csvUri = Uri.parse("content://synthetic/export-csv")
        val data = FakeDataGateway(snapshot)
        val files = FakeFileGateway(mutableMapOf())
        val viewModel = BackupViewModel(data, files, "synthetic-test", now = { 25_000 })

        viewModel.exportBackup(backupUri)
        advanceUntilIdle()
        val backupBytes = files.writes.getValue(backupUri)
        assertEquals(snapshot.normalized(), PortableBackupCodec.decode(backupBytes).snapshot)

        viewModel.exportCsv(csvUri)
        advanceUntilIdle()
        val csv = files.writes.getValue(csvUri).toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("workout_id,"))
    }

    private fun syntheticSnapshot(): BackupSnapshot = BackupSnapshot(
        exercises = listOf(ExerciseEntity("exercise-synthetic-ui", "Synthetic UI Exercise")),
        muscles = emptyList(),
        exerciseMuscles = emptyList(),
        routines = emptyList(),
        routineExercises = emptyList(),
        workouts = emptyList(),
        workoutExercises = emptyList(),
        workoutSets = emptyList(),
    )

    private class FakeDataGateway(initial: BackupSnapshot) : BackupDataGateway {
        var current = initial.normalized()
        var replaceCount = 0

        override suspend fun snapshot(): BackupSnapshot = current

        override suspend fun replaceAll(snapshot: BackupSnapshot) {
            replaceCount += 1
            current = snapshot.normalized()
        }
    }

    private class FakeFileGateway(
        val reads: MutableMap<Uri, ByteArray>,
        private val failReads: Boolean = false,
    ) : BackupFileGateway {
        val writes = mutableMapOf<Uri, ByteArray>()

        override suspend fun read(uri: Uri): ByteArray {
            if (failReads) throw IOException("synthetic IO failure")
            return reads.getValue(uri)
        }

        override suspend fun write(uri: Uri, bytes: ByteArray) {
            writes[uri] = bytes
        }
    }
}
