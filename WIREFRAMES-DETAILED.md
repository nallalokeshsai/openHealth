# openHealth — Detailed Individual Wireframes

**Version:** 0.1  
**Date:** 2026-11-21  
**Device:** Android, 393 dp width × 852 dp height  
**Theme:** Dark (canonical), Light (sibling)

---

## Wireframe Standards

All wireframes follow these conventions:

- **Device frame:** 393 × 852 dp (baseline Android)
- **Safe area top:** 24 dp (status bar clearance)
- **Safe area bottom:** 60 dp (tab bar + 12 dp padding)
- **Content area:** 393 × 768 dp (from 24 dp to 792 dp)
- **Horizontal padding:** 16 dp left & right (content width: 361 dp)
- **Spacing grid:** 4, 8, 12, 16, 20, 24, 28, 32, 40 dp
- **Colour notation:** `surface` (card bg), `label` (text), `acc` (accent lime), `sep` (separator)
- **Typography:** 11sp caption, 13sp label, 15sp footnote, 17sp body, 20sp headline, 34sp display

---

# SCREEN 1: TODAY

## Wireframe Structure

```
┌─────────────────────────────────────────┐  0 dp (top safe area starts at 24 dp)
│                                         │
│  Good morning                      ⚙️   │  24 dp (header start)
│  Saturday 21 November · 08:14      (36) │  48 sp / 700 weight
│                                         │
├─────────────────────────────────────────┤  52 dp (after header + 4 dp spacing)
│ ⚠️  Syncing paused                      │  Connection banner
│    Enable location or check connection  │  12 dp padding, 16 dp left icon
├─────────────────────────────────────────┤  Dismissed state: removed
│ 🌙 Readiness                      72%   │  64 dp card height
│ ┌──────────────────────────────────┐   │
│ │ Sleep: 7h 32m  │ HRV: 48 ms │ RH: 52│  Chip row (3 × variable width)
│ └──────────────────────────────────┘   │
│ You're ready for a hard session today.  │  13 sp label-2, text in body
├─────────────────────────────────────────┤
│ This week                          2 ⬤  │  116 dp card height
│ ┌─────────────────────────────────────┐ │  Week strip: 7 × 32w × 40h bubbles
│ │ Mon Tue Wed Thu* Fri Sat Sun        │ │  Today (Thu) = accent bg
│ │ 18  19  20  21* 22  23  24          │ │  2 ⬤ = session count trailing
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │  Plan row: 46dp map + flex + button
│ │ 💪  Upper Power          [Start]    │ │  44 dp min button height
│ │     Today · 50 min                  │ │
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ 🔥 Nutrition                            │  120 dp card height
│        1240 / 2200                      │  120 dp ring (center text)
│        ⭕                                │
│                                         │
│ Protein    120g / 160g  ▓▓▓▓▓░░░       │  4 × bar group (16 dp spacing)
│ Carbs      240g / 300g  ▓▓▓▓▓▓░░░░     │  Each: label row + 6 dp bar
│ Fat        48g / 60g    ▓▓▓▓▓▓░░░░     │
│ Fibre      24g / 30g    ▓▓▓▓▓▓░░░░     │
│ ─────────────────────────────────────  │  Separator (0.5 dp)
│ Water                        2.4 L     │  Row item: 44 dp min height
├─────────────────────────────────────────┤
│ 👣 Steps                                │  100 dp card height
│         8420                            │  96 dp ring (center text)
│         ⭕                              │
│                                         │
│ 12.4 km · 156 active min                │  Subtitle row
├─────────────────────────────────────────┤
│ ⚖️  Weight                              │  120 dp card height
│         75.2 kg                         │  36 sp / 700 (large value)
│    +1.1 kg this week · Goal: 73 kg      │  13 sp label-2 (delta + goal)
│ ┌─────────────────────────────────────┐ │  30-day sparkline (100% width)
│ │ 30-day trend chart placeholder      │ │
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ Due items                               │  Variable height (3 rows shown)
│ ┌─────────────────────────────────────┐ │  44 dp × row, separator between
│ │ Finish morning routine   10:30     ⚡ │
│ ├─────────────────────────────────────┤ │
│ │ Add body measurements   Today      ⚡ │
│ ├─────────────────────────────────────┤ │
│ │ Review weekly summary  18:00       ⚡ │
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ Quick add                               │  108 dp card height
│ ┌──┐ ┌──┐ ┌──┐                          │  3 × 2 grid of 84dp tiles
│ │🍽️│ │💧│ │⚖️│                          │  21 dp centered icon
│ │Fd│ │Wt│ │Wt│  21 sp / 500 label      │
│ ├──┤ ├──┤ ├──┤                          │
│ │💪│ │❤️│ │📝│                          │
│ │St│ │Vl│ │Nt│                          │
│ └──┘ └──┘ └──┘                          │
│                                         │
└─────────────────────────────────────────┘  768 dp (content end)

┌─────────────────────────────────────────┐  768-852 dp (tab bar area)
│ 📅 🏋️ 🍽️ 👤 📊 ⋯                       │  60 dp total (8 dp padding top)
│ Today Train Food Body Insights More      │  11 sp labels
│ (lime accent on Today)                  │
└─────────────────────────────────────────┘
```

