package android.content

import android.net.Uri

/** Enough of an Intent for what Butler asks of the phone: open a link, share text. */
class Intent(var action: String? = null, var data: Uri? = null) {
    constructor(context: Context, cls: Class<*>) : this(null, null) { component = cls.name }
    var component: String? = null
    var type: String? = null
    private val extras = mutableMapOf<String, Any?>()

    fun setType(type: String?): Intent = apply { this.type = type }
    fun putExtra(name: String, value: String?): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Long): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Int): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Boolean): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Intent): Intent = apply { extras[name] = value }
    fun getStringExtra(name: String): String? = extras[name] as? String
    fun getLongExtra(name: String, default: Long): Long = extras[name] as? Long ?: default
    fun getIntExtra(name: String, default: Int): Int = extras[name] as? Int ?: default
    fun getBooleanExtra(name: String, default: Boolean): Boolean = extras[name] as? Boolean ?: default
    fun intentExtra(name: String): Intent? = extras[name] as? Intent
    fun removeExtra(name: String) { extras.remove(name) }
    fun addFlags(flags: Int): Intent = this
    fun setFlags(flags: Int): Intent = this
    fun setPackage(name: String?): Intent = this

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_CHOOSER = "android.intent.action.CHOOSER"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_INTENT = "android.intent.extra.INTENT"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000

        fun createChooser(target: Intent, title: CharSequence?): Intent =
            Intent(ACTION_CHOOSER).putExtra(EXTRA_INTENT, target).putExtra(EXTRA_TITLE, title?.toString())
    }
}
