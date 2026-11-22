# openHealth — Build Roadmap

**Version:** 0.1  
**Date:** 2026-11-21  
**Reference:** pr.md, DESIGN.md, WIREFRAMES.md, COMPOSE-GUIDE.md, Tech-Stack.md

> **2026-11-22:** navigation is now five tabs (Today, Vitals, Train, Nutrition, Stats) with Train raised and a top bar carrying app name, date, quick add and settings (`DESIGN.md` §4.4, §5.15). Wherever this file says six tabs or a More screen, read that. The rest of this file stays stale as `AGENTS.md` describes.

This document lays out the phase-gated build sequence for the first usable Android release, mapping features to deliverables and exit tests.

---

## Phase Overview

| Phase | Duration | Scope | Gates |
|-------|----------|-------|-------|
| **0** | Weeks 1–3 | Gradle project, theme, 6 reusable components, bottom nav | Tokens frozen, components testable in isolation |
| **1** | Weeks 4–7 | Five root screens (MVP), no detail sheets | Each screen passes review gate (§WIREFRAMES.md §end) |
| **2** | Weeks 8–10 | Quick-add sheet, provenance sheets, live session | Logging loop under 2–5 s per action |
| **3** | Weeks 11–12 | Detail sheets (readiness, session summary, vital detail) | All numbers provenance-linked, all destructive undo-able |
| **4** | Weeks 13–15 | Charts (trends, heatmap, body map renderer) | Charts have table alternative + 1-sentence text |
| **5** | Weeks 16–18 | Sync engine, server integration (opt-in), device pairing | Local-first works offline, sync is optional |
| **6** | Weeks 19–20 | Accessibility audit + remediation, RTL, font scale | WCAG 2.1 AA on all screens, 1.3× font scale verified |
| **Beta** | Weeks 21+ | Bug fixes, performance tuning, play store submission | Signed APK, internal testing |

---

## Phase 0: Foundation (Weeks 1–3)

**Goal:** Frozen design tokens, reusable component library, navigation skeleton.

**Deliverables:**

1. **Gradle project** (Tech-Stack.md §11)
   - App module: `:app`
   - Core module: `:core` (no Android deps)
   - Data module: `:data` (database, sync, integrations)
   - Feature modules: `:feature:today`, `:feature:train`, etc.
   - Design module: `:design` (tokens, theme, components)
   - Hilt + Compose + Material 3 configured

2. **Token set** (`design/tokens.kt`)
   - Dark theme: all 11 colours from §A.1 DESIGN.md
   - Light theme: verified siblings with same contrast ratios
   - 11-step typography scale (13 sp baseline → 34 sp)
   - 9 spacing values (4–40 dp)
   - 6 radii (4–20 dp)
   - Motion constants (140–220 ms)
   - **Exit test:** No hexes in any screen file; all values come from tokens

3. **Material 3 theme** (`design/theme.kt`)
   - `OpenHealthTheme` composable with dark/light toggling
   - CompositionLocal providers for token access
   - Type + shape + color mapped to Material 3 defaults
   - **Exit test:** Theme switches instantly, all text reads in both themes

4. **Reusable components** (`design/components/`)
   - `OHCard` (surface bg, border, padding, 12 dp radius)
   - `CardHeader` (icon + title + value row)
   - `OHRowItem` (label + value, 44 dp min height, separator)
   - `OHPrimaryButton` (accent bg, 44 dp min height, 140 ms press anim)
   - `OHProgressRing` (canvas-drawn ring, centre text overlay, 96–120 dp)
   - `OHChip` (selected/unselected states)
   - `WeekStrip` (7 × 32×40 dp bubbles, today accent)
   - `OHBottomNavigation` (5-tab, accent on active only)
   - `OHBottomSheet` (Material 3, dismissible, header + content)
   - **Exit test:** Each component renders in Compose Preview, light + dark themes verified

5. **Navigation skeleton** (`navigation/`)
   - `NavGraph` with 5 root destinations (Today, Vitals, Train, Nutrition, Stats) plus a top bar
   - Bottom nav routing to each screen
   - Sheet composable for modal detail (provenance, quick-add, etc.)
   - **Exit test:** 5 tabs selectable, navigate between tabs, no crashes

6. **Data models** (`core/model/`)
   - `ReadinessData`, `WorkoutSession`, `Meal`, `Measurement`, `Vital`, `InsightMetric`
   - Align with pr.md §7 (5 layers: raw → observations → rollups → domains → settings)
   - Each model includes provenance fields: `sourceId`, `recordedAt`, `happendAt`, `method`
   - **Exit test:** Models map to room DB, no serialization errors

