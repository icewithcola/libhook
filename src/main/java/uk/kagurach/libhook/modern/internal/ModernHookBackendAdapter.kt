package uk.kagurach.libhook.modern.internal

import io.github.libxposed.api.XposedInterface
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import java.lang.reflect.Executable
import uk.kagurach.libhook.common.FrameworkInfo
import uk.kagurach.libhook.common.HookBackendAdapter
import uk.kagurach.libhook.common.HookCallback
import uk.kagurach.libhook.common.HookExceptionMode
import uk.kagurach.libhook.common.HookHandle
import uk.kagurach.libhook.common.HookParam
import uk.kagurach.libhook.common.ModernHookOptions
import uk.kagurach.libhook.common.UnsupportedHookOperationException

internal class ModernHookBackendAdapter(
    private val xposed: XposedInterface,
    private val onInstalled: (HookHandle) -> Unit,
) : HookBackendAdapter {
    override fun hookMethod(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle = hook(clazz.getDeclaredMethod(name, *args.toParameterTypes()), callback, options)

    override fun hookConstructor(
        clazz: Class<*>,
        args: Array<out Any?>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle = hook(clazz.getDeclaredConstructor(*args.toParameterTypes()), callback, options)

    override fun hookClassInitializer(
        clazz: Class<*>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle = hook(xposed.hookClassInitializer(clazz), callback, options)

    override fun deoptimize(executable: Executable): Boolean = xposed.deoptimize(executable)

    override fun frameworkInfo(): FrameworkInfo = FrameworkInfo(
        apiVersion = xposed.apiVersion,
        name = xposed.frameworkName,
        version = xposed.frameworkVersion,
        versionCode = xposed.frameworkVersionCode,
        properties = xposed.frameworkProperties,
    )

    override fun moduleApplicationInfo(): ApplicationInfo = xposed.moduleApplicationInfo

    override fun remotePreferences(group: String): SharedPreferences = xposed.getRemotePreferences(group)

    override fun getStaticField(clazz: Class<*>, name: String): Any? = clazz.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(null)
    }

    override fun setStaticField(clazz: Class<*>, name: String, value: Any?) {
        clazz.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
    }

    override fun getObjectField(obj: Any, name: String): Any? = obj.javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(obj)
    }

    override fun setObjectField(obj: Any, name: String, value: Any?) {
        obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(obj, value)
    }

    override fun legacyRaw(): Any = throw UnsupportedHookOperationException(
        "legacyRaw() is not available when libXposed API 102 is the active backend",
    )

    private fun hook(executable: Executable, callback: HookCallback, options: ModernHookOptions): HookHandle =
        hook(xposed.hook(executable), callback, options)

    private fun hook(
        builder: XposedInterface.HookBuilder,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle {
        options.priority?.let(builder::setPriority)
        options.id?.let(builder::setId)
        builder.setExceptionMode(
            when (options.exceptionMode) {
                HookExceptionMode.DEFAULT -> XposedInterface.ExceptionMode.DEFAULT
                HookExceptionMode.PROTECTIVE -> XposedInterface.ExceptionMode.PROTECTIVE
                HookExceptionMode.PASSTHROUGH -> XposedInterface.ExceptionMode.PASSTHROUGH
            },
        )
        val raw = builder.intercept { chain ->
            val args = chain.args.toTypedArray()
            val before = HookParam(args, chain.thisObject)
            callback.dispatchBefore(before)
            val result = when {
                before.throwable != null -> throw before.throwable!!
                before.hasResultOverride() -> before.result
                else -> chain.proceed(args)
            }
            val after = HookParam(args, chain.thisObject, result)
            callback.dispatchAfter(after)
            after.throwable?.let { throw it } ?: after.result
        }
        return HookHandle(raw::unhook).also(onInstalled)
    }

    private fun Array<out Any?>.toParameterTypes(): Array<Class<*>> = mapIndexed { index, value ->
        value as? Class<*> ?: throw UnsupportedHookOperationException(
            "libXposed backend requires Class<*> parameter types; argument #$index was ${value?.javaClass?.name ?: "null"}",
        )
    }.toTypedArray()
}
