package com.germanchoconta.gymtracker.ui.history

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.germanchoconta.gymtracker.data.local.HistoryExerciseRow

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
    onRecoveryContext: () -> Unit = {},
    onManageData: () -> Unit = {},
) {
    if (state.selectedExerciseId == null) {
        HistoryLibrary(
            exercises = state.exercises,
            onExerciseClick = viewModel::selectExercise,
            onRecoveryContext = onRecoveryContext,
            onManageData = onManageData,
        )
    } else {
        HistoryDetail(state, viewModel, viewModel::closeExercise)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryLibrary(
    exercises: List<HistoryExerciseRow>,
    onExerciseClick: (String) -> Unit,
    onRecoveryContext: () -> Unit,
    onManageData: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                actions = {
                    IconButton(onClick = onRecoveryContext) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Abrir contexto de recuperación")
                    }
                    IconButton(onClick = onManageData) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Gestionar backup y exportaciones")
                    }
                },
            )
        },
    ) { padding ->
        if (exercises.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Aún no hay historial terminado", style = MaterialTheme.typography.titleMedium)
                Text("Finaliza un workout para que sus sets aparezcan aquí. No hay una ventana artificial de historial.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(exercises, key = HistoryExerciseRow::id) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            val archived = if (exercise.archived) " · Archivado" else ""
                            Text("${exercise.sessionCount} sesiones · ${formatHistoryDate(exercise.lastStartedAt)}$archived")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseClick(exercise.id) }
                            .minimumInteractiveComponentSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetail(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedExercise?.name ?: "Historial del ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al historial")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HistoryDetailSectionTabs(
                selected = state.detailSection,
                onSelected = viewModel::selectDetailSection,
            )
            when (state.detailSection) {
                HistoryDetailSection.HISTORY -> HistoryRawHistoryContent(state, viewModel)
                HistoryDetailSection.PROGRESS -> ProgressHistoryContent(state.progress, viewModel)
            }
        }
    }
}

@Composable
internal fun HistoryDetailSectionTabs(
    selected: HistoryDetailSection,
    onSelected: (HistoryDetailSection) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        HistoryDetailSection.entries.forEach { section ->
            Tab(
                selected = selected == section,
                onClick = { onSelected(section) },
                text = {
                    Text(
                        when (section) {
                            HistoryDetailSection.HISTORY -> "Historial"
                            HistoryDetailSection.PROGRESS -> "Progreso"
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun HistoryRawHistoryContent(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
) {
    val historyItems = viewModel.historyItems.collectAsLazyPagingItems()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "records") { HistoryRecordSummary(state.records, state.loadingMetrics) }
        item(key = "comparison") { HistoryPreviousComparison(state.comparison) }
        item(key = "raw-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sets originales", style = MaterialTheme.typography.titleLarge)
                Text("Los records complementan los datos guardados; no los reemplazan.")
            }
        }

        when (historyItems.loadState.refresh) {
            is LoadState.Loading -> item(key = "history-refresh-loading") {
                HistoryLoadingState("Cargando historial…")
            }
            is LoadState.Error -> item(key = "history-refresh-error") {
                HistoryLoadError(onRetry = historyItems::retry)
            }
            is LoadState.NotLoading -> {
                if (historyItems.itemCount == 0) {
                    item(key = "history-empty-detail") {
                        Text("No hay sets guardados para este ejercicio.")
                    }
                } else {
                    items(
                        count = historyItems.itemCount,
                        key = historyItems.itemKey { it.stableKey },
                    ) { index ->
                        when (val item = historyItems[index]) {
                            is HistoryListItem.SessionHeader -> HistorySessionHeader(item)
                            is HistoryListItem.SetItem -> HistoryRawSetRow(item)
                            null -> Unit
                        }
                    }
                }
            }
        }

        when (historyItems.loadState.append) {
            is LoadState.Loading -> item(key = "history-more") {
                HistoryLoadingState("Cargando más historial…")
            }
            is LoadState.Error -> item(key = "history-more-error") {
                HistoryLoadError(
                    message = "No se pudo cargar más historial.",
                    onRetry = historyItems::retry,
                )
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun HistoryLoadingState(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(message)
    }
}

@Composable
private fun HistoryLoadError(
    message: String = "No se pudo cargar el historial.",
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Text("Tus datos siguen en Room. Puedes volver a intentar la lectura sin modificar el historial.")
        Button(
            onClick = onRetry,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun ProgressHistoryContent(
    state: ProgressUiState,
    viewModel: HistoryViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp),
    ) {
        item(key = "progress-analytics") {
            ProgressAnalyticsContent(
                state = state,
                onRangeModeChange = viewModel::setProgressRangeMode,
                onStartDateChange = viewModel::setCustomStartDate,
                onEndDateChange = viewModel::setCustomEndDate,
                onMetricChange = viewModel::setProgressMetric,
                onExactLoadChange = viewModel::setExactLoad,
                onFrequencyBucketChange = viewModel::setFrequencyBucketSize,
                onPreviousPoint = viewModel::selectPreviousProgressPoint,
                onNextPoint = viewModel::selectNextProgressPoint,
            )
        }
    }
}
