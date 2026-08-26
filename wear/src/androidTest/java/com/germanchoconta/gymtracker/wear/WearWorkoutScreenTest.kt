package com.germanchoconta.gymtracker.wear

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.wear.compose.material3.MaterialTheme
import com.germanchoconta.gymtracker.wear.protocol.WearActiveWorkoutSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearExerciseSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearPreviousSetSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearSetSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearWorkoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearWorkoutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wristScreenShowsLoggerContextAndLargeActionSemantics() {
        var repsUps = 0
        composeRule.setContent {
            MaterialTheme {
                WearWorkoutScreen(
                    uiState = syntheticUiState(),
                    onRefresh = {},
                    onLoadDown = {},
                    onLoadUp = {},
                    onRepsDown = {},
                    onRepsUp = { repsUps++ },
                    onRirDown = {},
                    onRirUp = {},
                    onClearRir = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithText("Synthetic Press").assertExists()
        composeRule.onNodeWithText("PREVIOUS  75 kg × 8 • RIR 2").assertExists()
        composeRule.onNodeWithText("TARGET  6–10 reps • RIR 2").assertExists()
        composeRule.onNodeWithContentDescription("Increase Reps").performClick()
        assertEquals(1, repsUps)
        composeRule.onNodeWithContentDescription("Complete current set").assertExists()
    }

    @Test
    fun noActiveWorkoutStaysMinimal() {
        composeRule.setContent {
            MaterialTheme {
                WearWorkoutScreen(
                    uiState = WearWorkoutUiState(
                        snapshot = WearWorkoutSnapshot(snapshotNonce = "none", activeWorkout = null),
                    ),
                    onRefresh = {},
                    onLoadDown = {},
                    onLoadUp = {},
                    onRepsDown = {},
                    onRepsUp = {},
                    onRirDown = {},
                    onRirUp = {},
                    onClearRir = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithText("No active workout").assertExists()
        composeRule.onNodeWithText("Start or resume on your phone").assertExists()
        composeRule.onNodeWithText("Refresh").assertExists()
    }

    private fun syntheticUiState() = WearWorkoutUiState(
        phoneReachable = true,
        snapshot = WearWorkoutSnapshot(
            snapshotNonce = "snapshot-a",
            activeWorkout = WearActiveWorkoutSnapshot(
                id = "workout-a",
                title = "Synthetic Workout",
                startedAt = 10_000L,
                restTimerEndsAt = null,
                restTimerWorkoutExerciseId = null,
                exercises = listOf(
                    WearExerciseSnapshot(
                        id = "workout-exercise-a",
                        exerciseId = "exercise-a",
                        name = "Synthetic Press",
                        position = 0,
                        targetSetCount = 2,
                        repMin = 6,
                        repMax = 10,
                        targetRirTenths = 20,
                        restSeconds = 75,
                        loadIncrementGrams = 2_500L,
                        previousReferenceMode = "ANY_WORKOUT",
                        sets = listOf(
                            WearSetSnapshot(
                                id = "set-a",
                                position = 0,
                                type = "WORK",
                                loadGrams = 80_000L,
                                reps = 8,
                                rirTenths = 20,
                                completedAt = null,
                                previous = WearPreviousSetSnapshot(75_000L, 8, 20, "WORK"),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
