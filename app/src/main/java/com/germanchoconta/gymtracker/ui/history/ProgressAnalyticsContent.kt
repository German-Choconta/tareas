package com.germanchoconta.gymtracker.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.germanchoconta.gymtracker.domain.FrequencyBucketSize
import com.germanchoconta.gymtracker.domain.ProgressMetric
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressAnalyticsContent(
    state: ProgressUiState,
    onRangeModeChange: (ProgressRangeMode) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onMetricChange: (ProgressMetric) -> Unit,
    onExactLoadChange: (Long) -> Unit,
    onFrequencyBucketChange: (FrequencyBucketSize) -> Unit,
    onPreviousPoint: () -> Unit,
    onNextPoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingStartDate by rememberSaveable { mutableStateOf(false) }
    var choosingEndDate by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Progress Analytics", style = MaterialTheme.typography.titleLarge)
            Text(
                "Descripción offline de tu historial terminado. No es coaching ni un score de calidad.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        ProgressRangeControls(
            state = state,
            onRangeModeChange = onRangeModeChange,
            onChooseStartDate = { choosingStartDate = true },
            onChooseEndDate = { choosingEndDate = true },
        )

        MetricSelector(state.metric, onMetricChange)

        if (state.metric == ProgressMetric.REPS_AT_EXACT_LOAD) {
            ExactLoadSelector(
                loads = state.exactLoads.map { it.loadGrams },
                selectedLoad = state.selectedExactLoadGrams,
                onSelected = onExactLoadChange,
            )
        }

        if (state.metric == ProgressMetric.FREQUENCY) {
            FrequencyBucketSelector(state.frequencyBucketSize, onFrequencyBucketChange)
        }

        when {
            !state.rangeValid -> InvalidRangeCard()
            state.loading -> LoadingAnalytics()
            state.errorMessage != null -> ErrorCard(state.errorMessage)
            state.chart.points.isEmpty() -> EmptyAnalyticsCard(state.metric)
            else -> {
                AnalyticsChartCard(state)
                SelectedPointCard(
                    state = state,
                    onPreviousPoint = onPreviousPoint,
                    onNextPoint = onNextPoint,
                )
            }
        }
    }

    if (choosingStartDate) {
        AnalyticsDatePickerDialog(
            initialDate = state.customStartDate,
            onDismiss = { choosingStartDate = false },
            onConfirm = { date ->
                choosingStartDate = false
                onStartDateChange(date)
            },
        )
    }
    if (choosingEndDate) {
        AnalyticsDatePickerDialog(
            initialDate = state.customEndDate,
            onDismiss = { choosingEndDate = false },
            onConfirm = { date ->
                choosingEndDate = false
                onEndDateChange(date)
            },
        )
    }
}

@Composable
private fun ProgressRangeControls(
    state: ProgressUiState,
    onRangeModeChange: (ProgressRangeMode) -> Unit,
    onChooseStartDate: () -> Unit,
    onChooseEndDate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rango", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.rangeMode == ProgressRangeMode.ALL_TIME,
                onClick = { onRangeModeChange(ProgressRangeMode.ALL_TIME) },
                label = { Text("Todo el historial") },
            )
            FilterChip(
                selected = state.rangeMode == ProgressRangeMode.CUSTOM,
                onClick = { onRangeModeChange(ProgressRangeMode.CUSTOM) },
                label = { Text("Personalizado") },
            )
        }
        if (state.rangeMode == ProgressRangeMode.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onChooseStartDate, modifier = Modifier.weight(1f)) {
                    Text("Desde ${state.customStartDate?.formatUiDate() ?: "—"}")
                }
                OutlinedButton(onClick = onChooseEndDate, modifier = Modifier.weight(1f)) {
                    Text("Hasta ${state.customEndDate?.formatUiDate() ?: "—"}")
                }
            }
            Text(
                "Las fechas son inclusivas. Internamente se consulta desde el inicio del primer día hasta el inicio del día posterior al final.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MetricSelector(
    selectedMetric: ProgressMetric,
    onMetricChange: (ProgressMetric) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Métrica", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProgressMetric.entries.forEach { metric ->
                FilterChip(
                    selected = selectedMetric == metric,
                    onClick = { onMetricChange(metric) },
                    label = { Text(metric.label()) },
                )
            }
        }
    }
}

