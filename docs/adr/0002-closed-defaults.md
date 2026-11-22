# ADR 0002 — Open decisions closed with recommended defaults

**Date:** 2026-11-22 · **Status:** accepted, owner may overrule any row
**Context:** the owner asked for the recommended option wherever a decision was
open, so that an agent can build without waiting. Each row closes an item of
`pr.md` §13. The owner is the only person who can reopen one; an agent that
disagrees writes a new ADR and asks (see `prompts.md` "When to ask").

| §13 | Decision | Reason |
|---|---|---|
| 2 Local DB | SQLite via SQLDelight 2.x + SQLCipher + custom append-only change log | `Tech-Stack.md` §5–6 |
| 3 Server | Kotlin/Ktor 3 + PostgreSQL 16 + S3/MinIO, Docker Compose. Built after the MVP | `Tech-Stack.md` §7 |
| 4 Account model | **Local-only, no accounts.** A server, when it exists, is optional and single-household | The owner and close family use it; data stays on the phone |
| 5 Licence | AGPL-3.0 `server/`, Apache-2.0 everything else | `Tech-Stack.md` §13 |
| 6 Medical scope | **Wellness only.** Neutral display of the user's own data against cited ranges; no diagnosis, no regulated claims, never "medical grade" in UI or store text | `pr.md` §12.1 |
| 7 AI | Off by default, bring-your-own-key or self-hosted, proposals only. **Not part of any build phase before B11** | `Tech-Stack.md` §9 |
| 8 First devices | Garmin (any model; vívoactive 5 is the reference) via FIT import, then Health Connect; phone step sensor; standard-GATT heart-rate strap, scale and BP cuff. CGM and Wear OS are later | `pr.md` §6.18 |
| 9 Languages | English first. All strings in resources keyed for translation; RTL-correct layouts from day one; no second language shipped in the MVP | Cheap now, expensive later |
| 10 Brand | Working name **openHealth**; placeholder `applicationId` `app.openhealth`. No domain or store name is claimed. Renaming before the first public release is a find-and-replace | Owner has not chosen a brand |
| 11 Food index | Bundle a compact generic-foods table built from USDA FoodData Central (CC0). Barcodes resolve through Open Food Facts on demand (ODbL, attribution shown) and are cached locally; a lookup is an explicit user action and says what leaves the phone | Keeps core logging offline and the bundle small |
| 12 Measurement | No telemetry. Performance budgets are measured on the owner's phone and an emulator with Macrobenchmark; `pr.md` §10 retention and activation metrics are not measured | `pr.md` §6.31 |
| 13 Business model | None. Personal and household use. No managed hosting, ads or paid tier | Owner's stated scope |
| 14 Minors | Under 16 is blocked in onboarding. 16 and 17 may track but get no calorie deficit target and no weight-loss goal | Adult formulas and ranges are wrong for children |
| 15 Encryption | SQLCipher at rest, key in the Android Keystore, always on. Optional biometric app lock. Exports and backups are encrypted with a user passphrase (Argon2id). A lost passphrase cannot be recovered and the app says so before the first backup. End-to-end encryption to a server is designed with the server | Simple, honest recovery model |
| 16 Nutrition in MVP | **In.** The owner wants it; accept the longer MVP (`pr.md` §9) | Owner's decision |
| 17 iOS | Deferred (ADR 0001) | |

Also closed here:

- **Readiness formula:** see `pr.md` §6.10 (versioned, explainable).
- **Exercise data:** self-authored names and muscle mappings, seeded from a
  public-domain or CC0 source whose licence the agent has read and recorded in
  `NOTICE.md`; no openGym or Gym visual media. Muscle geometry is the MIT
  MuscleMap set (notice retained). Until media is licensed, an exercise is text
  plus the highlighted body map.
- **Build toolchain on this workstation:** JDK 21, Android SDK at
  `%LOCALAPPDATA%\Android\Sdk`, Gradle 9.x on PATH, git and adb present.
