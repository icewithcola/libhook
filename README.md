# libhook

`libhook` is a Kotlin library for Android Xposed modules. It provides one hook-provider API that
works with modern libXposed API 102 and, where possible, legacy Xposed as a compatibility
fallback.

The library targets Android API 29 and later. New modules should use the libXposed entry point;
legacy Xposed support is deprecated and intentionally limited to the shared API surface.

## Install

```kotlin
dependencies {
    implementation("uk.kagurach:libhook:<version>")
    compileOnly("io.github.libxposed:api:102.0.0")
}
```

`libhook` does not package either hooking framework. Add the framework API your module uses as a
`compileOnly` dependency. For legacy compatibility, also add
`compileOnly("de.robv.android.xposed:api:82")` and make sure `https://api.xposed.info` is an
available repository.

## Write a modern module

Define provider methods with `@Hook`. A provider method must accept exactly one `HookContext`.

```kotlin
import uk.kagurach.libhook.common.Hook
import uk.kagurach.libhook.common.HookContext
import uk.kagurach.libhook.modern.SimpleLibXposedHookLoader

object ExampleHooks {
    @Hook(package = "com.example.target", process = ":remote")
    fun install(context: HookContext) = with(context) {
        hookMethod(
            className = "com.example.target.Feature",
            name = "isEnabled",
        ) {
            before { result = true }
        }
    }
}

class ModuleEntry : SimpleLibXposedHookLoader(ExampleHooks::class.java)
```

Register `ModuleEntry` in `META-INF/xposed/java_init.list`, one fully qualified class name per
line. libXposed invokes the loader when the target package is ready.

Use `HookRegistry.configure` on a `LibXposedHookLoader` subclass instead when registration needs
runtime Kotlin logic rather than annotations.

```kotlin
class ModuleEntry : LibXposedHookLoader() {
    override fun HookRegistry.configure() {
        onPackage("com.example.target") {
            // Install hooks through this HookContext.
        }
    }
}
```

## Backend behavior

- `HookPhase.APPLICATION` is the default and is supported by both backends. It runs after
  `Application.attach`; `HookContext.applicationContext` is available on legacy Xposed.
- `HookPhase.EARLY` runs during legacy `handleLoadPackage`, before an Android `Context` is
  available. libXposed does not run EARLY providers.
- Empty package or process values match every package or process. A process value such as
  `:remote` is relative to its package; full process names match exactly.
- `ModernHookOptions`, class-initializer hooks, deoptimization, remote preferences, and framework
  metadata require libXposed API 102. They fail explicitly on the legacy backend.
- When both entries are present, the modern loader commits only after its providers install
  successfully. The legacy loader then skips application-hook dispatch, avoiding duplicate hooks.

For a legacy-only module, extend the deprecated `HookLoader` or `SimpleHookLoader` and list the
entry class in `assets/xposed_init`. Keep shared providers limited to the backend-neutral API.

Consumer R8 rules are packaged with the AAR to retain annotated provider methods, their
no-argument receivers, and Kotlin `object` instances for reflection.

## Develop

The repository includes a Nix development shell with JDK 21, the Android SDK, Gradle, and GnuPG.

```sh
nix develop -c ./gradlew --no-daemon check assembleRelease
```

The release AAR is written to `build/outputs/aar/`. Use the following command to verify local
Maven consumption:

```sh
nix develop -c ./gradlew --no-daemon publishToMavenLocal
```

## Release

Pushing a `v*` tag, such as `v0.1.0`, publishes a signed bundle to Maven Central. Before the
first release, verify the `uk.kagurach` namespace in the
[Central Portal](https://central.sonatype.com/) and configure these repository secrets:

Release tags must use `v` followed by SemVer 2.0.0 and be greater than every previous release
tag. Invalid, repeated, or decreasing tags fail CI and are not published.

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` — Central Portal user-token credentials.
- `SIGNING_KEY` — ASCII-armored PGP private key, including its header and footer.
- `SIGNING_PASSWORD` — private-key passphrase.

Published Maven Central versions are immutable.

## License

Licensed under the [Apache License 2.0](LICENSE).
