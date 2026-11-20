# openHealth — Design Guide

**Version:** 1.0 · 2026-11-20
**Status:** Binding for all UI work until superseded.
**Platform:** Android phone (Kotlin + Jetpack Compose); Wear OS in Phase 2. iOS, web and desktop are deferred (`pr.md` §0.1), so nothing here is designed for them yet.
**Source of truth for screens:** the mockup canvas — https://claude.ai/artifact/PSZyUjMjRjayqkjC2tqBjR · downloaded HTML copy: [`openHealth App Mockups.html`](<openHealth App Mockups.html>) (use this when the canvas link is not accessible)
**Product contract:** [`pr.md`](pr.md). Where this file and `pr.md` disagree on behaviour, `pr.md` wins; on appearance, this file wins.

---

## 0. How to use this file

If you are an agent or developer building a screen:

1. Read §1 (the five rules), §2 (color), §3 (type), §4 (space).
2. Find your component in §5 and copy its spec. Do not invent a variant.
3. Check §6 if the screen shows numbers or charts, §7 if it shows a value that came from somewhere (it almost always did).
4. Run the §10 checklist before you call it done.

Every hex, size and radius here is a decision, not a suggestion. If a value you need is missing, use the nearest token and raise it — do not add a sibling shade.

---

## 1. The five rules

1. **Calm over clever.** One primary color, one accent, a warm neutral ground. No gradient washes, no glow, no decorative illustration, no emoji anywhere in the product UI.
2. **The number is the design.** Data is set in a distinct typeface at a large size; chrome shrinks around it. Never style a label larger than the value it labels.
3. **Every number shows where it came from.** A value with no provenance affordance is a bug (§7).
4. **Honest states, not happy states.** Offline, stale, estimated, entered-late and conflicted are designed states with their own treatment — never hidden, never red-alarmed.
5. **Reachable first.** Primary actions sit in the bottom third. Nothing essential lives behind a long-press, a swipe, or a hover.

---

## 2. Color

### 2.1 Tokens

Copy this block verbatim as the root of the stylesheet.

```css
:root {
  /* Ground & surfaces */
  --oh-ground:          #F4F2EE;  /* app background, sunken areas inside cards */
  --oh-surface:         #FFFFFF;  /* every card, sheet, list row, input */
  --oh-surface-inverse: #14171A;  /* rare: export/report card, full-bleed CTA */
  --oh-scrim:           rgba(20, 23, 26, 0.55); /* behind a sheet */

  /* Lines */
  --oh-border:          #E7E3DC;  /* card and section outlines */
  --oh-border-strong:   #D8D2C8;  /* interactive outlines: buttons, inputs, chips */
  --oh-border-subtle:   #F0EDE7;  /* dividers between rows inside one card */
  --oh-border-dashed:   #C9C2B6;  /* empty slot / "add" affordance */
  --oh-track:           #EDEAE3;  /* progress and ring tracks */
  --oh-track-alt:       #E9E5DD;  /* segmented-control trough */

  /* Ink */
  --oh-ink:             #14171A;  /* primary text, values */
  --oh-ink-2:           #4A5159;  /* secondary text, section labels */
  --oh-ink-3:           #5E6670;  /* meta, captions, inactive tab */
  --oh-ink-4:           #9BA3AA;  /* chevrons, disabled, future rows — NEVER plain text */

  /* Primary — deep teal */
  --oh-primary:         #0E5A6B;  /* CTAs, active nav, series 1, selection */
  --oh-primary-hover:   #0A414D;  /* link/button hover and press */
  --oh-primary-deep:    #0A4855;  /* panel inside a primary surface (rest timer) */
  --oh-primary-soft:    #E3F0F2;  /* soft badge / icon tile background */
  --oh-on-primary:      #FFFFFF;
  --oh-primary-muted:   #BFDDE3;  /* secondary text ON a primary surface */
  --oh-primary-border:  #4C8795;  /* outline ON a primary surface */

  /* Accent — clay amber: attention, never alarm */
  --oh-accent:          #B45F1E;  /* graphics, and text on WHITE only */
  --oh-accent-text:     #8A4A14;  /* text on ground or accent-soft */
  --oh-accent-soft:     #F6E4D2;  /* badge background */
  --oh-accent-surface:  #FFF7EF;  /* full card background for a flagged row */
  --oh-accent-border:   #E8D3BC;

  /* Data series (see §6.2) */
  --oh-series-1:        #0E5A6B;
  --oh-series-2:        #4C8795;
  --oh-series-3:        #B45F1E;
  --oh-fill-1:          #8AA7AE;  /* labelled fills only */
  --oh-fill-2:          #BFD3D7;  /* labelled fills only */
  --oh-point:           #B8C6CA;  /* raw check-in dots behind a trend line */
}
```

