# openHealth — Wireframe Specifications

**Version:** 0.1  
**Date:** 2026-11-21  
**Reference:** DESIGN.md §14, pr.md §5–6

This document details the wireframe layout, component hierarchy, and interaction patterns for openHealth's six root screens and key detail sheets.

---

## Overview: Information Architecture

The app uses a **six-tab bottom navigation** (Today, Train, Food, Body, Insights, More) with each tab as a root screen containing scrollable content cards. A floating accent disc above the trailing tab (More) surfaces the quick-add sheet. All detail opens over the current screen in a dismissible bottom sheet or pushed page; the back gesture closes it and preserves scroll position.

| Screen | Purpose | Primary Card | Signature Visual |
|--------|---------|--------------|------------------|
| **Today** | Readiness + plan | Readiness score + week strip | Mini body map on plan row |
| **Train** | Workouts + programmes | Week strip + session | Body map on routine rows |
| **Food** | Meals + macros | Energy ring + bars | Meal rows with product thumbs |
| **Body** | Measurements + vitals | Weight trend + silhouette | 7 measurement anchors |
| **Insights** | Trends + analysis | KPI band | Charts, heatmap, body map |
| **More** | Settings + profile | Profile row | Grouped settings list |

---

## Screen-by-Screen Detail

### 1. TODAY

**Purpose:** Morning readiness, today's plan, quick snapshot of key metrics.

**Layout (MVP, fixed order):**

1. **Large header** (34 sp / 700)  
   "Good morning", current time, one status line (e.g., "Syncing", "2 items due").  
   Trailing: settings gear (36 dp).

2. **Connection banner** (if any)  
   Orange/gold bg, dismissible, signals sync paused or pending data review.  
   Shows on Today and More only; count badge on More.

3. **Readiness card** (120 dp / surface)  
   - Header: moon glyph, "Readiness", score (28 sp / 600)
   - Three chips: sleep duration, HRV, resting HR (each in accent or neutral)
   - 13 sp explanation ("You're ready for a hard session today" or similar)
   - Tap opens readiness sheet with inputs

4. **Week strip + plan** (surface)  
   - 7-day bubble strip (32 × 40 dp each), today in accent disc, session count trailing
   - Today's plan row: 46 dp mini body map (accent fill), plan name, time, **Start** primary button
   - If no session: "Add a plan" text, secondary button

5. **Nutrition card** (surface)  
   - 120 dp ring (remaining calories in centre, e.g., "1240 / 2200")
   - Four macro bars: protein, carbs, fat, fibre (each with label, value, bar)
   - Fifth row: water (13 sp label + value, no bar)
   - Tap card opens Food screen

6. **Steps card** (surface)  
   - 96 dp ring with step count
   - 13 sp subtitle: distance + active minutes (e.g., "12.4 km · 156 active min")
   - Tap opens Body screen

7. **Weight card** (surface)  
   - Large value (36 sp / 700, e.g., "75.2 kg")
   - Signed weekly delta in status ink (gold if up, green if down, e.g., "+1.1 kg")
   - 30-day sparkline with goal line
   - Tap opens weight detail sheet (stepper, save)

8. **Due items** (surface, if any)  
   - Inset list, 46 dp rows (task name, due time, source chip)
   - One accent action per row (complete, reschedule)
   - Tap opens detail sheet

9. **Quick actions** (surface)  
   - 3 × 2 grid of 84 dp tiles (Food, Water, Weight, Set, Vitals, Note)
   - Each tile: 21 dp accent glyph, 13 sp / 500 label
   - Tap opens quick-add sheet or logs immediately (Water re-uses last container)

**Interactions:**
- Scroll: infinite, card-to-card
- Pull-to-refresh: syncs data, shows "Syncing…" then slides out
- Swipe right on notification row: dismiss with undo toast
- Long-press card: copy value (e.g., weight to clipboard)

---

### 2. TRAIN

**Purpose:** Workouts, programmes, session history, training metrics.

**Layout:**

1. **Large header** (34 sp / 700)  
   "Training", week range, one status line (e.g., "3 sessions this week").

2. **Week strip** (surface)  
   7-day bubbles, today in accent disc, session count trailing.

3. **Today's plan** (surface)  
   Single row with 46 dp mini body map, plan name, time, chevron.  
   If completed: summary overlay with badges.

4. **Active programmes** (surface)  
   - Inset list of active programmes
   - Each row: programme name, week X of Y, chevron
   - Tap opens programme detail sheet