**Success Criteria:**
- [ ] App launches, 5 tabs visible, can navigate between tabs
- [ ] Every colour, size, shape from tokens (no hardcodes)
- [ ] All 10 components render in Preview
- [ ] Light + dark themes switch instantly
- [ ] Font scale at 1.3× (200% user scaling) does not overflow or crash

---

## Phase 1: Root Screens (five roots + Settings) (Weeks 4–7)

**Goal:** Five root screens, MVP scope, no detail sheets yet. Each screen is scrollable, card-based, shows real data model.

**Deliverables:**

### Today Screen

**Wireframe:** See WIREFRAMES.md §1 and wireframes.html

**Components:**
- Page header (34 sp "Good morning", time, settings gear)
- Connection banner (dismissible, orange/gold if sync paused)
- Readiness card (score, 3 chips, explanation)
- Week strip + plan card (7 bubbles, today accent, plan row with mini body map, **Start** button)
- Nutrition card (120 dp ring, 4 macro bars, water row)
- Steps card (96 dp ring, distance + active min)
- Weight card (large value, signed delta, 30-day sparkline with goal)
- Due items card (inset list of 46 dp rows, editable state)
- Quick actions card (3×2 grid of 84 dp tiles)
- Tab bar (5 tabs, Today active, Train raised)

**Data flow:**
- ViewModel queries `ReadinessRepository`, `WorkoutRepository`, `NutritionRepository`, etc.
- State: `data class TodayUiState(readiness, week, nutrition, steps, weight, dueItems, connectionStatus)`
- Collect as state: `collectAsStateWithLifecycle()`

**Exit test (review gate §WIREFRAMES.md §end):**
- [ ] Every colour from DESIGN.md §2 (no hardcodes)
- [ ] Every size from DESIGN.md §3–4 (no arbitrary padding)
- [ ] One primary action (**Start** button)
- [ ] Every row is single a11y node (except secondary controls)
- [ ] 44 dp min touch targets (verified at 1.3× font scale)
- [ ] Light + dark themes verified, high contrast OK, reduced motion OK
- [ ] Colour-blind simulation: no information lost
- [ ] Every number is tappable (provenance placeholder for now)
- [ ] Empty state shown when no workouts / no nutrition logged
- [ ] No wording "risk", "warn", "screen" (§pr.md §12.1)
- [ ] Weight-neutral mode checked (can hide weight values, adjective wording OK)
- [ ] RTL checked (use `start`/`end`, not `left`/`right`)
- [ ] Reason to exist clear in 3 seconds

**Effort:** 40 hours (1 dev × 1 week)

---

### Train Screen

**Wireframe:** See WIREFRAMES.md §2 and wireframes.html

**Components:**
- Page header (34 sp "Training", week range, session count)
- Week strip (7 bubbles, session count trailing)
- Today's plan card (mini body map, plan name, time, chevron)
- Active programmes card (inset list, week X of Y, chevron)
- Recent sessions card (50 dp thumbnail, name, time, set count)
- This month stats card (2×2 grid: sessions, volume, duration, effort)

**Data flow:**
- ViewModel queries `WorkoutRepository.getWeekSummary()`, `.getActiveProgrammes()`, `.getRecentSessions()`
- State: `data class TrainUiState(weekSummary, todaysPlan, programmes, recentSessions, monthStats)`

**Exit test:**
- [ ] All review gate items (same as Today screen)
- [ ] Session rows swipe-left to delete (undo toast)
- [ ] Tap **Start** button: navigate to Live Session screen (Phase 2)
- [ ] Programme count matches data

**Effort:** 30 hours (1 dev × 1 week)

---

### Nutrition Screen

**Wireframe:** See WIREFRAMES.md §3 and wireframes.html

**Components:**
- Page header (34 sp "Nutrition", current date, scan button in header)
- Date strip (7 bubbles, tap to jump to other days)
- Energy card (120 dp ring, 4 macro bars: protein, carbs, fat, fibre)
- Meal sections (Breakfast, Lunch, Dinner, Snacks)
  - Each section: inset list of meal rows (44 dp image, name, time, calories)
  - Swipe-left to delete (undo toast)
- Water row (13 sp label, value, secondary button)
- **Add meal** button (primary)

**Data flow:**
- ViewModel queries `NutritionRepository.getMealsForDate(date)`
- State: `data class FoodUiState(date, energy, macros, meals, waterIntake, selectedDay)`

