# openHealth — Product Requirements & Feature Specification

**Version:** 0.1 (draft for build planning)  
**Status:** Living document  
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

- The phone and the browser hold the truth they can see.
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

| Tab | What lives there |
|---|---|
| **Today** | Readiness, plan, calories/macros, steps, water, due meds/vitals, quick actions |
| **Train** | Gym sessions, yoga, activities, programmes, routines, history |
| **Food** | Diary, recipes, meal plans, water, fasting, supplements |
| **Body** | Weight, measurements, photos, vitals, sleep, cycle |
| **Insights** | Trends, correlations, weekly review, reports, coach |
| **More** | Profile, modules, devices, integrations, data, settings |

Global layers:

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

**V1**

- Import from Apple Health, Health Connect, Fitbit, Garmin, Withings, Oura,
  WHOOP, Strava, CSV, and other apps.
- Guided first-week plan.
- “What do you want to improve first?” personalization.
- Unit and language auto-detection with an easy override.
- Accessibility setup: text size, contrast, motion, screen reader hints.

**Later**

- Clinician-shared profile.
- Family/household setup.
- Insurance or employer program import (only with explicit consent).

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
- Search local foods, Open Food Facts, USDA FoodData Central, and the German
  BLS (and other national databases as they are added).
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
- Restaurant menu database partnerships.
- Community recipe library with moderation and attribution.
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
- Session flow: start today’s plan, freestyle, or log a past workout.
- Fast set logging: weight, reps, RIR/RPE, done, rest.
- Warm-up ramps, work sets, back-off sets, drop sets, rest-pause, cluster sets,
  failure flags, tempo, and per-side work.
- Supersets, circuits, and combined routines.
- Plate math for barbells, EZ bars, trap bars, Smith machines, and custom bars.
- Equipment profiles per gym: plates, dumbbells, bars, machine stacks.
- Progression policies: linear, Greyskull LP, double, steady, triple,
  undulating, density, ladder, time, and off.
- Deloads, stalls, PR detection, estimated 1RM, and per-exercise history.
- Rest timer with sound, vibration, and native alerts.
- Session summary: volume, sets, PRs, duration, notes.
- Edit any saved workout; move it to another date; delete with undo.
- Bodyweight and assisted exercises; band and chain support.
- Notes per exercise, per set, and per session.
- Form videos attached to a set, stored privately.

**V1**

- Watch support: Apple Watch, Wear OS, Garmin, Zepp.
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

- Form-check AI with pose estimation (optional, private, on-device where
  possible).
- Velocity-based training and bar-path capture.
- Community programmes and coach-authored plans.
- Live group sessions.

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
- Health Connect and HealthKit import.
- Daily steps, distance, active calories, active minutes, floors where
  available.
- Goal setting and weekly average.
- Source priority and deduplication: take the best source, never sum sources.
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
- Health Connect / HealthKit mindful-session export.
- Watch support for timers and heart rate.
- “Yoga after leg day” and “mobility for desk workers” recommendations.

**Later**

- On-device pose estimation with real-time alignment feedback.
- Adaptive flows based on soreness and readiness.
- Teacher mode for classes.
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

**V1**

- Bluetooth scale import (OpenScale-compatible devices).
- Body-fat method tracking (BIA, DEXA, skinfold, Navy) with method-specific
  trends.
- Goal-rate corridor with safety warnings.
- Photo alignment and private gallery.
- Measurement reminders.
- Body-composition change vs strength and nutrition correlation.

**Later**

- 3D body scan import.
- Clothing-size tracking.
- Photo-based body composition estimates (clearly experimental).

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
- Lab results: LOINC-coded where possible, reference ranges, trend, PDF import.
- Clinical reference ranges with user/clinician overrides.
- Context-aware charts and averages.
- Doctor-ready PDF report.
- FHIR R4 and Open mHealth export.

**V1**

- CGM integration: Dexcom, Libre, Nightscout, xDrip.
- Blood pressure monitor import over Bluetooth.
- Home BP protocol: 2 readings, morning and evening, 7 days.
- Medication interaction warnings (education only, with sources).
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
- Data can be exported in FHIR and a plain CSV.

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

- Sleep apnea risk screening (not diagnosis).
- Smart alarm.
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
- Live coaching and race plans.
- Safety beacon and incident detection.

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
- Community challenges.
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

**V1**

- Granular permissions by data category and time range.
- Invite by link or code, with expiry.
- Audit log of who saw or changed what.
- Child and dependent profiles with guardian consent.
- Shared medication and appointment reminders.

**Later**

- Household dashboards.
- Clinician accounts with scoped access.
- Emergency information card.

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
- Clear privacy disclosure about what is sent to the provider.

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

