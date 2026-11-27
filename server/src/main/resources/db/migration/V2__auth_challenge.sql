-- WebAuthn challenges move out of process memory so several server instances can share
-- one database (Tech-Stack.md §7). A challenge is deleted when it is taken, so it works once.
CREATE TABLE auth_challenge (
    id TEXT PRIMARY KEY,
    challenge BYTEA NOT NULL,
    kind TEXT NOT NULL,
    username TEXT NOT NULL,
    expires_at BIGINT NOT NULL
);

CREATE INDEX auth_challenge_expires ON auth_challenge(expires_at);