### 2.2 Where each color goes

| Need | Token | Note |
|---|---|---|
| App background | `--oh-ground` | Never white. White is a card. |
| Card / row / input background | `--oh-surface` | 1px `--oh-border`, radius 16 (card) or 14 (row). |
| Primary action | `--oh-primary` bg + `--oh-on-primary` text | One per screen region. |
| Secondary action | `--oh-surface` bg + `--oh-border-strong` + `--oh-ink` text | |
| Tertiary / destructive-adjacent | text-only in `--oh-ink-2`, no border | |
| Active tab, selected chip, checked state | `--oh-primary` | |
| "Needs attention" (short on protein, entered late, raised reading) | `--oh-accent-text` on `--oh-accent-soft` | Not red. |
| Hero plan card (Today) | `--oh-primary` surface, `--oh-primary-muted` sub-text, `--oh-primary-border` outlines | Exactly one per screen. |
| Report / export block | `--oh-surface-inverse` with `#C9CDD1` sub-text | Exactly one per screen. |

### 2.3 Hard color rules

- **No red/green pass-fail.** Status is carried by teal (on track) vs amber (watch), which differ in lightness as well as hue, plus a word. Red is reserved for destructive confirmation dialogs only, which are not yet designed — ask before inventing one.
- **Amber as text:** `#B45F1E` on white is 4.57:1 — allowed. On `--oh-ground` it is 4.09:1 — **not allowed**; use `--oh-accent-text` (`#8A4A14`, 6.45:1 on `--oh-accent-surface`, 5.52:1 on `--oh-accent-soft`).
- `--oh-ink-4` is for chevrons, disabled controls and not-yet-reached rows. It never carries information a user must read.
- Clinical ranges are drawn as a neutral scale (§6.4), never as a traffic light.
- No shadows except one: a sheet lifting off the scrim, `0 -10px 30px rgba(0,0,0,0.25)`. Cards are separated by their border, not by elevation.

### 2.4 Measured contrast (keep these true)

| Pair | Ratio | Verdict |
|---|---|---|
| `--oh-ink` on `--oh-surface` | 18.0:1 | ✓ |
| `--oh-ink` on `--oh-ground` | 16.1:1 | ✓ |
| `--oh-ink-2` on `--oh-surface` | 8.0:1 | ✓ |
| `--oh-ink-3` on `--oh-surface` | 5.8:1 | ✓ small text OK |
| `--oh-ink-3` on `--oh-ground` | 5.2:1 | ✓ small text OK |
| `--oh-primary` on `--oh-surface` | 7.8:1 | ✓ |
| `--oh-on-primary` on `--oh-primary` | 7.8:1 | ✓ |
| `--oh-primary` on `--oh-primary-soft` | 6.7:1 | ✓ |
| `--oh-accent` on `--oh-surface` | 4.57:1 | ✓ text, and ≥3:1 as a graphic |
| `--oh-accent` on `--oh-ground` | 4.09:1 | ✗ text · ✓ graphic only |
| `--oh-series-2` on `--oh-surface` | 4.0:1 | ✓ graphic |
| `--oh-fill-1` / `--oh-fill-2` on `--oh-surface` | 2.6:1 / 1.6:1 | ✗ alone — labelled fills only |

---

## 3. Typography

### 3.1 Families

```css
--oh-font-text: Inter, system-ui, -apple-system, "Segoe UI", sans-serif;
--oh-font-num:  "Space Grotesk", Inter, system-ui, sans-serif;
```

- **Inter** — all prose, labels, buttons, navigation. Weights 400, 500, 600.
- **Space Grotesk** — **every number a user reads as data**, plus screen titles. Weights 500, 600.
- Always set `font-variant-numeric: tabular-nums` on numbers that sit in a column or update in place (set tables, timers, trend values).
- Ship both as self-hosted woff2 with `font-display: swap`; no third-party font request at runtime.

### 3.2 Scale — these steps only

