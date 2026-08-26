# PR10 — Wear OS workout companion design

Issue #10 adds a deliberately small, wrist-first companion for an active GymTracker workout. It does **not** make the watch a second workout database, history product, or Health Connect source.

Product loop remains **LOG → COMPARE → UNDERSTAND → PROGRESS**. The workout interaction remains **PREVIOUS + TARGET + TODAY**.

## 1. Verified baseline and non-negotiable boundary

Issue #10 started from verified `main` `b39f6ee60b7f9fd9e37a445df9d39eb1958854e8`, after Issue #9 / PR #18 was merged and its documentation-head Android CI #203 passed.

Phone Room remains the only canonical workout/history truth. `WorkoutSet` remains the canonical set record. Wear OS may retain only a minimal active-workout snapshot plus pending delivery operations required to survive connectivity loss, process death, or restart. That state is a delivery/recovery mechanism, not independent historical truth.

Issue #10 requires **no Room schema change**. Database version remains 2 and only committed schemas v1/v2 remain valid. There is no Wear Room table and no schema v3.

Health Connect is orthogonal to Wear sync. PR10 adds no Health Connect permission, write, background/history access, exercise-session import, recovery score, or persistence.

Portable backup/restore and CSV remain unchanged. Wear delivery state is intentionally outside canonical Room and outside backup/CSV.

## 2. Official Android/Wear research used for the design

Primary Android/Google guidance checked during implementation:

- Wear OS Data Layer overview and clients: https://developer.android.com/training/wearables/data/overview
- Sync data items with DataClient: https://developer.android.com/training/wearables/data/data-items
- Send messages with MessageClient: https://developer.android.com/training/wearables/data/messages
- Transfer streams with ChannelClient: https://developer.android.com/training/wearables/data/channel
- Advertise/discover app capabilities: https://developer.android.com/training/wearables/data/capabilities
- Listen for Data Layer events: https://developer.android.com/training/wearables/data/events
- Package/distribute Wear OS apps: https://developer.android.com/training/wearables/apps/packaging
- Standalone/non-standalone Wear apps: https://developer.android.com/training/wearables/apps/standalone-apps
- Compose for Wear OS: https://developer.android.com/training/wearables/compose
- Material 3 for Wear OS: https://developer.android.com/training/wearables/compose/material3
- Wear OS accessibility: https://developer.android.com/training/wearables/accessibility
- Wear OS testing: https://developer.android.com/training/wearables/apps/testing

Current Google Play Services wearable dependency used by PR10: `com.google.android.gms:play-services-wearable:20.0.1`.

### API choices

`DataClient` is the durable transport primitive for snapshot/request/journal DataItems. DataItems can be written while devices are disconnected and synchronize after connectivity returns. The watch still persists its pending journal locally because Data Layer synchronization is not application storage.

`MessageClient` is **not** a correctness path: messages require connected nodes and are not a persistent retry queue. `ChannelClient` is unnecessary because PR10 exchanges small structured payloads rather than files/streams. `CapabilityClient` is used only for reachability/installed-companion UX and never decides canonical truth. `NodeClient` is not needed for normal protocol routing.

## 3. Minimal Data Layer contract

All Data Layer payloads are versioned JSON shared by phone and watch through `:wear-protocol`.

Reserved paths:

- `/gymtracker/workout/request` — watch asks phone to republish current canonical active-workout state; a changing nonce makes repeated requests observable.
- `/gymtracker/workout/journal` — ordered watch pending-operation journal plus delivery nonce.
- `/gymtracker/workout/snapshot` — phone-published canonical active-workout snapshot plus terminal results for handled watch operations.

Writes are urgent because they originate from an active workout interaction. Correctness never depends on a particular delivery latency.

### Why one journal DataItem

The journal represents all watch operations that have not received a terminal phone result. If intermediate Data Layer changes are coalesced, the newest journal still contains every unacknowledged operation. The phone sorts operations by watch sequence before handling them. The watch removes an operation only after a phone snapshot includes a terminal result for that exact `operationId`.

The watch persists snapshot, pending journal, and next sequence in DataStore. Process death or watch restart therefore reconstructs pending intent and republishes it instead of silently dropping it.

## 4. Snapshot: only what the wrist needs

Phone publishes only the active workout, never History/Progress/Backup/Health Connect data.

Snapshot data includes:

- protocol version and snapshot nonce;
- active workout ID/title/start time or explicit no-active state;
- canonical rest-timer absolute end timestamp and owning workout-exercise ID;
- ordered active workout exercises with stable IDs, display name, position and target snapshot;
- ordered sets with stable canonical set ID, position, type, grams, reps, optional RIR tenths and completion timestamp;
- PREVIOUS values matched by set position and derived with unchanged PR4 previous-reference semantics;
- operation results keyed by `operationId`.

