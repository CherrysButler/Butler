package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences

/** The master key is Windows' own: DPAPI ties the file to this Windows user. */
class MasterKey private constructor() {
    enum class KeyScheme { AES256_GCM }
    class Builder(context: Context) {
        fun setKeyScheme(scheme: KeyScheme): Builder = this
        fun build(): MasterKey = MasterKey()
    }
}

object EncryptedSharedPreferences {
    enum class PrefKeyEncryptionScheme { AES256_SIV }
    enum class PrefValueEncryptionScheme { AES256_GCM }

    fun create(
        context: Context,
        fileName: String,
        masterKey: MasterKey,
        keyScheme: PrefKeyEncryptionScheme,
        valueScheme: PrefValueEncryptionScheme,
    ): SharedPreferences = com.cherry.butler.desktop.DesktopPrefs.open(fileName, encrypted = true)
}