5. **Recent sessions** (surface)  
   - 50 dp thumbnail + session name + time + set count
   - Swipe left: delete with undo
   - Tap: opens session summary (KPI band, PR list, notes, rate-effort)

6. **This month stats** (surface, 2 × 2 grid)  
   - Total sessions
   - Total volume (tonnes)
   - Avg duration
   - Avg effort (1–10 scale)

7. **PRs / Milestones** (surface, if any)  
   - Row per new PR, name + old → new weight, date, accent highlight

**Interactions:**
- Swipe left on session row: delete with undo toast
- Tap **Start** on plan: navigates to Live session screen (no tab bar, full focus)
- Tap session: opens summary sheet
- Pull-to-refresh: re-syncs from connected device

---

### 3. FOOD

**Purpose:** Meal logging, macronutrient tracking, barcode scan.

**Layout:**

1. **Large header** (34 sp / 700)  
   "Nutrition", current date.  
   Trailing: scan camera button (44 dp, in header, never floating).

2. **Date strip** (surface)  
   7-day bubble strip (not calendar), allows swipe to other days.

3. **Energy card** (surface)  
   - 120 dp ring (remaining calories in centre)
   - Four macro bars: protein, carbs, fat, fibre
   - Tap: opens macro detail sheet

4. **Meal sections** (surface, one per meal type)  
   - Breakfast, Lunch, Dinner, Snacks
   - Each section header: "Breakfast" (13 sp / 600)
   - Meal rows: 44 dp product image, meal name, time, calorie value
   - Swipe left: delete with undo
   - Tap: opens meal edit sheet (portion stepper, save)

5. **Water** (inset, within Nutrition card or separate row)  
   - Row: "Water", value (e.g., "2.4 L"), secondary button "Add"

6. **Add meal** (button)  
   - Primary button, full-width, "Add meal"
   - Opens quick-add sheet (search, scan, backdate)

**Interactions:**
- Tap date bubble: jump to that day
- Swipe left on meal row: delete with undo toast
- Tap meal row: edit portion/time
- Tap scan: camera opens, reads barcode, pre-fills product search
- Tap search result: portion sheet (stepper for quantity, unit picker), log
- Pull-to-refresh: re-syncs from connected devices

---

### 4. BODY

**Purpose:** Measurements, composition, photos, vitals, sleep/cycle (when enabled).

**Layout:**

1. **Large header** (34 sp / 700)  
   "Body", subtitle (e.g., "Your measurements & vitals").

2. **Weight trend card** (surface)  
   - Large value (36 sp / 700, e.g., "75.2 kg")
   - Trend sentence (13 sp, e.g., "+1.1 kg this week · Goal: 73 kg")
   - 90-day line chart with goal line
   - Tap: opens weight detail sheet

3. **Silhouette + measurements** (surface)  
   - Silhouette glyph (48 sp)
   - Seven inset rows: Neck, Waist, Hip, Chest, Arm, Thigh, Calf
   - Each row: label (13 sp), value (15 sp / 600, accent if PR)
   - Tap row: opens edit sheet (stepper)
   - Swipe left: delete with undo

4. **Composition tile grid** (2 × 2, surface)  
   - Body fat %, lean mass (kg), [reserved for future fields]
   - Each tile: label (11 sp), value (20 sp / 600)
   - Tap tile: opens detail sheet (trend, cite range)

5. **Photos section** (surface, if photos logged)  
   - 3-across grid, 120 dp thumbnails, date label underneath
   - Tap: opens photo detail sheet (date, body metrics on day, notes)

6. **Vitals section** (surface, if module enabled)  
   - Grouped list: HR, blood pressure, temperature, etc.
   - Each row: vital name, latest value, chevron
   - Tap: opens vitals detail sheet (trend, range, readings)

7. **Sleep section** (surface, if module enabled)  
   - Duration, bedtime, wake time, quality score
   - Tap: opens sleep detail sheet (stages chart, notes)

8. **Cycle section** (surface, if logged)  
   - Current phase, days until next event, notes
   - Tap: opens cycle detail sheet

**Interactions:**
- Scroll: card-to-card
- Tap measurement row: stepper sheet (0.1 cm increment), save
- Tap photo thumbnail: full-screen carousel, delete with undo
- Tap vital: trend sheet (30-day line, range citation)
- Pull-to-refresh: re-syncs health kit / connected devices

---

### 5. INSIGHTS

