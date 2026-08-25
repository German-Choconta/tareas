package com.germanchoconta.gymtracker.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.germanchoconta.gymtracker.data.backup.BackupDocumentIo
import com.germanchoconta.gymtracker.data.backup.BackupFormatException
import com.germanchoconta.gymtracker.data.backup.BackupPreview
import com.germanchoconta.gymtracker.data.backup.BackupRepository
import com.germanchoconta.gymtracker.data.backup.BackupValidationException
import com.germanchoconta.gymtracker.data.backup.BackupValidator
import com.germanchoconta.gymtracker.data.backup.DecodedBackup
import com.germanchoconta.gymtracker.data.backup.PortableBackupCodec
import com.germanchoconta.gymtracker.data.backup.WorkoutCsvExporter
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface BackupDataGateway {
    suspend fun snapshot(): com.germanchoconta.gymtracker.data.backup.BackupSnapshot
    suspend fun replaceAll(snapshot: com.germanchoconta.gymtracker.data.backup.BackupSnapshot)
}

internal interface BackupFileGateway {
    suspend fun read(uri: Uri): ByteArray
    suspend fun write(uri: Uri, bytes: ByteArray)
}

private class RepositoryGateway(private val repository: BackupRepository) : BackupDataGateway {
    override suspend fun snapshot() = repository.snapshot()
    override suspend fun replaceAll(snapshot: com.germanchoconta.gymtracker.data.backup.BackupSnapshot) =
        repository.replaceAll(snapshot)
}

private class DocumentGateway(private val documentIo: BackupDocumentIo) : BackupFileGateway {
    override suspend fun read(uri: Uri) = documentIo.read(uri)
    override suspend fun write(uri: Uri, bytes: ByteArray) = documentIo.write(uri, bytes)
}

data class BackupUiState(
    val busy: Boolean = false,
    val preview: BackupPreview? = null,
    val replaceConfirmationVisible: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class BackupViewModel internal constructor(
    private val dataGateway: BackupDataGateway,
    private val fileGateway: BackupFileGateway,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val actionMutex = Mutex()
    private var pendingImport: DecodedBackup? = null

    fun exportBackup(uri: Uri) = launchExclusive {
        val bytes = PortableBackupCodec.encode(
            snapshot = dataGateway.snapshot(),
            generatedAtEpochMillis = now(),
            appVersion = appVersion,
        )
        fileGateway.write(uri, bytes)
        _uiState.update { it.copy(message = "Backup portable exportado correctamente.") }
    }

    fun exportCsv(uri: Uri) = launchExclusive {
        val bytes = WorkoutCsvExporter.encode(dataGateway.snapshot())
        fileGateway.write(uri, bytes)
        _uiState.update { it.copy(message = "CSV de workouts exportado correctamente.") }
    }

    fun importBackup(uri: Uri) = launchExclusive {
        pendingImport = null
        _uiState.update { it.copy(preview = null, replaceConfirmationVisible = false) }
        val decoded = PortableBackupCodec.decode(fileGateway.read(uri))
        val preview = BackupValidator.validate(decoded)
        pendingImport = decoded
        _uiState.update { it.copy(preview = preview, message = "Backup validado. Revisa el resumen antes de restaurar.") }
    }

    fun requestReplaceConfirmation() {
        if (_uiState.value.busy || pendingImport == null) return
        _uiState.update { it.copy(replaceConfirmationVisible = true, message = null, error = null) }
    }

    fun dismissReplaceConfirmation() {
        _uiState.update { it.copy(replaceConfirmationVisible = false) }
    }

    fun confirmReplace(onRestored: () -> Unit = {}) = launchExclusive {
        val decoded = pendingImport
            ?: throw BackupValidationException("No hay un backup validado pendiente de restauración.")
        _uiState.update { it.copy(replaceConfirmationVisible = false) }
        dataGateway.replaceAll(decoded.snapshot)
        pendingImport = null
        _uiState.update {
            it.copy(
                preview = null,
                message = "Datos restaurados y verificados correctamente.",
            )
        }
        onRestored()
    }

    fun discardPreview() {
        if (_uiState.value.busy) return
        pendingImport = null
        _uiState.update { it.copy(preview = null, replaceConfirmationVisible = false) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun launchExclusive(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!actionMutex.tryLock()) return@launch
            try {
                _uiState.update { it.copy(busy = true, error = null, message = null) }
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: BackupFormatException) {
                _uiState.update { it.copy(error = error.message ?: "Backup incompatible o corrupto.") }
            } catch (error: BackupValidationException) {
                _uiState.update { it.copy(error = error.message ?: "El backup no pasó la validación.") }
            } catch (error: SecurityException) {
                _uiState.update { it.copy(error = "Android denegó el acceso al documento seleccionado.") }
            } catch (error: IOException) {
                _uiState.update { it.copy(error = "No se pudo leer o escribir el documento seleccionado.") }
            } catch (_: IllegalStateException) {
                _uiState.update { it.copy(error = "La operación no pudo completarse sin alterar los datos existentes.") }
            } finally {
                _uiState.update { it.copy(busy = false) }
                actionMutex.unlock()
            }
        }
    }

    companion object {
        fun factory(
            repository: BackupRepository,
            documentIo: BackupDocumentIo,
            appVersion: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackupViewModel(
                    dataGateway = RepositoryGateway(repository),
                    fileGateway = DocumentGateway(documentIo),
                    appVersion = appVersion,
                ) as T
        }
    }
}
