# Contribution guide for agents

## Project shape

- `src/main/java/uk/kagurach/libhook/common`: backend-neutral public API, provider scanning, and
  dispatch.
- `src/main/java/uk/kagurach/libhook/legacy`: deprecated Xposed compatibility entry and adapter.
- `src/main/java/uk/kagurach/libhook/modern`: libXposed API 102 entry, hot reload, and adapter.
- `src/test/java`: JVM unit tests for pure behavior and reflection/dispatch boundaries.

The AAR has `minSdk = 29`, `compileSdk = 36`, and Java 11 bytecode compatibility. Build with JDK
21 through the included Nix shell.

## Working rules

- Preserve the backend-neutral `HookContext` contract. Any modern-only helper must fail clearly on
  legacy Xposed rather than silently degrading.
- Treat lifecycle and hook installation as target-process code: make installation idempotent,
  clean up partial work on failure, and never dispatch the same application hook through both
  backends.
- Provider discovery is reflective. When changing annotations, providers, constructors, or Kotlin
  object handling, update `proguard-rules.pro` and add a regression test.
- Keep public KDoc in clear English. Document timing, backend limitations, and failure behavior
  where they affect a module author.
- Prefer focused JVM tests that do not require a device or emulator. Add instrumentation coverage
  only when Android runtime behavior is essential.
- Do not add framework APIs as `implementation` dependencies; Xposed and libXposed remain
  `compileOnly` so module authors choose their runtime.

## Verification

Run the complete check before committing:

```sh
nix develop -c ./gradlew --no-daemon check assembleRelease
```

For publishing changes, also run:

```sh
nix develop -c ./gradlew --no-daemon publishToMavenLocal
```

Do not publish releases or change Maven Central credentials from this repository. The GitHub
workflow publishes only signed `v*` tags.
