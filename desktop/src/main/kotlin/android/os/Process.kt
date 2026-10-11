package android.os

object Process {
    @JvmStatic fun myPid(): Int = ProcessHandle.current().pid().toInt()
}