## Component Breakdown

| Component | Dimensions | Notes |
|-----------|-----------|-------|
| Header | 393 × 44 dp | 34 sp / 700 title, time below, settings gear (36 dp) trailing |
| Connection banner | 393 × 48 dp | Orange bg, 12 dp padding, dismissible X (36 dp), 16 dp left icon |
| Readiness card | 361 × 120 dp | Card header + 3 chips + 13 sp text line |
| Week strip card | 361 × 116 dp | 7 bubbles (32 × 40), plan row (46 dp map, flex, button) |
| Nutrition card | 361 × 160 dp | 120 dp ring, 4 bar groups (16 dp spacing), water row |
| Steps card | 361 × 100 dp | 96 dp ring, subtitle line |
| Weight card | 361 × 120 dp | 36 sp value, 13 sp delta/goal, 30-day chart (100% width, 60 dp height) |
| Due items card | 361 × variable | 44 dp rows, separator 0.5 dp between |
| Quick actions card | 361 × 108 dp | 3 × 2 grid, 84 dp tiles, 21 dp icons, 13 sp labels |
| **Tab bar** | **393 × 60 dp** | **6 tabs, Today active (acc), 11 sp labels, 36 dp icons** |

## Spacing & Layout Grid

```
Vertical spacing (card-to-card): 12 dp
Horizontal content padding: 16 dp left & right
Card internal padding: 16 dp
Row height (inset list): 44 dp
Separator height: 0.5 dp (0.5 dp color: sep)
Chip gap: 8 dp (overflow to next line if needed)
```

## State Variations

| State | Change |
|-------|--------|
| No connection | Banner shown (orange bg), dismissible |
| Syncing | "Syncing..." in banner (blue bg), no X |
| No due items | Card hidden entirely |
| No meals logged | Nutrition card shows placeholder "No meals yet" (13 sp label-2) |
| No workouts today | Plan row shows "Add a plan" (secondary button) instead of **Start** |

---

# SCREEN 2: TRAIN

## Wireframe Structure

