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
    override fun retry() = Unit
}
