# openHealth — build log

Memory between build sessions. One section per phase, appended at every milestone and
closed at the phase exit (`prompts.md` "Log and finish"). A phase is complete only when
its section says **Completed** and its exit tests are ticked with evidence.

---

## B0 — Foundation

**Status:** Completed · **Date:** 2026-11-23 · **Branch:** `build/b0-foundation`
**Goal:** an empty but correct multi-module project that builds and enforces the
architecture (`BUILD-ROADMAP.md` §B0). No feature code.

### Environment and decisions taken at the start

- Workstation: Linux, JDK 21 (Temurin 21.0.13.1+2), Android SDK at `~/Android/Sdk`
  with platforms 35, 36 and **37.2**, build-tools 35.0.0 and 36.0.0. The "Windows 11 /
  OneDrive" premises in `prompts.md` and `BUILD-ROADMAP.md` §B0 do not describe this
  machine: the working copy is not on OneDrive or any synced folder, so nothing was
  moved and no sync was paused. Flagged here rather than rewriting those documents.
- Test target: **physical phone**, as `AGENTS.md` "Test device" already decided. It was
  attached over USB debugging, authorised and listed as `device` before any question was
  asked, so B0's "emulator or phone" question was not put to the owner again.
- Questions asked: **none**. Both B0 questions were already answered (phone by
  `AGENTS.md`; the OneDrive one is not applicable on this workstation).
- Versions were verified against current releases on 2026-11-23 and recorded in
  `docs/adr/0003-pinned-versions.md`. Summary: Gradle 9.9.2, AGP 9.5.2 (built-in
  Kotlin), Kotlin/KGP 2.4.21, Compose BOM 2026.11.01, SQLDelight 2.4.1, Koin 4.2.2,
  Ktor 3.6.1, Detekt 1.23.8, ktlint Gradle 14.2.0, `compileSdk` 37 (minor 2),
  `minSdk` 28, `targetSdk` 37. KSP is not used: no annotation processors on the client.

### Milestones

1. **Build skeleton** — commit `61a391d`.
   `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`,
   `gradle/libs.versions.toml`, Gradle wrapper 9.9.2 (SHA-256 pinned), a
   `build-logic/` included build with `openhealth.kotlin.library`,
   `openhealth.android.library` and `openhealth.android.app`, and the six modules in the
   `Tech-Stack.md` §13 layout: `:core :data :sync :integrations :ui :app-android`.
   ktlint and Detekt are applied by the convention plugins and wired into `check`.
   `checkPortability` scans `src/` of each pure module for `import android.` /
   `import androidx.`; `checkLicences` resolves each client module's runtime classpath
   and fails on any coordinate missing from `gradle/licences/allowed.tsv` (the GPL
   family cannot be listed, `pr.md` §12.4). `app-android` shows one blank screen.
2. **Licences, CI, pins** — commit `63cdbcf`.
   Apache-2.0 `LICENSE` with the `server/` AGPL-3.0 split recorded, `NOTICE.md` with the
   MIT MuscleMap notice copied from `references/openGym/NOTICE.md` (openGym's AGPL code
   and Gym visual media are explicitly not used), `.github/workflows/ci.yml` (not
   pushed), `.gitignore` for Gradle state and signing material, ADR 0003.
3. **Exit documentation and review** — `docs/build-log.md` (this file), the
   "Current state" section of `AGENTS.md`, `gradlew` recorded executable in the index,
   and the fixes from a fresh reviewer pass: `checkLicences` now takes a live view of
   each module's `*RuntimeClasspath` and `*CompileClasspath` (the eager filter taken
   while the convention plugin applied saw no Android configurations at all, so the
   gate would have passed by looking at nothing), it fails when it finds no
   configuration, and `org.jspecify:jspecify` (Apache-2.0, pulled in by AndroidX) was
   added to the allowlist. ADR 0003 now also records the four AndroidX pins.

### What was run, and its result

| Command | Result |
|---|---|
| `./gradlew wrapper --gradle-version 9.9.2 --distribution-type bin` | Wrapper generated; distribution and wrapper SHA-256 recorded in `gradle-wrapper.properties`. |
| `./gradlew check` | **BUILD SUCCESSFUL**, 110 tasks. Includes lint, ktlint, Detekt, both guards, unit-test and assemble tasks. |
| Guard proof 1: added `import android.os.Bundle` to a `:core` file | `:core:check` **failed** at `checkPortability`: "android.* and androidx.* are not allowed in a pure Kotlin module" naming the file and line. File removed, `:core:check` green. |
| Guard proof 2: added `org.hibernate.orm:hibernate-core:7.4.17.Final` (LGPL-2.1) to `:core` | `:core:checkLicences` **failed**, listing `org.hibernate.orm:hibernate-core`, `org.hibernate.models:hibernate-models`, `org.hibernate.orm:hibernate-platform`. Dependency removed, `:core:check` green. |
| Guard proof 3 (after the gate fix): the same LGPL dependency as `implementation` in `:ui`, and as `compileOnly` in `:app-android` | `:ui:checkLicences` and `:app-android:checkLicences` both **failed**, naming the Hibernate coordinates. Removed; full `check` green again. |
| `./gradlew clean check :app-android:assembleDebug --no-build-cache` | **BUILD SUCCESSFUL**, 137 tasks (128 executed), from a cleaned tree. |
| `./gradlew :app-android:assembleDebug` | **BUILD SUCCESSFUL**; debug APK produced. |
| `./gradlew :app-android:installDebug` | **BUILD SUCCESSFUL**; installed on the attached phone. |
| `adb shell am start -W -n app.openhealth/app.openhealth.android.MainActivity` (after `force-stop`, screen awake) | `Status: ok`, `LaunchState: COLD`, `TotalTime: 860` ms; focused window `app.openhealth/app.openhealth.android.MainActivity`; app process alive; `logcat -s AndroidRuntime:E` empty. |

No device identity (serial, model, build) is recorded in this repository.

### Extended verification (owner-requested follow-up, same day)

Every gate was exercised on purpose, and the app was checked again from a fresh clone.

