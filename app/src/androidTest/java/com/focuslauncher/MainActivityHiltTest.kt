package com.focuslauncher

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import android.content.Context
import com.focuslauncher.core.di.StorageModule
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.core.security.StateCipher
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.di.TestStores
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Hilt crea la Activity y su ViewModel, y Compose renderiza la pantalla Inicio. */
@HiltAndroidTest
@UninstallModules(StorageModule::class)
class MainActivityHiltTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object AcceptedStorage {
        @Provides @Singleton fun cipher(): StateCipher = TestStores.cipher()
        @Provides @Singleton fun location(@ApplicationContext context: Context): StateLocation = TestStores.location(context)
        @Provides @Singleton fun store(@ApplicationContext context: Context, location: StateLocation, cipher: StateCipher): FocusStateStore =
            TestStores.store(context, location, cipher as KeystoreStateCipher, accepted = true)
    }

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
