# ADR 0003 — Pinned build versions

**Date:** 2026-11-22 · **Status:** accepted (written in B0)
**Context:** `Tech-Stack.md` §14 lists version floors and requires every floor to be verified
against its current release before pinning, with the result recorded in an ADR. This is that
record. `gradle/libs.versions.toml` is the single place the build reads these numbers from.

## Verified pins

Checked on 2026-11-22 against the publishers' own metadata (Google Maven
`maven-metadata.xml`, Maven Central `maven-metadata.xml`, the Gradle Plugin Portal and
`services.gradle.org/versions/current`), then exercised by the B0 build.

| Component | Pinned | Evidence and why this one |
|---|---|---|
| Gradle (wrapper) | 9.9.2 | `services.gradle.org/versions/current` says 9.9.2 is the current release (built 2026-11-19). The wrapper is committed; the system Gradle was used only to generate it. |
| JDK | 21 (Temurin 21.0.13.1) | Installed on the workstation; AGP 9.5 requires JDK 17 or newer. |
| Android Gradle Plugin | 9.5.2 | Latest stable on Google Maven (9.6.0-alpha05 is the newest alpha). AGP 9.5 requires Gradle ≥ 9.6, SDK Build Tools 36.0.0 and JDK 17 — all satisfied. |
| Kotlin | 2.2.10 | **AGP 9.5.2's built-in Kotlin version** (`kotlin-gradle-plugin:2.2.10` in AGP's POM). See decision 1. Kotlin 2.4.21 is the current release and satisfies the §14 floor of 2.1+, but it is not what AGP 9.5 compiles with. |
| Compose Compiler Gradle plugin | 2.2.10 | Must equal the Kotlin version in use. |
| Compose BOM | 2026.11.01 | Latest stable on Google Maven; resolves Compose 1.13.0. |
| activity-compose | 1.13.0 | Latest stable (1.14.0-alpha03 is the newest alpha). |
| core-ktx | 1.19.1 | Latest stable (1.20.0-alpha01 is the newest alpha). |
| lifecycle (runtime-ktx) | 2.11.1 | Latest stable (2.12.0-alpha06 is the newest alpha). |
| compileSdk | 37, minor level 2 | `platforms;android-37.2` is installed and Compose 1.13.0 refuses anything below 37.1. |
| targetSdk | 37 | Current release platform (Android 17). |
| minSdk | 28 | Health Connect floor (`Tech-Stack.md` §8, §14). |
| Bytecode target | Java 17 | Toolchain is JDK 21; `jvmTarget`/`options.release` are 17 for every module, which D8 desugars. Two build-infrastructure tests assert the class-file level — `core`'s `ToolchainTest` for the pure modules and `app-android`'s `BytecodeTargetTest` for the Android side — so drift in either half fails `check`. |
| detekt | 1.23.8 | Latest stable (`dev.detekt:detekt-gradle-plugin` 2.0.0-alpha.6 is an alpha). |
| ktlint Gradle plugin | 14.2.0 | Latest on the Plugin Portal. |
| JUnit Jupiter | 6.1.3 | Latest stable (6.2.0-M1 is a milestone). |
| kotlin-test (junit5) | 2.2.10 | Matches the Kotlin version. |
| kotlinx-coroutines | 1.11.0 | Latest stable. |
| kotlinx-serialization | 1.13.0 | Latest stable. |
| kotlinx-datetime | 0.8.0 | Latest stable (`0.8.0-0.6.x-compat` is a compatibility artifact, not a newer release). |
| SQLDelight | 2.4.1 | Latest stable 2.x. **Pinned, not yet applied** (B2). |
| Koin | 4.2.2 | Latest stable (4.3.0-Beta1 is a beta). **Pinned, not yet applied** (B3). |
| Ktor | 3.6.1 | Latest stable 3.x. **Pinned, not yet applied** (B13). |
| Kotest | 6.2.7 | Latest stable. **Pinned, not yet applied** (B2 property tests). |
| KSP | not used | No annotation processor is in the stack: SQLDelight generates through its own Gradle plugin and Koin is runtime DI. No KSP version is pinned, so none can drift. |

PostgreSQL 16+ is a server runtime (`Tech-Stack.md` §7), not a Gradle dependency; it is pinned in
B13 with the server image.

## Added in B1 (verified 2026-11-23)

B1 applies the screenshot testing `Tech-Stack.md` §10 names and the one behavioural dependency the
components need. Roborazzi, Robolectric, JUnit 4, the vintage engine and the Compose UI test
artefacts are **test-only**: none is on a shipped classpath, and `checkLicences` still reviews only
what ships. Compose Material 3 **does** ship, as a behaviour primitive only (DESIGN.md Appendix B:
`ModalBottomSheet`, `Icon`, `Text`, `LocalTextStyle`; never its palette or shapes).