```
┌─────────────────────────────────────────┐  24 dp (top safe area)
│ Training                           ⚙️   │  34 sp / 700 header
│ Week of November 18                     │  13 sp label-2 subtitle
│                                         │
├─────────────────────────────────────────┤  52 dp (after header + 4 dp spacing)
│ This week                          3 ⬤  │  Week strip card (116 dp)
│ ┌─────────────────────────────────────┐ │
│ │ Mon Tue Wed Thu* Fri Sat Sun        │ │  7 × 32w × 40h bubbles
│ │ 18  19  20  21* 22  23  24          │ │  Today accent, session count trailing
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │  Plan row (68 dp total)
│ │ 🦵  Lower Power               ›    │ │
│ │     Squats, Deadlifts, Leg Press     │ │
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ Active programmes                       │  120 dp card
│ ┌─────────────────────────────────────┐ │
│ │ Push/Pull/Legs               Week 4 ›│  44 dp × row, 16 dp spacing
│ │ 4 of 12                              │
│ ├─────────────────────────────────────┤ │
│ │ Upper/Lower               Week 2 of 8›│
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ Recent workouts                         │  180 dp card
│ ┌─────────────────────────────────────┐ │
│ │ [thumb 50×50] Upper Power  8 sets  › │  44 dp × row
│ │            Wed · 55 min              │  Thumbnail left (50 dp)
│ ├─────────────────────────────────────┤ │
│ │ [thumb 50×50] Lower Hypertrophy     │  Flex middle (name + subtitle)
│ │            Mon · 48 min      12 sets›│  Trailing value (8 sp / label)
│ └─────────────────────────────────────┘ │
│                                         │
│ [+ load more]  (secondary button)       │  Optional if >2 sessions
├─────────────────────────────────────────┤
│ This month                              │  120 dp card
│ ┌──────────┐ ┌──────────┐              │  2 × 2 grid of stat boxes
│ │ 12       │ │ 24t      │              │  Each box: 11 sp label, 20 sp value
│ │ sessions │ │ volume   │              │
│ ├──────────┤ ├──────────┤              │
│ │ 52m      │ │ 7.2      │              │
│ │ avg dur. │ │ avg eff. │              │
│ └──────────┘ └──────────┘              │
│                                         │
└─────────────────────────────────────────┘  768 dp (content end)

┌─────────────────────────────────────────┐  768-852 dp (tab bar area)
│ 📅 🏋️ 🍽️ 👤 📊 ⋯                       │  Train tab active (lime)
│ Today Train Food Body Insights More      │
└─────────────────────────────────────────┘
```

## Component Breakdown

| Component | Dimensions | Notes |
|-----------|-----------|-------|
| Header | 393 × 44 dp | 34 sp title + 13 sp subtitle, settings gear |
| Week strip card | 361 × 116 dp | Same as Today screen |
| Today's plan card | 361 × 68 dp | 46 dp mini body map, flex text, chevron |
| Active programmes card | 361 × 120 dp | 44 dp × row (programme name, week, chevron) |
| Recent workouts card | 361 × 180 dp | 50 dp thumbnail left, flex title/subtitle, trailing value |
| This month stats card | 361 × 120 dp | 2 × 2 grid, stat boxes (11 sp label, 20 sp value) |
| **Tab bar** | **393 × 60 dp** | **Train tab active (accent lime)** |

---

# SCREEN 3: FOOD

## Wireframe Structure

```
┌─────────────────────────────────────────┐  24 dp
│ Nutrition                          📷   │  34 sp header, scan camera trailing
│ Thursday 21 November                    │  13 sp subtitle, scan button (44 dp)
│                                         │
├─────────────────────────────────────────┤  52 dp
│ ┌─────────────────────────────────────┐ │  Date strip card (60 dp)
│ │ Mon Tue Wed Thu* Fri Sat Sun        │ │  7 × 32w × 40h bubbles
│ │ 18  19  20  21* 22  23  24          │ │  Tap to jump to other days
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ Energy                                  │  160 dp card
│        1680 / 2200                      │  120 dp ring (center text)
│        ⭕                                │
│                                         │
│ Protein    128g / 160g  ▓▓▓▓▓▓▓░░░░    │  3 × bar group (16 dp spacing)
│ Carbs      280g / 300g  ▓▓▓▓▓▓▓▓▓░░    │
│ Fat        52g / 60g    ▓▓▓▓▓▓▓░░░░░   │
├─────────────────────────────────────────┤
│ Breakfast                               │  Meal section header (13 sp / 600)
│ ┌─────────────────────────────────────┐ │
│ │ [img 44×44] Oatmeal with berries   │ │  44 dp × row
│ │            08:30 · Logged    420 cal│ │  Thumbnail left, flex middle, trailing value
│ └─────────────────────────────────────┘ │  Swipe left to delete
├─────────────────────────────────────────┤
│ Lunch                                   │  Meal section header
│ ┌─────────────────────────────────────┐ │
│ │ [img 44×44] Grilled chicken salad  │ │  44 dp × row
│ │            13:20 · Logged    580 cal│ │
│ └─────────────────────────────────────┘ │  Swipe left to delete
├─────────────────────────────────────────┤
│ Snacks                                  │  Meal section header
│ ┌─────────────────────────────────────┐ │
│ │ [img 44×44] Greek yogurt & honey   │ │  44 dp × row
│ │            16:45 · Logged    280 cal│ │
│ └─────────────────────────────────────┘ │  Swipe left to delete
│                                         │
│ [Add meal]                              │  Primary button, 44 dp min height
│                                         │
│                                         │
└─────────────────────────────────────────┘  768 dp

┌─────────────────────────────────────────┐  768-852 dp
│ 📅 🏋️ 🍽️ 👤 📊 ⋯                       │  Food tab active (lime)
│ Today Train Food Body Insights More      │
└─────────────────────────────────────────┘
```

