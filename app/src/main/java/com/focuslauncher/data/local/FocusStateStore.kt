package com.focuslauncher.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.focuslauncher.core.security.KeyState
import com.focuslauncher.core.security.StateCipher
import com.focuslauncher.data.local.proto.FocusStateProto
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job

sealed interface StateResult {
    data class Ready(val state: FocusStateProto) : StateResult
    data class Failed(val failure: StateFailure) : StateResult
}

sealed interface StateWriteResult {
    data object Success : StateWriteResult
    data class InvalidInput(val reason: String) : StateWriteResult
    data object StorageFailure : StateWriteResult
    data object Unavailable : StateWriteResult
    data object RecoveryRequired : StateWriteResult
}

/** Ubicación del estado: archivo único (noBackupFilesDir) y alias de la clave. */
class StateLocation(val file: File)

/**
 * Único dueño del DataStore del estado. Una instancia por archivo. Las escrituras salen solo por
 * [update] (que valida invariantes); los ViewModels nunca reciben este tipo.
 */
class FocusStateStore(
    private val location: StateLocation,
    private val cipher: StateCipher,
    private val scope: CoroutineScope,
    private val ownPackage: String,
) {
    private val bootstrapLock = Mutex()
    private var dataStore: DataStore<FocusStateProto>? = null

    /** La clave se creó en este proceso pero el estado inicial aún no se escribió: reintento seguro. */
    private var initPending = false

    private fun tmpFile() = File(location.file.path + ".tmp")

    private fun hasStateRemnants(): Boolean {
        val dir = location.file.parentFile ?: return false
        return dir.listFiles { f -> f.name.startsWith(location.file.name) }?.isNotEmpty() == true
    }

    /**
     * Bootstrap (DATA.md §4). No lee el estado salvo para validarlo después; jamás regenera una
     * clave si existe estado cifrado y jamás sobrescribe el archivo cuando la clave es inaccesible.
     */
    private suspend fun ensureDataStore(): DataStore<FocusStateProto> = bootstrapLock.withLock {
        dataStore?.takeIf { !initPending }?.let { return@withLock it }
        // Una sola instancia por archivo: si el estado inicial quedó pendiente se reutiliza la creada.
        val store = dataStore ?: bootstrapNew().also { dataStore = it }
        if (initPending) {
            try {
                store.updateData { StateMapper.initialState() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw e.toStateFailure()
            }
            initPending = false
        }
        store
    }

    private fun bootstrapNew(): DataStore<FocusStateProto> {
        val file = location.file
        val key = cipher.keyState() // KeyUnavailable si el almacén no responde: reintentable
        when {
            file.exists() && key == KeyState.Present -> Unit
            file.exists() -> throw StateFailure(StateFailure.Kind.KeyLost)
            // Archivo ausente: solo es instalación limpia sin clave ni restos.
            key != KeyState.Absent || hasStateRemnants() || tmpFile().exists() ->
                throw StateFailure(StateFailure.Kind.InterruptedInit)
            else -> {
                try {
                    file.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("sin directorio") }
                } catch (e: IOException) {
                    throw StateFailure(StateFailure.Kind.Io, e)
                }
                cipher.createKey()
                initPending = true
            }
        }
        return DataStoreFactory.create(
            serializer = FocusStateSerializer(cipher),
            corruptionHandler = null,
            scope = scope,
            produceFile = { file },
        )
    }

    /** Estado validado, o un fallo clasificado. Nunca emite defaults ante un error. */
    fun observe(): Flow<StateResult> = flow {
        val store = try {
            ensureDataStore()
        } catch (e: StateFailure) {
            emit(StateResult.Failed(e))
            return@flow
        }
        emitAll(
            store.data
                .map<FocusStateProto, StateResult> { StateValidator.validate(it); StateResult.Ready(it) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(StateResult.Failed(e.toStateFailure()))
                },
        )
    }

    /**
     * Transformación atómica con `updateData`. Se valida el estado actual, el resultado y las reglas
     * de escritura antes de confirmar.
     */
    suspend fun update(transform: (FocusStateProto) -> FocusStateProto): StateWriteResult = try {
        val store = ensureDataStore()
        store.updateData { current ->
            StateValidator.validate(current)
            val next = transform(current)
            StateValidator.validateWrite(current, next, ownPackage)
            next
        }
        StateWriteResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: InvalidWriteException) {
        StateWriteResult.InvalidInput(e.message.orEmpty())
    } catch (e: Exception) {
        val failure = e.toStateFailure()
        when {
            failure.requiresRecovery -> StateWriteResult.RecoveryRequired
            failure.kind == StateFailure.Kind.KeyUnavailable -> StateWriteResult.Unavailable
            else -> StateWriteResult.StorageFailure
        }
    }

    /** Libera el DataStore (pruebas y cierre ordenado): permite crear otra instancia sobre el mismo archivo. */
    suspend fun close() {
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    private fun Throwable.toStateFailure(): StateFailure = when (this) {
        is StateFailure -> this
        is IOException -> StateFailure(StateFailure.Kind.Io, this)
        else -> StateFailure(StateFailure.Kind.Io, this)
    }
}
