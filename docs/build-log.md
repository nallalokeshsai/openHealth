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
`assembleDebug`. It ran for the first time after the branch was pushed and the PR opened; see the
owner follow-up below for the two failures and the fix that made it green.

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
| CI workflow written and proven | — (green on `a41a548`, both the push and PR runs) |

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
- **The repo is public and `main` is now protected** (2026-11-22): pull request required, the
  `build / check` status required, force pushes and branch deletion blocked, conversations must be
  resolved, admins may bypass. Dependabot alerts and security updates were enabled too; secret
  scanning and push protection were already on. Anyone can still read the code and logs — going
  private would need a paid plan for branch protection.
- **`gh` is not installed on this workstation**, so sessions read run results through the GitHub
  REST API with the stored credential; the web UI is the fallback.
- **The Gradle daemon ran out of metaspace once** during the B0 runs (with eight JVM projects and
  Detekt/ktlint per module). The cap was raised to 1.5 GB in `gradle.properties`; if the warning
  returns, raise it again before assuming a build problem.

### Owner follow-up after the B0 exit (2026-11-22)

Recorded because it changes the operating rules, not because it changes the build.

- **The stale OneDrive tree was deleted** at the owner's instruction, after verifying that
  `C:\dev\openHealth` contains every commit of it. Contents are gone; the empty folder entry stays
  until OneDrive releases its handle.
- **CI was proven, and its first failure was a real one.** The first two runs (push and PR on
  `7bdd520`) died in step 4: `android-actions/setup-android@v3` rejects the cmdline-tools the
  `ubuntu-latest` image now ships (`cmdline-tools/20.0`), so every later step was skipped. The fix
  (commit `a41a548`) drops that action and drives the runner's own `sdkmanager`, using the highest
  version under `cmdline-tools/*`; the discovery logic was exercised locally in Git Bash against a
  stubbed SDK tree first. Both runs on `a41a548` then passed every step, including `Check` and
  `Assemble the debug app`. The owner's Actions quota turned out not to block runs.
- **The owner authorised pushes from agent sessions permanently.** `opencode.json` now allows
  `git push`; `prompts.md` is at 1.1 with its Standing rule changed from "never push" to "push
  only when the owner asks for it in that session, and never force-push"; the `AGENTS.md` policy
  row and the prompts version row were updated to match. Pushing still requires an explicit ask
  from the owner in each session.
- **`build/b0-foundation` was pushed to origin** (`github.com/nallalokeshsai/openHealth`) and
  opened as PR #1 against `main`; the branch is green in CI and ready to merge. `main` itself was
  not moved by any session: merging is the owner's action, and `main` is protected from direct
  pushes as of 2026-11-22.

---

## B1 — Design system

**Status:** Completed · 2026-11-23
**Branch:** `build/b1-design-system`, cut from `build/b0-foundation` (B0 completed, `check` green
before any B1 work).
**Commits:** `029a26c` (tokens, contrast audit, DESIGN 0.3), `1ff3fd9` (icons, components,
gallery), `a3f253e` (debug gallery, screenshot goldens, token guard, licences, ADR, NOTICE) and
`eca587f` (reviewer fixes: 200 % layout, ripple, RTL, chrome, strings, guard completion), plus the
log/state commit that follows.

**Precondition:** `./gradlew check` was green on `build/b0-foundation` before the branch was cut.
The B0 PR #1 is still open; `main` was not moved by this session.

### Owner question asked (one batched dialog, before any work)

**`acc-2` for the eight non-brand presets.** DESIGN.md §2.6 and Appendix A define `acc-2` only for
the brand preset. Answered: the eight reuse their own fill; no hex is invented. Recorded in
DESIGN.md §2.6 and A.1–A.2 in the same commit, and DESIGN moved to 0.3 (2026-11-23).

### Milestone 1 — Tokens and the contrast audit (`029a26c`)

`:ui` gained one theme in `ui/src/main/kotlin/app/openhealth/ui/theme/`:

- `OhColors` (Appendix A.1–A.2): surface ramp, four-step text ramp plus `label-dim`, separators,
  nine accent presets with `acc-2`, `acc-soft`, `acc-line`, `fat`, the three recovery steps, every
  system hue and every status ink. Alpha tokens keep their alpha and are composited where measured.
