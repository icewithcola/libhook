@file:Suppress("DEPRECATION")

package uk.kagurach.libhook.legacy

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import uk.kagurach.libhook.common.DefaultLogger
import uk.kagurach.libhook.common.HookBackend
import uk.kagurach.libhook.common.HookBackendCoordinator
import uk.kagurach.libhook.common.HookContext
import uk.kagurach.libhook.common.HookLogger
import uk.kagurach.libhook.common.HookPhase
import uk.kagurach.libhook.common.HookRegistry
import uk.kagurach.libhook.common.HookRuntime
import uk.kagurach.libhook.legacy.internal.LegacyHookBackendAdapter
import java.util.concurrent.ConcurrentHashMap

/**
 * Legacy Xposed compatibility entry point.
 *
 * Register hooks with annotated [providers], the [configure] DSL, or both. Prefer
 * [uk.kagurach.libhook.modern.LibXposedHookLoader] for new modules; this entry remains useful
 * when a module also needs to run on legacy Xposed implementations.
 */
@Deprecated(
    message = "Legacy Xposed is only a compatibility fallback. Use LibXposedHookLoader instead.",
    level = DeprecationLevel.WARNING,
)
abstract class HookLoader : IXposedHookLoadPackage {

    /** Hook provider classes scanned for [uk.kagurach.libhook.common.Hook]-annotated methods. */
    protected open val providers: List<Class<*>> get() = emptyList()

    /** Logger used by lifecycle and provider dispatch; defaults to Logcat. */
    private val defaultLogger: HookLogger by lazy { DefaultLogger }
    protected open val logger: HookLogger get() = defaultLogger

    /** DSL extension point for programmatic hook registration. */
    protected open fun HookRegistry.configure() { }

    private val tag: String = "HookLoader"

    /** Process keys whose Application.attach method has already been hooked. */
    private val installedProcesses = ConcurrentHashMap.newKeySet<String>()

    private val registry: HookRegistry by lazy {
        HookRuntime.createRegistry(providers, { configure() }, logger, tag)
    }

    final override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (HookBackendCoordinator.isModernActive()) {
            logger.d(tag, "libXposed backend is active, skip legacy dispatch")
            return
        }
        if (lpparam == null) {
            logger.w(tag, "lpparam is null, skip")
            return
        }
        val pkg = lpparam.packageName
        val classLoader = lpparam.classLoader
        // The callback already exposes the process name, avoiding hidden-API reflection.
        val process = lpparam.processName
        logger.d(tag, "handleLoadPackage pkg=$pkg process=$process")

        val matched = HookRuntime.matchingEntries(registry, pkg, process)
        if (matched.isEmpty()) {
            logger.d(tag, "no matched hooks for pkg=$pkg process=$process")
            return
        }

        // EARLY hooks run immediately.
        val early = matched.filter { it.phase == HookPhase.EARLY }
        if (early.isNotEmpty()) {
            val ctx = HookContext(
                packageName = pkg,
                processName = process ?: pkg,
                classLoader = classLoader,
                applicationContext = null,
                phase = HookPhase.EARLY,
                backend = HookBackend.LEGACY,
                logger = logger,
                adapter = LegacyHookBackendAdapter(lpparam),
            )
            HookRuntime.dispatch(early, ctx, logger, tag)
        }

        // APPLICATION hooks run after Application.attach (one attach hook per process).
        val pendingApp = matched.filter { it.phase == HookPhase.APPLICATION }
        if (pendingApp.isEmpty()) return

        val processKey = "$pkg/$process"
        if (!installedProcesses.add(processKey)) {
            logger.w(tag, "APPLICATION hooks already installed for $processKey, skip re-hook of Application.attach")
            return
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (HookBackendCoordinator.isModernActive()) {
                            logger.d(tag, "libXposed backend became active, skip legacy APPLICATION dispatch")
                            return
                        }
                        if (param == null || param.args == null) {
                            logger.w(tag, "Application.attach got null param, skip")
                            return
                        }
                        val contextArg = param.args[0] as? Context
                        if (contextArg == null) {
                            logger.w(tag, "Application.attach arg0 not Context: ${param.args[0]?.javaClass?.name}")
                            return
                        }
                        val ctx = HookContext(
                            packageName = pkg,
                            processName = process ?: pkg,
                            classLoader = classLoader,
                            applicationContext = contextArg,
                            phase = HookPhase.APPLICATION,
                            backend = HookBackend.LEGACY,
                            logger = logger,
                            adapter = LegacyHookBackendAdapter(lpparam),
                        )
                        HookRuntime.dispatch(pendingApp, ctx, logger, tag)
                    }
                },
            )
        }.onFailure { e ->
            installedProcesses.remove(processKey)
            logger.e(tag, "Failed to hook Application.attach pkg=$pkg process=$process", e)
        }
    }
}

/** Convenience compatibility entry that accepts hook provider classes as constructor arguments. */
@Deprecated(
    message = "Legacy Xposed is only a compatibility fallback. Use SimpleLibXposedHookLoader instead.",
    level = DeprecationLevel.WARNING,
)
open class SimpleHookLoader(
    vararg providerClasses: Class<*>,
) : HookLoader() {
    override val providers: List<Class<*>> = providerClasses.toList()
}
