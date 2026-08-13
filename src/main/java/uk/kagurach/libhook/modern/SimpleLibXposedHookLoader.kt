package uk.kagurach.libhook.modern

/** Convenience modern entry that accepts hook provider classes as constructor arguments. */
open class SimpleLibXposedHookLoader(
    vararg providerClasses: Class<*>,
) : LibXposedHookLoader() {
    override val providers: List<Class<*>> = providerClasses.toList()
}
