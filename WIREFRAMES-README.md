# openHealth — Wireframes Package

**Version:** 0.1  
**Date:** 2026-11-21  
**Device:** Android 393 × 852 dp (baseline)  
**Reference:** DESIGN.md, pr.md §5–6, Tech-Stack.md

---

## Package Contents

This package contains professional, production-ready wireframes for all 6 root screens of openHealth, plus comprehensive specifications and implementation guidance.

### Files Overview

| File | Type | Purpose |
|------|------|---------|
| `wireframe-today.svg` | SVG diagram | Today screen (home, readiness, nutrition, steps, weight) |
| `wireframe-train.svg` | SVG diagram | Train screen (programmes, workouts, stats) |
| `wireframe-food.svg` | SVG diagram | Food screen (meals, macros, logging) |
| `wireframe-body.svg` | SVG diagram | Body screen (measurements, composition, vitals) |
| `wireframe-insights.svg` | SVG diagram | Insights screen (trends, charts, analytics) |
| `wireframe-more.svg` | SVG diagram | More screen (settings, profile, modules) |
| `WIREFRAMES-DETAILED.md` | Specification | Complete layout details, ASCII diagrams, measurements, interactions |
| `wireframes.html` | Interactive | Earlier unified mockup (for reference) |
| `WIREFRAMES.md` | Specification | Detailed screen recipes, review gate checklist |

---

## How to Use These Wireframes

### 1. **For Design Review & Stakeholder Approval**

**Use:** `wireframe-*.svg` files (open in browser or Figma)

- Each SVG is self-contained and shows the complete screen layout
- 393 × 852 dp device frame with proper proportions
- Safe areas marked (24 dp top, 60 dp bottom for tab bar)
- Component hierarchy, spacing, and colour coding visible