| Check | Result |
|---|---|
| `git clone` of the branch into a clean directory (no `local.properties`, `ANDROID_HOME` set) then `./gradlew check :app-android:assembleDebug` | **BUILD SUCCESSFUL**; `gradlew` is executable in the index and nothing machine-local is needed. |
| ktlint probe: a badly formatted file in `:core` | `:core:ktlintMainSourceSetCheck` **FAILED** with style violations; file removed. |
| Detekt probe: a seven-parameter constructor in `:core` | `:core:detekt` **FAILED** on `LongParameterList`; file removed. |
| Portability probe in `:data` (`import androidx.annotation.NonNull`) | `:data:checkPortability` **FAILED**, naming the file and line; file removed. |
| Licence probe in `:integrations` (LGPL `hibernate-core` as `testImplementation`) | `:integrations:checkLicences` **FAILED**; dependency removed. |
| `:app-android:assembleRelease` | **BUILD SUCCESSFUL**; unsigned release APK produced. (This entry originally said "release builds through R8's default config". That was wrong and is corrected in the B2 follow-up below: no `buildTypes` block is declared anywhere, so `isMinifyEnabled` is AGP's default of **false** and R8 does not run at all.) |
| CI workflow | `.github/workflows/ci.yml` parses; one job named `check` (the status name branch protection requires), five steps. |
| Debug APK manifest audit (`apkanalyzer`) | `minSdk 28`, `targetSdk 37`, `debuggable true`, `allowBackup false`, `fullBackupContent` and `dataExtractionRules` present; the only permission is AndroidX's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No network, storage, location or health permission. |
| `:app-android:lintDebug` | 0 errors, 1 warning: no application icon yet (design work, B1/B10). |

**Fix made in this pass.** Lint flagged that `android:allowBackup="false"` no longer
covers device-to-device transfer on Android 12+. Added
`app-android/src/main/res/xml/data_extraction_rules.xml` and `backup_rules.xml`, both
excluding every domain, and wired them into the manifest, so no health data leaves the
device through platform backup or transfer (Tech-Stack.md §5). That warning is gone.

### Hosted CI (first run, 2026-11-23)

The first GitHub Actions run failed in 22 s, before Gradle started, and reported no
status under the name branch protection requires:

- `android-actions/setup-android@v3` defaults to `packages: tools platform-tools`, and
  current `sdkmanager` no longer has a `tools` package: "Failed to find package 'tools'".
  The workflow now uses the runner image's own `sdkmanager` to accept licences and
  install `platform-tools`, `platforms;android-37.2` and `build-tools;36.0.0`.
- The job was named `build`; branch protection on `main` requires a status named
  `check` (contexts: `check`), which is what the PR showed as waiting. The job is now
  named `check`.

After those two fixes the hosted job passed every step (SDK install, `./gradlew check`,
`assembleDebug`) in 3 min 11 s, and the PR reported `check` green. The only remaining
CI noise was deprecation: `checkout@v4` and `setup-java@v4` run on Node 20, and
`setup-java` v4 is retired, so the workflow moved to `checkout@v7` and
`setup-java@v6`.

### Exit tests

| Exit test (`BUILD-ROADMAP.md` §B0) | Evidence | Result |
|---|---|---|
| `./gradlew check` green | BUILD SUCCESSFUL, 110 tasks, 2026-11-23; re-proved from a cleaned tree with `--no-build-cache` | **pass** |
| A deliberate `import android.os.Bundle` in `core` makes `check` fail, then is removed | Guard proof 1 above | **pass** |
| The app installs and launches on an emulator or the owner's phone | Cold launch on the physical phone (`Status: ok`, focused activity, no crash) after a clean build | **pass** |

### Post-exit amendment (2026-11-23, after the merge)

The owner supplied the anatomical reference for the body model and asked for its fat
colour. `fat` is now `#F2C230` in dark (the reference's golden yellow; 12.5:1 on `bg`,
10.2:1 on `surface`, 8.3:1 on `surface-2`) and `#A87C00` in light, the same hue at the
brightest value that still clears the 3:1 graphic bar (`#F2C230` measures 1.5:1 on light
`bg`). `DESIGN.md` §2.5, §12.5 and Appendix A carry the values and the measurements in a
dated edit, and `OhContrastTest` still passes over both themes. Nothing renders the layer
until B4, so no golden changed.

iOS gate review (`pr.md` §13.17): **not yet**. No change to the deferral.

### Placeholders and deferred work

- `:data` does not apply the SQLDelight plugin yet; the schema and migrations are B2.
  Compatibility of SQLDelight 2.4.1 with Gradle 9.9.2 / Kotlin 2.4.21 was proved in a
  throwaway JVM module (plugin applies, `.sq` generates, module builds) and is recorded
  in ADR 0003.
- `:core`, `:data`, `:sync`, `:integrations` and `:ui` have no source files yet, so
  their Detekt tasks report NO-SOURCE. Their guards still run.
- `app-android` has one blank Compose screen by design; the design system is B1.
- The launcher icon is the one remaining lint warning; the icon set and the app mark
  are design work (B1) and the store icon is release work (B10). No placeholder icon
  was invented.

### Open issues

- Gradle 9.9.2 warns that AGP and the ktlint plugin use deprecated Gradle features that
  will break on Gradle 10. Do not move to Gradle 10 until AGP supports it.
- `compileSdkMinor = 2` is a new AGP 9 DSL knob needed because Compose BOM 2026.11.01
  requires API 37.1+. Re-check it at the next Compose BOM bump.
- `checkPortability` scans the whole `src/` tree of `:integrations`. When B8 adds its
  Android-side implementations, they must live outside that module (or the task must
  learn to skip the Android source set) so the pure API half stays pure.
- `prompts.md`, `BUILD-ROADMAP.md` §B0 and `AGENTS.md` still describe a Windows +
  OneDrive workstation with Gradle on `PATH`. This machine has neither, and the wrapper
  is the only Gradle. Flagged, not swept (a docs sweep is its own task).

---

## B1 — Design system

**Status:** Completed · **Date:** 2026-11-23 · **Branch:** `build/b1-design-system`
**Goal:** the closed token set and the 19 components exist once, in code, in `:ui`, and
cannot drift (`BUILD-ROADMAP.md` §B1).

### Environment and decisions taken at the start

- Workstation: the B0 Linux machine, unchanged. Test target: the owner's **physical
  phone**, as `AGENTS.md` "Test device" decided; it was authorised and listed as
  `device` before any question was asked.
- **Questions asked: none.** `DESIGN.md` §3.1 answers the font question itself: "We ship
  no typeface at 1.0", the platform UI font through `FontFamily.Default`. Nothing is
  bundled, so there is no font licence to read; `NOTICE.md` records that.
- Appendix A is the source of every value. Three inconsistencies inside `DESIGN.md` were
  resolved in a dated edit rather than guessed in code (see "Contract corrections").
- `docs/adr/0004-design-system.md` records the eleven implementation decisions that go
  beyond the text of the contract (theme shape, `acc-2` for presets, the token gate,
  Detekt/ktlint scoping, Roborazzi without its plugin, the 200 % adaptations).

### Milestones

1. **Tokens and theme** — first code commit `aa04f7c` (the phase head, the commit this
   entry ships with, is where `check` is green; the first commit needed ktlint formatting
   that landed in the next one, and the documentation landed with it).
   `ui/…/theme/`: `OhColors` (surface ramp, text ramp, separators, the nine accent
   presets, the system palette, nine status inks, `fat`, the three recovery steps and
   the derived tokens the components state in words), `OhType` (the 9 text roles plus
   `display` and `timer`, `large` at 30 sp below 420 dp), `OhSpace`, `OhRadius`,
   `OhMotion`, `OhAlpha`, `OhDims` (component geometry quoted from §4.3/§4.5/§5) and one
   `OhTheme` over `CompositionLocal`s. No `MaterialTheme` is installed; no dynamic
   colour; no ripple. `checkDesignTokens` is registered for both Android modules.
2. **Components, top bar, tab bar, icons** — in the same commit.
   All 19 components of §5 (card, card header, inset group + row, media row, stat tile,
   KPI band, button in six variants and three sizes, icon button, chip + strip,
   segmented control, switch, stepper with press-and-hold acceleration, slider, text and
   search field, tab bar with the raised Train disc, sheet + modal sheet, dialog card +
   dialog, toast + undo, timer bar), the global top bar of §4.4, and an original 24 dp
   line set: 33 navigation/action icons, 12 module icons, 18 object icons and 12 routine
   glyph keys. Tab glyphs are re-stroked at 1.65 (rest) and 2.0 (active) from the same
   path data.
3. **Gallery, screenshots, contrast** — in the same commit.
   A debug-only `GalleryActivity` (dark/light and 100 %/200 % switches) in
   `app-android/src/debug`; 26 Roborazzi tests each rendered in dark, light, 200 % text
   and RTL (104 committed goldens); `ComponentSemanticsTest` (9 tests) asserts the §14.5
   accessibility items that can be measured — one merged node per row, a named 44 dp icon
   button, five selectable tabs, the Train disc's selected state, a chip's chosen state,
   checked state, no double-fired tap; `OhContrastTest` (12 tests) computes every pair of
   `DESIGN.md` §12.5 from the token values, including each ink over a translucent fill
   (`acc-soft`, the Danger fill, the toast, the timer bar, the bars) composited over the
   surface it is read on.

### What was run, and its result

| Command | Result |
|---|---|
| `./gradlew check` | **BUILD SUCCESSFUL**; 47 tests (26 screenshot × 4 variants, 9 accessibility-semantics, 12 contrast), lint, ktlint, Detekt, `checkPortability`, `checkLicences` and `checkDesignTokens` in every module. |
| `./gradlew :app-android:assembleDebug` | **BUILD SUCCESSFUL**; debug APK, with two launcher entries (the app and the gallery). |
| `./gradlew :app-android:assembleRelease` | **BUILD SUCCESSFUL**; unsigned release APK. Its manifest has **one** activity and its dex/zip has no `Gallery` entry, so the debug-only gallery cannot ship (ries checked with `aapt2` and `unzip -l`). |
| `./gradlew :ui:testDebugUnitTest` (verify mode, the default in `check`) | **BUILD SUCCESSFUL**; 104 goldens compared, none missing, none changed. |
| `./gradlew :ui:testDebugUnitTest -Proborazzi.record` | **BUILD SUCCESSFUL**; recordings are byte-identical between runs (compared by re-running the verify pass). |
| Guard proof 1 (`checkDesignTokens`): a file in `ui/…/components` with `Color(0xFF112233)` and `13.dp` | `:ui:checkDesignTokens` **failed** naming both lines; file removed, gate green. |
| Guard proof 1b (tightened rules): `Color.White`, `12f.dp`, `Color(red = …, green = …, blue = …)`, `Dp(13f)` and a `MaterialTheme` import | All six lines **reported** (named constant, raw .dp, `Color(...)`, `Dp(...)`, MaterialTheme at the import and at the usage); file removed, gate green. |
| Guard proof 2b (scoped Detekt rules): a seven-parameter **non-composable** function in `:ui` | `:ui:detekt` **failed** on `LongParameterList`, proving the `@Composable` scoping did not swallow the rule; removed, green. |
| Golden proof: one pixel of `dark/card.png` changed | `ComponentScreenshotTest > card` **failed** ("is changed") because `src/test/screenshots` is an input of the test task; the original restored, verify green. |
| Guard proof 2 (`checkLicences`): first `:ui:check` | **Failed** as designed, listing 13 test-only Robolectric/Roborazzi transitives missing from the allowlist. Each licence was read from its POM and recorded (see "Licences"); both gates then green. |
| Regression, B0 portability guard: `import android.os.Bundle` in a `:core` file | `:core:checkPortability` **failed** naming the file and line; file removed, green. |
| `./gradlew :app-android:installDebug` then launch the gallery cold | `LaunchState: COLD`, focused activity `…android.gallery.GalleryActivity`, `logcat -s AndroidRuntime:E` empty. |
| Same for `MainActivity` | `Status: ok`, cold, focused activity, no crash. |
| Gallery on the phone, dark → light → 200 % text → back | Every section renders; the two theme switches and both text-size switches work; no crash and no `AndroidRuntime` entry. |

No device identity (serial, model, build) is recorded in this repository.

### Exit tests (`BUILD-ROADMAP.md` §B1)

| Exit test | Evidence | Result |
|---|---|---|
| Gallery renders | `GalleryActivity` cold-launched on the physical phone in dark, light and 200 % text; screenshots inspected section by section; no crash | **pass** |
| Screenshot tests pass | 26 Roborazzi tests × 4 variants = 104 goldens under `ui/src/test/screenshots/{dark,light,text200,rtl}`, verified by plain `check` (record mode off) | **pass** |
| Contrast test passes | `OhContrastTest`, 11 tests over both themes and all nine presets: label-2, label-dim, every `acc-ink`, `on-acc`, `acc-2 ≥ 3`, the six text inks, streak/frost ≥ 3, the focus ring, `fat` on `bg`/`surface`/`surface-2`, and the recovery steps against Appendix A | **pass** |
| A `Color(` literal or a raw `.dp` literal outside `ui/theme` fails a check | Guard proof 1 above, then green after removal | **pass** |
| §14.5 items a component can carry | `ComponentSemanticsTest`, 8 tests: one node per row, a non-actionable row is not a button, named 44 dp icon button, a chip's 44 dp target, five selectable tabs with the active one selected, switch on/off state, one click per tap | **pass** |

### Regression over earlier phases

- **B0 `./gradlew check`** — covered by the full run above.
- **B0 portability guard** — probe re-run (above) because the convention plugins were
  edited to add `checkDesignTokens` and the Detekt configuration.
- **B0 licence guard** — re-run; new test-only transitives were allowed by licence, none
  in the GPL family (see below).
- **B0 install-and-launch** — redone on the physical phone for both activities.
- B0's ktlint and Detekt probes were **not** repeated: the rules are unchanged for
  non-composable code, and the scoped change is recorded in ADR 0004.

### Independent review and the fixes it produced

Three fresh reviewers saw only the diff and the contract (correctness and tests;
`AGENTS.md` invariants and licences; `DESIGN.md` conformance). Every finding below was
confirmed in the code or the goldens, then fixed in this phase and re-verified:

| Finding | Fix |
|---|---|
| A card's press scaled only its text, not the surface | `ohPressScale` moved before `clip`/`background`; the golden shows the card shrinking |
| The slider's only drag surface was its 4 dp track | the gesture lives on the 44 dp box; the range is validated, the latest callback is read, changes are consumed |
| A sheet could not scroll | the body is a `verticalScroll` column between the sticky title and the actions |
| The Danger and Tinted labels measured 4.23/4.19:1 on their own fills, and the audit could not see translucent fills | both use `label` ink (recorded in §5.7); `OhContrastTest` gained a composited-ink test over every tinted fill |
| Tab destinations lost their name at ≥150 % text | the name stays on the accessibility node and the label drops only at 175 % |
| The committed goldens were not task inputs, so a golden-only change could pass `check` | `inputs.dir("src/test/screenshots")`; proved by changing one pixel and watching `card` fail |
| A chip exposed no chosen state; the Train disc exposed no selection | `selectable` semantics and `selected` on the disc, both asserted in `ComponentSemanticsTest` |
| RTL hairline insets and chevrons were physical | logical inset by `LocalLayoutDirection`; `autoMirror` on the chevrons and the directional icons |
| The slider fill and the stepper unit ignored RTL | the fill starts at the logical end; the figure and its unit keep their order in every script |
| Chips and four tab cells had no press response | press scale on both (§6.1) |
| A disabled field's fill did not dim | the alpha wraps the fill |
| The sync dot's ring was 1 dp; the icon-button dot and the tab dot used different oranges | 2 dp ring, one `orange` |
| A destructive row had a tinted tile and no 22 dp separation | red-ink glyph on no tile, its own group 22 dp away |
| The work timer had no accent ring; the dialog drew a hairline in light | accent hairline when working; hairline only in dark, plus the 280 ms 94 % pop |
| Small buttons and segmented cells missed the 44 dp target | painted sizes kept, targets grown to 44 |
| A tab list without a raised tab drew a duplicate disc | exactly one raised tab is required |
| `checkDesignTokens`'s allowlist was an absolute-path substring and had false negatives | module-relative allowlist, a "scanned zero files" guard, and rules for named `Color` constants, factories, `Dp(...)`, `TextUnit(...)` and `MaterialTheme` |
| `ui-test-manifest` merged into every debug build | test classpath only |
| The icon golden covered 9 of 75 icons | every icon in the set is captured |

### Licences

`gradle/licences/allowed.tsv` gained eleven test-only rows, each read from the artefact's
own POM on Maven Central on 2026-11-23: sqlite4java (Apache-2.0), dropbox differ
(Apache-2.0), icu4j (Unicode-3.0), javawriter (Apache-2.0), javax.inject (Apache-2.0),
bouncycastle (MIT), conscrypt (Apache-2.0), ow2 asm (BSD-3-Clause), snakeyaml
(Apache-2.0). `javax.annotation:javax.annotation-api` is dual-licensed **CDDL-1.1 or
GPLv2+CPE**; the allowlist records the CDDL election, so no GPL-family code is elected
and none reaches the APK (the dependency is on the unit-test classpath only). Robolectric
and Roborazzi themselves are test-scope; `:app-android`'s runtime classpath is unchanged.

### Contract corrections (dated in `DESIGN.md`)

- §5.9 chip text was **14/400**, a size that is not one of the 11 roles of §3.2 and that
  §0.2 forbids as a twelfth step. It is now **15/400 (`subhead`)**.
- Appendix A's row padding said "11/14" while §5.3 says 12/16 and the hairline inset
  depends on 16. Appendix A now says **12/16**, matching §5.3.
- `acc-2` is specified for `brand` only. For the other eight presets it resolves to that
  preset's own "Light fill" (dark theme) or "Light ink" (light theme), i.e. the same
  hue one documented step away from the resting accent; no hex was invented and both
  clear 3:1 on `surface` (§2.6).
- **Danger and Tinted button labels use `label` ink.** Composited over `bg` and
  `surface`, `red-ink` on the 15 % Danger fill measured **4.23:1** (dark) and **4.40:1**
  (light), and `acc-ink` on `acc-soft` measured **4.19:1** for the default accent: all
  below the 4.5 of §11.1. The fills keep their stated tints and the fills carry the
  meaning; `label` passes. §5.7 and §12.5 record it and `OhContrastTest` now measures
  every tinted fill composited.
- §4.4's "`headline`" is not a role; the top-bar app name is the §10.5 wordmark,
  `body` 17/600. §4.4 now says so.
- §5.18's toast targets said 36 dp against §4.5/§11.1's 44; they are 44 dp now.
- §3.3 gives the timer 700 while §5.19 gives the bar 28/600; the bar is 600 and the
  full-screen clock is the 700 end of the same role. §3.3 now says so.
- The revision line of `DESIGN.md` carries the 2026-11-23 note.

### Invariant audit (`AGENTS.md`)

| Invariant | This phase |
|---|---|
| Provenance on every value | No value is stored or shown yet; the components carry slots for it and the sheet grammar is §7's. N/A. |
| `happened_at` ≠ `recorded_at` | No observation type yet. N/A. |
| Five layers, rebuildable rollups | No storage yet. N/A. |
| One source wins a day | No source registry yet. N/A. |
| Local-first | No network call, permission or service was added; nothing waits on a network. **Holds.** |
| Not a medical device | No clinical wording; component copy is neutral ("Two sources disagree", "That is outside the plausible range") and nothing diagnoses. **Holds.** |
| No telemetry | No analytics, crash reporter or third-party SDK added; test dependencies are test-scope. **Holds.** |
| Portability | `core/ data/ sync/ integrations/` untouched; the portability probe was re-run and still fails on an `android.*` import. **Holds.** |
| Licence split | Apache-2.0 client, no GPL-family dependency; the new rows are test-only and recorded with their real licence; `NOTICE.md` states no typeface is bundled and the icons are ours. **Holds.** |
| Tombstone undo, not a timer | The toast is documented as the shortcut; no destructive action exists yet. The component does not pretend to be the undo. **Holds.** |
| Modules hide, never delete | No module switch yet. N/A. |
| Weight-neutral mode | No weight, calorie or body-fat value is rendered app-wide yet; the components take values, they do not source them. The policy object is B3's. N/A. |
| Chart has a table and a sentence | No chart component was added (charts are B9); no chart can ship without them. N/A. |
| Closed design tokens | `checkDesignTokens` fails on a colour or raw size outside `ui/theme`; the contrast test measures every stated pair; the only `DESIGN.md` edits are dated corrections of internal contradictions and are listed above. **Holds.** |
| Closed cut list | Nothing from `pr.md` §14.5 was built. **Holds.** |
| Quality bars | No measured performance path changed; the gallery's cold start on the phone is ~1.0–1.3 s in debug, which is not the release figure. **Holds.** |

### Placeholders and deferred work

- **Backdrop blur** on the top bar and tab bar is not implemented: their fills carry the
  documented 94 % `bg-el`, but a real backdrop blur needs the content behind the bar and
  belongs to the shell (B3). Under Reduce Transparency they are already opaque enough to
  read; the switch itself is B10.
- **Edge-to-edge and inset consumption** are B3's. The debug gallery reserves the status
  bar itself; the components take inset-free geometry.
- The XML window frame (`Theme.OpenHealth`, no ActionBar, black window) is deliberately
  minimal; the real theme, splash and system-bar behaviour arrive in B3.
- `empty`, `loading` and `offline` are screen patterns of §6.7/§6.4/§6.5, not components of
  the 19; they are built with the screens that show them.
- `modal` sheet and dialog are used through Material 3 `ModalBottomSheet`/`Dialog` for
  behaviour only, with every colour, shape and inset passed from `OhTheme`.
- The timer's spoken countdown (10 s, 3, 2, 1) is session behaviour, not a component
  property; the timer bar already exposes a live region hook for B5 to drive.
- The screenshot matrix covers dark, light, 200 % text and RTL. **High contrast,
  reduce-transparency and colour-blind simulation are not yet separate goldens**; the
  tokens and the §12.5 audit cover the contrast part, and B10 owns the platform modes.
- Reduce Transparency and the bars' backdrop blur are deferred with the blur itself
  (they need the content behind the bar).

### Open issues

- The screenshot goldens depend on Robolectric's native renderer. Record them on Linux
  with the pinned Robolectric 4.18.1; a future upgrade may legitimately rewrite all 104
  images in one commit. Robolectric downloads its own `android-all` jar on a cold cache
  (Apache-2.0, test-only; not a Gradle dependency, so `checkLicences` does not see it).
- Detekt's four rules are scoped to `@Composable` in `gradle/detekt.yml` and ktlint's
  function-naming rule ignores `@Composable`; both were verified to still fire on
  non-composable code. Re-check when either tool is upgraded.
- The `timer` role is exposed as `timer` (28 sp) and `timerFullScreen` (64 sp), the two
  ends of §3.2's range; a mid-range clock is a size argument at the call site, not a new
  role.
- The gallery's colour-swatch section is a debug aid and is not held to the screen gate;
  product screens are.

### Post-exit amendment (2026-11-23, after the merge)

The owner supplied the anatomical reference for the body model and asked for its fat
colour. `fat` is now `#F2C230` in dark (the reference's golden yellow; 12.5:1 on `bg`,
10.2:1 on `surface`, 8.3:1 on `surface-2`) and `#A87C00` in light, the same hue at the
brightest value that still clears the 3:1 graphic bar (`#F2C230` measures 1.5:1 on light
`bg`). `DESIGN.md` §2.5, §12.5 and Appendix A carry the values and the measurements in a
dated edit, and `OhContrastTest` still passes over both themes. Nothing renders the layer
until B4, so no golden changed.

iOS gate review (`pr.md` §13.17): **not yet**. No change to the deferral.

---

## B2 — Core and data

**Status:** Completed · **Date:** 2026-11-23 · **Branch:** `build/b2-core-and-data`
**Goal:** the five-layer model and every formula, pure Kotlin, heavily tested, before
any feature screen (`BUILD-ROADMAP.md` §B2).

### Environment and decisions taken at the start

- Workstation and test target unchanged: this Linux machine and the owner's **physical
  phone**, authorised before any question was asked.
- **Questions asked: none.** The roadmap says "Ask the owner: nothing". Two choices it
  left open were decided and recorded in `docs/adr/0005-core-and-data.md`: Argon2id is
  Bouncy Castle (pure Java, MIT-style licence; `argon2-jvm` ships native binaries and was
  rejected) and blood glucose is stored in `mmol/L`.
- Twelve implementation decisions beyond the text of the contract are in ADR 0005:
  canonical units, the dedup coverage rule, read-time downstream aggregates, the
  migration baseline, the Keystore-held SQLCipher passphrase, the export container, the
  generic export registry, trash scope, formula choices (sex required for BMR, the
  inch-fitted Navy equations, 15-rep cap on 1RM, readiness mappings, hard-session
  recovery), the corpus, and the Detekt scopes.

### Milestones

1. **Core domain and formulas** — commit `9c8e569`.
   `:core`: value-class ids and a seeded id generator; `kotlinx-datetime` time with
   `happened_at`, `recorded_at`, local date and zone; the UCUM unit set with one canonical
   unit per dimension and affinity conversion; the provenance block, observation types,
   contexts, quality and edit trail; the source registry with per-type priorities and
   families; five dedup policies and daily aggregations; the merge-policy interface and
   the per-entity registry; plausibility ranges and validators; the citation table; the
   platform seams (clock, secret vault, files, notifications, health bus, step sensor,
   BLE, camera, PDF, export cipher); raw-record and rollup types; and twelve formulas —
   Mifflin-St Jeor, US Navy body fat, BMI and bands, FFMI and normalised FFMI, TDEE, MET
   energy with 42 bundled activities, Epley and Brzycki 1RM, session volume, EWMA weight
   trend, goal-rate clamping and ETA window, muscle recovery, readiness v1 and sleep debt.
   Every formula returns value, inputs, citation and version, or a reason it cannot run.
2. **Schema, rollups, trash, export, corpus** — commit `bc3ab88`.
   `:data` with SQLDelight 2.4.1: 20 tables across the five layers, the timeline view,
   indexes on `(local_date)`, `(type, happened_at)`, `(type, local_date)` and
   `happened_at`, schema version 2 with `1.sqm`, WAL, foreign keys, integrity check and a
   backup-before-migration wrapper. Repositories for observations, raw records, rollups,
   sources, profile, training, nutrition, body, settings and the timeline; a rollup
   engine that rebuilds a day from observations, entries, measurements and vitals and a
   full rebuild that runs the same code; `LocalStore` as the one production write path
   that wires every repository to that engine; `TrashService` as the only delete path; the Argon2id/AES-GCM export with a
   versioned bundle of every table and a CSV export; and the seeded five-year corpus.
3. **Android seams and fixture tool** — in commit `bc3ab88`.
   `app-android`: `AndroidSecretVault` (Keystore AES-256-GCM wrapping the database
   passphrase), `AndroidDatabaseFactory` (SQLCipher behind the `:data` seam, native
   library loaded explicitly, WAL, integrity check) and `AndroidMigrationBackup`;
   a device test that proves persistence, encryption and reopen. `tools/fixtures` is a
   runnable generator plus the query benchmark.
4. **Verification and documentation** — commits `61a4927`, `9455cb1` and `ba7688f`. Two fresh review passes,
   every confirmed finding fixed, the clean rebuild and device run re-run over the
   fixes, this log, ADR 0005, the NOTICE additions, and the "Current state" update in
   `AGENTS.md`.

### What was run, and its result

| Command | Result |
|---|---|
| `./gradlew clean check :app-android:assembleDebug --no-build-cache` | **BUILD SUCCESSFUL**, 195 tasks (186 executed), from a cleaned tree. |
| `./gradlew check` | **BUILD SUCCESSFUL**: 170 JVM tests (66 `:core`, 56 `:data`, 47 `:ui` from B1, 1 benchmark), ktlint, Detekt, `checkPortability`, `checkLicences` and `checkDesignTokens` in every module. |
| `./gradlew :tools:fixtures:test` (inside `check`) | **BUILD SUCCESSFUL**; five-year corpus built in **~4 s** (33,372 observations, 261 raw records, 392 sessions, 5,880 sets, 16,335 entries, 2,010 foods), 15,253 rollup rows (five layers, nutrition and vitals included) rebuilt in **~1.4 s**, every UI query under **5.2 ms** against the 16 ms budget. |
| `./gradlew :app-android:assembleRelease` | **BUILD SUCCESSFUL**; 41 MB unsigned APK with four SQLCipher ABIs, **no `Gallery` class or activity**, and only AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — no network, storage, location or health permission was added. |
| `./gradlew :app-android:installDebug` then `MainActivity` cold/warm launch | `Status: ok`, focused activity, `logcat -s AndroidRuntime:E` empty. |
| Gallery launch after the changes | `Status: ok`, cold, focused `GalleryActivity`, no crash (B1 regression). |
| `:app-android:connectedDebugAndroidTest` on the physical phone | **BUILD SUCCESSFUL**; `EncryptedDatabaseTest` inserted a weight, asserted the journal mode is WAL, closed, reopened and read it back, asserted the file does not start with the SQLite header, then exported through Argon2id/AES-GCM and restored into a second encrypted database on the device. Two real failures were caught here and fixed: SQLCipher's native library must be loaded explicitly (`System.loadLibrary`), and `PRAGMA journal_mode = WAL` must run as a query on Android, not `execute`. |
| Guard proof (`checkPortability`): `import android.os.Bundle` in `:core` | **FAILED**, naming the file and line; file removed, green. |
| Guard proof (`checkLicences`): LGPL `hibernate-core` as a `:data` test dependency | **FAILED**, naming the Hibernate coordinates; dependency removed, green. |
| `:tools:fixtures:run --args="--out … --days 30"` | Wrote a 30-day corpus (550 observations, 6 sessions, 90 sets, 284 entries). |
| Explicit SQLCipher load bug | caught by the device test, fixed in the same phase and re-verified. |

No device identity (serial, model, build) is recorded in this repository.

### Exit tests (`BUILD-ROADMAP.md` §B2)

| Exit test | Evidence | Result |
|---|---|---|
| Property tests show rollups are identical whether built incrementally or from scratch | `RollupEngineTest.incrementalAndFullRebuildsAgree` compares every rollup row (ignoring `computedAt`); the full rebuild deletes every row and re-runs the same day builder. Random event order in the core property tests; a five-source/30-day corpus exercise in the benchmark. | **pass** |
| Dedup never sums two sources | `StepsDedupPropertyTest.dedupNeverSumsTwoSources` (Kotest, randomized), `dedupTakesOneSourceAndNeverTheSum`, `theSameVendorThroughTwoPipesIsNotCountedTwice`, `aGapFallsBackToTheWholeNextSourceInsteadOfStitching` and `aWakingWindowOfSixteenHoursCoversTheDay`. A day's row names one source or, for a mean, no single source. | **pass** |
| Export then restore reproduces the database | `ExportRoundTripTest.exportThenRestoreReproducesEveryTable`: 60-day corpus → Argon2id container → restore into a fresh database → snapshot again; all table rows are equal, 20 tables and every row counted. The production Argon2 defaults are exercised in a second test; a wrong passphrase fails with `WrongPassphrase`; a newer schema is refused. | **pass** |
| Backfilling a past day does not change any `recorded_at` | `aBackfilledObservationKeepsItsRecordedAtAndRebuildsTheDay`, `aBackfilledObservationKeepsItsRecordedAtThroughExport` (the timestamp survives the encrypted export), and `aCorrectionKeepsTheOriginalAndDoesNotMoveRecordedAt`. | **pass** |
| The corpus benchmark passes | `QueryBenchmarkTest` on 1,825 days: worst query `timelinePage50` **2.55 ms**, next `observationsForDayAndType` **1.97 ms**, all others under 1 ms. The timeline page was 23.7 ms on the first run because the `UNION` view sorted every branch; it now merges six indexed newest-first reads. | **pass** |

### Regression over earlier phases

- **B0 clean checkout** — a fresh `git clone` of the branch into an empty directory
  with no `local.properties` and only `ANDROID_HOME` set ran `check` and
  `:app-android:assembleDebug`: **BUILD SUCCESSFUL**.
- **B0 `./gradlew check`** — covered by the clean rebuild above.
- **B0 portability guard** — re-run because the convention plugins changed for generated
  sources; it still fails on an `android.*` import (probe above).
- **B0 licence guard** — re-run with an LGPL test dependency in `:data`, still fails.
- **B0 install and launch** — redone on the phone; no crash, no new permission.
- **B1 screenshot and contrast tests** — all 47 `:ui` tests ran in `check` (104 goldens
  verified, no golden rewritten).
- **B1 gallery** — cold-launched and inspected; no crash.
- **B0/B1 Detekt and ktlint probes** — the rules are unchanged for non-composable code;
  the new scopes are for `repo/`, `corpus/` and test sources only and are recorded in
  ADR 0005.

### Independent review and the fixes it produced

Two fresh reviewers saw only the diff and the contract (correctness and tests;
`AGENTS.md` invariants and licences) and worked in parallel. Every finding below was
confirmed in the code, then fixed in this phase and re-verified:

| Finding | Fix |
|---|---|
| Family dedup was declared but never applied, so Garmin over FIT and Garmin over Health Connect counted twice for heart rate and water | Every policy first collapses candidates to one source per upstream family, highest priority wins; a test makes the Health Connect copy the higher-priority one and still gets one day |
| Heart rate was averaged across devices, which `pr.md` §7.6 forbids | `PrimarySourcePolicy`: the day's row is the highest-priority source's series mean; the other readings stay in layer 2 |
| Restore wrote `daily_rollup` rows from the file | Restore never inserts rollups; it rebuilds them from the observations through the engine. A deliberately corrupted rollup in the bundle is overwritten by the derived value |
| A bundle missing a table restored successfully and emptied that table | Restore requires the exact table set and refuses missing or unknown tables |
| No production write path kept rollups in step | `ObservationRepository`, `NutritionRepository` and `BodyRepository` take a rollup invalidator; the engine is wired to it in tests, and a backfilled day rebuilds that day |
| Step coverage demanded midnight-to-midnight and marked every real day a fallback | Coverage is internal gaps ≤1 h over at least a 16-hour waking window; a one-hour sync is not a day, and a 07:00–23:00 watch is |
| A single `end == null` daily-total steps record was not recognised | Any `end == null` observation of a SUM type is a daily total |
| Sleep "most detailed" was the greatest sum, so a fragmented `SLEEP_AWAKE` won | Most records first, then greatest duration; stage grouping by night stays B12's |
| EWMA applied its coefficient per observation, so a weekly weigher had a 49-day half-life | Alpha is computed from the elapsed days between entries |
| The ETA took the caller's raw current weight | It divides the last smoothed trend value; the raw parameter was removed |
| The goal-rate clamp was labelled as the CDC's cited range | `allowedRangeKgPerWeek` and `citedLossRangeKgPerWeek` are separate fields |
| `HardSession` compared a session with a weekly median | It compares with the median **session** for that muscle |
| Readiness ignored a short training-load baseline and a zero sleep need | The load average carries its day count, the minimum covers every baseline, and a non-positive sleep need is not a number |
| Layer-4 entries, vitals and sets had no idempotency key or observation link | One unique `idempotency_key`, `observation_id`, `raw_record_id`, `device` and `app_version` on each; the canonical value's full provenance lives on the linked observation |
| Tombstone purge deleted the sync deletion marker | Purge clears the payload and keeps the row as the permanent marker; a later sync cannot resurrect it |
| Unguarded `deleteProfile`, `deleteSource` and `deleteSession` | Removed; `TrashService` is the only delete path, and a session cascade waits for the screen that owns it |
| Work-set restore used `INSERT OR IGNORE`, so undo could silently do nothing | Layer-4 writes are append-only; undo only marks restored after checking the row is present, so a missing row is never reported as restored |
| Food search used a bound `LIKE`, a full scan | Range seek on the name index, and 2,000 indexed foods in the corpus; the benchmark measures it |
| The timeline omitted meals | A meal branch in the view and in the paged read |
| Export completeness had no tripwire for a new table or column | Tests compare the registry against `sqlite_master` tables and `PRAGMA table_info` for every table |
| The round-trip test never exercised settings or tombstones | The fixture writes both; the comparison ignores only `computedAt` on rollups |
| The rollup property test was one fixed dataset | A seeded, shuffled incremental rebuild is compared with the full rebuild |
| A tautological dedup assertion | Removed; the property asserts the total equals one source |
| The Bouncy Castle allowlist row said test-only | It is main scope for Argon2id and test-only for Robolectric |
| The export passphrase stayed live, and a truncated container escaped as a raw exception | `export`/`read` wipe the caller's array; truncation and every GCM failure become typed errors |
| `FieldLevelMergePolicy` was a no-op | `SettingsFieldMergePolicy` adds remote-only keys and keeps the local value on conflict, until B13 adds per-field clocks |
| The SQLCipher About notice was only promised | Recorded in `NOTICE.md`, the ADR and B3's exit note; nothing is distributed yet |

A second review round ran over the fixes themselves. It confirmed most of them and
found the remaining edges below; all were fixed and re-verified before the branch was
pushed.

| Final finding | Fix |
|---|---|
| Family collapse had no deterministic tie-break, so a backfill could flip the day's winner between two equal-priority sources | The tie-break is the source id, as every other policy does; a shuffled-order test proves the same winner |
| A source reporting a daily total *and* its intervals had both summed | A SUM type aggregates the daily total only when one exists |
| The write committed before its rollup rebuild | Every repository calls its invalidator inside the write transaction; `LocalStore` wires the observation, nutrition, body and trash paths to one engine, and a source-priority change refreshes the registry and rebuilds all days |
| Restore committed the data before rebuilding rollups, and `restoreBundle` skipped the format/schema guards | Guard first, rebuild inside the restore transaction; a failed rebuild leaves the old database |
| Layer-4 writes used `INSERT OR REPLACE`, which could rewrite `recorded_at` or delete a row silently; sessions had no provenance block | Layer-4 value inserts are `INSERT OR IGNORE` (append-only); sessions carry idempotency key, observation link, raw ref, device and app version; a replay test proves `recorded_at` and the value do not move |
| Weight entered as a measurement never reached layer 3; vital rollups averaged across devices and could overwrite a layer-2 row | Measurements that name an observation type produce the day's latest value; vitals follow the type's aggregation (last or mean); a layer-2 observation wins over a layer-4 row for the same type |
| An undone tombstone kept its payload forever | Undo clears the payload; only the marker remains |
| A zero sleep need was reported as "not enough data" and a one-minute need scored 100 | Below an hour is `OUT_OF_RANGE`; a supplied baseline shorter than seven days now drops that input instead of blocking a score the remaining inputs can support |
| Two readings with the same `happenedAt` kept the earlier `recordedAt` | The later recording wins, as documented |
| The Epley citation pointed at a DOI that returns 404 | It cites LeSuer et al. 1997, the validation paper for the Epley equation, with a resolving DOI |
| `checkPortability` only looked at `import` lines | It also fails a fully-qualified `android.`/`androidx.` use; proved with `android.util.Log` in `:core` |
| Test gaps: no meal in the timeline test, no vital-only day, no two-source vital, no daily-total test, no no-backup refusal | All added; the suite is now 170 JVM tests |
| ADR 0005 overstated "under 3 ms", called the corpus measurements weekly and left a broken sentence | Corrected: the slowest query is the timeline page at about 5.2 ms against 16 ms, a measurement is seeded every 30 days, and the prose is repaired |
| Layer-4 corrections had no edit time, and a layer-4 date could disagree with its timestamp | `edited_at` is stored and mapped; every layer-4 insert checks that `local_date` is `happened_at` in its zone |
| Observations, sessions and foods had no Trash entry; no foreign keys; training volume was not rolled up | Recorded as B3/B5/B10 work rather than faked; no delete path exists for them today |

### Invariant audit (`AGENTS.md`)

| Invariant | This phase |
|---|---|
| Provenance on every value | Observations carry source, device, method, `happened_at`, `recorded_at`, local date, zone, idempotency key and raw-record ref; domain rows carry their source and timestamps; rollups carry the winning source (or none for a multi-source mean), the contributing count and the watermark. A manual entry has no raw record because the observation is the entry. **Holds.** |
| `happened_at` ≠ `recorded_at` | Two separate columns and two separate instants; backfill and correction tests prove the recording time never moves, including through the encrypted export. **Holds.** |
| Five layers, rebuildable rollups | Raw, observation, daily rollup, domain and settings are physically separate; rollups are derived, rebuilt from observations and declared never synced; property tests compare incremental and full rebuilds. **Holds.** |
| One source wins a day | Priority per type, family-aware; gap fallback hands the whole day to the next complete source; property tests prove two sources are never summed. **Holds.** |
| Local-first | No network client, permission, service or account was added. Export, restore, rollups and formulas run on the device. **Holds.** |
| Not a medical device | Validators flag plausibility with neutral issue codes; no copy diagnoses, warns passively or treats a band as a condition; readiness and ETA are labelled estimates in the code and the ADR. **Holds.** |
| No telemetry | No analytics, crash reporter or third-party SDK. The only runtime log is a local `Log.w` that names a secret that failed to decrypt, never its value. **Holds.** |
| Portability | `checkPortability` green in `:core` and `:data`; no `android.*`/`androidx.*` there; `kotlinx-datetime`/`kotlinx.serialization`/`kotlinx.coroutines` only; every platform capability is a `:core` seam. The portability probe still fails on an `android.*` import. **Holds.** |
| Licence split | Apache-2.0 client, no GPL-family dependency; SQLCipher (BSD-3-Clause) attribution in `NOTICE.md` with the About-screen requirement, Bouncy Castle (MIT-style) recorded in both; test-only libraries stay out of the APK. **Holds.** |
| Tombstone undo, not a timer | `TrashService` is the only delete path in `:data` (repository deletes were removed); a tombstone is written before the row is removed, undo works once, purge only after 30 days. **Holds.** |
| Modules hide, never delete | No module switch exists yet; the schema keeps every module's tables with no delete-on-disable path. **N/A.** |
| Weight-neutral mode | The profile column and settings key exist; the policy object is B3's and no screen reads a weight. **N/A.** |
| Chart has a table and a sentence | No chart component exists (B9). **N/A.** |
| Closed design tokens | `DESIGN.md` untouched, no colour, size, component or screen was added; `checkDesignTokens` still runs in both Android modules. **Holds.** |
| Closed cut list | Nothing from `pr.md` §14.5 was built. **Holds.** |
| Quality bars | Database: worst UI query **5.17 ms** against 16 ms on 1,825 days. Cold start and Today are unchanged because no screen was added. No budget was lowered. **Holds.** |

iOS gate review (`pr.md` §13.17): **not yet**. No change to the deferral.

### Placeholders and deferred work

- **Domain models.** Profile, exercise, routine, programme, session, set, food, meal,
  entry, measurement, vital and source are exposed to callers as the generated
  SQLDelight row types. The screens that need a richer typed model get it with their
  phase (B3 profile, B4 exercise, B5 training, B6 nutrition, B7 body); the storage shape
  is already final.
- **Cascading trash.** Only leaf entities are restorable (set, diary entry, measurement,
  vital). Deleting a session or a recipe with children needs the screen that owns the
  cascade (B3/B5), and the tombstone payload is ready for it.
- **SQLCipher attribution screen.** `NOTICE.md` records the BSD notice; B3's About
  screen must reproduce it, which is the licence's user-accessible-location condition.
- **Other platform seams.** The clock, secret vault, encrypted driver and migration
  backup are implemented. Files, notifications, the health bus, the step sensor, BLE,
  the camera and the PDF seam are declared but wait for the phases that use them
  (B8/B11/B12).
- **Dependency injection.** Koin is pinned but not started; B3 owns the application
  container and opens `LocalStore` at launch.
- **Health-data wiring.** No screen opens the database yet; the instrumented test is the
  proof that the encrypted path works end to end.
- **Migration from a real release.** Version 1 never shipped. The machinery, the
  backup-before-migration wrapper and a v1→v2 test exist; the real upgrade matrix is
  B10's.
- **Export across schema versions.** An export at a different schema version is refused
  with a clear message; converting one is B10 work.
- **`editTrail.editedAt`.** The original value and the reason are stored; the instant of
  the edit is not in the schema. Correction history needs it when the UI offers history
  (V1), and that is a migration then.

### Open issues

- The 16 ms benchmark takes the best of five runs after two warmups. On a 6× slower CI
  runner it would still pass (worst measured 2.55 ms), but a future query that lands
  between 3 and 16 ms could become flaky; the fix is a larger corpus or an index, not a
  larger budget.
- The export registry is a hand-maintained table of columns. A new column is caught by
  the round-trip test because the two exports would still match but the row counts and
  values would not include it; a new **table** is caught by
  `everyStoredTableIsInTheExportRegistry`. A new column on an existing table with a
  default value is the one case that could pass unnoticed; B10's migration matrix should
  re-check it.
- SQLCipher's native library must be loaded explicitly (`System.loadLibrary("sqlcipher")`).
  It is a one-line trap and the device test caught it; any future entry point that opens
  the database must go through `AndroidDatabaseFactory`.
- R8 does not run: no `buildTypes` block is declared, so release uses AGP's default
  `isMinifyEnabled = false`. The APK carries three unshrunk dex files (~170k methods,
  33 MB) including Bouncy Castle's full certificate-path message bundles for every
  locale. B10 owns turning it on with the keep rules SQLCipher and SQLDelight need, and
  measuring the result; it must not be switched on without a device run.
- The release APK is 41 MB unsigned (15.6 MB estimated download), largely SQLCipher's
  four ABIs and the unshrunk dex. B10
  owns ABI splits/R8 measurement and the F-Droid flavour.
- `kotlinx-datetime` 0.8 exposes `kotlin.time.Instant`; a future Kotlin may require an
  explicit opt-in. The build currently needs none.
- The Detekt scopes for `repo/`, `corpus/`, tests and tooling are deliberate and recorded
  in ADR 0005; re-check them when Detekt is upgraded.
- The SQLCipher and Bouncy Castle versions were pinned from Maven Central on 2026-11-23;
  a major bump needs the export container's KDF parameters to stay readable (they are in
  the file) and the device encryption test re-run.
