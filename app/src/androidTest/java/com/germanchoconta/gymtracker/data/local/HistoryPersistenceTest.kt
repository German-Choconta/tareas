package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.executeSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryPersistenceTest {
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
    fun rawHistoryPagesOnlyFinishedWorkoutsAndKeepsIncompleteSetsVisible() = runTest {
        val exercise = ExerciseEntity("synthetic-history-exercise", "Synthetic History Exercise")
        db.exerciseDao().upsert(exercise)
        seedWorkout("finished", exercise.id, 2_000L, finishedAt = 2_500L, completed = true)
        seedWorkout("active", exercise.id, 3_000L, finishedAt = null, completed = true)
        val unfinishedSet = seedWorkout("finished-with-draft", exercise.id, 1_000L, finishedAt = 1_500L, completed = false)

        val page = historyDao.pageFinishedExerciseHistory(exercise.id).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("finished", "finished-with-draft"), page.data.map { it.workoutId })
        assertEquals(unfinishedSet, page.data.last().workoutSetId)
        assertNull(page.data.last().completedAt)
    }

    @Test
    fun sameTimestampHistoryOrderingIsDeterministicByStableIds() = runTest {
        val exercise = ExerciseEntity("synthetic-tie-exercise", "Synthetic Tie Exercise")
        db.exerciseDao().upsert(exercise)
        seedWorkout("a-workout", exercise.id, 1_000L, 1_500L, completed = true)
        seedWorkout("z-workout", exercise.id, 1_000L, 1_500L, completed = true)

        val page = historyDao.pageFinishedExerciseHistory(exercise.id).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("z-workout", "a-workout"), page.data.map { it.workoutId })
    }

    @Test
    fun archivedExerciseAndDeletedRoutineKeepReadableHistory() = runTest {
        val exercise = ExerciseEntity("synthetic-archived-exercise", "Synthetic Archived Exercise")
        val routine = RoutineEntity("synthetic-history-routine", "Synthetic History Routine", 0)
        db.exerciseDao().upsert(exercise)
        db.routineDao().upsert(routine)

        val workout = WorkoutEntity("synthetic-routine-workout", routine.id, routine.name, 1_000L, 1_500L)
        val workoutExercise = WorkoutExerciseEntity("synthetic-routine-we", workout.id, exercise.id, position = 0)
        db.workoutDao().upsert(workout)
        db.workoutDao().upsertExercise(workoutExercise)
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = "synthetic-routine-set",
                workoutExerciseId = workoutExercise.id,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 60_000L,
                reps = 8,
                completedAt = 1_100L,
            ),
        )

        db.exerciseDao().archive(exercise.id)
        db.useWriterConnection { it.executeSQL("DELETE FROM routine WHERE id = '${routine.id}'") }

        assertNull(db.workoutDao().getWorkout(workout.id)?.routineId)
        val historyExercises = historyDao.observeExercisesWithFinishedHistory().first()
        assertEquals(exercise.id, historyExercises.single().id)
        assertTrue(historyExercises.single().archived)
        val page = historyDao.pageFinishedExerciseHistory(exercise.id).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        assertEquals("Synthetic History Routine", page.data.single().workoutTitle)
    }

    @Test
    fun prFactQueryIncludesActiveAndIncompleteFactsSoDomainCanRejectThem() = runTest {
        val exercise = ExerciseEntity("synthetic-pr-source", "Synthetic PR Source")
        db.exerciseDao().upsert(exercise)
        seedWorkout("finished", exercise.id, 1_000L, 1_500L, completed = true)
        seedWorkout("active", exercise.id, 2_000L, null, completed = true)
        seedWorkout("incomplete", exercise.id, 3_000L, 3_500L, completed = false)

        val rows = historyDao.getExercisePrFacts(exercise.id)
        assertEquals(3, rows.size)
        assertTrue(rows.any { it.finishedAt == null })
        assertTrue(rows.any { it.completedAt == null })
    }

    @Test
    fun largeSyntheticHistoryLoadsInBoundedPagesWithoutArtificialWindow() = runTest {
        val exercise = ExerciseEntity("synthetic-large-exercise", "Synthetic Large Exercise")
        db.exerciseDao().upsert(exercise)
        repeat(240) { index ->
            seedWorkout(
                workoutId = "synthetic-large-${index.toString().padStart(3, '0')}",
                exerciseId = exercise.id,
                startedAt = index.toLong() * 1_000L,
                finishedAt = index.toLong() * 1_000L + 500L,
                completed = true,
            )
        }

        val source = historyDao.pageFinishedExerciseHistory(exercise.id)
        val first = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        assertEquals(30, first.data.size)
        assertTrue(first.nextKey != null)

        val second = source.load(
            PagingSource.LoadParams.Append(key = requireNotNull(first.nextKey), loadSize = 30, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        assertEquals(30, second.data.size)
        assertEquals("synthetic-large-239", first.data.first().workoutId)
        assertEquals("synthetic-large-209", second.data.first().workoutId)
    }

    private suspend fun seedWorkout(
        workoutId: String,
        exerciseId: String,
        startedAt: Long,
        finishedAt: Long?,
        completed: Boolean,
    ): String {
        val workout = WorkoutEntity(workoutId, null, "Synthetic Session $workoutId", startedAt, finishedAt)
        val workoutExercise = WorkoutExerciseEntity("we-$workoutId", workout.id, exerciseId, position = 0)
        val setId = "set-$workoutId"
        db.workoutDao().upsert(workout)
        db.workoutDao().upsertExercise(workoutExercise)
        db.workoutDao().upsertSet(
            WorkoutSetEntity(
                id = setId,
                workoutExerciseId = workoutExercise.id,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 50_000L,
                reps = if (completed) 8 else 0,
                completedAt = if (completed) startedAt + 100L else null,
            ),
        )
        return setId
    }
}
