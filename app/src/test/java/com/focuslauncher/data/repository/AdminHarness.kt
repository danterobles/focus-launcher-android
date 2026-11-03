package com.focuslauncher.data.repository

import com.focuslauncher.core.di.MonotonicClock
import com.focuslauncher.core.security.AdminEnvironment
import com.focuslauncher.core.security.AdminSessionManager
import com.focuslauncher.core.security.PinHasher
import com.focuslauncher.data.local.FakeKeyCipher
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.data.local.StateResult
import com.focuslauncher.data.local.StateWriteResult
import com.focuslauncher.data.local.proto.FocusStateProto
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

class FakeClock(var now: Long = 1_000) : MonotonicClock {
    override fun elapsedRealtimeMillis() = now
}

class FakeEnvironment : AdminEnvironment {
    @Volatile var interactive = true
    @Volatile var locked = false
    override fun isScreenInteractive() = interactive
    override fun isDeviceLocked() = locked
}

/** KDF rápida y controlable: cuenta llamadas y puede pausarse (`gate`) para simular una KDF lenta. */
class FakeHasher : PinHasher {
    private val random = SecureRandom()
    val calls = AtomicInteger(0)
    val concurrent = AtomicInteger(0)
    @Volatile var maxConcurrent = 0
    @Volatile var gate: CompletableDeferred<Unit>? = null
    @Volatile var entered: CompletableDeferred<Unit>? = null

    override fun newSalt() = ByteArray(16).also(random::nextBytes)

    override suspend fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        calls.incrementAndGet()
        val now = concurrent.incrementAndGet()
        if (now > maxConcurrent) maxConcurrent = now
        try {
            entered?.complete(Unit)
            gate?.await()
            return MessageDigest.getInstance("SHA-256").run { update(salt); update(String(pin).toByteArray()); digest() }
        } finally {
            concurrent.decrementAndGet()
        }
    }
}

/** Almacén que puede fallar en llamadas concretas a `update` (escrituras) para probar commits fallidos. */
class FlakyStore(location: StateLocation, cipher: FakeKeyCipher, scope: CoroutineScope, own: String) :
    FocusStateStore(location, cipher, scope, own) {
    val updates = AtomicInteger(0)
    @Volatile var failWhen: (Int) -> Boolean = { false }
    override suspend fun update(transform: (FocusStateProto) -> FocusStateProto): StateWriteResult {
        val n = updates.incrementAndGet()
        if (failWhen(n)) return StateWriteResult.StorageFailure
        return super.update(transform)
    }
}

/** Un «proceso» completo: almacén, reloj, entorno, sesión y coordinador. `restart()` simula un proceso nuevo. */
class AdminHarness(private val dir: File, val own: String = "com.focuslauncher") {
    val cipher = FakeKeyCipher()
    val file = File(File(dir, "focus"), "focus_state.pb.enc")
    val hasher = FakeHasher()
    var clock = FakeClock()
    var env = FakeEnvironment()
    lateinit var store: FlakyStore
    lateinit var sessions: AdminSessionManager
    lateinit var admin: AdminAccessCoordinator

    init { boot() }

    private fun boot() {
        store = FlakyStore(StateLocation(file), cipher, CoroutineScope(SupervisorJob() + Dispatchers.IO), own)
        sessions = AdminSessionManager(clock, env, scope = null)
        admin = AdminAccessCoordinator(store, sessions, hasher, clock)
    }

    /** Muerte del proceso + arranque nuevo: sin sesión, reloj monotónico distinto, mismo archivo. */
    fun restart(newClockStart: Long = 5_000) {
        runBlocking { store.close() }
        clock = FakeClock(newClockStart)
        env = FakeEnvironment()
        boot()
    }

    fun close() = runBlocking { store.close() }

    fun state(): FocusStateProto = (runBlocking { store.readCurrent() } as StateResult.Ready).state

    fun enablePin(pin: String = "123456") = runBlocking { admin.enablePin(pin.toCharArray(), pin.toCharArray()) }
    fun verify(pin: String) = runBlocking { admin.verifyPin(pin.toCharArray()) }
}
