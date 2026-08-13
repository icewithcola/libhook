@file:Suppress("unused")

package uk.kagurach.libhook.common

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import java.lang.reflect.Executable

/** The runtime backend that is currently executing a provider. */
enum class HookBackend {
    /**
     * Compatibility-only backend. New modules should target [LIBXPOSED].
     *
     * API 102-only helpers fail explicitly instead of silently degrading when this backend is
     * selected.
     */
    @Deprecated(
        message = "Legacy Xposed is a compatibility fallback. Target libXposed API 102 instead.",
        level = DeprecationLevel.WARNING,
    )
    LEGACY,

    LIBXPOSED,
}

/** Thrown when a valid libhook frontend operation is unavailable in the current backend. */
class UnsupportedHookOperationException(message: String) : UnsupportedOperationException(message)

/**
 * Unified hook frontend used by both legacy Xposed and libXposed API 102.
 *
 * The context intentionally exposes only framework-neutral operations. Legacy raw APIs are
 * available through [legacyRaw] only when [backend] is [HookBackend.LEGACY]; modern backends
 * throw [UnsupportedHookOperationException] instead of requiring a second provider source.
 */
class HookContext(
    val packageName: String,
    val processName: String,
    val classLoader: ClassLoader,
    val applicationContext: Context?,
    val phase: HookPhase,
    val backend: HookBackend,
    val logger: HookLogger,
    private val adapter: HookBackendAdapter,
) {
    fun findClass(name: String): Class<*> = Class.forName(name, false, classLoader)

    fun findClassOrNull(name: String): Class<*>? = runCatching { findClass(name) }.getOrNull()

    /**
     * Hooks a declared method. Every argument must be a [Class] parameter type for libXposed;
     * legacy Xposed additionally accepts its historical value-based parameter shorthand.
     */
    fun hookMethod(
        clazz: Class<*>,
        name: String,
        vararg args: Any?,
        block: HookCallback.() -> Unit,
    ): HookHandle = hookMethod(clazz, name, ModernHookOptions(), *args, block = block)

    /**
     * Hooks a method with API 102 options such as a stable [ModernHookOptions.id], priority, and
     * exception mode. Non-default options are unavailable on the deprecated legacy fallback.
     */
    fun hookMethod(
        clazz: Class<*>,
        name: String,
        options: ModernHookOptions,
        vararg args: Any?,
        block: HookCallback.() -> Unit,
    ): HookHandle = adapter.hookMethod(clazz, name, args, HookCallback().apply(block), options)

    fun hookMethod(
        className: String,
        name: String,
        vararg args: Any?,
        block: HookCallback.() -> Unit,
    ): HookHandle = hookMethod(findClass(className), name, *args, block = block)

    fun hookMethod(
        className: String,
        name: String,
        options: ModernHookOptions,
        vararg args: Any?,
        block: HookCallback.() -> Unit,
    ): HookHandle = hookMethod(findClass(className), name, options, *args, block = block)

    fun hookConstructor(
        clazz: Class<*>,
        vararg args: Any?,
        block: HookCallback.() -> Unit,
    ): HookHandle = hookConstructor(clazz, ModernHookOptions(), *args, block = block)

    /** API 102 hook options variant of [hookConstructor]. */
    fun hookConstructor(
        clazz: Class<*>,
        options: ModernHookOptions,
        vararg args: Any?,
        block: HookCallback.() -> Unit,
    ): HookHandle = adapter.hookConstructor(clazz, args, HookCallback().apply(block), options)

    /**
     * Hooks a class static initializer (`<clinit>`). This is a libXposed API 102-only operation.
     * If the class has already been initialized, the callback will never run.
     */
    fun hookClassInitializer(
        clazz: Class<*>,
        options: ModernHookOptions = ModernHookOptions(),
        block: HookCallback.() -> Unit,
    ): HookHandle = adapter.hookClassInitializer(clazz, HookCallback().apply(block), options)

    fun hookClassInitializer(
        className: String,
        options: ModernHookOptions = ModernHookOptions(),
        block: HookCallback.() -> Unit,
    ): HookHandle = hookClassInitializer(findClass(className), options, block)

    /** Requests ART deoptimization for a method/constructor to mitigate inlining. API 102-only. */
    fun deoptimize(executable: Executable): Boolean = adapter.deoptimize(executable)

    /** libXposed framework/runtime information. API 102-only. */
    fun frameworkInfo(): FrameworkInfo = adapter.frameworkInfo()

    /** The module application's [ApplicationInfo]. API 102-only. */
    fun moduleApplicationInfo(): ApplicationInfo = adapter.moduleApplicationInfo()

    /** Read-only remote preferences supplied by the libXposed framework. API 102-only. */
    fun remotePreferences(group: String): SharedPreferences = adapter.remotePreferences(group)

    fun getStaticField(clazz: Class<*>, name: String): Any? = adapter.getStaticField(clazz, name)

    fun setStaticField(clazz: Class<*>, name: String, value: Any?) = adapter.setStaticField(clazz, name, value)

    fun getObjectField(obj: Any, name: String): Any? = adapter.getObjectField(obj, name)

    fun setObjectField(obj: Any, name: String, value: Any?) = adapter.setObjectField(obj, name, value)

    /**
     * Returns the framework-specific legacy load-package object on the legacy backend.
     * It throws on libXposed; prefer the public context properties instead.
     */
    fun legacyRaw(): Any = adapter.legacyRaw()

}

/**
 * Backend bridge used by libhook's legacy and modern artifacts.
 *
 * Most module authors should use [HookContext] instead of implementing this interface directly.
 */
interface HookBackendAdapter {
    fun hookMethod(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle
    fun hookConstructor(
        clazz: Class<*>,
        args: Array<out Any?>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle
    fun hookClassInitializer(
        clazz: Class<*>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle
    fun deoptimize(executable: Executable): Boolean
    fun frameworkInfo(): FrameworkInfo
    fun moduleApplicationInfo(): ApplicationInfo
    fun remotePreferences(group: String): SharedPreferences
    fun getStaticField(clazz: Class<*>, name: String): Any?
    fun setStaticField(clazz: Class<*>, name: String, value: Any?)
    fun getObjectField(obj: Any, name: String): Any?
    fun setObjectField(obj: Any, name: String, value: Any?)
    fun legacyRaw(): Any
}

/** A stable snapshot of the framework identified by libXposed API 102. */
data class FrameworkInfo(
    /** API version implemented by the active libXposed runtime (for example `102`). */
    val apiVersion: Int,
    val name: String,
    val version: String,
    val versionCode: Long,
    val properties: Long,
)
