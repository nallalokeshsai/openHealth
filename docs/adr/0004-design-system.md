# ADR 0004 — Design system: implementation decisions

**Date:** 2026-11-23 · **Phase:** B1 · **Status:** accepted
**Context:** `DESIGN.md` fixes the values, `BUILD-ROADMAP.md` §B1 fixes what must exist.
Building them raised a handful of choices that are not spelled out in either file and
that a later phase would otherwise re-litigate. None of them adds a colour, type role,
spacing value, radius or component.

## Decisions

| # | Decision | Why |
|---|---|---|
| 1 | **One `OhTheme` over CompositionLocals** (`LocalOhColors/Type/Motion`), with `OhTheme.colors/type/space/radius/dims/motion` accessors. No `MaterialTheme` is installed and nothing reads `MaterialTheme.colorScheme`. | `DESIGN.md` Appendix B. Material 3 is used only for `ModalBottomSheet`'s behaviour; every visible colour, shape and inset is passed in explicitly. `checkDesignTokens` fails on a `MaterialTheme` reference. |
| 2 | **No typeface is bundled.** The type roles use `FontFamily.Default` (Roboto on Android). | `DESIGN.md` §3.1: "We ship no typeface at 1.0." There is therefore no font licence to record; `NOTICE.md` says so. |
| 3 | **`acc-2` for the eight non-brand presets** is the preset's own "Accent dark" in dark and its "Light ink" in light; `brand` keeps `#84CC16` / `#65A30D`. | Appendix A specifies `acc-2` for `brand` only and no other hex may be invented (§0.2). Both resolved values are already in the preset table and clear 3:1 on `surface`. Recorded as a dated clarification in `DESIGN.md` §2.6. |
| 4 | **Chip text is `subhead` 15/400.** | §5.9 said 14/400, but 14 sp is not one of the 11 roles of §3.2, and §0.2 forbids a twelfth step. §5.9 now states 15/400. |
| 5 | **Row padding is 12/16.** | §5.3 states 12 vertical / 16 horizontal and the hairline inset depends on 16; Appendix A's "11/14" disagreed. Appendix A now matches §5.3. |
| 6 | **Component geometry lives in `OhDims`**, quoting §4.3/§4.5/§5; spacing and radii stay in `OhSpace`/`OhRadius`. | The build needs sizes the token set does not carry (50 dp button, 46 dp row, 36 dp icon button). They are quoted values, not new tokens, and `checkDesignTokens` forbids a raw `.dp` outside the theme package. |
| 7 | **`checkDesignTokens` is a Gradle task**, not a Detekt rule: it fails on a `Color(...)` literal, a raw `.dp`/`.sp`, or a `MaterialTheme.` reference outside `ui/theme`. | Same shape as B0's `checkPortability`, so the guard is one readable task and runs in `check` in every Android module. |
| 8 | **Detekt and ktlint are scoped for `@Composable`**, via `gradle/detekt.yml` and `ktlint_function_naming_ignore_when_annotated_with`. | A composable's name is its component, its parameters are its public API and its body is a layout tree. Every non-composable function keeps the default rules; a probe proved the scoped rules still fire elsewhere (B0 pattern). |
| 9 | **Roborazzi without its Gradle plugin**, `-Proborazzi.record` to record and plain `check` to verify, goldens committed under `ui/src/test/screenshots/`. | The plugin's AGP 9 support is an unmerged branch (takahirom/roborazzi #781) and it races on Gradle 9 (#830). The no-plugin route is verified end to end and keeps `check` hermetic. |
| 10 | **At 200 % text, components adapt instead of clipping**: fixed heights become `heightIn(min = …)`, tab labels drop below the icons, the segmented control scrolls, and dialog actions stack. | §11.1 requires 200 % without loss of content or action; the screenshots at `text200/` are the evidence. No size shrinks and no hit target drops below 44 dp. |
| 11 | **Window frame only in XML**: a `Theme.OpenHealth` with no ActionBar and a black window, applied in the manifest. | The app had no theme, so the platform ActionBar drew over the design. Edge-to-edge, insets and the real shell theme are B3's; the gallery reserves the status bar itself. |

## Consequences

- Later phases must add anything new to `DESIGN.md` Appendix A first, or `checkDesignTokens`
  and the reviewer will reject the screen.
- The screenshot matrix is 26 tests × 4 variants (dark, light, 200 %, RTL) = 104 goldens,
  about 0.9 MB. A component change re-records with `-Proborazzi.record`; CI never records.
- `OhTheme` is the only way to reach a token; a screen that needs a value the theme does
  not expose is a signal to amend `DESIGN.md`, not to inline a literal.
