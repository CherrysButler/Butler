package com.cherry.butler.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Frame
import java.awt.event.WindowEvent

/**
 * Butler draws its own title bar. The window keeps the system's frame: its resize edges, its
 * shadow and rounded corners, snapping and Win+arrow. Only the caption goes: the client area
 * is let reach the window's top edge, and the strip Butler draws there is told to Windows as
 * caption, so dragging it moves the window, a double-click maximizes, and a right-click opens
 * the system menu. The bar's own buttons stay Butler's to click.
 */
internal object WindowFrame {
    /** Whether the caption is gone and Butler's bar should be drawn in its place. */
    var custom by mutableStateOf(false)
        private set

    /** Whether the window fills its screen, for the maximize key's glyph. */
    var maximized by mutableStateOf(false)
        private set

    /** The bar's height and where its buttons begin, in window pixels; the bar sets them as it is laid out. */
    @Volatile var barHeight = 0
    @Volatile var buttonsFrom = Int.MAX_VALUE

    private var window: ComposeWindow? = null

    /** Kept for as long as the window lives: Windows calls it, and the collector must not free it. */
    private var proc: WinUser.WindowProc? = null
    private var previous: Pointer? = null

    fun install(window: ComposeWindow) {
        if (proc != null || !System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching {
            val hwnd = HWND(Pointer(window.windowHandle))
            val user32 = User32.INSTANCE
            val p = WinUser.WindowProc { h, msg, wParam, lParam -> handle(h, msg, wParam, lParam) }
            proc = p
            previous = user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(p))
            this.window = window
            maximized = window.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
            window.addWindowStateListener { e -> maximized = e.newState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH }
            // Skia draws into a child window over the whole client area, and Windows asks the
            // window under the pointer what it is: the child passes the bar and the top edge on.
            user32.EnumChildWindows(hwnd, { child, _ -> passThrough(child, hwnd); true }, null)
            // Asks Windows to measure the frame again, now without the caption.
            user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
            custom = true
        }.onFailure { proc = null }
    }

    /**
     * The look's ground behind everything Butler draws: the toolkit's components and the
     * native windows' class brushes, so nothing shows the toolkit's white while the window
     * opens or a dialog comes up.
     */
    fun paintBehind(window: ComposeWindow, argb: Int) {
        val color = java.awt.Color(argb, false)
        fun paint(c: java.awt.Component) {
            c.background = color
            if (c is java.awt.Container) c.components.forEach(::paint)
        }
        paint(window)
        groundPaint.color = argb or (0xFF shl 24)
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching {
            val colorRef = (color.blue shl 16) or (color.green shl 8) or color.red
            val brush = Gdi.INSTANCE.CreateSolidBrush(colorRef) ?: return
            val hwnd = HWND(Pointer(window.windowHandle))
            Ex.INSTANCE.SetClassLongPtrW(hwnd, GCLP_HBRBACKGROUND, brush)
            User32.INSTANCE.EnumChildWindows(hwnd, { child, _ -> Ex.INSTANCE.SetClassLongPtrW(child, GCLP_HBRBACKGROUND, brush); true }, null)
            val old = ground
            ground = brush
            old?.let { Gdi.INSTANCE.DeleteObject(it) }
        }
    }

    /** The child windows' procedures, kept as [proc] is, each with the one it replaced. */
    private val children = mutableListOf<Pair<WinUser.WindowProc, Pointer?>>()

    /**
     * Makes [child] see-through to the pointer over the title bar (and the top resize edge):
     * there it answers "transparent", so Windows asks the frame beneath, which says caption.
     */
    private fun passThrough(child: HWND, frame: HWND) {
        var before: Pointer? = null
        val p = WinUser.WindowProc { h, msg, wParam, lParam ->
            if (msg == WM_NCHITTEST) {
                try {
                    val raw = lParam.toLong()
                    val point = Point(x = (raw and 0xFFFF).toShort().toInt(), y = ((raw shr 16) and 0xFFFF).toShort().toInt())
                    Ex.INSTANCE.ScreenToClient(frame, point)
                    val edge = !Ex.INSTANCE.IsZoomed(frame) && point.y < frameY(frame)
                    if (edge || (point.y < barHeight && point.x < buttonsFrom)) return@WindowProc LRESULT(HTTRANSPARENT)
                } catch (_: Throwable) {
                }
            }
            User32.INSTANCE.CallWindowProc(before, h, msg, wParam, lParam)
        }
        before = User32.INSTANCE.SetWindowLongPtr(child, GWLP_WNDPROC, CallbackReference.getFunctionPointer(p))
        children += p to before
    }

    private val groundPaint = org.jetbrains.skia.Paint()

    /**
     * Every frame starts with the ground, far past the window's edges. Skia clears each frame
     * to white before Butler draws, and while the window is resized it shows a frame at the
     * new size made from content laid out at the old one: the strip just gained was that
     * white. With the ground recorded first, and wider than any screen, the strip is the
     * look's own background instead, until the content catches up a frame later.
     */
    fun groundUnderFrames(window: java.awt.Window) {
        val layer = FramePacer.findLayer(window) ?: return
        val inner = layer.renderDelegate ?: return
        layer.renderDelegate = object : org.jetbrains.skiko.SkikoRenderDelegate {
            override fun onRender(canvas: org.jetbrains.skia.Canvas, width: Int, height: Int, nanoTime: Long) {
                canvas.drawRect(org.jetbrains.skia.Rect.makeWH(16_384f, 16_384f), groundPaint)
                inner.onRender(canvas, width, height, nanoTime)
            }
        }
    }

