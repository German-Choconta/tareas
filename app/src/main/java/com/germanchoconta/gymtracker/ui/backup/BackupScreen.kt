package com.germanchoconta.gymtracker.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.data.backup.BackupFormat
import com.germanchoconta.gymtracker.data.backup.BackupPreview
import java.time.Instant
import java.time.LocalDate

internal fun backupCreateDocumentContract() =
    ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)

internal fun csvCreateDocumentContract() =
    ActivityResultContracts.CreateDocument(BackupFormat.CSV_MIME_TYPE)

internal fun backupOpenDocumentContract() = ActivityResultContracts.OpenDocument()

@Composable
fun BackupScreen(
    state: BackupUiState,
    viewModel: BackupViewModel,
    onBack: () -> Unit,
) {
    val today = remember { LocalDate.now().toString() }
    val backupLauncher = rememberLauncherForActivityResult(backupCreateDocumentContract()) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val csvLauncher = rememberLauncherForActivityResult(csvCreateDocumentContract()) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val importLauncher = rememberLauncherForActivityResult(backupOpenDocumentContract()) { uri ->
        uri?.let(viewModel::importBackup)
    }

    BackupScreenContent(
        state = state,
        onBack = onBack,
        onExportBackup = { backupLauncher.launch("gymtracker-backup-$today.json") },
        onExportCsv = { csvLauncher.launch("gymtracker-workouts-$today.csv") },
        onImport = {
            importLauncher.launch(
                arrayOf(
                    BackupFormat.MIME_TYPE,
                    "text/json",
                    "application/octet-stream",
                ),
            )
        },
        onRequestReplace = viewModel::requestReplaceConfirmation,
        onDismissReplace = viewModel::dismissReplaceConfirmation,
        onConfirmReplace = { viewModel.confirmReplace() },
        onDiscardPreview = viewModel::discardPreview,
        onClearFeedback = viewModel::clearFeedback,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupScreenContent(
    state: BackupUiState,
    onBack: () -> Unit,
    onExportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    onRequestReplace: () -> Unit,
    onDismissReplace: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDiscardPreview: () -> Unit,
    onClearFeedback: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tus datos") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al historial",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Propiedad de tus datos", style = MaterialTheme.typography.headlineSmall)
            Text(
                "El backup portable es independiente del archivo interno de Room. " +
                    "CSV es para lectura y análisis; no se usa para restaurar.",
            )

            Button(
                onClick = onExportBackup,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Exportar backup portable")
            }
            OutlinedButton(
                onClick = onExportCsv,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Exportar workouts a CSV")
            }
            OutlinedButton(
                onClick = onImport,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Seleccionar backup para restaurar")
            }

            if (state.busy) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Procesando…")
                }
            }

            state.message?.let { message ->
                FeedbackPanel(
                    title = "Listo",
                    message = message,
                    onDismiss = onClearFeedback,
                )
            }
            state.error?.let { error ->
                FeedbackPanel(
                    title = "No se pudo completar",
                    message = error,
                    onDismiss = onClearFeedback,
                )
            }

            state.preview?.let { preview ->
                HorizontalDivider()
                ImportPreview(
                    preview = preview,
                    enabled = !state.busy,
                    onRequestReplace = onRequestReplace,
                    onDiscard = onDiscardPreview,
                )
            }

            HorizontalDivider()
            Text("Protección adicional", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android Auto Backup puede ayudar en migraciones o recuperación del dispositivo, " +
                    "pero depende del sistema, la cuenta y sus límites. Conserva también un backup portable manual.",
            )
        }
    }

    if (state.replaceConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text("Reemplazar todos los datos locales") },
            text = {
                Text(
                    "Esta restauración reemplaza de forma atómica el dataset local actual por el backup " +
                        "que acabas de validar. No se mezclan datasets. Si algo falla, la operación se revierte.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmReplace,
                    modifier = Modifier.semantics {
                        contentDescription = "Confirmar reemplazo destructivo de todos los datos locales"
                    },
                ) {
                    Text("Reemplazar y restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReplace) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun ImportPreview(
    preview: BackupPreview,
    enabled: Boolean,
    onRequestReplace: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vista previa validada", style = MaterialTheme.typography.titleLarge)
        Text("Formato: v${preview.metadata.formatVersion}")
        Text("Creado: ${Instant.ofEpochMilli(preview.metadata.generatedAtEpochMillis)}")
        Text("App de origen: ${preview.metadata.appVersion}")
        Text("Schema de origen: v${preview.metadata.databaseSchemaVersion}")
        Text("Ejercicios: ${preview.exerciseCount}")
        Text("Rutinas: ${preview.routineCount}")
        Text("Workouts: ${preview.workoutCount}")
        Text("Sets: ${preview.setCount}")
        if (preview.earliestWorkoutStartedAt != null && preview.latestWorkoutStartedAt != null) {
            Text(
                "Rango: ${Instant.ofEpochMilli(preview.earliestWorkoutStartedAt)} → " +
                    Instant.ofEpochMilli(preview.latestWorkoutStartedAt),
            )
        }
        Text("Workout activo incluido: ${if (preview.hasActiveWorkout) "sí" else "no"}")
        Text(
            "Nada se ha modificado todavía. Restaurar requiere una confirmación separada.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onRequestReplace,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Revisar reemplazo")
        }
        TextButton(onClick = onDiscard, enabled = enabled) {
            Text("Descartar esta vista previa")
        }
    }
}

@Composable
private fun FeedbackPanel(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message)
        TextButton(onClick = onDismiss) {
            Text("Cerrar mensaje")
        }
    }
}
