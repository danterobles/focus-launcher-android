package com.focuslauncher

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import com.focuslauncher.core.di.StorageModule
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.core.security.StateCipher
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.data.local.StateValidator
import com.focuslauncher.data.local.proto.PinConfigProto
import com.focuslauncher.data.local.proto.PinVerifierProto
import com.focuslauncher.di.TestStores
import com.google.protobuf.ByteString
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Activity real con PIN habilitado: ni rotar ni volver desde Recientes restauran el texto secreto ni
 * dejan la administración abierta. (Con un verificador sintético: nunca se verifica ningún PIN.)
 */
@HiltAndroidTest
@UninstallModules(StorageModule::class)
class PinGatePrivacyHiltTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PinEnabledStorage {
        @Provides @Singleton fun cipher(): StateCipher = TestStores.cipher()
        @Provides @Singleton fun location(@ApplicationContext context: Context): StateLocation = TestStores.location(context)
        @Provides @Singleton fun store(@ApplicationContext context: Context, location: StateLocation, cipher: StateCipher): FocusStateStore =
            TestStores.store(context, location, cipher as KeystoreStateCipher, accepted = true).also { store ->
                runBlocking {
                    store.update {
                        it.toBuilder().setPin(
                            PinConfigProto.newBuilder().setEnabled(
                                PinVerifierProto.newBuilder().setKdf(StateValidator.KDF).setIterations(StateValidator.KDF_ITERATIONS)
                                    .setSalt(ByteString.copyFrom(ByteArray(16) { 7 })).setVerifier(ByteString.copyFrom(ByteArray(32) { 9 })),
                            ),
                        ).build()
                    }
                }
            }
    }

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() = hiltRule.inject()

    private fun waitFor(text: String) = rule.waitUntil(15_000) {
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
    private fun passwordField() = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

    private fun openGateAndType() {
        waitFor("Configuración de Focus")
        rule.onNodeWithText("Configuración de Focus").performClick()
        waitFor("Ingresa el PIN")
        passwordField().performTextInput("123456")
        rule.onNodeWithText("Desbloquear").assertIsEnabled()
    }

    @Test fun rotatingWhileTypingTheSecretDoesNotRestoreItNorOpenAnything() {
        openGateAndType()
        rule.activityRule.scenario.recreate() // rotación / cambio de configuración
        waitFor("Ingresa el PIN") // sigue bloqueada
        rule.onNodeWithText("Desbloquear").assertIsNotEnabled() // el campo está vacío: no se restauró el texto
        rule.onNode(hasText("Visible")).assertDoesNotExist()
    }

    @Test fun comingBackFromRecentsShowsHomeNotTheGateNorTheTypedSecret() {
        openGateAndType()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // pasa a segundo plano (Recientes)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor("Inicio")
        rule.onNode(hasText("Ingresa el PIN")).assertDoesNotExist()
        // Al volver a abrir la configuración la puerta sigue exigiendo el PIN y vacía.
        rule.onNodeWithText("Configuración de Focus").performClick()
        waitFor("Ingresa el PIN")
        rule.onNodeWithText("Desbloquear").assertIsNotEnabled()
    }
}
