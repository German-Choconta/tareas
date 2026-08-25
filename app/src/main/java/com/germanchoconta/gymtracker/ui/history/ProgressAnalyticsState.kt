package com.germanchoconta.gymtracker.ui.history

import com.germanchoconta.gymtracker.domain.AnalyticsDateRange
import com.germanchoconta.gymtracker.domain.AnalyticsPoint
import com.germanchoconta.gymtracker.domain.AnalyticsPresentationSampler
import com.germanchoconta.gymtracker.domain.ExactLoadUsage
import com.germanchoconta.gymtracker.domain.ExerciseProgressAnalytics
import com.germanchoconta.gymtracker.domain.FrequencyBucketSize
import com.germanchoconta.gymtracker.domain.ProgressAnalyticsEngine
import com.germanchoconta.gymtracker.domain.ProgressMetric
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryDetailSection {
    HISTORY,
    PROGRESS,
}

enum class ProgressRangeMode {
    ALL_TIME,
    CUSTOM,
}

data class ProgressChartPoint(
    val stableId: String,
    val startedAt: Long? = null,
    val bucketStart: LocalDate? = null,
    val exactValue: BigInteger,
    val denominator: Int = 1,
    val isRecordWitness: Boolean = false,
)

data class ProgressChartState(
    val metric: ProgressMetric,
    val points: List<ProgressChartPoint> = emptyList(),
    val sourcePointCount: Int = 0,
    val unitLabel: String,
    val explanation: String,
)

data class ProgressUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val rangeMode: ProgressRangeMode = ProgressRangeMode.ALL_TIME,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val rangeValid: Boolean = true,
    val metric: ProgressMetric = ProgressMetric.LOAD,
    val frequencyBucketSize: FrequencyBucketSize = FrequencyBucketSize.WEEK,
    val exactLoads: List<ExactLoadUsage> = emptyList(),
    val selectedExactLoadGrams: Long? = null,
    val eligibleSessionCount: Int = 0,
    val chart: ProgressChartState = emptyProgressChart(ProgressMetric.LOAD),
    val selectedPointIndex: Int? = null,
)

internal fun ProgressUiState.activeDateRange(): AnalyticsDateRange? = when (rangeMode) {
    ProgressRangeMode.ALL_TIME -> AnalyticsDateRange.AllTime
    ProgressRangeMode.CUSTOM -> {
        val start = customStartDate
        val end = customEndDate
        if (start == null || end == null) null else AnalyticsDateRange.Custom(start, end)
    }
}

internal fun buildProgressChartState(
    analytics: ExerciseProgressAnalytics,
    metric: ProgressMetric,
    selectedExactLoadGrams: Long?,
    bucketSize: FrequencyBucketSize,
    range: AnalyticsDateRange,
    zoneId: ZoneId,
): ProgressChartState = when (metric) {
    ProgressMetric.LOAD -> trendChart(
        metric = metric,
        points = ProgressAnalyticsEngine.loadTrend(analytics),
        unitLabel = "kg",
        explanation = "Máxima carga exacta de un set elegible en cada workout terminado.",
    )
    ProgressMetric.REPS_AT_EXACT_LOAD -> {
        val load = selectedExactLoadGrams
        trendChart(
            metric = metric,
            points = if (load == null) emptyList() else ProgressAnalyticsEngine.repsTrend(analytics, load),
            unitLabel = "reps",
            explanation = if (load == null) {
                "Selecciona una carga exacta para comparar repeticiones equivalentes."
            } else {
                "Máximas reps por workout hechas a exactamente ${formatExactKilograms(load)} kg. Las sesiones con otra carga no se convierten en cero."
            },
        )
    }
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> trendChart(
        metric = metric,
        points = ProgressAnalyticsEngine.estimatedOneRepMaxTrend(analytics),
        unitLabel = "kg estimados",
        explanation = "Estimated 1RM / e1RM por workout con Epley y sets elegibles de 2–10 reps. RIR no forma parte de la fórmula.",
    )
    ProgressMetric.VOLUME -> trendChart(
        metric = metric,
        points = ProgressAnalyticsEngine.volumeTrend(analytics),
        unitLabel = "kg·reps",
        explanation = "Volumen descriptivo del ejercicio por workout: suma de carga × reps de sets elegibles. No es un score de calidad.",
    )
    ProgressMetric.FREQUENCY -> {
        val frequency = ProgressAnalyticsEngine.frequency(analytics, bucketSize, range, zoneId)
        ProgressChartState(
            metric = metric,
            points = frequency.map { point ->
                ProgressChartPoint(
                    stableId = "frequency-${bucketSize.name}-${point.bucketStart}",
                    bucketStart = point.bucketStart,
                    exactValue = BigInteger.valueOf(point.sessionCount.toLong()),
                )
            },
            sourcePointCount = frequency.size,
            unitLabel = "sesiones",
            explanation = when (bucketSize) {
                FrequencyBucketSize.WEEK -> "Workouts distintos con al menos un set elegible, agrupados por semana calendario desde el lunes."
                FrequencyBucketSize.MONTH -> "Workouts distintos con al menos un set elegible, agrupados por mes calendario."
            },
        )
    }
}

private fun trendChart(
    metric: ProgressMetric,
    points: List<AnalyticsPoint>,
    unitLabel: String,
    explanation: String,
): ProgressChartState {
    val sampled = AnalyticsPresentationSampler.sample(points)
    return ProgressChartState(
        metric = metric,
        points = sampled.map { point ->
            ProgressChartPoint(
                stableId = "${metric.name}-${point.workoutId}",
                startedAt = point.startedAt,
                exactValue = point.exactValue,
                denominator = point.denominator,
                isRecordWitness = point.isRecordWitness,
            )
        },
        sourcePointCount = points.size,
        unitLabel = unitLabel,
        explanation = explanation,
    )
}

internal fun emptyProgressChart(metric: ProgressMetric): ProgressChartState = ProgressChartState(
    metric = metric,
    unitLabel = when (metric) {
        ProgressMetric.LOAD -> "kg"
        ProgressMetric.REPS_AT_EXACT_LOAD -> "reps"
        ProgressMetric.ESTIMATED_ONE_REP_MAX -> "kg estimados"
        ProgressMetric.VOLUME -> "kg·reps"
        ProgressMetric.FREQUENCY -> "sesiones"
    },
    explanation = when (metric) {
        ProgressMetric.LOAD -> "Máxima carga exacta de un set elegible en cada workout terminado."
        ProgressMetric.REPS_AT_EXACT_LOAD -> "Selecciona una carga exacta para comparar repeticiones equivalentes."
        ProgressMetric.ESTIMATED_ONE_REP_MAX -> "Estimated 1RM / e1RM por workout con Epley y sets elegibles de 2–10 reps."
        ProgressMetric.VOLUME -> "Volumen descriptivo del ejercicio por workout; no es un score de calidad."
        ProgressMetric.FREQUENCY -> "Número de workouts distintos con al menos un set elegible."
    },
)

internal fun formatExactKilograms(grams: Long): String {
    val negative = grams < 0
    val absolute = BigInteger.valueOf(grams).abs()
    val thousand = BigInteger.valueOf(1_000L)
    val parts = absolute.divideAndRemainder(thousand)
    val fraction = parts[1].toString().padStart(3, '0').trimEnd('0')
    val sign = if (negative) "-" else ""
    return if (fraction.isEmpty()) "$sign${parts[0]}" else "$sign${parts[0]}.$fraction"
}
