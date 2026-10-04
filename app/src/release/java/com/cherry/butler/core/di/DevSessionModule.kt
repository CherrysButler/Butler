package com.cherry.butler.core.di

import com.cherry.butler.core.auth.DevSessionSink
import com.cherry.butler.core.auth.NoOpDevSessionSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Release variant: nothing is ever mirrored off encrypted storage. */
@Module
@InstallIn(SingletonComponent::class)
object DevSessionModule {

    @Provides
    @Singleton
    fun provideDevSessionSink(): DevSessionSink = NoOpDevSessionSink()
}
