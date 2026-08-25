package com.germanchoconta.gymtracker.ui.management

import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementStateTest {
    @Test
    fun searchMatchesNameOrEquipmentCaseInsensitively() {
        val exercises = listOf(
            ExerciseEntity(id = "bench", name = "Incline Bench Press", equipment = "Barbell"),
            ExerciseEntity(id = "curl", name = "Hammer Curl", equipment = "Dumbbell"),
        )

        assertEquals(listOf("bench"), filterExercises(exercises, "incline").map { it.id })
        assertEquals(listOf("curl"), filterExercises(exercises, "DUMB").map { it.id })
        assertEquals(exercises, filterExercises(exercises, ""))
    }

    @Test
    fun exactDecimalInputConvertsToIntegerStorageUnits() {
        assertEquals(42_500L, kilogramsToGrams("42.5"))
        assertEquals(2_500L, kilogramsToGrams("2,5"))
        assertEquals(15, parseRirTenths("1.5"))
        assertEquals("42.5", gramsToKilogramsText(42_500L))
        assertEquals("1.5", rirTenthsToText(15))
        assertNull(kilogramsToGrams("1.2345"))
        assertNull(parseRirTenths("1.55"))
    }

    @Test
    fun exerciseValidationRejectsIncompleteOrInvalidProgressionDefaults() {
        val draft = ExerciseEditorDraft(
            id = "exercise",
            isNew = true,
            name = "Bench Press",
            defaultRepMin = "12",
            defaultRepMax = "8",
            defaultTargetRir = "10.1",
            defaultRestSeconds = "-1",
            defaultLoadIncrementKg = "0",
        )

        val errors = ManagementValidation.validateExercise(draft)

        assertTrue(ManagementValidation.REP_MAX in errors)
        assertTrue(ManagementValidation.RIR in errors)
        assertTrue(ManagementValidation.REST in errors)
        assertTrue(ManagementValidation.LOAD_INCREMENT in errors)
    }

    @Test
    fun routineExerciseValidationAcceptsValidTargetsAndRejectsBadRange() {
        val valid = RoutineExerciseDraft(
            id = "row",
            exerciseId = "bench",
            exerciseName = "Bench",
            targetSetCount = "3",
            repMin = "8",
            repMax = "12",
            targetRir = "1.5",
            restSeconds = "120",
            loadIncrementKg = "2.5",
            previousReferenceMode = PreviousReferenceModes.SAME_ROUTINE,
        )
        assertTrue(ManagementValidation.validateRoutineExercise(valid).isEmpty())

        val invalid = valid.copy(
            targetSetCount = "0",
            repMin = "12",
            repMax = "8",
            targetRir = "-0.1",
            restSeconds = "3601",
            loadIncrementKg = "-2.5",
        )
        val errors = ManagementValidation.validateRoutineExercise(invalid)
        assertFalse(errors.isEmpty())
        assertTrue(ManagementValidation.TARGET_SETS in errors)
        assertTrue(ManagementValidation.REP_MAX in errors)
        assertTrue(ManagementValidation.RIR in errors)
        assertTrue(ManagementValidation.REST in errors)
        assertTrue(ManagementValidation.LOAD_INCREMENT in errors)
    }

    @Test
    fun moveItemReordersWithoutMutatingOutOfBounds() {
        val source = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveItem(source, 1, 0))
        assertEquals(listOf("a", "c", "b"), moveItem(source, 1, 2))
        assertEquals(source, moveItem(source, 0, -1))
    }
}