## Component Breakdown

| Component | Dimensions | Notes |
|-----------|-----------|-------|
| Header | 393 × 44 dp | 34 sp title, 13 sp subtitle, scan button (44 dp) trailing |
| Date strip card | 361 × 60 dp | 7 × 32w × 40h bubbles, tap to jump |
| Energy card | 361 × 160 dp | 120 dp ring, 3 macro bars (16 dp spacing) |
| Meal section | 361 × variable | Section header (13 sp / 600), 44 dp × row per meal, separator 0.5 dp |
| Meal row | 361 × 44 dp | 44 dp thumbnail left, flex middle (title + time), trailing value (kcal) |
| **Add meal button** | **361 × 44 dp** | **Primary button, full-width** |
| **Tab bar** | **393 × 60 dp** | **Food tab active (accent lime)** |

## Interactions

- **Date bubble tap:** Jump to that day's meals
- **Meal row swipe left:** Delete with undo toast (3–5 s)
- **Scan button:** Camera opens (barcode scan, Phase 2)
- **Add meal button:** Opens quick-add sheet or search

---

# SCREEN 4: BODY

## Wireframe Structure

```
┌─────────────────────────────────────────┐  24 dp
│ Body                               ⚙️   │  34 sp header
│ Your measurements & vitals              │  13 sp subtitle
│                                         │
├─────────────────────────────────────────┤  52 dp
│ Weight                                  │  120 dp card
│        75.2 kg                          │  36 sp / 700 (large value)
│    +1.1 kg this week · Goal: 73 kg      │  13 sp label-2
│ ┌─────────────────────────────────────┐ │  90-day sparkline (60 dp height)
│ │ 90-day trend chart                  │ │
│ │ with goal line                      │ │
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ Measurements                            │  180 dp card
│          👤 (48 sp silhouette)          │
│ ┌─────────────────────────────────────┐ │
│ │ Neck              37.5 cm          › │  44 dp × row, separator
│ ├─────────────────────────────────────┤ │
│ │ Waist             78.2 cm          › │
│ ├─────────────────────────────────────┤ │
│ │ Hip               92.1 cm          › │
│ ├─────────────────────────────────────┤ │
│ │ Chest             98.4 cm          › │
│ └─────────────────────────────────────┘ │
│                                         │
│ [+ more measurements]  (secondary)      │  Expandable list
├─────────────────────────────────────────┤
│ Composition                             │  120 dp card
│ ┌──────────────┐ ┌──────────────┐      │  2 × 2 stat boxes
│ │ 16.2%        │ │ 63.1 kg      │      │  11 sp label, 20 sp value
│ │ body fat     │ │ lean mass    │      │
│ └──────────────┘ └──────────────┘      │
├─────────────────────────────────────────┤
│ Latest vitals                           │  100 dp card
│ ┌─────────────────────────────────────┐ │
│ │ Heart rate        62 bpm           › │  44 dp × row
│ ├─────────────────────────────────────┤ │
│ │ Blood pressure    118/76 mmHg      › │
│ ├─────────────────────────────────────┤ │
│ │ Temperature       36.8 °C          › │
│ └─────────────────────────────────────┘ │
│                                         │
│                                         │
└─────────────────────────────────────────┘  768 dp

┌─────────────────────────────────────────┐  768-852 dp
│ 📅 🏋️ 🍽️ 👤 📊 ⋯                       │  Body tab active (lime)
│ Today Train Food Body Insights More      │
└─────────────────────────────────────────┘
```

## Component Breakdown

| Component | Dimensions | Notes |
|-----------|-----------|-------|
| Header | 393 × 44 dp | 34 sp title, 13 sp subtitle |
| Weight card | 361 × 120 dp | 36 sp value, 13 sp delta/goal, 60 dp sparkline |
| Measurements card | 361 × 180+ dp | Silhouette glyph (48 sp), 44 dp rows (7 anchors) |
| Composition card | 361 × 120 dp | 2 × 2 stat boxes (body fat %, lean mass, etc.) |
| Vitals card | 361 × 100 dp | 44 dp rows (HR, BP, temp), chevron on each |
| **Tab bar** | **393 × 60 dp** | **Body tab active (accent lime)** |