**Purpose:** Trends, analytics, weekly review, reports.

**Layout:**

1. **Large header** (34 sp / 700)  
   "Insights", week range.

2. **Segmented control** (surface-2, radius 8, 4 dp padding)  
   Five tabs: Overview, Training, Sleep, Body, Food.  
   Active tab: surface bg, label text; inactive: label-3 text.

3. **KPI band** (three tiles in a row, surface)  
   - Metric 1 (e.g., "Total volume"), value (20 sp / 600)
   - Metric 2 (e.g., "Avg effort"), value
   - Metric 3 (e.g., "Sessions"), value

4. **Chart cards** (one per metric, surface)  
   - Card header: metric name (15 sp / 600), "how this is calculated" chevron
   - Chart placeholder (11 dp cells for heatmap, smooth line for trend, bars for distribution)
   - Card footer: 13 sp "how this is calculated" link
   - Tap link: opens explanation sheet (formula, inputs, citation)

5. **Chart types by tab:**
   - **Overview:** volume trend (line), training heatmap (11 dp cells), muscle balance (body map), top lifts (bars)
   - **Training:** volume trend, consistency heatmap, effort distribution, top 3 exercises, PRs
   - **Sleep:** duration trend, quality distribution, bedtime range, REM/deep percentage
   - **Body:** weight trend, body fat trend, measurement trends, composition change
   - **Food:** calorie trend, macro distribution, meal frequency, nutrient adequacy

**Interactions:**
- Tap segmented tab: animates to that tab's data (140–220 ms)
- Tap KPI value: detail sheet (absolute, relative to goal, citation)
- Tap "how this is calculated": explanation sheet (formula, inputs, toggles for segment/filter)
- Scroll: card-to-card, charts scroll independently (do not pan the page)
- Pull-to-refresh: re-computes trends, shows "Calculating…"

---

### 6. MORE

**Purpose:** Settings, profile, modules, devices, data, appearance, privacy.

**Layout:**

1. **Profile row** (surface, 56 dp avatar, inset flex)  
   - Avatar (56 dp circle, accent bg with glyph or image)
   - Name (15 sp / 600), goal (12 sp / label-2)
   - Chevron (trailing)
   - Tap: opens profile edit sheet

2. **Modules group** (surface)  
   - Section header "Modules" (11 sp / 600 / label-3, uppercase)
   - Inset list: Training ✓, Nutrition ✓, Vitals ✓, Sleep ○, Cycle ○, Recovery ○
   - Each row: 32 dp icon bg (surface-2), title (15 sp / 600), status glyph (✓ / ○)
   - Tap row: toggle sheet (why disable, warning if data will be hidden)

3. **Devices group** (surface)  
   - Section header "Devices & integrations"
   - Inset list of connected devices (e.g., "Galaxy Watch 7", "Oura Ring")
   - Each row: icon, device name, status ("Connected · Last sync 2m ago"), chevron
   - Tap: device detail sheet (last sync, permissions, disconnect)

4. **Data group** (surface)  
   - Section header "Data & backups"
   - Backups row: last backup time, chevron
   - Data review row: count badge (if pending), chevron
   - Tap Backups: backup management sheet (auto-backup toggle, manual backup, download, cloud destination)
   - Tap Data review: review inbox (§6.24 pr.md)

5. **Appearance group** (surface)  
   - Accent colour row: current colour chip (20 dp circle), "Lime", chevron
   - Theme row: "Dark" (or "Light"), chevron
   - Tap Accent: colour picker sheet (9 presets + custom with readability guardrails)
   - Tap Theme: toggle sheet (Dark / Light)

6. **Privacy & security group** (surface)  
   - Security row: passphrase status, chevron
   - Privacy policy row: version, chevron
   - Terms row: version, chevron
   - Tap: respective detail sheets

7. **About group** (surface)  
   - Version row: app version + build number
   - Feedback row: email link
   - Open source row: licence list, chevron

