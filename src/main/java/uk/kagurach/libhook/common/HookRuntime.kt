package uk.kagurach.libhook.common

/**
 * Shared provider-registry and dispatch plumbing for backend entry artifacts.
 *
 * The legacy and modern modules intentionally contain only framework lifecycle integration and
 * their respective [HookBackendAdapter] implementations. Provider discovery, matching, logging,
 * and callback invocation live here so both backends expose identical hook semantics.
 */
object HookRuntime {

    fun createRegistry(
        providers: List<Class<*>>,
        configure: HookRegistry.() -> Unit,
        logger: HookLogger,
        tag: String,
    ): HookRegistry = HookRegistry().also(configure).also { registry ->
        val scan = HookFinder.collect(providers)
        scan.errors.forEach { error ->
            logger.w(
                tag,
                "Skipped @Hook on ${error.provider}.${error.method} [phase=${error.phase}]: ${error.reason}",
            )
        }
        // Annotation entries precede DSL entries for backward-compatible dispatch ordering.
        registry.entries.addAll(0, scan.entries)
    }

    fun matchingEntries(
        registry: HookRegistry,
        packageName: String,
        processName: String?,
        supportedPhases: Set<HookPhase> = HookPhase.entries.toSet(),
    ): List<HookRegistry.RegistryEntry> = registry.entries.filter { entry ->
        entry.phase in supportedPhases &&
            MatchLogic.matchPackage(entry.pkg, packageName) &&
            MatchLogic.matchProcess(entry.process, packageName, processName)
    }

    fun dispatch(
        entries: Iterable<HookRegistry.RegistryEntry>,
        context: HookContext,
        logger: HookLogger,
        tag: String,
        propagateFailures: Boolean = false,
    ) {
        entries.forEach { entry ->
            if (propagateFailures) {
                entry.block(context)
                return@forEach
            }
            runCatching { entry.block(context) }.onFailure { error ->
                logger.e(
                    tag,
                    "${context.phase} hook failed pkg=${context.packageName} " +
                        "process=${context.processName} src=${entry.providerName}.${entry.methodName}",
                    error,
                )
            }
        }
    }
}
