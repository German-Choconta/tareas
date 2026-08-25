package com.germanchoconta.gymtracker.ui.workout

import com.germanchoconta.gymtracker.data.local.SetTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLoggerStateTest {
    @Test
    fun loadValidationUsesExactIntegerGrams() {
        assertEquals(42_500L, WorkoutInputValidation.loadGrams("42.5"))
        assertEquals(42_500L, WorkoutInputValidation.loadGrams("42,5"))
        assertEquals(42_555L, WorkoutInputValidation.loadGrams("42.555"))
        assertEquals(0L, WorkoutInputValidation.loadGrams(""))
        assertNull(WorkoutInputValidation.loadGrams("42.5555"))
        assertNull(WorkoutInputValidation.loadGrams("-1"))
        assertNull(WorkoutInputValidation.loadGrams("abc"))
    }

    @Test
    fun repsValidationAcceptsFastBlankDraftButCompletionRequiresPositiveValue() {
        assertEquals(0, WorkoutInputValidation.reps(""))
        assertEquals(12, WorkoutInputValidation.reps("12"))
        assertNull(WorkoutInputValidation.reps("-1"))
        assertNull(WorkoutInputValidation.reps("1001"))
        assertNull(WorkoutInputValidation.reps("8.5"))
        assertNull(WorkoutInputValidation.repsError("12", completing = true))
        assertEquals(
            "Completar requiere al menos 1 repetición",
            WorkoutInputValidation.repsError("", completing = true),
        )
    }

    @Test
    fun rirValidationUsesExactTenths() {
        assertEquals(15, WorkoutInputValidation.rirTenths("1.5"))
        assertEquals(15, WorkoutInputValidation.rirTenths("1,5"))
        assertEquals(100, WorkoutInputValidation.rirTenths("10"))
        assertNull(WorkoutInputValidation.rirTenths(""))
        assertNull(WorkoutInputValidation.rirTenths("1.55"))
        assertNull(WorkoutInputValidation.rirTenths("10.1"))
        assertNull(WorkoutInputValidation.rirTenths("-0.1"))
    }

    @Test
    fun restTimerDerivesRemainingTimeFromAbsoluteDeadline() {
        val endsAt = 10_000L
        assertEquals(2L, restSecondsRemaining(endsAt, 8_001L))
        assertEquals(1L, restSecondsRemaining(endsAt, 9_001L))
        assertEquals(0L, restSecondsRemaining(endsAt, 10_000L))
        assertEquals(0L, restSecondsRemaining(endsAt, 25_000L))
        assertEquals(0L, restSecondsRemaining(null, 9_000L))
    }

    @Test
    fun meaningfulIncompleteDataIgnoresUntouchedPlannedSets() {
        val untouched = WorkoutSetUi(
            id = "synthetic-set-a",
            position = 0,
            loadText = "",
            repsText = "",
            rirText = "",
            type = SetTypes.WORK,
            completedAt = null,
        )
        val base = WorkoutLoggerUiState(
            loading = false,
            activeWorkoutId = "synthetic-workout",
            exercises = listOf(
                WorkoutExerciseUi(
                    id = "synthetic-we",
                    exerciseId = "synthetic-exercise",
                    exerciseName = "Synthetic Press",
                    notes = "",
                    targetSetCount = 1,
                    repMin = 8,
                    repMax = 12,
                    targetRirTenths = 20,
                    restSeconds = 90,
                    loadIncrementGrams = 2_500,
                    previousReferenceMode = "ANY_WORKOUT",
                    sets = listOf(untouched),
                ),
            ),
        )

        assertFalse(hasMeaningfulIncompleteData(base))
        val edited = base.copy(
            exercises = base.exercises.map { exercise ->
                exercise.copy(sets = listOf(untouched.copy(loadText = "20")))
            },
        )
        assertTrue(hasMeaningfulIncompleteData(edited))
    }
}
