package com.germanchoconta.gymtracker.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.germanchoconta.gymtracker.wear.protocol.WearPreviousSetSnapshot
import com.germanchoconta.gymtracker.wear.protocol.WearSetSnapshot
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<WearWorkoutViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                WearWorkoutScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refresh,
                    onLoadDown = { viewModel.adjustLoad(-1) },
                    onLoadUp = { viewModel.adjustLoad(1) },
                    onRepsDown = { viewModel.adjustReps(-1) },
                    onRepsUp = { viewModel.adjustReps(1) },
                    onRirDown = { viewModel.adjustRir(-1) },
                    onRirUp = { viewModel.adjustRir(1) },
                    onClearRir = viewModel::clearRir,
                    onComplete = viewModel::completeSet,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startListening()
    }

    override fun onStop() {
        viewModel.stopListening()
        super.onStop()
    }
}

@Composable
fun WearWorkoutScreen(
    uiState: WearWorkoutUiState,
    onRefresh: () -> Unit,
    onLoadDown: () -> Unit,
    onLoadUp: () -> Unit,
    onRepsDown: () -> Unit,
    onRepsUp: () -> Unit,
    onRirDown: () -> Unit,
    onRirUp: () -> Unit,
    onClearRir: () -> Unit,
    onComplete: () -> Unit,
) {
    val active = uiState.activeWorkout
    val current = uiState.current
    val listState = rememberTransformingLazyColumnState()

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(WORKOUT_LIST_TEST_TAG),
                contentPadding = contentPadding,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { SyncStatus(uiState) }
                if (active == null) {
                    item {
                        Text(
                            text = "No active workout",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item { Text("Start or resume on your phone", textAlign = TextAlign.Center) }
                    item {
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("Refresh") }
                    }
                    return@TransformingLazyColumn
                }

                item { Text(active.title, style = MaterialTheme.typography.labelMedium) }
                val restEndsAt = uiState.restTimerEndsAt
                if (restEndsAt != null) item { RestTimer(restEndsAt) }

                if (current == null) {
                    item {
                        Text(
                            "All sets logged",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item { Text("Finish or manage the workout on your phone", textAlign = TextAlign.Center) }
                    item {
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("Refresh") }
                    }
                    return@TransformingLazyColumn
                }

                item {
                    Text(
                        current.exercise.name,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    Text(
                        "Set ${current.set.position + 1}${current.exercise.targetSetCount?.let { " / $it" }.orEmpty()}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                item {
                    Text(
                        "PREVIOUS  ${formatPrevious(current.set.previous)}",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                item {
                    Text(
                        "TARGET  ${formatTarget(current)}",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                item {
                    Text(
                        "TODAY  ${formatToday(current.set)}",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                item {
                    NumericControl(
                        label = "Load",
                        value = "${formatKg(current.set.loadGrams)} kg",
                        onDown = onLoadDown,
                        onUp = onLoadUp,
                    )
                }
                item {
                    NumericControl(
                        label = "Reps",
                        value = current.set.reps.toString(),
                        onDown = onRepsDown,
                        onUp = onRepsUp,
                    )
                }
                item {
                    NumericControl(
                        label = "RIR",
                        value = current.set.rirTenths?.let(::formatRir) ?: "—",
                        onDown = onRirDown,
                        onUp = onRirUp,
                    )
                }
                if (current.set.rirTenths != null) {
                    item {
                        Button(
                            onClick = onClearRir,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Clear RIR") }
                    }
                }
                uiState.validationMessage?.let { message ->
                    item { Text(message, textAlign = TextAlign.Center) }
                }
                item {
                    Button(
                        onClick = onComplete,
                        enabled = current.set.reps > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .semantics { contentDescription = "Complete current set" },
                    ) { Text("Complete set") }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun SyncStatus(uiState: WearWorkoutUiState) {
    val text = when {
        uiState.hasPending && !uiState.phoneReachable -> "Offline • saved on watch"
        uiState.hasPending -> "Syncing ${uiState.pendingOperations.size} change${if (uiState.pendingOperations.size == 1) "" else "s"}"
        uiState.phoneReachable -> "Phone connected"
        else -> "Phone unavailable"
    }
    Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    uiState.conflictMessage?.let {
        Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NumericControl(
    label: String,
    value: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onDown,
            modifier = Modifier.size(52.dp).semantics { contentDescription = "Decrease $label" },
        ) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = onUp,
            modifier = Modifier.size(52.dp).semantics { contentDescription = "Increase $label" },
        ) { Text("+") }
    }
}

@Composable
private fun RestTimer(endsAt: Long) {
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = endsAt) {
        while (value < endsAt) {
            delay(1_000L)
            value = System.currentTimeMillis()
        }
    }
    val remaining = ((endsAt - now).coerceAtLeast(0L) + 999L) / 1_000L
    val minutes = remaining / 60L
    val seconds = remaining % 60L
    Text(
        if (remaining > 0L) "Rest %d:%02d".format(minutes, seconds) else "Rest complete",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { contentDescription = "$remaining seconds rest remaining" },
    )
}

private fun formatPrevious(previous: WearPreviousSetSnapshot?): String = previous?.let {
    "${formatKg(it.loadGrams)} kg × ${it.reps}${it.rirTenths?.let { rir -> " • RIR ${formatRir(rir)}" }.orEmpty()}"
} ?: "—"

private fun formatTarget(current: WearCurrentSetContext): String = buildString {
    val min = current.exercise.repMin
    val max = current.exercise.repMax
    if (min != null && max != null) append("$min–$max reps")
    current.exercise.targetRirTenths?.let {
        if (isNotEmpty()) append(" • ")
        append("RIR ${formatRir(it)}")
    }
    if (isEmpty()) append("—")
}

private fun formatToday(set: WearSetSnapshot): String =
    "${formatKg(set.loadGrams)} kg × ${set.reps}${set.rirTenths?.let { " • RIR ${formatRir(it)}" }.orEmpty()}"

private fun formatKg(grams: Long): String {
    val whole = grams / 1_000L
    val remainder = (grams % 1_000L).toString().padStart(3, '0').trimEnd('0')
    return if (remainder.isEmpty()) whole.toString() else "$whole.$remainder"
}

private fun formatRir(tenths: Int): String =
    if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"

internal const val WORKOUT_LIST_TEST_TAG = "wear-workout-list"
