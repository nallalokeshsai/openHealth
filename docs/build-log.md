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
| `:app-android:assembleRelease` | **BUILD SUCCESSFUL**; unsigned release APK produced (release builds through R8's default config). |
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
