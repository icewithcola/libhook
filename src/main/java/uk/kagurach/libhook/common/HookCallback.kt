package uk.kagurach.libhook.common

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Options backed by libXposed API 102 hook-builder features.
 *
 * The default options are also accepted by the legacy fallback. Any non-default option requires
 * the API 102 backend and throws [UnsupportedHookOperationException] on legacy Xposed.
 */
data class ModernHookOptions(
    val id: String? = null,
    val priority: Int? = null,
    val exceptionMode: HookExceptionMode = HookExceptionMode.DEFAULT,
) {
    internal fun requiresModernBackend(): Boolean =
        id != null || priority != null || exceptionMode != HookExceptionMode.DEFAULT
}

/** API 102 hook exception behavior. */
enum class HookExceptionMode {
    DEFAULT,
    PROTECTIVE,
    PASSTHROUGH,
}

/**
 * Backend-neutral method-hook callback builder.
 *
 * Both legacy Xposed and libXposed execute [before] before the original invocation and [after]
 * afterwards. Setting [HookParam.result] in [before] skips the original invocation; calling
 * [HookParam.throwThrowable] makes it throw instead.
 */
class HookCallback {
    private var beforeFn: (HookParam.() -> Unit)? = null
    private var afterFn: (HookParam.() -> Unit)? = null

    fun before(block: HookParam.() -> Unit) {
        beforeFn = block
    }

    fun after(block: HookParam.() -> Unit) {
        afterFn = block
    }

    /**
     * Invoked by a backend before the original call. This is public only for backend adapters;
     * application code should configure callbacks with [before].
     */
    fun dispatchBefore(param: HookParam) = beforeFn?.invoke(param)

    /**
     * Invoked by a backend after the original call. This is public only for backend adapters;
     * application code should configure callbacks with [after].
     */
    fun dispatchAfter(param: HookParam) = afterFn?.invoke(param)
}

/** Backend-neutral mutable state for one hooked invocation. */
class HookParam(
    val args: Array<Any?>,
    val thisObject: Any?,
    result: Any? = null,
    throwable: Throwable? = null,
) {
    private var resultWasSet = false

    var result: Any? = result
        set(value) {
            field = value
            resultWasSet = true
        }

    var throwable: Throwable? = throwable
        private set

    /** Whether [result] was explicitly assigned by a callback. */
    fun hasResultOverride(): Boolean = resultWasSet

    fun throwThrowable(t: Throwable) {
        throwable = t
    }
}

/** Backend-neutral hook handle. [unhook] is thread-safe and idempotent. */
class HookHandle(private val unhookFn: () -> Unit) {
    private val unhooked = AtomicBoolean(false)

    fun unhook() {
        if (unhooked.compareAndSet(false, true)) runCatching(unhookFn)
    }
}
