# 0020 — AI coach and the local MCP surface

**Date:** 2026-11-27 · **Phase:** B15 · **Status:** accepted

## Decisions

1. **Two provider adapters, no vendor lock.** `:core` owns a provider-agnostic
   `CoachProvider`; `AnthropicCoachProvider` speaks the Messages API with
   `claude-opus-5-5` (deep) and `claude-sonnet-5-5` (quick) as `Tech-Stack.md` §9 names
   them, and `SelfHostedCoachProvider` speaks any OpenAI-compatible `/v1/chat/completions`
   endpoint (LM Studio, Ollama, vLLM, a gateway). A self-hosted host may leave the key
   blank. The owner's answer on 2026-11-27 chose both adapters and Android Keystore
   storage.

2. **The key never enters the store, a log or a model.** It is written through the
   existing `SecretVault` seam under `coach.api_key.<provider>`, read at send time, and
   passed to the adapter per call. The settings row holds only the provider kind, model and
   base URL; the screen shows presence, never the value. Saving the provider and the key is
   one operation that names the target kind, so a key pasted before a provider switch can
   never be filed under, or sent to, the wrong provider. A failed call reports a status
   code, never a body or a header.

3. **Consent is per category and off by default.** `CoachConsent` names six categories
   (profile, training, recovery, nutrition, sleep, vitals), each its own switch, with a
   master switch that starts off. `CoachPrompt` serialises only a consented category and
   shows the disclosure list before the first send. Weight-neutral mode strips weight,
   body fat and calories from the request as well (`pr.md` §6.25).

4. **Health Connect data needs a second, separate opt-in.** A boolean `viaHealthConnect`
   on the source registry is the producer: it is set for `health-connect` and for
   `garmin-hc` (a Garmin watch whose readings arrived through the platform bus is kind
   GARMIN but still platform-derived). Schema v12 adds the column with a false default and
   the app backfills the two known platform sources at open, so an upgraded install cannot
   leak old rows. A row is sent only when its category is on *and* the platform switch is
   on (`pr.md` §12.3, a Play policy requirement).

5. **The coach proposes typed plan changes only.** `CoachAction` is `add_exercise`,
   `update_target` or `move_day` against a routine. `CoachActionValidator` checks routine,
   entry and exercise ids against the live plan and the same bounds the editor uses (sets
   1–20, reps 1–100, load 0–500 kg, RIR 0–10, ISO weekday 1–7), rejecting a no-op. The
   parser rejects unknown fields, a non-JSON answer, more than five proposals and more than
   ten actions in the whole answer (not per proposal). One repair round follows
   (`Tech-Stack.md` §9); a second failure is a
   visible stored row with stable finding codes, and nothing is applied. A safety filter
   rejects diagnosis, prescription, dosing, "stop taking" and "start taking" in the note,
   summary or rationale, because the acceptance criterion is that the coach never
   diagnoses, prescribes or doses.

6. **Approval is explicit, snapshot-backed and reversible.** Accepting writes through
   `TrainingRepository` (same rows, same sync capture) in one transaction with a snapshot of
   every affected routine and entry plus the ids the proposal created. Undo restores the
   snapshot and removes exactly those created rows, never an entry the person added later.
   Only the newest *accepted* proposal (by decision time) can be undone, and only in the
   same app session: that
   is the session-length tombstone of `DESIGN.md` §5.18 rather than a timer. A proposal
   that adds a row is not a Trash deletion — the person's history is untouched — so the
   snapshot, not `TrashService`, is the reversal record. Applying is refused unless the
   proposal is still `proposed`.

7. **The MCP server is on the device and scoped.** It is a stateless JSON-RPC 2.0 server
   over the Streamable HTTP transport at `127.0.0.1:<8765..8775>/mcp`, bound to the
   loopback interface only, off by default, started only while the app is alive, and
   requiring `Authorization: Bearer <token>` on every POST. A token is a 256-bit random
   value shown once; only its SHA-256 digest is stored (`mcp_token`). Its scopes are
   `read:profile`, `read:training`, `read:day`, `write:body`, `write:nutrition` and
   `write:notes`; `tools/list` returns only what the token may call, and an insufficient
   scope is refused before the host is reached. The audit stores tool, scope, time and
   allowed/refused, never an argument. The documented surface is seven tools:
   `openhealth_get_profile`, `openhealth_get_training_plan`,
   `openhealth_list_recent_sessions`, `openhealth_get_day_summary`,
   `openhealth_log_weight`, `openhealth_log_water` and `openhealth_add_note`. A write
   carries a `raw_record` and provenance under source `mcp:<tokenId>` (kind MCP, priority
   80, method other), runs `ObservationValidator`, and lands in the normal repositories so
   the day's rollup rebuilds exactly as for a typed value. Water is an increment
   (`end = happened_at`) so two glasses sum, never replace. A read tool omits a rollup
   whose winning source is platform-derived, and weight-neutral mode hides loads and
   volume; the token's scopes are consent for the tool surface, not a Health Connect
   opt-in. This is the one plain-HTTP listener in the
   client: it is loopback, token-gated and explicit, and the privacy source test names it.

8. **One POST path, with a per-call timeout.** `HttpTransport` gains `post` and
   `HttpRequest.timeoutMillis`. The barcode lookup keeps its 8 s; a model call asks 60 s.
   A self-hosted endpoint may be plain HTTP only on `127.0.0.1`, `localhost`, `10.0.2.2` or
   `[::1]`; any other host must be HTTPS, so a typo cannot send health data in the clear.
   Because `HttpURLConnection` honours the Android network security policy, those four
   loopback names are the only `cleartextTrafficPermitted="true"` domains in
   `network_security_config.xml`; a remote host is still refused by both the config and the
   adapter. A URL with userinfo (`http://127.0.0.1@evil.example`) is rejected before any
   call, and bracketed IPv6 loopback is handled.

9. **Schema v12 and local-only tables.** `11.sqm` adds the `via_health_connect` column and
   the six coach/MCP tables. The conversation, proposals, snapshots, token digests and
   audit are declared never-synced (`SyncTables.neverSynced`) and their settings keys
   (`coach.*`, `mcp.*`) are local. The encrypted export includes them: the coach history is
   part of the person's record, the token table carries only a digest, and the audit names
   no values. A pre-v12 export restores with the new tables empty; the two known platform
   sources are re-flagged on restore, and every restored MCP token is marked revoked so a
   backup cannot revive an old client.

## What this phase does not claim

The V1 items beyond the MVP — weekly adaptation, "ask my data" with citations, cost and
token estimates, model choice beyond the two presets, local models, richer crisis and
eating-disorder filters — are not built. The MCP surface is local only; the optional Ktor
server exposes no MCP endpoint. Its six scopes are per domain, not one per module as
`pr.md` §6.30 sketches, and a dedicated MCP Health Connect opt-in is deferred: the current
surface omits platform-derived values instead. The Connect IQ and Wear surfaces are untouched.
