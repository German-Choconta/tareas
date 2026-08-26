# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-26 (America/Bogota), after Issue #10 / PR #19 merge and successful post-merge validation.

## Repository and permanent rules

- Repository: `German-Choconta/tareas` (public).
- GymTracker only. **Never touch Pulso / pulso-finanzas.**
- Never commit real workout/health data, sleep, HR/RHR/HRV, names, private exports/backups, credentials, tokens, secrets, device logs with personal data, or other identifying information.
- Tests/fixtures must remain synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Work directly in GitHub, keep this handoff current, verify exact heads/CI/artifacts before advancing, and never advance to the next issue silently.

## Completed baseline through Issue #9

Core V1 (PR1–PR8) is completed/release-ready under the repository contract. Do not reopen or change those semantics without a concrete, reproducible regression.

Issue #9 / PR #18 — Health Connect recovery context — is also completely closed:

- Final PR head: `84a4107ff53b56364780ad8c92e54d88f3de5420`.
- Final PR CI: run `32924890756` (#201) — SUCCESS.
- Squash/main merge: `5136ce2a3939c691f8d6ab81f4e102e63ca52481`.
- Issue #9 — CLOSED / COMPLETED.
- Post-merge CI: `32926429035` (#202) — SUCCESS.
- Final Issue #9 documentation head: `b39f6ee60b7f9fd9e37a445df9d39eb1958854e8`.
- Documentation-head CI: `32927064134` (#203) — SUCCESS.

Issue #10 branched from exact verified main `b39f6ee60b7f9fd9e37a445df9d39eb1958854e8`.

## Issue #10 / PR #19 — FINAL CLOSED STATE

Issue #10: **Post-V1 — Wear OS workout companion**.
PR #19: `GymTracker PR 10: Wear OS workout companion`.
Branch used: `feat/wear-os-workout-companion`.
Design source: `docs/PR10_WEAR_OS_DESIGN.md`.

### Final PR evidence

- Final exact PR head: `473dd18d1345aaf31f2f5816a5d160fa040f57ba`.
- Final exact-head Android CI: run `32977841838` (#219) — **SUCCESS**.
- Both jobs passed on that exact head:
  - `test-build` — SUCCESS;
  - `wear-connected` — SUCCESS.
- Final PR artifacts on exact head `473dd18d1345aaf31f2f5816a5d160fa040f57ba`:
  - `gymtracker-room-schema` — `9610274357`;
  - `gymtracker-debug-apk` — `9610686849`;
  - `gymtracker-release-apk` — `9610688066`;
  - `gymtracker-wear-debug-apk` — `9610689649`;
  - `gymtracker-wear-release-apk` — `9610690667`.
- Final audit: 27 changed files, all GymTracker/Wear/CI/docs scope.
- Reviews: 0.
- PR conversation comments: 0.
- Review threads: 0.
- PR was kept DRAFT until exact-head CI and final audit were clean, then marked READY.

### Final scope/safety audit

Confirmed before ready/merge:

- no Pulso / pulso-finanzas paths;
- no real workout/health/personal data, secrets or identifying fixtures;
- Room DB remains version **2** with committed schemas **v1/v2 only**;
- no schema v3 and no Room schema file changes;
- no portable backup/restore/CSV format or semantics change;
- no Health Connect permission/scope change; the existing three read permissions remain unchanged;
- no cloud/backend/account requirement;
- no PR4–PR9 semantic change;
- existing phone app still passes the full hardened mobile contract without requiring a watch;
- Wear state is delivery/recovery state only, not a second canonical workout/history database;
- duplicate/lost-ACK/out-of-order/restart paths are guarded by stable operation IDs, sequence ordering, expected/desired field checks and durable terminal receipts;
- terminal phone receipts live under `noBackupFilesDir` and store only operation/workout IDs + terminal status/reason, never load/reps/RIR/timestamps/names/notes/health values.

### Merge / Issue closure

- PR #19 was squash-merged with `expected_head_sha=473dd18d1345aaf31f2f5816a5d160fa040f57ba`.
- Squash/main commit: `7a6ef8063825f920ccb8566311bc4e59228fad59`.
- PR #19 — MERGED / CLOSED.
- Issue #10 — CLOSED / COMPLETED automatically via `Closes #10`.
- `main` immediately after merge pointed exactly to `7a6ef8063825f920ccb8566311bc4e59228fad59`.

### Post-merge main validation

Android CI run `32983378019` (#220), event `push`, exact main head `7a6ef8063825f920ccb8566311bc4e59228fad59` — **SUCCESS**.

All post-merge gates passed:

1. mobile JVM tests;
2. Wear protocol + Wear JVM tests;
3. semantic Room schema verification;
4. Room schema artifact;
5. mobile API 35 connected tests;
6. mobile lint;
7. Wear/protocol lint;
8. mobile debug APK;
9. minified/resource-shrunk mobile release APK;
10. Wear debug APK;
11. minified/resource-shrunk Wear release APK;
12. all four APK uploads;
13. dedicated Wear OS connected instrumentation on API 34 small-round emulator.

Post-merge artifacts on exact squash `7a6ef8063825f920ccb8566311bc4e59228fad59`:

- `gymtracker-room-schema` — `9612460942`;
- `gymtracker-debug-apk` — `9612719863`;
- `gymtracker-release-apk` — `9612720424`;
- `gymtracker-wear-debug-apk` — `9612721174`;
- `gymtracker-wear-release-apk` — `9612721626`.

## Final Issue #10 architecture contract

### Canonical truth

Phone Room remains the **only canonical workout/history truth**. `WorkoutSet` remains canonical set truth. Wear does not own independent workout history.

The watch persists only:

- a minimal active-workout snapshot for wrist interaction; and
- a durable pending-operation outbox in DataStore for disconnect/process-death/restart recovery.

Wear backup is disabled. The phone persists only minimal terminal operation receipts under `noBackupFilesDir`; those receipts are delivery metadata, not workout truth and never enter portable backup/restore/CSV.

### Data Layer

Shared protocol module: `:wear-protocol`.
Dedicated Wear application module: `:wear`.
Phone module remains `:app`.

Transport contract:

- `DataClient` for durable request/journal/snapshot DataItems;
- `CapabilityClient` only for reachable-phone UX;
- `WearableListenerService` on phone for background Data Layer events;
- `MessageClient` is not a correctness path;
- no ChannelClient/NodeClient dependency for normal protocol operation.

Protocol paths:

- `/gymtracker/workout/request`
- `/gymtracker/workout/journal`
- `/gymtracker/workout/snapshot`

Google Play Services Wearable dependency: `20.0.1`.

### Sync / conflicts / idempotency

Watch mutations are narrow one-field operations with stable `operationId`, monotonic sequence, target workout/set IDs, operation kind, `expectedValue`, and `desiredValue`.

Supported mutations:

- edit load;
- edit reps;
- edit/clear optional RIR;
- complete current set.

Phone applies against canonical Room inside a write transaction:

1. a durable terminal receipt returns the same prior terminal result;
2. canonical value already equal to desired value is idempotent APPLIED;
3. canonical value equal to expected value applies only that field;
4. otherwise same-field stale state is CONFLICT and phone truth is not overwritten.

Different-field edits remain mergeable. Stale/wrong/finished workout targets are REJECTED. There is no blind last-write-wins.

### Rest timer

PR4 semantics remain canonical:

- `WorkoutSet.completedAt` is completion truth;
- canonical rest end is existing `Workout.restTimerEndsAt` + owning workout-exercise ID;
- watch completion creates one immutable user-action completion timestamp;
- replay never regenerates the timestamp;
- duplicate replay cannot restart a phone-stopped canonical timer.

### Wrist-first UI

Wear UI intentionally contains only:

- current exercise/current set;
- individually scrollable PREVIOUS / TARGET / TODAY rows;
- large load +/-;
- large reps +/-;
- optional RIR +/- and clear;
- prominent Complete set;
- rest countdown;
- concise connected/syncing/saved-offline/conflict state;
- minimal no-active/all-sets-complete states that direct workout start/finish management to phone.

It does not duplicate full phone History, Progress, routine editor, exercise library, backup, Health Connect/recovery, workout finish flow or broad settings.

## PR4–PR9 invariants retained after Issue #10

- PR4: immediate Room autosave, active-workout recovery, PREVIOUS + TARGET + TODAY, set types, notes, rest and finish semantics unchanged.
- PR5: History/PR eligibility, Epley, heaviest-load, reps-at-load and volume semantics unchanged.
- PR6: Progress/analytics formulas unchanged; no recovery-driven prescription added.
- PR7: portable backup/restore and CSV unchanged; Wear snapshots/outbox/receipt metadata are excluded.
- PR8: hardened phone accessibility, small-screen/recreation/error/loading behavior and full CI contract retained.
- PR9: Health Connect remains optional/read-only with the same narrow permissions; Wear sync is separate from Health Connect.

## Current validation gate after this documentation commit

This `PROJECT_CONTEXT.md` update creates a new documentation-only main head after the fully validated Issue #10 squash.

Before advancing beyond Issue #10:

1. fetch the exact documentation head created by this commit;
2. require its Android CI to complete SUCCESS, including both `test-build` and `wear-connected`;
3. verify its five artifacts: Room schema, mobile debug/release APK, Wear debug/release APK;
4. confirm PR #19 remains merged/closed and Issue #10 remains closed/completed;
5. confirm `main` points to the exact documentation head;
6. only after those checks, read the **next real GitHub issue** directly from GitHub and provide the next-stage continuation prompt;
7. **do not implement the next issue silently in the same stage.**

Never accept an older SHA as evidence for a newer head.
