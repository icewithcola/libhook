package uk.kagurach.libhook.legacy.internal

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import uk.kagurach.libhook.common.FrameworkInfo
import uk.kagurach.libhook.common.HookBackendAdapter
import uk.kagurach.libhook.common.HookCallback
import uk.kagurach.libhook.common.HookExceptionMode
import uk.kagurach.libhook.common.HookHandle
import uk.kagurach.libhook.common.HookParam
import uk.kagurach.libhook.common.ModernHookOptions
import uk.kagurach.libhook.common.UnsupportedHookOperationException
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import java.lang.reflect.Executable

internal class LegacyHookBackendAdapter(
    private val packageParam: XC_LoadPackage.LoadPackageParam,
) : HookBackendAdapter {
    override fun hookMethod(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle {
        rejectModernOptions(options)
        val unhook = XposedHelpers.findAndHookMethod(clazz, name, *args, callback.asLegacyCallback())
        return HookHandle(unhook::unhook)
    }

    override fun hookConstructor(
        clazz: Class<*>,
        args: Array<out Any?>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle {
        rejectModernOptions(options)
        val unhook = XposedHelpers.findAndHookConstructor(clazz, *args, callback.asLegacyCallback())
        return HookHandle(unhook::unhook)
    }

    override fun hookClassInitializer(
        clazz: Class<*>,
        callback: HookCallback,
        options: ModernHookOptions,
    ): HookHandle = modernOnly("hookClassInitializer()")

    override fun deoptimize(executable: Executable): Boolean = modernOnly("deoptimize()")

    override fun frameworkInfo(): FrameworkInfo = modernOnly("frameworkInfo()")

    override fun moduleApplicationInfo(): ApplicationInfo = modernOnly("moduleApplicationInfo()")

    override fun remotePreferences(group: String): SharedPreferences = modernOnly("remotePreferences()")

    override fun getStaticField(clazz: Class<*>, name: String): Any? = XposedHelpers.getStaticObjectField(clazz, name)
    override fun setStaticField(clazz: Class<*>, name: String, value: Any?) = XposedHelpers.setStaticObjectField(clazz, name, value)
    override fun getObjectField(obj: Any, name: String): Any? = XposedHelpers.getObjectField(obj, name)
    override fun setObjectField(obj: Any, name: String, value: Any?) = XposedHelpers.setObjectField(obj, name, value)
    override fun legacyRaw(): Any = packageParam

    private fun rejectModernOptions(options: ModernHookOptions) {
        if (options.requiresModernBackend()) {
            throw UnsupportedHookOperationException(
                "ModernHookOptions(id/priority/exceptionMode) requires libXposed API 102; " +
                    "legacy Xposed is a deprecated compatibility fallback",
            )
        }
    }

    private fun <T> modernOnly(helper: String): T = throw UnsupportedHookOperationException(
        "$helper requires libXposed API 102; legacy Xposed is a deprecated compatibility fallback",
    )

    private fun HookCallback.asLegacyCallback() = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val call = HookParam(param.args, param.thisObject, param.result, param.throwable)
            dispatchBefore(call)
            param.args.indices.forEach { param.args[it] = call.args[it] }
            if (call.throwable != null) param.throwable = call.throwable
            else if (call.hasResultOverride()) param.result = call.result
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            val call = HookParam(param.args, param.thisObject, param.result, param.throwable)
            dispatchAfter(call)
            if (call.throwable != null) param.throwable = call.throwable
            else param.result = call.result
        }
    }
}
