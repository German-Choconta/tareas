package com.germanchoconta.gymtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity
import com.germanchoconta.gymtracker.ui.management.gramsToKilogramsText
import com.germanchoconta.gymtracker.ui.management.kilogramsToGrams
import com.germanchoconta.gymtracker.ui.management.parseRirTenths
import com.germanchoconta.gymtracker.ui.management.rirTenthsToText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NOTE_AUTOSAVE_DELAY_MS = 250L

data class PreviousSetUi(
    val position: Int,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int?,
    val type: String,
)

data class WorkoutSetUi(
    val id: String,
    val position: Int,
    val loadText: String,
    val repsText: String,
    val rirText: String,
    val type: String,
    val completedAt: Long?,
    val previous: PreviousSetUi? = null,
    val loadError: String? = null,
    val repsError: String? = null,
    val rirError: String? = null,
) {
    val completed: Boolean get() = completedAt != null
}

data class WorkoutExerciseUi(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val notes: String,
    val targetSetCount: Int?,
    val repMin: Int?,
    val repMax: Int?,
    val targetRirTenths: Int?,
    val restSeconds: Int?,
    val loadIncrementGrams: Long?,
    val previousReferenceMode: String,
    val sets: List<WorkoutSetUi>,
)

data class WorkoutExerciseChoice(
    val id: String,
    val name: String,
    val equipment: String?,
)

data class WorkoutLoggerUiState(
    val loading: Boolean = true,
    val activeWorkoutId: String? = null,
    val title: String = "",
    val startedAt: Long = 0L,
    val workoutNotes: String = "",
    val restTimerEndsAt: Long? = null,
    val restTimerWorkoutExerciseId: String? = null,
    val exercises: List<WorkoutExerciseUi> = emptyList(),
    val exerciseChoices: List<WorkoutExerciseChoice> = emptyList(),
    val confirmFinish: Boolean = false,
    val message: String? = null,
) {
    val hasActiveWorkout: Boolean get() = activeWorkoutId != null
    val incompleteSetCount: Int get() = exercises.sumOf { exercise -> exercise.sets.count { !it.completed } }
}

object WorkoutInputValidation {
    fun loadGrams(text: String): Long? {
        if (text.isBlank()) return 0L
        return kilogramsToGrams(text)?.takeIf { it >= 0L }
    }

    fun reps(text: String): Int? {
        if (text.isBlank()) return 0
        return text.toIntOrNull()?.takeIf { it in 0..1000 }
    }

    fun rirTenths(text: String): Int? {
        if (text.isBlank()) return null
        return parseRirTenths(text)
    }

    fun loadError(text: String): String? =
        if (loadGrams(text) == null) "Usa kg válidos (máximo 3 decimales)" else null

    fun repsError(text: String, completing: Boolean = false): String? {
        val value = reps(text) ?: return "Usa 0–1000 repeticiones"
        return if (completing && value <= 0) "Completar requiere al menos 1 repetición" else null
    }

    fun rirError(text: String): String? =
        if (text.isNotBlank() && rirTenths(text) == null) "RIR debe estar entre 0 y 10 en pasos de 0.1" else null
}

fun restSecondsRemaining(endsAt: Long?, now: Long): Long {
    if (endsAt == null) return 0L
    val remainingMs = endsAt - now
    if (remainingMs <= 0L) return 0L
    return (remainingMs + 999L) / 1_000L
}

fun hasMeaningfulIncompleteData(state: WorkoutLoggerUiState): Boolean =
    state.exercises.any { exercise ->
        exercise.sets.any { set ->
            !set.completed && (
                WorkoutInputValidation.loadGrams(set.loadText)?.let { it > 0L } == true ||
                    WorkoutInputValidation.reps(set.repsText)?.let { it > 0 } == true ||
                    set.rirText.isNotBlank() ||
                    set.type != SetTypes.WORK
                )
        }
    }

