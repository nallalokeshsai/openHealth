# openHealth — build log

**What this is:** the memory between sessions. One section per phase, one entry per milestone:
date, what was done, what was run and its result, placeholders left, open issues. The next
session reads this file, not the transcript. Format follows `prompts.md` "Log and finish".

**Checkout:** `C:\dev\openHealth` (moved out of OneDrive in B0 at the owner's instruction; the
OneDrive copy is untouched and can be deleted by the owner). Branch `build/b0-foundation`.
Nothing is pushed from a build session.

---

## B0 — Foundation

**Status:** Completed · 2026-11-22
**Branch:** `build/b0-foundation`
**Commits:** `190e80c` (build, modules, guards, app), `66ea81a` (pinned-version ADR, notices,
CI file, this log) and the third commit that applies the reviewer's fixes (executable wrapper bit,
three missing pins, tighter GPL regex, log corrections, Android bytecode test).

### Owner questions asked (one batched dialog, asked before any work)

1. **Emulator or phone?** Answered: the **existing emulator `openHealth_API_36`** (x86_64,
   google_apis, API 36). `adb devices` had no phone attached; no AVD needed creating.
2. **OneDrive?** Answered: **copy the working tree to `C:\dev\openHealth` and keep the OneDrive
   copy**. `robocopy` moved 129,570 files including the 5 GB `references/`; the session was moved
   with it. The OneDrive original is untouched.

### Milestone 1 — Repository moved out of the synced folder

`robocopy /E /MT:16` to `C:\dev\openHealth`, verified `.git` and the working tree, then moved the
session. `.gitattributes` added (`* text=auto eol=lf`, `*.bat eol=crlf`) and `core.autocrlf`
set false locally so ktlint and git agree on line endings. **Ran:** `git status` clean.

### Milestone 2 — Multi-module Gradle build

Deliverable 1 of `BUILD-ROADMAP.md` B0. Wrapper 9.9.2 committed; `settings.gradle.kts` includes
`build-logic` and the six modules of `Tech-Stack.md` §13 (`:core :data :sync :integrations :ui
:app-android`); `gradle/libs.versions.toml` is the only version source; `build-logic` holds five
convention plugins (`openhealth.kotlin.library`, `openhealth.android.library`,
`openhealth.android.app`, `openhealth.quality`, `openhealth.guards`). Versions and the evidence
for each are in `docs/adr/0003-pinned-versions.md`. **Ran:** `gradlew projects`,
`gradlew :app-android:assembleDebug` — both green.

Decisions taken while building it (recorded in ADR 0003): AGP 9 built-in Kotlin at 2.2.10 rather
than `kotlin-android`; `compileSdk 37` with `compileSdkMinor 2` because Compose 1.13.0 requires
API 37.1; Java 17 bytecode on a JDK 21 toolchain.

### Milestone 3 — Guards and quality gates

Deliverable 3. `checkPortability` scans `core/`, `data/`, `sync/` and the pure half of
`integrations/` (Android implementations excluded by path) for `import android.` /
`import androidx.`. `checkLicences` resolves `:app-android`'s debug and release runtime classpaths
and compares them with `gradle/client-licences.txt`; an unreviewed module fails as well as a
GPL-family one. Both run inside `check`, with ktlint and Detekt per module.

Guard proofs, as the exit requires:

| Probe | Result |
|---|---|
| `import android.os.Bundle` added to `core/src/main/kotlin/.../GuardProbe.kt` | `:checkPortability FAILED` with the offending file and line; `gradlew check` red |
| Probe deleted | `checkPortability: scanned 5 source file(s), no Android imports.`; `gradlew check` green |
| `mysql:mysql-connector-java:8.0.33` added to `:app-android` | `:app-android:checkLicences FAILED`: `unreviewed client dependency` (both `mysql:mysql-connector-java` and its relocated `com.mysql:mysql-connector-j`, plus the protobuf it pulls) |
| the same dependency recorded as `GPL-2.0-only` in the registry | `checkLicences FAILED`: `GPL-family licence in a client dependency` |
| dependency and registry lines removed | `checkLicences: 93 client dependency module(s), all reviewed and non-GPL.`; `gradlew check` green |

