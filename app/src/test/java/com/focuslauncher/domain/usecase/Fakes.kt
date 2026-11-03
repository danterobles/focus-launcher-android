package com.focuslauncher.domain.usecase

import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.LauncherSettings
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.domain.model.VisibilityPolicy
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

val SERIAL = UserSerial(0)

fun app(pkg: String, cls: String = "$pkg.Main", label: String = pkg) =
    InstalledApp(AppId(pkg, cls, SERIAL), label, available = true)

fun key(pkg: String) = AppPackageKey(pkg, SERIAL)

fun readySettings(vararg hidden: String, pin: Boolean = false) = SettingsReadState.Ready(
    LauncherSettings(true, VisibilityPolicy(hidden.map { key(it) }.toSet()), pin),
)

class FakeCatalog(initial: CatalogState = CatalogState.Loading) : AppCatalogRepository {
    val state = MutableStateFlow(initial)
    var launchResult: LaunchResult = LaunchResult.Success
    val launched = mutableListOf<AppId>()
    var refreshes = 0
    override val catalog: Flow<CatalogState> = state
    override suspend fun refresh() { refreshes++ }
    override suspend fun launch(id: AppId): LaunchResult { launched += id; return launchResult }
}

class FakeSettings(initial: SettingsReadState = SettingsReadState.Loading) : FocusStateRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<SettingsReadState> = state
    var retries = 0
    override fun retry() { retries++ }
}

/** Administración simulada y programable. Al aceptar un cambio actualiza la política como lo haría la persistencia real. */
class FakeAdmin(private val settings: FakeSettings? = null) : com.focuslauncher.domain.repository.AdminRepository {
    override val isAuthorized = MutableStateFlow(false)
    val verifyResults = ArrayDeque<com.focuslauncher.domain.model.AuthenticationResult>()
    var verifyDelay: suspend () -> Unit = {}
    var verifyCalls = 0
    var cooldownMs = 0L
    var hideResult: com.focuslauncher.domain.model.MutationResult = com.focuslauncher.domain.model.MutationResult.Success
    var hideDelay: suspend () -> Unit = {}
    val hideCalls = mutableListOf<Pair<AppPackageKey, Boolean>>()
    var pinResult: com.focuslauncher.domain.model.MutationResult = com.focuslauncher.domain.model.MutationResult.Success
    val pinCalls = mutableListOf<String>()
    var locks = 0

    override suspend fun verifyPin(pin: CharArray): com.focuslauncher.domain.model.AuthenticationResult {
        verifyCalls++
        verifyDelay()
        val r = verifyResults.removeFirstOrNull() ?: com.focuslauncher.domain.model.AuthenticationResult.IncorrectPin(4)
        if (r == com.focuslauncher.domain.model.AuthenticationResult.Success) isAuthorized.value = true
        pin.fill('\u0000')
        return r
    }

    override fun lock() { locks++; isAuthorized.value = false }
    override suspend fun cooldownRemainingMs() = cooldownMs

    override suspend fun setPackageHidden(key: AppPackageKey, hidden: Boolean): com.focuslauncher.domain.model.MutationResult {
        hideCalls += key to hidden
        hideDelay()
        if (hideResult == com.focuslauncher.domain.model.MutationResult.Success) {
            (settings?.state?.value as? SettingsReadState.Ready)?.let { ready ->
                val set = ready.settings.visibility.hiddenPackages.let { if (hidden) it + key else it - key }
                settings.state.value = SettingsReadState.Ready(ready.settings.copy(visibility = VisibilityPolicy(set)))
            }
        }
        return hideResult
    }

    override suspend fun enablePin(pin: CharArray, confirmation: CharArray): com.focuslauncher.domain.model.MutationResult { pinCalls += "enable"; return pinResult }
    override suspend fun changePin(pin: CharArray, confirmation: CharArray): com.focuslauncher.domain.model.MutationResult { pinCalls += "change"; return pinResult }
    override suspend fun disablePin(): com.focuslauncher.domain.model.MutationResult { pinCalls += "disable"; return pinResult }
    override suspend fun resetConfiguration(): com.focuslauncher.domain.model.MutationResult { pinCalls += "reset"; return pinResult }
}
