package com.focuslauncher

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Hilt crea la Activity y su ViewModel, y Compose renderiza la pantalla Inicio. */
@HiltAndroidTest
class MainActivityHiltTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun activityStartsOnHomeScreen() {
        composeRule.onNodeWithText("Inicio").assertExists()
    }
}
