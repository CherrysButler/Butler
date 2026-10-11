package android.util

object Log {
    private fun out(level: String, tag: String?, msg: String?, tr: Throwable? = null): Int {
        System.err.println(level + "/" + tag + ": " + msg)
        tr?.printStackTrace()
        return 0
    }
    @JvmStatic fun v(tag: String?, msg: String): Int = out("V", tag, msg)
    @JvmStatic fun d(tag: String?, msg: String): Int = out("D", tag, msg)
    @JvmStatic fun i(tag: String?, msg: String): Int = out("I", tag, msg)
    @JvmStatic fun w(tag: String?, msg: String): Int = out("W", tag, msg)
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = out("W", tag, msg, tr)
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = out("W", tag, null, tr)
    @JvmStatic fun e(tag: String?, msg: String): Int = out("E", tag, msg)
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = out("E", tag, msg, tr)
}