## State Variations

- **No composition data:** Tile shows "Add body scan" (secondary button)
- **No recent vitals:** Card shows "No vitals yet — add one" with button
- **Photos section** (below, if photos logged): 3-across grid of 120 dp thumbnails

---

# SCREEN 5: INSIGHTS

## Wireframe Structure

```
┌─────────────────────────────────────────┐  24 dp
│ Insights                           ⚙️   │  34 sp header
│ Week of November 18                     │  13 sp subtitle
│                                         │
├─────────────────────────────────────────┤  52 dp
│ ┌──────────────┬──────────────┬────────┐│  Segmented control card (60 dp)
│ │ Overview   │ Training │ Sleep │        │  5 tabs, active = surface bg
│ └──────────────┴──────────────┴────────┘│  11 sp labels
│                                         │
├─────────────────────────────────────────┤  120 dp
│ ┌──────────────┐ ┌──────────────┐      │  KPI band (3 tiles, 60 dp total)
│ │ 24t          │ │ 7.2          │      │  11 sp label, 20 sp value
│ │ Total volume │ │ Avg effort   │      │
│ ├──────────────┤ ├──────────────┤      │
│ │ 3            │                        │
│ │ Sessions     │                        │
│ └──────────────┘ └──────────────┘      │
├─────────────────────────────────────────┤
│ Volume trend                            │  180 dp card
│ ┌─────────────────────────────────────┐ │
│ │ Line chart (12 weeks)               │ │  140 dp chart height
│ │ X-axis labels, Y-axis grid          │ │
│ │                                     │ │
│ └─────────────────────────────────────┘ │
│ How this is calculated              › │  13 sp label-2 footer
├─────────────────────────────────────────┤
│ Training heatmap                        │  140 dp card
│ ┌─────────────────────────────────────┐ │
│ │ [11 dp cells × cells, 2 dp gap]     │ │  Heatmap: 5-step accent ramp
│ │ Mon Tue Wed Thu Fri Sat Sun          │ │  Darker = more sessions
│ │ cells | cells | cells | ...         │ │
│ └─────────────────────────────────────┘ │
│ How this is calculated              › │  13 sp label-2 footer
├─────────────────────────────────────────┤
│ Muscle balance (body map)               │  180 dp card
│ ┌─────────────────────────────────────┐ │
│ │        👤                           │ │  Body map (SVG-to-Compose)
│ │   (body map with accent shading)    │ │  Tap muscle for detail
│ │                                     │ │
│ └─────────────────────────────────────┘ │
│ How this is calculated              › │  13 sp label-2 footer
├─────────────────────────────────────────┤
│ Top lifts                               │  140 dp card
│ ┌─────────────────────────────────────┐ │
│ │ Squat              150 kg  ▓▓▓▓▓    │ │  Bar chart (name + bar + value)
│ │ Bench Press        110 kg  ▓▓▓▓    │ │  Bars scaled to max value
│ │ Deadlift           190 kg  ▓▓▓▓▓▓  │ │
│ └─────────────────────────────────────┘ │
│ How this is calculated              › │  13 sp label-2 footer
│                                         │
└─────────────────────────────────────────┘  768 dp

┌─────────────────────────────────────────┐  768-852 dp
│ 📅 🏋️ 🍽️ 👤 📊 ⋯                       │  Insights tab active (lime)
│ Today Train Food Body Insights More      │
└─────────────────────────────────────────┘
```

## Component Breakdown

| Component | Dimensions | Notes |
|-----------|-----------|-------|
| Header | 393 × 44 dp | 34 sp title, 13 sp subtitle |
| Segmented control | 361 × 44 dp | 5 tabs, active = surface bg, 11 sp labels |
| KPI band | 361 × 60 dp | 3 tiles (1st: 2 rows, 2nd-3rd: flexible layout), stat label + value |
| Chart card | 361 × 180+ dp | Chart (140 dp height), "How this is calculated" footer link (13 sp) |
| Body map card | 361 × 180 dp | 140 dp SVG rendering, tap muscle for detail |
| **Tab bar** | **393 × 60 dp** | **Insights tab active (accent lime)** |

