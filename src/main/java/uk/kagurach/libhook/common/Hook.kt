package uk.kagurach.libhook.common

/**
 * Marks a provider method as a hook installation entry point.
 *
 * The method is called once when its target package and process match. Its signature must be
 * `fun(HookContext)`; this is the only backend-neutral signature.
 *
 * @property package Target package name. An empty string matches every package.
 * @property process Target process. An empty string matches every process; a value beginning
 * with `:` (for example, `:push`) is matched relative to the target package.
 * @property phase Installation phase. [HookPhase.EARLY] is supported only by the legacy backend;
 * [HookPhase.APPLICATION] is supported by both backends.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Hook(
    val `package`: String = "",
    val process: String = "",
    val phase: HookPhase = HookPhase.APPLICATION,
)

/** Hook installation phase. */
enum class HookPhase {
    /** Legacy `handleLoadPackage` phase; only a class loader is available. */
    EARLY,

    /** After `Application.attach`; a context is available. This is the default. */
    APPLICATION,
}
