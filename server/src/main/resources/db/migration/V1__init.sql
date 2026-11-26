-- openHealth sync server, schema 1 (Tech-Stack.md §7).
-- Times are BIGINT epoch milliseconds, matching the wire format and avoiding timezone
-- conversion in the relay. The server stores and relays; it does not interpret health
-- values.

CREATE TABLE account (
    id UUID PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    display_name TEXT,
    password_hash TEXT,
    created_at BIGINT NOT NULL
);

CREATE TABLE webauthn_credential (
    credential_id TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    attestation_object BYTEA NOT NULL,
    client_data BYTEA NOT NULL,
    transports TEXT,
    sign_count BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    last_used_at BIGINT
);

CREATE INDEX webauthn_credential_account ON webauthn_credential(account_id);

CREATE TABLE auth_token (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    scopes TEXT NOT NULL,
    label TEXT,
    device_id TEXT,
    created_at BIGINT NOT NULL,
    expires_at BIGINT,
    revoked_at BIGINT
);

CREATE INDEX auth_token_account ON auth_token(account_id);

CREATE TABLE pairing_code (
    code_hash TEXT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    used_at BIGINT
);

CREATE TABLE device (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    last_seen_at BIGINT
);

-- The append-only relay. `fields` keeps every column and every unknown field verbatim
-- (pr.md §7.9); the server never merges, so it cannot lose an edit.
CREATE TABLE change (
    seq BIGSERIAL PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    entity TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    fields JSONB NOT NULL,
    field_clocks JSONB NOT NULL,
    idempotency_key TEXT NOT NULL,
    hlc TEXT NOT NULL,
    device_id TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    local_date TEXT,
    recorded_at BIGINT NOT NULL,
    received_at BIGINT NOT NULL DEFAULT (extract(epoch FROM now()) * 1000)::bigint,
    UNIQUE (account_id, idempotency_key)
);

CREATE INDEX change_account_seq ON change(account_id, seq);
CREATE INDEX change_account_entity ON change(account_id, entity, entity_id);