- Sleep stage types are deduplicated one type at a time. B12's sleep screen groups the
  stages into a night and keeps one source; the policy comment says so.
- An export at a different schema version is refused; B10 owns the conversion matrix and
  should turn `verifyMigrations` on with real schema snapshots.
- The settings field merge keeps the local value on conflict until B13 adds per-field
  clocks and the conflict surface.
- SQLCipher's BSD notice must reach B3's About screen; B3's exit test should carry it.
- `AndroidDatabaseFactory` is the only supported way to open the store on device: it
  loads the native library, sets WAL, foreign keys and the integrity check. A future
  entry point must not construct a driver directly.


---

## B2 follow-up — review fixes (2026-11-23)

**Status:** Completed · **Date:** 2026-11-23 · **Branch:** `fix/b2-review-followups`
**Goal:** close the five findings an audit of the merged B2 raised. Four were asked for
by the owner; the fifth (R8) was found while auditing the APK and is corrected in the
documents, not in the build.

### What was found, and what was done

| # | Finding | Fix |
|---|---|---|
| 1 | The B2 log claimed the sleep dedup fix was "most records first, then greatest duration". `MostDetailedPolicy` only ever sorted by greatest summed duration; `git log -L` shows that comparator was never changed. The log described code that did not exist. | The policy now sorts by record count, then total duration, then priority, then source id — a staged night arrives as many records and a summary figure as one, which is what "most detailed" means. The deterministic source-id tie-break matches every other policy. |
| 2 | `deleteProfile`, `deleteSource` and `deleteTombstone` survived as generated queries with no caller, although the log said they were removed. Nothing stopped a later phase calling one and bypassing Trash. | Removed from the `.sq` files, each replaced by a comment saying why there is no such query. `TrashService` remains the only delete path. |
| 3 | `pr.md` §7.6's "nutrition: user-edited entries win over imported ones" was neither implemented nor recorded as deferred. | `SumAllPolicy` now collapses observations that share a `groupKey` — one entry, imported and then corrected — preferring the user-edited one, then the later recording. Observations with no group key still all count, because different foods do add up. |
| 4 | No table declared a single foreign key, so every link between layers could dangle. | Schema **v3**: 36 `REFERENCES` clauses over 15 tables, `RESTRICT` for structural parents and `SET NULL` for optional provenance links, with `2.sqm` generated from the `.sq` files. Recorded in `docs/adr/0006-referential-integrity.md`. |
| 5 | The B2 log said "release builds through R8's default config". No `buildTypes` block exists, so `isMinifyEnabled` is `false` and R8 never runs. | The two log entries are corrected. **Not** switched on here: R8 needs keep rules for SQLCipher and SQLDelight and a device run to verify, which is B10's work. Flagged as an open issue. |

