package uk.kagurach.libhook.common

import java.util.concurrent.atomic.AtomicReference

/**
 * Coordinates the optional libXposed entry with the legacy Xposed entry in one target process.
 *
 * A modern entry claims installation before registering hooks, then commits only after every
 * matching provider succeeds. This prevents two modern entries from installing the same hooks
 * concurrently. The legacy entry remains a fallback until the modern entry commits.
 */
object HookBackendCoordinator {
    enum class State { UNDECIDED, MODERN_INSTALLING, MODERN_ACTIVE, MODERN_FAILED }

    private val state = AtomicReference(State.UNDECIDED)

    internal fun isModernActive(): Boolean = state.get() == State.MODERN_ACTIVE

    /** Claims modern hook installation. Only one loader may hold the claim in a process. */
    internal fun beginModernInstallation(): Boolean =
        state.compareAndSet(State.UNDECIDED, State.MODERN_INSTALLING)

    /** Marks a claimed modern installation as active. */
    internal fun activateModern() {
        state.compareAndSet(State.MODERN_INSTALLING, State.MODERN_ACTIVE)
    }

    /** Releases a failed modern installation to the legacy compatibility fallback. */
    internal fun failModern() {
        state.compareAndSet(State.MODERN_INSTALLING, State.MODERN_FAILED)
    }
}
