package com.cherry.butler.core.background

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.cherry.butler.MainActivity
import com.cherry.butler.R
import com.cherry.butler.core.design.Palette
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sin

/** Butler on the home screen: the bowtie and the thinking dither, nothing more. */
@AndroidEntryPoint
class ButlerWidget : AppWidgetProvider() {

    @Inject lateinit var widgets: ButlerWidgets

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // A newly placed widget has nothing drawn yet.
        widgets.refresh(force = true)
    }
}

/**
 * Draws the widget. It says nothing about any chat (no names, no lines): a home screen is
 * seen by whoever holds the phone. While a reply is written the dither drifts, as on the
 * chat's thinking line; otherwise it rests, faint and still.
 *
 * Besides that, a placed widget is what keeps some phones from freezing Butler mid-reply:
 * Transsion's "Hiber" leaves apps with a home-screen widget alone.
 */
@Singleton
class ButlerWidgets @Inject constructor(@ApplicationContext private val context: Context) {

    /** What's generating right now, set by [BackgroundReplies]. */
    @Volatile var working: BackgroundReplies.Working? = null

    private val manager get() = AppWidgetManager.getInstance(context)
    private val component get() = ComponentName(context, ButlerWidget::class.java)

    /** Whether the user has a Butler widget on their home screen. */
    fun isPlaced(): Boolean = manager.getAppWidgetIds(component).isNotEmpty()

    /**
     * Asks the launcher to place one. Returns false if it can't (then the user adds it from
     * the launcher's widget list). The launcher shows its own confirmation.
     */
    fun requestPin(): Boolean =
        manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(component, null, null)

    private var drawnWorking: Boolean? = null

    fun refresh(force: Boolean = false) {
        if (!isPlaced()) return
        val busy = working != null
        // The frames don't change while a reply runs; only redraw when the state flips.
        if (busy == drawnWorking && !force) return
        drawnWorking = busy
        val views = RemoteViews(context.packageName, if (busy) R.layout.widget_butler_working else R.layout.widget_butler).apply {
            FRAMES.forEachIndexed { i, id -> setImageViewBitmap(id, field(phase = i / FRAMES.size.toFloat(), live = busy)) }
        }
        views.setOnClickPendingIntent(R.id.widget_root, open())
        manager.updateAppWidget(component, views)
    }

    /** Opens Butler where the user left it; the widget itself never names a chat. */
    private fun open(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, WIDGET_REQUEST, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * One frame of the thinking line's dither (TurnViews.ThinkingLine): dots on a 4x4 Bayer
     * matrix, one band per tile, two waves drifting along the row, rising out of the logo.
     * Dots are 2 dp so they read from across a home screen, and the bitmap is drawn at the
     * screen's own pixels so they stay crisp (the view shows it unscaled). At rest the dots
     * are grey and drift slowly; while a reply is written they turn red and quicken.
     */
    private fun field(phase: Float, live: Boolean): Bitmap {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = (36 * metrics.density).roundToInt()
        val dot = (2 * metrics.density).roundToInt().coerceAtLeast(2)
        val band = 4f * dot
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = (if (live) Palette.Classic.red else Palette.Classic.textLow).toArgb() }
        val fill = if (live) 0.85f else 0.55f
        // Bands span the widget's likely width, not the bitmap's, so the wave reads the same.
        val span = width * 0.72f
        val bands = (width / band).toInt()
        for (i in 0 until bands) {
            val x = (i + 0.5f) * band / span
            val wave = 0.5f + 0.35f * sin((x - phase) * TWO_PI) + 0.15f * sin((x * 2.3f + phase * 1.7f) * TWO_PI)
            val ramp = 0.4f + 0.6f * (x / 0.15f).coerceIn(0f, 1f)
            var level = ((fill * wave * ramp) * (LEVELS + 1)).roundToInt() - 1
            // Never a hole: where the wave dips there is still a trace, so the row reads as one field.
            level = level.coerceAtLeast(0)
            val lit = (level.coerceAtMost(LEVELS - 1) + 1) * 2
            val left = i * band.toInt()
            var y = 0
            while (y < height) {
                for (cy in 0 until 4) for (cx in 0 until 4) {
                    if (BAYER[cy * 4 + cx] < lit) {
                        val dx = left + cx * dot
                        val dy = y + cy * dot
                        canvas.drawRect(dx.toFloat(), dy.toFloat(), (dx + dot).toFloat(), (dy + dot).toFloat(), paint)
                    }
                }
                y += 4 * dot
            }
        }
        return bitmap
    }

    private companion object {
        const val WIDGET_REQUEST = 7_003
        /** Lights 2, 4 … 14 of the 16 cells, as the chat's tiles do. */
        const val LEVELS = 7
        const val TWO_PI = (2.0 * Math.PI).toFloat()
        val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        val FRAMES = intArrayOf(
            R.id.widget_frame0, R.id.widget_frame1, R.id.widget_frame2, R.id.widget_frame3,
            R.id.widget_frame4, R.id.widget_frame5, R.id.widget_frame6, R.id.widget_frame7,
            R.id.widget_frame8, R.id.widget_frame9, R.id.widget_frame10, R.id.widget_frame11,
            R.id.widget_frame12, R.id.widget_frame13, R.id.widget_frame14, R.id.widget_frame15,
        )
    }
}
