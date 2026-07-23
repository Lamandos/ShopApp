package com.example.shopapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createsOrderWithDiscount() {
        composeRule.onNodeWithText("Аудио").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("JBL Tune 520BT")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithText("iPhone 15 Pro 256GB")
                .fetchSemanticsNodes().isEmpty()
        )

        composeRule.onNodeWithTag("catalog_search").performTextInput("JBL")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("JBL Tune 520BT").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("AirPods Pro 2").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("increase_product_9").performClick()
        composeRule.onNodeWithTag("checkout").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("order_customer_name")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("order_customer_name").performTextInput("Телефонный тест")
        composeRule.onNodeWithTag("order_phone").performTextInput("+79990000000")
        composeRule.onNodeWithTag("order_address").performTextInput("Москва")
        composeRule.onNodeWithTag("order_promocode").performTextInput("FIX500")
        composeRule.onNodeWithTag("order_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Заказ оформлен")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("order_result").assertIsDisplayed()
        composeRule.onNodeWithText("Сумма: 5990,00 ₽").assertIsDisplayed()
        composeRule.onNodeWithText("Скидка: 500,00 ₽").assertIsDisplayed()
        composeRule.onNodeWithText("Итого: 5490,00 ₽").assertIsDisplayed()
    }
}