**Exit test:**
- [ ] All review gate items
- [ ] Date strip tap changes displayed date
- [ ] Meal rows swipe-left to delete (undo)
- [ ] Scan button in header (placeholder for now, Phase 2)
- [ ] Energy ring shows % of daily target

**Effort:** 30 hours (1 dev × 1 week)

---

### Vitals Screen

**Wireframe:** See WIREFRAMES.md §4 and wireframes.html

**Components:**
- Page header (34 sp "Body", subtitle)
- Weight card (large value, trend sentence, 90-day line chart with goal)
- Silhouette + measurements (silhouette glyph, 7 inset rows: neck, waist, hip, etc.)
- Composition tile grid (2×2: body fat %, lean mass, etc.)
- Photos section (3-across grid of 120 dp thumbnails, date labels)
- Vitals section (grouped list: HR, BP, temperature; tap for detail)
- Sleep section (if module enabled: duration, quality, bedtime)
- Cycle section (if logged: phase, days until next event)

**Data flow:**
- ViewModel queries `BodyRepository.getWeight()`, `.getMeasurements()`, `.getComposition()`, `.getPhotos()`, etc.
- State: `data class BodyUiState(weight, trend, measurements, composition, photos, vitals, sleep?, cycle?)`

**Exit test:**
- [ ] All review gate items
- [ ] Weight card sparkline is 30-day rolling window
- [ ] Measurements tied to date (or use `recorded_at`)
- [ ] Photos grid 3-across, images load from local DB
- [ ] Weight-neutral mode: can hide all weight values, adjust language
- [ ] Vitals section appears only if module enabled

**Effort:** 35 hours (1 dev × 1 week)

---

### Stats Screen

**Wireframe:** See WIREFRAMES.md §5 and wireframes.html

**Components:**
- Page header (34 sp "Stats", week range)
- Segmented control (5 tabs: Overview, Training, Sleep, Body, Nutrition)
- KPI band (3 tiles: volume, effort, sessions)
- Chart cards (1 per metric)
  - Volume trend (line chart placeholder)
  - Consistency heatmap (11 dp cells, 2 dp gap)
  - Muscle balance (body map placeholder)
  - Top lifts (bar chart)
  - Footer: "how this is calculated" link

**Data flow:**
- ViewModel queries `InsightRepository.getTrends(segment, timeRange)`
- State: `data class InsightUiState(selectedSegment, kpiValues, charts, calculationNote)`

**Exit test:**
- [ ] All review gate items
- [ ] Segmented tab selection animates (140–220 ms)
- [ ] Charts render with placeholder data (real data in Phase 4)
- [ ] "How this is calculated" link is tappable (opens sheet in Phase 3)

**Effort:** 30 hours (1 dev × 1 week)

---

### Settings Screen

**Wireframe:** See WIREFRAMES.md §6 and wireframes.html

**Components:**
- Profile row (56 dp avatar, name, goal, chevron)
- Modules group (Training ✓, Nutrition ✓, Vitals ✓, Sleep ○, Cycle ○, Recovery ○)
- Devices group (connected devices, last sync time, chevron)
- Data group (backups row, data review row)
- Appearance group (accent colour chip + name, theme toggle)
- Privacy & security group (passphrase, policy links)
- About group (version, feedback, open source)
- Destructive group (22 dp spacer above, "Delete all data", red-ink)

**Data flow:**
- ViewModel queries `SettingsRepository`, `DeviceRepository`, `AppStatusRepository`
- State: `data class MoreUiState(profile, modules, devices, appearance, appStatus)`

**Exit test:**
- [ ] All review gate items
- [ ] Module toggles appear (no logic yet; Phase 2)
- [ ] Device list shows connected devices (or placeholder "No devices")
- [ ] Accent colour chip is current theme accent
- [ ] Theme row shows "Dark" (light theme in Phase 6)
- [ ] Delete button is red-ink, separate group, 22 dp spacer

**Effort:** 25 hours (1 dev × 1 week)

---

## Phase 1 Exit Gate

Before moving to Phase 2:

- [ ] All 5 root screens render without crashes
- [ ] Tab navigation works (bottom nav switches between screens)
- [ ] All 5 root screens pass review gate (WIREFRAMES.md §end)
- [ ] No hardcoded hexes or sizes (all from tokens.kt)
- [ ] Light + dark themes verified on all screens
- [ ] Font scale 1.3× verified (no overflow, touch targets ≥ 44 dp)
- [ ] Colour-blind simulation passed (deuteranopia, protanopia)
- [ ] No wording violates pr.md §12.1 (medical device rules)
- [ ] Empty states shown (e.g., no workouts, no meals)
- [ ] Pull-to-refresh wired to data refresh (shows "Loading…")

