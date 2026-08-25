package com.germanchoconta.gymtracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.germanchoconta.gymtracker.data.local.HistoryExerciseRow
import com.germanchoconta.gymtracker.data.local.HistoryRepository
import com.germanchoconta.gymtracker.data.local.HistorySetRow
import com.germanchoconta.gymtracker.domain.AnalyticsDateRange
import com.germanchoconta.gymtracker.domain.ExercisePersonalRecords
import com.germanchoconta.gymtracker.domain.ExerciseProgressAnalytics
import com.germanchoconta.gymtracker.domain.FrequencyBucketSize
import com.germanchoconta.gymtracker.domain.PersonalRecordEngine
import com.germanchoconta.gymtracker.domain.PersonalRecordKind
import com.germanchoconta.gymtracker.domain.PrSetFact
import com.germanchoconta.gymtracker.domain.PreviousSessionComparison
import com.germanchoconta.gymtracker.domain.ProgressAnalyticsEngine
import com.germanchoconta.gymtracker.domain.ProgressMetric
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HistoryListItem {
    val stableKey: String

    data class SessionHeader(
        val workoutId: String,
        val title: String,
        val startedAt: Long,
        val workoutNotes: String?,
        val isVolumePrEvent: Boolean,
        val isCurrentVolumeBest: Boolean,
    ) : HistoryListItem {
        override val stableKey = "session-$workoutId"
    }

    data class SetItem(
        val row: HistorySetRow,
        val prKinds: Set<PersonalRecordKind>,
        val currentBestKinds: Set<PersonalRecordKind>,
        val estimatedOneRepMaxGrams: Long?,
    ) : HistoryListItem {
        override val stableKey = "set-${row.workoutSetId}"
    }
}

