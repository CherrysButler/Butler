package com.cherry.butler.core.di

import android.content.Context
import com.cherry.butler.core.auth.DevSessionSink
import com.cherry.butler.core.auth.FileDevSessionSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/** Debug variant: the session is mirrored to a file `adb` can read. */
@Module
@InstallIn(SingletonComponent::class)
object DevSessionModule {

    @Provides
    @Singleton
    fun provideDevSessionSink(
        @ApplicationContext context: Context,
        json: Json,
    ): DevSessionSink = FileDevSessionSink(context, json)
}