- Multi-agent coaching (training, nutrition, sleep).
- Voice coach.
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
- Predictive alerts for overtraining or illness risk (experimental).
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
- Health Connect and HealthKit read/write.
- WebDAV backup.
- Encrypted backup file.

**V1**

- Apple Health, Health Connect, Garmin, Fitbit, Withings, Oura, WHOOP, Polar,
  Suunto, COROS, Strava, Dexcom, Libre, Nightscout, Omron.
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
- Health Connect and HealthKit as platform buses.

**V1**

- Apple Watch: workout, sets, rest timer, heart rate, complications.
- Wear OS: workout, sets, rest timer, tiles.
- Garmin: Connect IQ app and FIT import.
- Zepp OS: relay through the server.
- CGM: Dexcom, Libre, Nightscout.
- Pulse oximeters and thermometers where standard GATT profiles exist.
- Walking pads and treadmills.

**Later**

- Smart rings and sleep buds.
- ECG devices.
- Smart clothing.
- Vehicle and gym equipment integrations.

**Acceptance criteria**

- A device that disconnects mid-workout loses nothing.
- The app degrades gracefully to manual logging.
- No vendor cloud account is required.

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
- 10+ languages at launch; community translations after.

**V1**

- 25+ languages.
- Localized food databases and exercise names.
- Voice-over and TalkBack tested flows.
- One-handed mode.
- Haptic patterns for rest and timers.

**Later**

- Sign-language video for key flows.
- Offline translation of user notes.

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

- Real-time collaborative editing for coaches.
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

Rollups are derived, never the source of truth.

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

- Steps: take the largest trusted source for the day, never sum.
- Sleep: take the most detailed record; never replace a full night with a
  partial one.
- Weight: manual or latest measurement wins; duplicates within a minute merge.
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

---

## 8. Recommended architecture

This section is a starting point, not a locked decision.

### 8.1 Client

- **Option A (recommended for speed):** React + TypeScript + Vite PWA with
  Capacitor shells for iOS/Android, sharing the openGym experience. Best for
  web, self-hosting, and fast iteration.
- **Option B (deeper native health):** React Native or Expo with native
  HealthKit/Health Connect modules. Better for background sensors and watch
  integration, heavier to build.
- **Option C (maximum native):** Swift + Kotlin. Best performance and device
  access, highest cost.

### 8.2 Local storage

- SQLite on device, with a typed query layer.
- OPFS/IndexedDB for the web build.
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

- Health Connect and HealthKit for platform data.
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
- Health Connect / HealthKit read.
- CSV/JSON export.

**Exit:** a person can run their whole day in openHealth without another app
for training, food, weight and steps.

### Phase 2 — Depth (3–4 months)

- Vitals, medications, symptoms, labs.
- Sleep and recovery.
- Yoga and mobility.
- Activities, FIT/GPX/TCX, maps.
- Weekly review and reports.
- Doctor PDF and FHIR export.
- Watch support.

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
- On-device AI and pose estimation.
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
- Median set log time under 3 seconds.
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

---

## 12. Open decisions

These need a decision before the schema is frozen:

1. **Client stack:** React + Capacitor, React Native/Expo, or native.
2. **Local database:** SQLite + custom sync, WatermelonDB, PowerSync, or
   ElectricSQL.
3. **Server:** Node/TypeScript or Go; Postgres; self-host and managed.
4. **Account model:** local-only first, optional server, family accounts.
5. **Licence:** AGPL-3.0, Apache-2.0, or open-core. This affects contributions
   and store distribution.
6. **Medical scope:** wellness only, or a regulated clinical product later.
7. **AI:** provider strategy, BYOK, local models, and data retention.
8. **First devices:** which watches, scales, cuffs and CGMs are supported at
   launch.
9. **First languages:** launch set and translation workflow.
10. **Brand and domain:** openHealth naming, logo, and store presence.

---

## 13. Appendix

### 13.1 Reference library

The `references/` folder contains 40 upstream projects and the original
openGym codebase. See [`references/README.md`](references/README.md) for the
study index and [`references/MANIFEST.md`](references/MANIFEST.md) for sizes
and commits.

### 13.2 What we borrow from each category

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

### 13.3 Standards to follow

- Health Connect (Android)
- HealthKit (Apple)
- FHIR R4
- LOINC
- UCUM
- Open mHealth / IEEE 1752.1
- FIT, GPX, TCX
- OAuth 2.1 + PKCE
- MCP
- WCAG 2.2 AA

### 13.4 Glossary

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

---

*This document is the contract. When a feature is ambiguous, the daily loop,
provenance, privacy and clinical safety decide. When a feature is tempting but
does not serve those, it waits.*