8. **Destructive action** (red-ink, bottom, 22 dp spacer above)  
   - Single row in its own group: "Delete all data"
   - Tap: confirmation dialog (irreversible, explain what's deleted)

**Interactions:**
- Tap profile row: profile edit sheet
- Tap module toggle: enable/disable sheet
- Tap device row: device detail sheet (permissions, last sync, disconnect)
- Tap Backups: backup management
- Tap Accent: colour picker (9 presets or custom with live readability feedback)
- Tap Theme: toggle with immediate preview
- Scroll: inset list stays in place, section headers sticky
- Pull-to-refresh: re-checks device connection status

---

## Global Patterns

### Quick Add (Sheet, MVP)

**Trigger:** Accent disc floating above trailing tab, or Today → Quick actions chips, or food scan.

**Layout:**

1. **Header:** "Quick add", close chevron (trailing).

2. **Grid section** (3 × 2, 84 dp tiles)  
   - Food, Water, Weight, Set, Vitals, Note
   - Each: 21 dp accent glyph, 13 sp / 500 label
   - Tap: opens item-specific sheet or logs immediately

3. **Recent section** (inset list)  
   - "Recent" header (13 sp / 600 / label-2)
   - Up to 5 rows: source icon, item name, time, source chip
   - Tap: logs in one tap, shows undo toast

4. **Backdate row** (13 sp / 600 / label-2, inset)  
   - "Backdate" label, chevron
   - Tap: date/time picker sheet

5. **Footer:** "Open Food app", "Browse exercises", etc. (secondary buttons, if relevant)

**Interactions:**
- Tap tile: opens item-specific sheet (portion stepper, weight input, etc.)
- Tap recent item: logs immediately, shows undo toast (3 s, tap to undo)
- Tap Backdate: date/time sheet, return to Quick Add after selection

---

### Live Session (Full-screen, no tab bar)

**Trigger:** Tap **Start** on a plan (Today or Train).

**Layout:**

1. **Header** (surface-2, 44 dp height)  
   - Left: close chevron
   - Centre: plan name (20 sp / 600), elapsed timer (15 sp / 500)
   - Right: **Finish** primary button

2. **Exercise blocks** (card per exercise)  
   - 50 dp media thumbnail
   - 20 sp / 600 exercise name
   - Equipment + body-part tags (chips, neutral)
   - "Plan 4 × 10 reps" (13 sp / label-2)
   - Last time (13 sp / label-2)
   - Progression line (accent, 2 dp, e.g., "+5 kg from last week")

3. **Set table** (the busiest control)  
   - Columns: Set | Weight | Reps | RIR | Done
   - Set: 34 dp (W1, W2, 1, 2, 3…; PR dot in gold-ink)
   - Weight: flex, 44 dp field, tabular font, unit in header
   - Reps: flex, 44 dp field, tabular font
   - RIR: 56 dp (opens effort picker; never free-text)
   - Done: 44 dp circle (38 dp), taps to toggle and advance row
   - Active row: surface-2 bg, 1.5 dp accent leading edge
   - Future rows: label-3 text
   - Completed rows: label text
   - Tap row: becomes active, fields focusable

4. **Rest timer** (docked at bottom, surface-2)  
   - Large countdown (28 sp / 600), "Rest"
   - Circular progress ring (accent)
   - Start / pause / skip buttons

5. **Chip row** (session context, sticky below header)  
   - Set chips (W1, W2, 1, 2, 3…) in horizontal scroll
   - 30 dp mini body map at the end (accent fill, muscles trained)
   - Tap chip: jumps to that set's row

**Interactions:**
- Tap weight/reps field: numpad opens, pre-filled from plan or last set
- Tab between fields: numpad stays open
- Tap RIR: effort picker sheet (1–10 scale, glyph + word)
- Tap Done circle: marks complete, accent fill, advances to next set, starts rest timer
- Tap rest timer: snooze (+30 s), skip, or lap
- Swipe left on completed set: undo (re-opens row for edit)
- Pull down: close session (ask "Save and finish?")

---

### Provenance Sheet (MVP, all numbers)

**Trigger:** Tap any value that is logged or calculated (weight, calorie, vital, metric).

**Layout:**

1. **Header:** Value (large), unit, date + time.

2. **Source section** (inset rows)  
   - Source: "Oura Ring", "Manual entry", etc.
   - Recorded at: timestamp with timezone
   - Happened at: timestamp (if different, e.g., earlier sleep data)
   - Method: "Weight scale", "Estimation formula", etc.
   - Device: "Oura Ring v3", "Samsung Galaxy Watch 7"
   - Edited: if yes, show by whom and when (locally only pre-cloud)

3. **Raw data** (if applicable)  
   - For calculated values: input rows (e.g., calories = weight × time × intensity)
   - For daily rollups: "Built from 240 observations between 00:00 and 23:59"

4. **Actions**  
   - Edit button: opens edit sheet (re-enter value, change time/source)
   - Delete button: removes from record, shows undo toast
   - Cite button: opens citation sheet (formula, reference, warning if clinical)

**Interactions:**
- Scroll: inset list, sticky header
- Tap Edit: form sheet (stepper, date/time picker)
- Tap Delete: confirmation, undo toast (5 s)
- Tap Cite: citation sheet (formula, reference DOI, warning if extrapolated)

---

### Explanation Sheet (Insights & Vitals, V1)

**Trigger:** Tap "how this is calculated" link on chart or vital.

**Layout:**

1. **Header:** Metric name (20 sp / 600), close chevron.

2. **Formula section** (13 sp / 400 body text)  
   - Equation (e.g., "Total Volume = ∑(weight × reps) per session")
   - Inputs: list of data points, toggles to include/exclude
   - Frequency: "Recalculated daily at midnight"

3. **Range section** (if applicable, e.g., vitals)  
   - Cited range (e.g., "Resting heart rate: 60–100 bpm (AHA 2015)")
   - Citation link (DOI, opens to paper or info sheet)
   - User's data: "Your average: 62 bpm (within range)"

4. **History** (small chart, e.g., last 90 days of that metric)

5. **Footer:** "Data source", "Updated", "Learn more" link.

**Interactions:**
- Scroll: inset content
- Toggle input: immediately recalculates and updates chart
- Tap citation: external link or in-app info sheet

---

## Review Gate (§14.5 DESIGN.md)

Every screen must pass before ship:

- [ ] Every colour from §2 (DESIGN.md); no hardcoded hex
- [ ] Every size from §3–4; no arbitrary padding
- [ ] One primary action per screen; one primary filled element per card
- [ ] Single accessibility node per row (unless secondary control exists)
- [ ] 44 dp touch targets, verified at max font scale
- [ ] Dark + Light themes tested; high contrast + reduced motion verified
- [ ] Colour-blind simulation (deuteranopia, protanopia, tritanopia)
- [ ] Every number opens provenance sheet
- [ ] Every destructive action has undo (3–5 s toast)
- [ ] Empty states: empty-state card with action ("No workouts yet — add one?" button)
- [ ] Charts: table alternative + 1-sentence text description
- [ ] Clinical values: unit + source + range citation (if shown)
- [ ] No words "risk", "detect", "warn", "screen" (§12.1 pr.md)
- [ ] Weight-neutral mode checked (hide weight, adjust language, offer alternatives)
- [ ] RTL checked; no hardcoded left/right
- [ ] Reason to exist clear in 3 seconds

---

## Responsive Notes

- **Device frame:** 393 × 852 dp (baseline, Android standard)
- **Tab bar:** fixed bottom, 60 dp height (12 dp padding, 36 dp icon + 11 sp label)
- **Safe areas:** No content under tab bar or notch
- **Landscape:** Tablets may use split-pane (Today + detail side-by-side); defer to V2
- **Font scale:** Test at 1.3× (200% user scaling) — touch targets grow, layouts reflow but do not overflow

---

## Implementation Notes for Compose

- Tokens: use frozen theme object (§Appendix B DESIGN.md)
- Cards: `Card(modifier = Modifier.padding(12.dp), shape = RoundedCornerShape(12.dp))` with `surface` bg
- Ring charts: custom canvas or Material progress indicator (verify accent reads in both themes)
- Inset rows: `Divider(thickness = 0.5.dp, color = Color.White.copy(0.09))` between rows, not full-width
- Week strip: 7-bubble grid, 32×40 dp each, `RoundedCornerShape(8.dp)`, accent disc for today
- Tab bar: `NavigationBar` (Material 3) with 6 destinations; accent only on active tab
- Sheets: `ModalBottomSheet` with `Scrim` and swipe-down dismiss; preserve scroll position on host
- Haptics: light feedback on tap, medium on action, strong on delete (defer if Compose support pending)

---

## Next Steps

1. Build Compose Theme object with all tokens (§Appendix A DESIGN.md)
2. Implement 6 root screens in order: Today, Train, Food, Body, Insights, More
3. For each screen, run review gate (§14.5) before marking done
4. Build provenance sheets (generic, reusable)
5. Build quick-add sheet (MVP: grid of tiles, recent list, backdate)
6. Build live session screen (set table, timer, effort picker)
7. Test all screens in both themes (dark canonical, light verified)
8. Test colour-blind simulation on all charts and status inks
9. Test at 1.3× font scale; reflow layouts if needed
10. Final audit: every number opens provenance, every destructive action has undo

