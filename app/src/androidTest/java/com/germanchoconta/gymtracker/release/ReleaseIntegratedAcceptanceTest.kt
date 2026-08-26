package com.germanchoconta.gymtracker.release

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.data.backup.BackupRepository
import com.germanchoconta.gymtracker.data.backup.BackupValidator
import com.germanchoconta.gymtracker.data.backup.PortableBackupCodec
import com.germanchoconta.gymtracker.data.backup.WorkoutCsvExporter
import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase
import com.germanchoconta.gymtracker.data.local.HistoryRepository
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.RoutineRepository
import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.domain.ProgressAnalyticsEngine
import com.germanchoconta.gymtracker.domain.ProgressionAction
import com.germanchoconta.gymtracker.ui.workout.WorkoutLoggerViewModel
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseIntegratedAcceptanceTest {
    private lateinit var sourceDb: GymTrackerDatabase
    private lateinit var restoreDb: GymTrackerDatabase
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var routineRepository: RoutineRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var backupRepository: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDb = inMemoryDatabase(context)
        restoreDb = inMemoryDatabase(context)
        exerciseRepository = ExerciseRepository(sourceDb.exerciseDao(), sourceDb.muscleDao())
        routineRepository = RoutineRepository(sourceDb.routineDao())
        workoutRepository = WorkoutRepository(sourceDb.workoutDao(), sourceDb.routineDao(), sourceDb.exerciseDao())
        historyRepository = HistoryRepository(sourceDb.historyDao())
        backupRepository = BackupRepository(sourceDb, sourceDb.backupDao())
    }

    @After
    fun tearDown() {
        sourceDb.close()
        restoreDb.close()
    }

    @Test
    fun logCompareUnderstandProgressRoundTripIsIntegratedAndCanonical() = runBlocking {
        val exercise = ExerciseEntity(
            id = "synthetic-rc-exercise",
            name = "Synthetic Integrated Press",
            equipment = "Synthetic Machine",
            defaultRepMin = 6,
            defaultRepMax = 10,
            defaultTargetRirTenths = 20,
            defaultRestSeconds = 90,
            defaultLoadIncrementGrams = 2_500L,
        )
        exerciseRepository.save(exercise)
        exerciseRepository.save(exercise.copy(name = "Synthetic Integrated Press v2"))
        assertEquals("Synthetic Integrated Press v2", exerciseRepository.getById(exercise.id)?.name)

        val routine = RoutineEntity("synthetic-rc-routine", "Synthetic RC Routine", 0)
        val routineExercise = RoutineExerciseEntity(
            id = "synthetic-rc-routine-exercise",
            routineId = routine.id,
            exerciseId = exercise.id,
            position = 0,
            targetSetCount = 1,
            repMin = 6,
            repMax = 10,
            targetRirTenths = 20,
            restSeconds = 90,
            loadIncrementGrams = 2_500L,
            previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
        )
        routineRepository.saveWithExercises(routine, listOf(routineExercise))

        val baseline = requireNotNull(workoutRepository.startFromRoutine(routine.id, 1_000L))
        val baselineExercise = workoutRepository.getExercises(baseline.id).single()
        val baselineSet = workoutRepository.getSets(baselineExercise.id).single()
        assertTrue(workoutRepository.updateSet(baselineSet.id, 40_000L, 10, 20, SetTypes.WORK))
        assertTrue(workoutRepository.setCompleted(baselineSet.id, 1_200L))
        assertTrue(workoutRepository.finishWorkout(baseline.id, 1_300L))

        val baselineHistory = sourceDb.historyDao().observeExercisesWithFinishedHistory().first()
        assertEquals(exercise.id, baselineHistory.single().id)
        val baselineRecords = historyRepository.records(exercise.id)
        assertEquals(40_000L, baselineRecords.heaviestLoad?.fact?.loadGrams)
        assertNotNull(baselineRecords.estimatedOneRepMax)
        val baselineAnalytics = ProgressAnalyticsEngine.calculate(
            historyRepository.prFacts(exercise.id),
            zoneId = ZoneId.of("UTC"),
        )
        assertEquals(1, baselineAnalytics.sessions.size)

        var now = 2_000L
        val logger = WorkoutLoggerViewModel(
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            historyRepository = historyRepository,
            now = { now },
        )
        withTimeout(5_000L) { logger.uiState.first { !it.loading } }
        logger.startRoutine(routine.id)
        val started = withTimeout(5_000L) {
            logger.uiState.first { !it.loading && it.hasActiveWorkout }
        }
        val startedExercise = started.exercises.single()
        val startedSet = startedExercise.sets.single()

        assertEquals(6, startedExercise.repMin)
        assertEquals(10, startedExercise.repMax)
        assertEquals(20, startedExercise.targetRirTenths)
        assertEquals(2_500L, startedExercise.loadIncrementGrams)
        assertEquals(40_000L, startedSet.previous?.loadGrams)
        assertEquals(10, startedSet.previous?.reps)
        assertEquals("", startedSet.loadText)
        assertEquals("", startedSet.repsText)
        assertEquals(ProgressionAction.INCREASE_LOAD, startedSet.progression?.action)
        assertEquals(42_500L, startedSet.progression?.suggestedLoadGrams)

        logger.updateLoad(startedSet.id, "35")
        logger.updateReps(startedSet.id, "7")
        logger.updateRir(startedSet.id, "1.5")
        val autosaved = waitForSet(startedExercise.id) {
            it.loadGrams == 35_000L && it.reps == 7 && it.rirTenths == 15
        }
        assertNull(autosaved.completedAt)

        val recoveredLogger = WorkoutLoggerViewModel(
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            historyRepository = historyRepository,
            now = { now },
        )
        val recovered = withTimeout(5_000L) {
            recoveredLogger.uiState.first { !it.loading && it.hasActiveWorkout }
        }
        val recoveredExercise = recovered.exercises.single()
        val recoveredSet = recoveredExercise.sets.single()
        assertEquals(started.activeWorkoutId, recovered.activeWorkoutId)
        assertEquals("35", recoveredSet.loadText)
        assertEquals("7", recoveredSet.repsText)
        assertEquals("1.5", recoveredSet.rirText)
        assertEquals(ProgressionAction.INCREASE_LOAD, recoveredSet.progression?.action)

        val routineTargetBeforeApply = routineRepository.getExercises(routine.id).single()
        val historyBeforeApply = historyRepository.prFacts(exercise.id)
        recoveredLogger.applySuggestedLoad(recoveredSet.id)
        val applied = waitForSet(recoveredExercise.id) { it.loadGrams == 42_500L }

        assertEquals(7, applied.reps)
        assertEquals(15, applied.rirTenths)
        assertEquals(SetTypes.WORK, applied.type)
        assertNull(applied.completedAt)
        assertEquals(routineTargetBeforeApply, routineRepository.getExercises(routine.id).single())
        assertEquals(historyBeforeApply, historyRepository.prFacts(exercise.id))
        val currentSnapshot = workoutRepository.getWorkoutExercise(recoveredExercise.id)
        assertEquals(6, currentSnapshot?.repMin)
        assertEquals(10, currentSnapshot?.repMax)
        assertEquals(20, currentSnapshot?.targetRirTenths)
        assertEquals(2_500L, currentSnapshot?.loadIncrementGrams)

        now = 2_500L
        recoveredLogger.toggleCompleted(recoveredSet.id)
        val completed = waitForSet(recoveredExercise.id) { it.completedAt == 2_500L }
        assertEquals(42_500L, completed.loadGrams)
        assertEquals(7, completed.reps)

        now = 3_000L
        recoveredLogger.finishConfirmed()
        withTimeout(5_000L) {
            recoveredLogger.uiState.first { !it.loading && !it.hasActiveWorkout }
        }
        val finished = workoutRepository.getWorkout(requireNotNull(recovered.activeWorkoutId))
        assertEquals(3_000L, finished?.finishedAt)
        assertNull(workoutRepository.getActiveWorkout())

        val finishedHistory = sourceDb.historyDao().observeExercisesWithFinishedHistory().first()
        assertEquals(2, finishedHistory.single().sessionCount)
        val finalRecords = historyRepository.records(exercise.id)
        assertEquals(42_500L, finalRecords.heaviestLoad?.fact?.loadGrams)
        assertNotNull(finalRecords.estimatedOneRepMax)
        val finalAnalytics = ProgressAnalyticsEngine.calculate(
            historyRepository.prFacts(exercise.id),
            zoneId = ZoneId.of("UTC"),
        )
        assertEquals(2, finalAnalytics.sessions.size)
        assertEquals(listOf(40_000L, 42_500L), finalAnalytics.sessions.map { it.heaviestLoadGrams })

        val snapshot = backupRepository.snapshot().normalized()
        val encoded = PortableBackupCodec.encode(
            snapshot = snapshot,
            generatedAtEpochMillis = 4_000L,
            appVersion = "1.0.0-rc1-synthetic",
        )
        val decoded = PortableBackupCodec.decode(encoded)
        val preview = BackupValidator.validate(decoded)
        assertEquals(2, preview.workoutCount)
        assertFalse(preview.hasActiveWorkout)

        val restoreRepository = BackupRepository(restoreDb, restoreDb.backupDao())
        restoreRepository.replaceAll(decoded.snapshot)
        assertEquals(snapshot, restoreRepository.snapshot().normalized())

        val csv = WorkoutCsvExporter.encode(snapshot).toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("workout_id,"))
        assertTrue(csv.contains("Synthetic Integrated Press v2"))
        assertTrue(csv.contains("42500"))
        assertFalse(csv.contains("health"))
    }

    private suspend fun waitForSet(
        workoutExerciseId: String,
        predicate: (com.germanchoconta.gymtracker.data.local.WorkoutSetEntity) -> Boolean,
    ) = withTimeout(5_000L) {
        var candidate = workoutRepository.getSets(workoutExerciseId).single()
        while (!predicate(candidate)) {
            delay(10L)
            candidate = workoutRepository.getSets(workoutExerciseId).single()
        }
        candidate
    }

    private fun inMemoryDatabase(context: Context): GymTrackerDatabase =
        Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
}
