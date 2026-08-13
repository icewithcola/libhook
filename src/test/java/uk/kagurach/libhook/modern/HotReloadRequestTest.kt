package uk.kagurach.libhook.modern

import org.junit.Assert.assertEquals
import org.junit.Test

class HotReloadRequestTest {
    @Test
    fun `cleanup runs once in reverse registration order even when an action fails`() {
        val events = mutableListOf<String>()
        val request = HotReloadRequest(extras = null, installedHooks = emptyList())
        request.cleanup { events += "first" }
        request.cleanup {
            events += "second"
            error("expected")
        }
        request.cleanup { events += "third" }

        runCatching { request.release() }
        request.release()

        assertEquals(listOf("third", "second", "first"), events)
    }
}