data class HistoryUiState(
    val exercises: List<HistoryExerciseRow> = emptyList(),
    val selectedExerciseId: String? = null,
    val selectedExercise: HistoryExerciseRow? = null,
    val records: ExercisePersonalRecords = ExercisePersonalRecords(),
    val comparison: PreviousSessionComparison? = null,
    val loadingMetrics: Boolean = false,
    val detailSection: HistoryDetailSection = HistoryDetailSection.HISTORY,
    val progress: ProgressUiState = ProgressUiState(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: HistoryRepository,
    private val savedStateHandle: SavedStateHandle,
    private val zoneIdProvider: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_EXERCISE_KEY, null)
    private val metrics = MutableStateFlow(ExercisePersonalRecords())
    private var progressAnalytics = emptyProgressAnalytics()
    private var analyticsJob: Job? = null
    private var analyticsZoneId: ZoneId = zoneIdProvider()

    private val mutableState = MutableStateFlow(
        HistoryUiState(
            selectedExerciseId = selectedId.value,
            detailSection = restoredEnum(DETAIL_SECTION_KEY, HistoryDetailSection.HISTORY),
            progress = restoredProgressState(),
        ),
    )
    val uiState: StateFlow<HistoryUiState> = mutableState.asStateFlow()

    val historyItems = combine(selectedId, metrics) { exerciseId, records -> exerciseId to records }
        .flatMapLatest { (exerciseId, records) ->
            if (exerciseId == null) {
                flowOf(PagingData.empty())
            } else {
                val kindsBySet = PersonalRecordEngine.eventKindsBySet(records)
                val volumePrWorkouts = PersonalRecordEngine.volumePrWorkoutIds(records)
                repository.exerciseHistory(exerciseId).flatMapLatest { rows ->
                    flowOf(
                        rows.map { row ->
                            val fact = row.toPrSetFact()
                            HistoryListItem.SetItem(
                                row = row,
                                prKinds = kindsBySet[row.workoutSetId].orEmpty(),
                                currentBestKinds = currentBestKinds(records, row),
                                estimatedOneRepMaxGrams = PersonalRecordEngine
                                    .estimatedOneRepMax(fact)
                                    ?.roundedGrams,
                            ) as HistoryListItem
                        }.insertSeparators { before, after ->
                            val afterSet = after as? HistoryListItem.SetItem ?: return@insertSeparators null
                            val beforeSet = before as? HistoryListItem.SetItem
                            if (beforeSet == null || beforeSet.row.workoutId != afterSet.row.workoutId) {
                                HistoryListItem.SessionHeader(
                                    workoutId = afterSet.row.workoutId,
                                    title = afterSet.row.workoutTitle,
                                    startedAt = afterSet.row.startedAt,
                                    workoutNotes = afterSet.row.workoutNotes,
                                    isVolumePrEvent = afterSet.row.workoutId in volumePrWorkouts,
                                    isCurrentVolumeBest = records.highestSessionVolume?.workoutId == afterSet.row.workoutId,
                                )
                            } else null
                        },
                    )
                }
            }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            repository.observeExercisesWithFinishedHistory().collect { exercises ->
                val selectedExerciseId = selectedId.value
                mutableState.update { state ->
                    state.copy(
                        exercises = exercises,
                        selectedExerciseId = selectedExerciseId,
                        selectedExercise = exercises.firstOrNull { it.id == selectedExerciseId },
                    )
                }
            }
        }
        selectedId.value?.let(::restoreSelection)
    }

    fun selectExercise(exerciseId: String) {
        savedStateHandle[SELECTED_EXERCISE_KEY] = exerciseId
        prepareSelection(exerciseId)
        loadMetrics(exerciseId)
        loadAnalytics(exerciseId)
    }

    fun closeExercise() {
        analyticsJob?.cancel()
        savedStateHandle[SELECTED_EXERCISE_KEY] = null
        metrics.value = ExercisePersonalRecords()
        progressAnalytics = emptyProgressAnalytics()
        mutableState.value = mutableState.value.copy(
            selectedExerciseId = null,
            selectedExercise = null,
            records = ExercisePersonalRecords(),
            comparison = null,
            loadingMetrics = false,
            progress = mutableState.value.progress.copy(
                loading = false,
                errorMessage = null,
                exactLoads = emptyList(),
                selectedExactLoadGrams = null,
                eligibleSessionCount = 0,
                chart = emptyProgressChart(mutableState.value.progress.metric),
                selectedPointIndex = null,
            ),
        )
    }

    fun selectDetailSection(section: HistoryDetailSection) {
        savedStateHandle[DETAIL_SECTION_KEY] = section.name
        mutableState.update { it.copy(detailSection = section) }
    }

    fun setProgressMetric(metric: ProgressMetric) {
        savedStateHandle[PROGRESS_METRIC_KEY] = metric.name
        mutableState.update { state ->
            val progress = state.progress.copy(metric = metric)
            state.copy(progress = rebuildProgressChart(progress))
        }
    }

    fun setProgressRangeMode(mode: ProgressRangeMode) {
        val current = mutableState.value.progress
        val zone = zoneIdProvider()
        var start = current.customStartDate
        var end = current.customEndDate
        if (mode == ProgressRangeMode.CUSTOM && (start == null || end == null)) {
            val first = progressAnalytics.sessions.firstOrNull()?.startedAt
            val last = progressAnalytics.sessions.lastOrNull()?.startedAt
            val fallback = LocalDate.now(zone)
            start = first?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: fallback
            end = last?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: fallback
            savedStateHandle[PROGRESS_START_EPOCH_DAY_KEY] = start.toEpochDay()
            savedStateHandle[PROGRESS_END_EPOCH_DAY_KEY] = end.toEpochDay()
        }
        savedStateHandle[PROGRESS_RANGE_MODE_KEY] = mode.name
        mutableState.update { state ->
            state.copy(
                progress = state.progress.copy(
                    rangeMode = mode,
                    customStartDate = start,
                    customEndDate = end,
                    errorMessage = null,
                ),
            )
        }
        selectedId.value?.let(::loadAnalytics)
    }

    fun setCustomStartDate(date: LocalDate) {
        savedStateHandle[PROGRESS_START_EPOCH_DAY_KEY] = date.toEpochDay()
        mutableState.update { state -> state.copy(progress = state.progress.copy(customStartDate = date, errorMessage = null)) }
        selectedId.value?.let(::loadAnalytics)
    }

    fun setCustomEndDate(date: LocalDate) {
        savedStateHandle[PROGRESS_END_EPOCH_DAY_KEY] = date.toEpochDay()
        mutableState.update { state -> state.copy(progress = state.progress.copy(customEndDate = date, errorMessage = null)) }
        selectedId.value?.let(::loadAnalytics)
    }

    fun setExactLoad(loadGrams: Long) {
        if (mutableState.value.progress.exactLoads.none { it.loadGrams == loadGrams }) return
        savedStateHandle[PROGRESS_EXACT_LOAD_KEY] = loadGrams
        mutableState.update { state ->
            val progress = state.progress.copy(selectedExactLoadGrams = loadGrams)
            state.copy(progress = rebuildProgressChart(progress))
        }
    }

    fun setFrequencyBucketSize(bucketSize: FrequencyBucketSize) {
        savedStateHandle[PROGRESS_BUCKET_KEY] = bucketSize.name
        mutableState.update { state ->
            val progress = state.progress.copy(frequencyBucketSize = bucketSize)
            state.copy(progress = rebuildProgressChart(progress))
        }
    }

    fun selectPreviousProgressPoint() = moveSelectedProgressPoint(-1)

    fun selectNextProgressPoint() = moveSelectedProgressPoint(1)

    private fun moveSelectedProgressPoint(delta: Int) {
        mutableState.update { state ->
            val points = state.progress.chart.points
            if (points.isEmpty()) return@update state
            val current = state.progress.selectedPointIndex ?: points.lastIndex
            val next = (current + delta).coerceIn(0, points.lastIndex)
            state.copy(progress = state.progress.copy(selectedPointIndex = next))
        }
    }

    private fun restoreSelection(exerciseId: String) {
        prepareSelection(exerciseId)
        loadMetrics(exerciseId)
        loadAnalytics(exerciseId)
    }

    private fun prepareSelection(exerciseId: String) {
        metrics.value = ExercisePersonalRecords()
        progressAnalytics = emptyProgressAnalytics()
        mutableState.update { state ->
            state.copy(
                selectedExerciseId = exerciseId,
                selectedExercise = state.exercises.firstOrNull { it.id == exerciseId },
                loadingMetrics = true,
                records = ExercisePersonalRecords(),
                comparison = null,
                progress = state.progress.copy(
                    loading = true,
                    errorMessage = null,
                    rangeValid = true,
                    exactLoads = emptyList(),
                    eligibleSessionCount = 0,
                    chart = emptyProgressChart(state.progress.metric),
                    selectedPointIndex = null,
                ),
            )
        }
    }

    private fun loadMetrics(exerciseId: String) {
        viewModelScope.launch {
            val facts = repository.prFacts(exerciseId)
            val calculated = PersonalRecordEngine.calculate(facts)
            if (selectedId.value != exerciseId) return@launch
            metrics.value = calculated
            mutableState.update { state ->
                if (state.selectedExerciseId != exerciseId) state else state.copy(
                    records = calculated,
                    comparison = PersonalRecordEngine.previousSessionComparison(facts),
                    loadingMetrics = false,
                )
            }
        }
    }

    private fun loadAnalytics(exerciseId: String) {
        analyticsJob?.cancel()
        val progress = mutableState.value.progress
        val range = progress.activeDateRange()
        if (range == null) {
            mutableState.update { state ->
                state.copy(
                    progress = state.progress.copy(
                        loading = false,
                        rangeValid = false,
                        chart = emptyProgressChart(state.progress.metric),
                        selectedPointIndex = null,
                    ),
                )
            }
            return
        }

        val zoneId = zoneIdProvider()
        val resolution = ProgressAnalyticsEngine.resolveRange(range, zoneId)
        if (!resolution.isValid) {
            mutableState.update { state ->
                state.copy(
                    progress = state.progress.copy(
                        loading = false,
                        rangeValid = false,
                        errorMessage = null,
                        chart = emptyProgressChart(state.progress.metric),
                        selectedPointIndex = null,
                    ),
                )
            }
            return
        }

        mutableState.update { state -> state.copy(progress = state.progress.copy(loading = true, rangeValid = true, errorMessage = null)) }
        analyticsJob = viewModelScope.launch {
            try {
                val facts = repository.analyticsFacts(exerciseId, resolution.bounds)
                val calculated = ProgressAnalyticsEngine.calculate(facts, range, zoneId)
                if (selectedId.value != exerciseId || mutableState.value.progress.activeDateRange() != range) return@launch

                progressAnalytics = calculated
                analyticsZoneId = zoneId
                val current = mutableState.value.progress
                val selectedLoad = current.selectedExactLoadGrams
                    ?.takeIf { load -> calculated.exactLoads.any { it.loadGrams == load } }
                    ?: calculated.defaultExactLoadGrams
                savedStateHandle[PROGRESS_EXACT_LOAD_KEY] = selectedLoad
                val chart = buildProgressChartState(
                    analytics = calculated,
                    metric = current.metric,
                    selectedExactLoadGrams = selectedLoad,
                    bucketSize = current.frequencyBucketSize,
                    range = range,
                    zoneId = zoneId,
                )
                mutableState.update { state ->
                    if (state.selectedExerciseId != exerciseId || state.progress.activeDateRange() != range) state else state.copy(
                        progress = state.progress.copy(
                            loading = false,
                            errorMessage = null,
                            rangeValid = true,
                            exactLoads = calculated.exactLoads,
                            selectedExactLoadGrams = selectedLoad,
                            eligibleSessionCount = calculated.sessions.size,
                            chart = chart,
                            selectedPointIndex = chart.points.lastIndex.takeIf { it >= 0 },
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (selectedId.value == exerciseId) {
                    mutableState.update { state ->
                        state.copy(
                            progress = state.progress.copy(
                                loading = false,
                                errorMessage = "No se pudieron calcular los analytics para este rango.",
                                chart = emptyProgressChart(state.progress.metric),
                                selectedPointIndex = null,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun rebuildProgressChart(progress: ProgressUiState): ProgressUiState {
        val range = progress.activeDateRange() ?: return progress.copy(
            rangeValid = false,
            chart = emptyProgressChart(progress.metric),
            selectedPointIndex = null,
        )
        val chart = buildProgressChartState(
            analytics = progressAnalytics,
            metric = progress.metric,
            selectedExactLoadGrams = progress.selectedExactLoadGrams,
            bucketSize = progress.frequencyBucketSize,
            range = range,
            zoneId = analyticsZoneId,
        )
        return progress.copy(
            rangeValid = true,
            chart = chart,
            selectedPointIndex = chart.points.lastIndex.takeIf { it >= 0 },
        )
    }

    private fun restoredProgressState(): ProgressUiState {
        val mode = restoredEnum(PROGRESS_RANGE_MODE_KEY, ProgressRangeMode.ALL_TIME)
        val start = savedStateHandle.get<Long>(PROGRESS_START_EPOCH_DAY_KEY)?.let(LocalDate::ofEpochDay)
        val end = savedStateHandle.get<Long>(PROGRESS_END_EPOCH_DAY_KEY)?.let(LocalDate::ofEpochDay)
        val metric = restoredEnum(PROGRESS_METRIC_KEY, ProgressMetric.LOAD)
        return ProgressUiState(
            rangeMode = mode,
            customStartDate = start,
            customEndDate = end,
            rangeValid = mode == ProgressRangeMode.ALL_TIME || (start != null && end != null && start <= end),
            metric = metric,
            frequencyBucketSize = restoredEnum(PROGRESS_BUCKET_KEY, FrequencyBucketSize.WEEK),
            selectedExactLoadGrams = savedStateHandle.get<Long>(PROGRESS_EXACT_LOAD_KEY),
            chart = emptyProgressChart(metric),
        )
    }

    private inline fun <reified T : Enum<T>> restoredEnum(key: String, fallback: T): T =
        savedStateHandle.get<String>(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    companion object {
        internal const val SELECTED_EXERCISE_KEY = "history_selected_exercise_id"
        internal const val DETAIL_SECTION_KEY = "history_detail_section"
        internal const val PROGRESS_RANGE_MODE_KEY = "history_progress_range_mode"
        internal const val PROGRESS_START_EPOCH_DAY_KEY = "history_progress_start_epoch_day"
        internal const val PROGRESS_END_EPOCH_DAY_KEY = "history_progress_end_epoch_day"
        internal const val PROGRESS_METRIC_KEY = "history_progress_metric"
        internal const val PROGRESS_EXACT_LOAD_KEY = "history_progress_exact_load_grams"
        internal const val PROGRESS_BUCKET_KEY = "history_progress_frequency_bucket"

        fun factory(repository: HistoryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    require(modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                        "Unsupported ViewModel class: ${modelClass.name}"
                    }
                    return HistoryViewModel(repository, extras.createSavedStateHandle()) as T
                }
            }
    }
}

private fun emptyProgressAnalytics() = ExerciseProgressAnalytics(
    rangeValid = true,
    sessions = emptyList(),
    exactLoads = emptyList(),
    defaultExactLoadGrams = null,
)

private fun HistorySetRow.toPrSetFact() = PrSetFact(
    workoutId = workoutId,
    workoutExerciseId = workoutExerciseId,
    workoutSetId = workoutSetId,
    startedAt = startedAt,
    finishedAt = finishedAt,
    workoutExercisePosition = workoutExercisePosition,
    setPosition = setPosition,
    type = type,
    loadGrams = loadGrams,
    reps = reps,
    rirTenths = rirTenths,
    completedAt = completedAt,
)

private fun currentBestKinds(
    records: ExercisePersonalRecords,
    row: HistorySetRow,
): Set<PersonalRecordKind> = buildSet {
    if (records.heaviestLoad?.fact?.workoutSetId == row.workoutSetId) {
        add(PersonalRecordKind.HEAVIEST_LOAD)
    }
    if (records.repsAtExactLoad[row.loadGrams]?.fact?.workoutSetId == row.workoutSetId) {
        add(PersonalRecordKind.REPS_AT_LOAD)
    }
    if (records.estimatedOneRepMax?.fact?.workoutSetId == row.workoutSetId) {
        add(PersonalRecordKind.ESTIMATED_ONE_REP_MAX)
    }
}
