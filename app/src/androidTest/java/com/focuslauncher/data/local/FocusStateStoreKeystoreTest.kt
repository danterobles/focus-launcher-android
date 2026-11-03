package com.focuslauncher.data.local

import android.content.Context
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import com.focuslauncher.core.security.KeyState
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.data.local.proto.FocusStateProto
import com.google.protobuf.ByteString
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Keystore y DataStore reales. Usa un alias y un directorio propios de prueba: nunca toca el
 * estado real de la app (`focus_state_aes_v1`, `noBackupFilesDir/focus`).
 */
class FocusStateStoreKeystoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "focus_state_test_" + UUID.randomUUID().toString().replace("-", "")
    private lateinit var dir: File
    private lateinit var file: File
    private val cipher = KeystoreStateCipher(alias)
    private var current: FocusStateStore? = null

    @Before fun setUp() {
        dir = File(context.cacheDir, "state-test-" + UUID.randomUUID()).also { it.mkdirs() }
        file = File(dir, "focus_state.pb.enc")
    }

    @After fun tearDown() = runBlocking {
        current?.close()
        dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { if (it.containsAlias(alias)) it.deleteEntry(alias) }
    }

    private fun open(): FocusStateStore = runBlocking {
        current?.close()
        FocusStateStore(StateLocation(file), cipher, CoroutineScope(SupervisorJob() + Dispatchers.IO), context.packageName)
            .also { current = it }
    }

    private fun FocusStateStore.read() = runBlocking { observe().first() }
    private fun FocusStateStore.ready(): FocusStateProto = (read() as StateResult.Ready).state
    private fun FocusStateStore.failure() = (read() as StateResult.Failed).failure.kind
    private fun FocusStateStore.write(f: (FocusStateProto) -> FocusStateProto) = runBlocking { update(f) }
    private fun deleteKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

    private fun rich() = StateMapper.initialState().toBuilder()
        .setOnboardingAccepted(true)
        .addHiddenPackages(com.focuslauncher.data.local.proto.PackageKeyProto.newBuilder().setPackageName("a.app").setUserSerial(0))
        .setPin(
            com.focuslauncher.data.local.proto.PinConfigProto.newBuilder().setEnabled(
                com.focuslauncher.data.local.proto.PinVerifierProto.newBuilder()
                    .setKdf(StateValidator.KDF).setIterations(StateValidator.KDF_ITERATIONS)
                    .setSalt(ByteString.copyFrom(ByteArray(16) { 3 })).setVerifier(ByteString.copyFrom(ByteArray(32) { 4 })),
            ),
        )
        .setAttempts(com.focuslauncher.data.local.proto.AttemptStateProto.newBuilder().setReservedInWindow(2).setPenaltyLevel(1))
        .build()

    @Test fun cleanInstallRoundTripAndRestartWithRealKeystore() {
        assertEquals(KeyState.Absent, cipher.keyState())
        assertEquals(StateMapper.initialState(), open().ready())
        assertEquals(KeyState.Present, cipher.keyState())
        assertEquals(StateWriteResult.Success, current!!.write { rich() })
        assertEquals(rich(), open().ready()) // otra instancia sobre el mismo archivo = reinicio
    }

    @Test fun everyWriteChangesTheIvAndFileIsNotPlaintext() {
        val store = open(); store.ready()
        val ivs = mutableListOf<List<Byte>>()
        ivs += file.readBytes().copyOfRange(6, 18).toList()
        repeat(3) { n ->
            store.write { it.toBuilder().setOnboardingAccepted(n % 2 == 0).build() }
            ivs += file.readBytes().copyOfRange(6, 18).toList()
        }
        assertEquals(ivs.size, ivs.toSet().size)
        val bytes = file.readBytes()
        assertEquals("FCLS", String(bytes.copyOfRange(0, 4)))
        assertFalse("el protobuf en claro no debe aparecer", String(bytes, Charsets.ISO_8859_1).contains("a.app"))
    }

    @Test fun keyIsNonExportableAes256GcmWithNoUserAuthentication() {
        open().ready()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(alias, null) as SecretKey
        assertNull("la clave no debe ser exportable", key.encoded)
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(256, info.keySize)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, info.purposes)
        assertEquals(listOf(KeyProperties.BLOCK_MODE_GCM), info.blockModes.toList())
        assertFalse(info.isUserAuthenticationRequired)
    }

    @Test fun alteredFileIsRecoveryRequiredAndNotOverwritten() {
        open().write { rich() }
        val tampered = file.readBytes().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }
        file.writeBytes(tampered)
        val store = open()
        assertEquals(StateFailure.Kind.Corrupt, store.failure())
        assertEquals(StateWriteResult.RecoveryRequired, store.write { rich() })
        assertArrayEquals(tampered, file.readBytes())
    }

    @Test fun lostKeyWithFileIsRecoveryRequiredAndKeyIsNotRegenerated() {
        open().write { rich() }
        val before = file.readBytes()
        deleteKey()
        val store = open()
        assertEquals(StateFailure.Kind.KeyLost, store.failure())
        assertEquals(KeyState.Absent, cipher.keyState()) // no se creó otra clave para ignorar el archivo
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun aliasWithoutFileIsInterruptedInit() {
        cipher.createKey()
        val store = open()
        assertEquals(StateFailure.Kind.InterruptedInit, store.failure())
        assertFalse(file.exists())
    }

    @Test fun futureSchemaIsRecoveryRequiredAndNotOverwritten() {
        open().ready()
        val future = StateEnvelope.seal(StateMapper.initialState().toBuilder().setSchemaVersion(2).build().toByteArray(), cipher)
        file.writeBytes(future)
        val store = open()
        assertEquals(StateFailure.Kind.FutureVersion, store.failure())
        assertEquals(StateWriteResult.RecoveryRequired, store.write { it.toBuilder().setOnboardingAccepted(true).build() })
        assertArrayEquals(future, file.readBytes())
    }

    @Test fun truncatedFileIsRecoveryRequired() {
        open().write { rich() }
        file.writeBytes(file.readBytes().copyOf(20))
        assertEquals(StateFailure.Kind.Corrupt, open().failure())
    }
}
