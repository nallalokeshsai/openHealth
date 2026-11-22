# openHealth — Product Requirements & Feature Specification

**Version:** 0.3 (draft for build planning)  
**Status:** Living document  
**Last revision:** 2026-11-22 — five-tab IA (5), tiered onboarding and splash
(6.1), dynamic body model (6.8), Garmin as the watch (6.18, 13.8). Earlier
2026-11-20 — platform pass: the first releases are Android
only (0.1), iOS/web deferred behind a gate (13.17), section 8.1 superseded by
`Tech-Stack.md`. Previous pass: MVP retagged, cut list recorded (14.5),
missing sections added (6.27–6.31, 7.9, 8.7), external constraints written
down (12).  
**Product:** openHealth, a private, local-first home for every health signal  
**Reference library:** [`references/`](references/README.md)

---

## 0. How to use this document

This is the product north star and the feature contract. It is deliberately
broader than the first release: it describes the whole product so early
decisions do not paint us into a corner, while every feature is tagged so we
know what ships when.

Priority tags:

- **MVP** — required for the first usable release.
- **V1** — the first “this is genuinely great” release.
- **Later** — valuable, but explicitly after the core is trusted.

The rule for every feature: if it does not make the daily loop faster, the
trend clearer, or the data more trustworthy, it waits.

---

## 0.1 Platform scope

**The first releases are Android only.** Phases 0 to 2 ship an Android phone
app, with Wear OS added in Phase 2. iOS, watchOS, the web client and desktop
are deferred — not cancelled. Starting iOS is an explicit gate (13.17 below,
`BUILD-PLAN.md` section 2).

What that changes in this document:

- Any feature below that names HealthKit, Apple Watch, iOS or a browser build
  is **Later**, whatever tag it carries, until the iOS gate opens. Health
  Connect is the platform bus for the first release.
- File-based imports stay fully in scope: an Apple Health export, a Garmin or
  Strava archive, FIT/GPX/TCX and CSV are read from the file system and need
  no vendor platform.
- Nothing in the data model, the formats or the server is Android-specific.
  The canonical schema keeps its `healthkit` source value and every
  iOS-shaped field, so the port is additive and never a migration.
- `Tech-Stack.md` records how this is built and supersedes section 8 where
  the two overlap.

---

## 1. Vision

**One private app for the whole of your health.**

Most people’s health data is scattered across a food app, a gym app, a watch
app, a scale app, a glucose monitor, a running app, a notes app and a portal
they can never find again. openHealth brings it into one timeline, one daily
loop, and one data model that the person actually owns.

> **North star:** open the app, know what to do today, log it in seconds,
> understand the trend, and own every byte.

openHealth is:

- **One app, not eight.** Nutrition, training, steps, yoga, body metrics,
  vitals, activities and recovery share one timeline and one identity.
- **Local-first.** Every core action works with no network. The server is a
  sync target and a backup, not a gatekeeper.
- **Provenance-first.** Every number knows where it came from, when, in what
  unit, and how much to trust it.
- **Modular.** A lean training log and a full clinical dashboard are the same
  build with different modules switched on.
- **Open.** Open formats, open API, open exports, optional self-hosting, and no
  lock-in.
- **Calm.** No ads, no streaks designed to guilt you, no dark patterns, no
  telemetry you did not ask for.

---

## 2. Product principles

### 2.1 One daily loop

The product wins or loses on the loop:

1. **Today** shows what matters now: readiness, planned session, calories and
   macros left, steps, water, any medication or vitals due.
2. **Log** takes two taps for the common case.
3. **Trend** answers “am I moving toward what I care about?”
4. **Leave** without hunting through menus.

Every module plugs into that loop. Deep analysis lives one level down.

### 2.2 Provenance before prettiness

Every observation carries:

- a canonical type and unit;
- a start and end time;
- the user’s local date and timezone;
- the source (manual, phone, watch, scale, cuff, CGM, lab, import, API);
- the device and method where known;
- context (resting, post-meal, fasting, pre-workout, arm, position);
- a confidence or quality hint;
- an idempotency key and the raw ingestion record.

A number without provenance is a decoration. We do not ship decorations.

### 2.3 Local-first, server optional

- The device holds the truth it can see.
- The server is optional: self-hosted, family-hosted, or a managed instance.
- Sync is incremental, conflict-aware, and never blocks logging.
- Export is a first-class feature, not a support ticket.

### 2.4 Modular depth

The app is a set of modules:

- Training
- Nutrition
- Steps and daily activity
- Yoga and mobility
- Body
- Vitals
- Sleep and recovery
- Activities
- Mental health and habits
- Cycle and sexual health
- Family and caregiving
- AI Coach
- Integrations and data

Each module can be switched off without deleting data. The core
(profile, timeline, logging, export) is always on.

### 2.5 Clinical correctness, not clinical diagnosis

We show reference ranges, trends, context and doctor-ready reports. We do not
diagnose, prescribe, dose insulin, or replace a clinician. The product is a
wellness and record-keeping tool unless a future regulated version is
deliberately built and certified.

### 2.6 Evidence over hype

- Formulas are documented and visible.
- Confidence and uncertainty are shown where they matter.
- AI output is optional, validated, and auditable.
- We prefer a simple, correct calculation over a black box.

### 2.7 The quality bar

“Best in the world” means:

- **Fast:** cold start under 1.5 s, log a set under 2 s, log a food under 5 s.
- **Quiet:** no spinner where a skeleton or an optimistic update will do.
- **Reversible:** every destructive action has undo.
- **Accessible:** WCAG 2.2 AA, screen readers, dynamic type, reduced motion,
  color-blind-safe charts, full RTL.
- **Offline:** core logging never waits for a network.
- **Honest:** no fake precision, no “burned calories” theatre.
- **Portable:** full export in open formats, always.

---

## 3. Target users

| Persona | What they need | What wins them |
|---|---|---|
| **The self-hoster** | Their data on their own box, no subscription, no telemetry | Docker one-liner, passkeys, local-first, export, no vendor lock-in |
| **The lifter** | Fast set logging, progression, plate math, history | Sub-2-second set logging, auditable progression, supersets, watch support |
| **The runner / cyclist** | GPS, pace/power/HR, maps, training load | FIT/GPX/TCX import, zones, load trends, Strava import/export |
| **The nutrition-focused** | Food logging, macros, micronutrients, recipes | Barcode, Open Food Facts/USDA, recipes, meal plans, adaptive TDEE |
| **The vitals / condition manager** | BP, glucose, weight, meds, labs, doctor reports | Context-rich observations, reference ranges, FHIR/PDF export |
| **The family caregiver** | Multiple profiles, permissions, shared visibility | Granular consent, caregiver mode, audit log |
| **The data tinkerer** | API, MCP, webhooks, raw export | Open API, token scopes, MCP server, Parquet/CSV/FHIR |
| **The beginner** | Not being overwhelmed | Presets, guided onboarding, smart defaults, one clear next action |

---

## 4. The daily loop in detail

### Morning

- Readiness card: sleep, resting HR, HRV, fatigue, soreness, cycle phase.
- Weight check-in: one tap, trend line, goal context.
- Today’s plan: workout, yoga flow, rest day, or activity.
- Medications and supplements due.
- Optional blood pressure or glucose reading.

### During the day

- Steps and active minutes, quietly.
- Food and water logging in two taps.
- Workout or yoga session with rest timers and cues.
- Any vitals reading, with context.

### Evening

- Session summary: volume, PRs, effort, how it felt.
- Nutrition summary: calories, macros, micronutrients, water.
- Sleep wind-down: bedtime reminder, screen-free cue, journal prompt.
- One-line journal or mood check-in.

### Weekly

- Weekly review: training load, consistency, weight trend, nutrition
  adherence, sleep debt, steps.
- Next week’s plan, with a suggested deload or progression.
- Optional coach review.

### Monthly / quarterly

- Long-term trends and correlations.
- Doctor-ready report.
- Backup and export reminder.
- Goal review.

---

## 5. Information architecture

Primary navigation, designed so the common loop is always one tap away:

Revised 2026-11-22: five tabs, Train as the raised centre (`DESIGN.md` §5.15).
Settings is a top-bar gear, not a tab. The first launch runs onboarding (6.1);
every later launch shows a splash, then Today.

