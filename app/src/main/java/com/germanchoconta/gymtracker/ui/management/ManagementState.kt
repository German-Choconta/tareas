package com.germanchoconta.gymtracker.ui.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseMuscleEntity
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.MuscleEntity
import com.germanchoconta.gymtracker.data.local.MuscleRoles
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.RoutineRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MuscleChoice(val id: String, val name: String)

object MuscleCatalog {
    val all = listOf(
        MuscleChoice("chest", "Chest"),
        MuscleChoice("lats", "Lats"),
        MuscleChoice("upper-back", "Upper back"),
        MuscleChoice("traps", "Traps"),
        MuscleChoice("front-delts", "Front delts"),
        MuscleChoice("side-delts", "Side delts"),
        MuscleChoice("rear-delts", "Rear delts"),
        MuscleChoice("biceps", "Biceps"),
        MuscleChoice("triceps", "Triceps"),
        MuscleChoice("forearms", "Forearms"),
        MuscleChoice("quads", "Quads"),
        MuscleChoice("hamstrings", "Hamstrings"),
        MuscleChoice("glutes", "Glutes"),
        MuscleChoice("calves", "Calves"),
        MuscleChoice("adductors", "Adductors"),
        MuscleChoice("abs", "Abs"),
        MuscleChoice("lower-back", "Lower back"),
    )

    fun find(id: String): MuscleChoice? = all.firstOrNull { it.id == id }
}

data class ExerciseEditorDraft(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val equipment: String = "",
    val unilateral: Boolean = false,
    val notes: String = "",
    val defaultRepMin: String = "",
    val defaultRepMax: String = "",
    val defaultTargetRir: String = "",
    val defaultRestSeconds: String = "",
    val defaultLoadIncrementKg: String = "",
    val primaryMuscleIds: Set<String> = emptySet(),
    val secondaryMuscleIds: Set<String> = emptySet(),
)

data class ExerciseLibraryUiState(
    val query: String = "",
    val exercises: List<ExerciseEntity> = emptyList(),
    val editor: ExerciseEditorDraft? = null,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
)

data class RoutineExerciseDraft(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val targetSetCount: String,
    val repMin: String,
    val repMax: String,
    val targetRir: String,
    val restSeconds: String,
    val loadIncrementKg: String,
    val previousReferenceMode: String,
)

data class RoutineEditorDraft(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val notes: String = "",
    val position: Int = 0,
    val exercises: List<RoutineExerciseDraft> = emptyList(),
)

data class RoutineLibraryUiState(
    val routines: List<RoutineEntity> = emptyList(),
    val availableExercises: List<ExerciseEntity> = emptyList(),
    val editor: RoutineEditorDraft? = null,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
)

object ManagementValidation {
    const val NAME = "name"
    const val REP_MIN = "repMin"
    const val REP_MAX = "repMax"
    const val RIR = "rir"
    const val REST = "rest"
    const val LOAD_INCREMENT = "loadIncrement"
    const val TARGET_SETS = "targetSets"

    fun validateExercise(draft: ExerciseEditorDraft): Map<String, String> = buildMap {
        if (draft.name.isBlank()) put(NAME, "Name is required")

        val minBlank = draft.defaultRepMin.isBlank()
        val maxBlank = draft.defaultRepMax.isBlank()
        if (minBlank xor maxBlank) {
            put(REP_MIN, "Set both rep limits or leave both blank")
            put(REP_MAX, "Set both rep limits or leave both blank")
        } else if (!minBlank) {
            val min = draft.defaultRepMin.toIntOrNull()
            val max = draft.defaultRepMax.toIntOrNull()
            if (min == null || min <= 0) put(REP_MIN, "Use a positive whole number")
            if (max == null || max <= 0) put(REP_MAX, "Use a positive whole number")
            if (min != null && max != null && min > 0 && max > 0 && max < min) {
                put(REP_MAX, "Max reps must be at least min reps")
            }
        }

        if (draft.defaultTargetRir.isNotBlank() && parseRirTenths(draft.defaultTargetRir) == null) {
            put(RIR, "RIR must be between 0 and 10 in 0.1 steps")
        }
        if (draft.defaultRestSeconds.isNotBlank()) {
            val value = draft.defaultRestSeconds.toIntOrNull()
            if (value == null || value !in 0..3600) put(REST, "Rest must be 0–3600 seconds")
        }
        if (draft.defaultLoadIncrementKg.isNotBlank()) {
            val grams = kilogramsToGrams(draft.defaultLoadIncrementKg)
            if (grams == null || grams <= 0) put(LOAD_INCREMENT, "Increment must be greater than 0 kg")
        }
    }

