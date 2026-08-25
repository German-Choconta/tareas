package com.germanchoconta.gymtracker.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.backup.BackupFormat
import com.germanchoconta.gymtracker.data.backup.BackupMetadata
import com.germanchoconta.gymtracker.data.backup.BackupPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun documentContractsUseStorageAccessFrameworkActionsAndMimeTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupIntent = backupCreateDocumentContract().createIntent(context, "synthetic.json")
        val csvIntent = csvCreateDocumentContract().createIntent(context, "synthetic.csv")
        val openIntent = backupOpenDocumentContract().createIntent(
            context,
            arrayOf(BackupFormat.MIME_TYPE, "application/octet-stream"),
        )

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, backupIntent.action)
        assertEquals(BackupFormat.MIME_TYPE, backupIntent.type)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, csvIntent.action)
        assertEquals(BackupFormat.CSV_MIME_TYPE, csvIntent.type)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, openIntent.action)
    }

    @Test
    fun canceledDocumentPickerDoesNothing() {
        var actions = 0
        dispatchDocumentUri(null) { actions += 1 }
        assertEquals(0, actions)

        dispatchDocumentUri(Uri.parse("content://synthetic/selected")) { actions += 1 }
        assertEquals(1, actions)
    }

    @Test
    fun validatedPreviewShowsCountsAndOnlyRequestsDestructiveConfirmation() {
        val preview = syntheticPreview()
        var replaceRequests = 0
        var confirmations = 0

        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(preview = preview),
                    onBack = {},
                    onExportBackup = {},
                    onExportCsv = {},
                    onImport = {},
                    onRequestReplace = { replaceRequests += 1 },
                    onDismissReplace = {},
                    onConfirmReplace = { confirmations += 1 },
                    onDiscardPreview = {},
                    onClearFeedback = {},
                )
            }
        }

        composeRule.onNodeWithText("Vista previa validada").performScrollTo().assertExists()
        composeRule.onNodeWithText("Ejercicios: 7").assertExists()
        composeRule.onNodeWithText("Rutinas: 3").assertExists()
        composeRule.onNodeWithText("Workouts: 11").assertExists()
        composeRule.onNodeWithText("Sets: 42").assertExists()
        composeRule.onNodeWithText("Workout activo incluido: sí").assertExists()
        assertEquals(0, replaceRequests)
        assertEquals(0, confirmations)

        composeRule.onNodeWithText("Revisar reemplazo").performClick()

        assertEquals(1, replaceRequests)
        assertEquals(0, confirmations)
    }

    @Test
    fun destructiveReplaceDialogHasSeparateAccessibleConfirmation() {
        var confirmations = 0

        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(
                        preview = syntheticPreview(),
                        replaceConfirmationVisible = true,
                    ),
                    onBack = {},
                    onExportBackup = {},
                    onExportCsv = {},
                    onImport = {},
                    onRequestReplace = {},
                    onDismissReplace = {},
                    onConfirmReplace = { confirmations += 1 },
                    onDiscardPreview = {},
                    onClearFeedback = {},
                )
            }
        }

        composeRule.onNodeWithText("Reemplazar todos los datos locales").assertExists()
        assertEquals(0, confirmations)
        composeRule.onNodeWithContentDescription(
            "Confirmar reemplazo destructivo de todos los datos locales",
        ).performClick()
        assertEquals(1, confirmations)
    }

    @Test
    fun exportAndImportButtonsRemainSeparateActions() {
        var backupExports = 0
        var csvExports = 0
        var imports = 0

        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(),
                    onBack = {},
                    onExportBackup = { backupExports += 1 },
                    onExportCsv = { csvExports += 1 },
                    onImport = { imports += 1 },
                    onRequestReplace = {},
                    onDismissReplace = {},
                    onConfirmReplace = {},
                    onDiscardPreview = {},
                    onClearFeedback = {},
                )
            }
        }

        composeRule.onNodeWithText("Exportar backup portable").performClick()
        composeRule.onNodeWithText("Exportar workouts a CSV").performClick()
        composeRule.onNodeWithText("Seleccionar backup para restaurar").performClick()

        assertEquals(1, backupExports)
        assertEquals(1, csvExports)
        assertEquals(1, imports)
    }

    private fun syntheticPreview() = BackupPreview(
        metadata = BackupMetadata(
            formatVersion = 1,
            generatedAtEpochMillis = 10_000,
            appVersion = "synthetic-ui",
            databaseSchemaVersion = 2,
            payloadSha256 = "0".repeat(64),
        ),
        exerciseCount = 7,
        routineCount = 3,
        workoutCount = 11,
        setCount = 42,
        earliestWorkoutStartedAt = 1_000,
        latestWorkoutStartedAt = 9_000,
        hasActiveWorkout = true,
    )
}
