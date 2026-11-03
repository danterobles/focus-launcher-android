package com.focuslauncher.data.android

import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.UserSerial
import java.text.Collator
import java.util.Locale

/** Actividad devuelta por LauncherApps (puede incluir entradas sintetizadas). */
data class LauncherEntry(val packageName: String, val className: String, val label: String?)

/** Actividad MAIN/LAUNCHER real devuelta por PackageManager. */
data class ResolvedLauncherActivity(
    val packageName: String,
    val className: String,
    val enabled: Boolean,
    val exported: Boolean,
)

/** Lógica pura (sin Android) del catálogo: intersección, filtrado, deduplicación y orden. */
object CatalogBuilder {

    /** Un serial negativo o desconocido es un error, nunca 0. */
    fun validSerialOrNull(raw: Long): UserSerial? = if (raw >= 0) UserSerial(raw) else null

    fun build(
        launcherEntries: List<LauncherEntry>,
        resolved: List<ResolvedLauncherActivity>,
        ownPackage: String,
        serial: UserSerial,
        locale: Locale,
    ): List<InstalledApp> {
        val launchable = resolved
            .filter { it.enabled && it.exported }
            .mapTo(HashSet()) { it.packageName to it.className }

        val apps = launcherEntries
            .asSequence()
            .filter { it.packageName != ownPackage }
            .filter { (it.packageName to it.className) in launchable }
            .map { entry ->
                InstalledApp(
                    id = AppId(entry.packageName, entry.className, serial),
                    label = entry.label?.trim().takeUnless { it.isNullOrEmpty() } ?: entry.packageName,
                    available = true,
                )
            }
            .distinctBy { it.id }
            .toList()

        val collator = Collator.getInstance(locale)
        return apps.sortedWith(
            Comparator<InstalledApp> { a, b -> collator.compare(a.label, b.label) }
                .thenBy { it.id.packageName }
                .thenBy { it.id.activityName },
        )
    }
}
