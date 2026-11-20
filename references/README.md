# openHealth reference library

This folder is a **study library**, not part of the openHealth application. Each
subfolder is an independent upstream project, cloned shallowly (`--depth 1`) so
the source can be read without carrying years of history.

Licences remain with the original authors. Before copying any code, read the
`LICENSE` file in that project. A few projects are source-available rather than
OSI open source; they are marked below. Clones are read-only references and
should not be imported into the app as dependencies without a deliberate
licence decision.

The original openGym codebase is in `openGym/`. It is the local reference for
the training domain and the local-first sync model.

See [MANIFEST.md](MANIFEST.md) for the exact upstream URL, on-disk size, HEAD
commit, shallow state, and clean-working-tree check for every checkout. There
are 41 folders in total: `openGym` plus 40 upstream projects.

## How to study a project

For each app, write a one-page note covering:

1. Domain model: entities, IDs, relationships, units, time, provenance.
2. Ingestion: manual, device, API, file; raw vs normalised; dedup; source priority.
3. Derived metrics: formulas, windows, baselines, confidence, stored vs recomputed.
4. UX: daily loop, quick add, dashboards, onboarding, offline, accessibility.
5. Data portability: export/import, round-trip fidelity, backups, FHIR/Open mHealth.
6. Architecture: local-first vs server, sync, multi-user, permissions, extensibility.
7. Licence and legal: code, data, API terms, medical claims.
8. What to borrow and what to avoid.

Then keep the comparison in one feature matrix and one canonical data-model
sketch, rather than trying to merge feature lists directly.

## Architecture and all-in-one

| Folder | Upstream | Study for |
|---|---|---|
| `openGym` | this repository | Pure training logic, one-document local-first state, revision sync, modules, watch sync |
| `wger` | github.com/wger-project/wger | Server of record, REST API, React + Flutter + PowerSync, routine/slot model, nutrition and body weight |
| `SparkyFitness` | github.com/CodeWithCJ/SparkyFitness | Feature breadth, database schema, family sharing, integrations, MCP. Source-available, non-commercial |
| `HealthLog` | github.com/MBombeck/HealthLog | Clinical vitals, signal registry, reference ranges, medications, labs, doctor PDF, FHIR mapping. Non-commercial |
| `OpenHealthSync` | github.com/soma-intelligence/open-health-sync | Raw event store, ETL, canonical model, units, dedup, REST/FHIR/Open mHealth/MCP |
| `FastenHealth` | github.com/fastenhealth/fasten-onprem | FHIR personal health record, provider connections. Archived upstream |
| `OpenmHealthSchemas` | github.com/openmhealth/schemas | Canonical schemas for sleep, activity, blood pressure, glucose, heart rate, step count; IEEE 1752.1 |

## Nutrition and recipes

| Folder | Upstream | Study for |
|---|---|---|
| `OpenNutriTracker` | github.com/simonoppowa/OpenNutriTracker | Food diary UX, barcode flow, Open Food Facts/USDA/BLS, recipes, micronutrients, export |
| `Waistline` | github.com/davidhealey/waistline | Data portability, CSV/JSON/snapshot round-trip, WebDAV sync, food-database fallback |
| `FoodYou` | github.com/maksimowiczm/FoodYou | Modular home cards, full micronutrient targets, multiple food databases, Material You |
| `Mealie` | github.com/mealie-recipes/mealie | Recipe, ingredient and meal-plan model |
| `Tandoor` | github.com/TandoorRecipes/recipes | Recipe, ingredient and meal-plan model |
| `OpenFoodFactsServer` | github.com/openfoodfacts/openfoodfacts-server | Product schema, barcode data, serving sizes, allergens, Nutri-Score, community data quality |

## Wearables, steps, body metrics and vitals

| Folder | Upstream | Study for |
|---|---|---|
| `Gadgetbridge` | codeberg.org/Freeyourgadget/Gadgetbridge | Bluetooth device protocols, band sync, Health Connect export, data types |
| `Stride` | github.com/NikhilKain/stride | Hardware step counter + Health Connect, max-not-sum dedup, foreground service, battery |
| `FitWren` | github.com/DorDeorz/adimsayar | Phone and Xiaomi/Huami band steps, Health Connect, source priority |
| `Stepsy` | github.com/nvllz/stepsy | Minimal sensor-based step counter, widgets, export |
| `Path` | github.com/sidhant947/path | Minimal privacy-first step counter |
| `OpenScale` | github.com/oliexdev/openScale | Bluetooth scales, body composition, charts, CSV |
| `Nightscout` | github.com/nightscout/cgm-remote-monitor | Real-time CGM, API, alarms, followers, plugin ecosystem |
| `xDrip` | github.com/NightscoutFoundation/xDrip | CGM data hub, device integrations, treatment data |