The watch derives current exercise/set from canonical snapshot plus its still-pending local overlay. It never maintains independent history.

## 5. Watch operation contract

Each operation contains:

- stable random `operationId`;
- monotonic watch-local `sequence`;
- `workoutId` and stable `setId`;
- operation kind / explicitly changed field;
- `expectedValue` and `desiredValue` for that field;
- for completion, the immutable user-action `completedAt` selected when the operation is created.

Supported PR10 mutations are only:

- edit load;
- edit reps;
- edit/clear optional RIR;
- complete current set.

Set type, notes, exercise/routine editing, History, Progress, Backup, Health Connect, and workout finish remain phone-only.

Phone validation preserves logger ranges: non-negative grams, reps 0–1000 for editing and >0 for completion, RIR null or 0–100 tenths. Missing/wrong/finished targets are rejected. Replay never regenerates a completion timestamp.

## 6. Idempotency, ordering, receipts, and conflicts

There is deliberately no clock-based or arbitrary last-write-wins rule.

For each mutation the phone executes against canonical Room state:

1. If the operation has a durable terminal receipt for the current active workout, return that exact prior result without reapplying it.
2. Otherwise, if the changed canonical value already equals `desiredValue`, treat replay as idempotent `APPLIED`.
3. Otherwise, if the canonical value equals `expectedValue`, apply only the requested field transactionally and return `APPLIED`.
4. Otherwise return `CONFLICT` and do not overwrite phone truth.

Only the explicitly changed field participates in conflict detection. A phone edit to reps therefore does not prevent a watch load-only edit. Same-field stale changes are rejected deterministically rather than silently overwriting canonical state.

### Durable phone operation receipts

Expected/desired comparison alone is insufficient for one lost-ACK case: operation A can be applied, operation B can later change the same field, and a replay of A could otherwise look stale even though A already succeeded.

To close that case without adding schema v3, phone stores a tiny terminal-receipt file using `AtomicFile` under `noBackupFilesDir`:

- key: `operationId`;
- metadata: active `workoutId`, terminal `APPLIED` / `CONFLICT` / `REJECTED`, and optional protocol reason;
- explicitly **not stored**: load, reps, RIR, completion timestamps, exercise/workout names, notes, health data, or canonical workout state.

Receipts are delivery metadata only. They are not Room truth, are excluded from Android backup by location, never enter GymTracker portable backup/restore or CSV, and are pruned when the active workout changes or ends.

This makes duplicate/lost-ACK replay deterministic across phone process death or restart while keeping Room version 2.

Terminal result meanings:

- `APPLIED` — canonical state reflects the operation, including replay of an already-applied operation;
- `CONFLICT` — same field changed elsewhere; canonical phone state wins and watch refreshes;
- `REJECTED` — invalid/stale target such as no active or finished workout.

## 7. Completion and rest timer

Existing phone semantics remain canonical:

- `WorkoutSet.completedAt` is completion truth.
- Successful completion writes `Workout.restTimerEndsAt = completedAt + restSeconds * 1000` plus owning workout-exercise ID.
- Remaining time is derived as `endsAt - now`; there is no second ticking timer database.

When watch completes offline, its durable operation keeps the original `completedAt`. Watch may derive a provisional rest end from that timestamp plus snapshotted rest duration. On reconnection phone applies/replays the same immutable timestamp and publishes canonical rest state.

A duplicate completion cannot create a second set or restart rest with a new time. If phone later stops its canonical timer, replay of an already-receipted completion returns the previous result instead of rerunning completion side effects.

PR10 does not add timer editing/stopping on the watch.

## 8. Connectivity and lifecycle behavior

### Connected

Watch persists intent locally first, publishes journal, phone applies against Room, phone publishes canonical snapshot/result, and watch removes only terminally acknowledged operations.

### Watch loses connection

Last active snapshot remains usable. New mutations are appended to local DataStore before delivery is attempted. Wrist logging therefore continues without making watch a second canonical database.

### Reconnection

Watch republishes the complete unacknowledged journal. Phone processes by sequence and publishes terminal results + canonical snapshot.

### Duplicate/out-of-order delivery

Stable operation IDs, durable terminal receipts, sequence ordering, and expected/desired conflict checks prevent duplicate side effects and blind rollback.

### Phone process death/restart

`WearableListenerService` receives Data Layer events without phone UI running. Room restores canonical workout truth and the `noBackupFilesDir` receipt store restores terminal delivery knowledge for the current active workout.