| Step | px / weight / family | Used for |
|---|---|---|
| `display` | 34 / 600 / num | The one hero metric on a detail screen (weight, BP average) |
| `metric-xl` | 26 / 600 / num | Card hero number (steps, kcal remaining) |
| `title` | 25 / 600 / num | Tab-screen title ("Train", "Food") |
| `metric-lg` | 21 / 600 / num | Secondary hero; pushed-screen title (`Space Grotesk`, 20–21) |
| `metric-md` | 18 / 600 / num | Stat grid values, input values |
| `heading` | 16 / 600 / text | Card headline in a sentence ("Proposal for next week") |
| `metric-sm` | 15 / 600 / num | Inline value in a row |
| `body-lg` | 15 / 600 / text | Primary button label |
| `body` | 14 / 400–600 / text | List row title, form labels, body copy |
| `body-sm` | 13 / 400–500 / text | Descriptions, secondary buttons, links |
| `meta` | 12 / 400 / text | Captions, timestamps, helper text |
| `label` | 12 / 600 / text / `uppercase` / `0.05em` | Section label (`--oh-ink-2`) |
| `micro` | 11 / 400–500 / text | Field labels, chip text, legend |
| `nav` | 10 / 500–600 / text | Tab bar label |

Line height: 1.15 for `display`–`title`, 1.3 for headings, **1.45–1.5 for anything over one line**. Never below 1.4 for prose.

The mockups contain half-steps (12.5, 13.5, 14.5) from hand-tuning. **Collapse them to the nearest step above when you build.**

### 3.3 Type rules

- A screen has exactly one `title`. A card has at most one `heading` or one `metric-xl`.
- Units are 55–60% of their number's size, weight 500, `--oh-ink-3`: `82.4` `display` + `kg` at 16/500.
- Section labels are 12/600 uppercase `0.05em` in `--oh-ink-2` — they are the only uppercase text in the product.
- Never uppercase a value, a name, or a sentence.
- Support dynamic type: sizes are **sp** in Compose (px here = sp there, 1:1); layouts must survive a 200% font scale without clipping (§10).

---

## 4. Space, shape, layout

### 4.1 Scale

Base 2px. Use only: **4, 6, 8, 10, 12, 14, 16, 20, 24**.

| Slot | Value |
|---|---|
| Screen gutter (left/right) | 16 |
| Header top padding | 20 |
| Gap between cards | 12 |
| Card padding | 14 |
| Gap inside a card (stacked blocks) | 10–12 |
| Row vertical padding in a list | 10 |
| Chip padding | 4 / 8 |
| Button padding (horizontal) | 13–16 |
| Icon ↔ label gap | 6–8 |

### 4.2 Radius

| Token | px | Applied to |
|---|---|---|
| `--oh-r-xl` | 16 | Section cards, sheets (top corners 22) |
| `--oh-r-lg` | 14 | List cards, primary CTA, profile tile |
| `--oh-r-md` | 12 | Buttons, inputs, icon buttons |
| `--oh-r-sm` | 10 | Icon tiles, inner boxes, small square buttons |
| `--oh-r-xs` | 6 | Plate chips, checkbox squares (7), bar ends (3–4) |
| `--oh-r-pill` | 999 | Chips, filters, badges |

### 4.3 Frame

- Design target **390 × 844**, which is **dp on Android** (1 px here = 1 dp there). Verify every screen at **360 dp** (the common small phone) and **412 dp** (large), and at 320 dp before shipping. Taller artboards on the canvas show the full scroll; they are not taller devices.
- Layout is a column: `header` → scrolling `main` → fixed `nav`. `main` scrolls; header and tab bar do not.
- **No fake status bar.** Start the design at the app header; the OS owns the strip above it.
- Draw edge-to-edge and consume insets: the tab bar takes `WindowInsets.navigationBars` (gesture or 3-button), the header takes `statusBars`, and any focused input takes `ime`. The 11 dp bottom padding in §5.11 is a *minimum* on top of the inset.
- **Predictive back** must show the real destination: a sheet dismisses, a pushed screen peels back to its parent, and the tab bar never animates under the gesture.
- Tablets, foldables and large screens are **not yet designed**. Until they are: keep the same column centred at a 480 dp maximum, never stretch a card past 560 dp, and never let the six-tab bar span a 900 dp window.

---

## 5. Components

