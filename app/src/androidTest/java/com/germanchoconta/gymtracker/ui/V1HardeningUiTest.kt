package com.germanchoconta.gymtracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.domain.ProgressionAction
import com.germanchoconta.gymtracker.domain.ProgressionRecommendation
import com.germanchoconta.gymtracker.ui.management.ExerciseLibraryUiState
import com.germanchoconta.gymtracker.ui.management.RoutineLibraryUiState
import com.germanchoconta.gymtracker.ui.theme.GymTrackerTheme
import com.germanchoconta.gymtracker.ui.workout.WorkoutExerciseChoice
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
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun exerciseCreateActionExposesSpecificTalkBackLabelAndPassesAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
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

        composeRule.onRoot().tryPerformAccessibilityChecks()
        composeRule.onNodeWithContentDescription("Crear ejercicio").performClick()
        composeRule.waitForIdle()
        assertEquals(1, exerciseCreates)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun routineCreateActionExposesSpecificTalkBackLabelAndPassesAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
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

        composeRule.onRoot().tryPerformAccessibilityChecks()
        composeRule.onNodeWithContentDescription("Crear rutina").performClick()
        composeRule.waitForIdle()
        assertEquals(1, routineCreates)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun narrowWorkoutViewportKeepsCriticalSetInputsReachableAndAccessible() {
        composeRule.enableAccessibilityChecks()
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                    WorkoutLoggerScreen(
                        state = syntheticWorkoutState(),
                        onLoadChange = { _, _ -> },
                        onApplySuggestedLoad = {},
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

        composeRule.onRoot().tryPerformAccessibilityChecks()
        composeRule.onNodeWithText("Carga (kg)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reps").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("RIR").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Completar serie").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Borrar serie 1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun progressionGuidanceIsExplicitAndApplyRequiresUserAction() {
        var applyCount = 0
        val state = syntheticWorkoutState().copy(
            exercises = syntheticWorkoutState().exercises.map { exercise ->
                exercise.copy(
                    sets = exercise.sets.map { set ->
                        set.copy(
                            progression = ProgressionRecommendation(
                                action = ProgressionAction.INCREASE_LOAD,
                                reason = "La referencia alcanzó el techo de 10 reps.",
                                suggestedLoadGrams = 42_500L,
                                suggestedReps = 6,
                                previousLoadGrams = 40_000L,
                                previousReps = 10,
                            ),
                        )
                    },
                )
            },
        )
        composeRule.setContent {
            MaterialTheme {
                WorkoutLoggerScreen(
                    state = state,
                    onLoadChange = { _, _ -> },
                    onApplySuggestedLoad = { applyCount += 1 },
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

        composeRule.onNodeWithText("PROGRESS • Sube carga").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Objetivo orientativo: 6 reps").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Aplicar 42.5 kg").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, applyCount)
    }

    @Test
    fun exercisePickerSurvivesSavedInstanceStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        val state = syntheticWorkoutState().copy(
            exercises = emptyList(),
            exerciseChoices = listOf(
                WorkoutExerciseChoice(
                    id = "synthetic-choice",
                    name = "Synthetic Choice",
                    equipment = "Synthetic Equipment",
                ),
            ),
        )

        restorationTester.setContent {
            MaterialTheme {
                WorkoutLoggerScreen(
                    state = state,
                    onLoadChange = { _, _ -> },
                    onApplySuggestedLoad = {},
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

        composeRule.onNodeWithText("Añadir ejercicio").performScrollTo().performClick()
        composeRule.onNodeWithText("Synthetic Choice").assertIsDisplayed()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Synthetic Choice").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun systemThemeContentPassesAccessibilityChecksInLightAndDarkSchemes() {
        composeRule.enableAccessibilityChecks()
        var dark by mutableStateOf(false)
        composeRule.setContent {
            GymTrackerTheme(darkTheme = dark) {
                ExerciseTopLevelScreen(
                    state = ExerciseLibraryUiState(),
                    onQueryChange = {},
                    onCreateExercise = {},
                    onEditExercise = {},
                )
            }
        }

        composeRule.onRoot().tryPerformAccessibilityChecks()
        composeRule.runOnIdle { dark = true }
        composeRule.waitForIdle()
        composeRule.onRoot().tryPerformAccessibilityChecks()
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
