package coil.request

import android.content.Context
import coil3.PlatformContext
import coil3.request.ImageRequest as Coil3Request

/** Coil 2's request builder over Coil 3's, with the options Butler sets. */
class ImageRequest internal constructor(internal val real: Coil3Request) {
    class Builder(context: Context) {
        private val b = Coil3Request.Builder(PlatformContext.INSTANCE)
        fun data(data: Any?) = apply { b.data(if (data is android.net.Uri) data.toString() else data) }
        fun size(size: coil.size.Size) = apply { b.size(size.real) }
        fun size(px: Int) = apply { b.size(px) }
        fun size(width: Int, height: Int) = apply { b.size(width, height) }
        fun memoryCacheKey(key: String?) = apply { b.memoryCacheKey(key) }
        fun diskCachePolicy(policy: CachePolicy) = apply { b.diskCachePolicy(policy.real) }
        fun memoryCachePolicy(policy: CachePolicy) = apply { b.memoryCachePolicy(policy.real) }
        fun allowHardware(enable: Boolean) = this
        fun crossfade(enable: Boolean) = this
        fun build(): ImageRequest = ImageRequest(b.build())
    }

    companion object {
        fun unwrap(model: Any?): Any? = when (model) {
            is ImageRequest -> model.real
            is android.net.Uri -> model.toString()
            else -> model
        }
    }
}

enum class CachePolicy(internal val real: coil3.request.CachePolicy) {
    ENABLED(coil3.request.CachePolicy.ENABLED),
    READ_ONLY(coil3.request.CachePolicy.READ_ONLY),
    WRITE_ONLY(coil3.request.CachePolicy.WRITE_ONLY),
    DISABLED(coil3.request.CachePolicy.DISABLED),
}