- `OhType`: 11 roles plus the `large` 30 sp and `timer` 64 sp ends of their ranges and the chip's
  14 sp component style; platform UI font, `tnum` on every role.
- `OhSpace` (9 values + hairline + 44 dp target + 560 dp column), `OhRadius` (6 + full),
  `OhMotion` (§6.9 durations and §6.1 press scales, zeroed under reduced motion).
- `OhGeometry`: component sizes from §4–§5, so no screen needs a raw `.dp`; `OhTopBar` and
  `OhTabBar` build their heights from tokens.
- `OhTheme`: one provided theme with `OhColors`, `OhType`, `OhSpace`, `OhRadius`, `OhMotion`,
  `OhGeometry`, `LocalTextStyle`; Material's minimum-target local set to 44 dp. Dark canonical,
  light verified sibling, nine presets, reduced motion; dynamic colour absent, no Material palette,
  no ripple. `FontFamily.Default`, so **no font is bundled** (NOTICE.md).

`ContrastTest` computes every §12.5 pair from the tokens, both themes, all nine presets, alpha
composited: `label-2`, `label-dim`, every `acc-ink`, `on-acc` on every fill, six status inks,
`streak-ink`/`frost` at 3:1, `acc-2`, `fat` on `bg`/`surface`/`surface-2`, the focus ring, the
recovery steps in the canonical theme, and the rejected light fat value. **Ran:** six tests, green.

### Milestone 2 — Icons, 19 components, gallery (`1ff3fd9`)

- `OhIcon` + `OhIconName`: ~70 glyphs drawn as path nodes on the 24 dp grid (nav, modules, actions,
  objects, status, the twenty routine keys, Settings and Error). `IconSetTest` proves every name
  builds on the 24 dp grid (2 tests).
- The 19 §5 components with their §6 states: card + card header, inset group + row, media row,
  stat tile, KPI band, six button variants × three sizes, icon button, chip, segmented control,
  switch, stepper, slider, text/search field, five-tab bar with the raised Train disc, sheet,
  dialog, toast + tombstone undo, timer bar; plus the §4.4 `OhTopBar`. Press tiers, selected,
  disabled, error, offline, empty, loading, success, record and conflict are rendered.
- `ComponentGallery` (initially `:ui` main, moved to `src/debug` in the review fix) shows every
  entry and theme control.

### Milestone 3 — App, goldens, guard (`a3f253e`)

- `MainActivity` opens the gallery under `BuildConfig.DEBUG` and the B0 placeholder otherwise; the
  Android window theme is edge-to-edge with the dark page; `enableEdgeToEdge()`.
- Roborazzi + Robolectric screenshot tests: 21 gallery entries in dark, light, 200 % font and RTL,
  plus pressed Button and Chip captures. **86 golden PNGs** (3.5 MB) under
  `ui/src/test/screenshots`, verified with `:ui:verifyRoborazziDebug`; capture is gated on the
  Roborazzi properties so a plain test run does not rewrite them.
- `checkDesignTokens` (build-logic): production Kotlin under `ui/src` (minus `ui/theme` and test
  sources) and `app-android/src/main` may not contain a `Color(` literal or a raw `.dp` literal;
  wired into the root `check`. A unit test (`DesignTokenGuardTest`, 2 tests) proves the matcher and
  scans the same trees.
- Material 3 is the only new shipped dependency; it and its three ripple modules are reviewed in
  `gradle/client-licences.txt` (101 modules, all reviewed and non-GPL). New test-only pins are in
  ADR 0003's "Added in B1" section. No font is bundled; NOTICE.md records that.

### Milestone 4 — Reviewer pass and fixes (`eca587f`)

A fresh reviewer read the branch diff and the `AGENTS.md` invariants, read-only. Fixed: 200 % text
now grows (`heightIn`, two-line segments, tab labels drop above 150 %, timer gives up its text
controls), all remaining ripple removed, RTL hairlines/chevron/slider mirrored, Train disc animated
and glowing, sync-dot rings, timer chrome and work ring, field error ring, chip 44 dp slop, disabled
chip/stepper, destructive row anatomy and group separation, x-octagon error glyph, component strings
in `ui/src/main/res/values/strings.xml`, the gallery moved to the debug source set, the token guard's
named-argument/float-dp/string handling, `check` now depends on `verifyRoborazziDebug`, and the light
segment thumb's hairline. Deferred and recorded below: high-contrast token stepping (DESIGN gives no
measured values; B10 owns the system pass), backdrop blur and reduced-transparency wiring (no
Compose backdrop blur; B10), sheet 90 % cap and icon 1 em scaling (B10).

