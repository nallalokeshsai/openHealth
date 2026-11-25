# 0013 — Activity sources: the step counter, the health bus, FIT import and standard GATT

**Date:** 2026-11-25 · **Phase:** B8 · **Status:** accepted
**Context:** `BUILD-ROADMAP.md` §B8, `pr.md` §6.6, §6.18, §7.6, `Tech-Stack.md` §8, §10.
**Supersedes:** nothing. **Amends:** `Tech-Stack.md` §8 (the Health Connect and step
service rows) and §14 (a pinned Health Connect client); `AGENTS.md` (the decisions table).

## 1. The FIT SDK is not used, bundled or ported

The Garmin FIT SDK is **source-available, not open source**. Its licence is
non-exclusive, royalty-free and non-transferable, forbids redistributing the SDK as a
library or tool, and forbids distributing it (or a modification) under terms that would
compel Garmin's technology into an open-source licence. This repository is public and
Apache-2.0, so bundling the SDK, its headers or its sample files would breach those
terms and would also make the "auditable build" claim false.

B8 therefore contains an **independent decoder** in
`integrations/.../fit/`: the FIT container (12/14-byte header, definition and data
messages, compressed-timestamp headers, developer fields, the CRC-16), and the field
numbers and scales of the five messages the app stores (`file_id`, `session`, `record`,
`monitoring`, `activity`), written from the published FIT profile. The fixture tool
(`tools/fixtures`) contains an independent **encoder**, so a round trip proves the parser
against a second implementation rather than against itself. No Garmin code, file or
sample is copied; the profile's field numbers are facts about a file format, like a PNG
chunk number.

Health Connect and Bluetooth GATT are used through their published APIs and profiles, so
nothing here depends on a vendor app or a vendor cloud.

## 2. Health Connect is the platform bus, read-only

- `HealthBus` in `:core` is the seam; `AndroidHealthBus` in `:app-android` maps Health
  Connect records to the canonical keys and units. `:core` never imports
  `androidx.health`, and a fake bus drives the same import path in the device test.
- The client is pinned to **`androidx.health.connect:connect-client` 1.1.0**, the stable
  channel on 2026-08-26; 1.2.0 exists only as an alpha. It requires `compileSdk 36`
  (this app is on 37), has no Google Play Services dependency, and therefore keeps the
  F-Droid flavour buildable. Health Connect itself is a system module on Android 14+ and
  a separate Play app on 13 and lower.
- **Read only.** openHealth never writes to Health Connect: a second copy of a value in
  the platform store would be read back on the next sync and counted twice. The rationale
  activity the platform links to says exactly what is read and that nothing is written.
- A fresh grant reaches **30 days** back. "Import my history" requests
  `READ_HEALTH_DATA_HISTORY` explicitly, so a day-one install never asks for a year of
  history it is not about to read.
