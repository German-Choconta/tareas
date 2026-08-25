package com.germanchoconta.gymtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.RoutineRepository
import com.germanchoconta.gymtracker.ui.management.ExerciseEditorScreen
import com.germanchoconta.gymtracker.ui.management.ExerciseLibraryViewModel
import com.germanchoconta.gymtracker.ui.management.ManagementDestination
import com.germanchoconta.gymtracker.ui.management.ManagementHome
import com.germanchoconta.gymtracker.ui.management.RoutineEditorScreen
import com.germanchoconta.gymtracker.ui.management.RoutineLibraryViewModel

@Composable
fun GymTrackerApp(
    exerciseRepository: ExerciseRepository,
    routineRepository: RoutineRepository,
) {
    val exerciseViewModel: ExerciseLibraryViewModel = viewModel(
        factory = ExerciseLibraryViewModel.factory(exerciseRepository),
    )
    val routineViewModel: RoutineLibraryViewModel = viewModel(
        factory = RoutineLibraryViewModel.factory(routineRepository, exerciseRepository),
    )
    val exerciseState by exerciseViewModel.uiState.collectAsStateWithLifecycle()
    val routineState by routineViewModel.uiState.collectAsStateWithLifecycle()
    var destinationName by rememberSaveable { mutableStateOf(ManagementDestination.EXERCISES.name) }
    val destination = ManagementDestination.valueOf(destinationName)

    when {
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