### What adding the foreign keys uncovered

The constraints caught four real defects that the no-key schema had hidden. Each is
described in ADR 0006; in short:

1. **`PRAGMA foreign_keys` never worked.** It is ignored on an established connection.
   Measured: a driver opened without the property reads back `foreign_keys = 0`, and
   `execute("PRAGMA foreign_keys = ON")` leaves it at `0`. B2's `DatabaseOpener` issued
   exactly that statement, so the setting it relied on had never taken effect. Nothing
   broke only because there were no constraints to enforce. `DatabaseOpener` now takes a
   `DatabaseDriverFactory`, opens the upgrade on a keys-off connection and the store on a
   keys-on one, and **reads enforcement back** rather than assuming it.
2. **`INSERT OR REPLACE` on a parent row deletes it.** Seven parent tables used it;
   against a foreign key target that trips `RESTRICT`, and under any cascade it would
   have taken the children with it. They are now `ON CONFLICT(id) DO UPDATE`.
3. **Twelve tests were relying on dangling links** — observations naming a source that
   was never created, a set naming a session and exercise that did not exist, a diary
   entry naming an absent food. The fixtures now create the parents, as the app must.
4. **Restore order would have mattered.** The restore transaction now sets
   `PRAGMA defer_foreign_keys = ON`, so a bundle reloads table by table and is checked
   once at commit; an inconsistent bundle still fails the whole restore.