class WorkoutLoggerViewModel(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutLoggerUiState())
    val uiState: StateFlow<WorkoutLoggerUiState> = _uiState.asStateFlow()

    private var workoutNotesJob: Job? = null
    private val exerciseNotesJobs = mutableMapOf<String, Job>()

    init {
        recoverActiveWorkout()
    }

    fun recoverActiveWorkout() {
        viewModelScope.launch {
            val active = workoutRepository.getActiveWorkout()
            if (active == null) {
                val choices = loadExerciseChoices(emptySet())
                _uiState.value = WorkoutLoggerUiState(loading = false, exerciseChoices = choices)
            } else {
                loadWorkout(active.id)
            }
        }
    }

    fun startRoutine(routineId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val workout = workoutRepository.startFromRoutine(routineId, now())
            if (workout == null) {
                _uiState.update {
                    it.copy(loading = false, message = "La rutina necesita al menos un ejercicio para iniciar.")
                }
            } else {
                loadWorkout(workout.id)
            }
        }
    }

    fun updateLoad(setId: String, text: String) {
        val error = WorkoutInputValidation.loadError(text)
        updateSetUi(setId) { it.copy(loadText = text, loadError = error) }
        val grams = WorkoutInputValidation.loadGrams(text) ?: return
        viewModelScope.launch { workoutRepository.updateSetLoad(setId, grams) }
    }

    fun updateReps(setId: String, text: String) {
        val error = WorkoutInputValidation.repsError(text)
        updateSetUi(setId) { it.copy(repsText = text, repsError = error) }
        val reps = WorkoutInputValidation.reps(text) ?: return
        viewModelScope.launch { workoutRepository.updateSetReps(setId, reps) }
    }

    fun updateRir(setId: String, text: String) {
        val error = WorkoutInputValidation.rirError(text)
        updateSetUi(setId) { it.copy(rirText = text, rirError = error) }
        if (error != null) return
        viewModelScope.launch { workoutRepository.updateSetRir(setId, WorkoutInputValidation.rirTenths(text)) }
    }

    fun updateSetType(setId: String, type: String) {
        if (type !in SetTypes.all) return
        updateSetUi(setId) { it.copy(type = type) }
        viewModelScope.launch { workoutRepository.updateSetType(setId, type) }
    }

    fun toggleCompleted(setId: String) {
        val set = findSet(setId) ?: return
        if (set.completed) {
            viewModelScope.launch {
                if (workoutRepository.setCompleted(setId, null)) loadCurrentWorkout()
            }
            return
        }

        val loadError = WorkoutInputValidation.loadError(set.loadText)
        val repsError = WorkoutInputValidation.repsError(set.repsText, completing = true)
        val rirError = WorkoutInputValidation.rirError(set.rirText)
        updateSetUi(setId) { it.copy(loadError = loadError, repsError = repsError, rirError = rirError) }
        if (loadError != null || repsError != null || rirError != null) return

        val load = requireNotNull(WorkoutInputValidation.loadGrams(set.loadText))
        val reps = requireNotNull(WorkoutInputValidation.reps(set.repsText))
        val rir = WorkoutInputValidation.rirTenths(set.rirText)
        viewModelScope.launch {
            val persisted = workoutRepository.updateSet(set.id, load, reps, rir, set.type)
            if (persisted && workoutRepository.setCompleted(set.id, now())) {
                loadCurrentWorkout()
            }
        }
    }

    fun addSet(workoutExerciseId: String) {
        viewModelScope.launch {
            workoutRepository.addSet(workoutExerciseId)
            loadCurrentWorkout()
        }
    }

    fun removeSet(setId: String, allowCompleted: Boolean = false) {
        viewModelScope.launch {
            if (workoutRepository.removeSet(setId, allowCompleted)) {
                loadCurrentWorkout()
            } else {
                _uiState.update { it.copy(message = "Confirma antes de borrar una serie completada.") }
            }
        }
    }

    fun addExercise(exerciseId: String) {
        val workoutId = _uiState.value.activeWorkoutId ?: return
        viewModelScope.launch {
            workoutRepository.addExercise(workoutId, exerciseId)
            loadCurrentWorkout()
        }
    }

    fun replaceExercise(workoutExerciseId: String, exerciseId: String) {
        viewModelScope.launch {
            if (workoutRepository.replaceExercise(workoutExerciseId, exerciseId)) {
                loadCurrentWorkout()
            } else {
                _uiState.update {
                    it.copy(message = "No se reemplaza un ejercicio con series completadas. Añade el sustituto como ejercicio nuevo.")
                }
            }
        }
    }

    fun updateWorkoutNotes(notes: String) {
        val workoutId = _uiState.value.activeWorkoutId ?: return
        _uiState.update { it.copy(workoutNotes = notes) }
        workoutNotesJob?.cancel()
        workoutNotesJob = viewModelScope.launch {
            delay(NOTE_AUTOSAVE_DELAY_MS)
            workoutRepository.updateWorkoutNotes(workoutId, notes)
        }
    }

    fun updateExerciseNotes(workoutExerciseId: String, notes: String) {
        updateExerciseUi(workoutExerciseId) { it.copy(notes = notes) }
        exerciseNotesJobs.remove(workoutExerciseId)?.cancel()
        exerciseNotesJobs[workoutExerciseId] = viewModelScope.launch {
            delay(NOTE_AUTOSAVE_DELAY_MS)
            workoutRepository.updateWorkoutExerciseNotes(workoutExerciseId, notes)
        }
    }

    fun stopRestTimer() {
        val workoutId = _uiState.value.activeWorkoutId ?: return
        _uiState.update { it.copy(restTimerEndsAt = null, restTimerWorkoutExerciseId = null) }
        viewModelScope.launch { workoutRepository.setRestTimer(workoutId, null, null) }
    }

    fun requestFinish() {
        if (_uiState.value.incompleteSetCount > 0) {
            _uiState.update { it.copy(confirmFinish = true) }
        } else {
            finishConfirmed()
        }
    }

    fun dismissFinishConfirmation() {
        _uiState.update { it.copy(confirmFinish = false) }
    }

    fun finishConfirmed() {
        val workoutId = _uiState.value.activeWorkoutId ?: return
        viewModelScope.launch {
            if (workoutRepository.finishWorkout(workoutId, now())) {
                workoutNotesJob?.join()
                exerciseNotesJobs.values.forEach { it.join() }
                val choices = loadExerciseChoices(emptySet())
                _uiState.value = WorkoutLoggerUiState(loading = false, exerciseChoices = choices)
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun loadCurrentWorkout() {
        val workoutId = _uiState.value.activeWorkoutId ?: return
        loadWorkout(workoutId)
    }

    private suspend fun loadWorkout(workoutId: String) {
        val aggregate = workoutRepository.getAggregate(workoutId)
        if (aggregate == null || aggregate.workout.finishedAt != null) {
            _uiState.value = WorkoutLoggerUiState(
                loading = false,
                exerciseChoices = loadExerciseChoices(emptySet()),
            )
            return
        }

        val workout = aggregate.workout
        val exerciseUi = aggregate.exercises.map { item ->
            val exercise = exerciseRepository.getById(item.exercise.exerciseId)
            val referenceMode = item.exercise.previousReferenceMode ?: PreviousReferenceModes.ANY_WORKOUT
            val previousByPosition = workoutRepository.previousCompletedSets(
                exerciseId = item.exercise.exerciseId,
                referenceMode = referenceMode,
                routineId = workout.routineId,
                beforeStartedAt = workout.startedAt,
            ).associateBy(WorkoutSetEntity::position)
            WorkoutExerciseUi(
                id = item.exercise.id,
                exerciseId = item.exercise.exerciseId,
                exerciseName = exercise?.name ?: "Ejercicio archivado",
                notes = item.exercise.notes.orEmpty(),
                targetSetCount = item.exercise.targetSetCount,
                repMin = item.exercise.repMin,
                repMax = item.exercise.repMax,
                targetRirTenths = item.exercise.targetRirTenths,
                restSeconds = item.exercise.restSeconds,
                loadIncrementGrams = item.exercise.loadIncrementGrams,
                previousReferenceMode = referenceMode,
                sets = item.sets.map { set ->
                    val previous = previousByPosition[set.position]?.let {
                        PreviousSetUi(it.position, it.loadGrams, it.reps, it.rirTenths, it.type)
                    }
                    WorkoutSetUi(
                        id = set.id,
                        position = set.position,
                        loadText = set.loadGrams.takeIf { it != 0L }?.let(::gramsToKilogramsText).orEmpty(),
                        repsText = set.reps.takeIf { it != 0 }?.toString().orEmpty(),
                        rirText = rirTenthsToText(set.rirTenths),
                        type = set.type,
                        completedAt = set.completedAt,
                        previous = previous,
                    )
                },
            )
        }
        val includedExerciseIds = exerciseUi.mapTo(hashSetOf(), WorkoutExerciseUi::exerciseId)
        _uiState.value = WorkoutLoggerUiState(
            loading = false,
            activeWorkoutId = workout.id,
            title = workout.title,
            startedAt = workout.startedAt,
            workoutNotes = workout.notes.orEmpty(),
            restTimerEndsAt = workout.restTimerEndsAt,
            restTimerWorkoutExerciseId = workout.restTimerWorkoutExerciseId,
            exercises = exerciseUi,
            exerciseChoices = loadExerciseChoices(includedExerciseIds),
            message = _uiState.value.message,
        )
    }

    private suspend fun loadExerciseChoices(excludedIds: Set<String>): List<WorkoutExerciseChoice> =
        exerciseRepository.getActive()
            .filterNot { it.id in excludedIds }
            .map { WorkoutExerciseChoice(it.id, it.name, it.equipment) }

    private fun findSet(setId: String): WorkoutSetUi? =
        _uiState.value.exercises.asSequence().flatMap { it.sets.asSequence() }.firstOrNull { it.id == setId }

    private fun updateSetUi(setId: String, transform: (WorkoutSetUi) -> WorkoutSetUi) {
        _uiState.update { state ->
            state.copy(
                exercises = state.exercises.map { exercise ->
                    exercise.copy(sets = exercise.sets.map { set -> if (set.id == setId) transform(set) else set })
                },
            )
        }
    }

    private fun updateExerciseUi(id: String, transform: (WorkoutExerciseUi) -> WorkoutExerciseUi) {
        _uiState.update { state ->
            state.copy(exercises = state.exercises.map { if (it.id == id) transform(it) else it })
        }
    }

    companion object {
        fun factory(
            workoutRepository: WorkoutRepository,
            exerciseRepository: ExerciseRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkoutLoggerViewModel(workoutRepository, exerciseRepository) as T
        }
    }
}
