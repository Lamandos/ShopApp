package com.example.shopapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensStatsAndShowsApiValues() {
        composeRule.onNodeWithTag("nav_stats").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("stats_content")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("36881662,28 ₽").assertIsDisplayed()
        composeRule.onNodeWithText("290").assertIsDisplayed()
        composeRule.onNodeWithText("127178,14 ₽").assertIsDisplayed()
        composeRule.onNodeWithText("MacBook Air M3").assertIsDisplayed()
    }
}