### Watch process death/restart

DataStore restores cached snapshot, pending journal, and sequence. The app republishes pending journal/request as needed.

### Phone edit while watch is stale

Different-field changes remain mergeable. Same-field changes produce explicit `CONFLICT`; phone canonical value wins.

### No active workout

Watch shows a minimal start/resume-on-phone state and does not expose a routine editor or invent a workout.

### Phone temporarily unavailable

With a cached active workout, watch may continue recording locally pending intent. Without cached active state, it waits for phone state rather than inventing truth.

## 9. Phone transaction boundary

PR10 adds synchronization adapters around the existing Room aggregate but no entity/table/column. Phone never trusts watch snapshot as truth; IDs only address an already-existing active workout/set. Historical workouts are not mutable through Wear.

The PR4 phone logger remains fully functional with the Wear module absent or no watch connected.

## 10. Wrist-first Material 3 UI

Wear UI is intentionally one task-focused surface:

- exercise name and set number;
- compact PREVIOUS / TARGET / TODAY;
- large load and reps controls without typing;
- optional RIR controls including clear;
- prominent Complete set action;
- rest timer;
- concise connected / saved-offline / syncing / conflict status;
- all-sets-complete state that directs finish/manage actions to phone.

No charts, full History, full Progress, routine editor, exercise library, backup, recovery UI, or broad settings surface.

The implementation uses current Wear Material 3 patterns, including `ScreenScaffold` and `TransformingLazyColumn`, instead of shrinking phone widgets or using the older Material `ScalingLazyColumn` pattern. Primary +/- controls are 52 dp and Complete is at least 56 dp with explicit semantics/content descriptions.

## 11. Packaging

A dedicated Wear application module uses the same application ID as the phone app, a unique version code, and required `android.hardware.type.watch` feature. It is **non-standalone** because phone/Room is canonical truth and starting/managing workouts remains a phone responsibility. Cached active-workout logging still tolerates temporary disconnects.

Phone advertises GymTracker workout-sync capability; watch uses capability reachability only for connection UX.

## 12. Test contract

All fixtures are synthetic and non-identifying.

Shared/JVM coverage includes protocol version/paths, serialization, pending-operation projection/order, current-set advancement, offline rest derivation, and no-active state.

Phone/Room instrumentation covers active/no-active snapshots, PREVIOUS/TARGET mapping, load/reps/RIR edits, completion, canonical rest end, duplicate replay, stale conflict, independent-field merge, no duplicate completion side effects, and durable receipt recreation.

Wear instrumentation covers no-active UI, current exercise/set + PREVIOUS/TARGET/TODAY, large action semantics and scrollability on a small round display, plus DataStore outbox restoration and terminal-result clearing across store recreation.

No CI test requires a physical watch, real pairing, real workout data, or personal health data.

## 13. CI contract

All existing hardened mobile gates remain mandatory:

1. mobile JVM tests;
2. semantic Room schema verification;
3. Room schema artifact;
4. mobile API 35 connected tests;
5. mobile lint;
6. mobile debug APK;
7. real minified/resource-shrunk mobile release APK;
8. mobile debug artifact;
9. mobile release artifact.

PR10 adds, rather than replaces, Wear gates:

- shared protocol JVM tests;
- Wear JVM tests;
- Wear/protocol lint;
- Wear connected UI/restart instrumentation on a hosted API 34 `android-wear` x86_64 small-round emulator;
- Wear debug APK;
- minified/resource-shrunk Wear release APK;
- Wear debug/release artifacts.

API 35 remains mandatory for the pre-existing **mobile** connected gate. API 34 is used for the dedicated Wear emulator because it is a stable hosted Wear system image for this CI lane; this does not weaken or replace the mobile API 35 contract.

The Room schema snapshot check must continue to prove v1/v2 semantically unchanged.

## 14. Explicit non-goals / safety audit checklist

PR10 must not:

- touch Pulso / pulso-finanzas;
- add cloud/backend/account requirements;
- create schema v3 without a demonstrated need (none exists);
- persist a second canonical workout/history database on watch;
- alter PR4 PREVIOUS/TARGET/TODAY, set types, finish semantics, or canonical rest behavior;
- alter PR5 PR/Epley/heaviest/reps-at-load/volume rules;
- alter PR6 analytics/progression semantics;
- alter PR7 portable backup/restore/CSV format;
- alter PR9 Health Connect permissions or data boundary;
- commit real workout/health data, device logs, credentials, secrets, or identifying fixtures;
- add health/workout analytics telemetry.
