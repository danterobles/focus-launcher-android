package com.focuslauncher.presentation.common

import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.VisibleAppsState
import com.focuslauncher.domain.usecase.LaunchOutcome

/** Contenido de una superficie de apps (Home o cajón), derivado siempre de la lista filtrada. */
sealed interface AppsContent {
    data object Loading : AppsContent
    data class Apps(val apps: List<InstalledApp>, val total: Int) : AppsContent

    /** No hay apps visibles. */
    data object Empty : AppsContent

    /** Hay apps visibles pero la consulta no coincide con ninguna. */
    data object NoResults : AppsContent
    data class Blocked(val reason: BlockedReason) : AppsContent
}

/** Avisos efímeros tras intentar abrir una app. */
enum class Notice { AppUnavailable, LaunchFailed, SettingsUnavailable }

fun LaunchOutcome.toNotice(): Notice? = when (this) {
    LaunchOutcome.Launched, LaunchOutcome.NotVisible -> null
    LaunchOutcome.Unavailable -> Notice.AppUnavailable
    LaunchOutcome.Failed -> Notice.LaunchFailed
    LaunchOutcome.SettingsUnavailable -> Notice.SettingsUnavailable
}

fun VisibleAppsState.toContent(limit: Int? = null): AppsContent = when (this) {
    VisibleAppsState.Loading -> AppsContent.Loading
    VisibleAppsState.Empty -> AppsContent.Empty
    is VisibleAppsState.Blocked -> AppsContent.Blocked(reason)
    is VisibleAppsState.Ready -> AppsContent.Apps(if (limit != null) apps.take(limit) else apps, apps.size)
}