    fun validateRoutineExercise(draft: RoutineExerciseDraft): Map<String, String> = buildMap {
        val sets = draft.targetSetCount.toIntOrNull()
        if (sets == null || sets !in 1..30) put(TARGET_SETS, "Target sets must be 1–30")

        val min = draft.repMin.toIntOrNull()
        val max = draft.repMax.toIntOrNull()
        if (min == null || min <= 0) put(REP_MIN, "Use a positive whole number")
        if (max == null || max <= 0) put(REP_MAX, "Use a positive whole number")
        if (min != null && max != null && min > 0 && max > 0 && max < min) {
            put(REP_MAX, "Max reps must be at least min reps")
        }

        if (draft.targetRir.isNotBlank() && parseRirTenths(draft.targetRir) == null) {
            put(RIR, "RIR must be between 0 and 10 in 0.1 steps")
        }
        val rest = draft.restSeconds.toIntOrNull()
        if (rest == null || rest !in 0..3600) put(REST, "Rest must be 0–3600 seconds")

        val grams = kilogramsToGrams(draft.loadIncrementKg)
        if (grams == null || grams <= 0) put(LOAD_INCREMENT, "Increment must be greater than 0 kg")
    }
}

fun kilogramsToGrams(text: String): Long? = try {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    BigDecimal(normalized)
        .multiply(BigDecimal(1000))
        .setScale(0, RoundingMode.UNNECESSARY)
        .longValueExact()
} catch (_: ArithmeticException) {
    null
} catch (_: NumberFormatException) {
    null
}

fun gramsToKilogramsText(grams: Long?): String = grams?.let {
    BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString()
}.orEmpty()

fun parseRirTenths(text: String): Int? = try {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val tenths = BigDecimal(normalized)
        .multiply(BigDecimal.TEN)
        .setScale(0, RoundingMode.UNNECESSARY)
        .intValueExact()
    tenths.takeIf { it in 0..100 }
} catch (_: ArithmeticException) {
    null
} catch (_: NumberFormatException) {
    null
}

fun rirTenthsToText(rirTenths: Int?): String = rirTenths?.let {
    BigDecimal(it).movePointLeft(1).stripTrailingZeros().toPlainString()
}.orEmpty()

fun filterExercises(exercises: List<ExerciseEntity>, query: String): List<ExerciseEntity> {
    val needle = query.trim()
    if (needle.isEmpty()) return exercises
    return exercises.filter { exercise ->
        exercise.name.contains(needle, ignoreCase = true) ||
            exercise.equipment?.contains(needle, ignoreCase = true) == true
    }
}

fun <T> moveItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