## Activities and Strava alternatives

| Folder | Upstream | Study for |
|---|---|---|
| `FitTrackee` | github.com/SamR1/FitTrackee | Activity server, GPX import, maps, statistics, PostGIS |
| `OpenTracks` | github.com/OpenTracksApp/OpenTracks | GPS recording, BLE sensors, voice announcements, GPX/FIT export |
| `GoldenCheetah` | github.com/GoldenCheetah/GoldenCheetah | Training-load analysis, power/HR charts, critical power, FIT/TCX import |
| `Endurain` | github.com/endurain-project/endurain | Strava/Garmin sync, gear, health metrics, followers |
| `Dawarich` | github.com/Freika/dawarich | Location history and movement analysis |
| `Wanderer` | github.com/Flomp/wanderer | Trail and route planning |
| `StatisticsForStrava` | github.com/robiningelbrecht/statistics-for-strava | Dashboard from a Strava bulk export |
| `OpenAthlete` | github.com/openathleteorg/openathlete | Endurance training platform, plans and analysis |

## Yoga, gym and vitals extras

| Folder | Upstream | Study for |
|---|---|---|
| `InnerBreeze` | github.com/waozixyz/breeze | Breathing, meditation and Sun Salutation session player, habits, Health Connect |
| `Feeel` | github.com/EnjoyingFOSS/feeel | Guided home-workout player, exercise media, timers |
| `YogaBase` | github.com/SimonSchubert/YogaBase | Basic yoga pose/flow data model |
| `Liftosaur` | github.com/astashov/liftosaur | Programmable progression (Liftoscript), offline PWA, web program editor |
| `FastNFitness` | github.com/brodeurlv/fastnfitness | Simple Android strength/cardio logging, body measurements, BMI/FFMI |
| `AortaLink` | github.com/fk0u/AortaLink | Blood pressure tracking, FHIR R4, LOINC, offline-first PWA |
| `UBPM` | codeberg.org/LazyT/ubpm | Blood pressure monitor import, statistics, CSV/JSON/XML/SQL export |
| `Tempo` | github.com/trevordavies095/tempo | Minimal self-hosted running tracker |

## Sync and tooling

| Folder | Upstream | Study for |
|---|---|---|
| `PowerSyncService` | github.com/powersync-ja/powersync-service | Postgres-to-SQLite sync for offline-first clients |
| `WatermelonDB` | github.com/Nozbe/WatermelonDB | Offline-first local database for React Native |
| `ElectricSQL` | github.com/electric-sql/electric | Postgres sync engine |
| `ActivityWatch` | github.com/ActivityWatch/activitywatch | Local-first event collection and bucket model |

## Licence notes

- **AGPL/GPL projects** (`wger`, `Gadgetbridge`, `Nightscout`, `xDrip`, `FitTrackee`,
  `OpenTracks` is Apache-2.0, `GoldenCheetah` is GPL-3.0, `OpenHealthSync`,
  `OpenNutriTracker`, `Waistline`, `FoodYou`, `OpenScale`, `Stride`, `FitWren`,
  `Stepsy`, `Path`, `InnerBreeze` is BSD-3-Clause, `Liftosaur`, `FastNFitness`
  is BSD-3-Clause, `AortaLink` is Apache-2.0, `Tempo` is MIT, and others) can be
  studied freely, but copying code into a distributed or network service has
  obligations. Check each `LICENSE`.
- **Source-available, not open source**: `SparkyFitness` (custom non-commercial
  licence) and `HealthLog` (PolyForm Noncommercial 1.0.0; older releases were
  AGPL-3.0). Study the feature and schema ideas, not the code.
- **Exercise media in openGym** is licensed separately from the AGPL code. Only
  the 180 px files may be used outside the signed store packages.
- **Food data**: Open Food Facts data is ODbL; USDA FoodData Central is CC0;
  German BLS is CC BY 4.0. Each has its own attribution/share-alike terms.
- **Clinical standards**: FHIR is CC0, but SNOMED CT, LOINC, UCUM and ICD have
  their own licences. Do not assume "FHIR" means every code system is free.

## Refreshing a clone

The clones are shallow. To update one:

```sh
cd references/<name>
git pull --ff-only
```

To start over, remove that one folder and clone it again with
`git clone --depth 1 --single-branch <url> <name>`.
