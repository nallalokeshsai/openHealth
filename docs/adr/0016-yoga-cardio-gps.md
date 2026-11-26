# 0016 — Yoga, cardio, GPS and maps

**Date:** 2026-11-26 · **Phase:** B11 · **Status:** accepted

## Decisions

1. **Basemap source.** Activity maps use **OpenFreeMap / OpenStreetMap** vector tiles
   packaged as PMTiles (ODbL data, MIT tooling, no API key, no account). The map shows
   `© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors` on every surface that draws
   it, and the attribution is also in `NOTICE.md`. MapLibre Native (BSD-2-Clause) renders
   it; Google Maps stays excluded (`Tech-Stack.md` §8).
2. **One-time map pack.** A pack is downloaded only on the person's explicit action,
   HTTPS only, one at a time, capped at 50 MB, validated by its `PMTiles` magic and stored
   in app-private storage. No pack is bundled and nothing is downloaded in the background.
   Without a pack the route draws on the neutral grid, so logging, import and export never
   wait for a map. The route is stored locally; no tile server learns where the person ran.
   The QA sample is the OpenFreeMap Monaco archive (about 470 KB), recorded in
   `docs/build-log.md`.
3. **Yoga content is ours.** The 29 poses and eight flows (Sun Salutation A/B, beginner
   full-body, hip opener, spine, post-run, desk reset, bedtime) are written for openHealth.
   No scraped text, still or animation is used. The body map is the pose diagram; a pose
   names its primary and secondary muscles, and a finished session feeds the recovery map.
4. **Training load is relative.** Cardio uses the Banister heart-rate ratio (TRIMP) when
   an average heart rate, resting heart rate and maximum exist, otherwise duration × MET.
   Strength uses session RPE × minutes. Fitness is a 42-day exponential average, fatigue a
   7-day average and form is fitness − fatigue (Allen & Coggan). It is displayed as a
   relative number for the person against their own history, never as a medical measure or
   a target.
5. **Strava is a file, not an API.** A Strava bulk-export ZIP is read locally; `activities.csv`
   supplies names, types and totals, and per-activity GPX/TCX/FIT files supply the route.
   No Strava account, token or live API is used, and Strava data is never required for a
   core action.
6. **Activities are layer-4 rows.** The activity row carries the route samples; distance,
   active minutes and active calories are emitted as layer-2 observations with provenance,
   so the existing dedup rules decide a day's winner. The route is never reconstructed from
   a summary, and a summary is never inferred from a partial route.
7. **GPS is a `location` foreground service.** It records at 2 s / 3 m, excludes paused
   time, persists every accepted fix, and resumes from the persisted snapshot after a
   process death. A finish writes through the same repository as manual and imported
   activities.
8. **Yoga keeps running with the screen off.** A `specialUse` foreground service ticks the
   pure player, persists the state and speaks the pose cue when voice is on. The finished
   session is saved automatically through the activity repository; the finish screen is a
   confirmation, and Delete is the ordinary Trash path.
