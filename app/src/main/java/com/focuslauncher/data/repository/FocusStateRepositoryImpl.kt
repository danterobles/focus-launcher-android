package com.focuslauncher.data.repository

import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateFailure
import com.focuslauncher.data.local.StateMapper
import com.focuslauncher.data.local.StateResult
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.repository.FocusStateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FocusStateRepositoryImpl @Inject constructor(
    private val store: FocusStateStore,
) : FocusStateRepository {

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val settings: Flow<SettingsReadState> = retries
        .onStart { emit(Unit) }
        .flatMapLatest {
            store.observe()
                .map { result -> result.toReadState() }
                .onStart { emit(SettingsReadState.Loading) }
        }

    override fun retry() {
        retries.tryEmit(Unit)
    }

    private fun StateResult.toReadState(): SettingsReadState = when (this) {
        is StateResult.Ready -> SettingsReadState.Ready(StateMapper.toSettings(state))
        is StateResult.Failed -> when (failure.kind) {
            StateFailure.Kind.Io -> SettingsReadState.ReadError(ReadErrorKind.StorageUnavailable)
            StateFailure.Kind.KeyUnavailable -> SettingsReadState.ReadError(ReadErrorKind.KeystoreUnavailable)
            StateFailure.Kind.KeyLost -> SettingsReadState.RecoveryRequired(RecoveryReason.KeyLost)
            StateFailure.Kind.InterruptedInit -> SettingsReadState.RecoveryRequired(RecoveryReason.InterruptedInit)
            StateFailure.Kind.FutureVersion -> SettingsReadState.RecoveryRequired(RecoveryReason.FutureVersion)
            StateFailure.Kind.InvalidState, StateFailure.Kind.TooLarge ->
                SettingsReadState.RecoveryRequired(RecoveryReason.InvalidState)
            StateFailure.Kind.Corrupt -> SettingsReadState.RecoveryRequired(RecoveryReason.Corrupt)
        }
    }
}