Specs are exact. Where a mockup differs, the spec wins (§12 lists the known deltas).

### 5.1 Screen header
Tab screens: `title` on the left, up to two 44×44 icon buttons on the right (`--oh-surface`, 1px `--oh-border-strong`, radius 12, icon 19px). Pushed screens: 44×44 back button (chevron-left), then `metric-lg` title, then one optional action.

### 5.2 Card
`--oh-surface`, 1px `--oh-border`, radius 16, padding 14. Optional header row: `label` on the left, a 13/500 `--oh-primary` link or a 12 `--oh-ink-3` meta on the right, 10–12 below it.

### 5.3 List row (inside a card)
Divider `1px --oh-border-subtle` between rows, never above the first. Height ≥44. Layout: optional 32–38 icon tile (radius 10, `--oh-primary-soft`) → flexible text block (title `body` 600 + meta 12 `--oh-ink-3`) → trailing value (`metric-sm`) or chevron (17px, `--oh-ink-4`).

### 5.4 Standalone list card
Same as 5.3 but each row is its own card: radius 14, padding 12/13, gap 8 between rows. Use when rows are independently tappable things (routines, timeline entries, search results).

### 5.5 Buttons

| Variant | Spec |
|---|---|
| Primary | `--oh-primary` bg, white 15/600, radius 12–14, min-height 46 (50 for a full-width screen CTA) |
| Secondary | `--oh-surface` bg, 1px `--oh-border-strong`, `--oh-ink` 14/500, radius 12, min-height 44 |
| Tertiary | text only, `--oh-ink-2` 13/500, min-height 44 |
| On primary surface | white bg + `--oh-primary` text, or transparent + 1px `--oh-primary-border` + white text |
| Icon-only | 40–44 square, radius 10–12, 1px `--oh-border-strong`, 17–19px icon, **`aria-label` required** |

Press state: background one step darker (`--oh-primary-hover`) or `--oh-track` for secondary. No scale transform.

### 5.6 Chip / badge
Pill, padding 4/8, 11/500. Three kinds only:
- **Source chip** — `--oh-ground` bg, `--oh-ink-2` text, optional 11px icon (§7).
- **State chip** — `--oh-accent-soft` bg, `--oh-accent-text` text ("Entered late", "1 PR", "short").
- **Affirm chip** — `--oh-primary-soft` bg, `--oh-primary` text ("In range", "Logged by you 9 times").

### 5.7 Filter chip (interactive)
Min-height 34, padding 0/12, 13/500. Selected: `--oh-primary` bg + white. Unselected: `--oh-surface` + 1px `--oh-border-strong` + `--oh-ink`. A horizontal row of these scrolls; it never wraps to a second line.

### 5.8 Segmented control
Trough `--oh-track-alt`, radius 11, padding 3. Items flex-1, min-height 38, radius 9. Selected: `--oh-surface`, 13/600, `--oh-ink`. Unselected: transparent, 13/500, `--oh-ink-2`. Max 4 items; beyond that use filter chips. Mark up as `role="tablist"` with `aria-selected`.

### 5.9 Input
Min-height 46–48, radius 11–12, 1px `--oh-border-strong`, padding 0/12, `--oh-surface`. Value text: numbers in `metric-md` num/600, prose in `body`. Focus: 1.5px `--oh-primary` border plus a 2px outline offset 2 — never remove the focus ring. Label 11 `--oh-ink-3`, 5px above; a visually hidden `<label>` is still required when the label is implied by context.

### 5.10 Toggle row
`body` label (+ optional 11.5 `--oh-ink-3` sub-line) on the left, native checkbox-as-switch 42×26 `accent-color: var(--oh-primary)` on the right. Rows divided by `--oh-border-subtle`. An "off" module label drops to `--oh-ink-3`.

### 5.11 Tab bar
`--oh-surface`, 1px top `--oh-border`, padding 7/2/11. Six items, flex-1, min-height 48, icon 20px above a 10px label, gap 4. Active: `--oh-primary` + 600 + `aria-current="page"`. Inactive: `--oh-ink-3` + 500. Order is fixed: **Today · Train · Food · Body · Insights · More**.

### 5.12 Ring (goal dial)
76×76 SVG, `r=33`, stroke-width 8, round cap, rotated −90°. Track `--oh-track`; progress `--oh-primary` (readiness, steps) or `--oh-accent` (calories remaining). Centre holds `metric-xl`/`metric-lg` value + 10px `--oh-ink-3` caption. `aria-hidden` on the SVG — the value is live text.

