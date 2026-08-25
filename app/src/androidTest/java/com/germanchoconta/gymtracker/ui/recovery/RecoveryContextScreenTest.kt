package com.germanchoconta.gymtracker.ui.recovery

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.health.RecoveryAvailability
import com.germanchoconta.gymtracker.data.health.RecoveryContext
import com.germanchoconta.gymtracker.data.health.RecoveryHrvRmssd
import com.germanchoconta.gymtracker.data.health.RecoveryPermission
import com.germanchoconta.gymtracker.data.health.RecoveryRestingHeartRate
import com.germanchoconta.gymtracker.data.health.RecoverySleepSession
import com.germanchoconta.gymtracker.data.health.SleepStageDurations
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryContextScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unavailableIsExplicitAndDoesNotLookLikeMissingData() {
        setScreen(RecoveryUiState(availability = RecoveryAvailability.UNAVAILABLE))

        composeRule.onNodeWithText("Health Connect no está disponible").assertIsDisplayed()
        composeRule.onNodeWithText("No hay datos para hoy").assertDoesNotExist()
    }

    @Test
    fun deniedPermissionOffersOptInWithoutBlockingApp() {
        var permissionRequests = 0
        setScreen(
            state = RecoveryUiState(availability = RecoveryAvailability.AVAILABLE),
            onRequestPermissions = { permissionRequests += 1 },
        )

        composeRule.onNodeWithText("Sin permisos de Health Connect.").assertIsDisplayed()
        composeRule.onNodeWithText("Conectar Health Connect").performClick()
        assertEquals(1, permissionRequests)
    }

    @Test
    fun partialPermissionIsDistinctFromEmptyData() {
        setScreen(
            RecoveryUiState(
                availability = RecoveryAvailability.AVAILABLE,
                grantedPermissions = setOf(RecoveryPermission.SLEEP),
                context = emptyContext(),
            ),
        )

        composeRule.onNodeWithText("Permisos parciales: 1 de 3.").assertIsDisplayed()
        composeRule.onNodeWithText("Revisar permisos").assertIsDisplayed()
        composeRule.onNodeWithText("No hay datos para hoy").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadedContextIsReachableAtNarrowWidthAndKeepsMedicalGuardrail() {
        composeRule.enableAccessibilityChecks()
        setScreen(
            state = RecoveryUiState(
                availability = RecoveryAvailability.AVAILABLE,
                grantedPermissions = RecoveryPermission.entries.toSet(),
                context = syntheticContext(),
            ),
            narrow = true,
        )

        composeRule.onNodeWithText("No es un diagnóstico", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sueño").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("6 h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("59 bpm").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("HRV (RMSSD)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("RMSSD; no se mezcla", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Desconectar").performScrollTo().assertIsDisplayed()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    private fun setScreen(
        state: RecoveryUiState,
        onRequestPermissions: () -> Unit = {},
        narrow: Boolean = false,
    ) {
        composeRule.setContent {
            MaterialTheme {
                if (narrow) {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        RecoveryContextScreen(
                            state = state,
                            onBack = {},
                            onRequestPermissions = onRequestPermissions,
                            onRefresh = {},
                            onDisconnect = {},
                        )
                    }
                } else {
                    RecoveryContextScreen(
                        state = state,
                        onBack = {},
                        onRequestPermissions = onRequestPermissions,
                        onRefresh = {},
                        onDisconnect = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun emptyContext() = RecoveryContext(
        day = LocalDate.of(2026, 8, 25),
        zoneId = ZoneId.of("UTC"),
        sleepSessions = emptyList(),
        restingHeartRates = emptyList(),
        hrvRmssd = emptyList(),
    )

    private fun syntheticContext() = RecoveryContext(
        day = LocalDate.of(2026, 8, 25),
        zoneId = ZoneId.of("UTC"),
        sleepSessions = listOf(
            RecoverySleepSession(
                sourcePackage = "synthetic.sleep.source",
                startTime = Instant.parse("2026-08-25T00:00:00Z"),
                endTime = Instant.parse("2026-08-25T06:00:00Z"),
                duration = Duration.ofHours(6),
                stages = SleepStageDurations(
                    light = Duration.ofHours(3),
                    deep = Duration.ofHours(1),
                    rem = Duration.ofHours(2),
                ),
            ),
        ),
        restingHeartRates = listOf(
            RecoveryRestingHeartRate(
                sourcePackage = "synthetic.rhr.source",
                time = Instant.parse("2026-08-25T08:00:00Z"),
                beatsPerMinute = 59,
            ),
        ),
        hrvRmssd = listOf(
            RecoveryHrvRmssd(
                sourcePackage = "synthetic.hrv.source",
                time = Instant.parse("2026-08-25T08:05:00Z"),
                milliseconds = 42.5,
            ),
        ),
    )
}
