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
import com.germanchoconta.gymtracker.domain.ExercisePersonalRecords
import com.germanchoconta.gymtracker.domain.PersonalRecordEngine
import com.germanchoconta.gymtracker.domain.PersonalRecordKind
import com.germanchoconta.gymtracker.domain.PrSetFact
import com.germanchoconta.gymtracker.domain.PreviousSessionComparison
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: HistoryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_EXERCISE_KEY, null)
    private val metrics = MutableStateFlow(ExercisePersonalRecords())
    private val mutableState = MutableStateFlow(
        HistoryUiState(selectedExerciseId = selectedId.value),
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
    }

    fun closeExercise() {
        savedStateHandle[SELECTED_EXERCISE_KEY] = null
        metrics.value = ExercisePersonalRecords()
        mutableState.value = mutableState.value.copy(
            selectedExerciseId = null,
            selectedExercise = null,
            records = ExercisePersonalRecords(),
            comparison = null,
            loadingMetrics = false,
        )
    }

    private fun restoreSelection(exerciseId: String) {
        prepareSelection(exerciseId)
        loadMetrics(exerciseId)
    }

    private fun prepareSelection(exerciseId: String) {
        metrics.value = ExercisePersonalRecords()
        mutableState.update { state ->
            state.copy(
                selectedExerciseId = exerciseId,
                selectedExercise = state.exercises.firstOrNull { it.id == exerciseId },
                loadingMetrics = true,
                records = ExercisePersonalRecords(),
                comparison = null,
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

    companion object {
        internal const val SELECTED_EXERCISE_KEY = "history_selected_exercise_id"

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
