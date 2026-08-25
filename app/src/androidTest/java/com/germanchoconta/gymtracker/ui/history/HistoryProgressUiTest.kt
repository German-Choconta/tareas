package com.germanchoconta.gymtracker.ui.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