class ExerciseLibraryViewModel(
    private val repository: ExerciseRepository,
) : ViewModel() {
    private var allExercises: List<ExerciseEntity> = emptyList()
    private val _uiState = MutableStateFlow(ExerciseLibraryUiState())
    val uiState: StateFlow<ExerciseLibraryUiState> = _uiState.asStateFlow()

    init {
        repository.observeActive()
            .onEach { exercises ->
                allExercises = exercises
                _uiState.update { state ->
                    state.copy(exercises = filterExercises(exercises, state.query))
                }
            }
            .launchIn(viewModelScope)
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query, exercises = filterExercises(allExercises, query)) }
    }

    fun startCreate() {
        _uiState.update {
            it.copy(
                editor = ExerciseEditorDraft(id = UUID.randomUUID().toString(), isNew = true),
                errors = emptyMap(),
            )
        }
    }

    fun startEdit(id: String) {
        viewModelScope.launch {
            val exercise = repository.getById(id) ?: return@launch
            val assignments = repository.getAssignments(id)
            _uiState.update {
                it.copy(
                    editor = ExerciseEditorDraft(
                        id = exercise.id,
                        isNew = false,
                        name = exercise.name,
                        equipment = exercise.equipment.orEmpty(),
                        unilateral = exercise.unilateral,
                        notes = exercise.notes.orEmpty(),
                        defaultRepMin = exercise.defaultRepMin?.toString().orEmpty(),
                        defaultRepMax = exercise.defaultRepMax?.toString().orEmpty(),
                        defaultTargetRir = rirTenthsToText(exercise.defaultTargetRirTenths),
                        defaultRestSeconds = exercise.defaultRestSeconds?.toString().orEmpty(),
                        defaultLoadIncrementKg = gramsToKilogramsText(exercise.defaultLoadIncrementGrams),
                        primaryMuscleIds = assignments.filter { row -> row.role == MuscleRoles.PRIMARY }
                            .mapTo(linkedSetOf()) { row -> row.muscleId },
                        secondaryMuscleIds = assignments.filter { row -> row.role == MuscleRoles.SECONDARY }
                            .mapTo(linkedSetOf()) { row -> row.muscleId },
                    ),
                    errors = emptyMap(),
                )
            }
        }
    }

    fun updateEditor(transform: (ExerciseEditorDraft) -> ExerciseEditorDraft) {
        _uiState.update { state ->
            state.editor?.let { state.copy(editor = transform(it), errors = emptyMap()) } ?: state
        }
    }

    fun setMuscleRole(muscleId: String, role: String?) {
        updateEditor { draft ->
            val primary = draft.primaryMuscleIds.toMutableSet().apply { remove(muscleId) }
            val secondary = draft.secondaryMuscleIds.toMutableSet().apply { remove(muscleId) }
            when (role) {
                MuscleRoles.PRIMARY -> primary += muscleId
                MuscleRoles.SECONDARY -> secondary += muscleId
            }
            draft.copy(primaryMuscleIds = primary, secondaryMuscleIds = secondary)
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editor = null, errors = emptyMap(), saving = false) }
    }

    fun saveEditor() {
        val draft = _uiState.value.editor ?: return
        val errors = ManagementValidation.validateExercise(draft)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        _uiState.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            val exercise = ExerciseEntity(
                id = draft.id,
                name = draft.name.trim(),
                equipment = draft.equipment.trim().ifEmpty { null },
                unilateral = draft.unilateral,
                notes = draft.notes.trim().ifEmpty { null },
                archived = false,
                defaultRepMin = draft.defaultRepMin.toIntOrNull(),
                defaultRepMax = draft.defaultRepMax.toIntOrNull(),
                defaultTargetRirTenths = draft.defaultTargetRir.takeIf { it.isNotBlank() }?.let(::parseRirTenths),
                defaultRestSeconds = draft.defaultRestSeconds.toIntOrNull(),
                defaultLoadIncrementGrams = draft.defaultLoadIncrementKg.takeIf { it.isNotBlank() }?.let(::kilogramsToGrams),
            )
            val selectedIds = draft.primaryMuscleIds + draft.secondaryMuscleIds
            val muscles = selectedIds.mapNotNull(MuscleCatalog::find)
                .map { MuscleEntity(it.id, it.name) }
            val links = buildList {
                draft.primaryMuscleIds.forEach { add(ExerciseMuscleEntity(draft.id, it, MuscleRoles.PRIMARY)) }
                draft.secondaryMuscleIds.forEach { add(ExerciseMuscleEntity(draft.id, it, MuscleRoles.SECONDARY)) }
            }
            repository.saveWithMuscles(exercise, muscles, links)
            _uiState.update { it.copy(editor = null, saving = false, errors = emptyMap()) }
        }
    }

    fun archiveEditor() {
        val id = _uiState.value.editor?.id ?: return
        viewModelScope.launch {
            repository.archive(id)
            closeEditor()
        }
    }

    companion object {
        fun factory(repository: ExerciseRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExerciseLibraryViewModel(repository) as T
            }
    }
}