### 5.13 Progress / macro bar
Height 6–8, radius 3–4, track `--oh-track`. One fill per bar, labelled above with name (12 `--oh-ink-2`) and `value / target` (`metric-sm`). A stacked bar (calories by macro) uses series 1 → 2 → `--oh-fill-1` left to right and is always accompanied by the numeric breakdown.

### 5.14 Set table (the busiest control in the app)
Columns: Set (34) · kg · Reps · RIR · Done (44, right-aligned). Header 11/600 uppercase `--oh-ink-3`. Body in num font, tabular.
- Completed row: `--oh-ink`, done tile 28 radius 9 `--oh-primary` with a white check.
- Warm-up row: whole row `--oh-ink-3`, done tile `--oh-primary-soft` + teal check.
- **Active row:** background `#F6FAFB`, top border 1.5px `--oh-primary`, inputs 40–46 tall with 1.5px `--oh-primary` border, done button 40×40 `--oh-primary`.
- Future row: `--oh-ink-4`, empty 28 box with 1.5px `--oh-border-dashed`.
One tap on Done advances the row; nothing else moves on screen.

### 5.15 Sheet (quick add, pickers)
Scrim `--oh-scrim` over the dimmed screen. Sheet `--oh-ground`, top radius 22, shadow per §2.3, 38×4 grab handle centred with 10 below. Title `metric-lg` left, 36×36 close button right. Sheets never nest; a second level replaces the first.

### 5.16 Action grid (quick add)
3 columns, gap 9, each tile min-height 84, `--oh-surface`, 1px `--oh-border-strong`, radius 14, 21px `--oh-primary` icon above a 13/500 label.

### 5.17 Undo bar
Pinned above the tab bar: `--oh-surface`, 1px top `--oh-border`, padding 10/16. Message 12 `--oh-ink-2` left; "Undo" pill right (min-height 38, 1px `--oh-primary`, `--oh-primary` 13/600). Lives 8 seconds, pauses on focus, and is reachable by keyboard before it dismisses.

### 5.18 Banner (offline / stale / caution)
Radius 12, padding 10/12, 12px text, 15px icon. Offline or caution: `--oh-accent-surface` bg, `--oh-accent-border`, `--oh-accent-text`. Neutral info: `--oh-surface` bg, `--oh-border`, `--oh-ink-2`. Banners sit inside the content column, never over the header.

### 5.19 Icons
Inline SVG, 24-box, `stroke-width: 1.9` (2.2–2.6 for checks and plus), round caps and joins, `currentColor`. Sizes: 20 nav, 19 header, 17 inline/chevron, 15 in text, 11 in chips. Decorative → `aria-hidden="true"`. Meaningful → `role="img"` + `aria-label`. **No emoji, no filled icon sets, no third-party icon font.**

---

## 6. Numbers and charts

### 6.1 Formatting
- Thousands separator per locale (`6,240`). Weight 1 decimal; macros and loads 1 decimal only when non-zero (`82.5`, `96 g`). Percentages 1 decimal.
- Duration as `7 h 10 m`, elapsed as `24:16`, rest countdown as `1:28`.
- A target is always written `value / target` with the unit once: `96 / 165 g`.
- Ranges, never fake precision: energy burn is `410–520 kcal`, not `463 kcal` (`pr.md` §6.4).
- Deltas carry a sign and a window: `−0.3 kg / week`, `+8%`.

