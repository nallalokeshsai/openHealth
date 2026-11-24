# 0010 — Gym training (B5)

**Date:** 2026-11-24
**Phase:** B5 (`BUILD-ROADMAP.md` §B5)
**Status:** Accepted

`pr.md` §6.5 fixes the training behaviour and `DESIGN.md` §14.2 the Train and Live-session
surfaces. This record keeps the implementation choices that went beyond either text.

## Scope

1. **Programmes are cut, routines are not.** `pr.md` §6.5 names the multi-week programme
   builder as the first thing to cut and calls it V1. B5 therefore ships routines,
   folders, favourites and weekday/rotation schedules; `PlanCoverage` remains the
   programme map for whenever B13 or a later phase brings the builder back. The Plan
   segment shows the week's routine coverage (or one rotation cycle when no routine has
   a weekday), and its caption says which.
2. **A routine has one weekday or one rotation position.** The single `weekday`
   (`pr.md` §6.5 says "by weekday") plus a `rotation_index` covers A/B/C. A favourite
   breaks a tie, and `RoutinePlanner` owns "today" so the screen cannot invent a plan.

## The set model

3. **A set is append-only and immediately saved.** Done writes one `work_set`; the rest
   timer starts after the write, so a dead battery cannot lose a set. Editing a saved set
   keeps `recorded_at` and bumps `revision`; deleting writes a tombstone
   (`TrashService`), and a whole session's tombstone carries its exercises and sets.
4. **Set kinds are one closed enum plus flags.** Warm-up, work, back-off, drop and
   failure are shown as one selection; the table stores the independent booleans the
   schema already had (`is_warmup`, `is_backoff`, `is_drop`, `is_failure`), so a later
   editor can combine them without a migration.
5. **Bodyweight, assistance, bands and tempo live on the set.** `is_bodyweight` makes the
   person's recorded bodyweight the load; `assistance_kg` is subtracted from it and
   `band_kg` added. Neither is inferred from the exercise: an assisted pull-up and a
   weighted pull-up are the same exercise row with different set values.
6. **Supersets and circuits are a group index on `session_exercise`.** Two or more
   exercises share it; rest starts after the last member of the group, not after each.
   A group of three or more is labelled a circuit in the live surface.
7. **A session carries its plan's targets.** `session_exercise` copies the routine's
   target sets, rep range, load, rest and progression policy, so editing a routine later
   cannot rewrite what the person was asked to do that day.

## Progression and records

8. **The policy interface is `:core` and the three MVP policies are stateless.** Linear,
   double progression and off; the other seven implement the same interface later. A
   missed rep always holds the load, and a stall of three sessions at one load suggests a
   ten-percent deload, floored at one increment.
9. **"Last time" is the last work set outside the current session.** The prefill is the
   policy's next weight and rep target, falling back to the last recorded set and then
   the plan's weight. That is why a set takes one tap rather than a keyboard.
10. **Records are defined, not guessed.** Heaviest weight, best estimated 1RM (Epley,
   1–15 reps) and most reps at a weight are compared with the exercise's earlier work
   sets. The first set is a baseline, not a record.

## Storage and rollups

11. **Schema v6 adds columns only.** Routine schedule/favourite/progression/rest,
    routine and session exercise targets, session title/feeling/effort, set drop/
    assisted/band/notes, and the timeline now includes `work_set`.
12. **Training rollups are layer 3 values.** `TRAINING_VOLUME` (kg), `TRAINING_SETS` and
    `TRAINING_DURATION` (seconds) are rebuilt from a day's **finished** sessions.
    Moving a workout updates `happened_at` and the session's local date on both session
    and sets, never `recorded_at`, and rebuilds both days.
13. **The timeline carries a set, not only its session.** `pr.md` §6.27 promises that
    nothing stored is invisible; B3's known gap is closed by a `work_set` branch in the
    view and the indexed page query.

## The rest timer

14. **A foreground service of type `specialUse`.** Android 14+ rejects a `health`
    foreground service without Health Connect permissions; a rest countdown is not a
    health sensor. The service declares the Play/F-Droid justification property, shows
    an ongoing low-importance notification with −15 s, +15 s, pause and skip, and plays
    the notification tone and one vibration when it reaches zero.
15. **State is on disk before the service starts.** Deadline, total, pause remainder and
    label live in a tiny preferences file, so `START_STICKY` (and `resume()` when a live
    session reopens) rebuilds the same rest after a process kill. `startForeground` is
    called even when the state is already empty, so the five-second FGS contract is
    never broken by a Skip that lands first.

## Presentation

16. **One presenter, one state holder.** `TrainingPresenter` builds the Train models and
    performs the writes; `AppViewModel` remains the single state holder and rebuilds the
    cheap shell first, then the Train model, so a quick add is never held behind the
    library. The presenter is an aggregation point and carries the same Detekt scope as
    `AppViewModel`; splitting it per surface is a later structural step.
17. **The screen models carry structure, not English.** Weekdays, policy names and
    equipment labels are resources in `:ui`; the presenter returns numbers, enum keys and
    muscles. Exercise names and instructions remain English content with their own
    translation path (`DESIGN.md` §11.4).

## Follow-ups from the review

18. **Loads are stored in kilograms and shown in the profile's unit.** `TrainingUnits`
    owns the pound conversion; every prefill, target, set row, volume, PR, plate target
    and equipment field crosses the boundary once. A profile created before B5 opens
    without a migration because the stored unit never changed.
19. **Custom exercises are created in the routine editor.** Name, one primary muscle,
    equipment and instructions; `is_custom` and the manual source are set, and the
    exercise appears in the library immediately. Photos and videos wait for B7.
20. **A routine exercise is removed with a tombstone**, and a routine is archived rather
    than deleted, so its sessions and targets survive (`pr.md` §6.5, §6.29).
21. **The layer-3 training volume uses the session formula.** A bodyweight set moves the
    day's weigh-in (or the profile weight) less assistance, plus bands, so the rollup and
    the summary cannot disagree (`pr.md` §7.3).
22. **The rest timer stores no exercise name.** Its preferences file is outside
    SQLCipher; the notification says "Rest" and the live screen names the exercise.