### Exit tests

| Exit test (`BUILD-ROADMAP.md` B1) | Result | Evidence |
|---|---|---|
| Gallery renders | **Pass** | `installDebug` + `am start` on `openHealth_API_36`: `topResumedActivity=app.openhealth/.android.MainActivity`; screenshot shows the gallery with the top bar, controls and tab bar inside the system insets |
| Screenshot tests pass | **Pass** | 23 Roborazzi tests, 86 goldens, `:ui:verifyRoborazziDebug --rerun-tasks` green; `check` now depends on it |
| Contrast test computes §12.5 and fails below the ratio | **Pass** | `ContrastTest`: 6 tests over both themes, nine presets, composited alphas, fat and recovery steps |
| A guard fails on a `Color(` or raw `.dp` outside `ui/theme`, then passes | **Pass** | Probe `Color(color = 0xFF123456)` + `16f.dp` in `ui/component`: `checkDesignTokens FAILED` naming both lines; after deletion `checkDesignTokens: scanned 10 screen file(s), no colour or dp literals.` |
| `./gradlew check` green | **Pass** | `BUILD SUCCESSFUL`, including `checkPortability` (5 files), `checkDesignTokens` (10 files), `checkLicences` (101 modules), ktlint, Detekt, unit tests and golden verification |
| `./gradlew :app-android:assembleDebug` passes | **Pass** | `BUILD SUCCESSFUL`; `installDebug` on the emulator |
| Every screen touched passes `DESIGN.md` §14.5 | **Pass** | The debug gallery only: tokens only, one accent, no borders/ripple/emoji, 44 dp targets, dark/light/200 %/RTL goldens, every status with a word or glyph; provenance and Trash rows belong to B3+ and are not invented here |
| `docs/build-log.md` entry | **Pass** | This section |
| iOS gate (`pr.md` §13.17) reviewed | **Not yet** (default) | No iOS work written, designed or planned; `core`/`data`/`sync`/`integrations` remain `android.*`-free |

### Placeholders and stubs left deliberately

| Item | Owner |
|---|---|
| Release `MainActivity` still a blank dark surface | B3 |
| Sheet 90 % max height and keyboard cap | B3/B10 (ModalBottomSheet caps to the screen today; `imePadding` is applied) |
| 10 dp backdrop blur on top bar/tab bar, 20 dp toast blur, 2 dp scrim blur | B10 (Compose has no backdrop blur; bars are 94 % opaque) |
| Reduced-transparency and high-contrast system handling | B10 (B1 exposes reduced motion only; no unmeasured high-contrast tokens ship) |
| Icons scale with type at 1 em | B10 (B1 uses the optical sizes §10.1 lists; no 200 % icon clipping was observed) |
| Focus-state screenshot | B10 a11y pass (the focus ring is drawn; a captured text cursor made the golden noisy) |
| Chip's 14 sp style is outside the 11-role scale | DESIGN §5.9 states 14/400; implemented as a component style, flagged here |
| CI does not run `verifyRoborazziDebug` | The local `check` does; the workflow is B0's and is updated when CI next runs |

### Open issues

- **`acc-2` for eight presets is the fill** (owner decision, DESIGN 0.3). A future screen needing a
  distinct pressed graphic in a preset must measure and record it in DESIGN first.
- **The chip's 14 sp** is the one size outside the type scale; DESIGN §5.9 and §3.2 disagree and
  were not amended because the owner's closed-token rule forbids a new role without a decision.
- **Screenshot goldens are platform-sensitive** in principle; they were recorded and verified on
  this Windows/Robolectric 4.18.1 setup. CI verification is wired into `check` but the workflow has
  not run since B0's, so the first CI run after merge is the proof.
- **The debug AAR ships `androidx.compose.ui:ui-test-manifest`** to provide the activity Robolectric
  launches; it is debug-only and reviewed, and never reaches a release build.
- **The B0 OneDrive empty-folder, targetSdk 37 on API 36 and CI quota open issues are unchanged.**