### 6.2 Series palette
In order: `--oh-series-1` (#0E5A6B) → `--oh-series-2` (#4C8795) → `--oh-series-3` (#B45F1E). Three series is the maximum on a phone chart.
- Any mark that must be told apart on its own uses a series color (all ≥3:1 on white) **and** differs in shape or dash — solid line vs dashed goal line, bar vs line.
- `--oh-fill-1` / `--oh-fill-2` are fills only, used where the adjacent label states the value. They never carry meaning alone.
- Raw observations behind a trend line are `--oh-point` dots, r=2.2.

### 6.3 Chart rules
1. Gridlines `--oh-track`, 1px, horizontal only. Axis labels 9px `--oh-ink-3`.
2. A threshold or goal line is dashed `4 4` in `--oh-series-3` and labelled with its value.
3. Every chart carries `role="img"` plus an `aria-label` that states the shape and endpoints in a sentence, and every chart screen offers a **"Read this chart as a table"** link (`pr.md` §6.16 acceptance).
4. A legend appears under the plot, 11px, with an 11×3 swatch per series.
5. Range selectors (30 d / 90 d / 1 y / All) are a 4-up row of 34-high buttons; selected is `--oh-primary` filled.
6. No 3D, no donut with more than one value, no area fill under a line, no dual y-axis without both axes labelled.

### 6.4 Clinical range scale
A four-stop bar, height 10, radius 5, in `#BFD3D7` → `--oh-primary` → `#DDB38A` → `--oh-accent`, with the user's value marked by a 3×16 `--oh-ink` tick labelled "you", and the stops labelled Low / Normal / Raised / High beneath. **The guideline and its region are printed in full above the bar** ("ESC/ESH 2023, adults, home measurement — below 135/85"). A range with no citation does not ship.

---

## 7. Provenance UI

Every stored value is reachable to its origin. Three levels:

1. **Chip** — on the row itself: `Phone sensor`, `Health Connect · imported`, `Open Food Facts · barcode`, `USDA · verified`, `Scale · Bluetooth`, `My recipe · 4 ingredients`, `Quick add · entered by you`, `Cuff`, `Manual`. Style per §5.6 (source chip). Source first, method second, separated by ` · `.
2. **Expanded panel** — tapping the row opens it in place: border becomes 1.5px `--oh-primary`, a divider, then a label "Where this number came from" (11/600 uppercase, `--oh-primary`) over a `dl` grid (96px / 1fr, 12.5px) listing **Source · Also seen · Rule · Happened · Recorded**, then secondary actions ("Change source", "Exclude this day").
3. **Correction** — any panel offers the edit that fixes the number, and the edit is undoable (§5.17).

Flagged provenance states:
- **Entered late** — state chip `Entered late · recorded 21:04`; the whole row card may take `--oh-accent-surface`.
- **Deduplicated** — the losing source is shown struck or as "not used", with the rule in plain words ("Highest-priority source wins the day, never the sum").
- **Estimated** — the word "estimate" appears next to the value, not in a tooltip.

---

## 8. States

| State | Treatment |
|---|---|
| Loading | Skeleton blocks in `--oh-track` at the final layout's shape. **No spinners**, no full-screen loaders. |
| Optimistic write | Value appears immediately; a 12px `--oh-ink-3` "Saving…" line under it only if it outlives 2 s. |
| Offline | Banner per §5.18 explaining what still works ("Showing the 2,400 foods cached on this phone"). Logging never blocks. |
| Local-only / synced | Header chips: a check + "Saved on this device", and "Synced 2 min ago". Plain text, never a status dot alone. |
| Empty | Dashed `--oh-border-dashed` card, a one-line explanation of what would appear, one primary action. Never an illustration. |
| Partial data | Say what is missing and what that costs ("projection widens to ±9 days because there were 4 missing check-ins"). |
| Error | Plain sentence in `--oh-accent-text` on `--oh-accent-surface`, with the retry as a secondary button. No modal for a recoverable error. |
| Destructive done | Undo bar, 8 s (§5.17). Trash keeps items 30 days. |

---

## 9. Motion

- Durations: 120 ms (state change), 200 ms (sheet, navigation), 320 ms (sheet dismiss). Easing `cubic-bezier(0.2, 0, 0, 1)`.
- Animate `transform` and `opacity` only.
- The rest timer and elapsed clock tick without animation.
- When the system animation scale is 0 or "remove animations" is on, all of the above become instant; nothing auto-scrolls, nothing pulses.
- Nothing in the product loops, bounces, confettis or celebrates. A PR gets a chip, not an animation.

---

## 10. Accessibility — non-negotiable

Target: **WCAG 2.2 AA**, full RTL, dynamic type.

- Real elements: `<button>`, `<a href>`, `<input>` + `<label>`, `<table>` with `<th scope>`. **Never** `role`/`onClick` on a `div` or `span`.
- Every icon-only control has an `aria-label` that names the action.
- Touch targets **≥44×44**, including header icon buttons and tab items.
- Text contrast ≥4.5:1 (≥3:1 only at 24px+ or 19px bold); graphics that carry meaning ≥3:1. §2.4 is the reference.
- Color is never the only signal: pair it with a word, an icon, a dash pattern or position.
- Focus is always visible: 2px `--oh-primary` outline, offset 2.
- Charts: `role="img"` + descriptive `aria-label` + a table alternative.
- Layout must survive 200% text and a 320px-wide viewport with no horizontal scroll and no clipped value.
- Announce async results with a polite live region (set saved, sync finished, undo available).

---

## 11. Voice in the UI

- Second person, present tense, lower-case sentence case. "You slept 7 h 10 m and recovered well."
- State the fact, then the consequence, then the option. Never the reverse.
- No guilt, no streak pressure, no exclamation marks, no praise for logging.
- Never diagnose, prescribe or dose. Health copy states what was recorded and, where relevant, the guideline it is compared against (`pr.md` §2.5, §6.9).
- Explanations are one tap away and written out: "Target came from double progression: 4×5 completed at 80 kg last week, so load went up one step."
- Unknown content in a mockup is a bracketed placeholder (`[YOUR PRICE]`), never lorem ipsum, never an invented statistic.

---

## 12. Dark mode — provisional

Not yet mocked. When it is built it is a token swap driven by `isSystemInDarkTheme()`, not a redesign:

| Token | Dark value |
|---|---|
| `--oh-ground` | `#141615` |
| `--oh-surface` | `#1C1F21` |
| `--oh-border` | `#2A2E31` |
| `--oh-border-subtle` | `#232729` |
| `--oh-track` | `#2A2E31` |
| `--oh-ink` | `#ECEAE5` |
| `--oh-ink-2` | `#B3BAC0` |
| `--oh-ink-3` | `#8D959C` |
| `--oh-primary` | `#5FB3C4` |
| `--oh-primary-soft` | `#143037` |
| `--oh-accent` | `#E08A47` |
| `--oh-accent-soft` | `#3A2616` |

Re-measure every pair in §2.4 against the new ground before shipping; these values are a starting point, not a verified palette.

---

## 13. Known deltas between mockups and this spec

Build to the spec, not to the pixel, in these three places:

1. Header icon buttons are drawn 40×40 on several artboards — **build them 44×44**.
2. The Insights volume bars use `--oh-fill-2` (1.6:1) — **build them `--oh-series-2`**.
3. Half-step font sizes (12.5/13.5/14.5) appear throughout — **round to the §3.2 scale**.

---

## 14. Screen inventory

Canvas: https://claude.ai/artifact/PSZyUjMjRjayqkjC2tqBjR
Downloaded HTML copy: [`openHealth App Mockups.html`](<openHealth App Mockups.html>) — open locally, no canvas access required.

| Screen | File on the canvas | Carries |
|---|---|---|
| Onboarding | `Onboarding.dc.html` | Goal radio set, data-location choice, skippable everything |
| Today (home) | `Main.dc.html` | Readiness ring, plan hero, nutrition, steps, weight, due items, quick add |
| Quick add | `QuickAdd.dc.html` | Sheet, search, 6-tile action grid, "again" repeats, backdate |
| Timeline | `Timeline.dc.html` | Day groups, filters, expanded provenance panel, undo bar |
| Train hub | `Train.dc.html` | Segmented kinds, next-up, routines, week bars, history |
| Live session | `Session.dc.html` | Set table, rest timer, plate math, progression explanation |
| Food diary | `Food.dc.html` | Day stepper, targets, meals, water, per-item source |
| Food search | `FoodSearch.dc.html` | Query + barcode, offline banner, serving editor |
| Body | `Body.dc.html` | Trend vs goal chart, goal projection, composition, photos |
| Vitals (BP) | `Vitals.dc.html` | Home average, cited range scale, context chips, entry |
| Insights | `Insights.dc.html` | Weekly review, load vs recovery, what moved, correlation caveat, report |
| Coach | `Coach.dc.html` | Diffable proposal, rationale links, "what left this phone" |
| More | `Settings.dc.html` | Modules, storage, connections, privacy, licence |

**Not yet designed:** yoga and mobility, GPS activities and maps, sleep detail, cycle tracking, family and caregiver, labs and medication detail, Wear OS screens, tablet and foldable layouts, dark mode, destructive-confirmation dialogs. (iOS and web layouts are not deferred work — they are out of scope until `pr.md` §13.17 opens.) Do not improvise these — extend this file first.
