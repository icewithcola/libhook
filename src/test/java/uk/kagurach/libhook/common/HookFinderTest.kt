package uk.kagurach.libhook.common

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import java.lang.reflect.Executable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookFinderTest {
    @After
    fun clearProviderCache() {
        HookFinder.clearCacheForTests()
    }

    @Test
    fun `collects private instance providers and retains the declared phase in diagnostics`() {
        val scan = HookFinder.collect(listOf(PrivateProvider::class.java))

        assertEquals(listOf("install"), scan.entries.map { it.methodName })
        assertEquals(1, scan.errors.size)
        assertEquals(HookPhase.EARLY, scan.errors.single().phase)
    }

    @Test
    fun `sorts provider methods by JVM signature`() {
        val scan = HookFinder.collect(listOf(OutOfOrderProvider::class.java))

        assertEquals(listOf("aInstall", "zInstall"), scan.entries.map { it.methodName })
    }

    @Test
    fun `propagates a provider exception without reflection wrapping`() {
        val scan = HookFinder.collect(listOf(ThrowingProvider::class.java))

        val error = runCatching { scan.entries.single().block(testContext()) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("boom", error?.message)
    }

    private class PrivateProvider private constructor() {
        @Hook(phase = HookPhase.EARLY)
        private fun install(context: HookContext) = Unit

        @Hook(phase = HookPhase.EARLY)
        private fun invalid() = Unit
    }

    private object OutOfOrderProvider {
        @Hook
        fun zInstall(context: HookContext) = Unit

        @Hook
        fun aInstall(context: HookContext) = Unit
    }

    private object ThrowingProvider {
        @Hook
        fun install(context: HookContext): Nothing = throw IllegalStateException("boom")
    }

    private fun testContext() = HookContext(
        packageName = "example.test",
        processName = "example.test",
        classLoader = javaClass.classLoader!!,
        applicationContext = null,
        phase = HookPhase.APPLICATION,
        backend = HookBackend.LIBXPOSED,
        logger = NoOpLogger,
        adapter = NoOpAdapter,
    )

    private object NoOpLogger : HookLogger {
        override fun d(tag: String, msg: String, t: Throwable?) = Unit
        override fun i(tag: String, msg: String, t: Throwable?) = Unit
        override fun w(tag: String, msg: String, t: Throwable?) = Unit
        override fun e(tag: String, msg: String, t: Throwable?) = Unit
    }

    private object NoOpAdapter : HookBackendAdapter {
        override fun hookMethod(
            clazz: Class<*>,
            name: String,
            args: Array<out Any?>,
            callback: HookCallback,
            options: ModernHookOptions,
        ): HookHandle = error("not used")

        override fun hookConstructor(
            clazz: Class<*>,
            args: Array<out Any?>,
            callback: HookCallback,
            options: ModernHookOptions,
        ): HookHandle = error("not used")

        override fun hookClassInitializer(
            clazz: Class<*>,
            callback: HookCallback,
            options: ModernHookOptions,
        ): HookHandle = error("not used")

        override fun deoptimize(executable: Executable): Boolean = error("not used")
        override fun frameworkInfo(): FrameworkInfo = error("not used")
        override fun moduleApplicationInfo(): ApplicationInfo = error("not used")
        override fun remotePreferences(group: String): SharedPreferences = error("not used")
        override fun getStaticField(clazz: Class<*>, name: String): Any? = error("not used")
        override fun setStaticField(clazz: Class<*>, name: String, value: Any?) = error("not used")
        override fun getObjectField(obj: Any, name: String): Any? = error("not used")
        override fun setObjectField(obj: Any, name: String, value: Any?) = error("not used")
        override fun legacyRaw(): Any = error("not used")
    }
}
