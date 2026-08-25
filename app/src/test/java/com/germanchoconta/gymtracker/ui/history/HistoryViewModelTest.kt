package com.germanchoconta.gymtracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.germanchoconta.gymtracker.data.local.HistoryDao
import com.germanchoconta.gymtracker.data.local.HistoryExerciseRow
import com.germanchoconta.gymtracker.data.local.HistoryRepository
import com.germanchoconta.gymtracker.data.local.HistorySetRow
import com.germanchoconta.gymtracker.data.local.PrFactRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restoredSelectedExerciseRehydratesMetricsAndCanBeCleared() = runTest(dispatcher) {
        val exerciseId = "synthetic-restored-exercise"
        val dao = FakeHistoryDao(
            exercises = listOf(
                HistoryExerciseRow(
                    id = exerciseId,
                    name = "Synthetic Restored Exercise",
                    equipment = null,
                    unilateral = false,
                    archived = false,
                    lastStartedAt = 1_000L,
                    sessionCount = 1L,
                ),
            ),
            facts = listOf(
                PrFactRow(
                    workoutId = "synthetic-restored-workout",
                    startedAt = 1_000L,
                    finishedAt = 1_500L,
                    workoutExerciseId = "synthetic-restored-workout-exercise",
                    workoutExercisePosition = 0,
                    workoutSetId = "synthetic-restored-set",
                    setPosition = 0,
                    type = "WORK",
                    loadGrams = 75_000L,
                    reps = 8,
                    rirTenths = null,
                    completedAt = 1_100L,
                ),
            ),
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(HistoryViewModel.SELECTED_EXERCISE_KEY to exerciseId),
        )
        val viewModel = HistoryViewModel(HistoryRepository(dao), savedStateHandle)

        advanceUntilIdle()

        assertEquals(exerciseId, viewModel.uiState.value.selectedExerciseId)
        assertEquals("Synthetic Restored Exercise", viewModel.uiState.value.selectedExercise?.name)
        assertEquals("synthetic-restored-set", viewModel.uiState.value.records.heaviestLoad?.fact?.workoutSetId)
        assertFalse(viewModel.uiState.value.loadingMetrics)

        viewModel.closeExercise()

        assertNull(savedStateHandle.get<String>(HistoryViewModel.SELECTED_EXERCISE_KEY))
        assertNull(viewModel.uiState.value.selectedExerciseId)
    }

    private class FakeHistoryDao(
        private val exercises: List<HistoryExerciseRow>,
        private val facts: List<PrFactRow>,
    ) : HistoryDao {
        override fun observeExercisesWithFinishedHistory(): Flow<List<HistoryExerciseRow>> = flowOf(exercises)

        override fun pageFinishedExerciseHistory(exerciseId: String): PagingSource<Int, HistorySetRow> =
            object : PagingSource<Int, HistorySetRow>() {
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HistorySetRow> =
                    LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

                override fun getRefreshKey(state: PagingState<Int, HistorySetRow>): Int? = null
            }

        override suspend fun getExercisePrFacts(exerciseId: String): List<PrFactRow> = facts
    }
}