**Effort:** 190 hours (4–5 devs × 2 weeks, or 1 dev × 5 weeks)

---

## Phase 2: Logging & Detail (Weeks 8–10)

**Goal:** Logging loop MVP (set, food, weight, water), quick-add sheet, provenance sheets, live session.

**Deliverables:**

1. **Quick-add sheet** (MVP, Phase 2)
   - 3×2 grid of 84 dp tiles (Food, Water, Weight, Set, Vitals, Note)
   - Tap tile: opens item-specific sheet or logs immediately
   - Recent list: last 5 items, tap to log (undo toast)
   - Backdate row: tap to open date/time picker

2. **Provenance sheet** (generic, reusable)
   - Value + unit + date + time
   - Source (device, method)
   - `recorded_at`, `happened_at` (if different), timezone
   - Edit + delete buttons
   - Cite button (links to range/formula)

3. **Live session screen** (full-screen, no tab bar)
   - Close chevron + plan name + elapsed timer, **Finish** button
   - Exercise blocks (image, name, tags, plan, last time, progression)
   - Set table (Set | Weight | Reps | RIR | Done columns)
   - Rest timer (dock at bottom)
   - Chip row (set bubbles, mini body map)

4. **Minor detail sheets**
   - Readiness detail (score breakdown: sleep %, HRV, RHR, explanation)
   - Session summary (KPI band, PR list, "how it felt", notes)
   - Effort picker (1–10 scale, glyph + word)

**Data models:**
- Extend with logging: `insertWorkoutSet()`, `insertMeal()`, `updateWeight()`, `insertVital()`
- Provenance complete: every logged value has source, method, `recorded_at`, `happened_at`, device, timezone

**Exit test:**
- [ ] Log a set in <2 s (weight/reps pre-filled, one tap per set, rest timer auto)
- [ ] Log food in <5 s (search/scan → portion sheet → save)
- [ ] Log weight in 3 taps, <5 s (card → stepper → save)
- [ ] Log water in 1 tap (quick-add chip re-uses last container)
- [ ] Every logged value: tap opens provenance sheet (source, method, timestamp, device)
- [ ] Delete any logged value: undo toast (3–5 s)
- [ ] Live session: close, resume, finish, save (data persists)

**Effort:** 120 hours (2–3 devs × 2.5 weeks, or 1 dev × 3 weeks)

---

## Phase 3: Detail Sheets & Calculations (Weeks 11–12)

**Goal:** All major detail sheets, correct calculations, provenance complete.

**Deliverables:**

1. **Detail sheets** (open from taps):
   - Readiness detail (inputs: sleep, HRV, RHR; calculated score)
   - Workout session summary (duration, volume, sets, PRs, effort, notes)
   - Vital detail (trend chart, range, readings list, citation)
   - Measurement detail (trend, date anchors, edit, delete)
   - Food detail (macros, ingredients, portion size, barcode)

2. **Calculation engine**
   - Readiness: `(sleep % × 0.4) + (HRV % × 0.3) + (RHR % × 0.3)` (or pr.md §6 formula)
   - Volume: `∑(weight × reps)` per session
   - Macros: `∑(protein, carbs, fat)` per day
   - Trends: 7-day, 30-day rolling average or linear regression
   - Sparklines: 30-day data point per day

3. **Explanation sheets** (V1, MVP minimal)
   - Formula (equation)
   - Inputs (which data points)
   - Recalculation frequency ("daily at midnight")
   - Cited range (if applicable; DOI link)

4. **Empty states**
   - "No workouts yet — tap **Start** to begin" (button)
   - "No meals logged — tap **Add meal** to start" (button)
   - "No measurements — add one to track progress" (button)

**Exit test:**
- [ ] Readiness score calculation correct (manual verification)
- [ ] Volume calculation correct (manual verification)
- [ ] Trends recalculate nightly (testable with fake date)
- [ ] Sparkline x-axis is 30 days (or 90 days for weight), gap shown for missing days
- [ ] Every trend chart has table alternative (details sheet)
- [ ] Cited range shown for vitals (e.g., "Normal BP: <120/80 mmHg (AHA 2015)")
- [ ] No hardcoded formulas in UI (all in `CalculationRepository`)