## Interactions

- **Segmented tab tap:** Animate to new segment (140–220 ms), refresh chart data
- **Chart tap:** Opens detail sheet (trend, formula, table alternative)
- **"How this is calculated" tap:** Opens explanation sheet (formula, inputs, toggles for filtering)
- **Body map tap:** Opens muscle detail (exercises, volume breakdown)

---

# SCREEN 6: MORE

## Wireframe Structure

```
┌─────────────────────────────────────────┐  24 dp
│ More                               ⚙️   │  34 sp header
│ Settings & profile                      │  13 sp subtitle
│                                         │
├─────────────────────────────────────────┤  52 dp
│ ┌─────────────────────────────────────┐ │  Profile row card (68 dp)
│ │ 👤 Alex Johnson        Goal: 73 kg › │ │  56 dp avatar, flex, chevron
│ └─────────────────────────────────────┘ │  Tap opens profile editor
│                                         │
│ MODULES                                 │  Section header (11 sp / 600 / label-3)
│ ┌─────────────────────────────────────┐ │  Uppercase, 22 dp spacing above
│ │ 🏋️ Training                    ✓   │ │  44 dp × row, icon (32 dp) left
│ ├─────────────────────────────────────┤ │  Tap: toggle enable/disable
│ │ 🍽️ Nutrition                   ✓   │ │
│ ├─────────────────────────────────────┤ │
│ │ ❤️ Vitals                      ✓   │ │
│ ├─────────────────────────────────────┤ │
│ │ 😴 Sleep                       ○   │ │
│ ├─────────────────────────────────────┤ │
│ │ 🔄 Cycle                       ○   │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ DEVICES & INTEGRATIONS                  │  Section header
│ ┌─────────────────────────────────────┐ │  22 dp spacing above
│ │ 📱 Galaxy Watch 7                 › │ │  44 dp × row
│ │    Connected · Last sync 2m ago      │ │  Icon (32 dp) left
│ ├─────────────────────────────────────┤ │  Subtitle line (12 sp / label-2)
│ │ ⌚ Oura Ring                      › │ │  Tap: device detail sheet
│ │    Connected · Last sync 1h ago      │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ DATA & BACKUPS                          │  Section header
│ ┌─────────────────────────────────────┐ │  22 dp spacing above
│ │ ☁️ Backups                        › │ │  44 dp × row
│ │    Last backup 1h ago                │ │
│ ├─────────────────────────────────────┤ │
│ │ 📋 Data review                    › │ │
│ │    0 items pending                  │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ APPEARANCE                              │  Section header
│ ┌─────────────────────────────────────┐ │  22 dp spacing above
│ │ 🎨 Accent colour          Lime  › │ │  44 dp × row, icon + flex + chevron
│ ├─────────────────────────────────────┤ │  Colour chip shown as 20 dp circle
│ │ 🌙 Theme                   Dark  › │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ PRIVACY & SECURITY                      │  Section header
│ ┌─────────────────────────────────────┐ │  22 dp spacing above
│ │ 🔒 Security                      › │ │  44 dp × row
│ ├─────────────────────────────────────┤ │
│ │ 📄 Privacy policy                › │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ABOUT                                   │  Section header
│ ┌─────────────────────────────────────┐ │  22 dp spacing above
│ │ Version 1.0 (Build 42)            › │ │  44 dp × row
│ ├─────────────────────────────────────┤ │
│ │ Send feedback                     › │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ [22 dp spacing]                         │  Spacer before destructive action
│                                         │
│ DELETE ALL DATA                         │  Red-ink, separate section
│ ┌─────────────────────────────────────┐ │  44 dp × row, red-ink text
│ │ 🗑️ Delete all data         ✓      │ │  Tap: confirmation dialog
│ └─────────────────────────────────────┘ │
│                                         │
└─────────────────────────────────────────┘  768 dp

┌─────────────────────────────────────────┐  768-852 dp
│ 📅 🏋️ 🍽️ 👤 📊 ⋯                       │  More tab active (lime)
│ Today Train Food Body Insights More      │
└─────────────────────────────────────────┘
```

## Component Breakdown

