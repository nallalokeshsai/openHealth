# openHealth — Design System

**Version:** 0.7
**Status:** Living document, first pass
**Last revision:** 2026-11-27 — B14 adds the Wear OS companion recipe (§14.6) and the sixth Settings → Devices group (§14.2): one screen,
the same tokens, one accent on Log set, 44 dp targets and the existing type roles. No new
colour, type role, spacing value or component. Previous: 2026-11-26 — B13 adds Settings →
Data → Sync (§14.2): local-only,
synced, offline and conflict states from existing components and inks, with the sync
problem in orange (§6.5) and no new colour, type role, spacing value or component.
Previous: 2026-11-26 — B12 adds the clinical report paper (§8.9): the report is
always light, its type roles and tables are the existing ones, and the sleep, clinical
hub, editor, reports and weekly-review pages reuse existing components and recipes. No
new colour, type role, spacing value or component. Previous: 2026-11-26 — B11 adds the route map as a chart surface (§8.8): the
theme's existing background, surface and accent ramp, a 2.5 dp route and 4 dp end dots,
no new colour, type role, spacing value or component. Previous: 2026-11-26 — B10 maps the three accessibility options named in §12.4
onto existing tokens (§12.4.1): high contrast, reduce transparency and reduce motion add
no colour, type role, spacing value or component. Previous: 2026-11-25 — B9 adds the KPI
band's 12 sp range line to §5.6 and the Today and Stats recipes to §14.2; no new colour,
type role, spacing value or component.
Previous: 2026-11-25 — B8 adds the Devices recipe to §14.2 (a pushed page under
Settings: grouped lists, one accent for the winning source, no new colour, type role,
spacing value or component). Previous: 2026-11-25 — B7 records the measurement anchor's 30 % glow, the
line-chart chrome and the photo-slider geometry in Appendix A; no new colour, type role,
spacing value or component. Previous: 2026-11-24 — B4 gives the body map its missing engraving rules
and the dynamic model its arithmetic, no new colour or type role: the Recovering hatch
(§9.1); the adipose band's thickness rule, region factors, self-relative tape scaling and
a closing ring that keeps the band out of the seams between muscles, with a full-opacity
light layer because 55 % measured 1.97:1 (§9.1.1, §12.5); and Today's `Where it may
appear` now says the hero pair rather than the mini map (§9.1). Appendix A records the
map's geometry (pair gap, strokes, mini and hatch sizes). Previous: 2026-11-23 (third edit) — the object set gains a water droplet
(§10.2), the only glyph quick add needed and the core set did not name; it is a
geometric icon on the same 24 dp grid, no colour or size token changed.
Previous: 2026-11-23 (second edit) — `fat` re-valued from the anatomical
reference to `#F2C230` in dark and its brightest 3:1 sibling `#A87C00` in light (§2.5,
§12.5, Appendix A).
Previous: 2026-11-23 — B1 corrections, no new token: the chip's text is the
`subhead` role (§5.9, the stated 14 sp is not one of the 11 roles in §3.2); `acc-2` is
resolved for the eight non-brand presets (§2.6); Appendix A's row padding matches §5.3
(12/16, not 11/14); the `headline` of §4.4 is the §10.5 wordmark (`body` 17/600); the
Danger and Tinted button labels use `label` ink because their tints measured below
4.5:1 with `red-ink`/`acc-ink` (§5.7, §12.5); toast actions are 44 dp targets (§5.18,
§4.5); and the timer bar's clock is 28/600 while the full-screen clock is 700 (§3.3,
§5.19).
Previous: 2026-11-22 — five-tab bar with a raised centre Train (§5.15,
§13.1), global top bar (§4.4), `fat` and `recovery` data hues (§2.5, App. A),
recovery ramp green → orange → red (§9.1), dynamic body model (§9.1.1).
Previous: 2026-11-21 — establishes the visual system from the openGym
1.4.5 design language, extended for openHealth's eight modules.
**Appearance authority for:** every colour, size, component, state, chart,
illustration and accessibility rule in the Android client.
**Behaviour authority:** [`pr.md`](pr.md) wins on what a screen does. This file
wins on what it looks like. [`Tech-Stack.md`](Tech-Stack.md) wins on how it is
built.

