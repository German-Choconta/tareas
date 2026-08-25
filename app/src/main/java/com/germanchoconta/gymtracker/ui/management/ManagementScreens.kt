package com.germanchoconta.gymtracker.ui.management

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.MuscleRoles
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.RoutineEntity

internal enum class ManagementDestination { EXERCISES, ROUTINES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManagementHome(
    destination: ManagementDestination,
    onDestinationChange: (ManagementDestination) -> Unit,
    exerciseState: ExerciseLibraryUiState,
    routineState: RoutineLibraryUiState,
    onExerciseQueryChange: (String) -> Unit,
    onCreateExercise: () -> Unit,
    onEditExercise: (String) -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("GymTracker") })
                PrimaryTabRow(selectedTabIndex = destination.ordinal) {
                    Tab(
                        selected = destination == ManagementDestination.EXERCISES,
                        onClick = { onDestinationChange(ManagementDestination.EXERCISES) },
                        text = { Text("Ejercicios") },
                    )
                    Tab(
                        selected = destination == ManagementDestination.ROUTINES,
                        onClick = { onDestinationChange(ManagementDestination.ROUTINES) },
                        text = { Text("Rutinas") },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (destination == ManagementDestination.EXERCISES) onCreateExercise else onCreateRoutine,
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        when (destination) {
            ManagementDestination.EXERCISES -> ExerciseLibraryContent(
                state = exerciseState,
                onQueryChange = onExerciseQueryChange,
                onEdit = onEditExercise,
                modifier = Modifier.padding(padding),
            )
            ManagementDestination.ROUTINES -> RoutineLibraryContent(
                routines = routineState.routines,
                onEdit = onEditRoutine,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ExerciseLibraryContent(
    state: ExerciseLibraryUiState,
    onQueryChange: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar por nombre o equipo") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                    }
                }
            } else null,
        )

        if (state.exercises.isEmpty()) {
            EmptyMessage(
                title = if (state.query.isBlank()) "Aún no hay ejercicios" else "No hay coincidencias",
                detail = if (state.query.isBlank()) {
                    "Crea tu primer ejercicio con el botón +."
                } else {
                    "Prueba otro nombre o equipo."
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.exercises, key = ExerciseEntity::id) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            val details = buildList {
                                exercise.equipment?.takeIf(String::isNotBlank)?.let(::add)
                                if (exercise.unilateral) add("Unilateral")
                            }
                            if (details.isNotEmpty()) Text(details.joinToString(" · "))
                        },
                        trailingContent = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(exercise.id) }
                            .minimumInteractiveComponentSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineLibraryContent(
    routines: List<RoutineEntity>,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (routines.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            EmptyMessage("Aún no hay rutinas", "Crea una rutina con el botón + y agrega ejercicios en orden.")
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(routines, key = RoutineEntity::id) { routine ->
                ListItem(
                    headlineContent = { Text(routine.name) },
                    supportingContent = routine.notes?.takeIf(String::isNotBlank)?.let { notes ->
                        { Text(notes, maxLines = 2) }
                    },
                    trailingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(routine.id) }
                        .minimumInteractiveComponentSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseEditorScreen(
    state: ExerciseLibraryUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onUpdate: ((ExerciseEditorDraft) -> ExerciseEditorDraft) -> Unit,
    onSetMuscleRole: (String, String?) -> Unit,
    onArchive: () -> Unit,
) {
    val draft = state.editor ?: return
    var confirmArchive by remember(draft.id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "Nuevo ejercicio" else "Editar ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.saving) { Text("Guardar") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ValidatedField(
                value = draft.name,
                onValueChange = { value -> onUpdate { it.copy(name = value) } },
                label = "Nombre",
                error = state.errors[ManagementValidation.NAME],
            )
            ValidatedField(
                value = draft.equipment,
                onValueChange = { value -> onUpdate { it.copy(equipment = value) } },
                label = "Equipo",
            )
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Unilateral", style = MaterialTheme.typography.titleMedium)
                    Text("Se realiza un lado a la vez", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = draft.unilateral,
                    onCheckedChange = { value -> onUpdate { it.copy(unilateral = value) } },
                )
            }
            ValidatedField(
                value = draft.notes,
                onValueChange = { value -> onUpdate { it.copy(notes = value) } },
                label = "Notas",
                minLines = 2,
            )

            SectionTitle("Músculos")
            Text(
                "Elige primario, secundario o ninguno. Un músculo no puede ocupar ambos roles.",
                style = MaterialTheme.typography.bodySmall,
            )
            MuscleCatalog.all.forEach { muscle ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(muscle.name, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = muscle.id in draft.primaryMuscleIds,
                        onClick = {
                            onSetMuscleRole(
                                muscle.id,
                                if (muscle.id in draft.primaryMuscleIds) null else MuscleRoles.PRIMARY,
                            )
                        },
                        label = { Text("Primario") },
                    )
                    FilterChip(
                        selected = muscle.id in draft.secondaryMuscleIds,
                        onClick = {
                            onSetMuscleRole(
                                muscle.id,
                                if (muscle.id in draft.secondaryMuscleIds) null else MuscleRoles.SECONDARY,
                            )
                        },
                        label = { Text("Sec.") },
                    )
                }
            }

            SectionTitle("Progresión predeterminada")
            Text(
                "Estos valores sirven como punto de partida al añadir el ejercicio a una rutina.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValidatedField(
                    value = draft.defaultRepMin,
                    onValueChange = { value -> onUpdate { it.copy(defaultRepMin = value) } },
                    label = "Reps mín.",
                    error = state.errors[ManagementValidation.REP_MIN],
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                ValidatedField(
                    value = draft.defaultRepMax,
                    onValueChange = { value -> onUpdate { it.copy(defaultRepMax = value) } },
                    label = "Reps máx.",
                    error = state.errors[ManagementValidation.REP_MAX],
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            ValidatedField(
                value = draft.defaultTargetRir,
                onValueChange = { value -> onUpdate { it.copy(defaultTargetRir = value) } },
                label = "RIR objetivo (opcional)",
                error = state.errors[ManagementValidation.RIR],
                keyboardType = KeyboardType.Decimal,
            )
            ValidatedField(
                value = draft.defaultRestSeconds,
                onValueChange = { value -> onUpdate { it.copy(defaultRestSeconds = value) } },
                label = "Descanso en segundos (opcional)",
                error = state.errors[ManagementValidation.REST],
                keyboardType = KeyboardType.Number,
            )
            ValidatedField(
                value = draft.defaultLoadIncrementKg,
                onValueChange = { value -> onUpdate { it.copy(defaultLoadIncrementKg = value) } },
                label = "Incremento de carga en kg (opcional)",
                error = state.errors[ManagementValidation.LOAD_INCREMENT],
                keyboardType = KeyboardType.Decimal,
            )

            if (!draft.isNew) {
                OutlinedButton(
                    onClick = { confirmArchive = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Archivar ejercicio")
                }
                Text(
                    "Archivar lo oculta de la biblioteca sin borrar entrenamientos anteriores.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("¿Archivar ejercicio?") },
            text = { Text("El histórico se conserva. El ejercicio dejará de aparecer en la biblioteca activa.") },
            confirmButton = {
                TextButton(onClick = { confirmArchive = false; onArchive() }) { Text("Archivar") }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineEditorScreen(
    state: RoutineLibraryUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onUpdate: ((RoutineEditorDraft) -> RoutineEditorDraft) -> Unit,
    onAddExercise: (String) -> Unit,
    onUpdateExercise: (String, (RoutineExerciseDraft) -> RoutineExerciseDraft) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onArchive: () -> Unit,
) {
    val draft = state.editor ?: return
    var showExercisePicker by remember(draft.id) { mutableStateOf(false) }
    var editingExerciseId by remember(draft.id) { mutableStateOf<String?>(null) }
    var confirmArchive by remember(draft.id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "Nueva rutina" else "Editar rutina") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.saving) { Text("Guardar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ValidatedField(
                    value = draft.name,
                    onValueChange = { value -> onUpdate { it.copy(name = value) } },
                    label = "Nombre",
                    error = state.errors[ManagementValidation.NAME],
                )
            }
            item {
                ValidatedField(
                    value = draft.notes,
                    onValueChange = { value -> onUpdate { it.copy(notes = value) } },
                    label = "Notas",
                    minLines = 2,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("Ejercicios")
                        Text("El orden aquí será el orden de la rutina.", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { showExercisePicker = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Añadir") }
                }
            }

            if (draft.exercises.isEmpty()) {
                item { EmptyMessage("Rutina vacía", "Añade ejercicios y configura sus objetivos.") }
            } else {
                itemsIndexed(draft.exercises, key = { _, item -> item.id }) { index, item ->
                    RoutineExerciseCard(
                        item = item,
                        index = index,
                        count = draft.exercises.size,
                        onEdit = { editingExerciseId = item.id },
                        onMove = { delta -> onMoveExercise(item.id, delta) },
                        onRemove = { onRemoveExercise(item.id) },
                    )
                }
            }

            if (!draft.isNew) {
                item {
                    OutlinedButton(
                        onClick = { confirmArchive = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Archivar rutina")
                    }
                }
                item {
                    Text(
                        "Archivar la plantilla no elimina entrenamientos realizados con ella.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            exercises = state.availableExercises.filterNot { exercise ->
                draft.exercises.any { it.exerciseId == exercise.id }
            },
            onDismiss = { showExercisePicker = false },
            onPick = { id ->
                onAddExercise(id)
                showExercisePicker = false
            },
        )
    }

    editingExerciseId?.let { id ->
        draft.exercises.firstOrNull { it.id == id }?.let { item ->
            RoutineExerciseEditorDialog(
                draft = item,
                errors = state.errors,
                onDismiss = { editingExerciseId = null },
                onUpdate = { transform -> onUpdateExercise(item.id, transform) },
            )
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("¿Archivar rutina?") },
            text = { Text("La rutina se ocultará de la lista activa. El histórico de entrenamientos se conserva.") },
            confirmButton = {
                TextButton(onClick = { confirmArchive = false; onArchive() }) { Text("Archivar") }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RoutineExerciseCard(
    item: RoutineExerciseDraft,
    index: Int,
    count: Int,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = onEdit).minimumInteractiveComponentSize(),
                ) {
                    Text(item.exerciseName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.targetSetCount} × ${item.repMin}–${item.repMax} · RIR ${item.targetRir.ifBlank { "—" }} · ${item.restSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar objetivos de ${item.exerciseName}")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir ${item.exerciseName}")
                }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar ${item.exerciseName}")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar ${item.exerciseName} de la rutina")
                }
            }
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    exercises: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Añadir ejercicio", style = MaterialTheme.typography.headlineSmall)
                if (exercises.isEmpty()) {
                    EmptyMessage("No hay ejercicios disponibles", "Crea otro ejercicio o quita uno de esta rutina.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp)) {
                        items(exercises, key = ExerciseEntity::id) { exercise ->
                            ListItem(
                                headlineContent = { Text(exercise.name) },
                                supportingContent = exercise.equipment?.let { equipment -> { Text(equipment) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(exercise.id) }
                                    .minimumInteractiveComponentSize(),
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun RoutineExerciseEditorDialog(
    draft: RoutineExerciseDraft,
    errors: Map<String, String>,
    onDismiss: () -> Unit,
    onUpdate: ((RoutineExerciseDraft) -> RoutineExerciseDraft) -> Unit,
) {
    fun error(field: String) = errors["${draft.id}:$field"]

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(draft.exerciseName, style = MaterialTheme.typography.headlineSmall)
                ValidatedField(
                    value = draft.targetSetCount,
                    onValueChange = { value -> onUpdate { it.copy(targetSetCount = value) } },
                    label = "Series objetivo",
                    error = error(ManagementValidation.TARGET_SETS),
                    keyboardType = KeyboardType.Number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ValidatedField(
                        value = draft.repMin,
                        onValueChange = { value -> onUpdate { it.copy(repMin = value) } },
                        label = "Reps mín.",
                        error = error(ManagementValidation.REP_MIN),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    ValidatedField(
                        value = draft.repMax,
                        onValueChange = { value -> onUpdate { it.copy(repMax = value) } },
                        label = "Reps máx.",
                        error = error(ManagementValidation.REP_MAX),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                ValidatedField(
                    value = draft.targetRir,
                    onValueChange = { value -> onUpdate { it.copy(targetRir = value) } },
                    label = "RIR objetivo (opcional)",
                    error = error(ManagementValidation.RIR),
                    keyboardType = KeyboardType.Decimal,
                )
                ValidatedField(
                    value = draft.restSeconds,
                    onValueChange = { value -> onUpdate { it.copy(restSeconds = value) } },
                    label = "Descanso (segundos)",
                    error = error(ManagementValidation.REST),
                    keyboardType = KeyboardType.Number,
                )
                ValidatedField(
                    value = draft.loadIncrementKg,
                    onValueChange = { value -> onUpdate { it.copy(loadIncrementKg = value) } },
                    label = "Incremento de carga (kg)",
                    error = error(ManagementValidation.LOAD_INCREMENT),
                    keyboardType = KeyboardType.Decimal,
                )
                Text("Referencia anterior", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.previousReferenceMode == PreviousReferenceModes.ANY_WORKOUT,
                        onClick = {
                            onUpdate { it.copy(previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT) }
                        },
                        label = { Text("Cualquier sesión") },
                    )
                    FilterChip(
                        selected = draft.previousReferenceMode == PreviousReferenceModes.SAME_ROUTINE,
                        onClick = {
                            onUpdate { it.copy(previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE) }
                        },
                        label = { Text("Misma rutina") },
                    )
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Listo")
                }
            }
        }
    }
}

@Composable
private fun ValidatedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        singleLine = minLines == 1,
    )
}

@Composable
private fun EmptyMessage(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}
