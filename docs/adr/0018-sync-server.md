# 0018 — Sync engine and the optional server

**Date:** 2026-11-26 · **Phase:** B13 · **Status:** accepted

## Decisions

1. **One append-only change log, one row per logical write.** Schema v11 (`10.sqm`) adds
   `change_log`, `sync_row_state` and `sync_conflict`. A change carries `entity`,
   `entity_id`, `operation`, the whole row as JSON text, per-field hybrid-logical-clock
   values, an idempotency key, the device id, the schema version and the local date. A
   write and its log entry commit in the same transaction, so B13 cannot describe a
   write that did not happen. Rollups, raw payloads, Trash tombstones and progress
   photos are declared never-synced: the first two are derived or device-local, and
   media is uploaded separately (`pr.md` §7.7). A synced row that references a raw
   record carries `raw_record_id = null` on the wire, because the receiver has no raw
   payload to point at. A change the receiver refuses is not marked applied, so an app
   update can retry it rather than losing it silently. Demo/sample generation pauses
   capture, so a sample corpus never reaches the server.
2. **Hybrid logical clock, not wall time.** `HybridLogicalClock` combines physical time
   with a counter and is observed on every pulled change, so two edits in the same
   millisecond still order and a device whose clock is behind cannot claim a newer
   revision. A new millisecond starts the counter at a device seed (a 16-bit hash of the
   device id) rather than zero, so two devices editing in the same millisecond do not tie.
   The encoding is fixed width and sorts correctly in the server's log.
3. **Per-entity merge policies live in `:core`.** `SyncMergePolicyRegistry.default()`
   declares every syncable entity: append-only for observations, sets, doses and route
   points; revision-based with a real conflict for editable documents and clinical
   records; last-writer-wins for free text and the source registry; field-by-field merge
   for settings (one entity instance per key). A policy that cannot settle a conflict
   returns both snapshots with a stable reason key. The engine records the conflict and
   the Sync page shows both sides with their device and clock; nothing is resolved
   silently.
4. **The server stores and relays; it never merges.** Ktor 3 compiles the same `:core`
   and `:sync`; the change log is PostgreSQL `JSONB`, the first copy of an idempotency
   key wins, and a pull is a cursor over the account's sequence. Unknown fields are
   stored verbatim and returned on read (`pr.md` §7.9). A change outside the supported
   schema window is rejected with a reason; the app keeps logging locally and says it
   needs an update.
5. **Passkeys, then a one-time code.** WebAuthn4J verifies registration and assertion
   server-side. The browser pairing page exchanges a passkey sign-in for a ten-character,
   single-use, hashed, ten-minute code; the app exchanges that code for a 32-byte sync
   token stored in the Android Keystore vault. Tokens are hashed at rest, scoped
   (`sync:read`, `sync:write`, `session`) and revocable. Only an allowlisted username may
   register. The password fallback exists for recovery but is off by default. In-app
   Credential Manager passkeys are deferred until a verified HTTPS domain and asset links
   exist; that is why the owner chose the browser pairing flow.
6. **The transport is HTTPS, with a named loopback exception.** `KtorSyncTransport`
   refuses a cleartext base URL except `localhost`, `127.0.0.1` and the Android emulator's
   `10.0.2.2`, and the server URL is validated before a client is built. The server URL
   and token are configuration, not data; they are excluded from the change log, so a
   device cannot sync its own credentials to another device.
7. **Sync is optional and never blocks a local action.** Pairing and a manual sync are
   the whole MVP surface; background sync is `pr.md` §6.24 V1. A failure sets Offline or
   Needs attention with the reason and leaves every change queued. The Settings row says
   Local only until a server is paired.
8. **Deployment is the owner's.** The Compose file (`server/docker-compose.yml`) starts
   PostgreSQL 16, MinIO, the server and Caddy. Nothing in this phase deployed, uploaded
   or published it. The three `BUILD-ROADMAP.md` B13 questions were asked and answered:
   **local Docker Compose only**, a **placeholder domain configured by
   `OPENHEALTH_DOMAIN`**, and **the owner as the only allowlisted user**. The pairing flow
   is the browser page plus a one-time code. The server ships with the loopback defaults
   (`OPENHEALTH_DOMAIN=localhost`, `OPENHEALTH_ALLOWED_USERS=owner`, password fallback
   off); the owner sets the real domain and allowlist in `server/.env` before any
   deployment.

## Consequences

- A new syncable entity needs one row in `SyncTables`, one policy in
  `SyncMergePolicyRegistry.default()` and a capture call at its repository write. A test
  compares the two lists (`SyncIntegrityTest.everySyncableTableHasAMergePolicy`).
- A record that predates schema v11 has no change entry; the first pairing sweeps every
  syncable row once (`SyncRepository.backfillIfNeeded`), guarded by a per-device marker.
- A tombstoned row is never silently restored: an incoming edit surfaces a conflict and
  the user chooses the delete or the edit.
- The change log grows with the record; an export includes it so version history and
  conflict evidence survive a restore. Compaction of applied remote changes is a later
  maintenance task, not a correctness one.
- The server is a single instance with in-memory WebAuthn challenges and pairing codes.
  A multi-instance deployment needs a shared challenge table and a shared pairing store.
- Media upload (MinIO) and OIDC are provisioned/optional but not enabled. Selective media
  sync is V1.