- Garmin Connect writes to Health Connect **one way** on Android 14+ (Garmin's "Sharing
  your Garmin Connect data with Health Connect", verified 2026-11-25): steps, step
  distance, floors climbed, heart rate, sleep stages, weight, body fat, active and total
  calories and exercise sessions. Body Battery, HRV status, VO2 max, intensity minutes
  and training status stay in Garmin. The Devices screen says this in those words.
- A staged night arrives as its stages; a summary-only night as one in-bed reading.
  Emitting both would sum a total and its parts.

## 3. Steps: a hardware counter, one source, one winner

- `Sensor.TYPE_STEP_COUNTER` is cumulative since boot and resets on a reboot. `StepCounter`
  in `:core` turns a reading into intervals with five rules: a new boot is a new count,
  a counter that goes backwards is a reset, the delta is clamped to 240 steps/min, a
  midnight crossing is split across the two local days, and a clock that moves backwards
  never invents an interval. The high-water mark never moves back, so an interval is
  never counted twice.
- The reading is written as an interval observation under the phone's own source
  (`phone-steps`, `SourceKind.DEVICE`, family `phone`), so a day reported by the phone and
  by a watch is deduplicated like any other pair: one wins, the other stays in layer 2.
- The right foreground service type is **`health`** (`FOREGROUND_SERVICE_HEALTH` with
  `ACTIVITY_RECOGNITION`), which is what Android allows for a health sensor. The service
  does no polling: the platform batches the counter (`StepSampling`, 30 s in the
  foreground, 5 min in the background, 15 min under 15 % battery), and the service exists
  to keep the process alive long enough to persist an interval.
- `ACTIVITY_RECOGNITION` is requested when steps are first started, never at launch. A
  boot broadcast restarts tracking where the platform allows it; Android 15+ may refuse
  that launch, in which case the counter keeps counting and the next app start recovers
  the interval. Either way the day is not lost, which is the `pr.md` §6.6 requirement.

## 4. Bluetooth is the three Bluetooth SIG profiles

Heart rate (0x180D / 0x2A37), weight and body composition (0x181D / 0x2A9D, 0x2A9C) and
blood pressure (0x1810 / 0x2A35). The decoders are pure functions in `:core`, tested
against packets built from the specifications, and the Android side only moves bytes. A
measurement is written the moment it is decoded; a failed write is buffered
(`MeasurementBuffer`) and flushed on reconnect (`BleRetry`, 1 s doubling to 30 s, six
attempts). A workout's sets are persisted as they are logged, so a strap dropping out
costs heart-rate coverage, never a set.

## 5. Dedup, priority and the visible rule

`pr.md` §7.6 is already implemented by `OneSourcePerDayPolicy` and the rollup engine; B8
adds the **editable order**. Moving a source writes an explicit priority for every source
of that type (10, 20, 30 …) and goes through `SourceRepository.upsertAll`, which refreshes
the registry and rebuilds every rollup, because the winner of a day may have changed. A
manual correction is a manual-source daily total, which wins the day by priority; the
other figures stay in raw and layer 2 and the devices screen names them.

## 6. Where the state lives

A paired device, the counter's high-water mark and each source's last sync are settings
(`device.registry`, `steps.*`, `activity.sync_book`), not health data: they need no schema
change, no migration and no export table, and a restored database re-pairs from scratch.
Every reading still names a real `source` row, so provenance is unchanged.

## 7. What B8 deliberately does not do

- No Garmin Health API, no Garmin account, no Connect IQ app (V1).
- No vendor scale protocols (Xiaomi/Withings bodies beyond the standard profile): a
  device that implements the SIG profile works; one that does not is out of scope until
  a later phase names it.
- No background Health Connect worker: a sync happens when the person asks for it, which
  is the local-first rule (`pr.md` §2.3). A scheduled worker can arrive with B12's clinical
  imports if the owner asks.
- No route, GPS or imported-activity screen: B11 owns those. B8 stores the activity's
  duration, distance, calories and heart rate so B11 can render it.

## 8. Amendments from the verification cycle

- **FIT steps.** `monitoring.cycles` has scale 2 and one cycle is two steps, so the raw
  field is the day's step count; `session.total_cycles` is strides, so steps are cycles × 2.
  The first implementation divided both by two and halved every import; the fixtures now
  assert their on-disk raw values.
- **Sleep types.** Each Health Connect sleep key has its own observation type. Collapsing
  all six into `SLEEP_DURATION` summed a night's stages with its total.
- **Blood pressure** fields are IEEE-11073 SFLOATs, not integers; the decoder converts them.
  A scale's body-composition characteristic (0x2A9C) is subscribed alongside its weight.
- **The Garmin family** is decided per reading from the writing device's name. Health
  Connect does not name the app, so a reading whose device names a Garmin model is imported
  under `garmin-hc` (kind Garmin, family `garmin`) and collapses with a FIT import; any other
  device stays with the platform bus rather than being guessed into the family.
- **History is a separate grant.** `READ_HEALTH_DATA_HISTORY` is requested only by "Import
  my history", and a refusal is reported instead of importing 30 days and calling it done.
- **Layer 1 covers the sensors.** The step tracker and the BLE manager write a raw record per
  batch/measurement and reference it from the observation's provenance.
- **The module gates the service.** Turning Steps off stops the service and hides the
  figures; the data stays.
- **No device address in ids.** A source id carries an 8-hex tag derived from the address;
  the address itself lives only in the paired-device registry. That registry and the Health
  Connect consent are settings, so they do travel in an encrypted export the person makes;
  a restore on another phone refreshes the granted set from the platform.
- **A day nobody covers is `INCOMPLETE_DAY`,** not a gap fallback, and both reasons are shown
  on the Steps card.
