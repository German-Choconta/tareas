# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), during Issue #10 / PR #19 implementation.

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

Issue #9 / PR #18 is also completely closed and was reverified directly in GitHub before Issue #10 work began:

- PR #18 — `GymTracker PR 9: Health Connect recovery context` — MERGED/CLOSED.
- Final PR head: `84a4107ff53b56364780ad8c92e54d88f3de5420`.
- Final exact-head Android CI: run `32924890756` (#201) — SUCCESS.
- PR artifacts:
  - `gymtracker-room-schema` — `9591172894`
  - `gymtracker-debug-apk` — `9591318921`
  - `gymtracker-release-apk` — `9591319691`
- Squash merge/main commit: `5136ce2a3939c691f8d6ab81f4e102e63ca52481`.
- Issue #9 — CLOSED / COMPLETED.
- Post-merge main CI: run `32926429035` (#202) — SUCCESS.
- Post-merge artifacts:
  - `gymtracker-room-schema` — `9591706046`
  - `gymtracker-debug-apk` — `9591852116`
  - `gymtracker-release-apk` — `9591852485`
- Final Issue #9 documentation head on main: `b39f6ee60b7f9fd9e37a445df9d39eb1958854e8` — `Document final Issue 9 closure`.
- Documentation-head CI: run `32927064134` (#203) — SUCCESS.
- Documentation-head artifacts:
  - `gymtracker-room-schema` — `9591907058`
  - `gymtracker-debug-apk` — `9591966376`
  - `gymtracker-release-apk` — `9591967052`

Issue #10 was branched from exact main `b39f6ee60b7f9fd9e37a445df9d39eb1958854e8` only after all of the above was verified.

## Current stage — Issue #10 / PR #19

Real Issue #10: **Post-V1 — Wear OS workout companion**.

Goal: minimal wrist-first active-workout companion so sets can be logged without repeatedly using the phone.

Current GitHub state at this handoff update:

- Branch: `feat/wear-os-workout-companion`.
- PR #19: `GymTracker PR 10: Wear OS workout companion`.
- PR #19 is intentionally **DRAFT**.
- Base: `main` at the verified Issue #9 documentation head.
- Implementation head immediately before this documentation update: `68b8b0975cf8ba765ca1aab559cf9b1579caf7c0`.
- The documentation update itself creates a newer PR head; always re-fetch the exact head before using any CI result.
- Issue #10 remains open while PR #19 is in progress.
- **Do not mark ready, merge, close Issue #10, or begin Issue #11 until the exact final PR head passes the full contract below and the final audit/review is clean.**

Design source: `docs/PR10_WEAR_OS_DESIGN.md`.

## Issue #10 verified architecture

### Canonical truth

Phone Room remains the **only canonical workout/history truth**. `WorkoutSet` remains the canonical set record. The watch is not a second history database and cannot create an independent workout truth.

Room stays at **DB version 2** with committed schemas **v1/v2 only**. Issue #10 has no demonstrated reason for schema v3 and currently makes **no Room schema change**.

The watch may persist only:

- a minimal active-workout snapshot required for wrist interaction; and
- a durable pending-operation outbox required to survive connectivity loss/process death/restart.

That watch state is delivery/recovery state, not historical truth. Wear `android:allowBackup` is disabled.

The phone additionally persists only terminal Wear operation receipts (operation/workout IDs + APPLIED/CONFLICT/REJECTED status/reason) in `noBackupFilesDir`. These receipts contain no load/reps/RIR/timestamps/names/notes/health values and exist only to make lost-ACK replay durable across phone process death. They are not Room truth and cannot enter backup/restore/CSV.

### Data Layer transport

Official current Android/Wear guidance was checked before implementation. The design uses:

- `DataClient` for durable DataItems/state synchronization, including disconnected writes/reconnect delivery;
- `CapabilityClient` only for reachability/installed-companion UX;
- `WearableListenerService` on phone for Data Layer events when UI is not alive.

`MessageClient` is deliberately **not** a correctness path because messages require connectivity and are not persisted/retried. `ChannelClient` is unnecessary for these small structured payloads. `NodeClient` is unnecessary for normal routing.

Current Google Play Services Wearable dependency: `com.google.android.gms:play-services-wearable:20.0.1`.

Protocol paths:

- `/gymtracker/workout/request`
- `/gymtracker/workout/journal`
- `/gymtracker/workout/snapshot`

Shared protocol module: `:wear-protocol`.

### Sync / conflict contract

Watch mutations are narrow one-field operations with a stable `operationId`, monotonic watch-local `sequence`, target workout/set IDs, operation kind, `expectedValue`, and `desiredValue`.

Supported operations:

- edit load;
- edit reps;
- edit/clear optional RIR;
- complete current set.

Phone applies operations through Room transactions. For each changed field:

1. if canonical current value already equals `desiredValue`, replay is idempotent APPLIED;
2. otherwise, if canonical current value equals `expectedValue`, apply only that field;
3. otherwise return deterministic CONFLICT and do not overwrite phone truth.

The phone receipt store remembers terminal results for the current active workout so a lost ACK followed by later same-field operations cannot turn a previously applied operation into a false conflict. Receipt metadata is pruned when the active workout changes/ends.

Stale/wrong/finished workout targets are REJECTED. No blind clock-based last-write-wins exists.

Different-field edits remain mergeable: for example, a phone load edit does not block a watch reps-only edit. A same-field phone edit wins through explicit conflict rather than silent overwrite.

### Offline / lifecycle behavior

- Connected: watch persists user intent locally first, publishes pending journal, phone applies Room transaction, phone publishes canonical snapshot/result, watch clears only terminally acknowledged operations.
- Connection lost: cached active snapshot remains usable; watch operation is saved locally before delivery is attempted.
- Reconnect: watch republishes the complete unacknowledged journal in sequence order.
- Duplicate/retransmitted journal: stable IDs + phone receipts + expected/desired comparison keep replay idempotent.
- Out-of-order delivery: journal sequence ordering prevents blind rollback; stale writes cannot overwrite a different canonical field value.
- Watch process death/restart: DataStore restores cached snapshot, pending journal and next sequence; pending work is republished.
- Phone process death/restart: Room restores canonical truth; receipt metadata in `noBackupFilesDir` restores terminal operation knowledge for the active workout.
- Phone edit while watch is stale: same-field conflict is explicit; different fields are preserved.
- No active workout: watch shows a minimal start/resume-on-phone state and does not invent a workout.
- Phone temporarily unavailable: cached active workout can continue generating locally saved pending operations; without a cached active workout the watch waits for phone state instead of inventing truth.

### Rest timer

Existing PR4 semantics remain canonical:

- `WorkoutSet.completedAt` is completion truth.
- canonical rest end remains the existing absolute `Workout.restTimerEndsAt` plus owning workout-exercise ID.
- watch completion records one immutable user-action completion timestamp.
- watch can derive provisional offline rest end from completion timestamp + snapshotted rest seconds.
- replay never creates a new completion timestamp and therefore cannot restart rest merely because delivery was duplicated.
- if the phone stops the canonical timer after completion, replay of an already-applied completion must not restart it.

No separate ticking timer database is introduced.

## Wrist-first UI scope

The Wear app intentionally includes only:

- current exercise and current set;
- compact PREVIOUS / TARGET / TODAY context;
- large load +/- controls using the snapshotted load increment;
- large reps +/- controls;
- optional RIR +/- and clear;
- prominent Complete set action;
- rest countdown from an absolute end timestamp;
- concise connected/syncing/saved-offline/conflict status;
- minimal no-active/all-sets-complete states directing start/finish management to phone.

It intentionally excludes full History, Progress/charts, routine editor, exercise library, backup UI, Health Connect/recovery UI, workout finish flow and broad settings.

Wear Compose/Material components are used instead of shrinking phone UI. Primary +/- controls are 52 dp and Complete is at least 56 dp with explicit semantics/content descriptions.

## Packaging/modules

Current modules:

- `:app` — canonical phone application/Room source of truth.
- `:wear-protocol` — shared versioned serialization/state projection contract.
- `:wear` — dedicated Wear OS application.

Wear packaging:

- same application ID as phone: `com.germanchoconta.gymtracker`;
- unique Wear version code;
- `android.hardware.type.watch` required;
- marked non-standalone because starting/managing canonical workouts depends on phone;
- cached active-workout interaction still tolerates temporary disconnects.

Phone advertises capability `gymtracker_phone_workout_sync`.

## Tests added for Issue #10

All new fixtures are synthetic/non-identifying.

Shared/JVM coverage includes:

- protocol version/paths;
- serialization round-trip including null RIR;
- deterministic pending-operation projection/order;
- current-set advancement after pending completion;
- offline rest-end derivation;
- no-active state.

Phone/Room instrumentation includes:

- duplicate operation replay/idempotency;
- same-field stale conflict without overwrite;
- independent-field merge/preservation;
- completion timestamp persistence;
- canonical rest timer end;
- duplicate completion does not restart a phone-stopped timer;
- PREVIOUS/TARGET snapshot mapping using existing semantics;
- no-active snapshot;
- terminal operation receipt persistence across store/service recreation.

Wear instrumentation includes:

- minimal no-active UI;
- current exercise/set + PREVIOUS/TARGET context;
- accessible action semantics;
- pending offline operation persistence across `WearSyncStore` recreation;
- clearing pending work only after terminal phone result.

No test requires a physical watch, real pairing, real workout data or personal health data.

## CI contract for PR #19

Existing hardened mobile gates remain present and must all pass on the exact final head:

1. mobile JVM tests;
2. semantic Room schema verification (must prove v1/v2 unchanged);
3. `gymtracker-room-schema` artifact;
4. mobile API 35 connected tests;
5. mobile lint;
6. mobile debug APK;
7. real minified/resource-shrunk mobile release APK;
8. `gymtracker-debug-apk` artifact;
9. `gymtracker-release-apk` artifact.

Added Wear gates:

- shared protocol JVM tests;
- Wear JVM tests;
- Wear/protocol lint;
- Wear OS connected UI/restart tests on a hosted `android-wear` round emulator;
- Wear debug APK;
- Wear minified/resource-shrunk release APK;
- `gymtracker-wear-debug-apk` artifact;
- `gymtracker-wear-release-apk` artifact.

CI history that must **not** be mistaken for final evidence:

- run #206 / `32928727521` failed at Wear compilation because of an invalid explicit `item` DSL import. Protocol compilation/tests had passed. Fixed in commit `f1482931e05d85d8fbc69eda15da97fa449104b4`.
- later intermediate heads demonstrated passing mobile JVM + Wear protocol/Wear JVM + semantic Room verification, but they are not final-head evidence.
- Current exact-head CI must always be re-fetched after every commit.

## PR4–PR9 invariants that Issue #10 must not change

### PR4 — Workout logger

- Phone Room remains canonical workout truth.
- `WorkoutSet` remains canonical set truth.
- Immediate autosave and active-workout recovery remain intact.
- PREVIOUS + TARGET + TODAY meaning is unchanged.
- Set types, notes, rest timer and finish semantics remain intact.
- An active workout keeps priority.

### PR5 — History / PR engine

- Do not change PR eligibility.
- Do not change Epley.
- Do not change heaviest-load, reps-at-load or volume semantics.
- Wear is not a second historical source.

### PR6 — Progress

- Do not change analytics/formulas.
- Do not add recovery/progression prescriptions.

### PR7 — Backup

- Portable backup/restore and CSV formats remain unchanged.
- Wear snapshots/outboxes/phone receipt metadata must never enter portable backup/CSV.

### PR8 — UX/reliability

- Do not degrade phone accessibility, small-screen, recreation, error or loading handling.
- Keep the full hardened CI contract.

### PR9 — Health Connect

- Health Connect remains optional/read-only.
- Wear sync is separate from Health Connect.
- No new Health Connect permission.
- No background/history permission.
- No Health Connect write or `ExerciseSessionRecord`.
- No recovery score or progression prescription.
- No Health Connect persistence in Room.

## Final audit required before ready/merge

Before marking PR #19 ready:

- re-fetch exact PR head;
- confirm no Pulso/pulso-finanzas changes;
- inspect all changed files/patches;
- confirm no real/personal/sensitive fixtures/logs/secrets;
- confirm only Room schemas v1/v2 and no DB version change;
- confirm no portable backup/CSV change;
- confirm no Health Connect permission/scope change;
- confirm no cloud/backend/account dependency;
- confirm no PR4–PR9 semantic change;
- confirm mobile still functions without watch;
- confirm duplicate/lost-ACK/out-of-order/restart paths cannot duplicate or silently overwrite sets;
- confirm all PR reviews/comments/threads are resolved;
- confirm **all jobs of the exact final head** are SUCCESS;
- verify all five expected artifacts on that exact head: Room schema, mobile debug/release APK, Wear debug/release APK.

Only then mark ready. Re-check exact head/status after ready. Merge only with expected-head protection. Then verify Issue #10 CLOSED/COMPLETED and run/artifacts on post-merge main.

After merge, update this file on main with final evidence, verify the documentation-head CI/artifacts, and only then read the next real GitHub issue and provide the next-stage prompt. **Do not start Issue #11 silently.**
