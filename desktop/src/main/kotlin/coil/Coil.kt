package coil

import android.content.Context
import coil.request.ImageRequest
import coil3.SingletonImageLoader

/** Coil 2's singleton loader, for prefetching tiles: enqueue goes to Coil 3's. */
object Coil {
    fun imageLoader(context: Context): ImageLoader = ImageLoader

    object ImageLoader {
        fun enqueue(request: ImageRequest) {
            SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).enqueue(request.real)
        }
    }
}
