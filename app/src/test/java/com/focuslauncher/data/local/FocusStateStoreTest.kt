package com.focuslauncher.data.local

import com.focuslauncher.core.security.KeyState
import com.focuslauncher.data.local.proto.FocusStateProto
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FocusStateStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val own = "com.focuslauncher"
    private lateinit var file: File
    private var current: FocusStateStore? = null

    @Before fun setUp() { file = File(File(tmp.root, "focus"), "focus_state.pb.enc") }

    @After fun tearDown() = runBlocking { current?.close(); Unit }

    /** Una sola instancia por archivo: se cierra la anterior, como en un reinicio de proceso. */
    private fun open(cipher: FakeKeyCipher): FocusStateStore = runBlocking {
        current?.close()
        FocusStateStore(StateLocation(file), cipher, CoroutineScope(SupervisorJob() + Dispatchers.IO), own)
            .also { current = it }
    }

    private fun FocusStateStore.read(): StateResult = runBlocking { observe().first() }
    private fun FocusStateStore.ready(): FocusStateProto = (read() as StateResult.Ready).state
    private fun FocusStateStore.failure(): StateFailure.Kind = (read() as StateResult.Failed).failure.kind
    private fun FocusStateStore.write(f: (FocusStateProto) -> FocusStateProto) = runBlocking { update(f) }

    private fun ivOf(f: File) = f.readBytes().copyOfRange(6, 18).toList()

    @Test fun cleanInstallCreatesKeyAndExplicitValidInitialState() {
        val cipher = FakeKeyCipher()
        val store = open(cipher)
        assertEquals(StateMapper.initialState(), store.ready())
        assertEquals(1, cipher.createCount)
        assertTrue(file.exists())
        val s = StateMapper.toSettings(store.ready())
        assertFalse(s.onboardingAccepted); assertFalse(s.pinEnabled); assertTrue(s.visibility.hiddenPackages.isEmpty())
    }

    @Test fun rulesPinAndAttemptsSurviveARestart() {
        val cipher = FakeKeyCipher()
        assertEquals(StateWriteResult.Success, open(cipher).write { Fixtures.rich() })
        val reopened = open(cipher) // nueva instancia sobre el mismo archivo = reinicio normal
        assertEquals(Fixtures.rich(), reopened.ready())
        assertEquals(1, cipher.createCount) // no se vuelve a inicializar ni a regenerar la clave
    }

    @Test fun everyWriteUsesANewIv() {
        val store = open(FakeKeyCipher())
        store.ready()
        val ivs = mutableListOf(ivOf(file))
        repeat(3) { n ->
            store.write { it.toBuilder().setOnboardingAccepted(n % 2 == 0).build() }
            ivs += ivOf(file)
        }
        assertEquals(ivs.size, ivs.toSet().size)
    }

    @Test fun alteredFileIsRecoveryRequiredAndNeverDefaultsOrOverwrites() {
        val cipher = FakeKeyCipher()
        open(cipher).write { Fixtures.rich() }
        val bytes = file.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        file.writeBytes(bytes)
        val store = open(cipher)
        assertEquals(StateFailure.Kind.Corrupt, store.failure())
        assertEquals(StateWriteResult.RecoveryRequired, store.write { Fixtures.rich() })
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(1, cipher.createCount)
    }

    @Test fun truncatedFileIsRecoveryRequired() {
        val cipher = FakeKeyCipher()
        open(cipher).write { Fixtures.rich() }
        file.writeBytes(file.readBytes().copyOf(10))
        assertEquals(StateFailure.Kind.Corrupt, open(cipher).failure())
    }

    @Test fun lostKeyWithExistingFileIsRecoveryRequiredWithoutRegeneratingTheKey() {
        val cipher = FakeKeyCipher()
        open(cipher).write { Fixtures.rich() }
        val before = file.readBytes()
        cipher.loseKey()
        val store = open(cipher)
        assertEquals(StateFailure.Kind.KeyLost, store.failure())
        assertEquals(StateWriteResult.RecoveryRequired, store.write { Fixtures.rich() })
        assertEquals(1, cipher.createCount)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun aliasWithoutFileIsInterruptedInitNotACleanInstall() {
        val cipher = FakeKeyCipher(KeyState.Present)
        assertEquals(StateFailure.Kind.InterruptedInit, open(cipher).failure())
        assertFalse(file.exists())
        assertEquals(0, cipher.createCount)
    }

    @Test fun leftoverTempFileWithoutStateFileIsInterruptedInit() {
        file.parentFile!!.mkdirs()
        File(file.path + ".tmp").writeBytes(byteArrayOf(1, 2, 3))
        val cipher = FakeKeyCipher()
        assertEquals(StateFailure.Kind.InterruptedInit, open(cipher).failure())
        assertEquals(0, cipher.createCount)
    }

    @Test fun invalidKeyEntryWithFileIsRecoveryRequired() {
        val cipher = FakeKeyCipher()
        open(cipher).ready()
        cipher.state = KeyState.Invalid
        assertEquals(StateFailure.Kind.KeyLost, open(cipher).failure())
    }

    @Test fun futureSchemaIsRecoveryRequiredAndNeverOverwritten() {
        val cipher = FakeKeyCipher()
        open(cipher).ready()
        val future = StateEnvelope.seal(StateMapper.initialState().toBuilder().setSchemaVersion(2).build().toByteArray(), cipher)
        file.writeBytes(future)
        val store = open(cipher)
        assertEquals(StateFailure.Kind.FutureVersion, store.failure())
        assertEquals(StateWriteResult.RecoveryRequired, store.write { it.toBuilder().setOnboardingAccepted(true).build() })
        assertArrayEquals(future, file.readBytes())
    }

    @Test fun futureEnvelopeVersionIsRecoveryRequired() {
        val cipher = FakeKeyCipher()
        open(cipher).ready()
        file.writeBytes(file.readBytes().also { it[4] = 2 })
        assertEquals(StateFailure.Kind.FutureVersion, open(cipher).failure())
    }

    @Test fun oversizedFileIsRejectedExplicitly() {
        val cipher = FakeKeyCipher()
        open(cipher).ready()
        file.writeBytes(ByteArray(StateEnvelope.MAX_FILE_BYTES + 10))
        assertEquals(StateFailure.Kind.TooLarge, open(cipher).failure())
    }

    @Test fun unavailableKeystoreIsRetryableAndLeavesNoState() {
        val cipher = FakeKeyCipher().also { it.unavailable = true }
        val store = open(cipher)
        assertEquals(StateFailure.Kind.KeyUnavailable, store.failure())
        assertEquals(StateWriteResult.Unavailable, store.write { Fixtures.rich() })
        assertFalse(file.exists())
        cipher.unavailable = false
        assertEquals(StateMapper.initialState(), store.ready()) // reintento en la misma instancia
    }

    @Test fun ownPackageRuleIsRejectedAndStateKept() {
        val store = open(FakeKeyCipher())
        val before = store.ready()
        val r = store.write { it.toBuilder().addHiddenPackages(Fixtures.pkg(own)).build() }
        assertTrue(r is StateWriteResult.InvalidInput)
        assertEquals(before, store.ready())
    }

    @Test fun invalidResultingStateIsInvalidInputNotRecovery() {
        val store = open(FakeKeyCipher())
        val r = store.write { it.toBuilder().setSchemaVersion(9).build() }
        assertTrue(r is StateWriteResult.InvalidInput)
        assertEquals(StateMapper.initialState(), store.ready())
    }

    @Test fun failedWriteIsNeverReportedAsSuccessAndKeepsPreviousState() {
        assumeTrue("requiere un directorio no escribible (no root)", System.getProperty("user.name") != "root")
        val store = open(FakeKeyCipher())
        store.ready()
        val before = file.readBytes()
        file.parentFile!!.setWritable(false)
        try {
            assertEquals(StateWriteResult.StorageFailure, store.write { it.toBuilder().setOnboardingAccepted(true).build() })
        } finally {
            file.parentFile!!.setWritable(true)
        }
        assertArrayEquals(before, file.readBytes())
        assertFalse(store.ready().onboardingAccepted)
    }

    @Test fun failedInitialWriteIsRetriedInTheSameProcessInsteadOfBecomingInterruptedInit() {
        assumeTrue("requiere un directorio no escribible (no root)", System.getProperty("user.name") != "root")
        file.parentFile!!.mkdirs()
        file.parentFile!!.setWritable(false)
        val cipher = FakeKeyCipher()
        val store = open(cipher)
        try {
            assertEquals(StateFailure.Kind.Io, store.failure())
        } finally {
            file.parentFile!!.setWritable(true)
        }
        assertEquals(StateMapper.initialState(), store.ready())
        assertEquals(1, cipher.createCount)
    }

    @Test fun writesSerializeAndNeverLoseUpdates() {
        val store = open(FakeKeyCipher())
        store.ready()
        runBlocking {
            coroutineScope {
                (1..20).map { n ->
                    launch {
                        store.update { it.toBuilder().addHiddenPackages(Fixtures.pkg("p$n")).build() }
                    }
                }
            }
        }
        assertEquals(20, store.ready().hiddenPackagesCount)
    }
}