**Contents:** [0. How to use](#0-how-to-use-this-document) ·
[1. Principles](#1-design-principles) ·
[2. Colour](#2-colour) ·
[3. Typography](#3-typography) ·
[4. Space and layout](#4-space-shape-and-layout) ·
[5. Components](#5-components) ·
[6. States and motion](#6-states-and-motion) ·
[7. Provenance](#7-provenance-and-trust-in-the-interface) ·
[8. Charts](#8-data-visuals-and-charts) ·
[9. Body and media](#9-the-body-photography-and-media) ·
[10. Icons](#10-icons-and-illustration) ·
[11. Accessibility](#11-accessibility-and-internationalisation) ·
[12. Themes](#12-themes-and-the-full-palette) ·
[13. Departures](#13-deliberate-departures-from-opengym) ·
[14. Screens](#14-screens-and-the-review-gate) ·
[A. Tokens](#appendix-a--token-reference) ·
[B. Compose](#appendix-b--compose-mapping-notes) ·
[C. Provenance](#appendix-c--where-the-pieces-come-from)

---

## 0. How to use this document

### 0.1 The source

The visual language is taken from **openGym 1.4.5** (`references/openGym/`):
its neutral surface ramp, single-accent economy, hairlines, weight-led
typography, iOS-style grouped lists, bottom-sheet grammar, the anatomical body
map and the 140–220 ms press-and-page motion. That app is beautiful because it
is disciplined, not because it is decorated. We take the discipline.

This file **supersedes the previous `DESIGN.md` v1.0** (light ground, teal
primary, clay accent, bordered cards) and the mockup canvas it pointed at, both
of which were removed from the working tree in the 2026-11-21 reset. Their
functional rules on provenance, states, voice and the screen inventory are
carried forward here in the new visual language; their palette, typefaces,
borders and elevation model are not.

What we take, in one sentence: **black and near-neutral greys, white type at
four strengths, one lime accent, sub-pixel separators, very few borders, and a
human body drawn in five shades of that accent.**

openGym is a training log. openHealth is a health record. The vocabulary
travels; the composition does not. Section 13 lists every deliberate departure,
and section 14 maps the vocabulary onto openHealth's screens.

Reference frames, worth opening before the first screen:
`references/openGym/assets/screenshots/home.png` (the card stack, week strip and
mini body map), `workout.png` (the set table and docked timer), `stats.png`
(the KPI band, stat bars and segmented control) and `library.png` (media rows,
chips, search).

### 0.2 The closed set

This is a **closed token set**. Use its hexes, its 11-step type scale, its 9
spacing values, its 6 radii and its 19 components. Do not invent a sibling
shade, a 12th type step or a new card variant. If a screen seems to need one,
the screen is wrong or this document is; fix it here first, in a dated edit.

### 0.3 Units

All sizes are **dp** (layout) and **sp** (type), matching Compose 1:1. The
numbers in this file are the same numbers a Compose implementation writes. Where
a value is a hairline it is `0.5.dp`, drawn as a sub-pixel line.

### 0.4 Priority tags

- **MVP** — required for the first usable release (`pr.md` §0, §9 Phase 1).
- **V1** — the first "genuinely great" release.
- **Later** — after the core is trusted.

### 0.5 What this file is not

It is not a marketing brand book, not a component library manual and not a
promise that every screen exists. It is the contract between the person using
the app and the pixels they trust with their health data.

---

## 1. Design principles

### 1.1 One accent, spent like money

Exactly one accent colour is live at a time: the profile's accent, lime by
default. It marks **the next action** (Start, Add set, Save), **the chosen
state** (selected chip, active tab, today) and **the one number that matters**
(a trend, a PR). Everywhere else is grey.

A screen with five accent elements has no accent. The budget for a card is **one
primary filled accent element** (a button, the selected day, a ring), plus small
accent data marks (a mini body map, a dot, a 1 dp line) and up to two
accent-coloured words or icons.

### 1.2 Greys carry the interface

Surfaces are neutral greys a few points apart, never saturated blue-greys. The
card is not "dark blue"; it is `#1C1C1E` on `#000000`. Colour is information.

### 1.3 Hierarchy comes from weight and size, not colour

600 for a title, 400 for its content, 700 for a page title, and size does the
rest. Nothing is bold "for emphasis". If everything shouts, position is the only
cue left.

Where a card exists to show **one number**, that number is the largest thing on
the card and the chrome shrinks around it. A label is never larger than the
value it labels.

### 1.4 Hairlines, not boxes

Separators are `0.5 dp`, inset past a row's icon rail, and exist **only between
rows** — never above the first, never around a group. A list reads as one
object, not a stack of outlined boxes.

### 1.5 The body is the hero visual

Every module has a body in it somewhere: the muscle map in Train, the silhouette
in Body, the recovery shading on Today, the posture of a pose in Yoga. It is the
one illustration in the product that is data, not decoration. It is drawn from
real anatomy geometry and shaded with the same five-step ramp as the activity
heatmap, so "more accent = more of the thing" means one thing everywhere.

### 1.6 Every number can be interrogated

A value on screen is a door, not a fact. One tap shows where it came from, when
it happened, when it was recorded, on what device, by what method, and whether
anyone edited it (`pr.md` §2.2). A number without provenance is a decoration;
we do not ship decorations.

### 1.7 Sheets, not dead ends

Detail opens over the page in a dismissible sheet. Back closes the sheet, the
page underneath keeps its scroll position, and no tap ever lands on a blank
screen with only a back arrow.

### 1.8 Motion acknowledges; it does not perform

Presses answer on touch-down: 3 % on cards, buttons and rows that scale; 8 % on
small round controls. Pages slide in 380 ms. Sheets rise 340 ms. Nothing
bounces, nothing loops for decoration, nothing animates a number just to look
alive. Reduced motion removes all of it without removing information.

### 1.9 Calm by default; celebrate only what was earned

No red until something is destructive or the person asked to be alerted. No
streak guilt, no "you missed a day" banner, no confetti for opening the app. A
personal record, a finished programme and a saved backup may celebrate once,
briefly, and then stop.

### 1.10 Dark first, light equal

Dark is the canonical theme: it is what the product looks like, and every token
is authored there. Light is a complete, contrast-verified sibling, not an
afterthought and not an inversion. Both ship at 1.0; the system follows the
device unless the person overrides it.

### 1.11 The thumb owns the bottom

Primary actions live in the bottom third: the tab bar, the raised Train disc, a
sheet's main button. Destructive actions never sit under the thumb's resting
position.

### 1.12 Accessibility is the style

4.5:1 text, 3:1 controls, 44 dp targets, dynamic type, screen-reader names on
charts and the body map, colour-blind-safe status pairs, full RTL. These are not
a later pass; they are why the interface looks the way it does (`pr.md` §2.7,
§6.22).

---

## 2. Colour

### 2.1 The rule

Colour is **status or action**, never decoration. There are exactly four jobs
for colour in this product:

1. the accent says "this is the next thing";
2. the status inks say "done / attention / stop / record";
3. the surface ramp says "this is a card, this is pressed, this is floating";
4. domain hues inside a single chart (macros, sleep stages, cycle) where a
   legend names them.

Anything else is grey.

### 2.2 The surface ramp

| Token | Dark (canonical) | Light | Use |
|---|---|---|---|
| `bg` | `#000000` | `#F2F2F7` | The page. Pure black, so OLED panels disappear at night. |
| `bg-el` | `#0E0E10` | `#F7F7FA` | Elevated backdrop: sheets, tab bar, pinned headers. |
| `surface` | `#1C1C1E` | `#FFFFFF` | Cards, grouped-list fill, list rows. |
| `surface-2` | `#2C2C2E` | `#ECECEF` | Pressed fill, nested row, secondary button. |
| `surface-3` | `#3A3A3C` | `#E3E3E8` | Control tracks: segments, switches off, progress track. |
| `surface-raised` | `#2C2C2E` | `#FFFFFF` | Things that float: dialog, timer, toast, chart tooltip. |
| `raised-line` | `rgba(255,255,255,.09)` | `rgba(60,60,67,.10)` | The hairline that edges a raised surface in dark. |

A raised surface in dark is **one step lighter, edged with a hairline of light**,
with a soft shadow that supports it rather than separating it; on black a shadow
alone does no work. Light mode uses white plus a shadow.

**Never** introduce a sixth surface, a gradient card, or a translucent card over
a photo. `surface` values are opaque.

### 2.3 Text

| Token | Dark | Light | Use |
|---|---|---|---|
| `label` | `#FFFFFF` | `#000000` | Titles, values, body. |
| `label-2` | `rgba(235,235,245,.60)` | `rgba(60,60,67,.75)` | Secondary lines, subtitles, units in words. Reads ≥4.5:1 on page and card. |
| `label-3` | `rgba(235,235,245,.32)` | `rgba(60,60,67,.30)` | Placeholders, disabled glyphs, hairline icons. **Never body text.** |
| `label-4` | `rgba(235,235,245,.18)` | `rgba(60,60,67,.16)` | Grabber, tracks, decorative rules. |
| `label-dim` | `rgba(235,235,245,.55)` | `var(--label-2)` | Quiet text that is still read: footnotes, "average", dates. |

The light theme's `label-2` is deliberately `.75`, not the platform's `.60`: at
`.60` secondary text was 3.2–3.5:1 on white, below AA. `label-dim` is a
separate token because 200 lines of real text had been using the tertiary grey.

### 2.4 Separators

| Token | Dark | Light |
|---|---|---|
| `sep` | `rgba(84,84,88,.60)` | `rgba(60,60,67,.29)` |
| `sep-op` | `rgba(84,84,88,.34)` | `rgba(60,60,67,.20)` |
| `hair` | `0.5 dp` | `0.5 dp` |

`sep-op` is for edges sitting over a blurred bar. No 1 dp borders, no card
outlines, no dividers above the first row of a group.

### 2.5 The system palette

Dark values are the iOS dark system colours; light values the iOS light set. We
borrow the values because they are calibrated, not because we want an iOS clone.

| Name | Dark | Light | Meaning in openHealth |
|---|---|---|---|
| `blue` | `#0A84FF` | `#007AFF` | Data, links inside charts, carbs in the macro set. |
| `green` | `#30D158` | `#34C759` | Done, within range, success. |
| `red` | `#FF453A` | `#FF3B30` | Destructive, error, user-set clinical alert, and the "Recovering" step of the muscle recovery map (§9.1). **Nothing else.** |
| `orange` | `#FF9F0A` | `#FF9500` | Attention: over target, missed, offline, in-progress. |
| `yellow` | `#FFD60A` | `#FFCC00` | Records, PRs, favourites, goal lines. |
| `fat` | `#F2C230` | `#A87C00` | **Added 2026-11-22, re-valued 2026-11-23** from the anatomical reference (the fat in the figure is this golden yellow). Adipose layer of the dynamic body model (§9.1.1) and body-fat series. Data only, never status, never an accent. Measured: 12.5:1 on dark `bg`, 10.2:1 on dark `surface`, 8.3:1 on dark `surface-2`; light 3.9:1 on `bg`, 4.4:1 on `surface`, 3.7:1 on `surface-2` (§12.5). The light theme takes the same hue at the brightest value that clears 3:1; a light value of `#F2C230` is 1.5:1 and cannot carry the layer there. |
| `teal` | `#40C8E0` | `#30B0C7` | Location, gym, fibre in the macro set. |
| `indigo` | `#5E5CE6` | `#5856D6` | Life events and notes drawn as bands on charts. |
| `pink` | `#FF375F` | `#FF2D55` | Mood and cycle accents; never status. |
| `purple` | `#BF5AF2` | `#AF52DE` | Fat in the macro set; sleep "deep" in the stage ramp. |
| `mint` | `#63E6E2` | `#00C7BE` | Reserved. |
| `brown` | `#AC8E68` | `#A2845E` | Reserved (body composition method tags). |
| `grey` | `#8E8E93` | `#8E8E93` | Neutral data series, disabled. |

### 2.6 The accent

Default: the logo lime.

| Accent token | Dark | Light |
|---|---|---|
| `acc` | `#A3E635` | `#84CC16` |
| `acc-2` (pressed) | `#84CC16` | `#65A30D` |
| `on-acc` | `#0C0E12` | `#0C0E12` |
| `acc-ink` (accent as text/icon) | `#A3E635` | `#4A7A0E` |
| `acc-soft` | `acc` at 16 % | `acc` at 16 % |
| `acc-line` | `acc` at 38 % | `acc` at 38 % |

`on-acc` is **dark** on every preset. White fails 4.5:1 on all nine fills
(4.0:1 at best, on blue, and 1.5:1 on lime), and a 17 sp/600 button label is not
"large text" under WCAG, so the familiar white-on-blue button is not available
to us. Measured black ink is 5.1–14.9:1 on these fills. Every filled accent
surface therefore carries `#000000` or the brand's near-black `#0C0E12`.

A switch knob on a very light accent gets the `rgba(0,0,0,.28)` hairline ring so
it does not vanish. `acc-2` is the pressed **graphic** shade for an accent fill
that carries no text (a bar, a ring segment). A filled button does not use it:
darkening the fill pushes dark ink below 4.5:1 on six of the nine presets (blue
falls to 3.7:1). Buttons press by scale and opacity instead (§5.7).

**For the eight presets other than `brand` (2026-11-23).** Appendix A fixes `acc-2`
for `brand` only, so a preset's `acc-2` is not a new hex: it is the preset's own
"Accent dark" value in dark and its "Light ink" value in light. Both already clear
3:1 on `surface`; `brand` keeps `#84CC16` / `#65A30D` exactly as Appendix A states.
Nothing presses with it (§5.7).

Presets (Settings → Appearance). Nine, no more:

| Key | Accent dark | Light fill | Light ink | On-accent |
|---|---|---|---|---|
| `brand` | `#A3E635` | `#84CC16` | `#4A7A0E` | `#0C0E12` |
| `lime` | `#30D158` | `#34C759` | `#1A7F37` | `#000000` |
| `sky` | `#0A84FF` | `#007AFF` | `#0062CC` | `#000000` |
| `orange` | `#FF9F0A` | `#FF9500` | `#A85200` | `#000000` |
| `violet` | `#BF5AF2` | `#AF52DE` | `#8944AB` | `#000000` |
| `pink` | `#FF375F` | `#FF2D55` | `#D70036` | `#000000` |
| `red` | `#FF453A` | `#FF3B30` | `#D70015` | `#000000` |
| `teal` | `#40C8E0` | `#30B0C7` | `#0071A4` | `#000000` |
| `gold` | `#FFD60A` | `#FFCC00` | `#8F5E00` | `#000000` |

**Custom accents.** A person may pick any `#rrggbb`. It is never trusted
blindly and never drawn raw. Per theme: keep it if it clears 3:1 against both
`bg` and `surface`; otherwise move lightness in 2 % steps (yellow also drifts
towards orange on the way down, so it becomes gold, not olive) until it does.
Text in the accent must clear 4.5:1, so `acc-ink` is a second, darker/lighter
shade. Greys are held to 4.5:1 and 2:1 away from `label-2`, so a grey accent
never looks like a disabled button.

**One accent per account, synced.** It is a Look setting like theme and text
size (`pr.md` §6.21 V1). It never changes meaning: it is not "protein colour" or
"training colour".

### 2.7 Status inks: the colon of the interface

System colours are fills. When the same hue is a **word or a thin line**, it
uses an ink that clears 4.5:1 on both page and card. Glyphs that are themselves
the mark (the flame, a snowflake, a medal) and figures at 22 sp/700 or larger
may use the 3:1 graphic shade, labelled below.

| Token | Dark | Light | Use |
|---|---|---|---|
| `green-ink` | `#30D158` | `#1A7F37` | Within range, done, upward-good trend. |
| `orange-ink` | `#FF9F0A` | `#A85200` | Attention, over target, offline. |
| `gold-ink` | `#FFD60A` | `#8F5E00` | PR, goal line, favourite, warning that is not an error. |
| `red-ink` | `#FF453A` | `#D70015` | Destructive words, form errors, a user-set alert. |
| `blue-ink` | `#0A84FF` | `#0058C4` | Data/link words in charts and source rows. |
| `teal-ink` | `#40C8E0` | `#0E7490` | Gym/location, fibre. |
| `streak-ink` | `#FF9F0A` | `#E06A00` | **Graphic only:** the flame, level numbers at `title2`/700. Never body text. |
| `win-ink` | `#30D158` | `#1A7F37` | A completed challenge. Text-safe. |
| `frost` | `#64D2FF` | `#0A7FC4` | **Graphic only:** freezes, sleep "REM" marks. Never body text. |

Fills keep `green`, `orange` and `yellow`. A filled badge is not text.

### 2.8 Status meaning, fixed

| State | Colour | Never |
|---|---|---|
| Done / on target | green ink | also using green for "new" or "pro" |
| Over target / attention | orange ink | red, unless it is an error |
| Destructive / error | red ink | colouring a high-but-normal vital red |
| Record / best | gold ink | gold for ordinary success |
| Note / life event | indigo | as a status |
| Offline / unsynced | orange + the connection banner | a modal |
| Disabled | `label-3` | a colour |

A number that is merely **outside a published reference range** is orange-ink at
most, with the range and its citation beside it. Red is reserved for something
the person explicitly configured as an alert (`pr.md` §12.1).

### 2.9 Contrast rules

- Body text ≥ 4.5:1 on its actual background.
- Large text (≥ 18.66 sp bold or 24 sp regular) and control glyphs ≥ 3:1.
- Text inks (`green`, `orange`, `gold`, `red`, `teal`, `blue`) reach 4.5:1.
  The graphic inks (`streak`, `frost`) reach 3:1 and never carry body text.
- Never put text on `surface-3` without checking it.
- Never rely on hue alone: every status carries an icon or a word.
- The high-contrast option (§12.4.1) firms `label-2`, `label-dim` and `sep-op`.
  It is an app preference, not a branch on an Android setting.
- Colour-blind simulation is part of the review gate (§14.5).

### 2.10 Forbidden

- Pure `#FFFFFF` text at 100 % for long body copy in dark; it glares. Use
  `label` for titles and short values, `label-2` for prose.
- Saturated brand gradients, glassmorphism cards, neon glows outside the single
  accent disc's shadow.
- More than one accent hue on a screen (the macro chart is the documented
  exception).
- Green for "new", purple for "AI", red for "high".
- Any colour that is not in this section.

---

## 3. Typography

### 3.1 Family

The **platform UI font**: Roboto on Android, with `FontFamily.Default`. We ship
no typeface at 1.0. The system font is the brand, it handles every script, it
respects the person's text size, and it costs nothing.

If a future brand pass requires cross-platform sameness, Inter (OFL) is the only
approved alternative, and shipping it is a decision recorded here first, not a
default.

**Numerals are always tabular** (`FontFeatureSettings("tnum")`) wherever figures
can change or align: timers, weights, macros, charts, tables. Proportional
figures are allowed only inside prose.

### 3.2 The 11-step scale

| Token | Size | Line height | Tracking | Use |
|---|---|---|---|---|
| `caption2` | 11 sp | 1.33 | 0 | Tab labels, heatmap months, badge counts. |
| `caption` | 12 sp | 1.33 | 0 | Meta, axis labels, provenance chips, units. |
| `footnote` | 13 sp | 1.38 | -0.004 em | Subtitles, row secondary lines, section headers. |
| `subhead` | 15 sp | 1.33 | -0.01 em | Values, secondary lines, field labels. |
| `callout` | 16 sp | 1.29 | -0.01 em | Dense list titles, inputs, body in sheets. |
| `body` | 17 sp | 1.29 | -0.01 em | Default body and row titles. |
| `title3` | 20 sp | 1.2 | -0.021 em | Card titles, exercise name, sheet title. |
| `title2` | 22 sp | 1.2 | -0.021 em | A lead line, a dialog figure. |
| `large` | 34 sp | 1.06 | -0.028 em | Page title. Drops to 30 sp below 420 dp. |
| `display` | 28 sp | 1.1 | -0.026 em | The one big number in a card (weight, kcal left). |
| `timer` | 28–64 sp | 1.1 | -0.026 em | Rest/work clock and full-screen session timer. Fixed box, so it may exceed the scale. |

Note the two extras: `display` and `timer` are **display roles inside fixed
boxes**, not text sizes for prose. The text scale proper is the nine roles from
`caption2` to `large`; both display roles use the same tracking and weight rules.

Rules:

- A role's size, leading and tracking move as a set. Never mix one role's size
  with another's leading.
- Tracking tightens as type grows, loosens a hair as it shrinks.
- Body copy never goes below `footnote` (13 sp). Captions are for labels, not
  sentences.

### 3.3 Weight

| Weight | Use |
|---|---|
| 400 | Body, subtitles, values in rows, most everything. |
| 500 | Chips, small emphasis, a chosen filter. |
| 600 | Titles, card headers, buttons, the display number. |
| 700 | Page title, the full-screen session timer, the single strongest figure on a celebration card. |

No 800/900. No italic except the one line of a "why this number" explanation
and the caption of a shared image. No uppercase labels; sentence case always
(tracked capitals read as shouting and break translation).

### 3.4 Figures

- Use `−` (U+2212) for negative numbers, not a hyphen.
- A unit sits at `subhead` or `caption` in `label-2`, with a 2 dp gap:
  **82.4** `kg`, not **82.4kg**.
- One decimal for weight (kg/lb), body fat and circumferences; zero decimals for
  calories, steps, reps and sets.
- Never fake precision: 1 234 kcal, not 1 234.0 kcal.
- Very large values abbreviate at 4 digits in tiles (12.4k) but show in full in
  the detail sheet.
- Ranges use an en dash: `60–70 min`.
- A dash `—` means "no data", and always with a screen-reader label saying so.

### 3.5 Wrapping

- Titles wrap, values ellipsise. A row's title keeps at least its longest word;
  the value shrinks first.
- Use balanced wrapping (`text-wrap: balance` / `Balanced` in Compose) for short
  titles and buttons, so no two-word button leaves an orphan.
- Never break a word inside a name unless no line can hold it (`overflow-wrap:
  break-word`, then `anywhere`).
- Numbers never wrap away from their unit.

---

## 4. Space, shape and layout

### 4.1 Spacing (9 values)

| Token | dp | Use |
|---|---|---|
| `space-0` | 2 | Icon-to-text optical nudge, chart tick gap. |
| `space-1` | 4 | Inside a chip, badge padding, tight stacks. |
| `space-2` | 6 | Chip gaps, hairline insets, row internal gaps. |
| `space-3` | 8 | Grid gaps, icon-to-label, small stacks. |
| `space-4` | 10 | Tile grid gap, segmented inner gap. |
| `space-5` | 12 | Card inner stack, row gap, control padding. |
| `space-6` | 16 | Screen padding, card padding, sheet side padding. |
| `space-7` | 22 | Between sections, sheet title to content. |
| `space-8` | 32 | Section break on a sparse screen, empty-state breathing. |

Anything larger is a layout decision (content max width, tab-bar reserve), not a
spacing token. The 44 dp touch target is a **size**, not `space`.

### 4.2 Shape (6 radii + full)

| Token | dp | Use |
|---|---|---|
| `r-sm` | 8 | Tags, small icon tiles, small buttons, checkbox. |
| `r-md` | 10 | Inputs, segments, keyboard-adjacent controls. |
| `r` | 12 | Buttons, steppers, small cards. |
| `r-card` | 14 | Cards, list rows, grouped-list container. |
| `r-lg` | 16 | Media tiles, dialogs, photo cards. |
| `r-xl` | 22 | Sheet top corners, toast. |
| `r-full` | 999 | Chips, avatars, switches, the raised Train disc, pills. |

### 4.3 Density, insets and platform mapping

This is the section that makes the Compose build match the specification.

- **Content column:** `maxWidth = 560 dp`, centred. On a phone it is the screen
  minus `space-6` (16 dp) each side.
- **Screen padding:** top = status-bar inset + 8 dp; sides 16 dp; bottom =
  88 dp + navigation-bar inset (the tab bar's reserve).
- **Tab bar:** 56 dp content height + navigation inset; 6 dp top padding, 6 dp
  bottom padding. It is `bg-el` at 94 % opacity with a 10 dp blur and a
  `0.5 dp` top hairline. With Reduce Transparency it is opaque `bg-el`.
- **Large titles** sit in the page, not in an app bar: 34 sp/700, 4 dp above a
  15 sp `label-2` subtitle, 16 dp below the block. The title never scrolls under
  a translucent bar; pinned headers are opaque with a hairline.
- **Status bar and cutout:** draw edge-to-edge, never paint a black bar of our
  own. Use `WindowInsets.statusBars` and `displayCutout`; take the larger. The
  content starts below whatever the connection banner occupies.
- **Navigation bar / gesture inset:** every fixed bottom surface adds it; the
  sheet adds it once, not on top of the keyboard.
- **Keyboard:** a sheet stands **on** the keyboard, not behind it. When the
  keyboard is up, the sheet's bottom equals the keyboard height and its cap is
  the remaining visual viewport. Fields are never scrolled out of reach.
- **Predictive back:** back closes the topmost sheet/dialog first, then the
  detail page, then the tab's root. Root tabs are peers and never unwind into
  each other. The back preview shows the underlying page, not a white card.
- **Font scale:** the layout must survive 200 % text. Titles wrap; values stack
  under their label; the tab bar labels may drop to icons at the largest sizes,
  but hit targets never shrink.
- **Hairline:** `0.5.dp` via `Modifier.drawBehind`, not a 1 dp `Divider`.
- **Haptics:** light tick on selection, a firmer confirmation on a logged set,
  a double tick on a PR. Haptics respect the system setting.
- **Desktop/tablet (Later):** at ≥ 600 dp the content column stays 560 dp and
  lists may become two columns, except any list that reorders or represents a
  sequence (routines, sets, timeline), which stays one column at every width.

### 4.4 The screen skeleton

Every root screen is composed from the same five parts, in this order:

0. **Top bar (global, added 2026-11-22)** — pinned on every root: app name
   (`body` 17/600, the §10.5 wordmark) and the current date (`caption`, `label-2`) at the leading end;
   quick add (`+`) and settings (gear) as two 36 dp icon buttons at the trailing
   end. Settings opens the former More surface (profile, modules, devices, data,
   appearance, privacy). Hidden in a live session. `bg-el`, same blur and
   hairline as the tab bar.
1. **Header** — title, subtitle, at most one trailing 36 dp icon button.
2. **Status strip** — the connection banner (only when unsynced), never a modal.
3. **Primary card(s)** — the thing the screen exists for, in the largest type.
4. **Grouped sections** — headed inset lists with 22 dp between sections.
5. **Tab bar** — always, except in a live session, which is a focused surface.

A screen may skip parts; it may not reorder them.

### 4.5 Touch targets

| Control | Painted | Minimum target |
|---|---|---|
| Row | full width × ≥46 dp | row itself |
| Icon button | 36 dp circle | 44 dp via 4 dp hit slop |
| Chip | 32 dp tall | 44 dp vertical slop inside a strip |
| Switch | 51 × 31 dp | 44 dp via slop |
| Tab | 1/5 width × 56 dp (Train: 56 dp raised disc, centre) | full cell |
| Set's done circle | 38 dp | 44 dp via slop |
| Chart point | 4.5 dp dot | 44 dp scrub area |

Invisible slop is drawn outside the painted box and never overlaps a
neighbour's target. The one exception is a row's trailing chevron, which is the
row's own target.

### 4.6 The one-hand zone

- Bottom 40 % of the screen: tab bar, quick-add, a sheet's primary button, a
  session's Next/Skip.
- Top 60 %: reading. Nothing destructive or frequently tapped lives there.
- A sheet's Cancel/Done pair sits under the main button, not at the top.

### 4.7 Backgrounds

There is no wallpaper, pattern, texture or photograph behind a screen. The page
is `bg`. A photo is content inside a card, never a backdrop. The only translucent
surfaces are the tab bar, a pinned header and the toast, and all three go opaque
under Reduce Transparency.

---

## 5. Components

Nineteen. Each has one anatomy, one set of states and no optional decoration.
Sizes below are Compose values.

### 5.1 Card

- Fill `surface`, radius `r-card` (14 dp), padding 16 dp, 12 dp below.
- No border, no shadow in dark. A card is separated by space, not an outline.
- A card that opens something scales to `.985` on press; it does not fill.
- A card may hold at most one `.primary` action.
- Cards never nest. A nested group uses `surface-2` or an inset list.

### 5.2 Card header

The one header used everywhere (Today, Stats, Vitals):

```
[ 29 dp glyph ]  EYEBROW (13, label-2)          [one trailing control]
                 Title (17/600, label, wraps)
                 Subtitle (13, label-2)
```

- Glyph is a single line icon in the accent or a status ink, 29 dp optical box,
  no tile unless the card is a list of rows.
- At most one trailing control (chevron, ×, or a small menu). It owns its corner
  with a 44 dp target and never sits inside the title's press area.
- The card is **not** a button when it contains another control: the title is
  the button, and its target covers the card. No nested buttons, no
  `stopPropagation` hacks.

### 5.3 Inset group list and row

The settings/list grammar, adapted from iOS:

- Container `surface`, radius 14 dp, clips children, no padding.
- Row: 46 dp minimum, 12 dp vertical / 16 dp horizontal padding, 12 dp gap.
- Leading icon tile: 29 × 29 dp, radius 8 dp, fill = accent or a module tint,
  glyph in `on-acc`.
- Title 17/400, subtitle 13 `label-2`, value 17 `label-2` aligned to the end,
  chevron 15 dp `label`.
- Hairline between rows only, starting at 16 dp (or 57 dp after an icon rail),
  `sep` at `0.5 dp`.
- Pressed: fill `surface-2`, no scale.
- A destructive row is `red-ink` for title and icon; it is separated from the
  rest by 22 dp and says exactly what it deletes.
- A value that is words moves under the title at ≤ 430 dp rather than crushing
  it.

The only red outside a destructive action or a user-set alert is the training
fatigue map's top band. It is training load, not a clinical reading, and it
never appears on Today, on a vital, or in a report.

### 5.4 Media row

For exercises, foods, routines, activities, articles:

- `surface`, radius 14 dp, 12 dp padding, 60 dp minimum.
- 50 × 50 dp thumbnail, radius 8 dp, white matte for transparent or white-background
  media.
- Title 16/400, subtitle 13 `label-2` (`Pectorals · Barbell`, `per 100 g ·
  Open Food Facts`).
- Trailing: one 32 dp quiet icon button (add, log, more) or a chevron. Never
  both a chevron and two buttons.
- In a grid variant, the thumbnail becomes a square tile, the title sits under
  it at 15 sp, and the grid is `minmax(150 dp, 1fr)`.

### 5.5 Stat tile

- `surface`, radius 14 dp, padding 12 dp, two per row, 10 dp gap.
- Label 13 `label-2`, value 28/600 `display`, optional delta line 13 in a
  status ink.
- No icon larger than 14 dp, no sparkline inside a tile unless it is the tile's
  whole point.

### 5.6 KPI band

For a row of headline figures (Stats overview, session summary): one
`surface` card divided by vertical hairlines, three figures across, 12/16 dp
padding. Figures are **type, not tiles**: 28/600 with a 13 `label-2` label. The
lifetime total may sit underneath at full width. No boxes inside the card.

An uncertain figure keeps its central value at 28/600 and puts its range or
qualifier on a 12 sp `label-2` detail line under the label ("1,930–2,180"), so a
range never clips a third of the band (2026-11-25, B9). The centre is the
estimate; the line is the interval.

### 5.7 Button

| Variant | Fill | Text | Use |
|---|---|---|---|
| Primary | `acc` | `on-acc` 17/600 | The one next action. |
| Secondary | `surface-2` | `label` | Cancel, alternative. |
| Tinted | `acc-soft` | `label` | A secondary action that belongs to the accent. |
| Danger | `red` at 15 % | `label` | Delete, discard. |
| Ghost / plain | none | `acc-ink` | "Choose another", "See all". |
| Quiet | none | `label-2` | Cancel under a primary; never accent. |

- **2026-11-23:** the Tinted and Danger fills keep their stated tints, but their
  labels use `label` ink. Measured composited over `bg` and `surface`, `acc-ink` on
  `acc-soft` is 4.19:1 for the default accent and `red-ink` on the 15 % Danger fill is
  4.23:1 (dark) / 4.40:1 (light), all below the 4.5 of §11.1; `label` passes on both
  and the fill carries the accent or the danger.
- Height 50 dp, padding 12/16, radius 12 dp, full width unless `.sm`
  (35 dp, 8/12, radius 8) or `.xs` (28 dp).
- Pressed: scale `.97` and 90 % opacity, on the same fill. The fill never
  darkens behind text, because that would break the measured 4.5:1 in §2.6.
- Disabled: 32 % opacity, no press.
- A button's label is a verb + object ("Log weight", "Start session"), never
  "OK". "OK" is banned.
- One primary per sheet. Two primaries side by side is a layout error.

### 5.8 Icon button

- 36 dp circle, `surface`, glyph 18 dp `label`; `.on` uses `acc-soft` and
  `acc-ink`.
- Pressed: scale `.92`.
- Used for close, more, settings, scan. Never the only way to reach a whole
  module.

### 5.9 Chip

- 32 dp tall, 6/12 padding, `r-full`, `surface`, text 15/400 (`subhead`).
- Selected: `acc` fill, `on-acc` text, weight 500.
- Filter rows (Library, Food) may use a **neutral** selected state
  (`surface-3` + `label`) so the accent is not spent three times before the
  content.
- Scrolling strips never scroll vertically; a chip's slop is clipped by the
  strip.
- A chip is a filter or a choice, never a status. Status is a tag.

### 5.10 Segmented control

- Track `surface-3` (dark) with a 2 dp padding; selected thumb `#636366` or
  `surface` (light) with a hairline; radius 10 dp; each segment 32 dp tall.
- Selected text 15/600 `label`; unselected 15/400 `label-2`.
- Up to five segments; more becomes a chip strip or a dropdown sheet.
- Used for Overview/Body/Lifts/Effort, week/month/year, slider vs side-by-side.

### 5.11 Switch

- 51 × 31 dp, `r-full`. On: `acc`; off: `surface-3`. Knob white 27 dp, with the
  hairline ring on a light accent.
- On press the knob stretches before it snaps, the platform's own gesture.
- A switch takes effect immediately; there is no Save in a row.

### 5.12 Stepper

- A number field between − and +, 44 dp tall, 12 dp radius, `surface-2` cells,
  tabular numerals, unit as a 13 sp suffix.
- Long-press accelerates. A step is a sensible unit: 0.1 kg, 1 rep, 5 s, 250 ml.
- The number is selectable for direct entry; the keyboard is numeric.

### 5.13 Slider

- 4 dp track, `surface-3`; fill `acc`; knob 24 dp white with `acc` ring.
- Value bubble above while dragging, 12/600 tabular.
- Used for effort/RIR, photo zoom, rest length. Never for a clinical value.

### 5.14 Text and search field

- `surface-2`, radius 10 dp, 46 dp tall, 16 dp side padding, 17 sp text,
  placeholder `label-3`.
- Focus: 2.5 dp `acc-ink` ring, 2 dp offset, radius 8 dp. No glow.
- Search: 18 dp magnifier, a 22 dp clear button, and an optional trailing count
  badge (`84` in `surface-3`). The field never grows a border.
- Error: message in `red-ink` 13 sp below, icon + text. The field gets a 1 dp
  `red` ring only after a failed submit, never while typing.
- Numeric fields use a numeric keyboard and keep the unit outside the field.

### 5.15 Tab bar

Five destinations with a raised centre (revised 2026-11-22; see §13.1):

```
Today   Vitals   [ Train ]   Nutrition   Stats
```

- **Train** is the raised centre disc, openGym's signature: 56 dp, `acc`,
  `on-acc` glyph, 12 dp above the bar, soft accent shadow, pressed scale `.93`.
  It is the only glow in the app. Tapping it opens Train; with a session planned
  for today the Train root leads with Start.
- **Vitals** holds weight, measurements, composition, photos, vitals, sleep and
  cycle (the former Body tab). **Stats** is the former Insights. Settings, the
  former More, is the top-bar gear (§4.4).
- Quick add moves to the top bar; there is no second floating disc.

- `bg-el` at 94 %, 10 dp blur, `0.5 dp` top `sep-op`; opaque under Reduce
  Transparency.
- Icon 25 dp, stroke 1.65 at rest and 2.0 active; label 11/500.
- Inactive `label-2`, active `acc-ink` (label and icon).
- One 8 dp orange dot (with a 2 dp `bg-el` ring) on Today and on the settings
  gear when a sync problem is hidden by the connection banner.
- A scroll-to-top 36 dp button may appear at the **leading** end after two
  screens of scroll.
- In a live session the tab bar is replaced by the session chrome (close,
  timer, finish). Leaving mid-session asks once, not on every back.

With five destinations the raised centre returns, as in openGym, and goes to
Train, the primary surface. The change is recorded in §13.1.

### 5.16 Sheet

- Bottom sheet, `bg-el`, top radius 22 dp, 8/16 padding, bottom 22 dp + nav
  inset, grabber 36 × 5 dp `label-4` centred 6 dp from the top.
- Max height 90 % of the viewport minus the status inset; it scrolls only
  downwards.
- Title 20/600, 16 dp below the grabber, sticky search under it.
- Animation: 340 ms `ease-out` rise. Back, scrim tap and swipe-down all close;
  the scrim is 40 % black with a 2 dp blur.
- A sheet that holds a field stands on the keyboard (§4.3).
- The primary action is a full-width button under the content, with Cancel as a
  quiet text button below it.
- A sheet never contains another sheet. If it needs a picker, the picker is a
  step inside it.

### 5.17 Dialog

For one decision or one small result:

- 300 dp wide (440 dp for a real summary), `surface-raised`, radius 16 dp,
  padding 22 dp, centred.
- Dark: hairline `raised-line` + a soft shadow. Light: white + shadow.
- Pop 280 ms from 94 % scale.
- Two actions maximum: the primary and a quiet cancel. A dialog with a form is
  a sheet.

### 5.18 Toast and undo

- Floating 22 dp-radius pill, `surface-2` at 88 % with a 20 dp blur, 12/22
  padding, 15/500, max 88 % width, 96 dp above the bottom inset.
- With an action (Undo): message + accent action + optional ×, 44 dp targets
  (2026-11-23: the earlier 36 dp contradicted §4.5 and §11.1).
- One toast at a time; a new message replaces the old. Visible 4 s for a plain
  message, 6 s with Undo. Announced to screen readers.
- **The toast is not the undo.** A destructive action also leaves a tombstone
  row where the entry was ("Deleted · Undo"), which stays for the session, and
  the entry stays in Trash for 30 days (`pr.md` §6.29). The toast is the
  immediate shortcut; the tombstone is the promise.

### 5.19 Timer bar

The rest timer, the fast-log timer and a live activity's elapsed time:

- Inline bar 22 dp above the tab bar (or docked to the bottom in a focused
  session), 12 dp radius, `surface-raised` at 97 %, hairline + shadow.
- Clock 28/600 tabular; a 3 dp progress line along the top edge, `acc`.
- Controls: −15 s, +15 s, pause, Skip — all 44 dp; Skip gives up its label
  before the clock gives up a digit.
- Work timer: hairline ring in `acc` and the clock in `acc-ink`, so "holding"
  and "resting" are distinguishable at a glance.
- Paused: clock and bar at 50 %.
- Full-screen session timer: 64 sp/700, the only number allowed to be that
  large.

---

## 6. States and motion

### 6.1 Press states, one rule per tier

| Tier | Response | Duration |
|---|---|---|
| Rows | fill `surface-2`, no movement | 140 ms |
| Controls (button, chip, segment, stepper, tick) | scale `.97` (small round `.92`) | 140 ms |
| Cards | scale `.985` | 140 ms |
| Tab / disc | scale `.93` on the glyph or disc | 140 ms |

Feedback starts on touch-down, never on release. No ripple, no long-press
highlight, no grey overlay.

### 6.2 Selected

Selection is a fill (`acc` for a choice, `surface-3` for a filter), a weight
change (500/600), or a 2 dp inset accent line. Never a border on the outside of
a chip, never a glow.

### 6.3 Disabled

32 % opacity for a control, 45 % for a row, `label-3` for text. A disabled
control still explains itself in its subtitle if the reason is not obvious.

### 6.4 Loading

- Skeletons shaped like the content (a card, a row, a chart baseline), pulsing
  4 % opacity at 1.2 s, never a spinner over a whole screen.
- A local read finishes in under 300 ms and gets **no** loading state at all.
- A network path that must show progress uses a 2 dp indeterminate line under
  the header, not a modal.
- Optimistic writes: the value appears immediately, with a subtle "saving" dot
  that becomes "saved" or "queued" in the provenance area.

### 6.5 Offline and sync

- A single banner under the status bar, 34 dp, 13 sp: "Offline. Your changes are
  saved on this phone." with a `label-2` icon and an optional "Retry" pill.
- Deliberate local-only mode is **not** an error: one quiet 11 sp line, no fill.
- A sync problem is orange, never red; a rejected write is red and names the
  record.
- The banner is dismissible; the status stays reachable in Settings → Data and as a
  dot on Today.

### 6.6 Errors

- Form errors sit under the field: icon + 13 sp `red-ink`, announced politely.
- A failed screen keeps its content and offers a retry; it never becomes a
  full-screen error illustration.
- Never blame the person. "Couldn't save. Your entry is still here." not
  "Invalid request."

### 6.7 Empty

An empty state is a 29 dp glyph, a 17/600 line, a 13 `label-2` sentence and one
action. It is a card, not a hole. Never "Nothing here yet" alone, never a
cartoon.

### 6.8 Success

A logged value confirms in place: the row updates, the accent ticks, a light
haptic. No success modal. A PR adds a gold tag that stays.

### 6.9 Motion tokens

| Token | Value | Use |
|---|---|---|
| `ease` | `cubic-bezier(.32,.72,0,1)` | Everything. Decelerating, no overshoot. |
| `fast` | 140 ms | Press, toggle, chip. |
| `med` | 220 ms | Toast, card, fade. |
| `page-push` | 380 ms | Drill in: enters from the inline end, 100 → 0. |
| `page-pop` | 340 ms | Back: previous page enters from -28 % at 70 % opacity. |
| `sheet` | 340 ms | Bottom sheet rise. |
| `dialog` | 280 ms | 94 % scale + fade. |
| `progress` | 700 ms | A bar's fill to a new value, `cubic-bezier(.23,1,.32,1)`. |

- Only `transform` and `opacity` animate. Width, height, colour and blur do not
  (colour changes are instant, or a 140 ms cross-fade of opacity).
- Nothing animates on scroll except a pinned header's hairline and the
  scroll-to-top button's transform.
- `prefers-reduced-motion` / Android "Remove animations": all of the above
  become instant; the sheet still appears, the page still changes, progress
  still moves.
- `prefers-reduced-transparency`: all blurs and translucency off, opaque fills.
- High contrast: system palette steps up, `label-2` and separators firm up.

### 6.10 Celebration

An earned milestone may use one 1.2 s burst: a soft warm banner, the glyph
popping once, fewer than 30 particles, and only if motion is allowed. It never
appears on a logging screen, never blocks a tap, and never repeats for the same
milestone. No sound unless the person chose one.

---

## 7. Provenance and trust in the interface

openGym has no equivalent; this section is openHealth's own and is not
negotiable (`pr.md` §2.2, §6.27, §6.28).

### 7.1 The provenance affordance

Any number or row can be tapped to open a **provenance sheet**. The affordance
is the row itself; charts get a point scrub then a "Details" row in the tooltip
card. There is no separate "info" icon on every value.

### 7.2 The provenance sheet

```
Value                    82.4 kg
Trend                    −0.3 kg this week

Source                   Withings scale
Method                   Bioimpedance (BIA)
Happened                 Sat 21 Nov, 07:42
Recorded                 Sat 21 Nov, 09:10  ·  1 h 28 m later
Local day                Sat 21 Nov (Europe/Berlin)
Quality                  Good · 1 of 1 reading
Edited                   No
Raw record               #8f2c…  ›

[Exclude from trends]    [Edit]    [Delete]
```

Rules:

- `recorded_at` is always shown when it differs from `happened_at`, with the
  delay in words. A late entry carries a `Late entry` tag in the list.
- Every row in the sheet is 46 dp and tappable only if it leads somewhere.
- Source icons: phone, watch/band, scale, cuff, CGM, lab, import file, API,
  manual hand. A manual entry says "Entered by you".
- The raw-record row is present only when the raw payload is still kept
  (`pr.md` §7.1's 90-day policy); otherwise say "Raw record compacted".
- Delete is destructive and separated; Edit keeps the original value and asks
  for a reason only when the person chooses to add one.

### 7.3 In lists and charts

- A list row's subtitle names the source when more than one source exists:
  `Watch · 07:42` or `Manual · recorded 09:10`.
- Imported data carries a small 12 sp tag: `Imported`, `Late`, `Edited`,
  `Excluded`. Tags are neutral except `Excluded` (orange-ink) and `Edited`
  (indigo).
- A chart's detail shows the source under the value; a mixed-source line uses
  one series per source with a legend, never a blind average (`pr.md` §7.6).

### 7.4 Conflicts and quality

- A conflict is a review inbox card: "Two sources disagree about Thursday's
  steps." Two values side by side, the configured priority marked "wins", and
  `Keep 8 412` / `Use 9 103` / `Review`. Never auto-resolve silently.
- A value outside a plausible range gets an orange inline note, not a modal.
- The review inbox lives in Settings → Data, with a count badge, and is surfaced on
  Today only when it blocks a trend the person is looking at.

### 7.5 Reference ranges

- A published range is drawn as a **band**, `green-ink` at 18 %, never as a
  coloured zone with a hard red edge.
- The range's guideline and region sit directly under the chart, 12 sp
  `label-2`, with a link: "ESC 2024 · European Society of Cardiology".
- A user override is a dashed boundary and a note: "Your clinician's range".
- Wording is neutral: "outside the published range", never "high", "risky",
  "concerning" (`pr.md` §12.1). The zone names Low / Normal / Raised / High in §8.2.1 are
  the scale's own words, not a verdict about the person (2026-11-25).
- No range ships without a citation; a range without a citation is an opinion
  and is not drawn.

### 7.6 Weight-neutral mode

One switch (`pr.md` §6.25) hides calories, weight, body fat and progress photos
across the app.

- Hidden cards are **removed**, not blurred, dashed or filled with `—`.
- Training, steps, sleep, vitals, habits and insights that do not depend on
  those values keep working unchanged.
- No upsell, no "turn it back on" banner, no missing-feature ghost. Settings →
  Appearance holds the only control, with a plain sentence about what is hidden.
- If every card on a module's root would be hidden, the module shows its
  remaining content or a calm explanation, never an empty shell.

### 7.7 Sync and local-only states

Local-only, syncing, synced, offline and error are five distinct states with
five distinct sentences. The person can always answer "what is not on the
server yet?" from one screen in Settings → Data (`pr.md` §6.24, §6.29).

---

## 8. Data visuals and charts

### 8.1 Chart chrome

- No chart border, no background panel inside a card.
- Horizontal gridlines only, `sep-op` at 1 dp, starting after the y labels.
- Axis labels 12 sp `label-2`, tabular; no tick marks.
- A line is 2.5 dp, round joins and caps, one series = one colour from the
  palette (accent first, then `blue`, `teal`, `purple`, `orange`).
- A second series on the same axis uses a 2 dp stroke and a legend, never a
  second y-axis on a phone.
- The goal line is 1.5 dp `gold-ink`, dashed 7/4, labelled at the end.
- A reference band is `green-ink` at 18 % with a 12 sp label, and the citation
  line below.
- Hover/scrub: 1 dp dashed `label-3` crosshair, a 5 dp dot in the series colour
  with a 2 dp card-coloured ring, and a tooltip on `surface-raised` with an
  inset hairline. Touch scrub is a 44 dp zone.
- Annotations (illness, travel, injury, new medication) are 1 dp `indigo`
  vertical bands with a 12 sp label at the top, never an icon floating over the
  data. A band is tappable to read the note.
- Data gaps are gaps. Never interpolate across missing days, never draw a
  straight line through a gap.
- Insufficient data is a sentence with the minimum needed: "Three weigh-ins are
  needed for a trend."

### 8.2 Chart types

| Type | Use | Rules |
|---|---|---|
| Line | Weight, HRV, resting HR, glucose, steps | One point per local day; trend line optional; 30-day default. |
| Sparkline | Inside a card, no axes | 1.5 dp, accent, last point 3 dp; decorative but with an accessible summary. |
| Bar | Weekly volume, macro totals, sleep duration | 8 dp radius cap, 2 dp gap; one colour per meaning. |
| Stacked bar | Sleep stages, macro split | 2 dp seams; legend with figures; 14 dp tall. |
| Horizontal bar list | Sets per muscle, top foods | Name, 8 dp track, fill, value; 30 % width for the track; at ≤ 400 dp the bar runs full width under the name. |
| Ring | Calories, steps | 8 dp stroke, round cap, `surface-3` track; centre 28/600; max two rings side by side. |
| Heatmap | Training consistency, steps | 11 dp cells, 2 dp gap, 2 dp radius, five accent steps. |
| Body map | Muscle load, recovery | §9.1. |
| Hypnogram | Sleep stages | Stage rows at fixed heights, 2 dp seams, labelled; never hue-only. |
| Clinical range strip | BP, glucose, temperature, lab values | See §8.2.1. |

#### 8.2.1 The clinical range strip

The one place a scale of four stops is allowed, because a single reading has to
be located against a published range:

```
Low ░░░░░  Normal █████  Raised ▒▒▒▒  High ▓▓▓
                         ▲ you
```

- 10 dp tall, radius 5, four stops: `blue` at 35 %, `green` at 35 %,
  `yellow` at 45 %, `orange` at 55 %. These are **zones, not alarms**; no stop
  is red. Zone labels use the matching text inks.
- The person's value is a 3 × 16 dp `label` tick with a 12 sp label "you"
  above, never a coloured dot.
- Stops are labelled Low / Normal / Raised / High beneath, 11 sp `label-2`, and
  the numeric boundaries are printed under each.
- The guideline and region are printed in full above the strip:
  "ESH 2023, adults, home measurement — below 135/85". A range with no
  citation does not ship.
- The strip has a table alternative with the same numbers and the citation.
- A user or clinician override draws a dashed boundary and says "Your range".


### 8.3 The one ramp

Heatmaps, body maps, macro bars and progress bars use the same five steps:

| Step | Mix |
|---|---|
| `l0` | `surface-2` (nothing) |
| `l1` | 30 % accent over `surface-2` |
| `l2` | 55 % accent |
| `l3` | 78 % accent |
| `l4` | `acc` |

So "darker lime" always means "more", everywhere. The body map's untrained base
is `label` at 11 % over `surface` (dark) rather than a fixed grey, so the body
stays visible in both themes.

### 8.4 Domain hues

Three charts may bring a small named set of hues, and only inside the chart:

- **Macros:** protein `green`, carbs `blue`, fat `purple`, fibre `teal`.
  Legend always names them; the calorie ring stays the accent.
- **Sleep:** awake `orange`, REM `frost`, light `blue`, deep `purple`.
  Stage labels carry the name, so the chart survives colour blindness.
- **Cycle:** flow and symptoms use `pink` and `label-2`, with words, never a
  red alarm ramp.

Labels beside these marks are `label-2` with a coloured swatch; only the four
macro names may take their matching ink. The fill value is never used as text.

No other domain palette.

### 8.5 Numbers and axes

- Axes start where the data means something; a weight chart does not start at
  zero (it would flatten the signal), a steps or volume chart does.
- Show the unit once, in the axis label or the card header, not on every point.
- One decimal on weight and body fat; whole numbers on steps, calories, sets.
- Null is a gap. Zero is zero.
- A change is always signed and directional (`−0.8 kg`, an arrow glyph), with
  the period named: `30 d`.

### 8.6 Tables and alternatives

Every chart has an accessible table alternative (TalkBack action "Show as
table"), and every insight links to the raw entries behind it (`pr.md` §6.16).
A chart's content description is a sentence, not a dump: "Weight, 30 days,
down 0.8 kilograms, from 83.2 to 82.4."

### 8.7 Print and export

Doctor-ready PDF and shared images are **always light**, regardless of theme:

- A4, 12 mm margins, black on white, 10/12/16 sp equivalents.
- Charts render in greyscale-safe line styles: solid, dashed, dotted, with
  labels.
- Every figure keeps its unit, source and the report's generation date.
- Reference ranges keep their citation line.
- The disclaimer from onboarding appears once, in 8 sp, at the foot of the
  clinical report, never buried.
- Shared progress images use the dark card at 1080 × 1350, the app mark, the
  date range, and only the values the person ticked.

### 8.8 The route map (added 2026-11-26)

A route is a chart over geography, so it follows §8.1's chrome rather than inventing a
panel: no border, no inner card and no shadow. It uses the theme's existing ramp and
accent, with no new token:

- **Base:** `bg` behind the tiles; land `surface-2`; water `surface-3`; boundaries
  `sep-op`; roads `sep`. Labels are optional and, when a pack provides glyphs, use
  `label-3`; no label is required for the map to be readable.
- **Route:** 2.5 dp `acc`, round joins and caps. Start and end are 4 dp dots, start in
  `green-ink` and end in `label`; the shape itself carries the direction.
- **No pack:** the same route on `surface-2` with `sep-op` gridlines. The map is an
  enhancement, never a condition for logging or export.
- **Privacy:** a "Hide the route" switch removes the map and keeps the splits, zones and
  export. The route is never mirrored in RTL (§11.5).
- **Attribution:** `© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors` sits below
  the map at `caption-2`/`label-3`, never over the route.
- **Table:** the activity detail already carries splits and heart-rate zones as text; the
  map is not the only way to read the activity.

### 8.9 The clinical report paper (added 2026-11-26)

A report is a document, not a screen, so it leaves the dark theme behind (`pr.md` §6.9,
§6.16, §6.25). The preview on the reports page shows the same paper the PDF uses.

- **Paper:** pure white `#FFFFFF`, ink `#111111`, secondary ink `#555555`, hairlines
  `#BEBEBE` at 0.5 pt. These are the document's own values, not app tokens; no accent
  appears in a report and no dark surface is ever printed.
- **Type:** the report uses four sizes only, mapped from the existing roles: title 20 pt
  (`title3`), heading 13 pt (`footnote` bold), body 9.5 pt (`caption`-scale) and caption
  8 pt (`caption2`-scale). They are points, not sp: a PDF does not scale with the phone.
- **Tables:** header bold, 0.5 pt rule under the header and under each row, relative
  column weights normalised to the text width, a row never split across pages. A table's
  rows are the preview's rows and the CSV's rows.
- **Content order:** title, window, generated date, then Vitals, Medicines, Symptoms,
  Labs, Allergies, Conditions, Vaccinations, Sleep, Activity, Trend summary, References,
  and the source note plus disclaimer in the footer of every page.
- **Every figure** carries its unit, its source and, where a range is shown, the citation
  that backs the range. A missing range says "not entered"; a missing value is omitted,
  never printed as zero.
- **Weight-neutral:** weight, body fat and photo-derived values are absent from the paper
  and from every export, not hidden behind a label.
- **Sleep page:** the same surface and rows as the rest of the app; consistency is a
  sentence with a number, not a score. Stages use the existing chart/table pairing when a
  source reports them.

---

## 9. The body, photography and media

The body visuals are the part of openGym worth taking most literally. They are
also the part with the clearest licence boundary.

### 9.1 The body map

- **Geometry:** the four views (male/female × front/back) from **MuscleMap** by
  Melih Colpan, MIT, converted to SVG paths. MIT permits reuse with the notice
  retained; it is the one image asset we may take from openGym. openGym's
  rendering code is AGPL and is **not** copied; we write our own renderer against
  the same idea.
- **Anatomy:** whole muscles, not sub-groups. A sub-region would imply a
  precision the exercise data does not have.
- **Views:** front and back side by side, 6 dp gap, each with its own viewBox;
  `maxHeight = min(46 % of viewport, 340 dp)`. The pair is centred and always
  stands at equal height.
- **Untrained base:** `label` at 11 % over `surface` (dark);
  `label` at 14 % over `surface` in light. Never `surface-2`, which vanishes on
  a white card.
- **Ramp:** the five steps in §8.3, relative to the hardest-worked muscle in the
  same map (balance) or to fixed thresholds (recovery).
- **Separation:** every path is stroked 2.5 dp in the **card's surface colour**,
  so neighbouring muscles read as separate forms. There is no outline path of
  its own; without this the body is a blob.
- **Selected muscle:** stroke 7 dp `label` plus a 2 dp surface glow on each side,
  drawn as an 11 dp `surface` stroke under the 7 dp label stroke. The selected
  muscle's name appears below the map, never as a floating tooltip.
- **Recovery view (revised 2026-11-22):** base (untrained) → `green` (Ready) →
  `orange` (Easing) → `red` (Recovering). A muscle trained today is `red`, the
  next day `orange`, the third day `green`; later it returns to base. Steps come
  from fixed hours-since-load thresholds scaled by session volume, with 24 / 48 /
  72 h as the defaults. The legend names Recovering / Easing / Ready. Colour is
  never the only channel (§11.3): the selected-muscle line states the step and
  hours, and Recovering carries a diagonal hatch for red-green colour blindness.
  The wording never says "pain" or "sore"; soreness is something the person logs.
- **Hatch (added 2026-11-24):** a Recovering muscle carries a diagonal `surface`
  hatch at 35 %, a 1.5 dp line every 7 dp, clipped to the muscle and drawn over the
  red. It is the channel that survives red-green colour blindness; the legend's word
  and the muscle sheet's step are the other two. No other step is hatched.
- **Plan view:** the same geometry shaded by the single accent ramp of §8.3, by each
  muscle's share of the plan's working sets. Plan views never use red, orange or
  green, so "what the plan trains" and "what is recovering" can never be confused.
- **Where it may appear:** Today (the hero pair), Train (the pair and the mini map),
  Stats (mini). Not on a vital, a clinical card or a report (§13.9).
- **Strength view:** the top band is `yellow` with a 1.5 dp glow; only the
  strongest group earns it.
- **Mini map:** two views at 46 dp tall, 2 dp gap, 26 % base, 3 dp strokes. Used
  beside a routine name and in a session header at 30 dp.
- **Interaction:** every drawn shape of a muscle is tappable, but only **one**
  shape per muscle is a button in the accessibility tree, so TalkBack hears
  "Forearms", not fourteen forearms. `aria-pressed` mirrors selection.
- **Motion:** fill transitions 220 ms. No pulsing, no rotating, no scanning
  animation over a body. A body is not a loading spinner.

### 9.1.1 Dynamic body model (added 2026-11-22)

The body map is also a model of the person.

- **Layers, back to front:** a `fat` adipose layer, then the MuscleMap muscle
  paths, then the recovery or balance colour. The adipose layer is an offset of
  the body outline whose thickness per region comes from the person's own data:
  body fat %, then tape measurements (waist, hips, chest, arm, thigh, calf, neck).
  With measurements missing it falls back to body fat %, and with neither it is
  not drawn.
- **It is a 2D schematic, not a likeness.** No 3D, no photo-derived shape (photo
  body composition is on the cut list, `pr.md` §14.5). Region thickness is
  clamped so the figure stays anatomical at any input.
- **Dynamic:** it re-renders from the latest accepted observation. As body fat or
  waist falls, the layer thins; a before/after scrub shows the change over time.
- **Colour:** `fat` at 55 % over the base, separated by the same 2.5 dp surface
  stroke. Fat is never drawn red or orange. The layer is drawn **only outside the
  muscle shapes**, never under a recovery colour, so a pale fat edge cannot be
  read as a recovery step. A "Fat layer" chip under the figure toggles it, and the
  legend names it (§12.5 records why colour alone cannot separate fat from orange).
- **Provenance:** every region is tied to its measurement. The tap sheet names the
  source and method (BIA, DEXA, Navy, tape) per §9.2 and says when a figure is an
  estimate. Weight-neutral mode hides the adipose layer entirely and draws the
  muscle figure only (§7.6).
- **Thickness (added 2026-11-24):** base = body-fat fraction × 0.08 of the drawn
  figure height, clamped to 0.4–3.5 %; then the region factor neck 0.5, chest 0.9,
  waist 1.0, hips 1.05, thighs 0.95, arms 0.6, calves 0.55. With two or more tape
  sites measured, a region also scales by its own circumference against the median
  of that person's measured sites, clamped 0.75–1.3; one measurement changes
  nothing and no population reference is invented. The band is one rim around the
  union of the muscles plus an outward ring wherever a region is thicker, cut
  against the muscle union, so it exists only outside the muscle shapes. The
  union is **closed** by a 24-unit ring first (§9.1), so the rim never runs down
  the seam between two neighbouring muscles. The layer is `fat` at 55 % in dark;
  in light it is drawn at full opacity, because 55 % composites to 1.97:1 and
  misses the 3:1 graphic bar (§12.5).

### 9.2 Body composition card

For scale-imported values (body fat, lean, bone, water, BMR):

- One card, a 2×2 or 3-across grid, label 12 `label-2`, value 20/600, change
  line 12 in `green-ink`/`orange-ink` with the period (`−0.4 kg in a month`).
- A 13 sp footer names the source and says it is read-only: "Straight from your
  scale. Change it where it was measured."
- Never mix methods in one trend without a method tag (BIA vs DEXA vs Navy).

### 9.3 Measurement silhouette

- The same body geometry, no heat colour, with nine anchor dots: neck,
  shoulders, chest, waist, hips, upper arm, forearm, thigh, calf.
- Tapping an anchor opens the measurement field for that site. Measured sites
  glow with `acc` at 30 %.
- A before/after outline (last month vs today) may overlay at 1 dp, dashed for
  the older one.
- Circumference values sit in a list under the figure, not floating on it.

### 9.4 Progress photos

- Stored privately, encrypted where possible, never uploaded without an explicit
  action, excluded from a general export unless the person includes photos. B7's Vitals
  card carries the opt-in ("Include photos in encrypted backups", off by default); the
  encrypted backup includes the photo rows only when it is on (2026-11-25).
- The gallery is a 3-across grid of square tiles, 10 dp gaps, 16 dp radius,
  date in a 12 sp pill at the bottom. A photo is blurred until tapped if the
  person enabled "Hide photos in the gallery".
- Compare: two photos open a **before/after slider** — a 2 dp white handle,
  24 dp grip, date pills on each corner, 44 dp drag area, `role="slider"` with
  arrow-key support. Three to nine photos open as a grid of equal-aspect cells
  (rows of at most three) with drag-to-crop.
- A share action renders exactly what is on screen, strips location metadata,
  and never includes a photo the person did not select. No auto-share, no
  "share to community".
- The first photo of a comparison is always the older one. The date range is
  captioned below: `12 weeks apart`.

### 9.5 Exercise, pose and food media

- **Licensing first.** openGym's exercise stills and animations are licensed to
  openGym from Gym visual and are **not** reusable. Never copy, extract, hotlink
  or feed them to a model. openHealth's media must be our own or licensed
  (`pr.md` §12.2). The 180 px rule in openGym is their licence's limit, not
  ours.
- **Stills:** square, 1:1, with a white or transparent matte. Row thumb 50 dp /
  radius 8; grid tile 150 dp minimum / radius 12; detail 16:9 or 1:1, full card
  width.
- **Animation:** off by default in a list. One animation plays at a time, only
  the one in view, and only when the person taps play or enables autoplay. A
  data-saver or battery-saver setting disables autoplay.
- **A play affordance** is a 32 dp translucent dark pill with a white glyph at
  the top-trailing corner; a badge (equipment, body part) sits at the top-leading
  corner. Never three badges on one image.
- **Food:** product images are small (44 dp) and optional; a missing image is a
  neutral glyph tile, never a stock photo. No full-bleed food photography as a
  background.
- **Poses:** a still of the pose at the starting position, with a 2 dp accent
  line showing the direction of movement on the detail screen. No form-check
  overlay, no skeleton, no camera AR (`pr.md` §14.5).

### 9.6 Illustration and decoration

- Icons are line drawings (§10). There are **no character illustrations** and
  no 3D renders, and **no stock photography of people, gyms or meals**. The body
  geometry is the product's imagery; a photograph is only ever the person's own
  progress photo or a licensed product/exercise image.
- Empty states use a 29 dp glyph, not a drawing.
- Seasonal decoration (openGym's Halloween/Christmas) is **Later, off by
  default, and never on a logging or clinical surface**. It may tint a header
  or add a static motif behind a card; it never animates over data, never
  changes a status colour, and never appears in a report.

### 9.7 Avatars

Initials on `surface-2` at 17/600, 36 dp circle, with a 2 dp `surface` ring when
overlapping. A profile photo is optional, 36 dp, round, and never used in a
shared report.

---

## 10. Icons and illustration

### 10.1 The set

One custom line set, drawn for this product. Do not mix Material Icons, SF
Symbols and our set in one screen.

- 24 × 24 dp box, 1.5 dp padding, **stroke 1.7**, round caps and joins, no fill.
- `currentColor`; the icon inherits the text role's size at `1 em`, so it scales
  with type and font scale.
- Optical sizes: 14 dp inside 13 sp text, 18 dp in a button, 25 dp in the tab
  bar, 29 dp as a card glyph.
- Active tab and chevrons may go to 2.0–2.4 for weight, never below 1.65.
- Icons are never the only label for a module; they may be the only label for a
  familiar control (close, more, back).

### 10.2 The core set

Navigation: today (sun/house), train (dumbbell/figure), food (fork/plate), body
(silhouette), insights (chart), more (sliders).

Modules: workout, yoga, steps, weight, heart, sleep, activity, mood, cycle,
family, coach, integrations.

Actions: add, check, close, chevron-left/right/down, search, filter, scan
(barcode), camera, play, pause, skip, timer, edit, delete (trash), undo, share,
export, lock, cloud-off, refresh, info, warning, external-link.

Objects: barbell, dumbbell, kettlebell, plate, machine, band, mat, shoe, bike,
route, scale, cuff, glucose drop, water drop, pill, lab flask, file, watch, phone.
The water drop was added 2026-11-23 for the global quick add (B3); it is a geometric
droplet on the same grid and carries the same stroke rule as the rest of the set.

### 10.3 Glyph keys for routines

A routine's icon is a **key**, not an emoji: strength, arms, abs, legs, pull-up,
dumbbell, barbell, kettlebell, plate, machine, run, bike, swim, boxing, timer,
stretch, moon, heart, flame, bolt. Twenty, grouped into Strength / Equipment /
Cardio / Recovery. An emoji is never stored in a new record; an old emoji is
mapped to its nearest key on read. A person may choose an emoji only for their
own streak/badge decoration, and it is rendered at 1 em inside a plain tile.

### 10.4 Chart icons and status glyphs

Status always pairs an icon with a word: check (done), triangle (attention),
x-octagon (error), trophy (record), snowflake (freeze), flame (streak). The
flame is the only multi-colour illustration in the set, and it is warm by
design.

### 10.5 Brand mark

The product mark is **not decided** (`pr.md` §13.10) and openGym's logo is not
ours to copy. Until it is:

- In-app use the wordmark "openHealth" set in the UI font at 17/600, lower-case
  except the H, in `label`. No custom lettering.
- If a symbol is needed (app icon, splash), it is a geometric mark in `acc` on
  `bg`, drawn on the same 24 dp grid as the icons, with no gradient, no gloss
  and no drop shadow. The app icon is the only place the mark may fill the
  frame.
- The mark never appears inside a card, a chart or a report header. The person's
  data is the identity of a screen, not our logo.
- No mascots, no anthropomorphic body parts, no "wellness" leaf.

---

## 11. Accessibility and internationalisation

### 11.1 The bars

| Item | Requirement |
|---|---|
| Body text | 4.5:1 |
| Large text and controls | 3:1 |
| Focus ring | 2.5 dp `acc-ink`, 2 dp offset, radius 6 |
| Touch target | 44 × 44 dp |
| Text scaling | 200 % without loss of content or action |
| Motion | reduced-motion honoured |
| Transparency | reduced-transparency honoured |
| Contrast | high-contrast honoured |
| Chart | text alternative + table |
| Body map | one button per muscle, name + pressed state |
| Error | announced, associated with the field |
| Live region | sync status, timer, toast |

### 11.2 Screen readers

- Every control has a name; icon buttons have a content description, not a tooltip.
- A chart's description is a sentence with the trend, period and endpoints.
- The body map exposes its muscles as a group with one button each; drawn
  duplicates are hidden.
- A row reads as one item: title, value, subtitle, role. Never "button, button,
  button" inside one row.
- The timer announces at 10 s, 3, 2, 1 and Ready, and respects "announcements
  off".
- Decorative art is hidden. There is no "image" announcement for a hairline.

### 11.3 Colour is never the only channel

Every status carries a word or a glyph. The macro and sleep charts label their
series. The body map's ramp has a legend. A selected tab has a weight and colour
change. A red row says "Delete".

### 11.4 Text and translation

- Sentence case; no uppercase transforms on labels (Turkish, German and Greek
  break them).
- Strings are keys, never concatenated sentences; plurals use the platform's
  plural rules.
- A layout must survive +40 % string length (German) and 200 % font scale.
- Numbers, dates, units and first day of week are localised. Units are chosen
  per profile, per exercise where needed (`pr.md` §6.5 V1).
- Exercise, pose and food names are content, not UI strings, and have their own
  translation path.

### 11.5 RTL

- Use logical start/end everywhere: padding, hairlines, chevrons, progress
  fills, sliders.
- Page push enters from the inline end; the back gesture mirrors.
- Charts read left-to-right in time even in RTL (time is not mirrored), but
  labels and legends mirror.
- The body map is not mirrored; anatomy is anatomy.

### 11.6 Focus and keyboard

- A visible focus ring on every interactive element, never removed without a
  replacement.
- Tab order follows reading order; sheets trap focus and restore it on close.
- A keyboard (Android desktop/tablet, Later) can operate every control,
  including the chart scrub and the body map, with Enter/Space/arrows.

### 11.7 Voice and copy

The interface speaks like a careful friend who happens to be a clinician's
assistant.

- Second person, present tense, sentence case. "You slept 7 h 10 m and recovered
  well."
- Fact, then consequence, then option — never the reverse. "Your cuff recorded
  148/94 at 08:12, above the range you set. Add a second reading?"
- No guilt, no streak pressure, no exclamation marks, no praise for logging, no
  "Great job!".
- Never diagnose, prescribe or dose. State what was recorded and, where
  relevant, the guideline it is compared against (`pr.md` §2.5, §6.9).
- Explain every derived number one tap away: "Target came from double
  progression: 4 × 5 at 80 kg last week, so load went up one step."
- Numbers are formatted, not concatenated: use the locale's separators, the
  profile's units, and a non-breaking space between value and unit.
- Buttons are verbs and objects: "Log weight", "Start session", "Keep 8 412".
  "OK", "Submit", "Yes/No" and "Are you sure?" are banned.
- An error never blames: "Couldn't save. Your entry is still here."
- A placeholder in a design is a bracketed fact (`[YOUR PRICE]`), never lorem
  ipsum and never an invented statistic.
- No em dashes in UI copy, and no jargon where a plain word exists ("water",
  not "hydration input").

---

## 12. Themes and the full palette

### 12.1 Dark is canonical

The dark theme is authored first and is the reference for every contrast
decision. It is not a filter over light. The page is true black so an OLED phone
at night shows a screen, not a grey rectangle.

### 12.2 Light is derived and verified

Light is not an inversion: surfaces become white cards on `#F2F2F7`, secondary
text deepens to `.75`, accent fills darken one step, and every accent-as-text
gets its own ink. Shadows return because a white card on a near-white page needs
one; dark uses a raised surface plus a hairline instead.

### 12.3 Elevation

| Level | Dark | Light |
|---|---|---|
| Page | `bg` | `bg` |
| Card | `surface` | `surface` + no shadow (a list card) |
| Pressed / nested | `surface-2` | `surface-2` |
| Control track | `surface-3` | `surface-3` |
| Floating | `surface-raised` + `raised-line` hairline + soft shadow | white + soft shadow |
| Sheet | `bg-el` | `bg` |

Shadows: `0 12px 34px -8px rgba(0,0,0,.55)` for floating dark surfaces,
`0 10px 30px -6px rgba(0,0,0,.18)` for light. A card never has a shadow.

### 12.4 What can be themed

Theme (dark/light/system), accent (9 presets + custom), text size, icon style
(line or the person's own emoji for streak decoration), reduced motion,
transparency, contrast, and (V1) home-card order. Everything else is fixed.

Dynamic colour (Material You) is **off**: it would let the wallpaper choose the
colour of a clinical value.

#### 12.4.1 The three accessibility options, mapped to existing values

They are preferences in Settings → Appearance rather than device-dependent branches,
because Android does not expose all three consistently; "Remove animations" is honoured
from the system as well.

- **High contrast.** `label-2` and `label-dim` take `label` and the optional `sep-op`
  hairline takes `sep`. No new value: the same tokens at full strength. Full-strength
  body copy in dark is the one place the high-contrast choice overrules §2.10, and only
  while the option is on.
- **Reduce transparency.** `barFill` becomes opaque `bg-el`, `toastFill` and
  `timerBarFill` become opaque `surface-2`. This is exactly what §4.3 asks of the tab bar.
- **Reduce motion.** Every motion token built on `OhMotion` resolves to its instant state
  (§6.9), which covers press, selection, charts and the timer bar; Material's own sheet
  animation is outside the token and is unchanged, and the setting's copy says so.

### 12.5 Contrast audit

The review gate (§14.5) checks, in both themes:

- `label-2` on `bg` and `surface` ≥ 4.5.
- `label-dim` ≥ 4.5.
- Every preset `acc-ink` ≥ 4.5 on `bg` and `surface`.
- `gold-ink`, `green-ink`, `orange-ink`, `red-ink`, `teal-ink`, `blue-ink` ≥ 4.5
  in both themes; `streak-ink` and `frost` ≥ 3 as graphics only.
- `on-acc` ≥ 4.5 on every preset's `acc` (all nine carry dark ink). `acc-2` is
  a graphic shade and needs 3:1, never text.
- The focus ring ≥ 3 against its surrounding fill.
- The adipose layer's band, composited over `surface`: dark `fat` at 55 % is
  3.96:1; light `fat` at full opacity is 3.79:1 on `surface` and 3.39:1 on `bg`
  (2026-11-24; at 55 % light measured 1.97:1 and was rejected).
- Text on a tinted fill is measured with the fill composited over an opaque base:
  `label` on `acc-soft`, on the 15 % Danger fill, on the toast and on the timer bar;
  `acc-ink` on the work timer bar; `label-2` on the bar (2026-11-23).
- `fat` (2026-11-22, re-valued 2026-11-23) as a graphic ≥ 3 on `bg`, `surface` and
  `surface-2`. Measured: dark `#F2C230` 12.5 on `bg`, 10.2 on `surface`, 8.3 on
  `surface-2`; light `#A87C00` 3.9 on `bg`, 4.4 on `surface`, 3.7 on `surface-2`.
  The light theme takes the same hue at the brightest value that clears 3:1, because
  `#F2C230` measures 1.5 on light `bg`.
- `fat` is close in hue to `orange` and `yellow`, so luminance cannot separate
  them (1.1 to 1.5). The separation is structural, not chromatic: the adipose
  layer is drawn only outside the muscle shapes, behind the 2.5 dp surface stroke
  (§9.1.1), and the legend names it.

No token ships before its measurement is in the PR.

### 12.6 Full token sheet

The canonical values live in Appendix A. A Compose theme object is the only
place they are declared; no screen hard-codes a hex, a radius or a text size.

---

## 13. Deliberate departures from openGym

Recorded so they cannot be mistaken for drift.

1. **Five tabs, raised centre Train (revised 2026-11-22).** The earlier six-tab
   flat bar is superseded. The five destinations are Today, Vitals, Train,
   Nutrition and Stats, and Train is openGym's raised disc. Settings moves to a
   global top-bar gear and quick add to a top-bar plus (§4.4). Decided, not
   provisional: a second floating disc would compete with the raised Train disc.

2. **Macro hues are the one multi-colour surface.** openGym spends colour only
   on the accent and status. A food diary needs protein/carbs/fat/fibre to be
   distinguishable at a glance, so those four named hues appear **inside the
   nutrition chart only**, with a legend and figures. Everywhere else, one
   accent.

3. **Nutrition rings and a readiness score are new primitives.** openGym has
   neither. Both are built from existing tokens: the ring uses the 8 dp track,
   the five-step ramp and `display` type; readiness uses the card + KPI grammar.
   No new palette, no new radius.

4. **Light is first-class from 1.0.** The earlier assumption that the first build
   was light-only is superseded: dark is canonical, light ships with it, and
   both are contrast-verified. A health app is opened at 03:00.

5. **No streak economy on the home screen.** openGym's streak, freezes, levels
   and monthly challenges are excellent engagement design for a gym. openHealth's
   contract forbids guilt-driven engagement (`pr.md` §2.7, §6.12), so progress
   is a trend, not a flame. A flame may appear inside a training view, not on
   Today.

6. **The body map is shared, the media is not.** MuscleMap's geometry is MIT and
   may be ported with its notice. openGym's exercise stills and animations are
   licensed to openGym from Gym visual and may not be reused in any form.
   openHealth's exercise and food media are ours or licensed (`pr.md` §12.2).

7. **No seasonal decoration at launch.** The mechanism may return in V1, off by
   default, never on a logging or clinical screen, and never in a report.

8. **Provenance is a first-class visual layer.** openGym shows where a number
   came from in a few places; openHealth shows it everywhere, in a sheet, because
   the product is a record (`pr.md` §2.2). This is the biggest visual addition
   and the reason some rows carry a source line.

9. **Clinical visuals are deliberately quiet.** openGym's fatigue view can go
   red. openHealth's reference ranges are green bands with a citation, and red
   exists only for a user-configured alert. A high-but-normal reading is not an
   emergency. The muscle recovery map (red → orange → green, §9.1) shows load,
   not a reading; it may appear on Today, Train and Stats, and never on a vital,
   a clinical card or a report.

10. **Weight-neutral mode can empty a card.** openGym never removes a metric.
    openHealth removes it, without a placeholder, because a blurred number is
    still a number.

---

## 14. Screens and the review gate

### 14.1 Screen inventory

Mapped to the information architecture in `pr.md` §5. Each root is built from
the §4.4 skeleton; each detail is a sheet or a pushed page.

| Root | Primary card | Sections | Signature visual |
|---|---|---|---|
| **Today** | Readiness + today's plan | Nutrition, steps, weight, net energy and goal ETA, due items, quick actions | Dynamic body model with recovery colours; two rings at most |
| **Vitals** | Weight trend + dynamic body model | Measurements, composition, photos, vitals, sleep, cycle | Measurement anchors; fat layer; photo slider |
| **Train** (raised centre) | Week strip + today's session | Gym, yoga, cardio and imported activities, programmes, routines, history, recovery map, programme coverage map | Body map on session and routine rows |
| **Nutrition** | Energy ring + macro bars | Meals as inset lists, micros, water, fasting | Barcode action, product thumbs |
| **Stats** | KPI band | Trends by domain, body and workout statistics, weekly review, reports | Line charts, heatmap, stat bars |
| **Settings** (top-bar gear, not a tab) | Profile row | Modules, devices, data (Sync, export, Trash, review), appearance, privacy | Grouped settings list, status inks |

### 14.2 Composition recipes

**Today (MVP, fixed order).**

The global top bar (§4.4) already carries the app name, the date, quick add and
settings, so the header no longer repeats the date or a settings button.

1. Header: "Good morning" 34/700 and one 15 `label-2` status line.
2. Connection banner, if any.
3. Body card (the hero): the dynamic body model (§9.1.1), front and back, at the
   §9.1 size, coloured by the recovery map; a chip row under it (Recovery · Fat
   layer); a 13 sp line naming the most recently trained muscle and its step. Tap
   a muscle for its sheet.
4. Readiness card: card header (moon glyph, "Readiness", score 28/600), three
   chips (sleep, HRV, resting HR), a 13 sp explanation line.
5. Week strip card: "This week", seven days, today in an accent disc, session
   count left; today's plan row with a 46 dp mini body map of the muscles the plan
   trains, plan name, and a **Start** primary button.
6. Energy card (`pr.md` §6.4): three figures in a KPI band (In, Out, Net, each a
   range where uncertain), one calorie ring (120 dp) with remaining kcal in the
   centre, four macro bars, water as a fifth row. Under it one 13 sp line: "At
   this pace: 14 Feb to 3 Mar" in `label-2`, opening the goal-ETA sheet with the
   what-if control. Absent in weight-neutral mode and before day 14 ("not enough
   data yet").
7. Steps card: one ring (96 dp) + active minutes and distance as a 13 sp line; a
   source line names the Garmin or phone source that won the day.
8. Weight card: `display` value, signed weekly delta in a status ink, 30-day
   sparkline, goal line.
9. Due items: inset list, 46 dp rows, one accent action.

One accent per screen still holds: the accent is **Start**, the ring is the only
other accent use, and the body card's colours are data hues, not accents.

**Train.** Week strip and today's plan card with **Start**. Then a segmented
control (Recovery / Plan) over one full-size body map pair: Recovery shows the
red, orange, green map (§9.1); Plan shows the programme coverage map in the accent
ramp with a sets-per-week figure per muscle. A routine row carries a 46 dp mini
body map of its trained muscles (accent ramp, shaded by share of working sets);
a programme row carries the combined map. The Train root also lists gym, yoga,
cardio and imported activities as one history with a type chip, rows with a 50 dp
thumbnail. A routine's detail repeats its map at full size above the exercise
list, each exercise tagged with the muscles it loads. A finished session opens a
summary: KPI band (duration, volume, sets), PR list, the muscles it loaded as a
mini map, a 13 sp "how it felt", and a note field. Every map has a table
alternative (muscle, sets, hours since load, step).

**Nutrition.** A date strip (seven days, not a calendar), an energy card (ring +
macros), then four meal sections. A meal row is a media row with kcal at the
end; a swipe offers delete with undo. The scan action is a 44 dp button in the
header, never a floating camera over the list.

**Vitals.** A lead card: current weight `display`, trend sentence, 90-day line,
goal line. Under it, the dynamic body model with measurement anchors (the
measurement silhouette of §9.3 drawn on the §9.1.1 figure; nine anchors: neck,
shoulders, chest, waist, hips, upper arm, forearm, thigh, calf, plus the
wrist/ankle frame sizes in the list only) and a 2-column tile
grid (body fat, lean mass, waist, resting HR) with source lines. Then photos
(3-across) and vitals as a grouped list. Sleep and cycle are sections when the
modules are on.

**Stats.** Segmented control (Overview / Body / Training / Sleep / Nutrition), a
KPI band, then one card per chart. Every card has a "how this is calculated"
row at the bottom, 13 sp `label-2`, opening an explanation sheet with the
formula and its inputs (`pr.md` §6.4).

**Settings** (opened by the top-bar gear; formerly More). Profile row (avatar, name, goal), then groups: Modules, Devices and
integrations, Data and backups, Appearance, Privacy and security, About. A
destructive action is its own group at the bottom, 22 dp away, in `red-ink`.

**Settings → Data → Sync** (pushed from the Data sheet, not a root). One page in the
Settings surface language: the page header, then the server row and either the pairing
form (URL and one-time code) or the paired block (server, status, waiting count, last
synced), with one primary action (**Pair this device** or **Sync now**) and a secondary
**Disconnect**. A failed sync is stated in `orange-ink` under the block ("Offline",
"Needs attention", the reason), never red (§6.5). The conflicts section is a
`label-2` heading followed by one card per conflict: the entity title, a 13 sp
`label-2` sentence, the two sides side by side (device, time, differing fields) and two
buttons that name the value they keep ("Keep 8 412" / "Keep 9 103"). Every row is a
46 dp target; the empty state is the §6.7 pattern ("Nothing to review"). No server is
required for any other screen.

**Devices and integrations** (pushed from the Settings row, not a root). One scrolling
page in the Settings surface language: a page header with a back chevron, then six
groups separated by 22 dp, each introduced by a 13/600 `label-2` title that is a
heading to the screen reader.

1. **Steps.** A card, not an inset group, because one number leads it: the title row
   carries an On/Off word in `label-2`, the day's total is the `display` role, and under
   it the winning source sits on the single line of accent this page is allowed
   (`Counted by …`). Every losing source follows in `label-2`, one 2 dp-spaced line
   each, with its own figure — a loser is named and kept, never summed in and never
   hidden (`pr.md` §7.6). The last reading's time closes the block. Two small buttons
   10 dp below: Start/Stop counting (secondary) and Correct today (ghost). A footnote
   says what the permission is for, or that the phone has no counter.
2. **Health Connect.** An inset group: a status row (granted of total, or off, or not
   on this phone), a last-sync row, one row per data type with a granted tick, then
   Connect and Import my history. A footnote names what Garmin publishes to the bus and
   what stays in Garmin, and says the app only ever reads.
3. **Garmin files.** An inset group with the last import, its summary line, where the
   steps in that file came from (a day's monitoring or the workout) and an Import a FIT
   file row that opens the system picker.
4. **Bluetooth.** An inset group: Scan (secondary, disabled with a reason in `label`
   when the radio is off), the paired devices with what each provides and whether the
   link is idle, connecting, reconnecting or connected, then anything the scan found.
5. **Connect IQ watch.** An inset group with a closed-by-default switch and one
   `footnote` line: it advertises a local Bluetooth link only while a workout is open,
   and the Garmin app keeps its sets on the watch when the link is away. When nearby
   devices are not allowed, the switch is disabled and the line says what to allow;
   the row never spends the accent.
6. **Which source wins.** An inset group of the sources that report the type, in
   priority order: a rank number in a 12 dp leading slot, the source's name, a plain
   human description beneath it (never an internal family key), and two 44 dp chevron
   buttons that move the row up or down. Moving a row rewrites the order and rebuilds
   the affected days, so the Steps card above re-resolves in place.

Correct today is a modal sheet with a number field, a Save and a quiet "Leave today out"
that excludes the day without deleting it. No colour, type role, spacing value,
component or icon beyond Appendix A appears on this page; the accent is spent once, on
the winning source.

**Global: quick add.** Opens from the top-bar plus (§4.4). A 3 × 2 grid of 84 dp tiles
(`surface`, radius 14, 21 dp accent glyph over a 13/500 label), then "Recent" as
46 dp rows with the source line, and a "Backdate" row at the bottom. A recent
item logs in one tap and leaves an undo toast; everything else opens its own
small sheet. Backdating is a date/time row, never a hidden gesture.

**Global: timeline.** Grouped by local day; the day header is 13/600 `label-2`,
22 dp above its group. Each event is a 60 dp row: 29 dp module tile, title,
subtitle with source, trailing value. Tap opens that record; long-press or swipe
offers delete with undo. A filter chip row (module, source, manual/imported)
sits under the header. Nothing the app stores may be missing from it
(`pr.md` §6.27).

**Global: onboarding.** One question per screen: 34/700 question, two to four
full-width inset rows as answers, a quiet "Skip" always visible, and a 3 dp
accent progress line under the header (no dots, no percentages). Units and
locale are pre-detected and shown as the default answer. "Try with sample data"
is a row, not a modal. The whole flow must survive being completed in under
three minutes with one hand. The tiers are `pr.md` §6.1's: tier 1 (about a
minute) is required, tier 2 recommended, tier 3 (body fat %, tape measurements,
resting heart rate, blood pressure, injuries and the rest) is skippable and
asked once, later, where it is used. A "Fill in later" row ends each tier. After
onboarding every launch shows the system splash (one centred mark on `bg`, no
animation under Reduce Motion) and then Today.

**Live session.** A focused surface with no tab bar: close chevron + plan name +
elapsed on the left, **Finish** primary on the right. A chip row of sets with a
30 dp mini body map at its end. The exercise block: 20/600 name, 50 dp media
thumb, equipment/body-part tags, "Plan 4 × 10", last time, and the progression
line in `acc-ink`. The set table is the busiest control in the app:

| Column | Width | Notes |
|---|---|---|
| Set | 34 dp | Warm-ups as W1…, work sets numbered; PR dot in `gold-ink`. |
| Weight | flex | 44 dp field, tabular, unit in the header. |
| Reps | flex | 44 dp field, tabular. |
| RIR | 56 dp | Opens the effort sheet; never a free-text keyboard. |
| Done | 44 dp | 38 dp circle, `acc` when done; one tap advances. |

The active row is `surface-2` with a 1.5 dp `acc` leading edge; future rows are
`label-3`; completed rows keep `label`. The rest timer docks at the bottom.

**Vitals detail.** KPI band (latest, average, count), the cited range strip
(§8.2.1), a 30-day line, context chips (arm, position, resting, post-meal), the
reading list with provenance, and **Add reading** as the single primary. The
home-BP protocol is a sheet that walks 2 readings × morning/evening × 7 days.

**Coach.** Off by default. A proposal is a **diff card**, never a chat bubble:
29 dp glyph, "Proposed change" eyebrow, rows of `before → after` at 17/400,
the rationale as a 13 sp link, and **Accept** / **Discard**. The conversation
is a list of proposals and notes, with no avatars, no typing dots and no accent
bubble. The first send shows what leaves the phone, by category, before it
leaves (`pr.md` §6.15).

**Search.** A sheet with a sticky field, recent items first, results grouped by
type with a source chip, and a 13 sp count. Keyboard opens immediately; results
never shift after the first keystroke.

**Reports.** Always light (§8.7). The in-app preview uses a white sheet inside
the dark app, letterboxed, with the export actions below it.

### 14.3 The logging loop

The most important four screens, and their interaction budget:

| Action | Budget | Design |
|---|---|---|
| Log a set | < 2 s | Weight and reps pre-filled from the plan; one 38 dp tick per set; rest timer starts automatically; the next target line is above the row. |
| Log a food | < 5 s | Search or scan → portion sheet with a stepper and recent portions → Log. The meal is chosen by the time of day, editable in one tap. |
| Log weight | 3 taps, < 5 s | The weight card repeats the last value in one tap; otherwise card → stepper (0.1) → Save. The trend updates in place. |
| Log water | 1 tap | A quick-action chip logs the last container; the quick-add sheet exposes the other sizes. |

### 14.4 Deltas from openGym's screens

- Five tabs with Train as the raised centre, as openGym, but Today, Vitals, Nutrition and Stats replace its other destinations, and a global top bar carries quick add and Settings (§4.4, §5.15, §13.1).
- No streak/level/challenge cards on the home.
- No exercise media from openGym; use our own.
- The muscle recovery map (red, orange, green) is allowed on Today, Train and Stats (§9.1, §13.9); it is never on a vital, a clinical card or a report.
- The previous mockup canvas and its downloaded `openHealth App Mockups.html`
  are superseded by this document and must not be used as a visual reference.
- Keep: the large title, the week strip, the plan row with a mini body map, the
  card header, the inset list, the sheet grammar, the stat bars, the body map,
  the calm motion.

### 14.5 The review gate

A screen is not done until all of these pass:

- [ ] Every colour comes from §2; no hex in a screen file.
- [ ] Every size comes from §3 or §4; no 13.5 sp, no 15 dp padding.
- [ ] One primary action; one primary filled accent element per card (small data
      marks excepted).
- [ ] Every row is a single accessibility node unless it contains a real
      secondary control.
- [ ] 44 dp targets, verified in the largest font scale.
- [ ] Both themes, high contrast and reduced motion checked.
- [ ] Colour-blind simulation checked for every status and every chart.
- [ ] Every number opens a provenance sheet.
- [ ] Every destructive action has an undo.
- [ ] Every list has an empty state that says what to do.
- [ ] Every chart has a table alternative and a one-sentence description.
- [ ] Every clinical value has its unit, its source and, if a range is shown, its
      citation.
- [ ] No wording that diagnoses, warns passively, or shames (`pr.md` §12.1,
      §6.25).
- [ ] Weight-neutral mode checked if the screen touches weight, calories, body
      fat or photos.
- [ ] RTL checked; no physical `left`/`right`.
- [ ] The screen's reason to exist is legible in three seconds.

### 14.6 Wear OS companion

The watch screen (`pr.md` §6.18; `docs/adr/0019`) is a remote for an open session, not a
second app. It uses the same `OhTheme` tokens and no new colour, type role, spacing value
or component.

- **One screen, one column.** Round-safe: 16 dp side padding and a 40 dp top reserve for
  the system time (32 + 8 from the existing token set, so the curved clock clears at
  200 %); everything scrolls at 200 % text. Exercise `body` 17/600, set line
  `caption2` 11, the selected weight `title3` 20 tabular with the other value `caption2`,
  rest `timer` 28 tabular, labels `caption2` 11. There is no second title line: the
  exercise is the title.
- **One accent.** The accent is the Log set button and the rest bar's fill. Weight and
  reps share one selector row: a 44 dp neutral `−`/`+` pair and two stacked values, the
  selected one in `label`, the other in `label-3`; tapping the values switches which one
  steps. This keeps Log set on the first screen of a 454 px watch; the rest and
  heart-rate lines follow below and scroll.
- **States.** No session: a `title3` "Workout", "Start a workout on your phone" as
  `footnote`, and, only when disconnected, a plain connection line. Session: exercise name, "Set 2 of 3 · Exercise 1
  of 1", the selector, Log set, rest clock with +15 s and Skip, and a `caption`
  heart-rate line when the sensor is allowed. A queued command says "N waiting for the
  phone"; disconnected says the sets stay on the watch. An ended session returns to the
  empty state.
- **Never here.** No body map, no chart, no calorie or weight goal, no clinical value, no
  destructive action. Ending a workout is not destructive and has no confirm dialog.
- **Connection.** "Waiting for your phone" is `caption-2` in `label-3`; no warning
  triangle, no red and no "disconnected" blame.
- **Connect IQ variant.** The Garmin app is a reduced remote for the same session. A
  Connect IQ characteristic carries 20 bytes, so its packet has no exercise name and no
  routine picker: it draws the set line, weight, reps, rest and heart rate, with the accent
  on the set figure and the rest bar, and never a second accent. It starts nothing; a
  workout is started on the phone. This is a deliberate departure from the full Watch
  anatomy because the Garmin SDK and developer key are the owner's and the app is
  source-only until they exist (`docs/adr/0019`).
- **Both themes and RTL.** Dark is canonical; light is the same tokens. The screen mirrors
  in RTL, but the clock and its figures stay left-to-right (`§11.5`).

---

## Appendix A — token reference

### A.1 Dark (canonical)

```text
bg            #000000
bg-el         #0E0E10
surface       #1C1C1E
surface-2     #2C2C2E
surface-3     #3A3A3C
surface-raised#2C2C2E
raised-line   rgba(255,255,255,.09)

label         #FFFFFF
label-2       rgba(235,235,245,.60)
label-3       rgba(235,235,245,.32)
label-4       rgba(235,235,245,.18)
label-dim     rgba(235,235,245,.55)

sep           rgba(84,84,88,.60)
sep-op        rgba(84,84,88,.34)
hair          0.5 dp

acc           #A3E635
acc-2         #84CC16
on-acc        #0C0E12
acc-ink       #A3E635
acc-soft      acc 16%
acc-line      acc 38%

fat           #F2C230   (added 2026-11-22, re-valued 2026-11-23; body model + body-fat series only)
rec-ready     = green   #30D158
rec-easing    = orange  #FF9F0A
rec-active    = red     #FF453A   (label "Recovering")
```

### A.2 Light

```text
bg            #F2F2F7
bg-el         #F7F7FA
surface       #FFFFFF
surface-2     #ECECEF
surface-3     #E3E3E8
surface-raised#FFFFFF
raised-line   rgba(60,60,67,.10)

label         #000000
label-2       rgba(60,60,67,.75)
label-3       rgba(60,60,67,.30)
label-4       rgba(60,60,67,.16)
label-dim     = label-2

sep           rgba(60,60,67,.29)
sep-op        rgba(60,60,67,.20)

acc           #84CC16
acc-2         #65A30D
on-acc        #0C0E12
acc-ink       #4A7A0E

fat           #A87C00   (2026-11-23: same hue, brightest value clearing 3:1 in light)
rec-ready     = green   #34C759
rec-easing    = orange  #FF9500
rec-active    = red     #FF3B30
```

### A.3 Type

```text
caption2 11 / 1.33 / 0
caption  12 / 1.33 / 0
footnote 13 / 1.38 / -0.004em
subhead  15 / 1.33 / -0.010em
callout  16 / 1.29 / -0.010em
body     17 / 1.29 / -0.010em
title3   20 / 1.20 / -0.021em
title2   22 / 1.20 / -0.021em
large    34 / 1.06 / -0.028em   (30 below 420 dp)
display  28 / 1.10 / -0.026em   (fixed-box figure)
timer    28-64                  (fixed-box figure)
```

### A.4 Geometry

```text
space   2 4 6 8 10 12 16 22 32
radius  8 10 12 14 16 22 (full)
stroke  1.7 icons (1.65 tab, 2.0 active, 2.4 chevron)
hair    0.5
target  44
column  560 max
pad     16 screen, 16 card, 12/16 row
motion  fast 140 / med 220 / push 380 / pop 340 / sheet 340 / dialog 280
ease    cubic-bezier(.32,.72,0,1)
press   .97 / .92 small / .985 card
quick   84 tile / 21 glyph   (§14.2, B3; no new token, quoted here for the guard)
body    pair gap 6 · stroke 2.5 · selected 7 + 2 glow · mini 46 (event 30) tall,
        3 stroke, 2 gap · hatch 1.5 / 7 at 35 % · fat 55 %   (§9.1, §9.1.1, B4)
anchor  measured 22 dp glow at 30 % · 8 dp dot   (§9.3, B7)
chart   height 160 · axis 46 · scale label 64 · line 2.5 (2 second) · grid 1 · goal 1.5
        · point 2.5 (last 4) · scrub 7/5 · crosshair 1 · legend 8 · dash 7/4
        · range strip 16 box / 10 track / 3 tick   (§8.1, §8.2.1, B7)
photo   compare 420 max · handle 2 · grip 12 · grip dot 3 at offset 4, stroke 1.5
        · date pad 2 · blur 18 · grid gap 10   (§9.4, B7)
```

---

## Appendix B — Compose mapping notes

- One theme object (`OhTheme`) exposes the tokens as `CompositionLocal`s:
  `OhColors`, `OhType`, `OhSpace`, `OhRadius`, `OhMotion`. Screens never import
  Material colours. Material 3 is used for behaviour (insets, semantics,
  ripple-less indication), not for its palette or shapes.
- Do not enable dynamic colour. Do not use `MaterialTheme.colorScheme` defaults.
- `Typography` carries the 11 roles; `display` and `timer` are explicit
  `TextStyle`s in `OhType`, not extra Material roles.
- Hairlines: `Modifier.drawBehind { drawLine(..., strokeWidth = 0.5.dp.toPx()) }`.
- Press scale: `Modifier.graphicsLayer` driven by `interactionSource`, with
  `animateFloatAsState` at 140 ms and the `ease` curve; press feedback on
  `PressInteraction.Press`, never on release.
- Sheets: `ModalBottomSheet` with `containerColor = OhColors.bgEl`,
  `shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)`,
  `windowInsets = WindowInsets(0)`, and the keyboard insets handled explicitly
  (§4.3). The drag handle is ours (36 × 5 dp, `label-4`).
- State: values are stored at full precision; formatting lives in one
  `format` module with a locale and a unit parameter, so no screen formats a
  number by hand.
- Charts are custom `Canvas` composables (no chart dependency): the line chart
  needs a 44 dp scrub area, a tooltip on a raised surface, and a table
  alternative, which no general chart library gives cheaply.
- The body map is an `Svg`-free path renderer: paths are parsed once at startup
  of the map screen, cached, and drawn with `Path`. It is loaded lazily, keeps
  its height while loading, and never blocks the first frame.
- All strings go through the resource system; no string is built by
  concatenation. Plurals use `pluralStringResource`.

---

## Appendix C — where the pieces come from

| Piece | Source | Licence / status |
|---|---|---|
| Surface ramp, text ramp, hairlines, type scale, motion | openGym 1.4.5 | Studied; values re-specified here. No AGPL code copied. |
| Body geometry (four views) | MuscleMap, Melih Colpan, via openGym | **MIT** — reusable with the notice retained. This is the only image asset we may take from openGym. |
| Body map rendering, ramp and interaction | This document | Ours. |
| Exercise stills/animations (openGym) | Gym visual | **Not reusable.** Own or licensed media only. |
| Icons | This product | Drawn for openHealth; not copied from openGym's set. |
| System palette values | Platform HIGs | Calibrated values, borrowed as colour constants. |
| Exercise names/instructions | openGym catalogue / ExerciseDB | Check `pr.md` §12.2 and the upstream licence before importing any text. |

---

*This document is the appearance contract. When a screen is ambiguous, the
principles in section 1 decide. When a value is missing, it is added here with a
date before it is used in code.*
