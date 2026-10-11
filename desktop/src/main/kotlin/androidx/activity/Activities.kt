package androidx.activity

/** The desktop window stands where the phone's activity would. */
open class ComponentActivity : android.content.Context() {
    override val filesDir get() = com.cherry.butler.desktop.DesktopContext.filesDir
    override val cacheDir get() = com.cherry.butler.desktop.DesktopContext.cacheDir
    override fun getSharedPreferences(name: String, mode: Int) = com.cherry.butler.desktop.DesktopContext.getSharedPreferences(name, mode)
    override fun startActivity(intent: android.content.Intent) = com.cherry.butler.desktop.DesktopContext.startActivity(intent)
    val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()
}