**Effort:** 100 hours (2–3 devs × 2 weeks, or 1 dev × 2.5 weeks)

---

## Phase 4: Charts & Visuals (Weeks 13–15)

**Goal:** All charts render, body map renderer, heatmap, line charts, bars.

**Deliverables:**

1. **Chart library**
   - Line chart (custom canvas or Vico)
   - Bar chart (custom or Material baseline)
   - Heatmap (11 dp cells, 2 dp gap, 5-step accent ramp)
   - Sparkline (30 dp height, inline in cards)

2. **Body map renderer**
   - SVG-to-Compose renderer (use openGym geometry, MIT licence, with notice)
   - 4 views: male/female × front/back (defer animated rotation to V2)
   - 90 muscles, 5-step accent ramp (more loaded = darker accent)
   - Tap muscle: detail sheet (volume, sets, exercises)

3. **Chart cards**
   - Volume trend (line, 12 weeks)
   - Consistency heatmap (cells per day, grey if no session, accent if trained)
   - Muscle balance (body map with accent shading)
   - Top lifts (bars, name + max weight)
   - Stat bars (e.g., macro distribution: protein %, carbs %, fat %)

**Exit test:**
- [ ] Line chart renders 12-week data, x-axis labels, y-axis grid
- [ ] Heatmap cells 11 dp, gap 2 dp, 5-step ramp matches accent
- [ ] Body map renders both themes (accent changes)
- [ ] Tap muscle: detail sheet shows exercises, volumes
- [ ] Every chart has 1-sentence text description (a11y)
- [ ] Every chart has table alternative (data download or details sheet)
- [ ] Charts responsive to screen width (no horizontal scroll, reflow instead)

**Effort:** 150 hours (3 devs × 2.5 weeks, or 1 dev × 3.75 weeks)

---

## Phase 5: Sync Engine & Server (Weeks 16–18)

**Goal:** Local-first works offline, optional cloud sync, device pairing (Oura, Watch, etc.).

**Deliverables:**

1. **Local database** (Room)
   - Five layers: `RawObservation`, `CanonicalObservation`, `DailyRollup`, `DomainObject`, `UserSetting`
   - Deduplication: priority per source, one source wins per day
   - Backfill: rebuilds rollups from observations, never patches

2. **Sync engine**
   - Local write-first: all actions complete offline
   - Background sync: periodic upload (1 min, 5 min, 1 hr user-configurable)
   - Conflict resolution: last-write-wins + source priority
   - Device sync: pull from Oura, Galaxy Watch, Health Kit (Android 12+)

3. **Server integration** (optional, opt-in)
   - API: RESTful or GraphQL (Tech-Stack.md §8.2)
   - Auth: local token, no account required
   - Backup: encrypted, user-deletable
   - Self-hosting: Ktor server available on GitHub

4. **Device pairing**
   - Oura Ring OAuth flow (Phase 5 or later, V1+)
   - Galaxy Watch: Wear OS companion app + BLE sync
   - Health Kit: permission request, continuous read
   - Fitbit, Garmin: defer to V2 (license terms)

**Exit test:**
- [ ] All actions work offline (no network required)
- [ ] Sync banner shows status ("Syncing", "Paused", "5 items pending")
- [ ] Pull-to-refresh triggers sync immediately
- [ ] Data merges correctly (no duplicates, correct priorities)
- [ ] Rollups rebuild nightly (testable with fake date)
- [ ] Device pairing works (fake device for testing)

**Effort:** 200 hours (3 devs × 3 weeks, or 1 dev × 5 weeks)

---

## Phase 6: Accessibility & Themes (Weeks 19–20)

**Goal:** WCAG 2.1 AA on all screens, RTL, light theme, reduced motion.

**Deliverables:**

1. **Accessibility audit**
   - WCAG 2.1 AA: all text ≥ 4.5:1 on actual backgrounds
   - Touch targets: all ≥ 44 dp (measured at 1.3× font scale)
   - Colour-blind: no information lost in deuteranopia, protanopia, tritanopia
   - Keyboard navigation: all actions reachable without touch
   - Screen reader: all images have descriptions, single a11y node per row

2. **Light theme**
   - Dark tokens → light tokens (Surface: white, Label: black, Separators: light grey)
   - Verify 4.5:1 on light bg for all text
   - Verify accent reads in both themes (all 9 presets)
   - Toggle instantly (Theme row in Settings)

3. **Reduced motion**
   - Remove animations: scale, opacity, slide transitions
   - Instant state changes (no animation)
   - Haptics remain (or toggle separately)

