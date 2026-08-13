package uk.kagurach.libhook.common

/**
 * Registry DSL populated through the `configure` extension in a loader subclass:
 *
 * ```
 * class HookEntry : uk.kagurach.libhook.legacy.HookLoader() {
 *     override fun HookRegistry.configure() {
 *         onPackage("com.tencent.news") {
 *             val clazz = findClass("com.tencent.news.debug.DebugUtil")
 *             setStaticField(clazz, "buildTimeInfo", " hooked")
 *         }
 *         onPackage("com.tencent.news") { /* shared legacy and libXposed provider */ }
 *     }
 * }
 * ```
 */
class HookRegistry {
    /**
     * Registered entries. Backend artifacts consume this list; applications normally populate it
     * through [onPackage] and [onAnyPackage].
     */
    val entries = mutableListOf<RegistryEntry>()

    /**
     * Registers a hook installation block.
     *
     * @param pkg Target package name. An empty string matches every package.
     * @param process Target process. An empty string matches every process; a leading `:` makes
     * the process relative to [pkg].
     * @param phase Installation phase. The libXposed frontend supports only
     * [HookPhase.APPLICATION]; [HookPhase.EARLY] is skipped there and handled by legacy Xposed.
     * @param block Installation logic executed on a [HookContext].
     */
    fun onPackage(
        pkg: String,
        process: String = "",
        phase: HookPhase = HookPhase.APPLICATION,
        block: HookContext.() -> Unit,
    ) {
        entries += RegistryEntry(
            pkg = pkg,
            process = process,
            phase = phase,
            providerName = "<dsl>",
            methodName = "onPackage($pkg)",
            block = block,
        )
    }

    /** Equivalent to [onPackage], but matches every package and process. */
    fun onAnyPackage(
        process: String = "",
        phase: HookPhase = HookPhase.APPLICATION,
        block: HookContext.() -> Unit,
    ) = onPackage("", process, phase, block)

    /** Internal representation of an installed provider. */
    data class RegistryEntry(
        val pkg: String,
        val process: String,
        val phase: HookPhase,
        val providerName: String,
        val methodName: String,
        val block: HookContext.() -> Unit,
    )
}
