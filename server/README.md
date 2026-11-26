# openHealth sync server

Optional, self-hostable, and **AGPL-3.0** (`LICENSE` in this directory). The Android app
is complete without it; pairing a device with a server only copies the change log so the
owner's other devices can read it. The server stores and relays changes; it never merges
them and never becomes required for logging (`pr.md` §2.3, §6.24, §7.7).

## What it is

- Kotlin + Ktor 3, compiling the same `:core` and `:sync` modules the app uses.
- PostgreSQL 16 through Flyway migrations; the change log is append-only and keyed by
  `(account_id, idempotency_key)`, so a retried push is free.
- Passkeys (WebAuthn via WebAuthn4J) as the primary sign-in; a password fallback exists
  but is **off** unless `OPENHEALTH_ALLOW_PASSWORD=true`.
- Scoped, revocable, hashed bearer tokens. A browser pairing page exchanges a passkey
  sign-in for a one-time code; the app exchanges the code for a sync token.
- MinIO is provisioned for media but no media route is enabled in B13 (selective media
  sync is `pr.md` §6.24 V1). Caddy terminates TLS.

## Run it locally

```sh
cd server
cp .env.example .env        # change the three secrets
docker compose up -d --build
curl http://localhost:8080/health
```

Open `http://localhost:8080/pair` in a browser on the same machine (or reach the host
from an emulator at `http://10.0.2.2:8080`). Create the first passkey for the allowlisted
user, then enter the one-time code in **Settings → Sync** on the phone. For an emulator,
the server address is `http://10.0.2.2:8080`; that is the only cleartext address the app
accepts, together with loopback.

The first `docker compose up` builds the server from the repository root with
`--configure-on-demand`, so the Android SDK is not needed; `.dockerignore` keeps
`references/` and build output out of the context.

## Configure

| Variable | Default | Meaning |
|---|---|---|
| `OPENHEALTH_DOMAIN` | `localhost` | Public name shown on the pairing page |
| `OPENHEALTH_RP_ID` | the domain | WebAuthn relying-party id; must suffix the origin |
| `OPENHEALTH_ORIGIN` | `https://<domain>` | Exact browser origin that may register a passkey |
| `OPENHEALTH_ALLOWED_USERS` | `owner` | Comma-separated usernames allowed to register |
| `OPENHEALTH_ALLOW_PASSWORD` | `false` | Enables the 12-character password fallback |
| `OPENHEALTH_DB_URL` | — | `jdbc:postgresql://host:5432/openhealth`; without it the server uses in-memory stores (trial only) |
| `OPENHEALTH_S3_*` | — | MinIO/S3 endpoint, bucket and credentials for later media |

For a real deployment set the domain, RP ID and origin together and let Caddy obtain the
certificate. Passkeys will not verify against a different origin; changing the domain
means re-registering the passkey.

## Security notes

- Passkey challenges live in memory for five minutes and are single-use. The server is
  designed for one instance; a restart cancels a pending sign-in, never a token.
- A pairing code is ten characters, hashed at rest, single-use and expires after ten
  minutes.
- Tokens are 32 random bytes, stored only as SHA-256 hashes, and can be revoked. Sync
  tokens carry `sync:read` and `sync:write`; browser sessions carry `session` only.
- Every account is isolated: a pull only returns that account's changes, and a token
  cannot read another account's log.
- The server refuses a change outside the supported schema window and stores unknown
  fields verbatim (`pr.md` §7.9). It never logs request bodies; only the sync route and
  its status are logged.
- No telemetry, analytics or crash reporting is linked into the server or the app.
- Back up the PostgreSQL volume (`docker compose exec db pg_dump …`) and keep
  `server/.env` out of version control.

## Test it

```sh
./gradlew :server:test
```

The tests cover the passkey options and allowlist, password fallback, pairing single-use
and expiry, token scopes and revocation, account isolation, unknown-field round-trip,
schema-window refusal, and an end-to-end convergence test: two SQLite stores (the same
`:data` code the app runs) push and pull through a real Ktor server, resolve a genuine
same-revision conflict, and retry a response lost after the server committed without a
duplicate.

The full build is `./gradlew check`; the server module also runs the shared ktlint,
Detekt, portability and licence gates.

## Limits in B13

- Media upload is not enabled; MinIO is provisioned for the phase that adds it.
- OIDC is not enabled (the contract calls it optional).
- The server is one instance with in-memory WebAuthn challenges and pairing codes; a
  multi-instance deployment needs a shared challenge table.
- Android pairing uses the browser page plus a one-time code rather than an in-app
  Credential Manager passkey, which needs a verified HTTPS domain and asset links.
- Nothing here deploys itself. The owner runs it; this phase did not publish or upload
  anything.