**What you see:**
- Dark theme background (#000000 page, #1C1C1E cards)
- Accent lime (#A3E635) on active elements (Today tab highlighted)
- Proper card spacing (12 dp between cards)
- Realistic component arrangements (rings, bars, lists)
- 6-tab bottom navigation bar

### 2. **For Implementation (Compose Code)**

**Use:** `WIREFRAMES-DETAILED.md` + `COMPOSE-GUIDE.md`

Each screen in WIREFRAMES-DETAILED.md includes:
- ASCII wireframe showing exact layout
- Component dimensions and spacing values
- Colour notation (use from DESIGN.md)
- Typography scale (11 sp caption → 34 sp header)
- Interaction patterns
- State variations

**Example from Today screen:**

```
│ 🌙 Readiness                      72%   │  64 dp card height
│ ┌──────────────────────────────────┐   │
│ │ Sleep: 7h 32m  │ HRV: 48 ms │ RH: 52│  Chip row (3 × variable width)
│ └──────────────────────────────────┘   │
│ You're ready for a hard session today.  │  13 sp label-2, text in body
```

**Measurements:**
- Card padding: 16 dp
- Chip gap: 8 dp
- Row height: 44 dp (minimum touch target)
- Separator: 0.5 dp (0.5 dp color: sep)

### 3. **For Quality Assurance & Review Gate**

**Use:** `WIREFRAMES.md` §end (review gate checklist)

Before shipping each screen, verify:

```
- [ ] Every colour from DESIGN.md §2 (no hardcodes)
- [ ] Every size from DESIGN.md §3–4
- [ ] 44 dp minimum touch targets (checked at 1.3× font scale)
- [ ] One primary action per screen
- [ ] Light + dark themes verified
- [ ] High contrast + reduced motion OK
- [ ] Colour-blind simulation passed
- [ ] Every number is tappable (provenance)
- [ ] Weight-neutral mode checked
- [ ] RTL verified
```

---

## Screen Layouts at a Glance

### Today (Home)
**Primary card:** Readiness score + week strip  
**Key elements:**
- Large header (34 sp "Good morning")
- Connection banner (optional, dismissible)
- Readiness card (score, 3 chips, explanation)
- Week strip (7 day bubbles, today accent)
- Today's plan row (mini body map, name, **Start** button)
- Nutrition ring (120 dp, macros bars)
- Steps ring (96 dp)
- Weight card (trend, sparkline with goal)
- Quick actions (3×2 grid, 6 tiles)
- **Bottom:** 60 dp tab bar (Today active, lime accent)

**Key measurements:**
- Header: 44 dp
- Cards: 361 × variable (16 dp padding each side)
- Rings: 96–120 dp
- Week bubbles: 32 × 40 dp (8 dp gap)
- Tab bar: 60 dp total (8 dp padding + 36 dp icon + 16 dp label + 5 dp gap)

---

### Train (Workouts)
**Primary card:** Week strip + today's session  
**Key elements:**
- Header (34 sp "Training")
- Week strip (7 day bubbles)
- Today's plan row (mini body map + plan details + **chevron**)
- Active programmes card (inset list, week X of Y)
- Recent workouts card (50 dp thumbnail left, name, duration, sets)
- This month stats (2 × 2 grid: sessions, volume, duration, effort)
- **Bottom:** 60 dp tab bar (Train active, lime accent)

**Key measurements:**
- Thumbnail: 50 × 50 dp
- Stat box: 50% width - 6 dp gap
- Row height: 44 dp (inset list)

---

### Food (Meals)
**Primary card:** Energy ring + macros  
**Key elements:**
- Header (34 sp "Nutrition") + scan button (44 dp trailing)
- Date strip (7 day bubbles, tap to jump)
- Energy card (120 dp ring, 3 macro bars: protein, carbs, fat)
- Meal sections (Breakfast, Lunch, Dinner, Snacks)
  - Each: inset list of 44 dp meal rows (image left, name, time, calories)
  - Swipe-left to delete (undo toast)
- **Add meal** button (44 dp, primary)
- **Bottom:** 60 dp tab bar (Food active, lime accent)

**Key measurements:**
- Meal image: 44 × 44 dp
- Ring: 120 dp
- Bar height: 4 dp
- Row: 44 dp min

---

### Body (Measurements)
**Primary card:** Weight trend + silhouette  
**Key elements:**
- Header (34 sp "Body")
- Weight card (36 sp large value, delta + goal, 90-day sparkline)
- Measurements card (silhouette + 7 anchors: Neck, Waist, Hip, Chest, Arm, Thigh, Calf)
- Composition tile grid (2 × 2: Body fat %, lean mass, muscle, water)
- Vitals card (inset list: HR, BP, temperature, chevron on each)
- **Bottom:** 60 dp tab bar (Body active, lime accent)

**Key measurements:**
- Silhouette: 48 sp emoji
- Stat tile: 50% width - 6 dp gap
- Vitals row: 44 dp min

---

### Insights (Analytics)
**Primary card:** KPI band + charts  
**Key elements:**
- Header (34 sp "Insights")
- Segmented control (5 tabs: Overview, Training, Sleep, Body, Food)
  - Active = surface bg, 11 sp labels
- KPI band (3 tiles: volume, effort, sessions)
- Chart cards (1 per metric, 140–180 dp each):
  - Volume trend (line chart placeholder)
  - Training heatmap (11 dp cells, 2 dp gap, 5-step accent ramp)
  - Muscle balance (body map SVG, tap for detail)
  - Top lifts (bars scaled to max)
  - Footer: "How this is calculated ›" (13 sp link)
- **Bottom:** 60 dp tab bar (Insights active, lime accent)

**Key measurements:**
- Heatmap cells: 11 × 11 dp, 2 dp gap
- Chart card: 361 × 140–180 dp
- Chart height: 90 dp (SVG/canvas)

---

### More (Settings)
**Primary card:** Profile row  
**Key elements:**
- Header (34 sp "More")
- Profile row (56 dp avatar, name, goal, chevron)
- Section: Modules (Training ✓, Nutrition ✓, Vitals ✓, Sleep ○, Cycle ○)
  - Toggle rows (32 dp icon, name, status glyph)
  - Tap to enable/disable
- Section: Devices & Integrations (connected devices, last sync)
- Section: Data & Backups (backups, data review)
- Section: Appearance (accent colour chip, theme toggle)
- Section: Privacy & Security (passphrase, policies)
- Section: About (version, feedback, open source)
- **Destructive group** (22 dp spacing above, red-ink):
  - Delete all data row (glyph, red text, confirmation)
- **Bottom:** 60 dp tab bar (More active, lime accent)

**Key measurements:**
- Avatar: 56 × 56 dp
- Setting icon: 32 × 32 dp
- Colour chip: 20 dp circle
- Section header: 10 sp / 600 / label-3, 22 dp spacing above first row
- Row: 44 dp min

---

## Device Frame Standards

All wireframes follow these conventions:

```
Device: 393 × 852 dp (Android baseline, Pixel 5 dimensions)

Safe areas:
- Top: 24 dp (status bar clearance)
- Bottom: 60 dp (tab bar + 12 dp device safe area)
- Left & right: 16 dp content padding (card width: 361 dp)

Content area: 393 × 768 dp (from 24 dp to 792 dp, excluding tab bar)

Spacing grid:
- Vertical between cards: 12 dp
- Horizontal padding: 16 dp (all sides)
- Card internal padding: 16 dp
- Row height (inset list): 44 dp (minimum touch target)
- Separator: 0.5 dp hairline

Colour scheme (dark theme, canonical):
- Background: #000000
- Card: #1C1C1E
- Secondary: #2C2C2E
- Text: #FFFFFF (primary), rgba(235,235,245,0.60) (secondary)
- Accent: #A3E635 (lime, active state)
- Separator: rgba(84,84,88,0.60) (gray, 0.5 dp)
```

---

## Component Library Reference

Every wireframe uses only these 10 components (see COMPOSE-GUIDE.md):

1. **OHCard** — Surface bg, 12 dp radius, separator border, padding
2. **CardHeader** — Icon + title + value row, bottom separator
3. **OHRowItem** — Label + value, 44 dp min, bottom separator
4. **OHPrimaryButton** — Accent bg, 44 dp min, press animation (140 ms)
5. **OHProgressRing** — Canvas ring chart (96–120 dp), center text
6. **OHChip** — Selected/unselected bg, 20 dp border radius
7. **WeekStrip** — 7 × 32×40 dp bubbles, today accent, 8 dp gap
8. **OHBottomNavigation** — 6 tabs, 36 dp icons, 11 sp labels, accent on active
9. **OHBottomSheet** — Material 3, dismissible, header + content
10. **Stat box** — 2×2 grid tiles, 11 sp label, 20 sp value

**Rule:** No other components exist. If a screen seems to need a new variant, the screen is wrong. Edit this wireframe first.

---

## Interactions & Flows

### Logging Loop (Priority: MVP)

| Action | Budget | Design |
|--------|--------|--------|
| Log a set | <2 s | Weight/reps pre-filled, one 38 dp tick per set, rest timer auto-start, next target above row |
| Log food | <5 s | Search or scan → portion sheet (stepper) → log. Meal chosen by time of day, editable in one tap |
| Log weight | 3 taps, <5 s | Card value repeats last, stepper (0.1 increment), save. Trend updates in place |
| Log water | 1 tap | Quick-action chip logs last container; quick-add sheet shows other sizes |

### Navigation Patterns

- **Tab tap:** Slide to new screen (140–220 ms animation)
- **Card tap:** Open detail sheet over current screen (preserves scroll position)
- **Back/close:** Swipe down or X button on sheet
- **Swipe left on row:** Delete with undo toast (3–5 s)
- **Pull-to-refresh:** Trigger sync, show "Syncing..." status

---

## Review Checklist (Before Ship)

```
Colour:
- [ ] Every hex comes from DESIGN.md §2.12 (no hardcodes)
- [ ] Dark theme: all text ≥ 4.5:1 on actual bg
- [ ] Light theme: all text ≥ 4.5:1 on light bg
- [ ] No colour-only information (use patterns, icons, text)

Size & Layout:
- [ ] Every size from DESIGN.md §3–4 (no arbitrary 12.5 dp)
- [ ] All touch targets ≥ 44 dp (measured at 1.3× font scale)
- [ ] Safe areas respected (24 dp top, 60 dp bottom)
- [ ] No overflow at max font scale

Interaction:
- [ ] One primary action per screen (button or chip)
- [ ] One primary filled element per card (small data marks OK)
- [ ] All numbers are tappable (provenance placeholder)
- [ ] Destructive actions have undo (3–5 s toast)
- [ ] Every list has empty state ("No items yet — add one?")

Accessibility:
- [ ] Every row is single a11y node (merge descendants)
- [ ] Every image has content description
- [ ] High contrast mode verified
- [ ] Reduced motion: animations disabled, instant states
- [ ] Colour-blind (deuteranopia): no info lost
- [ ] RTL: no hardcoded start/end or left/right
- [ ] 1.3× font scale: no layout break, all targets ≥ 44 dp

Content:
- [ ] No wording "risk", "warn", "screen" (§pr.md §12.1)
- [ ] Clinical values show range + citation (if shown)
- [ ] Weight-neutral mode checked (hide/adjust as needed)
- [ ] Every chart has 1-sentence description + table alt
- [ ] Every formula has explanation sheet

Data:
- [ ] Every value has source, device, method, timestamp (provenance)
- [ ] All data local-first (works offline)
- [ ] Backfill safe (rollups rebuildable from observations)
```

---

## Next Steps for Developers

1. **Open** `wireframe-*.svg` in browser or Figma for visual reference
2. **Read** `WIREFRAMES-DETAILED.md` for each screen's exact layout
3. **Copy** component recipes from `COMPOSE-GUIDE.md`
4. **Build** in order: Today → Train → Food → Body → Insights → More
5. **Test** each screen against review checklist before commit
6. **Verify** both themes (dark canonical, light sibling) pass accessibility

---

## File Formats

**SVG wireframes:** View in any modern browser or Figma. Fully editable vector format.

**Markdown specs:** Read in GitHub or any text editor. Contains ASCII diagrams, measurements, component breakdowns.

**HTML mockup:** `wireframes.html` (earlier deliverable) shows all 6 screens with unified tab bar toggle. Use for quick visual reference or demo to stakeholders. Not the primary wireframe source.

---

## Questions & Troubleshooting

**Q: Can I edit the wireframes?**  
A: Yes! SVGs are editable in Figma or Illustrator. For measurements or color updates, also edit the corresponding section in WIREFRAMES-DETAILED.md.

**Q: What if a screen needs a new component?**  
A: File an issue titled "New component: X (needed for screen Y)". Update DESIGN.md, WIREFRAMES-DETAILED.md, and COMPOSE-GUIDE.md together. Never ship undocumented components.

**Q: Can I add rounded corners or transparency to cards?**  
A: Check DESIGN.md §2 (colour) and §4 (shape). Card radius is 12 dp (frozen). Transparency values are fixed (e.g., label-2 = 0.60 opacity). Edit the document first.

**Q: How do I test responsiveness?**  
A: Zoom browser to 1.3× (200% user scaling) and verify no layouts break. Check Firefox's responsive design mode (Pixel 5 preset = 393 × 852 dp).

---

## References

- **DESIGN.md** — Design token reference (colours, type, spacing, shapes)
- **COMPOSE-GUIDE.md** — Kotlin/Jetpack Compose code recipes
- **pr.md** — Product contract & feature spec (behaviour authority)
- **Tech-Stack.md** — Stack decisions & portability rules
- **BUILD-ROADMAP.md** — Phase plan & effort estimates

