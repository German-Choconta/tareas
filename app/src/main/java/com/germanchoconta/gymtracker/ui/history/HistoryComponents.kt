package com.germanchoconta.gymtracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.domain.ExercisePersonalRecords
import com.germanchoconta.gymtracker.domain.PersonalRecordEngine
import com.germanchoconta.gymtracker.domain.PersonalRecordKind
import com.germanchoconta.gymtracker.domain.PreviousSessionComparison
import com.germanchoconta.gymtracker.ui.management.gramsToKilogramsText
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryRecordSummary(records: ExercisePersonalRecords, loading: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Records actuales", style = MaterialTheme.typography.titleMedium)
            if (loading) {
                Text("Calculando desde los sets guardados…")
            } else {
                val heaviest = records.heaviestLoad?.fact?.loadGrams
                val e1rm = records.estimatedOneRepMax?.fact
                    ?.let(PersonalRecordEngine::estimatedOneRepMax)
                    ?.roundedGrams
                val volume = records.highestSessionVolume?.volumeGramReps
                HistoryMetricLine("Carga más alta", heaviest?.let(::formatHistoryKg) ?: "—")
                HistoryMetricLine("Estimated 1RM / e1RM", e1rm?.let(::formatHistoryKg) ?: "—")
                HistoryMetricLine("Mayor volumen de sesión", volume?.let(::formatHistoryVolume) ?: "—")
                Text(
                    "Los records de reps por carga exacta se marcan en su set testigo. " +
                        "e1RM usa Epley solo entre 2–10 reps y no usa RIR: es una estimación, no un 1RM medido. " +
                        "El volumen es descriptivo y no significa automáticamente un mejor entrenamiento.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun HistoryPreviousComparison(comparison: PreviousSessionComparison?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comparación con sesión anterior", style = MaterialTheme.typography.titleMedium)
            if (comparison == null) {
                Text("Se necesitan sesiones terminadas con sets elegibles para comparar.")
            } else {
                val latest = comparison.latest
                val previous = comparison.previous
                Text("Última: ${formatHistoryDate(latest.startedAt)} · ${latest.completedSetCount} sets completados")
                if (previous == null) {
                    Text("No existe una sesión anterior comparable.")
                } else {
                    Text("Anterior: ${formatHistoryDate(previous.startedAt)} · ${previous.completedSetCount} sets completados")
                    HistoryMetricLine(
                        "Carga máxima",
                        "${previous.heaviestLoadGrams?.let(::formatHistoryKg) ?: "—"} → " +
                            (latest.heaviestLoadGrams?.let(::formatHistoryKg) ?: "—"),
                    )
                    HistoryMetricLine(
                        "Volumen",
                        "${formatHistoryVolume(previous.volumeGramReps)} → ${formatHistoryVolume(latest.volumeGramReps)}",
                    )
                }
            }
        }
    }
}

@Composable
internal fun HistorySessionHeader(item: HistoryListItem.SessionHeader) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(formatHistoryDate(item.startedAt), style = MaterialTheme.typography.labelLarge)
            }
            if (item.isCurrentVolumeBest) {
                Text("Record actual · mayor volumen de sesión", style = MaterialTheme.typography.labelMedium)
            }
            if (item.isVolumePrEvent) {
                Text(
                    "Hito histórico · estableció un nuevo máximo de volumen en ese momento",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            item.workoutNotes?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun HistoryRawSetRow(item: HistoryListItem.SetItem) {
    val row = item.row
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Set ${row.setPosition + 1} · ${row.type}", style = MaterialTheme.typography.labelLarge)
                Text(if (row.completedAt == null) "Incompleto" else "Completado", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${formatHistoryKg(row.loadGrams)} × ${row.reps} reps" +
                    (row.rirTenths?.let { " · RIR ${formatHistoryRir(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
            )
            item.estimatedOneRepMaxGrams?.let { estimate ->
                Text(
                    "Estimated 1RM / e1RM: ${formatHistoryKg(estimate)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item.currentBestKinds.sortedBy { it.ordinal }.forEach { kind ->
                Text(currentBestExplanation(kind, row.loadGrams), style = MaterialTheme.typography.labelMedium)
            }
            item.prKinds.sortedBy { it.ordinal }.forEach { kind ->
                Text(historyPrExplanation(kind, row.loadGrams), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HistoryMetricLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun currentBestExplanation(kind: PersonalRecordKind, loadGrams: Long): String = when (kind) {
    PersonalRecordKind.HEAVIEST_LOAD -> "Record actual · carga más pesada"
    PersonalRecordKind.REPS_AT_LOAD -> "Record actual · más reps a exactamente ${formatHistoryKg(loadGrams)}"
    PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> "Record actual · Estimated 1RM / e1RM más alto"
    PersonalRecordKind.EXERCISE_SESSION_VOLUME -> "Record actual · volumen de sesión"
}

private fun historyPrExplanation(kind: PersonalRecordKind, loadGrams: Long): String = when (kind) {
    PersonalRecordKind.HEAVIEST_LOAD -> "Hito histórico · nueva carga máxima en ese momento"
    PersonalRecordKind.REPS_AT_LOAD -> "Hito histórico · nuevas reps máximas a exactamente ${formatHistoryKg(loadGrams)}"
    PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> "Hito histórico · nuevo Estimated 1RM / e1RM en ese momento"
    PersonalRecordKind.EXERCISE_SESSION_VOLUME -> "Hito histórico · nuevo volumen máximo de sesión"
}

private val historyDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

internal fun formatHistoryDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(historyDateFormatter)

private fun formatHistoryKg(grams: Long): String = "${gramsToKilogramsText(grams)} kg"

private fun formatHistoryRir(tenths: Int): String = if (tenths % 10 == 0) {
    (tenths / 10).toString()
} else {
    "${tenths / 10}.${tenths % 10}"
}

private fun formatHistoryVolume(value: BigInteger): String {
    val thousand = BigInteger.valueOf(1_000)
    val parts = value.divideAndRemainder(thousand)
    val remainder = parts[1].abs()
    val decimal = if (remainder == BigInteger.ZERO) {
        ""
    } else {
        ".${remainder.toString().padStart(3, '0').trimEnd('0')}"
    }
    return "${parts[0]}$decimal kg·reps"
}
