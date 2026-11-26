# openHealth privacy policy

**Version:** 1.0 · 2026-11-26
**Applies to:** the openHealth Android app, version 1.0.0, and its self-hosted server when one
is used.

openHealth is a record-keeping and wellness tool, not a medical device. It does not
diagnose, treat, cure or prevent any disease, and it is not a substitute for a clinician.

**This file is the source text. The public URL is the one open item:** the app and the
store listing will point at it once the owner hosts it. Until then the same text is shown
inside the app under Settings → Privacy.

## What the app stores, and where

Everything you log is stored on your phone, in a database encrypted with a key that never
leaves the device's keystore. There is no account and there is no openHealth cloud: the
app works with no network at all.

The record holds what you enter and what a source you connected reports: steps, distance,
floors, heart rate, sleep, workouts, weight, body measurements, body-fat figures, blood
pressure, glucose, temperature, oxygen saturation, respiratory rate, nutrition and water,
notes, and your profile. Every value carries where it came from and when it was recorded.

## What leaves the phone

Nothing leaves the phone unless you start an action that says so:

- **A barcode lookup.** When you scan an unknown barcode and confirm the lookup, the app
  sends that barcode to Open Food Facts (`https://world.openfoodfacts.org`) and receives
  the product's public nutrition data. The request carries the barcode, a User-Agent and
  nothing else. It is offered only after a disclosure that names the service, and the
  answer is cached on the phone.
- **An export you share.** Settings → Data → Export writes an encrypted file that you
  choose where to put. It goes nowhere unless you send it.
- **A server you configure yourself.** A self-hosted openHealth server is optional. If
  you never add one, the app never contacts one.

There is no advertising, no third-party analytics, no crash reporting and no telemetry.
The app contains no SDK from an advertising, analytics or attribution company.

## Health Connect

If you connect Health Connect, the app asks for read-only access to the data types the
Devices screen lists. It never writes to Health Connect. Health Connect data is used only
to show your record back to you, and it is covered by the same "nothing leaves the phone"
rule.

## Permissions

The app asks for a permission at the moment it is needed, never at launch: camera for a
barcode, activity recognition for step counting, Bluetooth for a heart-rate strap or
scale, notifications for the rest timer, and biometrics if you turn on the app lock. The
full list is visible in Android's app settings.

## Backups and deletion

Android's cloud backup and device transfer are switched off for this app, so the encrypted
record is not copied into a Google account or onto a new phone by the operating system.

You can export the whole record at any time, and deleting the app deletes the record with
it. Deleted entries go to Trash for 30 days before they can be removed permanently. The
export is complete enough to rebuild the record on a clean install, which is the test we
run before every release.

## Children

The app is not directed at children under 16, and onboarding refuses a birth date that
would make the person younger than 16 for the parts of the product that use weight-loss
targets.

## Changes

This policy is versioned in the public source repository. A change that affects what
leaves the device will be stated in the release notes before it ships.

## Contact

The project's public issue tracker is the contact point:
<https://github.com/nallalokeshsai/openHealth/issues>.