| Tab | What lives there |
|---|---|
| **Today** | Readiness, plan, dynamic body model with recovery colours, calories in/out/net and goal ETA, steps, water, due meds/vitals |
| **Vitals** | Weight, measurements, composition, photos, vitals, sleep, cycle |
| **Train** (centre) | Gym, yoga, cardio and imported activities (Strava archive, FIT/GPX/TCX), programmes, routines, history, recovery map, programme coverage map |
| **Nutrition** | Diary, macros, micros, water, recipes, meal plans, fasting, supplements |
| **Stats** | Trends, body and workout statistics, correlations, weekly review, reports, coach |
| **Settings** (top bar) | Profile, modules, devices, integrations, data, privacy, appearance |

Global layers (quick add is the top-bar plus):

- **Quick add** from anywhere: food, water, weight, set, note, vitals.
- **Timeline**: every event, newest first, filterable by type and source.
- **Search**: global search and command palette.
- **Sheets, not dead ends**: every detail opens in a dismissible sheet with
  back behavior preserved.

---

## 6. Feature specification

### 6.1 Onboarding and profile

**Goal:** a new person reaches their first meaningful log in under three
minutes.

**MVP**

- Name, date of birth, sex or gender (optional, with “prefer not to say”),
  height, weight, units, timezone, language.
- Goal: lose fat, maintain, gain muscle, improve health, manage a condition,
  or “just track.”
- Activity level and training experience.
- Dietary pattern and allergies (optional).
- Module preset: “Just training,” “Training and body,” “Everything.”
- Privacy choice: local-only, self-hosted server, or managed server.
- Passkey or password sign-in on the server; local-only needs no account.
- Demo mode with realistic sample data.
- **Tiered intake (revised 2026-11-22).** Tier 1 is required and takes about a
  minute: name, date of birth, sex at birth, height, weight, units, goal. Tier 2
  is recommended: activity level, training experience, equipment, dietary
  pattern, allergies, sleep schedule, modules. Tier 3 is optional and can be
  deferred: body fat %, body composition source, tape measurements at neck,
  shoulders, chest, waist, hips, upper arm, forearm, thigh, calf, wrist and ankle,
  resting heart rate, blood pressure, injuries and limitations, medications, cycle
  tracking, a baseline photo, and goal weight with target date. Each field says
  what uses it. Anything skipped is asked once, later, where it is used.
- Under 18 or an aggressive goal rate shows a neutral notice, not a refusal (13.14).
- Weight-neutral mode is offered during onboarding.
- **Splash.** Every launch after onboarding shows the Android system splash, then
  Today, without waiting on a network or a sync.

**V1**

- Import from Health Connect, and from exported files: Apple Health, Fitbit,
  Garmin, Withings, Oura, WHOOP, Strava, CSV, and other apps.
- Guided first-week plan.
- “What do you want to improve first?” personalization.
- Unit and language auto-detection with an easy override.
- Accessibility setup: text size, contrast, motion, screen reader hints.

**Later**

- Clinician-shared profile.
- Family/household setup.

**Acceptance criteria**

- A new user can create a profile, set units and goals, and log a meal,
  workout or weight in under three minutes on a mid-range phone.
- Every optional field can be skipped.
- The app works with no network from the first screen.

---

### 6.2 Today (Home)

**Goal:** answer “what should I do now?” in one screen.

**MVP**

- Greeting with date, local time, and a one-line status.
- Readiness card: sleep, resting HR, HRV, fatigue, soreness, cycle phase
  (when those modules are on).
- Today’s plan: workout, yoga, activity, or rest day, with a one-tap start.
- Nutrition ring: calories, protein, carbs, fat, fibre, water.
- Steps ring and active minutes.
- Weight card with trend and goal line.
- Due items: medications, supplements, BP/glucose check, journal.
- Quick actions: log food, water, weight, workout, vitals, note.
- Sync status, offline banner, and a “what’s new” line only when relevant.

**V1**

- Adaptive card ordering based on what the person actually uses.
- “Your week so far” strip: sessions, steps, calories, sleep.
- Contextual nudge: “You slept 5 h 40 m; today’s session is a deload” or
  “You have not logged protein in two days.”
- Widgets and watch complications that mirror the Today card.
- One-line journal prompt in the evening.

**Later**

- Voice quick add.
- Natural-language logging (“two eggs and toast”).
- Home screen customization with drag-and-drop cards.

**Acceptance criteria**

- The most common actions are reachable in one tap from Today.
- The screen loads from cache in under 300 ms on a warm start.
- Nothing on Today is a dead end; every card opens a useful detail.

---

### 6.3 Nutrition and diet

**Goal:** the fastest honest food diary, with the depth of a nutrition app
when you want it.

**MVP**

- Food diary by day and meal (breakfast, lunch, dinner, snacks; custom meals).
- Search local foods, Open Food Facts, and USDA FoodData Central. Other
  national databases arrive in V1, once each licence position is settled (12.2).
- A bundled offline index: the most-used foods for the user's region plus
  everything they have ever logged, so search and barcode lookup work on a
  plane. The full remote corpus is fetched and cached on demand, never shipped
  whole: Open Food Facts alone is far too large to bundle.
- Barcode scanning with offline fallback and a clear “found / not found” state.
- Serving units: grams, millilitres, household measures, servings, portions.
- Quick add calories and macros.
- Custom foods and recipes with ingredient breakdown.
- Copy a meal or a day; duplicate a recipe.
- Daily targets for calories, protein, carbs, fat, fibre, water.
- Micronutrient panel for the nutrients the data supports.
- Water tracking with configurable containers.
- Fasting timer, optional and non-judgmental.
- Allergies and dietary patterns: vegetarian, vegan, halal, kosher, gluten-free,
  lactose-free, nut-free, and custom.
- Export/import CSV and JSON; recipe sharing as a file or QR code.

**V1**

- National food databases: German BLS, Swiss Food Composition, Canadian
  Nutrient File, each carrying its licence, version and attribution.
- Meal plans and weekly planning.
- Restaurant and branded menu items (manual or community).
- Photo-assisted portion estimation, clearly labelled as an estimate and
  always editable.
- Voice and chat logging, with a review step.
- Nutrient timing and pre/post-workout suggestions.
- Supplement logging with doses and schedules.
- “What changed?” weekly nutrition review.
- Barcode and label OCR for local products.
- Household recipes with serving scaling.

**Later**

- Grocery list generation and pantry integration.
- Lab-informed nutrition suggestions (for example, iron or vitamin D), only
  as education, never as medical advice.

**Data sources**

- Open Food Facts (ODbL), USDA FoodData Central (CC0), German BLS (CC BY 4.0),
  Swiss Food Composition Database, Canadian Nutrient File, Mealie/Tandoor
  recipes, user-created foods.

**Acceptance criteria**

- A barcode scan to a logged food takes under five seconds on a warm path.
- Every entry shows its source and whether it was verified by the user.
- A day’s totals match the sum of its entries exactly, with no hidden rounding.
- The diary works offline for foods already cached.

---

### 6.4 Calories and energy

**Goal:** an energy model that is useful without pretending to be exact.

**MVP**

- BMR with a documented formula (Mifflin-St Jeor by default; alternatives
  visible).
- TDEE from activity level and logged activity.
- MET-based exercise calories from the Compendium of Physical Activities.
- Goal modes: lose, maintain, gain, custom rate.
- Safety floors and a warning when the target is too aggressive.
- “Eaten vs burned” shown as a range, not a false single number.
- Weekly average and trend, not just today.

**V1**

- Adaptive TDEE: estimate expenditure from intake and weight trend over 2–4
  weeks, with a confidence indicator.
- Activity calories from steps, workouts, and wearables, deduplicated.
- Protein target based on body weight and training goal.
- Diet break and refeed suggestions.
- Menstrual-cycle-aware energy notes (optional).
- “Why is my target this number?” explanation screen.

**Later**

- Metabolic adaptation tracking.
- Continuous glucose and energy correlation (research-grade, clearly
  experimental).
- Coach-adjusted targets with user approval.

**Acceptance criteria**

- The formula and inputs behind every target are visible in one tap.
- The app never presents a calorie burn as a precise measurement.
- Changing a goal updates the target without rewriting history.

---

### 6.5 Gym and strength training

**Goal:** the best strength-training log in the world, from a first set to a
five-year history.

**MVP**

- Exercise library with muscles, equipment, instructions, media, and
  alternatives.
- Custom exercises with photo, video, and notes.
- Routines by weekday or rotation (A/B/C), with folders and favourites.
- Programmes: multi-week blocks with percentage, RPE, or top-set targets.
  (First to be cut if the date slips: routines plus a progression policy cover
  most people, and the programme builder is V1 anyway.)
