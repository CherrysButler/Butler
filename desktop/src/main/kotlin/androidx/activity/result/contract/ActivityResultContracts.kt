package androidx.activity.result.contract

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest

/**
 * The phone's pickers as the desktop's file dialogs. Each contract says what it opens and
 * hands back a file: URI, or null when the dialog is closed.
 */
abstract class ActivityResultContract<I, O> {
    abstract fun run(input: I, done: (O) -> Unit)
}

object ActivityResultContracts {
    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
        object VideoOnly : VisualMediaType

        override fun run(input: PickVisualMediaRequest, done: (Uri?) -> Unit) =
            com.cherry.butler.desktop.FilePickers.open("Pick a picture", listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")) { done(it) }

        companion object {
            val ImageOnly = PickVisualMedia.ImageOnly
            val ImageAndVideo = PickVisualMedia.ImageAndVideo
        }
    }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun run(input: Array<String>, done: (Uri?) -> Unit) =
            com.cherry.butler.desktop.FilePickers.open("Open", null) { done(it) }
    }

    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun run(input: String, done: (Uri?) -> Unit) =
            com.cherry.butler.desktop.FilePickers.open("Open", null) { done(it) }
    }

    class CreateDocument(private val mimeType: String) : ActivityResultContract<String, Uri?>() {
        override fun run(input: String, done: (Uri?) -> Unit) =
            com.cherry.butler.desktop.FilePickers.save("Save", input) { done(it) }
    }

    /** The desktop asks nothing of the user to post a toast; every permission is granted. */
    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun run(input: String, done: (Boolean) -> Unit) = done(true)
    }
}
