package com.cherry.butler.desktop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.cherry.butler.feature.settings.LockEntryPoint
import com.cherry.butler.feature.settings.WidgetsEntryPoint
import dagger.BindsInstance
import dagger.Component
import dagger.Subcomponent
import javax.inject.Provider
import javax.inject.Singleton

/** What Hilt's view-model component is on Android: every @HiltViewModel, with its handle. */
@Subcomponent(modules = [ViewModelBindings::class])
interface ViewModelComponent {
    fun viewModels(): Map<Class<*>, @JvmSuppressWildcards Provider<ViewModel>>

    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance handle: SavedStateHandle): ViewModelComponent
    }
}

/** Hilt's SingletonComponent on the desktop: the same @Inject graph, built by plain Dagger. */
@Singleton
@Component(modules = [DesktopModule::class])
interface DesktopComponent : WidgetsEntryPoint, LockEntryPoint {
    fun viewModels(): ViewModelComponent.Factory
    fun supabase(): io.github.jan.supabase.SupabaseClient
    fun updates(): com.cherry.butler.core.update.UpdateChecker
    fun fontPrefs(): com.cherry.butler.core.data.FontPrefs
    fun themePrefs(): com.cherry.butler.core.data.ThemePrefs
    fun lastPlace(): com.cherry.butler.core.data.LastPlace
    fun textLook(): com.cherry.butler.core.data.TextLookPrefs
    fun openChats(): com.cherry.butler.core.background.OpenChatRequests
    fun pipeline(): com.cherry.butler.core.generation.SendPipeline
    fun appLock(): com.cherry.butler.core.security.AppLock
    fun sessionKeeper(): com.cherry.butler.core.auth.SessionKeeper
    fun okHttpClient(): okhttp3.OkHttpClient
    fun chatRepository(): com.cherry.butler.core.data.ChatRepository
    fun database(): com.cherry.butler.core.data.local.ButlerDatabase
    fun backgroundReplies(): com.cherry.butler.core.background.BackgroundReplies
}

object DesktopGraph {
    val component: DesktopComponent by lazy { DaggerDesktopComponent.create() }

    fun <T : ViewModel> viewModel(type: Class<T>, handle: SavedStateHandle): T {
        val provider = component.viewModels().create(handle).viewModels()[type]
            ?: error("${type.name} isn't a @HiltViewModel the desktop knows")
        return type.cast(provider.get())
    }
}

/** The screen's navigation arguments, the way Hilt hands them to a view model. */
fun savedState(extras: CreationExtras): SavedStateHandle =
    runCatching { extras.createSavedStateHandle() }.getOrElse { SavedStateHandle() }