### What was run, and its result

| Command | Result |
|---|---|
| `./gradlew clean check :app-android:assembleDebug` | **BUILD SUCCESSFUL**. 171 JVM tests, 0 failures (66 `:core`, 57 `:data`, 47 `:ui`, 1 benchmark). |
| `:tools:fixtures:test` in isolation | Corpus 1,825 days; rollups 15,253 rows rebuilt in **701 ms**; slowest UI query `timelinePage50` **5.22 ms** against the 16 ms budget, every other query under 2.1 ms. |
| The same benchmark during a full parallel `check` | `timelinePage50` read **12.02 ms** — still inside budget, but it shows how load-sensitive the benchmark is. The existing open issue about a query drifting between 3 and 16 ms stands. |
| `apkanalyzer` on the release APK | `minSdk 28`, `targetSdk 37`, `allowBackup false`, `supportsRtl true`, `extractNativeLibs false`, backup and data-extraction rules present. The **only** permission is AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — no network, storage, location or health permission. No `Gallery` activity in release. 41.3 MB raw, 15.6 MB estimated download; three dex files totalling ~33 MB because R8 does not run. |
| New test `aV2DatabaseGainsItsForeignKeysAndKeepsEveryRow` | Builds a v2 database by stripping `REFERENCES` from the live schema (so the fixture tracks the schema instead of drifting), migrates it, and asserts every row survives, the keys are declared, a dangling `source_id` is refused and a source with history cannot be deleted. |
| Live guard probe: `android.util.Log` fully qualified in `:core` | `checkPortability` **FAILED** naming file and line; removed, green. |

