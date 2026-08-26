package com.germanchoconta.gymtracker.ui.workout

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase
import com.germanchoconta.gymtracker.data.local.HistoryRepository
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.data.local.WorkoutEntity
import com.germanchoconta.gymtracker.data.local.WorkoutExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity
import com.germanchoconta.gymtracker.domain.ProgressionAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressionApplicationStateTest {
    private lateinit var db: GymTrackerDatabase
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var historyRepository: HistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        workoutRepository = WorkoutRepository(db.workoutDao(), db.routineDao(), db.exerciseDao())
        exerciseRepository = ExerciseRepository(db.exerciseDao(), db.muscleDao())
        historyRepository = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun applySuggestedLoadPersistsOnlyTodayLoad() = runBlocking {
        val exercise = ExerciseEntity("synthetic-apply-exercise", "Synthetic Apply Exercise")
        val routine = RoutineEntity("synthetic-apply-routine", "Synthetic Apply Routine", 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)
        db.routineDao().upsertExercise(
            RoutineExerciseEntity(
                id = "synthetic-apply-template",
                routineId = routine.id,
                exerciseId = exercise.id,
                position = 0,
                targetSetCount = 1,
                repMin = 8,
                repMax = 12,
                targetRirTenths = 20,
                restSeconds = 90,
                loadIncrementGrams = 2_500L,
                previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
            ),
        )
        seedPreviousWorkSet(exercise.id, routine.id)

        val current = requireNotNull(workoutRepository.startFromRoutine(routine.id, 2_000L))
        val currentExercise = workoutRepository.getExercises(current.id).single()
        val currentSet = workoutRepository.getSets(currentExercise.id).single()
        workoutRepository.updateSetLoad(currentSet.id, 35_000L)
        workoutRepository.updateSetReps(currentSet.id, 7)
        workoutRepository.updateSetRir(currentSet.id, 15)

        val viewModel = WorkoutLoggerViewModel(
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            historyRepository = historyRepository,
            now = { 9_000L },
        )
        val loaded = withTimeout(5_000L) {
            viewModel.uiState.first { !it.loading && it.hasActiveWorkout }
        }
        val uiSet = loaded.exercises.single().sets.single()
        assertEquals(ProgressionAction.INCREASE_LOAD, uiSet.progression?.action)
        assertEquals(42_500L, uiSet.progression?.suggestedLoadGrams)

        viewModel.applySuggestedLoad(currentSet.id)

        val persisted = withTimeout(5_000L) {
            var candidate = workoutRepository.getSets(currentExercise.id).single()
            while (candidate.loadGrams != 42_500L) {
                delay(10L)
                candidate = workoutRepository.getSets(currentExercise.id).single()
            }
            candidate
        }
        assertEquals(42_500L, persisted.loadGrams)
        assertEquals(7, persisted.reps)
        assertEquals(15, persisted.rirTenths)
        assertEquals(SetTypes.WORK, persisted.type)
        assertNull(persisted.completedAt)
        assertEquals(8, currentExercise.repMin)
        assertEquals(12, currentExercise.repMax)
        assertEquals(2_500L, currentExercise.loadIncrementGrams)
    }

    @Test
    fun legacyIncompleteTargetSnapshotProducesNoRecommendationDespiteValidHistory() = runBlocking {
        val exercise = ExerciseEntity("synthetic-legacy-exercise", "Synthetic Legacy Exercise")
        val routine = RoutineEntity("synthetic-legacy-routine", "Synthetic Legacy Routine", 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)
        seedPreviousWorkSet(exercise.id, routine.id)

        val current = WorkoutEntity(
            id = "synthetic-legacy-current",
            routineId = routine.id,
            title = "Synthetic Legacy Current",
            startedAt = 2_000L,
        )
        val currentExercise = WorkoutExerciseEntity(
            id = "synthetic-legacy-current-exercise",
            workoutId = current.id,
            exerciseId = exercise.id,
            position = 0,
            targetSetCount = 1,
            repMin = 8,
            repMax = null,
            targetRirTenths = 20,
            restSeconds = 90,
            loadIncrementGrams = 2_500L,
            previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
        )
        val currentSet = WorkoutSetEntity(
            id = "synthetic-legacy-current-set",
            workoutExerciseId = currentExercise.id,
            position = 0,
            type = SetTypes.WORK,
            loadGrams = 0L,
            reps = 0,
        )
        db.workoutDao().insertWorkoutAggregate(current, listOf(currentExercise), listOf(currentSet))

        val viewModel = WorkoutLoggerViewModel(
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            historyRepository = historyRepository,
            now = { 9_000L },
        )
        val loaded = withTimeout(5_000L) {
            viewModel.uiState.first { !it.loading && it.hasActiveWorkout }
        }
        val uiSet = loaded.exercises.single().sets.single()

        assertNotNull(uiSet.previous)
        assertNull(uiSet.progression)
    }

    private suspend fun seedPreviousWorkSet(exerciseId: String, routineId: String) {
        val previous = WorkoutEntity(
            id = "synthetic-progression-previous-$exerciseId",
            routineId = routineId,
            title = "Synthetic Previous",
            startedAt = 1_000L,
            finishedAt = 1_500L,
        )
        val previousExercise = WorkoutExerciseEntity(
            id = "synthetic-progression-previous-exercise-$exerciseId",
            workoutId = previous.id,
            exerciseId = exerciseId,
            position = 0,
        )
        val previousSet = WorkoutSetEntity(
            id = "synthetic-progression-previous-set-$exerciseId",
            workoutExerciseId = previousExercise.id,
            position = 0,
            type = SetTypes.WORK,
            loadGrams = 40_000L,
            reps = 12,
            rirTenths = 20,
            completedAt = 1_250L,
        )
        db.workoutDao().insertWorkoutAggregate(previous, listOf(previousExercise), listOf(previousSet))
    }
}