    /** The brush the window's background is erased with, in the look's ground. */
    @Volatile private var ground: Pointer? = null

    fun minimize() { window?.let { it.extendedState = it.extendedState or Frame.ICONIFIED } }

    fun toggleMaximized() {
        val w = window ?: return
        w.extendedState = if (maximized) Frame.NORMAL else Frame.MAXIMIZED_BOTH
    }

    /** As the system's close does: the window's own close request decides what happens. */
    fun close() { window?.let { it.dispatchEvent(WindowEvent(it, WindowEvent.WINDOW_CLOSING)) } }

    private fun handle(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
        try {
            when (msg) {
                WM_NCCALCSIZE -> if (wParam.toLong() != 0L) {
                    // Windows works out the client area inside the frame; the top edge is then put
                    // back where the window begins, so the caption's band is Butler's to draw.
                    // Maximized, the window hangs a frame's thickness over the screen's edge: the
                    // top keeps that much in, as the sides already do.
                    val rect = Pointer(lParam.toLong())
                    val top = rect.getInt(4)
                    val result = forward(hwnd, msg, wParam, lParam)
                    rect.setInt(4, if (Ex.INSTANCE.IsZoomed(hwnd)) top + frameY(hwnd) else top)
                    return result
                }
                WM_NCHITTEST -> {
                    val result = forward(hwnd, msg, wParam, lParam)
                    if (result.toLong() != HTCLIENT) return result
                    val raw = lParam.toLong()
                    val point = Point(x = (raw and 0xFFFF).toShort().toInt(), y = ((raw shr 16) and 0xFFFF).toShort().toInt())
                    Ex.INSTANCE.ScreenToClient(hwnd, point)
                    val client = RECT().also { User32.INSTANCE.GetClientRect(hwnd, it) }
                    val width = client.right - client.left
                    val overButtons = point.x >= buttonsFrom
                    // The top edge still resizes, a thinner band over the buttons so they keep most of their height.
                    if (!Ex.INSTANCE.IsZoomed(hwnd)) {
                        val band = frameY(hwnd).let { if (overButtons) it / 2 else it }
                        if (point.y < band) {
                            val corner = frameX(hwnd) * 2
                            return LRESULT(
                                when {
                                    point.x < corner -> HTTOPLEFT
                                    point.x >= width - corner -> HTTOPRIGHT
                                    else -> HTTOP
                                },
                            )
                        }
                    }
                    if (point.y < barHeight && !overButtons) return LRESULT(HTCAPTION)
                    return result
                }
            }
        } catch (_: Throwable) {
            // Whatever went wrong, the window keeps working as Windows would have it.
        }
        return forward(hwnd, msg, wParam, lParam)
    }

    private fun forward(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT =
        User32.INSTANCE.CallWindowProc(previous, hwnd, msg, wParam, lParam)

    private fun frameY(hwnd: HWND): Int {
        val dpi = Ex.INSTANCE.GetDpiForWindow(hwnd)
        return Ex.INSTANCE.GetSystemMetricsForDpi(SM_CYSIZEFRAME, dpi) + Ex.INSTANCE.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
    }

    private fun frameX(hwnd: HWND): Int {
        val dpi = Ex.INSTANCE.GetDpiForWindow(hwnd)
        return Ex.INSTANCE.GetSystemMetricsForDpi(SM_CXSIZEFRAME, dpi) + Ex.INSTANCE.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
    }

    @Structure.FieldOrder("x", "y")
    class Point(@JvmField var x: Int = 0, @JvmField var y: Int = 0) : Structure()

    @Suppress("FunctionName")
    private interface Ex : StdCallLibrary {
        fun IsZoomed(hwnd: HWND): Boolean
        fun ScreenToClient(hwnd: HWND, point: Point): Boolean
        fun GetDpiForWindow(hwnd: HWND): Int
        fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int
        fun SetClassLongPtrW(hwnd: HWND, index: Int, value: Pointer): Pointer?

        companion object {
            val INSTANCE: Ex = Native.load("user32", Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    @Suppress("FunctionName")
    private interface Gdi : StdCallLibrary {
        fun CreateSolidBrush(colorRef: Int): Pointer?
        fun DeleteObject(handle: Pointer): Boolean

        companion object {
            val INSTANCE: Gdi = Native.load("gdi32", Gdi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private const val GWLP_WNDPROC = -4
    private const val GCLP_HBRBACKGROUND = -10
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val HTTRANSPARENT = -1L
    private const val HTCLIENT = 1L
    private const val HTCAPTION = 2L
    private const val HTTOP = 12L
    private const val HTTOPLEFT = 13L
    private const val HTTOPRIGHT = 14L
    private const val SM_CXSIZEFRAME = 32
    private const val SM_CYSIZEFRAME = 33
    private const val SM_CXPADDEDBORDER = 92
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
}
