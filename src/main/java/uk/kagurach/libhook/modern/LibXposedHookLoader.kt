package uk.kagurach.libhook.modern

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import uk.kagurach.libhook.common.HookBackend
import uk.kagurach.libhook.common.HookBackendCoordinator
import uk.kagurach.libhook.common.HookContext
import uk.kagurach.libhook.common.HookHandle
import uk.kagurach.libhook.common.HookLogger
import uk.kagurach.libhook.common.HookPhase
import uk.kagurach.libhook.common.HookRegistry
import uk.kagurach.libhook.common.HookRuntime
import uk.kagurach.libhook.modern.internal.ModernHookBackendAdapter
import uk.kagurach.libhook.modern.internal.ModernHookLogger

/**
 * Modern API 102 entry which executes the same `@Hook` providers and [HookRegistry] DSL as the
 * legacy [uk.kagurach.libhook.legacy.HookLoader].
 */
abstract class LibXposedHookLoader : XposedModule() {
    protected open val providers: List<Class<*>> get() = emptyList()
    protected open fun HookRegistry.configure() {}
    protected open val logger: HookLogger get() = ModernHookLogger(this)

    private val registry: HookRegistry by lazy {
        HookRuntime.createRegistry(providers, { configure() }, logger, "LibXposedHookLoader")
    }
    private val installed = mutableListOf<HookHandle>()
    private var processName = ""
    private var lastPackageReady: PackageReadySnapshot? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
        logger.d(
            "LibXposedHookLoader",
            "module loaded in $processName; runtime API=${apiVersion} " +
                "framework=$frameworkName/$frameworkVersion",
        )
    }

    /** The runtime libXposed API version reported by [XposedInterface.getApiVersion]. */
    protected val runtimeApiVersion: Int get() = apiVersion

    /** Whether the current framework supports the requested API level. */
    protected fun supportsApiVersion(version: Int): Boolean = apiVersion >= version

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (HookBackendCoordinator.isModernActive()) return
        val matched = HookRuntime.matchingEntries(
            registry,
            param.packageName,
            processName,
            supportedPhases = setOf(HookPhase.APPLICATION),
        )
        if (matched.isEmpty()) return
        if (!HookBackendCoordinator.beginModernInstallation()) return

        val snapshot = PackageReadySnapshot(param.packageName, param.classLoader)
        if (install(snapshot, matched)) {
            lastPackageReady = snapshot
            HookBackendCoordinator.activateModern()
        }
    }

    /**
     * Allows hot reload by default.
     *
     * Override this to reject a reload based on [HotReloadRequest.extras], register cleanup, or
     * set a classloader-neutral [HotReloadRequest.savedState] for the next generation. Registered
     * cleanup runs only after this method accepts the reload.
     */
    protected open fun onPrepareHotReload(request: HotReloadRequest): Boolean = true

    /** Invoked in the new generation after hooks are reinstalled from the last package snapshot. */
    protected open fun onHotReloaded(context: HotReloadedContext) = Unit

    final override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        return runCatching {
            val request = HotReloadRequest(
                extras = param.extras,
                installedHooks = installed.toList(),
            )
            val allowed = onPrepareHotReload(request)
            if (allowed) {
                request.release()
                // PackageReady is intentionally not replayed after hot reload, so retain its
                // package name alongside user state in a classloader-neutral envelope.
                param.setSavedInstanceState(
                    HotReloadEnvelope(lastPackageReady?.packageName, request.savedState),
                )
            }
            allowed
        }.onFailure {
            logger.e("LibXposedHookLoader", "hot-reload preparation failed", it)
        }.getOrDefault(false)
    }

    final override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        processName = param.processName
        val saved = param.savedInstanceState as? HotReloadEnvelope
        val packageName = saved?.packageName
        val classLoader = param.oldHookHandles
            .firstOrNull()
            ?.executable
            ?.declaringClass
            ?.classLoader
        if (packageName == null || classLoader == null) {
            logger.w(
                "LibXposedHookLoader",
                "hot reload skipped: no package state or old-hook classloader available",
            )
            param.oldHookHandles.forEach(XposedInterface.HookHandle::unhook)
            return
        }
        val snapshot = PackageReadySnapshot(packageName, classLoader)

        val matched = HookRuntime.matchingEntries(
            registry,
            snapshot.packageName,
            processName,
            supportedPhases = setOf(HookPhase.APPLICATION),
        )
        if (!install(snapshot, matched)) return

        runCatching {
            onHotReloaded(
                HotReloadedContext(
                    processName = processName,
                    extras = param.extras,
                    savedState = saved.userState,
                    oldHookHandles = param.oldHookHandles,
                ),
            )
            param.oldHookHandles.forEach(XposedInterface.HookHandle::unhook)
        }.onFailure { error ->
            rollbackFrom(0)
            logger.e("LibXposedHookLoader", "hot-reload installation failed", error)
        }
    }

    private fun install(
        snapshot: PackageReadySnapshot,
        matched: List<HookRegistry.RegistryEntry>,
    ): Boolean {
        if (matched.isEmpty()) return true
        val start = installed.size
        val context = HookContext(
            packageName = snapshot.packageName,
            processName = processName,
            classLoader = snapshot.classLoader,
            applicationContext = null,
            phase = HookPhase.APPLICATION,
            backend = HookBackend.LIBXPOSED,
            logger = logger,
            adapter = ModernHookBackendAdapter(this, installed::add),
        )
        return try {
            HookRuntime.dispatch(matched, context, logger, "LibXposedHookLoader", propagateFailures = true)
            true
        } catch (t: Throwable) {
            rollbackFrom(start)
            HookBackendCoordinator.failModern()
            logger.e("LibXposedHookLoader", "modern provider installation failed for ${snapshot.packageName}", t)
            false
        }
    }

    private fun rollbackFrom(start: Int) {
        installed.subList(start, installed.size).asReversed().forEach(HookHandle::unhook)
        installed.subList(start, installed.size).clear()
    }

    private data class PackageReadySnapshot(
        val packageName: String,
        val classLoader: ClassLoader,
    )
}