- Session flow: start today’s plan, freestyle, or log a past workout.
- Fast set logging: weight, reps, RIR/RPE, done, rest.
- Warm-up ramps, work sets, back-off sets, drop sets, failure flags, and tempo.
  Rest-pause, cluster sets and per-side work are V1: each one changes the set
  model, and the set model has to be right before it is wide.
- Supersets, circuits, and combined routines.
- Plate math for barbells, EZ bars, trap bars, Smith machines, and custom bars.
- One equipment profile: plates, dumbbells, bars. Multiple per-gym profiles
  are V1.
- Progression policies: linear, double progression, and off. The other seven
  (Greyskull LP, steady, triple, undulating, density, ladder, time) are V1.
  The policy interface ships in the MVP so the rest are additive, not a
  rewrite.
- Deloads, stalls, PR detection, estimated 1RM, and per-exercise history.
- Rest timer with sound, vibration, and native alerts.
- Session summary: volume, sets, PRs, duration, notes.
- Edit any saved workout; move it to another date; delete with undo.
- Bodyweight and assisted exercises; band and chain support.
- Notes per exercise, per set, and per session.

**V1**

- Watch support: Wear OS first, then Garmin and Zepp; Apple Watch after the
  iOS gate (0.1).
- Form videos attached to a set, stored privately.
- Rest-pause, cluster sets, per-side work, and per-gym equipment profiles.
- Live Activity / Lock Screen controls.
- Plate-loading suggestions with a visual plate stack.
- Warm-up and work-set auto-regulation from readiness.
- Exercise substitution with a same-muscle backup.
- Programme builder with a visual timeline.
- “Repeat workout,” “save as routine,” and “combine routines.”
- Training max and 5/3/1-style percentage programming.
- Gym check-in with QR/NFC and automatic gym selection.
- Rest-day suggestions and missed-session recovery.
- Exercise-level units (kg or lb per exercise).
- Per-gym exercise variants and machine brands.

**Later**

- Community programmes and coach-authored plans.

**Acceptance criteria**

- A set can be logged in under two seconds with one hand.
- The next target is always explainable: policy, last session, and why.
- No progression rule can advance load after a missed rep.
- A workout recorded offline syncs without duplicates or lost sets.

---

### 6.6 Steps and daily activity

**Goal:** accurate, battery-friendly movement tracking that never
double-counts.

**MVP**

- Phone step counter (hardware sensor where available; fallback detector).
- Health Connect import (HealthKit after the iOS gate, 0.1).
- Daily steps, distance, active calories, active minutes, floors where
  available.
- Goal setting and weekly average.
- Source priority and deduplication: one source wins the day by configured
  priority, never the largest value and never the sum (7.6).
- Local-day rollups with timezone handling.
- Manual correction and “exclude this day.”
- Steps from walking pads and treadmills where the device exposes them.

**V1**

- Watch and band steps via Gadgetbridge-compatible devices.
- Hourly and minute-level charts.
- Streaks and gentle consistency insights, not guilt.
- “Steps after poor sleep” correlation.
- Walking routes and virtual routes.
- Widgets and watch complications.

**Later**

- Activity recognition (walking, running, cycling, in-vehicle) with a
  privacy-preserving on-device classifier.
- Floor climbing and stair goals.
- Family step challenges (opt-in).

**Acceptance criteria**

- The same day never shows two different step totals because two sources
  disagree; the chosen source is visible.
- The app uses less than 2% battery per day for passive step tracking.
- A reboot or app kill does not lose the day’s count.

---

### 6.7 Yoga and mobility

**Goal:** a calm, beautiful, genuinely useful yoga and mobility tracker, not a
video library with a timer bolted on.

**MVP**

- Pose library with name, Sanskrit name, muscles, difficulty, contraindications,
  instructions, and media.
- Flows: ordered poses with hold duration, side, breath cue, and transitions.
- Built-in flows: Sun Salutation A/B, beginner full-body, hip opener, spine,
  post-run, desk reset, bedtime.
- Session player: large timer, voice cues, optional music, pause, skip,
  repeat, and a “switch sides” prompt.
- Manual logging: style, duration, intensity, focus, notes.
- History, weekly minutes, streak, and favourite flows.
- Offline playback of owned media.

**V1**

- Custom flow builder with drag-and-drop.
- Programmes: 7-day, 14-day, 30-day, and custom.
- Breathwork and meditation sessions.
- Mobility tests (sit-and-reach, shoulder, ankle, hip) with progress.
- Voice selection and cue timing.
- Health Connect mindful-session export.
- Watch support for timers and heart rate.
- “Yoga after leg day” and “mobility for desk workers” recommendations.

**Later**

- Adaptive flows based on soreness and readiness.
- Community flows with attribution and licensing.

**Acceptance criteria**

- A flow starts in under three seconds and continues with the screen off.
- Voice cues stay in sync through pause, skip, and background.
- Every pose has a clear “why” and a modification.

---

### 6.8 Body metrics

**Goal:** the clearest possible picture of body change over time.

**MVP**

- Weight check-ins, one or more per day, with a single daily value rule.
- Trend line, weekly rate, goal line, and projected date.
- Body fat, lean mass, water, muscle, bone, BMI, FFMI, BSA.
- Circumference measurements: waist, hips, chest, neck, arms, thighs, calves.
- Progress photos on a timeline with a before/after slider.
- Custom measurements with units, precision, and display preferences.
- Unit handling: kg/lb, cm/in, percentage, and canonical storage.
- CSV import/export, including Apple Health weight exports.
- “One check-in per day” merge rule for sync.
- Dynamic body model: a 2D schematic whose adipose layer follows body fat % and
  tape measurements and thins as they fall; estimate-labelled, hidden in
  weight-neutral mode (`DESIGN.md` §9.1.1).
- Body-fat estimate from tape measurements (US Navy method), tagged as an
  estimate and overridden by any scale or DEXA reading.

**V1**

- Bluetooth scale import (OpenScale-compatible devices).
- Body-fat method tracking (BIA, DEXA, skinfold, Navy) with method-specific
  trends.
- Goal-rate corridor with safety warnings.
- Photo alignment and private gallery.
- Measurement reminders.
- Body-composition change vs strength and nutrition correlation.

**Later**

- Clothing-size tracking.

**Acceptance criteria**

- The trend is never distorted by unit conversion or duplicate entries.
- A photo is stored privately, never in a public share by accident.
- Every measurement knows its method and unit.

---

### 6.9 Vitals and clinical records

**Goal:** a trustworthy personal health record that a clinician can read,
without pretending to be a medical device.

**MVP**

- Blood pressure: systolic, diastolic, pulse, arm, position, cuff, repeated
  readings, and home-average protocol.
- Heart rate: resting, active, max, and manual readings.
- SpO2, respiratory rate, body temperature.
- Blood glucose: mg/dL or mmol/L, fasting/post-meal context, manual and CGM.
- Medications and supplements: dose, schedule, adherence, refill reminder.
- Symptoms and episodes: type, severity, duration, triggers, notes.
- Allergies and conditions.
- Vaccinations.
- Lab results: manual entry and CSV, LOINC-coded where possible, with
  reference ranges and trends. PDF import and parsing is V1.
- Clinical reference ranges with user and clinician overrides. Every range
  records the guideline and region it came from, because BP and glucose
  thresholds genuinely differ between ESC, ACC/AHA and national guidance. A
  range without a citation is an opinion.
- Context-aware charts and averages.
- Doctor-ready PDF report and a plain CSV export.

**V1**

- FHIR R4 and Open mHealth export.
- CGM integration: Dexcom, Libre, Nightscout, xDrip.
- Blood pressure monitor import over Bluetooth.
- Home BP protocol: 2 readings, morning and evening, 7 days.
- Medication interaction warnings: education only, with named sources, a
  licensed data set, and the gate in 12.1.
- Symptom correlation with sleep, food, cycle, and training.
- Lab PDF parsing with review.
- FHIR import from providers.
- Caregiver sharing with granular consent.
- Clinical alerts only where the user explicitly configures them.

**Later**

- SMART on FHIR provider connections.
- Remote patient monitoring workflows.
- Research-grade export and consent.
- Wearable ECG import.
- Insulin and dosing workflows only in a regulated product.

**Acceptance criteria**

- Every vital shows context and source.
- A doctor report is generated in under 30 seconds and is readable without
  the app.
- No alert is ever presented as a diagnosis.
- Data can be exported as plain CSV from the MVP, and as FHIR R4 from V1.

---

### 6.10 Sleep and recovery

**Goal:** connect sleep, training, nutrition and stress into one recovery
picture.

**MVP**

