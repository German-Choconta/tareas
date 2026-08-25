package com.germanchoconta.gymtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.ui.management.ExerciseLibraryUiState
import com.germanchoconta.gymtracker.ui.management.RoutineLibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseTopLevelScreen(
    state: ExerciseLibraryUiState,
    onQueryChange: (String) -> Unit,
    onCreateExercise: () -> Unit,
    onEditExercise: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ejercicios") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateExercise) {
                Icon(Icons.Default.Add, contentDescription = "Crear ejercicio")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.query.isBlank()) "Aún no hay ejercicios" else "No hay coincidencias",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (state.query.isBlank()) "Crea tu primer ejercicio con el botón +."
                        else "Prueba otro nombre o equipo.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
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
                                .clickable { onEditExercise(exercise.id) }
                                .minimumInteractiveComponentSize(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineTopLevelScreen(
    state: RoutineLibraryUiState,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Rutinas") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoutine) {
                Icon(Icons.Default.Add, contentDescription = "Crear rutina")
            }
        },
    ) { padding ->
        if (state.routines.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Aún no hay rutinas",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                Text("Crea una rutina con el botón + y agrega ejercicios en orden.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            ) {
                items(state.routines, key = RoutineEntity::id) { routine ->
                    ListItem(
                        headlineContent = { Text(routine.name) },
                        supportingContent = routine.notes?.takeIf(String::isNotBlank)?.let { notes ->
                            { Text(notes, maxLines = 2) }
                        },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { onStartRoutine(routine.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text("Iniciar")
                                }
                                IconButton(
                                    onClick = { onEditRoutine(routine.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar ${routine.name}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    )
                }
            }
        }
    }
}
