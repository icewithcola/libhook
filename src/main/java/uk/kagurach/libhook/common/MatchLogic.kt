package uk.kagurach.libhook.common

/**
 * Shared package and process matching rules used by all backends.
 *
 * An empty process matches every process. A process beginning with `:` is relative to the package
 * name (`:push` matches `com.example:push`); every other process name is matched exactly.
 */
object MatchLogic {

    fun matchPackage(entryPkg: String, currentPkg: String): Boolean =
        entryPkg.isEmpty() || entryPkg == currentPkg

    fun matchProcess(entryProcess: String, currentPkg: String, currentProcess: String?): Boolean {
        if (entryProcess.isEmpty()) return true
        if (currentProcess == null) return false
        if (currentProcess == entryProcess) return true
        // A process suffix is relative to its matching package.
        if (entryProcess.startsWith(":") && currentProcess == currentPkg + entryProcess) return true
        return false
    }
}