| Component | Dimensions | Notes |
|-----------|-----------|-------|
| Header | 393 × 44 dp | 34 sp title, 13 sp subtitle |
| Profile row | 361 × 68 dp | 56 dp avatar (surface-2 bg), flex (name + goal), chevron |
| Section | 361 × variable | Header (11 sp / 600 / label-3, uppercase, 22 dp above), inset rows |
| Setting row | 361 × 44 dp | 32 dp icon (surface-2 bg), flex (title ± subtitle), chevron or toggle or status glyph |
| Colour chip | 20 dp circle | Accent colour preview (20 dp, border) |
| Destructive section | 361 × 44 dp | 22 dp spacing above, red-ink text, inset row |
| **Tab bar** | **393 × 60 dp** | **More tab active (accent lime)** |

## Interactions

- **Profile row tap:** Profile editor sheet
- **Module row tap:** Toggle sheet (why disable? confirm)
- **Device row tap:** Device detail sheet (last sync, permissions, disconnect)
- **Backups row tap:** Backup management sheet
- **Data review row tap:** Review inbox (pending data review items)
- **Accent colour tap:** Colour picker sheet (9 presets + custom)
- **Theme row tap:** Toggle Dark / Light (instant switch)
- **Delete data tap:** Confirmation dialog (irreversible warning)

---

## Cross-Tab Consistency

### Bottom Tab Bar (All Screens)

```
┌─────────────────────────────────────────┐  768 dp
│ 📅  🏋️  🍽️  👤  📊  ⋯                  │
│ Today Train Food Body Insights More      │  11 sp labels (caption)
│                                         │  36 dp icons (emojis or custom)
│ [Active tab: acc text + icon]            │  Accent lime on active tab
│ [Inactive tab: label-3 text + icon]      │  Gray on inactive tabs
│                                         │
│ 60 dp total height                      │
│ 8 dp padding top (content area)          │
│ 12 dp padding bottom (device safe area)  │
└─────────────────────────────────────────┘  852 dp (device bottom)
```

### Tab Badge (Sync Status)

If sync is paused or data review pending:
- 8 dp orange dot on **Today** tab
- 8 dp orange dot on **More** tab (data review)
- Dot position: top-right of tab icon (28 dp from left, 8 dp from top of tab)

---

## Spacing Reference Grid

```
Vertical spacing between cards: 12 dp
Horizontal content padding: 16 dp (left & right)
Card internal padding: 16 dp
Row height (inset list items): 44 dp minimum
Section header spacing above: 22 dp
Section header to first row: 8 dp
Separator height: 0.5 dp
Chip gap: 8 dp (overflow to next line)
Icon size (setting rows): 32 × 32 dp
Avatar size: 56 × 56 dp
Mini body map (plan row): 46 × 46 dp
Thumbnail (workout/meal): 44 × 44 dp (optional img) or 50 × 50 dp (session)
Ring sizes: 96 dp (steps), 120 dp (nutrition/weight)
Stat box (grid tile): 50% width - 6 dp gap
Button height: 44 dp minimum
Tab bar height: 60 dp total (8 dp padding + 36 dp icon + 11 sp label + 5 dp gap)
```

---

## Colour Key

- `bg` = #000000 (page background)
- `surface` = #1C1C1E (card background)
- `surface-2` = #2C2C2E (secondary surfaces, tabs, icons)
- `label` = #FFFFFF (primary text)
- `label-2` = rgba(235, 235, 245, 0.60) (secondary text, subheader)
- `label-3` = rgba(235, 235, 245, 0.32) (tertiary text, section headers)
- `acc` = #A3E635 (accent lime, active state)
- `sep` = rgba(84, 84, 88, 0.60) (separator line, 0.5 dp)
- `gold` = #FFB84D (status: positive/trending)
- `red` = #FF453A (status: destructive, only for user alerts)

---

## Typography Key

- **34 sp / 700** — Page header ("Good morning", "Training")
- **13 sp / 600 / label-3** — Section header ("MODULES", "DEVICES")
- **15 sp / 600** — Card header title, row label (bold)
- **13 sp / 400** — Body text, card content, row value
- **11 sp / 400** — Stat labels, captions, timestamps
- **13 sp / 500** — Chip text
- **20 sp / 600** — Stat value (KPI band)
- **36 sp / 700** — Large metric value (weight, calories remaining)

