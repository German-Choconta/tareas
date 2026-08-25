package com.germanchoconta.gymtracker.ui.workout

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.ui.management.ManagementDestination
import com.germanchoconta.gymtracker.ui.management.RoutineLibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineLaunchHome(
    state: RoutineLibraryUiState,
    onDestinationChange: (ManagementDestination) -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("GymTracker") })
                PrimaryTabRow(selectedTabIndex = ManagementDestination.ROUTINES.ordinal) {
                    Tab(
                        selected = false,
                        onClick = { onDestinationChange(ManagementDestination.EXERCISES) },
                        text = { Text("Ejercicios") },
                    )
                    Tab(
                        selected = true,
                        onClick = {},
                        text = { Text("Rutinas") },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoutine) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        if (state.routines.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Aún no hay rutinas", style = MaterialTheme.typography.titleMedium)
                Text("Crea una rutina con el botón + y agrega ejercicios en orden.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