### Not verified on hardware

The phone was not attached during this session (`adb devices` listed nothing), so the
instrumented test did **not** run. `EncryptedDatabaseTest` was extended to assert, on
device, that `PRAGMA foreign_keys` reads back `1` through SQLCipher's own SQLite and
Android's connection pool, and that a dangling `source_id` is refused. That assertion is
the only proof that `setForeignKeyConstraintsEnabled` in the open-helper callback works;
**B3 must run `:app-android:connectedDebugAndroidTest` on the phone before relying on
it.**

### Documents touched

- `prompts.md`, `BUILD-ROADMAP.md` §B0 and `AGENTS.md`: the Windows + OneDrive premises
  are gone. The repository is on a plain local path on Linux, Gradle is the wrapper, the
  PowerShell fallbacks are now shell, and B0 is recorded as having no questions.
- `docs/adr/0006-referential-integrity.md`: new.
- Two corrected B2 claims (R8, and the sleep dedup row in the findings table).

### Open issues added

- **R8 is off.** Turning it on is B10's, and needs SQLCipher/SQLDelight keep rules plus a
  device run. Until then every release APK ships unshrunk.
- **The device assertion is unrun.** See "Not verified on hardware" above.
- **`defer_foreign_keys` on restore** means a bad bundle fails at commit with a generic
  constraint error rather than naming the offending row. If restore diagnostics matter to
  the user, B10 should run `PRAGMA foreign_key_check` before committing and report it.