- Sleep duration, bedtime, wake time, and a simple quality rating.
- Sleep stages from wearables where available.
- Sleep debt and consistency.
- Resting heart rate and HRV trends.
- Readiness score with an explanation.
- Fatigue and muscle-recovery map from training.
- Deload suggestions.

**V1**

- Sleep score with transparent inputs.
- Sleep and appetite, training performance, and mood correlations.
- Wind-down routine and bedtime reminder.
- Nap tracking.
- Menstrual-cycle-aware recovery notes.
- “Should I train today?” recommendation with a clear rationale.

**Later**

- Sleep apnea risk screening: a signposted, cited questionnaire the person
  chooses to take, never a passive alert (see 12.1).
- Environmental inputs (temperature, noise) with user permission.

**Acceptance criteria**

- The readiness score is explainable from its inputs.
- A missing wearable never blocks manual sleep logging.
- Recovery suggestions never override the person’s judgment.

---

### 6.11 Activities and Strava

**Goal:** own your activity history and get serious analysis without a social
network.

**MVP**

- Record GPS activities: run, walk, hike, ride, swim, row, ski, and custom.
- Import FIT, GPX, and TCX files.
- Manual activity entry.
- Distance, duration, pace, speed, elevation, heart rate, cadence, power,
  calories, and route.
- Maps with privacy controls and route hiding.
- Splits, laps, and best efforts.
- Heart-rate and power zones.
- Weekly and monthly totals.
- Gear tracking: shoes, bike, components, wear.
- Activity history and filters.
- Export FIT/GPX/TCX and CSV.

**V1**

- Strava import/export (subject to Strava’s API terms).
- Garmin, Polar, Suunto, COROS, and Wahoo import.
- Training load: TRIMP, TSS, rTSS, hrTSS, CTL/ATL/TSB.
- Critical power, FTP, pace zones, and personal bests.
- Route planning and route collections.
- Live tracking link with expiry.
- Segments as personal efforts; global leaderboards later and optional.
- Strength activities linked to gym sessions.

**Later**

- Social feed, clubs, challenges, and leaderboards as an optional module.
- Segment explorer and route popularity.

**Acceptance criteria**

- A recorded activity survives a crash and can be recovered.
- A FIT import preserves laps, sensors, and route.
- Strava data is never required for core functionality.
- The app clearly separates recorded, imported, and manually entered data.

---

### 6.12 Mental health, habits and journal

**Goal:** the emotional and behavioural side of health, without turning it
into a clinical claim.

**MVP**

- Mood check-in with a small, non-judgmental scale.
- One-line journal and tags.
- Habit tracker with flexible schedules.
- Breathing exercises.
- Gratitude and wins.
- Stress and energy check-ins.

**V1**

- Guided journaling prompts.
- Meditation timer with interval bells.
- Validated self-checks (WHO-5, PHQ-9, GAD-7) with crisis resources and clear
  “not a diagnosis” language, only where appropriate.
- Habit streaks with forgiveness and “skip” rules.
- Correlations with sleep, training, cycle and nutrition.

**Later**

- Therapist-shared summaries.
- Voice journaling.

**Acceptance criteria**

- Mental-health content is reviewed and safe.
- Crisis resources are available in the user’s region.
- No streak guilt is used to drive engagement.

---

### 6.13 Cycle and sexual health

**Goal:** a private, useful cycle and reproductive health module that does not
leak.

**MVP**

- Cycle start/end, flow, symptoms, mood, pain, and notes.
- Predictions with uncertainty.
- Cycle-aware training and recovery notes.
- Private by default.

**V1**

- Trying-to-conceive, pregnancy, postpartum, and perimenopause modes.
- Basal body temperature, cervical mucus, ovulation tests.
- Medication and appointment tracking.
- Export and clinician report.
- Partner/caregiver sharing with explicit consent.

**Later**

- Fertility insights with clear non-diagnostic language.
- Perimenopause symptom patterns.
- Research-grade export.

**Acceptance criteria**

- Cycle data is never included in a general share or export without consent.
- Predictions show a range, not a certainty.
- Turning the module off hides it without deleting it.

---

### 6.14 Family and caregivers

**Goal:** health is often shared, but privacy is not negotiable.

**MVP**

- Multiple profiles on one instance.
- Separate data, settings, and goals per profile.
- Optional caregiver mode with read-only or limited access.
- An emergency card: conditions, allergies, medications, blood type and
  contacts, printable and readable without unlocking the app. It is cheap to
  build and it is the one screen that might matter most.

**V1**

- Granular permissions by data category and time range.
- Invite by link or code, with expiry.
- Audit log of who saw or changed what.
- Child and dependent profiles with guardian consent, age-appropriate
  content, growth percentiles in place of BMI, and no calorie targets for
  minors. Adult BMR formulas and adult reference ranges do not apply to
  children and must be blocked, not merely discouraged (13.14).
- Shared medication and appointment reminders.

**Later**

- Household dashboards.
- Clinician accounts with scoped access.

**Acceptance criteria**

- No data is shared without an explicit, revocable grant.
- Every access is logged.
- A revoked grant takes effect immediately.

---

### 6.15 AI Coach

**Goal:** an optional, auditable coach that uses your data to propose changes,
not a chatbot that guesses.

**MVP**

- Off by default.
- Bring-your-own-key or self-hosted provider.
- Reads the profile, plan, recent sessions, nutrition, sleep, and recovery.
- Proposes a plan or changes; never applies them without approval.
- Explains every recommendation.
- Validates exercise ids, sets, reps, loads, and dates against the data model.
- One repair round when output is invalid.
- Conversation history, proposal snapshots, and undo.
- Clear privacy disclosure about what is sent to the provider, by category,
  with a separate opt-in for each. Data read from Health Connect (and from
  HealthKit once iOS ships) is excluded from provider requests unless the
  user opts that category in: platform terms restrict passing it onward, and
  this is a Play Store rejection risk as much as a privacy one (12.3).

**V1**

- Weekly review and adaptation.
- Nutrition and recovery suggestions.
- “Ask my data” questions with citations to the user’s own records.
- MCP tools so external assistants can read and write with scoped tokens.
- Model choice and provider choice.
- Local model support.
- Cost and token estimates.
- Safety filters for medical, eating-disorder, and crisis content.

**Later**

- On-device small models.
- Research-mode experiments with consent.

**Acceptance criteria**

- The coach never diagnoses, prescribes, or doses.
- Every proposal is diffable and reversible.
- The app works fully without the coach.
- A provider outage is a graceful, non-blocking state.

---

### 6.16 Insights and reports

**Goal:** turn logs into understanding.

**MVP**

- Weight, steps, calories, protein, training volume, and sleep trends.
- Weekly and monthly summaries.
- Personal records and milestones.
- Simple correlations with a “correlation is not causation” note.
- Doctor-ready PDF report.
- Export charts as images.

**V1**

- Cross-domain insights: nutrition × training × sleep × recovery × cycle.
- Training load and readiness trends.
- Goal projections with confidence.
- Lab trends with reference bands.
- Custom date ranges and comparisons.
- Shareable progress card with privacy controls.
- Clinician summary with medication adherence and vitals.

**Later**

- Anomaly detection with careful thresholds.
- Predictive alerts for overtraining or illness risk: experimental, opt-in,
  and gated by 12.1.
- Research export with consent.

**Acceptance criteria**

- Every chart has an accessible table alternative.
- Every insight links to the underlying data.
- No insight is presented as medical advice.

---

### 6.17 Data, integrations and interoperability

**Goal:** be the place data comes together, not another silo.

**MVP**

- Local-first storage with full JSON export.
- CSV export for food, workouts, body metrics, and vitals.
- Import from CSV, JSON, and common health exports.
- Health Connect read/write (HealthKit after the iOS gate, 0.1).
- WebDAV backup.
- Encrypted backup file.

**V1**

- Health Connect, Garmin, Fitbit, Withings, Oura, WHOOP, Polar, Suunto,
  COROS, Strava, Dexcom, Libre, Nightscout, Omron; Apple Health by export
  file.
- Open Food Facts, USDA, BLS, Swiss, Canadian Nutrient File.
- Mealie and Tandoor recipe import.
- FHIR R4 and Open mHealth export.
- MCP server with scoped tokens.
- Webhooks for workout finished, PR, weigh-in, and vitals.
- Calendar feed for planned sessions.
- API tokens with category-level scopes.
- Scheduled backups.

**Later**

- SMART on FHIR provider connections.
- Research data export.
- Partner APIs.
- Plugin system for community connectors.

**Acceptance criteria**