4. **RTL (Arabic, Hebrew)**
   - Remove hardcoded `left`/`right`, use `start`/`end`
   - Reverse leading/trailing edges
   - Test with RTL emulator

5. **Font scale (1.3×)**
   - Layout reflows: no overflow, cards stack
   - Touch targets: ≥ 44 dp even at 1.3× scale
   - Text truncation: handled gracefully

**Exit test:**
- [ ] WCAG AA contrast checker: all text ✓
- [ ] Font scale 1.3×: no overflow, all targets ≥ 44 dp
- [ ] Colour-blind (deuteranopia): no chart info lost, icons + patterns
- [ ] Keyboard navigation: all screens navigable without touch
- [ ] Screen reader: all images have `contentDescription`
- [ ] Light theme: all screens verified
- [ ] RTL: layout mirrors correctly, no hardcoded directions
- [ ] Reduced motion: all transitions instant

**Effort:** 100 hours (2 devs × 2.5 weeks, or 1 dev × 2.5 weeks)

---

## Beta & Launch (Weeks 21+)

**Goal:** Bug fixes, performance, play store submission.

**Deliverables:**

1. **Bug triage & fixes**
   - Internal testing: 10–15 testers
   - Crash reporting (Crashlytics)
   - Performance profiling (Android Studio Profiler)

2. **Performance tuning**
   - List scroll: 60 fps (measure with Compose Performance)
   - Sync: <2 s for small updates
   - Charts: render in <1 s
   - Database: queries <100 ms

3. **Play Store**
   - Signed release APK
   - Metadata: description, screenshots, privacy policy, permissions
   - Play Integrity token
   - Target API 34+ (Android 14)

4. **Marketing assets**
   - Screenshots (6–8 screens, with captions)
   - 30-second video walkthrough
   - Privacy policy + data processing agreement

**Effort:** 100+ hours (varies by bug count + compliance review)

---

## Risk Register & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| Body map rendering too slow | Charts unusable | Medium | Custom canvas (not SVG), render once per 24 h |
| Sync conflicts corrupt data | Data loss | Low | Last-write-wins + tests for merge edge cases |
| Device pairing fails (Oura OAuth) | Features blocked | Medium | Defer device pairing to V1.1, offer manual entry |
| Accessibility audit failures | Store rejection | Low | QA on 100% of screens before submission |
| Font scale reflow breaks layout | Bad UX at 1.3× | Medium | Reflow all cards to vertical stack layout |
| Light theme contrast fails | Store rejection | Low | Measure every colour pair upfront (Phase 0) |

---

## Success Metrics (Phase 0 → Beta)

- **Velocity:** 190 h Phase 1 / 5 weeks = 38 h/week (1 dev) or 76 h/week (2 devs) ✓
- **Quality:** 0 crashes in Phase 1 exit gate, <5 bugs in Phase 2
- **Accessibility:** WCAG AA on all 5 root screens + 3 major sheets
- **Performance:** 60 fps scroll on all lists, <2 s sync for typical data
- **Data integrity:** 100% provenance (every value has source + timestamp)
- **User trust:** no data loss, all actions undoable within 5 s

---

## Implementation Order (1 Dev, 20 Weeks)

Weeks 1–3: **Phase 0** (tokens, components, nav)
Weeks 4–8: **Phase 1** (5 root screens + Settings)
Weeks 9–10: **Phase 2** (logging, quick-add, live session)
Weeks 11–12: **Phase 3** (detail sheets, calculations)
Weeks 13–15: **Phase 4** (charts, body map)
Weeks 16–18: **Phase 5** (sync, server, devices)
Weeks 19–20: **Phase 6** (accessibility, light theme, RTL)
Weeks 21+: **Beta** (testing, fixes, launch)

---

## Blockers & Dependencies

- **Phase 0 → Phase 1:** Tokens frozen (§DESIGN.md §0.2)
- **Phase 1 → Phase 2:** ViewModel state management working (Hilt + Flow)
- **Phase 2 → Phase 3:** Database schema finalized (5 layers: pr.md §7)
- **Phase 4:** Chart library decision (custom canvas vs Vico vs Compose)
- **Phase 5:** Backend API design (pr.md §6.17, §8.2)
- **Phase 6:** Accessibility tools configured (Accessibility Scanner, font scale emulator)

---

## Next Step

**Start Phase 0, Week 1:** Create Gradle project, initialize `design/tokens.kt` from DESIGN.md §Appendix A.

