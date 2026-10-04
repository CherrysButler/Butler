package com.cherry.butler

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cherry.butler.core.generation.SendPipeline
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ButlerApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var sendPipeline: SendPipeline

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // The last crash is kept for the diagnostics report; nothing is sent anywhere.
        com.cherry.butler.core.diagnostics.Diagnostics.installCrashKeeper(this)
        // Anything the outbox was doing when the process died picks up where it left off.
        sendPipeline.resumeAll()
    }

    /**
     * One image loader for the whole app, with a real disk cache. Avatars are the bulk of
     * what scrolls; decoding them once and reading them back from disk is the difference
     * between a list that glides and one that stutters on every revisit.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { okHttpClient.newBuilder().build() }
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
