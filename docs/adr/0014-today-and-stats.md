# 0014 — Today and Stats: energy as a range, readiness assembly, sleep semantics and the chart registry

**Date:** 2026-11-25 · **Phase:** B9 · **Status:** accepted
**Context:** `BUILD-ROADMAP.md` §B9, `pr.md` §6.2, §6.4, §6.6, §6.10, §6.16, `DESIGN.md`
§5.6, §8, §14.2. **Supersedes:** nothing. **Amends:** `DESIGN.md` §5.6 (the KPI band's
range line) and §14.2 (the Today and Stats recipes are now implemented).

## 1. Sleep has one settled meaning before it reaches Today

B8 left one point open. Health Connect reports a night as a session **span**, and also
reports stages inside that span. Adding the span and the stages counts a staged night
about twice, and treating the span as sleep counts awake minutes. `SleepTotalsCalculator`
in `:core` now settles it once:

- when stages exist, **asleep = deep + light + REM**, and awake is never added;
- when a source reports only a span, the span is the only honest duration;
- time in bed is carried beside them and never added to either;
- an absurd or negative stage is treated as absent, not clamped into a score.

Today's readiness, the Stats sleep bar and the stage stacked bar all read this one
function, so the same night cannot be two durations.

## 2. Energy is a range, assembled from cited parts

`EnergyBalanceCalculator` composes the day from BMR (Mifflin-St Jeor), step energy
(0.762 m stride, 4 km/h walking at 3 MET, then the Compendium's MET equation), and the
logged workouts (resistance training at 3.5 MET, or an imported kcal figure). BMR carries
±5 %, step energy ±25 % and a workout ±20 %; net is `in − out`, so its range is reversed.
The central figure and its interval are both shown. The EUR 7,700 kcal/kg rule is used
only for the what-if projection, never for the measured day.

Goal ETA states are explicit: `NeedsData` (fewer than 14 days), `Steady` (14 days but the
trend is flatter than 0.05 kg/week), `Away` (the trend moves away from the goal),
`NoGoalWeight`, and `Window`. The window widens with the trend's uncertainty; there is
never a single date. Weight-neutral mode removes the whole energy card, not just its
numbers.

## 3. Readiness assembly

The v1 formula of `pr.md` §6.10 is unchanged. `ReadinessSeries` supplies its inputs from
the person's own days: a 28-day mean with at least seven days for resting heart rate and
HRV, and a **zero-filled** 28-day load series, because a day without training is a real
zero rather than a missing value. Sleep is taken from the night attributed to the
previous local day and converted through §1. Fewer than two inputs, or fewer than seven
baseline days, still produces no score; the card shows the inputs it does have and says
what is missing.

## 4. The chart registry is the gate

`ChartId` names every chart in the app. `ChartRegistry` gives each one a title, a
"how this is calculated" body and a `CitationKey`; the registry test fails if any id is
missing any of the three. `ChartTableModel` rejects an empty table, and `StatsCard` and
`TodayChartModel` reject a blank sentence, so a screen cannot draw a chart without a
table and an accessible description. Charts are hand-built in Compose from the tokens of
§8; no chart library is added.

The KPI band gained one detail line (12 sp `label-2`) for a range, because three 28/600
figures across a phone cannot hold "1,930–2,180". It uses existing roles and no new
colour; `DESIGN.md` §5.6 records the amendment.

## 5. Step goal is a setting, not a schema change

`pr.md` §6.6 requires a step goal. It is `goal.steps` in layer 5 with an 8,000 default,
edited from Today's steps card. No new table, no migration; the ring fills towards it and
the setting is local-first like every other preference.

## 6. The benchmark is a separate module and a benchmark-only source set

`:tools:benchmark` is a `com.android.test` module targeting the app's `benchmark` build type
(release-shaped, debug-signed). The five-year seed activity lives in
`app-android/src/benchmark/`, so no debug or release APK contains it. The benchmark
reports cold startup and warm Today; the emulator caveat in the phase exit test is
handled by reporting the device actually used.

## Consequences

- A future phase that adds a chart must add its registry entry and table, or the build
  fails.
- Sleep and energy semantics now have one home; changing either is a change here.
- The sample corpus writes resting heart rate and HRV daily so readiness has a baseline,
  and the demo sets a goal weight so the ETA surface can be exercised without pretending
  the trend supports it (it reports `Steady` when it does not).