- Every integration can be disconnected without deleting history.
- Every imported record keeps its source and raw payload reference.
- Export is complete enough to rebuild the account elsewhere.
- No integration is required for core logging.

---

### 6.18 Devices and wearables

**Goal:** work with the devices people already own, without vendor clouds.

**MVP**

- Phone sensors: steps, motion, GPS, camera for barcodes and food.
- Bluetooth heart-rate straps.
- Bluetooth scales (OpenScale-compatible).
- Bluetooth blood-pressure cuffs.
- Health Connect as the platform bus (HealthKit after the iOS gate, 0.1).

**V1**

- Wear OS: workout, sets, rest timer, heart rate, tiles, complications.
- Apple Watch: the same, after the iOS gate (0.1).
- Garmin (the owner's primary watch, 2026-11-22): FIT import first, then
  Health Connect for whatever Garmin Connect writes to it (verify data types and
  latency before promising any), then a Connect IQ app. Garmin's Health API is
  partner-only and is not assumed. Gadgetbridge's Garmin support is GPL: study
  only, never bundled.
- Zepp OS: relay through the server.
- CGM: Dexcom, Libre, Nightscout.
- Pulse oximeters and thermometers where standard GATT profiles exist.
- Walking pads and treadmills.

**Later**

- Smart rings and sleep buds.
- ECG devices.

**Acceptance criteria**

- A device that disconnects mid-workout loses nothing.
- The app degrades gracefully to manual logging.
- No vendor cloud account is required for core logging. Where an integration
  genuinely needs one (Garmin Connect, Fitbit, Oura, WHOOP, Dexcom Share), the
  app says so plainly before the user connects it.

---

### 6.19 Notifications and reminders

**Goal:** timely, useful, quiet.

**MVP**

- Workout reminders.
- Rest-timer alerts.
- Medication and supplement reminders.
- Weigh-in and measurement reminders.
- Water reminders (off by default).
- Weekly review reminder.
- Quiet hours and per-notification controls.

**V1**

- Sleep wind-down.
- Fasting start/end.
- Lab or appointment reminders.
- “You have not logged in a while” re-engagement, off by default.
- Location-based gym reminder, on-device only, off by default.
- Watch and widget notifications.

**Later**

- Contextual coaching nudges.
- Family reminders.
- Emergency contacts.

**Acceptance criteria**

- Every notification can be turned off in two taps.
- No notification contains sensitive data on the lock screen by default.
- Reminders survive timezone changes.

---

### 6.20 Search and command palette

**Goal:** find anything in two keystrokes.

**MVP**

- Global search across exercises, foods, routines, workouts, notes, and
  settings.
- Recent items and suggestions.
- Filter by type, date, and source.

**V1**

- Command palette: “log 500 ml water,” “start push day,” “add weight 82.4,”
  “show my bench history.”
- Natural-language search.
- Search within a date range.
- Saved searches.

**Later**

- Voice commands.
- Semantic search across notes and reports.

---

### 6.21 Modules and customization

**Goal:** the app fits the person, not the other way around.

**MVP**

- Module toggles: training, nutrition, steps, yoga, body, vitals, sleep,
  activities, mental health, cycle, family, coach, integrations.
- Presets: “Just training,” “Training and body,” “Everything.”
- Turning a module off hides its surfaces but keeps its data.
- Per-module settings and notifications.

**V1**

- Home card customization.
- Custom dashboards.
- Theme, accent, icon, and text size.
- Per-device settings (phone vs tablet vs web).
- “Simple mode” that hides advanced controls.

**Later**

- Community layouts.
- Role-based layouts for coaches and caregivers.

---

### 6.22 Accessibility and internationalization

**Goal:** usable by everyone, everywhere.

**MVP**

- WCAG 2.2 AA: contrast, focus, labels, touch targets, reduced motion.
- Screen-reader labels for every control and chart.
- Dynamic type and large text.
- Color-blind-safe charts and status colors.
- Full RTL support for Arabic, Hebrew, Persian, and Urdu.
- Units: kg/lb, cm/in, km/mi, kcal/kJ, mg/dL/mmol/L.
- Local date, number, and time formats.
- Three languages at launch (English plus two), every string externalized,
  and the community translation pipeline open from day one. Ten shipped badly
  is worse than three shipped well, and exercise, pose and food names are a
  far larger translation problem than UI strings.

**V1**

- 10+ languages, then 25+ as the community pipeline fills.
- Localized food databases and exercise names.
- Voice-over and TalkBack tested flows.
- One-handed mode.
- Haptic patterns for rest and timers.

**Later**

- Captions and transcripts for every instructional video.
- Community translation of exercise, pose and food names, not only UI strings.

---

### 6.23 Security and privacy

**Goal:** health data is the most sensitive data. Treat it that way.

**MVP**

- Local-first storage.
- Encryption in transit (TLS) and at rest where the platform allows.
- Passkeys for server sign-in; optional password.
- No ads, no third-party analytics, no telemetry by default.
- Explicit consent for every integration.
- Full export and account deletion.
- Session management and “sign out everywhere.”
- Audit log for server access.

**V1**

- End-to-end encrypted backup option.
- Encrypted attachments.
- Scoped API tokens and revocable device pairing.
- Caregiver access with expiry and audit.
- Rate limiting, CSRF protection, and secure headers.
- Dependency and image scanning in CI.
- Threat model and security policy.

**Later**

- Client-side encrypted sync.
- Hardware-backed keys.
- Independent security audit.
- HIPAA/GDPR readiness documentation (not a compliance claim).

**Acceptance criteria**

- A stolen server disk does not expose readable health data where encryption
  is enabled.
- A revoked token stops working immediately.
- The app never sends health data to a third party without an explicit action.

---

### 6.24 Offline and sync

**Goal:** logging never fails because the network did.

**MVP**

- All core logging works offline.
- Local database with atomic writes.
- Sync queue with retries and backoff.
- Conflict resolution for editable documents.
- Append-only measurements with idempotency keys.
- Clear status: local-only, syncing, synced, offline, error.
- No silent data loss.

**V1**

- Multi-device sync with field-level merge for plans and settings.
- Source-aware merge for measurements.
- Background sync on mobile.
- Selective sync for media.
- Version history and restore.
- Server-to-server migration.

**Later**

- CRDT-backed notes (only where it helps).
- Peer-to-peer sync on the local network.

**Acceptance criteria**

- A two-hour offline workout syncs exactly once.
- A conflict never deletes the newer edit.
- The user can always see what is not yet synced.

---

### 6.25 Clinical safety

**Goal:** be useful without being dangerous.

- The product is a wellness and record-keeping tool, not a medical device.
- No diagnosis, treatment, dosing, or emergency advice.
- Reference ranges are informational and overridable.
- Alerts are user-configured and clearly labelled.
- Mental-health content includes crisis resources.
- Eating-disorder content avoids calorie shaming and unsafe targets.
- AI output is reviewed, validated, and never authoritative.
- A clear disclaimer appears during onboarding and in exported reports.
- **Weight-neutral mode.** One switch hides calories, weight, body fat and
  progress photos across the whole app, while training, steps, sleep and
  vitals keep working. Nothing is deleted; the numbers simply stop being
  shown. This is a first-class mode, not a settings footnote, and it is
  offered during onboarding to anyone who says their relationship with food or
  weight is difficult. It is also the single most defensible feature in this
  document: no major tracker has it.
- **Scope discipline.** Any feature that screens for, predicts, or warns about
  a condition moves this product toward being a regulated medical device in
  the EU and UK. Those features stay off by default, carry their evidence and
  citation, and are checked against 12.1 before they ship.

---

### 6.26 Performance and reliability

**Targets**

| Action | Target |
|---|---|
| Cold start | < 1.5 s on a mid-range phone |
| Warm start | < 300 ms |
| Log a set | < 2 s |
| Log a food | < 5 s |
| Open a chart | < 300 ms |
| Incremental sync | < 5 s on a normal connection |
| Crash-free sessions | > 99.5% |
| Passive step tracking | < 2% battery per day |
| GPS workout | < 8% battery per hour |
| Data loss | zero tolerance |

Reliability rules:

- Atomic writes everywhere.
- Every destructive action is undoable.
- Every sync is idempotent.
- Every migration is reversible or backed up.
- Every release has a rollback plan.

---

### 6.27 Timeline, quick add and backfill

**Goal:** one place where everything that happened shows up, and one gesture
that gets anything into it.

Both are promised in the information architecture and neither had a spec.

**MVP**

- Timeline: every observation, session, meal, reading and note, newest first,
  grouped by local day.
- Filters: module, type, source, and manual vs imported.
- Every row opens the thing it came from. Every number shows its provenance in
  one tap: source, device, method, when it happened, when it was recorded, and
  whether anyone edited it.
- Quick add from anywhere: food, water, weight, set, vitals, note, with the
  most recent items first.
- Backfill: every log accepts a past date and time, keeps "happened at"
  separate from "recorded at", and marks the entry as entered late.
- Undo on every write, from the timeline row itself.

**V1**

- Attachments on any entry: photo, file, lab PDF.
- Timeline search, date jump, and "on this day".
- Life events as annotations: injury, illness, travel, a new job, a new
  medication. They draw as bands across every chart and explain more trends
  than any correlation engine will.

**Later**

- Shareable, redacted timeline slices for a clinician or coach.

**Acceptance criteria**

- Anything the app stores appears in the timeline; nothing is invisible.
- Quick add to a committed entry is two taps for a recent item.
- A backdated entry never rewrites when it was recorded, and the affected
  day's rollups recompute.

---

### 6.28 Data quality, duplicates and corrections

**Goal:** provenance is only worth storing if the person can act on it.

The document is built on provenance but never says where a user resolves a
conflict. This is that place.

**MVP**

- A review inbox: imports that matched no known food or exercise, probable
  duplicates, values outside a plausible range, and days where two sources
  disagree. Each item offers accept, merge, edit, or ignore.
- Import dry run: every import shows what will be created, merged and skipped
  before anything is written.
- Source health: last sync, expected cadence, and gaps, stated plainly:
  "no steps from your watch since Tuesday."
- Correction history: editing a value keeps the original and the reason, and
  everything derived from it recomputes.
- "Exclude this reading from trends" without deleting it.

**V1**

- Bulk corrections: a unit mistake, a mis-set scale, a week logged in the
  wrong timezone.
- A source-priority editor per data type that previews what would change
  before it changes.
- Per-module data quality summary, explained, never gamified.

**Acceptance criteria**

- No import writes anything the user has not seen summarized first.
- A merge that cannot be undone is never performed automatically.
- Every number in the app can answer "where did you come from, and has anyone
  touched you?"

---

### 6.29 Undo, trash and recovery

**Goal:** "zero tolerance for data loss" is a claim that needs machinery
behind it.

**MVP**

- Undo for every destructive action, for the length of the session, not three
  seconds.
- Trash: deleted entries recoverable for 30 days per profile, with an explicit
  "delete permanently".
- An automatic local snapshot before every schema migration and every bulk
  import, restorable from settings.
- Backup health: when the last backup succeeded, how large it was, and a
  one-tap verify-restore into a sandbox.
- An export complete enough that the acceptance test is rebuilding the account
  on a clean install from the export alone.

**V1**

- Version history with restore for editable documents: routines, plans, notes.
- Point-in-time restore of a single day.
- Scheduled, verified backups that notify on failure.

**Acceptance criteria**

- A restore test runs in CI against a real export on every release.
- No code path removes an observation without a tombstone.
- One screen answers "if this phone died right now, what would I lose?"

---

### 6.30 Automation and the developer surface

**Goal:** the data tinkerer is one of eight personas and currently has nothing
before V1.

**MVP**

- A local HTTP API, on device or server, with per-module read and write
  scopes.
- Android intents and quick-settings tiles: log water, log weight, start a
  session, add a note, read today's summary. iOS Shortcuts after the iOS
  gate (0.1).
- Deep links to every screen.

**V1**

- Webhooks: workout finished, PR, weigh-in, a vital outside a user-set band.
- MCP server with scoped tokens and a documented tool surface.
- A CLI for import, export, backup and bulk correction.
- Calendar feed (ICS) for planned sessions and appointments.
- Parquet export for people who want to do their own analysis.

**Later**

- A sandboxed, permissioned plugin interface for community connectors.

**Acceptance criteria**

- Every automation path runs the same validation as the UI; nothing can write
  an invalid record through the back door.
- A token's scope is visible, and revoking it takes effect on the next
  request.

---

### 6.31 Diagnostics and support without telemetry

**Goal:** we promised no telemetry. We still have to be able to fix things, and
section 10 is full of numbers we currently have no honest way to measure.

**MVP**

- A local, rotating diagnostic log: sync attempts, migration results, import
  outcomes, crashes. It never leaves the device unless the user sends it.
- "Send a diagnostic bundle": a redacted archive the user reviews in full
  before it is attached to a support request.
- Crash reporting off by default, opt-in, with no health data in the payload.
- An in-app changelog and a known-issues line.

**V1**

- Either opt-in aggregate product measurement, with a published schema,
  visible in settings, off by default and never tied to an identity, or an
  explicit decision to measure only through recruited beta cohorts (13.12).
- A self-hoster dashboard: sync queue depth, failed jobs, storage use, backup
  status.

**Acceptance criteria**

- Nothing leaves the device without an explicit, per-event user action.
- A support request can be diagnosed from the bundle alone in common cases.
- Every metric in section 10 either has a legitimate way to be measured, or is
  removed from section 10.

---

## 7. Data model

The product is built around five layers. This is the most important design
decision in the document.

### 7.1 Layer 1: raw ingestion

Every import, device sync, manual entry, and API call creates an immutable
ingestion record:

- id
- source (manual, health-connect, healthkit, garmin, fitbit, strava, cgm, lab,
  csv, api, mcp)
- device and app version
- received-at
- recorded-at
- payload hash
- raw payload reference (encrypted where needed)
- idempotency key

This lets us replay, audit, and debug without guessing.

Raw payloads are not free, and an append-only log on a phone grows without
limit. The policy: keep the full payload for 90 days on device and for as long
as the user chooses on the server (default forever, configurable); keep the
hash, source, timestamps and idempotency key permanently. A compacted record
stays auditable and still explains everything derived from it; it simply
cannot be re-parsed from scratch. Raw storage cost is shown in settings with a
one-tap compaction.

### 7.2 Layer 2: canonical observations

Normalized, typed measurements:

- type (steps, heart-rate, blood-pressure, glucose, weight, sleep, etc.)
- value and unit (canonical storage: kg, cm, °C, mg/dL or mmol/L with a
  canonical choice, seconds, metres)
- start and end
- local date and timezone
- source id
- context (resting, post-meal, fasting, pre-workout, arm, position)
- method (cuff, CGM, BIA, DEXA, manual)
- confidence and quality
- user-verified and user-edited flags, with the original value kept on edit
- raw ingestion id

### 7.3 Layer 3: daily summaries

Rollups computed at the user’s local midnight:

- steps, distance, active calories, active minutes
- sleep duration, stages, debt
- nutrition totals and micronutrients
- training volume, sets, duration, load
- weight and body composition
- vitals averages and ranges
- mood, stress, symptoms
- adherence

Rollups are derived, never the source of truth. Late data is the normal case,
not the exception: a watch that syncs on Tuesday afternoon rewrites Monday.
Every rollup records the observation watermark it was computed from, and any
write bearing an older local date enqueues a recompute of that day and of
everything downstream of it: weekly averages, streaks, sleep debt, adaptive
TDEE.

### 7.4 Layer 4: domain objects

- Profile and goals
- Exercise, routine, programme, session, set
- Food, recipe, meal, meal plan, diary entry
- Activity, route, gear
- Measurement, photo
- Medication, supplement, lab result, symptom, condition, allergy, vaccine
- Cycle day, pregnancy, appointment
- Habit, journal entry, mood check
- Share grant, caregiver relationship
- Coach conversation, proposal, snapshot

### 7.5 Layer 5: settings and preferences

- Units, language, timezone, week start
- Modules, notifications, privacy
- Device and integration settings
- Display and accessibility
- Sync and backup

### 7.6 Source registry and deduplication

Each source has:

- priority per data type
- expected cadence
- reliability notes
- last sync
- permissions

Default rules:

- Steps: the highest-priority source that covers the day wins. Never sum, and
  never choose by magnitude: "largest wins" systematically rewards the
  worst-calibrated device. If the winning source has a gap longer than an
  hour, fall back to the next source for the whole day and say so, rather than
  stitching two sources into one number.
- Sleep: take the most detailed record; never replace a full night with a
  partial one.
- Weight: one value per local day. A manual entry beats a device entry;
  between two of the same kind, the later wins. Duplicates within a minute
  merge.
- Blood pressure: keep every reading; average with context.
- Heart rate: keep per-source series; do not average across devices blindly.
- Nutrition: user-edited entries win over imported ones.

Every rule is visible and overridable.

### 7.7 Sync model

- Measurements: append-only, idempotent, server-assigned sequence.
- Editable documents (plans, settings, notes): revision-based with field-level
  merge.
- Deletions: tombstones, never silent resurrection.
- Media: content-addressed, uploaded separately, referenced by hash.
- Conflicts: preserve both sides, let the user resolve if automatic merge is
  unsafe.

### 7.8 Identifiers and time

- Stable UUIDs for user-created objects.
- Exercise and food ids from canonical libraries where possible.
- Store UTC timestamps plus local date and timezone.
- Rollups use the local day, not UTC.
- Handle DST and travel explicitly.

### 7.9 Schema versions and mixed-version sync

Two of the user's own devices will run different app versions for months, and
a self-hoster will run a server older than both of them.

- Every payload carries a schema version.
- The server accepts any version inside a supported window and never silently
  drops fields it does not understand: unknown fields are stored verbatim and
  returned on read.
- Migrations are forward-only on device, each preceded by an automatic local
  snapshot, each with a documented rollback for that release.
- A client too old to sync safely is told to update and keeps logging locally
  in the meantime. It is never allowed to write a shape the server cannot
  store.

---

## 8. Recommended architecture

Sections 8.1 to 8.4 are decided in `Tech-Stack.md` and summarised here; 8.5
to 8.7 still hold as written.

### 8.1 Client

A native **Android** app in Kotlin and Jetpack Compose, with the domain,
storage and sync written as platform-neutral Kotlin modules so an iOS client
can be added later without reimplementing a single rule. The three options
this section used to list — Capacitor PWA, React Native, dual native — were
weighed in `Tech-Stack.md` section 2 and rejected there.

### 8.2 Local storage

- SQLite on device, with a typed query layer.
- OPFS/IndexedDB when the web client is built (0.1).
- Media in the app’s private storage, encrypted where possible.
- A single local schema with migrations and backups.

### 8.3 Server (optional)

- Node/TypeScript or Go.
- PostgreSQL for multi-user and time-series-friendly queries.
- S3-compatible object storage for media.
- Docker Compose, Helm, and a one-command self-host path.
- Passkeys, optional password, OIDC, scoped tokens.
- No vendor cloud dependency.

### 8.4 Sync

- Append-only observation log with idempotency.
- Revision/merge for documents.
- PowerSync, ElectricSQL, or a custom protocol depending on the stack.
- Background sync with clear status and retry.

### 8.5 Standards

- Health Connect for platform data; HealthKit from the iOS gate (0.1).
- FHIR R4 for clinical export.
- LOINC for observation codes.
- UCUM for units.
- Open mHealth / IEEE 1752.1 where they fit.
- FIT, GPX, TCX for activities.
- Open Food Facts, USDA, BLS for food.
- OAuth 2.1 and PKCE for integrations.
- MCP for AI tool access.

### 8.6 AI

- Optional, off by default.
- Provider-agnostic adapters.
- Bring-your-own-key and self-hosted models.
- Structured output validation.
- Proposal, review, approve, undo.
- No diagnosis, no dosing, no emergency advice.

### 8.7 Keys and recovery

End-to-end encryption is the easiest way in this product to destroy someone's
data permanently, so the recovery story is designed before the encryption is.

- A recovery code generated at setup, shown once, confirmed by re-entry, and
  re-issuable from any signed-in device.
- Any enrolled device can admit another, so a second device is a real backup.
- An explicit, understood choice at setup: if the code and every device are
  lost, the backup is gone. No silent key escrow, and no pretending the risk
  is not there.
- Local-only users see the same prompt about export, because a lost phone with
  no server and no backup is exactly the same loss.

---

## 9. Roadmap

### Phase 0 — Foundations (4–6 weeks)

- Product shell, design system, onboarding.
- Local database and migrations.
- Profile, units, timezone, modules.
- Timeline and Today skeleton.
- Import/export foundation.
- Security and privacy baseline.

**Exit:** a person can create a profile, log a note, export it, and restore it.

### Phase 1 — MVP (3–4 months)

- Gym and strength training.
- Body metrics and weight trend.
- Steps and daily activity.
- Nutrition diary, calories, water.
- Today, Timeline, basic Insights.
- Local-first storage and backup.
- Health Connect read.
- CSV/JSON export.

**Exit:** a person can run their whole day in openHealth without another app
for training, food, weight and steps.

**On the dates.** The MVP above, a full strength log, a food diary with a real
database, deduplicated steps, body metrics, Today, Timeline, local-first
storage with sync, and WCAG 2.2 AA throughout, is not three to four months of
work for a small team. Plan for six to nine, or cut nutrition to Phase 2 and
ship training, body and steps first. Decide which now, rather than discovering
it in month five (13.16).

### Phase 2 — Depth (3–4 months)

- Vitals, medications, symptoms, labs.
- Sleep and recovery.
- Yoga and mobility.
- Activities, FIT/GPX/TCX, maps.
- Weekly review and reports.
- Doctor PDF and FHIR export.
- Wear OS support.

**Exit:** a person with a wearable and a home BP cuff can replace three apps
and take a clean report to a clinician.

### Phase 3 — Ecosystem (3–4 months)

- Strava, Garmin, Fitbit, Withings, Oura, WHOOP, Polar, Dexcom, Nightscout.
- Family and caregiver sharing.
- AI Coach and MCP.
- Webhooks, calendar, WebDAV.
- Multi-device sync.
- Managed hosting option.

**Exit:** a household can share selectively, and an AI assistant can read and
write with scoped consent.

### Phase 4 — Best in the world (ongoing)

- Advanced analytics and correlations.
- Community programmes and yoga flows.
- Optional social module.
- Research exports.
- On-device AI for search, summaries and natural-language logging.
- Independent security audit.
- Accessibility and localization excellence.

---

## 10. Success metrics

### Activation

- First meaningful log within 3 minutes.
- 70% of new users log something on day one.
- 50% return on day seven.

### Engagement

- 4+ active days per week for retained users.
- Median food log time under 20 seconds per meal.
- Median set log time under 2 seconds, matching the bar in 2.7.
- Weekly review opened by 30% of active users.

### Trust

- Sync success rate > 99.5%.
- Zero data-loss reports.
- Export completion rate > 95%.
- Crash-free sessions > 99.5%.

### Health outcomes (self-reported, not clinical claims)

- Users report better consistency.
- Users report better understanding of trends.
- Users report confidence in the data.

### Quality

- App store rating > 4.7.
- Accessibility audit pass.
- Battery budget met.
- Cold start target met.

---

## 11. Non-goals

- **Not a medical device.** No diagnosis, treatment, dosing, or emergency
  response.
- **Not a social network first.** Social, segments and leaderboards are
  optional and later.
- **Not a photo-calorie estimator in the MVP.** Estimation is a later,
  clearly labelled helper.
- **Not an EHR.** We are a personal health record, not a hospital system.
- **Not an insulin dosing app.** CGM display and trends only, unless a
  regulated product is built.
- **Not a supplement store.** No affiliate pressure, no miracle cures.
- **Not a subscription trap.** Core health data is never held hostage.
- **Not a data broker.** No ads, no resale, no hidden telemetry.
- **Not a form coach.** We do not tell you your squat is wrong from a camera.
- **Not funded by you being the product.** How it is actually paid for is
  12.5, and it is decided before Phase 2, not after.

---

## 12. Constraints that shape the product

Four things in this document are not engineering problems and will not be
solved by building harder. They decide scope, so they belong in the spec.

### 12.1 Regulatory scope

Under EU MDR, software intended for prediction or prognosis of disease is a
medical device, and Rule 11 puts most software that informs a clinical
decision into Class IIa. The UK and most other markets follow similar logic;
the US FDA's general wellness policy is more forgiving but still turns on the
claims made in the UI, not the ones in the privacy policy.

Features in this document that cross the line if their wording is not
disciplined: sleep apnea risk screening, predictive alerts for overtraining or
illness risk, medication interaction warnings, configurable clinical alerts,
lab interpretation, and anything the AI Coach says about a symptom.

Position: each of those ships either as a neutral display of the person's own
data against a cited published range, or not at all until a regulated build is
funded. Section 11 says "not a medical device"; the feature list has to agree
with it, and today it does not quite.

### 12.2 Data and content licensing

Every bundled dataset records its licence, version and attribution, in the app
and in every export. Specifically:

- **Open Food Facts** is ODbL. Exporting a user's own diary is fine; shipping
  a bundled index is a derived database and carries attribution and
  share-alike obligations.
- **USDA FoodData Central** is public domain. Safe.
- **German BLS** is licensed by the Max Rubner-Institut and is not freely
  redistributable. Budget for a licence or drop it; do not plan around it.
- **Compendium of Physical Activities** MET values are published under terms
  that require attribution and restrict redistribution. Check before embedding
  the table.
- **LOINC** is free but requires registration and attribution.
- **FIT SDK** is under Garmin's developer terms.
- **Exercise and pose media** must be our own or clearly licensed. wger's
  library is CC-BY-SA, which is share-alike on derivatives.

### 12.3 Platform rules

- **HealthKit** — binds from the iOS gate (0.1); Health Connect is what binds
  the first release. Data read from HealthKit may not be used for advertising
  or data mining, may not be sold, and may not be disclosed to third parties
  without explicit user consent. That directly constrains the AI Coach: a
  provider call containing HealthKit-derived data needs its own consent and
  its own disclosure, or the app gets rejected.
- **Health Connect:** equivalent policy, plus a declared health-permissions
  use case in the Play console.
- **Strava:** the API terms restrict retention and prohibit using Strava data
  to build a competing analysis product. Assume Strava import is read and
  display only, and treat "own your history" as the user exporting their own
  archive and importing the files.
- **Garmin, Fitbit, Oura, WHOOP, Dexcom:** partner-gated APIs with approval
  processes measured in weeks to months. Start those applications in Phase 1,
  not Phase 3.

### 12.4 Licence and distribution

AGPL-3.0 and App Store distribution conflict: GPL-family terms clash with the
store's redistribution restrictions, and GPL apps have been removed over it.
The split that works is AGPL-3.0 for the server and Apache-2.0 or MPL-2.0 for
the client. That keeps self-hosting honest, keeps server improvements flowing
back, and still allows a store presence.

Decide before the first public commit. Relicensing after contributors arrive
means chasing every one of them for permission.

### 12.5 How this is paid for

The product promises no ads, no data sale, and no subscription trap. That
leaves paid managed hosting, a one-off app purchase, donations and
sponsorship, and paid support for self-hosters. Pick a primary source before
Phase 2, because it decides whether there is a server team at all, and
therefore whether Phase 3 exists.

Whatever is chosen, core logging, full export, and self-hosting stay free
permanently. That is the promise; everything else is negotiable.

---

## 13. Open decisions

These need a decision before the schema is frozen:

1. **Client stack:** decided — native Android (Kotlin + Compose) with
   platform-neutral core modules; iOS later (`Tech-Stack.md`, 0.1).
2. **Local database:** SQLite + custom sync, WatermelonDB, PowerSync, or
   ElectricSQL.
3. **Server:** Node/TypeScript or Go; Postgres; self-host and managed.
4. **Account model:** local-only first, optional server, family accounts.
5. **Licence:** a split licence, AGPL-3.0 server and Apache-2.0 or MPL-2.0
   client, is the only combination that survives App Store distribution. See
   12.4. Decide before the first public commit.
6. **Medical scope:** wellness only, or a regulated clinical product later.
7. **AI:** provider strategy, BYOK, local models, and data retention.
8. **First devices:** which watches, scales, cuffs and CGMs are supported at
   launch. Partly answered 2026-11-22: the watch is a Garmin (6.18); the model,
   scale, cuff and CGM are still open.
9. **First languages:** launch set and translation workflow.
10. **Brand and domain:** openHealth naming, logo, and store presence.
11. **Offline food index:** which foods ship in the bundle, how large it may
    be, how it updates, and the ODbL obligations that come with it (12.2).
12. **Measurement:** how section 10 is measured without telemetry. Opt-in
    aggregate metrics, recruited beta cohorts, or deleting the metrics that
    cannot be measured honestly. Today the document asks for retention and
    crash-rate numbers it has forbidden itself from collecting.
13. **Business model:** managed hosting, paid app, donations, or paid support
    (12.5).
14. **Minors:** supported with guardian consent and pediatric-appropriate
    content, or blocked under 16 until it can be done properly. Adult
    formulas and reference ranges are wrong for children, and consent rules
    differ by jurisdiction.
15. **Encryption:** end-to-end by default or opt-in, and which recovery model
    (8.7).
16. **Nutrition in the MVP:** in, or deferred to Phase 2 to protect the date
    (section 9).
17. **iOS start:** what has to be true before an iOS client begins — Android
    adoption, a second engineer who owns it, or a funded port. Until that is
    answered the answer is "not yet", and it is reviewed at every phase exit
    (0.1).

---

## 14. Appendix

### 14.1 Reference library

The `references/` folder contains 40 upstream projects and the original
openGym codebase. See [`references/README.md`](references/README.md) for the
study index and [`references/MANIFEST.md`](references/MANIFEST.md) for sizes
and commits.

### 14.2 What we borrow from each category

| Area | References | What to learn |
|---|---|---|
| Architecture | wger, SparkyFitness, HealthLog, Open Health Sync | Server-of-record, schema breadth, clinical signal registry, ingestion pipeline |
| Nutrition | OpenNutriTracker, Waistline, Food You, Mealie, Tandoor | Diary UX, food database fallback, data portability, recipes |
| Steps and wearables | Gadgetbridge, Stride, FitWren, Stepsy | Device protocols, source dedup, battery, Health Connect |
| Body metrics | OpenScale, Waistline, HealthLog | Bluetooth scales, body composition, clinical context |
| Vitals | Nightscout, xDrip, HealthLog, AortaLink, UBPM | Continuous data, alarms, BP context, FHIR |
| Activities | FitTrackee, OpenTracks, GoldenCheetah, OpenAthlete | Capture, maps, training load, FIT/GPX |
| Yoga | Inner Breeze, Feeel, YogaBase | Session player, cues, pose/flow model |
| Gym | openGym, wger, Liftosaur, FastNFitness | Progression, routines, programmable plans, simple logging |
| Sync | PowerSync, WatermelonDB, ElectricSQL, openGym | Offline-first sync and conflict handling |

### 14.3 Standards to follow

- Health Connect (Android)
- HealthKit (Apple) — from the iOS gate (0.1)
- FHIR R4
- LOINC
- UCUM
- Open mHealth / IEEE 1752.1
- FIT, GPX, TCX
- OAuth 2.1 + PKCE
- MCP
- WCAG 2.2 AA

### 14.4 Glossary

- **Observation:** a single typed measurement with time, unit, source and
  context.
- **Rollup:** a daily or weekly derived summary.
- **Provenance:** where a number came from and how much to trust it.
- **Source priority:** which source wins when two sources report the same
  thing.
- **Module:** a switchable feature area that can hide without deleting data.
- **Local-first:** the device can read and write without a server.
- **Self-host:** the user runs the server on their own hardware.
- **MCP:** Model Context Protocol, used to expose tools to AI assistants.

### 14.5 What we deliberately cut

Recorded so it cannot creep back in without an argument.

| Cut | Why |
|---|---|
| Form-check AI and pose estimation, gym and yoga | A model good enough to correct a loaded squat is a product in itself. One that is not good enough gives injury-causing advice through a confident interface. |
| Velocity-based training and bar-path capture | Phone-camera VBT is not accurate enough to autoregulate load, and sensor VBT serves a few thousand people worldwide. |
| Photo-based body composition, 3D body scan import | Error bars wider than the change being measured, and no scanner ecosystem to import from. |
| Smart alarm | Continuous overnight sensing and background audio on two platforms, for something a cheap single-purpose app already does well. |
| Safety beacon and incident detection | A safety promise we cannot keep. If it fails once, it fails at the worst possible moment, and the liability is real. |
| Live group sessions, teacher mode, live coaching, race plans | Real-time multi-user infrastructure for a product whose core is one person's private record. |
| Community recipe library, community challenges | Moderation is a staffed, permanent job, not a feature. File and QR recipe sharing covers the actual need. |
| Restaurant menu database partnerships | Business development, not engineering, and the underlying data is unreliable. |
| Insurance or employer program import | At odds with the privacy promise regardless of what the consent screen says. |
| Multi-agent coaching, voice coach | Cost and complexity with no evidence they beat one well-grounded, reviewable proposal. |
| Real-time collaborative editing for coaches | CRDT infrastructure for a use case nobody has asked for. |
| Sign-language video, offline note translation | Replaced by captions and transcripts on instructional media, which is achievable and helps more people. |
| Smart clothing, vehicle and gym equipment integrations | No open protocols, no installed base. |

Moved later rather than cut: social feed and segments, SMART on FHIR, research
export, peer-to-peer sync.

---

*This document is the contract. When a feature is ambiguous, the daily loop,
provenance, privacy and clinical safety decide. When a feature is tempting but
does not serve those, it waits.*
