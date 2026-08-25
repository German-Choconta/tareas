package com.germanchoconta.gymtracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.RoutineRepository
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.ui.management.ExerciseEditorScreen
import com.germanchoconta.gymtracker.ui.management.ExerciseLibraryViewModel
import com.germanchoconta.gymtracker.ui.management.ManagementDestination
import com.germanchoconta.gymtracker.ui.management.ManagementHome
import com.germanchoconta.gymtracker.ui.management.RoutineEditorScreen
import com.germanchoconta.gymtracker.ui.management.RoutineLibraryViewModel
import com.germanchoconta.gymtracker.ui.workout.RoutineLaunchHome
import com.germanchoconta.gymtracker.ui.workout.WorkoutLoggerScreen
import com.germanchoconta.gymtracker.ui.workout.WorkoutLoggerViewModel

@Composable
fun GymTrackerApp(
    exerciseRepository: ExerciseRepository,
    routineRepository: RoutineRepository,
    workoutRepository: WorkoutRepository,
) {
    val exerciseViewModel: ExerciseLibraryViewModel = viewModel(
        factory = ExerciseLibraryViewModel.factory(exerciseRepository),
    )
    val routineViewModel: RoutineLibraryViewModel = viewModel(
        factory = RoutineLibraryViewModel.factory(routineRepository, exerciseRepository),
    )
    val workoutViewModel: WorkoutLoggerViewModel = viewModel(
        factory = WorkoutLoggerViewModel.factory(workoutRepository, exerciseRepository),
    )
    val exerciseState by exerciseViewModel.uiState.collectAsStateWithLifecycle()
    val routineState by routineViewModel.uiState.collectAsStateWithLifecycle()
    val workoutState by workoutViewModel.uiState.collectAsStateWithLifecycle()
    var destinationName by rememberSaveable { mutableStateOf(ManagementDestination.EXERCISES.name) }
    val destination = ManagementDestination.valueOf(destinationName)

    when {
        workoutState.loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        workoutState.hasActiveWorkout -> WorkoutLoggerScreen(
            state = workoutState,
            onLoadChange = workoutViewModel::updateLoad,
            onRepsChange = workoutViewModel::updateReps,
            onRirChange = workoutViewModel::updateRir,
            onTypeChange = workoutViewModel::updateSetType,
            onToggleComplete = workoutViewModel::toggleCompleted,
            onAddSet = workoutViewModel::addSet,
            onRemoveSet = workoutViewModel::removeSet,
            onAddExercise = workoutViewModel::addExercise,
            onReplaceExercise = workoutViewModel::replaceExercise,
            onWorkoutNotesChange = workoutViewModel::updateWorkoutNotes,
            onExerciseNotesChange = workoutViewModel::updateExerciseNotes,
            onStopTimer = workoutViewModel::stopRestTimer,
            onRequestFinish = workoutViewModel::requestFinish,
            onFinishConfirmed = workoutViewModel::finishConfirmed,
            onDismissFinish = workoutViewModel::dismissFinishConfirmation,
            onMessageShown = workoutViewModel::clearMessage,
        )
        exerciseState.editor != null -> ExerciseEditorScreen(
            state = exerciseState,
            onBack = exerciseViewModel::closeEditor,
            onSave = exerciseViewModel::saveEditor,
            onUpdate = exerciseViewModel::updateEditor,
            onSetMuscleRole = exerciseViewModel::setMuscleRole,
            onArchive = exerciseViewModel::archiveEditor,
        )
        routineState.editor != null -> RoutineEditorScreen(
            state = routineState,
            onBack = routineViewModel::closeEditor,
            onSave = routineViewModel::saveEditor,
            onUpdate = routineViewModel::updateEditor,
            onAddExercise = routineViewModel::addExercise,
            onUpdateExercise = routineViewModel::updateRoutineExercise,
            onMoveExercise = routineViewModel::moveRoutineExercise,
            onRemoveExercise = routineViewModel::removeRoutineExercise,
            onArchive = routineViewModel::archiveEditor,
        )
        destination == ManagementDestination.ROUTINES -> RoutineLaunchHome(
            state = routineState,
            onDestinationChange = { destinationName = it.name },
            onCreateRoutine = routineViewModel::startCreate,
            onEditRoutine = routineViewModel::startEdit,
            onStartRoutine = workoutViewModel::startRoutine,
        )
        else -> ManagementHome(
            destination = destination,
            onDestinationChange = { destinationName = it.name },
            exerciseState = exerciseState,
            routineState = routineState,
            onExerciseQueryChange = exerciseViewModel::updateQuery,
            onCreateExercise = exerciseViewModel::startCreate,
            onEditExercise = exerciseViewModel::startEdit,
            onCreateRoutine = routineViewModel::startCreate,
            onEditRoutine = routineViewModel::startEdit,
        )
    }
}
