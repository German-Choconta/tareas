package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressAnalyticsPersistenceTest {
    private lateinit var db: GymTrackerDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<GymTrackerDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        historyDao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun boundedFactsUseHalfOpenStartedAtRangeAndKeepRawEligibilityFacts() = runTest {
        val exercise = ExerciseEntity("synthetic-range-exercise", "Synthetic Range Exercise")
        db.exerciseDao().upsert(exercise)
        seedWorkout("before", exercise.id, 999L, finishedAt = 1_100L, completedAt = 1_050L)
        seedWorkout("start", exercise.id, 1_000L, finishedAt = 1_200L, completedAt = 1_100L)
        seedWorkout("inside-active", exercise.id, 1_500L, finishedAt = null, completedAt = 1_600L)
        seedWorkout("inside-incomplete", exercise.id, 1_700L, finishedAt = 1_900L, completedAt = null)
        seedWorkout("end", exercise.id, 2_000L, finishedAt = 2_200L, completedAt = 2_100L)

        val rows = historyDao.getExercisePrFactsInRange(exercise.id, 1_000L, 2_000L)
        assertEquals(listOf("start", "inside-active", "inside-incomplete"), rows.map { it.workoutId })
        assertTrue(rows.any { it.finishedAt == null })
        assertTrue(rows.any { it.completedAt == null })
    }

    @Test
    fun rangeFactsPreserveArchivedExerciseAndRoutineDeletionSafeHistory() = runTest {
        val exercise = ExerciseEntity("synthetic-range-archived", "Synthetic Range Archived")
        val routine = RoutineEntity("synthetic-range-routine", "Synthetic Range Routine", 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)

        val workout = WorkoutEntity(
            id = "synthetic-range-workout",
            routineId = routine.id,
            title = routine.name,
            startedAt = 10_000L,
            finishedAt = 11_000L,
        )
        val workoutExercise = WorkoutExerciseEntity(
            id = "synthetic-range-we",
            workoutId = workout.id,
            exerciseId = exercise.id,
            position = 0,
        )
        db.workoutDao().upsert(workout)
        db.workoutDao().upsertExercise(workoutExercise)
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "synthetic-range-set",
                workoutExerciseId = workoutExercise.id,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 70_000L,
                reps = 8,
                completedAt = 10_500L,
            ),
        )

        db.exerciseDao().archive(exercise.id)
        db.routineDao().delete(routine.id)

        assertNull(db.workoutDao().getWorkout(workout.id)?.routineId)
        val rows = historyDao.getExercisePrFactsInRange(exercise.id, 9_000L, 12_000L)
        assertEquals("synthetic-range-workout", rows.single().workoutId)
    }

    @Test
    fun multiYearSyntheticHistoryReturnsArbitraryYearWithoutArtificialLimit() = runTest {
        val exercise = ExerciseEntity("synthetic-multi-year", "Synthetic Multi Year")
        db.exerciseDao().upsert(exercise)
        val zone = ZoneId.of("UTC")
        val startDate = LocalDate.of(2018, 1, 1)
        var workoutCount = 0
        repeat(8 * 52) { week ->
            repeat(2) { sessionInWeek ->
                val date = startDate.plusWeeks(week.toLong()).plusDays((sessionInWeek * 3).toLong())
                val startedAt = date.atStartOfDay(zone).toInstant().toEpochMilli()
                seedWorkout(
                    workoutId = "synthetic-years-${workoutCount.toString().padStart(4, '0')}",
                    exerciseId = exercise.id,
                    startedAt = startedAt,
                    finishedAt = startedAt + 3_600_000L,
                    completedAt = startedAt + 1_000L,
                )
                workoutCount++
            }
        }

        val all = historyDao.getExercisePrFacts(exercise.id)
        assertEquals(832, all.size)

        val rangeStart = LocalDate.of(2021, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.of(2022, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val year = historyDao.getExercisePrFactsInRange(exercise.id, rangeStart, rangeEnd)
        assertTrue(year.isNotEmpty())
        assertTrue(year.size < all.size)
        assertTrue(year.all { it.startedAt >= rangeStart && it.startedAt < rangeEnd })
        assertTrue(all.first().startedAt < year.first().startedAt)
        assertTrue(all.last().startedAt > year.last().startedAt)
    }

    @Test
    fun boundedQueryPlanUsesExistingV2IndicesSoSchemaUpgradeIsNotRequired() = runTest {
        val plan = db.useReaderConnection { connection ->
            connection.usePrepared(
                """
                EXPLAIN QUERY PLAN
                SELECT w.id, w.startedAt, we.id, ws.id
                FROM workout_exercise we
                INNER JOIN workout w ON w.id = we.workoutId
                INNER JOIN workout_set ws ON ws.workoutExerciseId = we.id
                WHERE we.exerciseId = 'synthetic-plan-exercise'
                  AND w.startedAt >= 0
                  AND w.startedAt < 9999999999999
                ORDER BY w.startedAt ASC, w.id ASC, we.position ASC, we.id ASC, ws.position ASC, ws.id ASC
                """.trimIndent(),
            ) { statement ->
                buildList {
                    while (statement.step()) add(statement.getText(3))
                }
            }
        }

        assertTrue(plan.any { it.contains("index_workout_exercise_exerciseId") })
        assertTrue(plan.any { it.contains("index_workout_set_workoutExerciseId") })
        assertTrue(plan.none { it.contains("SCAN workout_exercise") && !it.contains("USING INDEX") })
    }

    private suspend fun seedWorkout(
        workoutId: String,
        exerciseId: String,
        startedAt: Long,
        finishedAt: Long?,
        completedAt: Long?,
    ) {
        val workout = WorkoutEntity(
            id = workoutId,
            routineId = null,
            title = "Synthetic Analytics Session $workoutId",
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
        val workoutExercise = WorkoutExerciseEntity(
            id = "we-$workoutId",
            workoutId = workoutId,
            exerciseId = exerciseId,
            position = 0,
        )
        db.workoutDao().upsert(workout)
        db.workoutDao().upsertExercise(workoutExercise)
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "set-$workoutId",
                workoutExerciseId = workoutExercise.id,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 50_000L + (startedAt % 20_000L),
                reps = 8,
                completedAt = completedAt,
            ),
        )
    }
}
