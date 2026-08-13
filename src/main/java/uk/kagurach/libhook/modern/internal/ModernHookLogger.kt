package uk.kagurach.libhook.modern.internal

import android.util.Log
import io.github.libxposed.api.XposedInterface
import uk.kagurach.libhook.common.HookLogger

internal class ModernHookLogger(private val xposed: XposedInterface) : HookLogger {
    override fun d(tag: String, msg: String, t: Throwable?) = write(Log.DEBUG, tag, msg, t)
    override fun i(tag: String, msg: String, t: Throwable?) = write(Log.INFO, tag, msg, t)
    override fun w(tag: String, msg: String, t: Throwable?) = write(Log.WARN, tag, msg, t)
    override fun e(tag: String, msg: String, t: Throwable?) = write(Log.ERROR, tag, msg, t)

    private fun write(priority: Int, tag: String, msg: String, throwable: Throwable?) {
        if (throwable == null) xposed.log(priority, tag, msg) else xposed.log(priority, tag, msg, throwable)
    }
}