class RoutineLibraryViewModel(
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private var allRoutines: List<RoutineEntity> = emptyList()
    private val _uiState = MutableStateFlow(RoutineLibraryUiState())
    val uiState: StateFlow<RoutineLibraryUiState> = _uiState.asStateFlow()

    init {
        routineRepository.observeActive()
            .onEach { routines ->
                allRoutines = routines
                _uiState.update { it.copy(routines = routines) }
            }
            .launchIn(viewModelScope)
        exerciseRepository.observeActive()
            .onEach { exercises -> _uiState.update { it.copy(availableExercises = exercises) } }
            .launchIn(viewModelScope)
    }

    fun startCreate() {
        _uiState.update {
            it.copy(
                editor = RoutineEditorDraft(
                    id = UUID.randomUUID().toString(),
                    isNew = true,
                    position = (allRoutines.maxOfOrNull(RoutineEntity::position) ?: -1) + 1,
                ),
                errors = emptyMap(),
            )
        }
    }

    fun startEdit(id: String) {
        viewModelScope.launch {
            val routine = routineRepository.getById(id) ?: return@launch
            val persisted = routineRepository.getExercises(id)
            val drafts = persisted.map { item ->
                val exercise = exerciseRepository.getById(item.exerciseId)
                RoutineExerciseDraft(
                    id = item.id,
                    exerciseId = item.exerciseId,
                    exerciseName = exercise?.name ?: "Archived exercise",
                    targetSetCount = item.targetSetCount.toString(),
                    repMin = item.repMin.toString(),
                    repMax = item.repMax.toString(),
                    targetRir = rirTenthsToText(item.targetRirTenths),
                    restSeconds = item.restSeconds.toString(),
                    loadIncrementKg = gramsToKilogramsText(item.loadIncrementGrams),
                    previousReferenceMode = item.previousReferenceMode,
                )
            }
            _uiState.update {
                it.copy(
                    editor = RoutineEditorDraft(
                        id = routine.id,
                        isNew = false,
                        name = routine.name,
                        notes = routine.notes.orEmpty(),
                        position = routine.position,
                        exercises = drafts,
                    ),
                    errors = emptyMap(),
                )
            }
        }
    }

    fun updateEditor(transform: (RoutineEditorDraft) -> RoutineEditorDraft) {
        _uiState.update { state ->
            state.editor?.let { state.copy(editor = transform(it), errors = emptyMap()) } ?: state
        }
    }

    fun addExercise(exerciseId: String) {
        val exercise = _uiState.value.availableExercises.firstOrNull { it.id == exerciseId } ?: return
        updateEditor { editor ->
            if (editor.exercises.any { it.exerciseId == exerciseId }) return@updateEditor editor
            editor.copy(
                exercises = editor.exercises + RoutineExerciseDraft(
                    id = UUID.randomUUID().toString(),
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    targetSetCount = "3",
                    repMin = (exercise.defaultRepMin ?: 8).toString(),
                    repMax = (exercise.defaultRepMax ?: 12).toString(),
                    targetRir = rirTenthsToText(exercise.defaultTargetRirTenths ?: 20),
                    restSeconds = (exercise.defaultRestSeconds ?: 120).toString(),
                    loadIncrementKg = gramsToKilogramsText(exercise.defaultLoadIncrementGrams ?: 2500),
                    previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
                ),
            )
        }
    }

    fun updateRoutineExercise(
        id: String,
        transform: (RoutineExerciseDraft) -> RoutineExerciseDraft,
    ) {
        updateEditor { editor ->
            editor.copy(exercises = editor.exercises.map { if (it.id == id) transform(it) else it })
        }
    }

    fun removeRoutineExercise(id: String) {
        updateEditor { it.copy(exercises = it.exercises.filterNot { item -> item.id == id }) }
    }

    fun moveRoutineExercise(id: String, delta: Int) {
        updateEditor { editor ->
            val from = editor.exercises.indexOfFirst { it.id == id }
            if (from == -1) editor else editor.copy(exercises = moveItem(editor.exercises, from, from + delta))
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editor = null, errors = emptyMap(), saving = false) }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        val errors = linkedMapOf<String, String>()
        if (editor.name.isBlank()) errors[ManagementValidation.NAME] = "Name is required"
        editor.exercises.forEach { item ->
            ManagementValidation.validateRoutineExercise(item).forEach { (field, message) ->
                errors["${item.id}:$field"] = message
            }
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        _uiState.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            val routine = RoutineEntity(
                id = editor.id,
                name = editor.name.trim(),
                position = editor.position,
                notes = editor.notes.trim().ifEmpty { null },
                archived = false,
            )
            val entities = editor.exercises.mapIndexed { index, item ->
                RoutineExerciseEntity(
                    id = item.id,
                    routineId = editor.id,
                    exerciseId = item.exerciseId,
                    position = index,
                    targetSetCount = item.targetSetCount.toInt(),
                    repMin = item.repMin.toInt(),
                    repMax = item.repMax.toInt(),
                    targetRirTenths = item.targetRir.takeIf { it.isNotBlank() }?.let(::parseRirTenths),
                    restSeconds = item.restSeconds.toInt(),
                    loadIncrementGrams = requireNotNull(kilogramsToGrams(item.loadIncrementKg)),
                    previousReferenceMode = item.previousReferenceMode,
                )
            }
            routineRepository.saveWithExercises(routine, entities)
            _uiState.update { it.copy(editor = null, saving = false, errors = emptyMap()) }
        }
    }

    fun archiveEditor() {
        val id = _uiState.value.editor?.id ?: return
        viewModelScope.launch {
            routineRepository.archive(id)
            closeEditor()
        }
    }

    companion object {
        fun factory(
            routineRepository: RoutineRepository,
            exerciseRepository: ExerciseRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RoutineLibraryViewModel(routineRepository, exerciseRepository) as T
        }
    }
}
