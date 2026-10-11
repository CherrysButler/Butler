package android.content.pm

object PackageManager {
    const val PERMISSION_GRANTED = 0
    const val PERMISSION_DENIED = -1
    const val GET_SIGNATURES = 64
    const val GET_SIGNING_CERTIFICATES = 0x08000000

    fun getPackageInfo(name: String, flags: Int): PackageInfo = PackageInfo()
}

class PackageInfo {
    val versionName: String = com.cherry.butler.BuildConfig.VERSION_NAME
    val longVersionCode: Long = com.cherry.butler.BuildConfig.VERSION_CODE.toLong()
    /** A desktop build has no APK signature; the update check skips the key comparison. */
    val signingInfo: SigningInfo? = null
    val signatures: Array<Signature>? = null
}

class SigningInfo { val apkContentsSigners: Array<Signature>? = null }

class Signature(private val bytes: ByteArray) { fun toByteArray(): ByteArray = bytes }

object ServiceInfo { const val FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1 }
