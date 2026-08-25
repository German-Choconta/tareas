package com.germanchoconta.gymtracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.ui.management.ExerciseLibraryUiState
import com.germanchoconta.gymtracker.ui.management.RoutineLibraryUiState
import com.germanchoconta.gymtracker.ui.workout.WorkoutExerciseUi
import com.germanchoconta.gymtracker.ui.workout.WorkoutLoggerScreen
import com.germanchoconta.gymtracker.ui.workout.WorkoutLoggerUiState
import com.germanchoconta.gymtracker.ui.workout.WorkoutSetUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V1HardeningUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topLevelCreateActionsExposeSpecificTalkBackLabels() {
        var exerciseCreates = 0
        composeRule.setContent {
            MaterialTheme {
                ExerciseTopLevelScreen(
                    state = ExerciseLibraryUiState(),
                    onQueryChange = {},
                    onCreateExercise = { exerciseCreates += 1 },
                    onEditExercise = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Crear ejercicio").performClick()
        composeRule.waitForIdle()
        assertEquals(1, exerciseCreates)

        var routineCreates = 0
        composeRule.setContent {
            MaterialTheme {
                RoutineTopLevelScreen(
                    state = RoutineLibraryUiState(),
                    onCreateRoutine = { routineCreates += 1 },
                    onEditRoutine = {},
                    onStartRoutine = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Crear rutina").performClick()
        composeRule.waitForIdle()
        assertEquals(1, routineCreates)
    }

    @Test
    fun narrowWorkoutViewportKeepsCriticalSetInputsReachable() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                    WorkoutLoggerScreen(
                        state = syntheticWorkoutState(),
                        onLoadChange = { _, _ -> },
                        onRepsChange = { _, _ -> },
                        onRirChange = { _, _ -> },
                        onTypeChange = { _, _ -> },
                        onToggleComplete = {},
                        onAddSet = {},
                        onRemoveSet = { _, _ -> },
                        onAddExercise = {},
                        onReplaceExercise = { _, _ -> },
                        onWorkoutNotesChange = {},
                        onExerciseNotesChange = { _, _ -> },
                        onStopTimer = {},
                        onRequestFinish = {},
                        onFinishConfirmed = {},
                        onDismissFinish = {},
                        onMessageShown = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Carga (kg)").assertIsDisplayed()
        composeRule.onNodeWithText("Reps").assertIsDisplayed()
        composeRule.onNodeWithText("RIR").assertIsDisplayed()
        composeRule.onNodeWithText("Completar serie").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Borrar serie 1").assertIsDisplayed()
    }

    private fun syntheticWorkoutState() = WorkoutLoggerUiState(
        loading = false,
        activeWorkoutId = "synthetic-workout-ui",
        title = "Synthetic Workout",
        startedAt = 10_000L,
        exercises = listOf(
            WorkoutExerciseUi(
                id = "synthetic-workout-exercise-ui",
                exerciseId = "synthetic-exercise-ui",
                exerciseName = "Synthetic Press",
                notes = "",
                targetSetCount = 1,
                repMin = 6,
                repMax = 10,
                targetRirTenths = 20,
                restSeconds = 90,
                loadIncrementGrams = 2_500,
                previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
                sets = listOf(
                    WorkoutSetUi(
                        id = "synthetic-set-ui",
                        position = 0,
                        loadText = "40",
                        repsText = "8",
                        rirText = "2",
                        type = SetTypes.WORK,
                        completedAt = null,
                    ),
                ),
            ),
        ),
    )
}