| Component | Pinned | Evidence and why this one |
|---|---|---|
| Roborazzi (plugin and libraries) | 1.79.0 | Latest stable on Maven Central (`maven-metadata.xml`, last updated 2026-11-12; 1.80 is not published). Apache-2.0. |
| Robolectric | 4.18.1 | Latest stable on Maven Central (4.18.1, last updated 2026-11-18). Apache-2.0. Its native graphics mode renders Compose on the JVM. |
| androidx.test.ext:junit | 1.3.0 | Latest stable on Google Maven (1.4.0-alpha04 is the newest alpha). Apache-2.0. |
| JUnit 4 | 4.13.2 | The last JUnit 4 release; Robolectric and Compose UI rules run on it. EPL-1.0. |
| JUnit Vintage engine | 6.1.3 | Matches the Jupiter pin, so JUnit 4 test classes run under the module's existing `useJUnitPlatform()`. EPL-2.0. |
| Compose UI test artefacts | via the 2026.11.01 BOM | From the BOM already pinned; test-only. |
| Compose Material 3 | via the 2026.11.01 BOM | From the BOM already pinned; the only new shipped module family. Its licence and the three transitive ripple modules are reviewed in `gradle/client-licences.txt`. |

The Roborazzi plugin is applied only to `:ui`; it creates the record/verify/compare tasks and passes
the mode to the test JVM. Screenshot goldens are committed under `ui/src/test/screenshots` and are
re-recorded only with `recordRoborazziDebug`.

## Decisions this pinning forced

1. **Built-in Kotlin, not the `kotlin-android` plugin.** AGP 9 enables its own Kotlin compilation
   and rejects `org.jetbrains.kotlin.android`. The Android modules therefore apply only
   `com.android.application` / `com.android.library` plus the Compose compiler plugin. Kotlin is
   held at AGP's bundled version rather than the newest release: forcing a different KGP into
   built-in Kotlin is unsupported, and one Kotlin version across the app and the pure modules
   matters more than the newest patch. Revisit when AGP ships a newer bundled Kotlin.
2. **compileSdk is pinned to 37 *minor 2*.** Android 17 ships minor API levels; AndroidX Compose
   1.13.0 requires API 37.1 or later, so `compileSdkMinor = 2` is part of the pin, not incidental.
3. **Pure modules are Kotlin/JVM only, with a JDK 21 toolchain targeting Java 17.** The KMP plugin
   is deliberately absent until the iOS gate opens (`pr.md` §13.17); the portability guard, not a
   KMP target list, keeps `android.*` out.
4. **The licence guard needs a reviewed registry, not a blocklist.** A blocklist can only catch
   licences it already knows. `gradle/client-licences.txt` lists every module on the shipped
   runtime classpath with its SPDX licence, and `checkLicences` fails on both an unreviewed module
   and a GPL-family entry, so a new dependency cannot reach the app without a licence review.
   Where a dependency offers a choice of licences, the permissive arm is recorded.
5. **Test dependencies are declared in each module's build file, not in a convention plugin.**
   Version-catalog accessors are not available to precompiled script plugins in build-logic, and
   the alternative — a second copy of the versions — is worse. The catalogue stays the only source
   of version numbers.
6. **ktlint's function-naming rule is off; Detekt's is on with `ignoreAnnotated: [Composable]`.**
   Compose composables are PascalCase (`DESIGN.md` §5), and ktlint cannot see the annotation.
   Detekt can, so ordinary functions stay covered.
7. **`data/` is a source module.** The old `.gitignore` ignored `data/` to keep local health data
   out of git; that would have silently excluded the `:data` module. Local data is now
   `local-data/` and the module is tracked.

## What this ADR does not claim

- SQLDelight 2.4.1, Koin 4.2.2, Ktor 3.6.1 and Kotest 6.2.7 are pinned but **not yet applied**.
  Their compatibility with Gradle 9.9.2 and Kotlin 2.2.10 is proven by the phase that applies
  them (B2, B3, B13), and this ADR is amended with a dated line if a pin has to move.
- The GitHub Actions workflow is written but has never run: nothing was pushed from a build
  session. The SDK package ids it installs (`platforms;android-37.2`, `build-tools;37.0.0`) are
  read from the installed SDK's own `package.xml`, not guessed. The owner's Actions quota is spent
  until the next billing period, so the first run is deferred; it can be started by hand with
  `workflow_dispatch` when the quota returns, and it checks nothing that the local
  `./gradlew check` and `:app-android:assembleDebug` runs above did not already check.
