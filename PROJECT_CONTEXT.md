# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-26 (America/Bogota), during Issue #21 / PR #22 implementation, after successful CI #225 on the stabilized implementation head and before the final exact-head gate.

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

## Final documentation-head gate resolution

The implementation code for Issue #10 is fully validated by Android CI #220 on exact squash `7a6ef8063825f920ccb8566311bc4e59228fad59`, including both jobs and all five expected artifacts.

After that validated squash, only documentation changed before the gate investigation:

- `1888de23b2ebe2818f231ce240e5cd88de05f9c5` modified only `PROJECT_CONTEXT.md`;
- PR #20 (`GymTracker docs: validate final Issue 10 closure`) changed exactly one file, `docs/ISSUE10_FINAL_VALIDATION.md`, with +7 / -0 and was squash-merged as `e5e6a04b170fe699e31db3e29fb0645ee828f062`.

At the time of the final gate investigation, `main` pointed exactly to `e5e6a04b170fe699e31db3e29fb0645ee828f062`.

A real Android CI push run eventually appeared for that exact documentation head:

- run `32984560582` (#222);
- event `push`;
- exact head `e5e6a04b170fe699e31db3e29fb0645ee828f062`;
- workflow-level status `completed` / conclusion `failure`.

This was **not a test/build failure**. GitHub never assigned either job to a runner:

- `test-build` remained `queued`, with no runner, no executed steps and no conclusion;
- `wear-connected` remained `queued`, with no runner, no executed steps and no conclusion;
- the run produced zero artifacts.

The connected GitHub integration exposes no workflow-dispatch action for starting a fresh run on the same SHA. The available safe retry operation was attempted against #222 and GitHub rejected it with `403: This workflow run cannot be retried`.

Therefore the documentation-head gate is resolved as an **execution/infrastructure limitation, not SUCCESS and not a product regression**. Never attribute CI #220 to `e5e6a04...`, and never claim #222 passed. The canonical implementation evidence remains #220 on `7a6ef806...`; the later changes before this resolution were documentation-only and introduced no application, CI workflow, Room, backup/CSV, Health Connect, Wear protocol or product-semantic changes.

This documentation update exists only to record that resolution. Do **not** create an infinite chain of documentation-only commits solely to demand CI for the commit that documents the previous CI state. A future product/code/configuration change must still be validated on its own exact head under the normal hardened CI contract.

Issue #10 is considered technically/documentally closed under that distinction.

## Issue #21 / PR #22 — DRAFT IMPLEMENTATION STATE

Issue #21: **Post-V1 — Deterministic progression recommendations**.
PR #22: `GymTracker PR 11: deterministic progression recommendations`.
Branch: `feat/deterministic-progression-recommendations`.
Design contract: `docs/ISSUE21_PROGRESSION_DESIGN.md`.

Issue #21 was created only after verifying the real GitHub state; GitHub assigned number **#21**. Do not rename/re-number it to an assumed Issue #11.

### Baseline and pre-implementation gate

The feature branch was created from exact main `508e0c75127fb784f85895dc5bbb7e288e52c07b`.

Android CI #223 (`32991824542`) on that exact main head initially failed only the dedicated Wear connected test `WearWorkoutScreenTest.noActiveWorkoutStaysMinimal` with `No compose hierarchies found in the app`. The entire `test-build` job passed. A safe re-run of only the failed Wear job then passed on the same exact SHA; attempt 2 of #223 was **SUCCESS**. Treat the first failure as an observed instrumentation flake, not a reproducible product regression.

### Progression architecture locked for Issue #21

- One progression engine only: evolve the pre-existing foundational `ProgressionEngine`; do not create a parallel coaching engine.
- Production truth uses integer grams (`Long`) and RIR tenths (`Int?`), not `Double` kilograms.
- Recommendations are derived/recalculable output and are never persisted as canonical history/routine truth.
- Room remains version **2**, schemas **v1/v2 only**; no recommendation cache/table/column and no schema v3.
- Evidence is only completed `WORK` sets from finished workouts, with reps > 0.
- `WARMUP`, `DROP`, `FAILURE`, incomplete sets and active workouts cannot drive normal double progression.
- Existing `ANY_WORKOUT` / `SAME_ROUTINE` semantics remain canonical.
- Comparison is per same set position with deterministic newest ordering and at most one observation per prior workout.
- Legacy/incomplete TARGET snapshots degrade to no recommendation rather than inventing defaults.
- Zero/bodyweight load never generates a made-up positive external load.
- Top-of-range can increase exact configured load increment; optional RIR can conservatively block an increase when actual effort was harder than target.
- Missing actual RIR does not block progression; rationale states when the decision is reps-only.
- In-range performance holds load and suggests a non-canonical next-rep aim.
- One under-range session never triggers reduction.
- Reduction requires the latest two different comparable finished workouts to be under-range at the same exact positive load; differing loads produce `REVIEW`.
- Checked integer addition prevents overflow; unsafe arithmetic produces `REVIEW`.
- Each recommendation carries explicit `BASE WORK` evidence (load/reps and optional RIR) before its explanation.
- PR5 `PersonalRecordEngine.estimatedOneRepMax` remains the sole canonical e1RM implementation; the duplicate Epley helper was removed from `ProgressionEngine`.

### User-control / UI contract

- Recommendations appear in the phone workout logger with TARGET / PREVIOUS / TODAY.
- User sees a structured action (`NO_BASELINE`, `INCREASE_LOAD`, `HOLD_LOAD`, `REDUCE_LOAD`, `REVIEW`), concrete evidence and rationale.
- `Aplicar <kg>` is explicit user action only.
- Applying a suggestion routes through the existing TODAY load autosave path and changes only active set load.
- It never writes suggested reps as performed reps, never writes RIR/completion/history, and never mutates routine targets.
- User can ignore or override every recommendation.
- Wear recommendation UI/protocol is out of scope and unchanged.
- Health Connect remains optional/read-only context and never modifies prescribed load in this issue.

### Validation history before the final gate

Initial PR CI #224 (`32996149558`) on head `4d5087411c117df54c6e9f6456171cbd5fcd7fdd` failed during mobile unit-test compilation because the existing synthetic `HistoryViewModelTest.FakeHistoryDao` had not yet implemented the newly added read-only DAO method. Production Kotlin compilation had already passed. The fake was updated with an empty synthetic implementation; no History semantics changed.

Stabilized head `b6eef192432563271ff9df52cf7332c85bce479b` then passed Android CI #225 (`32996421831`) completely:

- `test-build` — SUCCESS;
- `wear-connected` — SUCCESS;
- mobile JVM tests — SUCCESS;
- Wear protocol/Wear JVM tests — SUCCESS;
- semantic Room schema verification — SUCCESS;
- mobile API 35 connected tests — SUCCESS;
- mobile lint — SUCCESS;
- Wear/protocol lint — SUCCESS;
- mobile debug/release builds — SUCCESS;
- Wear debug/release builds — SUCCESS;
- all expected uploads — SUCCESS.

Artifacts from CI #225 / `b6eef192...`:

- `gymtracker-room-schema` — `9616778236`;
- `gymtracker-debug-apk` — `9617030304`;
- `gymtracker-release-apk` — `9617031787`;
- `gymtracker-wear-debug-apk` — `9617033521`;
- `gymtracker-wear-release-apk` — `9617034610`.

### Final-candidate additions in the commit containing this handoff

The commit containing this section also adds final acceptance hardening:

- recommendation explanations begin with concrete `BASE WORK` evidence using exact integer-derived kg/RIR formatting;
- a JVM test locks that evidence string;
- an Android instrumentation test uses real in-memory Room + the real `WorkoutLoggerViewModel` to prove explicit `applySuggestedLoad()` persists only `loadGrams` while preserving reps, RIR, type, completion and TARGET snapshot;
- the same instrumentation test proves a legacy/incomplete TARGET snapshot produces no recommendation even when valid comparable history exists.

**Do not mark PR #22 ready and do not merge it based on CI #225 alone.** The exact GitHub head after this final-candidate commit must pass the complete hardened Android CI contract again. Verify the final SHA, both jobs, all five artifacts, PR diff/reviews/comments and scope/privacy audit directly in GitHub before ready/merge. GitHub wins if this section is stale.

Do not start another issue while #21 / PR #22 is open.