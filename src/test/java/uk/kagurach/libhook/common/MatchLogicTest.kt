package uk.kagurach.libhook.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLogicTest {
    @Test
    fun `package matching supports exact and wildcard entries`() {
        assertTrue(MatchLogic.matchPackage("", "com.example.app"))
        assertTrue(MatchLogic.matchPackage("com.example.app", "com.example.app"))
        assertFalse(MatchLogic.matchPackage("com.example.other", "com.example.app"))
    }

    @Test
    fun `process matching supports wildcard exact and package-relative entries`() {
        assertTrue(MatchLogic.matchProcess("", "com.example.app", null))
        assertTrue(MatchLogic.matchProcess("com.example.app:remote", "com.example.app", "com.example.app:remote"))
        assertTrue(MatchLogic.matchProcess(":remote", "com.example.app", "com.example.app:remote"))
        assertFalse(MatchLogic.matchProcess(":remote", "com.example.other", "com.example.app:remote"))
        assertFalse(MatchLogic.matchProcess("com.example.app", "com.example.app", null))
    }
}
