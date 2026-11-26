# 0019 — Wear OS companion and Connect IQ app

**Date:** 2026-11-26 · **Phase:** B14 · **Status:** accepted

## Decisions

1. **The Wear OS companion is `:app-wear`, not a second copy of the phone.** It is an
   Android application module with the same `applicationId` as the phone (`app.openhealth`),
   `minSdk` 30, Compose for Wear OS and the `:core` module verbatim. It owns no database
   and no second set of domain rules: the phone remains the record, and the watch renders
   a snapshot and sends set, rest and heart-rate commands. The watch keeps the last
   snapshot and its unsent commands in a small private preferences file, so a dropped
   link, a process kill or a flat battery loses nothing; the queue is retried against the
   phone's snapshot acknowledgement.

2. **The Wear link is the Wear OS Data Layer, restricted to the Play build.** The phone
   app receives commands in a `WearableListenerService` in the `play` source set and
   writes snapshots to a Data Client item. The `fdroid` flavour compiles neither the
   service nor `play-services-wearable`, so the Play-Services-free build stays clean. The
   telemetry gate keeps denying every `com.google.android.gms` artifact except the exact
   Wearable stack (`play-services-wearable`, `play-services-base`,
   `play-services-basement` and `play-services-tasks`), and only on the two modules that
   need it. Analytics, ads, crash-reporting, Firebase and Data Transport remain denied.
   The Data Layer carries commands over the paired Bluetooth/Wi-Fi link; it is not an
   analytics path and the app sets its own data, not a Google account profile.

3. **One wire model in `:core`, two encodings.** `WatchProtocol` serialises a versioned
   envelope with a snapshot request, a command or a snapshot for the Wear Data Layer. A
   command carries its own idempotency key and `happenedAt`; the phone stamps
   `recordedAt` when it applies it. `WatchBlePacket` is the fixed 16/20-byte projection of
   the same model for the Connect IQ link, whose characteristic limit is 20 bytes. The
   domain rejects malformed commands (non-finite weight, negative reps, an unknown
   command type) before any write. Both encodings have tests, so there is one set of
   rules to change.

4. **Provenance names the wrist.** A Wear log names the `wear` source (kind `DEVICE`,
   family `watch`, method `MANUAL_ENTRY`, device `wear`); a Connect IQ log names the
   `garmin-connect-iq` source (kind `GARMIN`, family `garmin`, method `MANUAL_ENTRY`,
   device `connect-iq`), so a watch set is never mistaken for a phone entry and the
   existing Garmin source family keeps one day's dedup story. Heart rate names the same
   source and `WATCH_OPTICAL`. `happenedAt` comes from the watch command; `recordedAt` is
   when the phone applied it. A queued set therefore records late without rewriting when
   it happened, and the affected day's rollups are rebuilt by the normal write path.

5. **The Connect IQ app is written in Monkey C and speaks direct BLE, not Garmin's
   proprietary mobile SDK.** The watch scans for the phone's GATT service, writes a
   16-byte command packet to a command characteristic and subscribes to a 20-byte
   snapshot notification; the phone hosts the GATT server over standard Android
   Bluetooth LE. The small fixed packet is not a shortcut: a Connect IQ characteristic
   write and notification are limited to 20 bytes and long writes are unsupported, so
   JSON cannot cross this link. The packet carries the session-less action (log the
   current set, finish, heart rate) and the phone fills the session and exercise from
   the snapshot it already built. This keeps the client Apache-2.0, needs no Garmin
   developer key, no Garmin Connect Mobile app and no Garmin account, and leaves the
   watch usable on the F-Droid phone build as well. The app targets the
   vívoactive 5 first and other models only through `watch/connectiq/devices.json`, which
   records screen shape and resolution, Connect IQ API level, BLE and optical-HR support.
   Garmin's Connect IQ SDK licence is not accepted by an agent on the owner's behalf, and
   the SDK is not installed on this machine; the app is source-complete and the table is
   validated, but the Monkey C build and the watch walk are unverified and recorded as an
   open issue.

6. **The watch is an input, never a second source of truth.** The phone chooses the open
   session, the current exercise and its target; a watch snapshot says what to show. A
   watch command is applied through the same `TrainingPresenter`/repository path as the
   phone, so personal records, rest alerts, the session rollup and the change log happen
   once. A watch never writes a rollup, never changes a plan and never edits a saved
   session. A watch that is disconnected shows the last snapshot and a plain connection
   state, and the phone keeps logging normally.

7. **Testing uses a Wear OS emulator.** There is no Wear OS hardware in this build and
   the owner tests the Garmin half on a vívoactive 5 that has no Connect IQ developer
   setup. The emulator covers the Wear screens, the queue and the phone round trip;
   the Connect IQ app is source and table only. Both gaps are named in `docs/build-log.md`
   rather than implied to be tested.

## Consequences

- A future watch link (another Garmin model, a Zepp relay) adds a transport, not a second
  domain model. The protocol compatibility version is checked on every envelope.
- The Data Layer exception is narrow and reviewed: adding another Google artifact still
  fails `checkNoTelemetrySdks`, and the F-Droid phone build never sees `com.google.android.gms`.
- The phone's GATT server advertises only while a training session is open and the
  person has granted the Bluetooth advertise/connect permissions. When it is off, the
  Connect IQ app says so and keeps logging locally in its queue.
- A watch set carries `happened_at` from the watch and `recorded_at` from the phone. A
  backfilled day is rebuilt, never patched, and the provenance sheet names the wrist.
- The Connect IQ app cannot be installed without a signed `.prg` and a compatible
  device. That release step, the Garmin developer key and the physical-watch walk are
  the owner's, not an agent's.
