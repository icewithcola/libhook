package uk.kagurach.libhook.modern

import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import uk.kagurach.libhook.common.HookHandle

/**
 * Hot-reload request delivered to the old module generation.
 *
 * [savedState] is passed to [LibXposedHookLoader.onHotReloaded] in the new generation. It must
 * be classloader-neutral: use primitives, strings, arrays, framework bundles, and similarly safe
 * values only.
 */
class HotReloadRequest internal constructor(
    val extras: Bundle?,
    val installedHooks: List<HookHandle>,
) {
    private val cleanupActions = mutableListOf<() -> Unit>()
    /**
     * State transferred to the new generation.
     *
     * libhook reserves a [HotReloadEnvelope] internally, so this value is restored as
     * [HotReloadedContext.savedState] without losing the package name required to replay hooks.
     */
    var savedState: Any? = null

    /**
     * Registers cleanup that runs before this generation retires.
     *
     * Use it for module-owned threads, native callbacks, global references, and references placed
     * in target-process objects. Actions run once in reverse registration order after
     * [LibXposedHookLoader.onPrepareHotReload] accepts the reload. Hook handles are unhooked by
     * [LibXposedHookLoader] automatically.
     */
    fun cleanup(action: () -> Unit) {
        cleanupActions += action
    }

    internal fun release() {
        val actions = cleanupActions.asReversed().toList()
        cleanupActions.clear()

        var failure: Throwable? = null
        actions.forEach { action ->
            runCatching(action).onFailure { error ->
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}

/** Internal classloader-neutral envelope stored in API 102's saved-instance-state slot. */
internal data class HotReloadEnvelope(
    val packageName: String?,
    val userState: Any?,
)

/** Data made available to a new module generation after a successful API 102 hot reload. */
data class HotReloadedContext(
    val processName: String,
    val extras: Bundle?,
    val savedState: Any?,
    /** Raw API 102 handles created by the old generation, for advanced replacement workflows. */
    val oldHookHandles: List<XposedInterface.HookHandle>,
)