- **`RESTRICT`, not `CASCADE`, is deliberate.** B3/B5 cannot delete a session, routine or
  programme that has children until they write the children's tombstones first. That is
  the point, but it is work those phases must now plan for.

---

## B3 — Shell and onboarding

**Status:** Completed · **Date:** 2026-11-23 · **Branch:** `build/b3-shell-and-onboarding`
**Goal:** first launch to Today, with navigation and settings in place and nothing
hollow (`BUILD-ROADMAP.md` §B3).

### Environment and decisions taken at the start

- Workstation unchanged: Linux, JDK 21, the wrapper. Test target: the owner's
  **physical phone**, attached and authorised before the device step.
- **Questions asked (one batched dialog, recommended option first):** attach the
  phone (owner did); merge the still-open B2 follow-up PR #7 into `main` first (merged
  as `4aa5b49`, so B3 starts on schema v3 and the foreign-key fixes); use Compose UI
  instrumented tests instead of Maestro, which is not installed (owner agreed).
  Nothing about product content was asked: `pr.md` §6.1 and `DESIGN.md` §14.2 answer
  it.
- `docs/adr/0007-shell-and-onboarding.md` records thirteen implementation decisions:
  screens in `:ui` with the state holder in the app, one shell state holder, the note
  table and schema v4, observation tombstones, Koin plus a lazy store, the platform
  `BiometricPrompt`, the splash API, demo mode, the water/weight storage shapes,
  onboarding progress, the backdate strip, the water glyph and the SAF export seam.

### Milestones

1. **Policies, notes and schema v4** — commit `8bcd807`.
   `:core` gained the closed 13-module set and its three presets, the one
   `DisplayPolicy` object (weight, calories, body fat, progress photos), the profile
   options of `pr.md` §6.1 and `AgeRules` (under-16 blocked, 16–17 no weight-loss
   goal). `:data` gained the `note` table with full provenance, `3.sqm`, the timeline
   union and repository, note and observation tombstones, an explicit
   `TrashService.purge`, the export-registry row and repository reads by id. The
   database is now schema **v4**, 21 tables and three migrations.
2. **Splash, shell, Today, quick add** — commit `4de9e31`.
   The Android 12 splash API and the 28–30 starting window, a Koin-started
   `OpenHealthApp` and an `AppContainer` that opens SQLCipher lazily on IO, the
   type-safe navigation graph, the global top bar and five-tab shell with the raised
   Train, Today in the §14.2 order with honest empty states, the 3 × 2 quick-add sheet
   (note, water and weight really write; food, set and vitals say what they wait for),
   the timeline grouped by local day with provenance sheets and delete-with-undo, and
   the first logged note path.
3. **Onboarding** — in commit `4de9e31`. The three tiers, one question per screen,
   Skip on every question (a required one explains itself instead of advancing), units
   detected from the locale and converted on change, under-16 blocking, 16–17 goal
   filtering, the weight-neutral offer, the module preset, values written with
   provenance (weight and vitals as observations, body fat and tape as measurements,
   the rest as settings), and demo mode through the seeded corpus.
4. **Settings and privacy** — in commit `4de9e31`. Profile, modules (13 switches that
   hide surfaces and keep data), devices placeholder, data (timeline, Trash,
   encrypted export/restore through SAF, sample data, counts), appearance (system/dark/
   light and nine accents, applied live), privacy (app lock through the platform
   prompt, weight-neutral, the local-only statement), About (version, not a medical
   device, Apache-2.0, SQLCipher BSD attribution) and a destructive group backed by
   tombstones.
5. **Exit tests, goldens and the decisions** — commit `68b50d6`. Six instrumented
   `ShellExitTest` scenarios through the real screens, state holder and encrypted
   database (the four exits, the under-16 block and the weight replacement); eight
   screen goldens in dark, light, 200 % text and RTL (32 images);
   data tests for observation undo and explicit purge; the lazy store; ADR 0007; the
   dated `DESIGN.md` water-glyph correction; the Detekt scope for the shell state
   holder and screen files.

### What was run, and its result

| Command | Result |
|---|---|
| `./gradlew clean check :app-android:assembleDebug` | **BUILD SUCCESSFUL** from a cleaned tree: ktlint, Detekt, `checkPortability`, `checkLicences`, `checkDesignTokens`, lint, 197 JVM tests (78 `:core`, 63 `:data`, 55 `:ui`, 1 benchmark) and the debug APK. |
| `./gradlew :core:test :data:test :ui:testDebugUnitTest :tools:fixtures:test --rerun-tasks` | **BUILD SUCCESSFUL**, 197 tests, no failures. `:ui` verifies 136 goldens (104 B1 + 32 B3). |
| `./gradlew :app-android:connectedDebugAndroidTest` on the physical phone | **BUILD SUCCESSFUL**: 11 tests, 10 B3 scenarios (the four exits plus the under-16 block and the quick-add weight replacement) and B2's `EncryptedDatabaseTest` (persistence, encryption, reopen, and the foreign-key assertion B2 could not run). |
| `./gradlew :app-android:installDebug` and cold launch | `Status: ok`, `LaunchState: COLD`, onboarding renders; no `AndroidRuntime` entry from this build. |
| `adb shell am start -W` after `force-stop`, three runs | `TotalTime` **1008–1043 ms** in the **debug** build, inside the 1.5 s budget. The store opens lazily on IO after the first frame, so the launch path is Compose + Koin only. |
| Demo mode on the phone ("Try with sample data") | 90 days from the corpus; Today shows the sample-data line, readiness/steps/energy empty states and the demo profile; no crash. |
| Settings walk-through on the phone | Profile, 13 modules, devices, data, appearance, privacy, About and the destructive group render; back returns to Today. |
| `:tools:fixtures:test` (inside `check`) | Five-year corpus and the 16 ms benchmark still pass; no B2 regression. |

No device identity (serial, model, build) is recorded in this repository.

### Exit tests (`BUILD-ROADMAP.md` §B3)

| Exit test | Evidence | Result |
|---|---|---|
| Fresh install to first logged note in under three minutes | `ShellExitTest.freshInstallReachesTheFirstNoteInUnderThreeMinutes`: the real onboarding (tier 1 answered, tiers 2–3 filled later), the shell, the quick-add note sheet and a row in `note`; the assertion is `< 3 min`; measured **7.0 s** on the phone, including Compose setup and the encrypted store. | **pass** |
| Relaunch shows splash then Today | Manual on the phone: `force-stop`, `am start -W` reports `LaunchState: COLD`, then the Today screen with the sample-data line; no network wait. | **pass** |
| Turning a module off hides it and re-enabling restores its data | `ShellExitTest.turningAModuleOffHidesItAndReenablingRestoresItsData`: logs water, turns Nutrition off in Settings, asserts no Water tile, turns it on, asserts the tile returns and the observation count is still 1. | **pass** |
| Weight-neutral hides every weight, calorie and body-fat value present so far | `ShellExitTest.weightNeutralHidesWeightAndCaloriesAcrossToday`: seeds a weight, enables the mode, asserts the Today weight card (`Trend`, `Log weight`) is absent, then disables it and asserts the card returns. The `DisplayPolicy` unit tests cover all four hidden categories. | **pass** |
| Export then restore from Settings round-trips | `ShellExitTest.exportThenRestoreFromSettingsRoundTripsEveryTable`: a note, the encrypted container through the Settings `DocumentStore` seam, a second note, restore, and the first note is back while the second is gone; the profile is present. The SAF picker itself is manual (see placeholders). | **pass** |

### Regression over earlier phases

- **B0 clean build and launch** — the clean `check`/`assembleDebug` and the phone
  install/launch above.
- **B0 portability and licence guards** — unchanged and run in `check`; the new
  `:ui -> :core` edge is a pure Kotlin dependency and no `android.*` reached a pure
  module.
- **B1 design system** — all 104 goldens verify; the new water glyph is the only icon
  change. The 200 %/RTL passes are part of the same run.
- **B2 rollups, dedup, export and the encrypted device test** — the 61 `:data` tests
  run in `check`; B2's instrumented foreign-key assertion **was run on the phone** in
  the connected suite and passes. The benchmark still passes.

### Independent review and the fixes it produced

Three fresh reviewers saw only the B3 diff and the contract (correctness and tests;
`AGENTS.md` invariants and licences; `DESIGN.md` conformance). Every finding below was
confirmed in the code or a golden, then fixed in this phase and re-verified.

