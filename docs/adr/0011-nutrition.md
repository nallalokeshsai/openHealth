# 0011 — Nutrition: the food index, the two barcode decoders and the diary's layers

**Date:** 2026-11-25 · **Phase:** B6 · **Status:** accepted
**Context:** `BUILD-ROADMAP.md` §B6, `pr.md` §6.3–6.4, §12.2, `DESIGN.md` §14.2.
**Supersedes:** nothing. **Amends:** `Tech-Stack.md` §8 (barcode row).

## 1. What is bundled and what is fetched

- **USDA FoodData Central is the only bundled food data** (`pr.md` §12.2: CC0). The index
  is Foundation Foods **2026-04-30** (469 foods) plus SR Legacy **2018-04** (7,793 foods):
  8,262 foods with per-100 g nutrients and household portions. The branded FDC corpus
  (~450 MB zipped) is not shipped; a barcode that is not in the local diary goes to Open
  Food Facts on an explicit action.
- `tools/foodindex` converts the two unzipped CSV releases into
  `app-android/src/main/assets/foodindex.db` (9.4 MB, committed). The generated file is
  smaller than the source CSVs because nutrients and portions are one compact JSON value
  per food and search uses a token table instead of an FTS index the platform SQLite may
  not have. The tool records the two source URLs, the SHA-256 of each `food.csv` and
  `food_nutrient.csv`, the licence and the generation time in a `meta` table and a
  sidecar JSON. Nothing downloads at build time.
- Logging a bundled food copies it into the encrypted `food` table, so "everything the
  person has ever logged" works offline and the entry has a real foreign key.

## 2. Open Food Facts is a user action, not a background sync

- `OpenFoodFactsClient` builds one request: the barcode digits, the app's user agent and
  `Accept: application/json`. The disclosure screen names the destination and states that
  only the barcode and app name leave the phone. There is no account, cookie jar, retry,
  analytics or diary data in the request.
- The HTTP transport is the `HttpTransport` seam in `:core`; the Android implementation
  is `HttpURLConnection`. The parser is pure Kotlin in `:integrations` and tested against
  real product JSON. The answer is cached as a `food` row with the ODbL attribution on
  the lookup result and in `NOTICE.md`.
- Offline, a lookup returns `Unavailable` and the screen offers manual entry; it never
  silently substitutes a different product.

## 3. Barcode decoders by store flavour

- `pr.md` §6.3 and `Tech-Stack.md` §8 require CameraX and the **bundled** ML Kit model
  on the Play path, with no Play Services runtime.
- F-Droid cannot redistribute the proprietary ML Kit binaries. The `fdroid` product
  flavour therefore uses **ZXing core 3.5.4 (Apache-2.0)** on the same CameraX
  `ImageAnalysis` pipeline; the `play` flavour uses `com.google.mlkit:barcode-scanning`
  17.3.0. Both sit behind the `BarcodeDecoder` seam and accept the same NV21 frame.
- This is a build-contract amendment, not a reversal: `Tech-Stack.md` §8 now names both
  implementations. `checkLicences` records ML Kit's own terms in
  `gradle/licences/allowed.tsv` (it is not Apache-2.0, despite the old wildcard).
- Camera permission is requested when the scanner opens, never at launch; denial is a
  visible state with the manual and create-food paths.

## 4. Layer model and totals

- `entry` remains the layer-4 source of truth for a diary; water remains an
  `observation` of type `WATER` with the existing sum-all dedup, and the kitchen's water
  containers are settings-like rows.
- A day's macro and micronutrient totals are `NutrientTotals.sumInOrder` over the same
  entry order the repository returns (`happened_at, id`). The rollup engine calls the
  same function, so "the totals equal the sum of the entries" is bit-for-bit, with no
  per-entry rounding. Display formatting is the only rounding.
- The micronutrient totals are stored on the day's first nutrition rollup row's `detail`
  JSON, keyed by `Nutrient.key`; they are rebuilt with the day, not patched.
- Recipe per-100 g values are computed from ingredient grams and stored as a food row;
  logging a portion uses the same portion math as any other food. Ingredients chosen from
  the bundled index or OFF are copied into the local food table when the recipe is saved.
- Targets are dated rows (`effective_from`); changing a target adds a row for today and
  leaves earlier days' targets intact. A target suggestion is a starting point from the
  IOM macronutrient report (protein 0.8 g/kg, fat 30 % of energy, fibre 14 g/1,000 kcal)
  with a water starting point that is labelled as the app's, not a citation.

## 5. Citations and copy

- The micronutrient panel cites the FDA Daily Values (2020) for adults and children four
  and older; the citation table and `NOTICE.md` carry the row. A nutrient without a
  published daily value shows no percentage rather than a guessed one.
- The fasting card is opt-in, stores a start/target/end, has no score, streak or guilt
  wording, and says "a timer, not a score".
- Allergen and dietary-pattern notes are the product's own declared tags or Open Food
  Facts' published tags. A food with no tag data says so; the app never calls it safe.
