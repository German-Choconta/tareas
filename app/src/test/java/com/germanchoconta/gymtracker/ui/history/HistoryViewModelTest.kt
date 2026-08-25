package com.germanchoconta.gymtracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.germanchoconta.gymtracker.data.local.HistoryDao
import com.germanchoconta.gymtracker.data.local.HistoryExerciseRow
import com.germanchoconta.gymtracker.data.local.HistoryRepository
import com.germanchoconta.gymtracker.data.local.HistorySetRow
import com.germanchoconta.gymtracker.data.local.PrFactRow
import com.germanchoconta.gymtracker.domain.FrequencyBucketSize
import com.germanchoconta.gymtracker.domain.ProgressMetric
import java.time.LocalDate
import java.time.ZoneId
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val utc = ZoneId.of("UTC")

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
            exercises = listOf(exercise(exerciseId, 1_000L, 1L)),
            facts = listOf(fact("synthetic-restored-workout", 1_000L, 75_000L, 8)),
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(HistoryViewModel.SELECTED_EXERCISE_KEY to exerciseId),
        )
        val viewModel = HistoryViewModel(HistoryRepository(dao), savedStateHandle, zoneIdProvider = { utc })

        advanceUntilIdle()

        assertEquals(exerciseId, viewModel.uiState.value.selectedExerciseId)
        assertEquals("Synthetic $exerciseId", viewModel.uiState.value.selectedExercise?.name)
        assertEquals("set-synthetic-restored-workout", viewModel.uiState.value.records.heaviestLoad?.fact?.workoutSetId)
        assertFalse(viewModel.uiState.value.loadingMetrics)
        assertFalse(viewModel.uiState.value.progress.loading)
        assertEquals(1, viewModel.uiState.value.progress.eligibleSessionCount)
        assertEquals(1, dao.allFactsQueryCount)

        viewModel.closeExercise()

        assertNull(savedStateHandle.get<String>(HistoryViewModel.SELECTED_EXERCISE_KEY))
        assertNull(viewModel.uiState.value.selectedExerciseId)
    }

    @Test
    fun progressSelectionFiltersAndMetricRestoreAcrossViewModelRecreation() = runTest(dispatcher) {
        val exerciseId = "synthetic-progress-restore"
        val jan5 = LocalDate.of(2026, 1, 5).atStartOfDay(utc).toInstant().toEpochMilli()
        val jan20 = LocalDate.of(2026, 1, 20).atStartOfDay(utc).toInstant().toEpochMilli()
        val dao = FakeHistoryDao(
            exercises = listOf(exercise(exerciseId, jan20, 2L)),
            facts = listOf(
                fact("restore-a", jan5, 50_000L, 8),
                fact("restore-b", jan20, 60_000L, 6),
            ),
        )
        val handle = SavedStateHandle(mapOf(HistoryViewModel.SELECTED_EXERCISE_KEY to exerciseId))
        val first = HistoryViewModel(HistoryRepository(dao), handle, zoneIdProvider = { utc })
        advanceUntilIdle()

        first.selectDetailSection(HistoryDetailSection.PROGRESS)
        first.setProgressRangeMode(ProgressRangeMode.CUSTOM)
        advanceUntilIdle()
        first.setCustomStartDate(LocalDate.of(2026, 1, 1))
        advanceUntilIdle()
        first.setCustomEndDate(LocalDate.of(2026, 1, 31))
        advanceUntilIdle()
        first.setProgressMetric(ProgressMetric.FREQUENCY)
        first.setFrequencyBucketSize(FrequencyBucketSize.MONTH)

        val restored = HistoryViewModel(HistoryRepository(dao), handle, zoneIdProvider = { utc })
        advanceUntilIdle()
        val state = restored.uiState.value

        assertEquals(HistoryDetailSection.PROGRESS, state.detailSection)
        assertEquals(ProgressRangeMode.CUSTOM, state.progress.rangeMode)
        assertEquals(LocalDate.of(2026, 1, 1), state.progress.customStartDate)
        assertEquals(LocalDate.of(2026, 1, 31), state.progress.customEndDate)
        assertEquals(ProgressMetric.FREQUENCY, state.progress.metric)
        assertEquals(FrequencyBucketSize.MONTH, state.progress.frequencyBucketSize)
        assertTrue(state.progress.rangeValid)
        assertEquals(2, state.progress.eligibleSessionCount)
        assertEquals(1, state.progress.chart.points.size)
        assertEquals(2L, state.progress.chart.points.single().exactValue.toLong())
    }

    @Test
    fun invalidCustomRangeDoesNotIssueBoundedRoomQuery() = runTest(dispatcher) {
        val exerciseId = "synthetic-invalid-range"
        val jan5 = LocalDate.of(2026, 1, 5).atStartOfDay(utc).toInstant().toEpochMilli()
        val jan20 = LocalDate.of(2026, 1, 20).atStartOfDay(utc).toInstant().toEpochMilli()
        val dao = FakeHistoryDao(
            exercises = listOf(exercise(exerciseId, jan20, 2L)),
            facts = listOf(
                fact("invalid-a", jan5, 50_000L, 8),
                fact("invalid-b", jan20, 55_000L, 8),
            ),
        )
        val viewModel = HistoryViewModel(
            HistoryRepository(dao),
            SavedStateHandle(mapOf(HistoryViewModel.SELECTED_EXERCISE_KEY to exerciseId)),
            zoneIdProvider = { utc },
        )
        advanceUntilIdle()

        viewModel.setProgressRangeMode(ProgressRangeMode.CUSTOM)
        advanceUntilIdle()
        val boundedQueriesBeforeInvalidRange = dao.rangeQueryCount

        viewModel.setCustomStartDate(LocalDate.of(2026, 1, 31))
        advanceUntilIdle()
        viewModel.setCustomEndDate(LocalDate.of(2026, 1, 1))
        advanceUntilIdle()

        assertEquals(boundedQueriesBeforeInvalidRange, dao.rangeQueryCount)
        assertFalse(viewModel.uiState.value.progress.rangeValid)
        assertFalse(viewModel.uiState.value.progress.loading)
        assertTrue(viewModel.uiState.value.progress.chart.points.isEmpty())
    }

    @Test
    fun switchingMetricAndExactLoadReusesLoadedAnalyticsWithoutRoomRequery() = runTest(dispatcher) {
        val exerciseId = "synthetic-metric-switch"
        val dao = FakeHistoryDao(
            exercises = listOf(exercise(exerciseId, 2_000L, 2L)),
            facts = listOf(
                fact("switch-a", 1_000L, 50_000L, 8),
                fact("switch-b", 2_000L, 55_000L, 7),
            ),
        )
        val viewModel = HistoryViewModel(
            HistoryRepository(dao),
            SavedStateHandle(mapOf(HistoryViewModel.SELECTED_EXERCISE_KEY to exerciseId)),
            zoneIdProvider = { utc },
        )
        advanceUntilIdle()
        val allQueries = dao.allFactsQueryCount
        val boundedQueries = dao.rangeQueryCount

        viewModel.setProgressMetric(ProgressMetric.VOLUME)
        assertEquals(ProgressMetric.VOLUME, viewModel.uiState.value.progress.chart.metric)
        assertEquals(2, viewModel.uiState.value.progress.chart.points.size)

        viewModel.setProgressMetric(ProgressMetric.REPS_AT_EXACT_LOAD)
        assertEquals(ProgressMetric.REPS_AT_EXACT_LOAD, viewModel.uiState.value.progress.chart.metric)
        assertEquals(55_000L, viewModel.uiState.value.progress.selectedExactLoadGrams)
        assertTrue(viewModel.uiState.value.progress.chart.points.isNotEmpty())

        viewModel.setExactLoad(50_000L)
        assertEquals(50_000L, viewModel.uiState.value.progress.selectedExactLoadGrams)
        assertEquals(1, viewModel.uiState.value.progress.chart.points.size)
        assertEquals(8L, viewModel.uiState.value.progress.chart.points.single().exactValue.toLong())

        assertEquals(allQueries, dao.allFactsQueryCount)
        assertEquals(boundedQueries, dao.rangeQueryCount)
    }

    private fun exercise(id: String, lastStartedAt: Long, sessionCount: Long) = HistoryExerciseRow(
        id = id,
        name = "Synthetic $id",
        equipment = null,
        unilateral = false,
        archived = false,
        lastStartedAt = lastStartedAt,
        sessionCount = sessionCount,
    )

    private fun fact(
        workoutId: String,
        startedAt: Long,
        loadGrams: Long,
        reps: Int,
    ) = PrFactRow(
        workoutId = workoutId,
        startedAt = startedAt,
        finishedAt = startedAt + 500L,
        workoutExerciseId = "we-$workoutId",
        workoutExercisePosition = 0,
        workoutSetId = "set-$workoutId",
        setPosition = 0,
        type = "WORK",
        loadGrams = loadGrams,
        reps = reps,
        rirTenths = null,
        completedAt = startedAt + 100L,
    )

    internal class FakeHistoryDao(
        private val exercises: List<HistoryExerciseRow>,
        private val facts: List<PrFactRow>,
    ) : HistoryDao {
        var allFactsQueryCount: Int = 0
            private set
        var rangeQueryCount: Int = 0
            private set

        override fun observeExercisesWithFinishedHistory(): Flow<List<HistoryExerciseRow>> = flowOf(exercises)

        override fun pageFinishedExerciseHistory(exerciseId: String): PagingSource<Int, HistorySetRow> =
            object : PagingSource<Int, HistorySetRow>() {
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HistorySetRow> =
                    LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

                override fun getRefreshKey(state: PagingState<Int, HistorySetRow>): Int? = null
            }

        override suspend fun getExercisePrFacts(exerciseId: String): List<PrFactRow> {
            allFactsQueryCount++
            return facts
        }

        override suspend fun getExercisePrFactsInRange(
            exerciseId: String,
            startInclusive: Long,
            endExclusive: Long,
        ): List<PrFactRow> {
            rangeQueryCount++
            return facts.filter { it.startedAt >= startInclusive && it.startedAt < endExclusive }
        }
    }
}
