package com.cherry.butler.core.data

import android.media.ExifInterface
import android.graphics.Matrix
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.cherry.butler.core.data.remote.ProfileRemoteSource
import com.cherry.butler.core.network.ApiError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * A picture from the phone, up to Janitor: scaled to at most [MAX_SIDE] on its long side,
 * re-encoded as WebP (what Janitor stores its own pictures as), then the three-step upload
 * of §23.2. Returns the file name to save on the persona or profile.
 */
@Singleton
class PictureUpload @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remote: ProfileRemoteSource,
    client: OkHttpClient,
) {
    /** The presigned URL breaks if Janitor's headers are added to it. */
    private val plain: OkHttpClient = client.newBuilder().apply { interceptors().clear() }.build()

    /** [type] is `avatar` for a persona, `profile-avatar` for the profile (§29). */
    suspend fun upload(uri: Uri, type: String): String = withContext(Dispatchers.IO) {
        val bytes = encode(uri)
        val slot = remote.uploadSlot(type, "webp")
        val put = Request.Builder().url(slot.url).put(bytes.toRequestBody("image/webp".toMediaType())).build()
        plain.newCall(put).execute().use {
            if (!it.isSuccessful) throw ApiError.Api(code = it.code, janitorCode = "BUTLER_UPLOAD", serverMessage = "Couldn't upload the picture.")
        }
        slot.filename
    }

    private fun encode(uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) throw ApiError.Api(code = 0, janitorCode = "BUTLER_PICTURE", serverMessage = "That file isn't a picture Butler can read.")
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw ApiError.Api(code = 0, janitorCode = "BUTLER_PICTURE", serverMessage = "Couldn't open that picture.")
        // Cameras often store a portrait shot as landscape pixels plus an EXIF turn; the
        // decoder ignores the tag, so the turn is applied here, with the scaling, in one pass.
        val scale = (MAX_SIDE.toFloat() / max(decoded.width, decoded.height)).coerceAtMost(1f)
        val matrix = orientation(uri).apply { postScale(scale, scale) }
        val bitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bitmap.compress(format, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** The rotation and mirroring the file's EXIF tag asks for; identity when it has none. */
    private fun orientation(uri: Uri): Matrix {
        val tag = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return Matrix().apply {
            when (tag) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
    }

    private companion object {
        const val MAX_SIDE = 1024
    }
}
