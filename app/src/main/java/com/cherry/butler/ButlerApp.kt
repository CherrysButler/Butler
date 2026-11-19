package com.cherry.butler

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.config.JanitorEndpointPrefs
import com.cherry.butler.core.generation.SendPipeline
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@HiltAndroidApp
class ButlerApp : Application(), ImageLoaderFactory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppServices {
        fun sendPipeline(): SendPipeline
        fun okHttpClient(): OkHttpClient
    }

    private val services by lazy { EntryPointAccessors.fromApplication(this, AppServices::class.java) }

    override fun onCreate() {
        super.onCreate()
        val processName = if (Build.VERSION.SDK_INT >= 28) getProcessName() else {
            getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        if (processName?.endsWith(EndpointRestartActivity.PROCESS_SUFFIX) == true) return
        JanitorConfig.initialize(JanitorEndpointPrefs(this))
        // The last crash is kept for the diagnostics report; nothing is sent anywhere.
        com.cherry.butler.core.diagnostics.Diagnostics.installCrashKeeper(this)
        // Anything the outbox was doing when the process died picks up where it left off.
        services.sendPipeline().resumeAll()
    }

    /**
     * One image loader for the whole app, with a real disk cache. Avatars are the bulk of
     * what scrolls; decoding them once and reading them back from disk is the difference
     * between a list that glides and one that stutters on every revisit.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { services.okHttpClient().newBuilder().build() }
        // Sized decodes keep each bitmap small; a tenth of the heap holds a few screens of
        // them, and anything older comes back from the disk cache in a few milliseconds.
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.10).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        // The CDN serves immutable, content-addressed files; don't let its headers shorten the cache.
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}