Licence review: all 93 shipped modules are Apache-2.0, read from each module's published POM;
`com.google.guava:listenablefuture` declares no licence of its own and inherits Apache-2.0 from
`guava-parent 26.0-android`, checked at the source POM. Detekt `FunctionNaming` ignores
`@Composable`; ktlint's function-naming rule is disabled because it cannot see the annotation
(rationale in `.editorconfig` and ADR 0003).

### Milestone 4 — App on the emulator

Deliverable 6. `:app-android` builds a blank Compose screen (no tokens, no copy, no Material
defaults — B1 replaces it). **Ran:** `gradlew :app-android:installDebug`, then
`adb shell am start -n app.openhealth/app.openhealth.android.MainActivity`. Confirmed
`topResumedActivity=app.openhealth/.android.MainActivity` and `pidof app.openhealth` = 4801, and
captured a screenshot showing the blank dark surface on the API 36 emulator (1080×2400). A second
build-infrastructure test, `app-android/.../BytecodeTargetTest.kt`, proves the Android half also
compiles to Java 17 bytecode (class-file major 61); it runs in `check`.

### Milestone 5 — Licences, CI file, pinned versions, this log

Deliverables 2, 4 and 5 and the log. `LICENSE` is the Apache-2.0 text fetched from
`apache.org/licenses/LICENSE-2.0.txt` (202 lines, ending with the "APPENDIX: How to apply the
Apache License" section). `NOTICE.md` carries the
MuscleMap MIT notice (copied from `references/openGym/NOTICE.md`, geometry only, first used in
B4) and a table of notices each later phase must add. `server/` is recorded as AGPL-3.0, to be
built in B13. `.gitignore` no longer ignores `data/` (it is a module now; local data is
`local-data/`) and ignores `.gradle/`, `local.properties`, keystores and `*.jks`.
`.github/workflows/ci.yml` accepts the SDK licences, installs `platforms;android-37.2` and
`build-tools;37.0.0` (ids read from the installed SDK's `package.xml`), then runs `check` and
`assembleDebug`; it has never run because nothing is pushed, and the owner's Actions quota is
spent until the next billing period (see Open issues).

### Reviewer pass

A fresh reviewer agent was given only the branch diff and the `AGENTS.md` invariants, read-only.
It confirmed the portability scope, that the registry matches what ships, the emulator run, the
invariant set (no GPL/analytics dependency, no keystore, no `references/` code, `allowBackup=false`,
no `INTERNET` permission) and the ADR's pin evidence. It raised five things, all fixed in the commit
that follows:

| Finding | Fix |
|---|---|
| `gradlew` was committed mode `100644`, so the CI file would fail on Linux before Gradle ran | exec bit set on the index (`100755`), plus a `chmod +x` step in the workflow |
| `activity-compose`, `core-ktx` and `lifecycle` pins were missing from ADR 0003 although the catalogue says every pin is recorded there | three rows added with their release evidence |
| `GPL_FAMILY` missed `GPLv2`/`AGPLv3`/`LGPLv2.1` spellings, which real POMs use | regex loosened; proven by recording `org.jspecify:jspecify = GPLv2` and watching `checkLicences` fail, then reverting |
| this log said `LICENSE` was 169 lines and pointed the ktlint deprecation at the wrong line | corrected to 202 lines and the ktlint `apply` line |
| the licence registry's header was double-encoded UTF-8 (`Â§`) | rewritten; every file in the tree is now plain UTF-8 |

### Exit tests

| Exit test (`BUILD-ROADMAP.md` B0) | Result | Evidence |
|---|---|---|
| `./gradlew check` green | **Pass** | `BUILD SUCCESSFUL`, 113 tasks; `checkPortability`, `checkLicences`, ktlint and Detekt all executed and passed |
| A deliberate `import android.os.Bundle` in `core` makes `check` fail, then is removed | **Pass** | `:checkPortability FAILED` with `core/.../GuardProbe.kt:3: import android.os.Bundle`; green again after removal |
| The app installs and launches on an emulator or the owner's phone | **Pass** | `installDebug` + `am start`; `topResumedActivity=app.openhealth/.android.MainActivity`; screenshot on `openHealth_API_36` |
| `./gradlew :app-android:assembleDebug` passes (build gate §1) | **Pass** | `BUILD SUCCESSFUL` |
| Every screen touched passes `DESIGN.md` §14.5 | **Pass (trivially)** | One blank screen with no tokens or copy; B1 owns the design system and the gate starts to bite there |
| `docs/build-log.md` has an entry | **Pass** | This section |
| iOS gate (`pr.md` §13.17) reviewed | **Not yet** (default) | No iOS work was written, designed or planned; `core`, `data`, `sync` and `integrations` stay free of `android.*`/`androidx.*` |

### Placeholders and stubs left deliberately

| Item | Owner phase |
|---|---|
| The blank `MainActivity` screen | B1 (theme) and B3 (shell) |
| `app-android/src/main/res/values/themes.xml` inherits a platform theme | B1 |
| `core/src/test/.../ToolchainTest.kt` proves test wiring and the 17 bytecode target | B2 replaces it with domain tests |
| `:data`, `:sync`, `:integrations` and `:ui` are empty modules | B2, B13, B8 and B1 |
| CI workflow written, never run | the owner's first manual dispatch, once the Actions quota resets |

### Open issues

- **Two Gradle deprecations are third-party, not ours:** `Configuration.setVisible(boolean)`
  (scheduled for Gradle 11) comes from AGP/ktlint internals, and
  `ReportingExtension.file(String)` (scheduled for Gradle 10) is called by the ktlint Gradle
  plugin when it is applied (`openhealth.quality.gradle.kts`, the ktlint `apply` on line 3).
  Both clear when those plugins update; no action available in this repository. The build reports
  "incompatible with Gradle 10" because of them.
- **`checkLicences` only covers the runtime classpath**, so a test-only dependency is not
  reviewed. That is deliberate (test libraries never ship) but it means a licence problem in a
  test library is invisible — the rule for those is in `AGENTS.md`, not in a task.
- **SQLDelight, Koin, Ktor and Kotest are pinned but unproven** at this Gradle/Kotlin pair; the
  phase that applies each one proves it and amends ADR 0003 if a pin moves (§14 floors are all
  satisfied).
- **The OneDrive copy is deleted**: its 5 GB of contents are gone and `C:\dev\openHealth` was
  verified to hold every commit first. An empty folder entry at
  `C:\Users\naidu\OneDrive\Desktop\openHealth` could not be removed because OneDrive still holds a
  handle on it (0 items inside); a OneDrive restart or reboot clears it.
- **`targetSdk` 37 is untested against real behaviour changes**: the only device here runs API 36.
  B10's device pass is where that gets checked.
- **The GitHub Actions quota is spent until the next billing period**, so the workflow cannot be
  proven this month. It is not a gate for B0: `./gradlew check` and `:app-android:assembleDebug`
  ran locally from a clean tree instead. Pushing a `build/**` branch will show a queue-time
  failure until the quota resets; start the workflow by hand (`workflow_dispatch`) after that.
- **`gh` is not installed on this workstation**, so no session can read run results; the GitHub
  web UI is the place to look.
- **The Gradle daemon ran out of metaspace once** during the B0 runs (with eight JVM projects and
  Detekt/ktlint per module). The cap was raised to 1.5 GB in `gradle.properties`; if the warning
  returns, raise it again before assuming a build problem.
