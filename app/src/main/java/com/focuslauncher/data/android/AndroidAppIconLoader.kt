package com.focuslauncher.data.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.focuslauncher.core.di.DispatcherProvider
import com.focuslauncher.core.ui.AppIconLoader
import com.focuslauncher.domain.model.AppId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Iconos en memoria con LRU acotada por bytes. Nunca se persisten. */
@Singleton
class AndroidAppIconLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : AppIconLoader {

    private data class Key(val id: AppId, val sizePx: Int, val densityDpi: Int, val generation: Int)

    private val cache = object : LruCache<Key, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    @Volatile private var generation = 0

    /** Cambios de paquetes, locale o configuración invalidan los iconos. */
    fun invalidate() {
        generation++
        cache.evictAll()
    }

    override suspend fun load(id: AppId, sizePx: Int): Bitmap = withContext(dispatchers.io) {
        val density = context.resources.configuration.densityDpi
        val key = Key(id, sizePx, density, generation)
        cache.get(key)?.let { return@withContext it }
        val bitmap = runCatching { loadFromSystem(id, sizePx, density) }.getOrNull() ?: fallback(sizePx)
        cache.put(key, bitmap)
        bitmap
    }

    private fun loadFromSystem(id: AppId, sizePx: Int, density: Int): Bitmap? {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val intent = Intent().setComponent(ComponentName(id.packageName, id.activityName))
        val info = launcherApps.resolveActivity(intent, Process.myUserHandle()) ?: return null
        return info.getBadgedIcon(density).toBitmap(sizePx, sizePx)
    }

    private fun fallback(sizePx: Int): Bitmap =
        context.packageManager.defaultActivityIcon.toBitmap(sizePx, sizePx)

    private companion object {
        const val CACHE_BYTES = 8 * 1024 * 1024
    }
}
