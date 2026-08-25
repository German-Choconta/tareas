package com.germanchoconta.gymtracker.ui.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.germanchoconta.gymtracker.domain.FrequencyBucketSize
import com.germanchoconta.gymtracker.domain.ProgressMetric
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryProgressUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyAndProgressTabsExposeSelectionAndSwitchContentSection() {
        var selected by mutableStateOf(HistoryDetailSection.HISTORY)

        composeRule.setContent {
            MaterialTheme {
                HistoryDetailSectionTabs(
                    selected = selected,
                    onSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Historial").assertIsSelected()
        composeRule.onNodeWithText("Progreso").performClick()
        composeRule.onNodeWithText("Progreso").assertIsSelected()
        composeRule.onNodeWithText("Historial").performClick()
        composeRule.onNodeWithText("Historial").assertIsSelected()
    }

    @Test
    fun progressContentShowsExplicitEmptyStateAndMetricControlUpdatesState() {
        var state by mutableStateOf(
            ProgressUiState(
                metric = ProgressMetric.LOAD,
                chart = emptyProgressChart(ProgressMetric.LOAD),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                ProgressAnalyticsContent(
                    state = state,
                    onRangeModeChange = {},
                    onStartDateChange = {},
                    onEndDateChange = {},
                    onMetricChange = { metric ->
                        state = state.copy(metric = metric, chart = emptyProgressChart(metric))
                    },
                    onExactLoadChange = {},
                    onFrequencyBucketChange = { _: FrequencyBucketSize -> },
                    onPreviousPoint = {},
                    onNextPoint = {},
                )
            }
        }

        composeRule.onNodeWithText("Sin datos para esta vista").assertTextEquals("Sin datos para esta vista")
        composeRule.onNodeWithText("Volumen").performScrollTo().performClick()
        composeRule.onNodeWithText("Volumen").assertIsSelected()
        composeRule.onNodeWithText("Sin datos para esta vista").assertTextEquals("Sin datos para esta vista")
    }
}
