package com.focuslauncher.domain.usecase

import app.cash.turbine.test
import com.focuslauncher.domain.model.CatalogError
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.ManageableState
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.SettingsReadState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveManageablePackagesTest {
    private val camA = app("cam.pkg", "cam.pkg.Main", "Cámara")
    private val camB = app("cam.pkg", "cam.pkg.Video", "Cámara de vídeo")
    private val music = app("mus.pkg", label = "Música")
    private val catalog = FakeCatalog(CatalogState.Ready(listOf(camA, camB, music)))
    private val settings = FakeSettings(readySettings())
    private val observe = ObserveManageablePackages(catalog, settings, OwnPackage("com.focuslauncher"))

    private suspend fun ready(f: suspend (ManageableState.Ready) -> Unit) = observe().test { f(awaitItem() as ManageableState.Ready); cancelAndIgnoreRemainingEvents() }

    @Test fun groupsActivitiesByPackageWithASummary() = runTest {
        ready { r ->
            assertEquals(listOf("cam.pkg", "mus.pkg"), r.packages.map { it.key.packageName })
            val cam = r.packages.first()
            assertEquals(listOf("Cámara", "Cámara de vídeo"), cam.activityLabels)
            assertEquals("Cámara", cam.title); assertTrue(cam.installed); assertFalse(cam.hidden)
        }
    }

    @Test fun hiddenFlagIsPerPackageSoAllItsActivitiesFollowIt() = runTest {
        settings.state.value = readySettings("cam.pkg")
        ready { r -> assertTrue(r.packages.first { it.key.packageName == "cam.pkg" }.hidden); assertFalse(r.packages.first { it.key.packageName == "mus.pkg" }.hidden) }
    }

    @Test fun aNewActivityInAHiddenPackageIsHiddenToo() = runTest {
        settings.state.value = readySettings("cam.pkg")
        observe().test {
            awaitItem()
            catalog.state.value = CatalogState.Ready(listOf(camA, camB, app("cam.pkg", "cam.pkg.Extra", "Cámara extra"), music))
            val r = awaitItem() as ManageableState.Ready
            val cam = r.packages.first { it.key.packageName == "cam.pkg" }
            assertEquals(3, cam.activityLabels.size); assertTrue(cam.hidden)
        }
    }

    @Test fun uninstalledHiddenPackagesKeepTheirRuleAsATombstoneAndReinstallingKeepsThemHidden() = runTest {
        settings.state.value = readySettings("cam.pkg")
        observe().test {
            awaitItem()
            catalog.state.value = CatalogState.Ready(listOf(music)) // desinstalada
            val gone = awaitItem() as ManageableState.Ready
            val tomb = gone.packages.first { it.key.packageName == "cam.pkg" }
            assertFalse(tomb.installed); assertTrue(tomb.hidden); assertEquals("cam.pkg", tomb.title)
            catalog.state.value = CatalogState.Ready(listOf(camA, music)) // reinstalada
            val back = awaitItem() as ManageableState.Ready
            val cam = back.packages.first { it.key.packageName == "cam.pkg" }
            assertTrue(cam.installed); assertTrue(cam.hidden)
            assertEquals(1, back.packages.count { it.key.packageName == "cam.pkg" })
        }
    }

    @Test fun staleOwnPackageRuleIsNotListed() = runTest {
        settings.state.value = readySettings("com.focuslauncher")
        ready { r -> assertTrue(r.packages.none { it.key.packageName == "com.focuslauncher" }) }
    }

    @Test fun catalogFailureStillListsExistingRulesSoTheyCanBeRemoved() = runTest {
        settings.state.value = readySettings("old.pkg")
        catalog.state.value = CatalogState.Error(CatalogError.QueryFailed)
        ready { r ->
            assertEquals(CatalogError.QueryFailed, r.catalogError)
            assertEquals(listOf("old.pkg"), r.packages.map { it.key.packageName })
        }
    }

    @Test fun pinEnabledIsProjectedAndPolicyErrorsBlock() = runTest {
        settings.state.value = readySettings(pin = true)
        ready { assertTrue(it.pinEnabled) }
        settings.state.value = SettingsReadState.ReadError(ReadErrorKind.StorageUnavailable)
        observe().test { assertTrue(awaitItem() is ManageableState.Blocked) }
        settings.state.value = SettingsReadState.Loading
        observe().test { assertEquals(ManageableState.Loading, awaitItem()) }
    }
}
