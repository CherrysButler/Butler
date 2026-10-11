package dagger.hilt.android

@Target(AnnotationTarget.CLASS)
annotation class AndroidEntryPoint

@Target(AnnotationTarget.CLASS)
annotation class HiltAndroidApp

/** Hilt's entry points are interfaces the desktop component implements. */
object EntryPointAccessors {
    fun <T> fromApplication(context: android.content.Context, entryPoint: Class<T>): T =
        entryPoint.cast(com.cherry.butler.desktop.DesktopGraph.component)
}
