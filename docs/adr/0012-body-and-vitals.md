# 0012 — Body and vitals: cited ranges, an in-database photo store and the measurement model

**Date:** 2026-11-25 · **Phase:** B7 · **Status:** accepted
**Context:** `BUILD-ROADMAP.md` §B7, `pr.md` §6.8–6.9, §6.25, §12.1, `DESIGN.md` §7.5,
§8.2.1, §9.1.1–9.4, §14.2.
**Supersedes:** nothing. **Amends:** `Tech-Stack.md` §5 (the photo storage note), `DESIGN.md`
Appendix A (the measurement anchor geometry).

## 1. Every vital has a cited range, and the range is a fact

- `VitalRanges` in `:core` owns the five B7 ranges: home blood pressure, resting heart
  rate, oxygen saturation, body temperature and blood glucose. Each carries a
  `CitationKey`; `NOTICE.md` records the source, version and the numeric claim. A range
  without a citation cannot be constructed.
- The 2023 blood-pressure guideline is the **ESH** guideline, not a joint ESC/ESH
  document. The citation and the on-screen label say ESH 2023 and use the home threshold
  of 135/85 mmHg and the 7-day duplicate morning/evening protocol with its first day
  discarded. Calling it ESC/ESH would have been a misattribution.
- The temperature range is **36.2–37.0 °C**, the mean ± 2 SD across sites reported by
  Geneva et al. 2019 (36.16–37.02 °C), not the consumer shorthand 36.1–37.2 °C. The
  systematic review does not support the wider figure.
- Glucose uses ADA Standards of Care 2026: fasting below 5.6 mmol/L and the two-hour
  75 g OGTT below 7.8 mmol/L; the 3.9 mmol/L low threshold is the same standards' Level 1
  hypoglycaemia threshold. The context (fasting, pre-meal, after a meal) selects the
  range.
- A reading is shown inside or **outside the published range**. No alert, no "high" as a
  verdict, no red: the four-stop strip of `DESIGN.md` §8.2.1 is drawn only for blood
  pressure and glucose, and its stops are zones. A wording test in `:ui` fails the build
  on "risk", "warning", "diagnose" and "abnormal" in product copy.
- No HbA1c, respiratory rate, medication or lab screen is built in B7: the roadmap's
  Vitals MVP names five readings, and the rest of `pr.md` §6.9 is later.

## 2. Photos live in the encrypted database

- A progress photo is re-encoded to JPEG, downscaled to 1600 px on the long edge, and
  stored with a 256 px thumbnail as base64 text in the `body_photo` table. That table is
  inside the SQLCipher database, whose passphrase is held by the Android Keystore.
- The original file is never copied: EXIF (including any location tag) is dropped by
  re-encoding, no MediaStore row is created, no storage permission is requested, and the
  platform backup/transfer rules exclude every domain. The photo picker is the system's,
  so the app sees only the bytes the person chose.
- A photo is a layer-4 row with full provenance, joins the timeline, and deletes through
  a tombstone with a 30-day Trash entry. Restoring the tombstone restores the image
  because the bytes travel in the payload.
- The general encrypted export carries the photo rows, because the export is the person's
  own restorable backup, not a share. There is no public share action in B7; a share
  would strip metadata and include only selected photos (`DESIGN.md` §9.4), and that is
  left for the reporting phase.

## 3. Measurements and the dynamic model

- Weight is an observation (LAST wins for the day); tape and composition values are
  `measurement` rows. Both carry source, method, `happened_at`, `recorded_at`, local day,
  zone and an idempotency key. The body model reads the newest accepted value per site.
- The adipose layer uses recorded body fat first, then the US Navy tape estimate. The
  estimate is labelled wherever it appears and never overwrites a measured figure. The
  same seven regions and clamps of `DESIGN.md` §9.1.1 apply; the before/after scrub blends
  two real layers and is offered only when a layer exists at least 30 days earlier.
- Weight-neutral mode removes the weight card, the composition card, the fat layer and
  the photos. The muscle figure stays (it is not a weight value), and every vital keeps
  working, as `pr.md` §6.25 asks.
- CSV (`date,type,value,unit,method,notes`) is the interchange format for weight and tape
  measurements. Units are converted before storage; a re-import of the app's own export
  is idempotent by date and value.

## 4. Consequences

- A new vital needs a citation before it can be rendered; adding one is a code change in
  `:core`, not a string in a screen.
- A photo can be large; the database grows with the person's gallery. The thumbnail query
  keeps the gallery from decoding full images, and the timeline reads metadata only.
- The reference-range table is a clinical fact table. If a guideline is revised, the
  citation and the numeric range change together in one commit, and the on-screen label
  changes with them.
