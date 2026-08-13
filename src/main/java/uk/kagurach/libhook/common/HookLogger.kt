package uk.kagurach.libhook.common

import android.util.Log

/** Backend-neutral logger used by [HookContext]. */
interface HookLogger {
    fun d(tag: String, msg: String, t: Throwable? = null)
    fun i(tag: String, msg: String, t: Throwable? = null)
    fun w(tag: String, msg: String, t: Throwable? = null)
    fun e(tag: String, msg: String, t: Throwable? = null)
}

/** Outputs to Android Logcat using the supplied tag. */
object DefaultLogger : HookLogger {
    override fun d(tag: String, msg: String, t: Throwable?) { Log.d(tag, msg, t) }
    override fun i(tag: String, msg: String, t: Throwable?) { Log.i(tag, msg, t) }
    override fun w(tag: String, msg: String, t: Throwable?) { Log.w(tag, msg, t) }
    override fun e(tag: String, msg: String, t: Throwable?) { Log.e(tag, msg, t) }
}
