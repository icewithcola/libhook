package uk.kagurach.libhook.common

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared provider scanner used by all backend entry artifacts.
 *
 * Only `fun(HookContext)` is backend-neutral and supported by both implementations. Static
 * methods are invoked without a receiver. For instance methods, Kotlin `object` providers are
 * resolved through `INSTANCE`; other provider classes need a no-argument constructor.
 *
 * Entries are sorted by their JVM signature so dispatch ordering remains stable across runtimes.
 */
object HookFinder {

    /** Entries collected successfully, together with non-fatal validation diagnostics. */
    data class ScanResult(
        val entries: List<HookRegistry.RegistryEntry>,
        val errors: List<ScanError>,
    )

    data class ScanError(
        val provider: String,
        val method: String,
        val phase: HookPhase,
        val reason: String,
    )

    /** Cached receivers for non-static providers. */
    private val instanceCache = ConcurrentHashMap<Class<*>, Any>()

    /** Converts every annotated provider method into an executable registry entry. */
    fun collect(providers: List<Class<*>>): ScanResult {
        val entries = mutableListOf<HookRegistry.RegistryEntry>()
        val errors = mutableListOf<ScanError>()

        for (provider in providers) {
            for (method in provider.declaredMethods.sortedWith(methodComparator)) {
                val hook = method.getAnnotation(Hook::class.java) ?: continue
                val kind = classifySignature(method.parameterTypes)

                if (kind == SignatureKind.UNSUPPORTED) {
                    errors += ScanError(
                        provider = provider.name,
                        method = "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }})",
                        phase = hook.phase,
                        reason = "unsupported signature; the unified frontend requires (HookContext)",
                    )
                    continue
                }

                val invoke = adapt(method, provider, kind, hook.phase, errors) ?: continue
                entries += HookRegistry.RegistryEntry(
                    pkg = hook.`package`,
                    process = hook.process,
                    phase = hook.phase,
                    providerName = provider.name,
                    methodName = method.name,
                    block = invoke,
                )
            }
        }

        return ScanResult(entries, errors)
    }

    /** Classifies supported provider signatures. */
    fun classifySignature(params: Array<Class<*>>): SignatureKind = when {
        params.size == 1 && params[0] == HookContext::class.java -> SignatureKind.HOOK_CONTEXT
        else -> SignatureKind.UNSUPPORTED
    }

    private fun adapt(
        method: Method,
        provider: Class<*>,
        kind: SignatureKind,
        phase: HookPhase,
        errors: MutableList<ScanError>,
    ): (HookContext.() -> Unit)? {
        try {
            method.isAccessible = true
        } catch (error: Throwable) {
            errors += ScanError(
                provider = provider.name,
                method = describe(method),
                phase = phase,
                reason = "could not make provider method accessible: ${error.javaClass.name}: ${error.message}",
            )
            return null
        }
        val isStatic = Modifier.isStatic(method.modifiers)
        val receiver: Any? = if (isStatic) {
            null
        } else {
            try {
                resolveInstance(provider)
            } catch (e: Throwable) {
                errors += ScanError(
                    provider = provider.name,
                    method = describe(method),
                    phase = phase,
                    reason = "failed to resolve provider instance (need Kotlin object INSTANCE or a no-argument constructor): ${e.javaClass.name}: ${e.message}",
                )
                return null
            }
        }

        return when (kind) {
            SignatureKind.HOOK_CONTEXT -> { context: HookContext ->
                try {
                    method.invoke(receiver, context)
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
                Unit
            }
            SignatureKind.UNSUPPORTED -> return null
        }
    }

    /** Resolves and caches a receiver, preferring a Kotlin object instance. */
    private fun resolveInstance(provider: Class<*>): Any {
        return instanceCache.getOrPut(provider) {
            val instanceField = runCatching { provider.getDeclaredField("INSTANCE") }.getOrNull()
            if (instanceField != null && Modifier.isStatic(instanceField.modifiers)) {
                instanceField.isAccessible = true
                requireNotNull(instanceField.get(null)) { "provider INSTANCE must not be null" }
            } else {
                provider.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            }
        }
    }

    /** Clears cached provider receivers for tests. */
    fun clearCacheForTests() {
        instanceCache.clear()
    }

    private fun describe(method: Method): String =
        "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }})"

    private val methodComparator = compareBy<Method>(
        { it.name },
        { method -> method.parameterTypes.joinToString(",") { it.name } },
        { it.returnType.name },
    )
}

enum class SignatureKind {
    HOOK_CONTEXT,
    UNSUPPORTED,
}
