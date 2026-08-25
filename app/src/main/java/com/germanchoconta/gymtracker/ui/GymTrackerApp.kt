package com.germanchoconta.gymtracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.germanchoconta.gymtracker.data.backup.BackupDocumentIo
import com.germanchoconta.gymtracker.data.backup.BackupRepository
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.HistoryRepository
import com.germanchoconta.gymtracker.data.local.RoutineRepository
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.ui.backup.BackupScreen
import com.germanchoconta.gymtracker.ui.backup.BackupViewModel
import com.germanchoconta.gymtracker.ui.history.HistoryScreen
import com.germanchoconta.gymtracker.ui.history.HistoryViewModel
import com.germanchoconta.gymtracker.ui.management.ExerciseEditorScreen
import com.germanchoconta.gymtracker.ui.management.ExerciseLibraryViewModel
import com.germanchoconta.gymtracker.ui.management.RoutineEditorScreen
import com.germanchoconta.gymtracker.ui.management.RoutineLibraryViewModel
import com.germanchoconta.gymtracker.ui.workout.WorkoutLoggerScreen
import com.germanchoconta.gymtracker.ui.workout.WorkoutLoggerViewModel

private enum class AppDestination { EXERCISES, ROUTINES, HISTORY }

@Composable
fun GymTrackerApp(
    exerciseRepository: ExerciseRepository,
    routineRepository: RoutineRepository,
    workoutRepository: WorkoutRepository,
    historyRepository: HistoryRepository,
    backupRepository: BackupRepository,
    backupDocumentIo: BackupDocumentIo,
    appVersion: String,
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
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(historyRepository),
    )
    val backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(backupRepository, backupDocumentIo, appVersion),
    )
    val exerciseState by exerciseViewModel.uiState.collectAsStateWithLifecycle()
    val routineState by routineViewModel.uiState.collectAsStateWithLifecycle()
    val workoutState by workoutViewModel.uiState.collectAsStateWithLifecycle()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    var appDestinationName by rememberSaveable { mutableStateOf(AppDestination.EXERCISES.name) }
    var dataManagementOpen by rememberSaveable { mutableStateOf(false) }
    val appDestination = AppDestination.valueOf(appDestinationName)
    val topLevelStateHolder = rememberSaveableStateHolder()

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
        dataManagementOpen -> BackupScreen(
            state = backupState,
            viewModel = backupViewModel,
            onBack = {
                dataManagementOpen = false
                workoutViewModel.recoverActiveWorkout()
            },
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
        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = appDestination == AppDestination.EXERCISES,
                        onClick = { appDestinationName = AppDestination.EXERCISES.name },
                        icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                        label = { Text("Ejercicios") },
                    )
                    NavigationBarItem(
                        selected = appDestination == AppDestination.ROUTINES,
                        onClick = { appDestinationName = AppDestination.ROUTINES.name },
                        icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
                        label = { Text("Rutinas") },
                    )
                    NavigationBarItem(
                        selected = appDestination == AppDestination.HISTORY,
                        onClick = { appDestinationName = AppDestination.HISTORY.name },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Historial") },
                    )
                }
            },
        ) { outerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(outerPadding)) {
                topLevelStateHolder.SaveableStateProvider(appDestination.name) {
                    when (appDestination) {
                        AppDestination.EXERCISES -> ExerciseTopLevelScreen(
                            state = exerciseState,
                            onQueryChange = exerciseViewModel::updateQuery,
                            onCreateExercise = exerciseViewModel::startCreate,
                            onEditExercise = exerciseViewModel::startEdit,
                        )
                        AppDestination.ROUTINES -> RoutineTopLevelScreen(
                            state = routineState,
                            onCreateRoutine = routineViewModel::startCreate,
                            onEditRoutine = routineViewModel::startEdit,
                            onStartRoutine = workoutViewModel::startRoutine,
                        )
                        AppDestination.HISTORY -> HistoryScreen(
                            state = historyState,
                            viewModel = historyViewModel,
                            onManageData = { dataManagementOpen = true },
                        )
                    }
                }
            }
        }
    }
}