@Composable
private fun ExactLoadSelector(
    loads: List<Long>,
    selectedLoad: Long?,
    onSelected: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carga exacta", style = MaterialTheme.typography.titleMedium)
        if (loads.isEmpty()) {
            Text("No hay cargas elegibles en este rango.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                loads.forEach { load ->
                    FilterChip(
                        selected = selectedLoad == load,
                        onClick = { onSelected(load) },
                        label = { Text("${formatExactKilograms(load)} kg") },
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequencyBucketSelector(
    selected: FrequencyBucketSize,
    onSelected: (FrequencyBucketSize) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Agrupación", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == FrequencyBucketSize.WEEK,
                onClick = { onSelected(FrequencyBucketSize.WEEK) },
                label = { Text("Semana") },
            )
            FilterChip(
                selected = selected == FrequencyBucketSize.MONTH,
                onClick = { onSelected(FrequencyBucketSize.MONTH) },
                label = { Text("Mes") },
            )
        }
    }
}

@Composable
private fun AnalyticsChartCard(state: ProgressUiState) {
    val chart = state.chart
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(chart.metric.label(), style = MaterialTheme.typography.titleMedium)
            Text("Unidad: ${chart.unitLabel}", style = MaterialTheme.typography.labelLarge)
            Text(chart.explanation, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${state.eligibleSessionCount} sesiones elegibles en el rango.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (chart.sourcePointCount > chart.points.size) {
                Text(
                    "Vista reducida: ${chart.points.size} de ${chart.sourcePointCount} puntos. El histórico original no se modifica; se preservan extremos y testigos de progreso.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ProgressVicoChart(chart)
        }
    }
}

@Composable
private fun ProgressVicoChart(chart: ProgressChartState) {
    val points = chart.points
    val temporalAxis = remember(points) { buildProgressTemporalAxis(points) }
    val modelProducer = remember { CartesianChartModelProducer() }
    val bottomAxisFormatter = remember(temporalAxis) {
        object : CartesianValueFormatter {
            override fun format(
                context: CartesianMeasuringContext,
                value: Double,
                verticalAxisPosition: Axis.Position.Vertical?,
            ): CharSequence = temporalAxis.dateAt(value)?.format(CHART_DATE_FORMATTER).orEmpty()
        }
    }

    LaunchedEffect(points, chart.metric, temporalAxis.xValues) {
        modelProducer.runTransaction {
            lineModel {
                series(
                    x = temporalAxis.xValues,
                    y = points.map { it.chartYValue(chart.metric) },
                )
            }
        }
    }

    val semanticSummary = remember(chart) {
        buildString {
            append("Gráfica de ${chart.metric.label()}. ")
            append(chart.explanation)
            append(" ${chart.sourcePointCount} puntos de origen. ")
            append("El eje horizontal conserva el espaciado temporal relativo. ")
            append("Los valores exactos están disponibles en el detalle debajo de la gráfica.")
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clearAndSetSemantics { contentDescription = semanticSummary },
        scrollState = rememberVicoScrollState(),
        zoomState = rememberVicoZoomState(),
    )
}

@Composable
private fun SelectedPointCard(
    state: ProgressUiState,
    onPreviousPoint: () -> Unit,
    onNextPoint: () -> Unit,
) {
    val points = state.chart.points
    val index = (state.selectedPointIndex ?: points.lastIndex).coerceIn(0, points.lastIndex)
    val point = points[index]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Dato exacto", style = MaterialTheme.typography.titleMedium)
            Text(
                "${point.localDate?.formatUiDate() ?: "Fecha no disponible"} · ${point.formatValue(state.chart.metric)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (point.isRecordWitness) {
                Text(
                    "Este punto fue una mejora estricta frente a los puntos anteriores de esta misma serie.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPreviousPoint,
                    enabled = index > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Anterior")
                }
                Button(
                    onClick = onNextPoint,
                    enabled = index < points.lastIndex,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Siguiente")
                }
            }
            Text(
                "Punto ${index + 1} de ${points.size}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun LoadingAnalytics() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InvalidRangeCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Rango inválido", style = MaterialTheme.typography.titleMedium)
            Text("La fecha inicial debe ser anterior o igual a la fecha final.")
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No se pudieron cargar los analytics", style = MaterialTheme.typography.titleMedium)
            Text(message)
        }
    }
}

@Composable
private fun EmptyAnalyticsCard(metric: ProgressMetric) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sin datos para esta vista", style = MaterialTheme.typography.titleMedium)
            Text(
                if (metric == ProgressMetric.REPS_AT_EXACT_LOAD) {
                    "No hay sesiones con la carga exacta seleccionada dentro del rango."
                } else {
                    "No hay puntos elegibles para esta métrica dentro del rango seleccionado."
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialMillis = initialDate
        ?.atStartOfDay(ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun ProgressMetric.label(): String = when (this) {
    ProgressMetric.LOAD -> "Carga"
    ProgressMetric.REPS_AT_EXACT_LOAD -> "Reps a carga"
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> "Estimated 1RM / e1RM"
    ProgressMetric.VOLUME -> "Volumen"
    ProgressMetric.FREQUENCY -> "Frecuencia"
}

private fun ProgressChartPoint.chartYValue(metric: ProgressMetric): Double = when (metric) {
    ProgressMetric.LOAD -> exactValue.toBigDecimal().divide(THOUSAND).toDouble()
    ProgressMetric.REPS_AT_EXACT_LOAD -> exactValue.toDouble()
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> exactValue.toBigDecimal()
        .divide(BigDecimal.valueOf(denominator.toLong()).multiply(THOUSAND), 8, RoundingMode.HALF_UP)
        .toDouble()
    ProgressMetric.VOLUME -> exactValue.toBigDecimal().divide(THOUSAND).toDouble()
    ProgressMetric.FREQUENCY -> exactValue.toDouble()
}

private fun ProgressChartPoint.formatValue(metric: ProgressMetric): String = when (metric) {
    ProgressMetric.LOAD -> "${formatBigIntegerThousands(exactValue)} kg"
    ProgressMetric.REPS_AT_EXACT_LOAD -> "$exactValue reps"
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> {
        val kilograms = exactValue.toBigDecimal()
            .divide(BigDecimal.valueOf(denominator.toLong()).multiply(THOUSAND), 1, RoundingMode.HALF_UP)
        "$kilograms kg Estimated 1RM / e1RM"
    }
    ProgressMetric.VOLUME -> "${formatBigIntegerThousands(exactValue)} kg·reps"
    ProgressMetric.FREQUENCY -> "$exactValue sesiones"
}

private fun formatBigIntegerThousands(value: BigInteger): String {
    val negative = value.signum() < 0
    val absolute = value.abs()
    val parts = absolute.divideAndRemainder(BigInteger.valueOf(1_000L))
    val fraction = parts[1].toString().padStart(3, '0').trimEnd('0')
    val sign = if (negative) "-" else ""
    return if (fraction.isEmpty()) "$sign${parts[0]}" else "$sign${parts[0]}.$fraction"
}

private fun LocalDate.formatUiDate(): String = format(UI_DATE_FORMATTER)

private val THOUSAND = BigDecimal.valueOf(1_000L)
private val UI_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val CHART_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM")