| Finding | Fix |
|---|---|
| Quick-add weight wrote a layer-4 measurement, which the rollup skips when the day already has a weight observation, so a second weigh-in was silently ignored | Weight is written as a `WEIGHT` observation (LAST wins). The new `quickAddWeightReplacesTheDaysWeight` test proves 81 then 82 replace the day's value |
| Water was a zero-length interval while `WATER` is declared `DAILY_TOTAL`; a later source with a total would drop every glass | Each glass is one `end = start` part of the manual source; the rollup sums that source's parts and a daily total from any one source replaces only that source's intervals (tested in `RollupEngineTest`) |
| Tapping a recent weight row converted canonical kg through the imperial converter again, storing 36.3 kg for 80 kg | The recent value is parsed as canonical kg; the weight form labels the profile's unit |
| The encrypted store opened during composition on the main thread, and a Keystore failure crashed out of `viewModel` | `AppViewModel` no longer touches the store in its constructor; the store opens on IO in `rebuild`, failures set `fatal` and show the startup screen. Cold start is now 1008–1043 ms |
| 16–17 could select Lose fat in Settings and could set a goal weight/target date | Settings filters the goal and rejects it in the save; `visibleSteps` hides GoalWeight while under 18 |
| Schema v3→v4 created the note table but not the `timeline` view, so upgraded devices would hide notes in the view path | `3.sqm` drops and recreates the view; `aV3DatabaseRecreatesTheTimelineViewWithNotes` asserts it |
| Weight-neutral leaked weight through the timeline, profile sheet, quick-add tile/recent rows, Vitals action and nutrition values | A `sensitive` row flag filters weight/body-fat/nutrition rows; the tile, recent rows, profile field and Vitals action are policy-gated |
| Restore replaced the whole record with no confirmation and no snapshot | The Settings restore now confirms by name, takes an encrypted local snapshot first (Keystore-held passphrase) and offers Undo last restore |
| App lock only engaged on the first load of a process | `ON_STOP` re-arms it; API 28 no longer offers a lock that could never authenticate |
| Trash's permanent delete purged `items.first()`, an unselected row | Each row has its own permanent-delete action and confirmation |
| Timeline truncated at 200 rows and dropped diary entries | Older pages load on request; entries are mapped; the view migration covers notes |
| Timeline rows were 46 dp and had no long-press delete | A 60 dp event row with long-press delete and undo; the provenance sheet gained value, local day + zone, quality, raw record and the recording delay in words at 46 dp |
| Quick-add tiles rippled and announced their label twice; empty states lacked the §6.7 glyph/17-600 line | `ohClickable` + press scale, text-only label; `OhEmptyState` now has the glyph and weight, with actions on Timeline/Trash/roots |
| Today's cards touched, the week strip drew seven discs, Today kept a months-old profile weight and had no unit | 12 dp between cards, only today's disc, weight comes from today's observation only and converts to the profile's unit |
| Settings mixed unit systems, showed a 17-year-old Lose fat, said "1 items", used accent for errors and had no avatar | Display and placeholders follow the profile's units; the goal list is age-filtered; plurals; red-ink + icon; initials avatar |
| Selection used a trailing check, which is not one of §6.2's treatments | A 2 dp accent inset line on the selected row |
| Dates and times were English/24-hour and the app name was a literal | `mediumDate`, `shortDate`, `timeOf`, `hourOf` and `recordingDelay` use the observed locale; `app_name` is a resource |

**Review fixes shipped in `2864314`.** The goldens were re-recorded after the fixes and
verify in `check`; the instrumented suite was re-run on the phone (7/7).

**Independent Claude audit (same day).** A headless `claude -p` pass read the contract and
the B3 diff, fixed 20 more defects in code and tests, and left three owner decisions and
five known gaps. Its fixes were: water as per-glass parts with a per-source total rule;
the 16–17 rule in `saveProfile`; out-of-range tape values and comma-decimal body fat/RHR/BP;
undo/restore engine refresh and double-tap repeat; the profile sheet reading the display
policy; Vitals/module and units-vs-weight-neutral leaks; empty export passphrases and
passphrase zeroing; export/restore busy feedback; the startup retry and light window;
timeline §7.3 tags and "Entered by you"; locale number formatting; and the timeline
provenance copy. Reports: **197 JVM tests**, **11/11 connected**. Owner decisions: the
`indigo` Edited tag's contrast against §11.1 (rendered neutral), the raised Train disc's
selected state (now a stroke-weight change, no outside border), and the contract wording
for the water shape recorded in ADR 0007 §9. Known gaps: the `timeline` view/repository
put `entry.source_kind` in the source column and omit `work_set` (B5/B6 migration);
`TrashService.preview` has untranslated type names (data has no resources); export/restore
have no time budget; `rebuild()` refreshes every model; Today's demo numbers have no
provenance sheet.

**Deliberately not changed, and why:** Today's demo numbers do not open a provenance
sheet yet; the real cards replace them in B5–B9 and the timeline already exposes their
provenance. The bars still use the 94 % fill without a real
backdrop blur (a platform-mode feature with Reduce Transparency, B10); app lock does not
immediately re-prompt when switched on (it re-arms on the next background); the timeline
has no source filter or Edit/Exclude on measurements (B6/B8 add the sources and the
edit affordance); session rows keep a plain "Workout session" title; and the demo
corpus still has no bulk-remove action.

### Invariant audit (`AGENTS.md`)

| Invariant | This phase |
|---|---|
| Provenance on every value | Onboarding weight, RHR and BP are observations; body fat and tape are measurements; notes, water and weight writes all carry source, device, method, `happened_at`, `recorded_at`, local date, zone and idempotency key. The profile keeps its source and revision. **Holds.** |
| `happened_at` ≠ `recorded_at` | Quick add backdates `happened_at` and never moves `recorded_at`; the clamp refuses a future time. **Holds.** |
| Five layers, rebuildable rollups | The new writes go through `LocalStore`/repositories and rebuild their day; notes have no rollup. **Holds.** |
| One source wins a day | Manual water is a SUM of one source; weight is LAST; no screen sums two sources. **Holds.** |
| Local-first | No network permission, client or sync. Export is a person-chosen file. **Holds.** |
| Not a medical device | Onboarding, About and Today copy stay neutral; no readiness score is invented; the age rule is a plain support boundary. **Holds.** |
| No telemetry | No analytics or third-party SDK; the only new dependency set is AndroidX/Koin (Apache-2.0). **Holds.** |
| Portability | `:core` gained pure policy objects; `checkPortability` passes in every pure module; `:ui` depends on `:core`, not `:data`. **Holds.** |
| Licence split | Apache-2.0 client; Koin/Stately and AndroidX recorded; SQLCipher attribution is in About and `NOTICE.md`; no GPL-family dependency. **Holds.** |
| Tombstone undo, not a timer | Notes, observations, measurements and vitals delete through `TrashService`; the toast is the shortcut; Trash keeps 30 days. **Holds.** |
| Modules hide, never delete | Thirteen switches change surfaces only; the module-off test proves the stored observation survives. **Holds.** |
| Weight-neutral first-class | One `DisplayPolicy` object; no screen reads the switch; Today omits the weight and energy cards; the test proves both states. **Holds.** |
| Chart has a table and a sentence | No chart is rendered in B3. **N/A.** |
| Closed design tokens | Every new value comes from `OhTheme`; `checkDesignTokens` passes in `:ui` and `:app-android`; the only DESIGN edit is the dated water glyph. **Holds.** |
| Closed cut list | Nothing from `pr.md` §14.5 was built. **Holds.** |
| Quality bars | Cold start **1008–1043 ms debug**, inside 1.5 s; Today is an empty skeleton, so the 300 ms warm target is not yet exercised. The release number is B14's. |

iOS gate review (`pr.md` §13.17): **not yet**. No change to the deferral.

### Placeholders and deferred work

- **Food, set and vitals quick-add sheets** are honest shells; the real forms arrive
  with B6, B5 and B7. They say so and offer a note instead.
- **Today's hero body card** is an empty state; the body model and its recovery map are
  B4.
- **Vitals, Train, Nutrition and Stats roots** are empty states with one working action
  each; their content is B5–B9.
- **Backdating** is a seven-day strip plus an hour stepper; the full calendar picker
  belongs with the logging screens (B5–B7).
- **Baseline photo** (tier 3) waits for the photo work in B7; the step is not shown.
- **Server privacy choice and passkey** (`pr.md` §6.1) wait for the optional server
  (B13); onboarding states local-only honestly.
- **Devices and integrations** is an empty state; Health Connect, Garmin and BLE are
  B8.
- **App lock**: the platform prompt and the lock screen are implemented and the lock
  screen is goldened; the owner's biometric or PIN was not exercised by an automated
  test (that would need their credential). The switch is disabled when the phone has no
  screen lock.
- **Export/restore SAF picker** is manual; the encrypted round-trip behind it is
  automated.
- **Demo data** has no bulk-remove action; it is only offered on an empty database or
  at first launch, and the label says it is sample data.
- **Deep module surfaces** (Coach, Family, Mental health, Cycle) only affect what B3
  renders.

### Open issues

- **Cold start** is now 1008–1043 ms in the debug build. The release figure still
  belongs to B14's macrobenchmark; a baseline profile is B10/B14 work.
- **`AppViewModel` is large** (80 helpers). Detekt's `LargeClass` and
  `TooManyFunctions` are scoped to it and to `screen/**`; splitting into per-screen
  holders is the next structural step. Do not widen the scope.
- **Process death and rotation:** onboarding answers and the current step live in the
  `AppViewModel`, not `SavedStateHandle`, so a process kill mid-onboarding restarts at
  Welcome and an open Settings sheet closes on rotation. The quick-add text survives in
  `AppState`; the rest is a later reliability pass.
- **The timezone is captured at container creation.** A timezone change while the app
  is running is not applied until the next process start; the rollups use the zone on
  each value, so the dates stay correct, but the displayed "today" can lag.
- **The profile sheet duplicates the unit conversion** with the onboarding converter;
  a shared helper should replace both when the profile gains more units.
- **The backdate strip is not a calendar**; a past date older than seven days needs the
  logging screens' picker.
- **The Today week strip** shows no session dots yet (training is B5); its day cells are
  dates only.
- **Backdrop blur on the top and tab bars is still not implemented.** B1 deferred it
  to the shell; B3 draws the documented 94 % `bg-el` fill and the hairline, and the
  content behind is faintly visible instead of blurred. A real backdrop blur and the
  Reduce Transparency switch are one platform-mode feature and remain B10's, as B1's
  placeholder said. The screen reserve keeps every control clear of the bar.
- **The `Skip` key on a required question** explains itself rather than advancing; this
  is the resolution of the "Skip always visible" recipe with "tier 1 required" and is
  recorded in ADR 0007.
