package com.germanchoconta.gymtracker.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.ui.management.gramsToKilogramsText
import com.germanchoconta.gymtracker.ui.management.rirTenthsToText
import kotlinx.coroutines.delay

private val NARROW_SET_EDITOR_WIDTH = 360.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutLoggerScreen(
    state: WorkoutLoggerUiState,
    onLoadChange: (String, String) -> Unit,
    onRepsChange: (String, String) -> Unit,
    onRirChange: (String, String) -> Unit,
    onTypeChange: (String, String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, Boolean) -> Unit,
    onAddExercise: (String) -> Unit,
    onReplaceExercise: (String, String) -> Unit,
    onWorkoutNotesChange: (String) -> Unit,
    onExerciseNotesChange: (String, String) -> Unit,
    onStopTimer: () -> Unit,
    onRequestFinish: () -> Unit,
    onFinishConfirmed: () -> Unit,
    onDismissFinish: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var addExerciseOpen by rememberSaveable { mutableStateOf(false) }
    var replaceWorkoutExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCompletedSetId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    if (state.confirmFinish) {
        AlertDialog(
            onDismissRequest = onDismissFinish,
            title = { Text("¿Finalizar entrenamiento?") },
            text = {
                Text(
                    "Quedan ${state.incompleteSetCount} series sin completar. Se conservarán como incompletas y no se marcarán como realizadas.",
                )
            },
            confirmButton = { TextButton(onClick = onFinishConfirmed) { Text("Finalizar") } },
            dismissButton = { TextButton(onClick = onDismissFinish) { Text("Seguir entrenando") } },
        )
    }

    deleteCompletedSetId?.let { setId ->
        AlertDialog(
            onDismissRequest = { deleteCompletedSetId = null },
            title = { Text("Borrar serie completada") },
            text = { Text("Esta serie ya fue marcada como completada. Confirma para borrarla de esta sesión.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveSet(setId, true)
                        deleteCompletedSetId = null
                    },
                ) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCompletedSetId = null }) { Text("Cancelar") }
            },
        )
    }

    if (addExerciseOpen) {
        ExercisePickerDialog(
            title = "Añadir ejercicio",
            choices = state.exerciseChoices,
            onDismiss = { addExerciseOpen = false },
            onChoose = { exerciseId ->
                onAddExercise(exerciseId)
                addExerciseOpen = false
            },
        )
    }

    replaceWorkoutExerciseId?.let { workoutExerciseId ->
        ExercisePickerDialog(
            title = "Reemplazar ejercicio",
            choices = state.exerciseChoices,
            onDismiss = { replaceWorkoutExerciseId = null },
            onChoose = { exerciseId ->
                onReplaceExercise(workoutExerciseId, exerciseId)
                replaceWorkoutExerciseId = null
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Entrenamiento" }) },
                actions = {
                    TextButton(onClick = onRequestFinish) {
                        Text("Finalizar")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "rest-timer") {
                RestTimerSurface(
                    endsAt = state.restTimerEndsAt,
                    onStop = onStopTimer,
                )
            }

            items(state.exercises, key = WorkoutExerciseUi::id) { exercise ->
                WorkoutExerciseCard(
                    exercise = exercise,
                    onLoadChange = onLoadChange,
                    onRepsChange = onRepsChange,
                    onRirChange = onRirChange,
                    onTypeChange = onTypeChange,
                    onToggleComplete = onToggleComplete,
                    onAddSet = onAddSet,
                    onDeleteSet = { set ->
                        if (set.completed) deleteCompletedSetId = set.id else onRemoveSet(set.id, false)
                    },
                    onReplace = { replaceWorkoutExerciseId = exercise.id },
                    onNotesChange = { onExerciseNotesChange(exercise.id, it) },
                )
            }

            item(key = "add-exercise") {
                OutlinedButton(
                    onClick = { addExerciseOpen = true },
                    enabled = state.exerciseChoices.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                ) {
                    Text("Añadir ejercicio")
                }
            }

            item(key = "workout-notes") {
                OutlinedTextField(
                    value = state.workoutNotes,
                    onValueChange = onWorkoutNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notas del entrenamiento") },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        }
    }
}

@Composable
private fun RestTimerSurface(
    endsAt: Long?,
    onStop: () -> Unit,
) {
    if (endsAt == null) return
    var now by remember(endsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    val remaining = restSecondsRemaining(endsAt, now)

    LaunchedEffect(endsAt) {
        while (restSecondsRemaining(endsAt, System.currentTimeMillis()) > 0L) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
        now = System.currentTimeMillis()
        onStop()
    }

    if (remaining <= 0L) return
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("DESCANSO", style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(remaining), style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = onStop, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("Detener")
            }
        }
    }
}

@Composable
private fun WorkoutExerciseCard(
    exercise: WorkoutExerciseUi,
    onLoadChange: (String, String) -> Unit,
    onRepsChange: (String, String) -> Unit,
    onRirChange: (String, String) -> Unit,
    onTypeChange: (String, String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onAddSet: (String) -> Unit,
    onDeleteSet: (WorkoutSetUi) -> Unit,
    onReplace: () -> Unit,
    onNotesChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    exercise.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onReplace, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Cambiar")
                }
            }

            Text(
                targetLine(exercise),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "TODAY",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            exercise.sets.forEach { set ->
                key(set.id) {
                    WorkoutSetEditor(
                        set = set,
                        onLoadChange = { onLoadChange(set.id, it) },
                        onRepsChange = { onRepsChange(set.id, it) },
                        onRirChange = { onRirChange(set.id, it) },
                        onTypeChange = { onTypeChange(set.id, it) },
                        onToggleComplete = { onToggleComplete(set.id) },
                        onDelete = { onDeleteSet(set) },
                    )
                }
            }

            OutlinedButton(
                onClick = { onAddSet(exercise.id) },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
                Text("Añadir serie")
            }

            OutlinedTextField(
                value = exercise.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota de este ejercicio") },
                minLines = 1,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun WorkoutSetEditor(
    set: WorkoutSetUi,
    onLoadChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRirChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val repsFocus = remember { FocusRequester() }
    val rirFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var typeMenuOpen by rememberSaveable(set.id) { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (set.completed) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Serie ${set.position + 1}", style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(
                        onClick = { typeMenuOpen = true },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(setTypeLabel(set.type))
                    }
                    DropdownMenu(
                        expanded = typeMenuOpen,
                        onDismissRequest = { typeMenuOpen = false },
                    ) {
                        SetTypes.all.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(setTypeLabel(type)) },
                                onClick = {
                                    onTypeChange(type)
                                    typeMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Text(
                previousLine(set.previous),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stacked = maxWidth < NARROW_SET_EDITOR_WIDTH

                @Composable
                fun LoadField(modifier: Modifier) {
                    OutlinedTextField(
                        value = set.loadText,
                        onValueChange = onLoadChange,
                        modifier = modifier,
                        singleLine = true,
                        label = { Text("Carga (kg)") },
                        isError = set.loadError != null,
                        supportingText = set.loadError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { repsFocus.requestFocus() }),
                    )
                }

                @Composable
                fun RepsField(modifier: Modifier) {
                    OutlinedTextField(
                        value = set.repsText,
                        onValueChange = onRepsChange,
                        modifier = modifier.focusRequester(repsFocus),
                        singleLine = true,
                        label = { Text("Reps") },
                        isError = set.repsError != null,
                        supportingText = set.repsError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { rirFocus.requestFocus() }),
                    )
                }

                @Composable
                fun RirField(modifier: Modifier) {
                    OutlinedTextField(
                        value = set.rirText,
                        onValueChange = onRirChange,
                        modifier = modifier.focusRequester(rirFocus),
                        singleLine = true,
                        label = { Text("RIR") },
                        isError = set.rirError != null,
                        supportingText = set.rirError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    )
                }

                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoadField(Modifier.fillMaxWidth())
                        RepsField(Modifier.fillMaxWidth())
                        RirField(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        LoadField(Modifier.weight(1.25f))
                        RepsField(Modifier.weight(1f))
                        RirField(Modifier.weight(1f))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onToggleComplete,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .semantics {
                            stateDescription = if (set.completed) "Serie completada" else "Serie pendiente"
                        },
                ) {
                    Text(if (set.completed) "Completada ✓" else "Completar serie")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Borrar serie ${set.position + 1}")
                }
            }
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    title: String,
    choices: List<WorkoutExerciseChoice>,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (choices.isEmpty()) {
                Text("No hay otros ejercicios activos disponibles.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(choices, key = WorkoutExerciseChoice::id) { choice ->
                        ListItem(
                            headlineContent = { Text(choice.name) },
                            supportingContent = choice.equipment?.takeIf(String::isNotBlank)?.let { equipment ->
                                { Text(equipment) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(choice.id) }
                                .minimumInteractiveComponentSize(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun targetLine(exercise: WorkoutExerciseUi): String {
    val sets = exercise.targetSetCount?.let { "$it series" } ?: "objetivo heredado"
    val reps = if (exercise.repMin != null && exercise.repMax != null) {
        "${exercise.repMin}–${exercise.repMax} reps"
    } else {
        "reps sin snapshot"
    }
    val rir = exercise.targetRirTenths?.let { "RIR ${rirTenthsToText(it)}" } ?: "RIR libre"
    val rest = exercise.restSeconds?.let { "descanso ${formatDuration(it.toLong())}" } ?: "descanso libre"
    val increment = exercise.loadIncrementGrams?.let { "+${gramsToKilogramsText(it)} kg" } ?: "incremento libre"
    return "TARGET • $sets · $reps · $rir · $rest · $increment"
}

private fun previousLine(previous: PreviousSetUi?): String {
    if (previous == null) return "PREVIOUS • —"
    val rir = previous.rirTenths?.let { " · RIR ${rirTenthsToText(it)}" }.orEmpty()
    return "PREVIOUS • ${gramsToKilogramsText(previous.loadGrams)} kg × ${previous.reps}$rir · ${setTypeLabel(previous.type)}"
}

private fun setTypeLabel(type: String): String = when (type) {
    SetTypes.WARMUP -> "Calentamiento"
    SetTypes.WORK -> "Trabajo"
    SetTypes.DROP -> "Drop"
    SetTypes.FAILURE -> "Fallo"
    else -> type
}

private fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val minutes = safe / 60L
    val seconds = safe % 60L
    return "%d:%02d".format(minutes, seconds)
}
