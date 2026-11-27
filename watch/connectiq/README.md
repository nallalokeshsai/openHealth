# openHealth for Garmin Connect IQ

This directory is the B14 Connect IQ companion (`pr.md` §6.18; `docs/adr/0019`). It is a
Monkey C app for the owner's **vívoactive 5** and a small set of other BLE-capable models,
with the per-device table in [`devices.json`](devices.json).

It logs the current set, runs the rest countdown and shows live optical heart rate during
a workout that was started on the phone. The phone is the record: the watch queues every
command in its own storage and sends it over a direct Bluetooth LE link. There is no
Garmin Connect Mobile relay, no Garmin account and no Garmin Mobile SDK. The link is a
standard Android GATT server on the phone (`AndroidWatchGattServer`) and a BLE central in
`source/BleLink.mc`.

## Status

**Unverified.** The owner has no Connect IQ developer setup on this machine, and the
Garmin SDK (9.3.0 at the time of writing) is not installed. Garmin's Connect IQ Developer
Agreement must be accepted by the owner, not by an agent, and a release needs a developer
key from a Garmin Developer Account. The Kotlin side of the packet, the table and the
manifest are tested; the Monkey C app has not been compiled or walked on a watch. That is
recorded as an open issue in `docs/build-log.md`.

## Build

1. Install the Connect IQ SDK manager from
   <https://developer.garmin.com/connect-iq/sdk/> (it includes the licence agreement) and
   install SDK 9.3.0 or later plus the vívoactive 5 device files.
2. Create a developer key in the Garmin Developer Dashboard (Submit an App) and keep the
   `.der` file outside this repository.
3. From this directory:

   ```sh
   export CONNECTIQ_HOME="$HOME/connectiq-sdk"
   export CONNECTIQ_DEVELOPER_KEY="/path/outside/the/repo/developer_key.der"
   ./build.sh
   ```

   The script runs `monkeyc` for every product in `manifest.xml`, writes the `.prg` files
   to `bin/` (gitignored), and refuses to run without both variables. Sideload the
   `vivoactive5` build into the watch's `GARMIN/APPS` folder over USB, or export an `.iq`
   bundle from the VS Code Monkey C extension for the Store.

## Table

`devices.json` is checked by `:integrations:test` against `manifest.xml`:

- a target must have Bluetooth Low Energy, an optical heart-rate sensor and Connect IQ
  API 3.1.0 or later;
- the manifest's product ids and the table's `manifestTarget` ids must match exactly;
- a model whose id is not confirmed against the SDK stays out of the manifest with a
  `candidate` status instead of being guessed.

Add a model by updating both the table and the manifest only after its product id appears
in the installed SDK's device list.
