# PR10 — Wear OS workout companion design

Issue #10 adds a deliberately small, wrist-first companion for an active GymTracker workout. It does **not** make the watch a second workout database, history product, or Health Connect source.

Product loop remains **LOG → COMPARE → UNDERSTAND → PROGRESS**. The workout interaction remains **PREVIOUS + TARGET + TODAY**.

## 1. Verified baseline and non-negotiable boundary

Issue #10 starts from `main` `b39f6ee60b7f9fd9e37a445df9d39eb1958854e8` after Issue #9 / PR #18 was merged and its documentation-head Android CI #203 passed.

Phone Room remains the only canonical workout/history truth. `WorkoutSet` remains the canonical set record. Wear OS can temporarily retain a minimal active-workout snapshot and pending user operations so an interrupted connection cannot lose a set, but that temporary state is a delivery/recovery mechanism, not an independent historical database.

Issue #10 requires **no Room schema change**. Database version remains 2 and only committed schemas v1/v2 remain valid. There is no Wear table and no schema v3.

Health Connect is orthogonal to Wear sync. PR10 adds no Health Connect permission, write, background/history access, exercise-session import, recovery score, or persistence.

Portable backup/restore and CSV stay unchanged. Wear delivery state is intentionally outside canonical Room and outside backup/CSV.

## 2. Official Android/Wear research used for the design

Primary Android/Google guidance checked at implementation time:

- Wear OS Data Layer overview and clients: https://developer.android.com/training/wearables/data/overview
- Sync data items with DataClient: https://developer.android.com/training/wearables/data/data-items
- Send messages with MessageClient: https://developer.android.com/training/wearables/data/messages
- Transfer streams with ChannelClient: https://developer.android.com/training/wearables/data/channel
- Advertise/discover app capabilities: https://developer.android.com/training/wearables/data/capabilities
- Listen for Data Layer events: https://developer.android.com/training/wearables/data/events
- Package/distribute Wear OS apps: https://developer.android.com/training/wearables/apps/packaging
- Standalone/non-standalone Wear apps: https://developer.android.com/training/wearables/apps/standalone-apps
- Compose for Wear OS: https://developer.android.com/training/wearables/compose
- Wear OS accessibility: https://developer.android.com/training/wearables/accessibility
- Wear OS testing: https://developer.android.com/training/wearables/apps/testing

The current Data Layer dependency documented by Google is `com.google.android.gms:play-services-wearable:20.0.1`.

### API choices

`DataClient` is the durable transport primitive for state and pending-operation journal delivery. Data items can be written while devices are disconnected and synchronize after connectivity returns. The watch still keeps its own pending-operation copy because Google explicitly warns that Data Layer synchronization is not a replacement for app storage.

`MessageClient` is **not** the correctness path: messages require connected nodes and are not persisted/retried. It may be useful for future transient hints, but PR10 does not need it.

`ChannelClient` is not needed: payloads are small structured state, not files or long byte streams.

`CapabilityClient` is used only for reachability/installed-feature UX. It does not decide truth and a temporarily unreachable phone never blocks local wrist logging.

`NodeClient` is not needed for normal protocol routing because capability discovery is more precise than assuming every connected Android node has GymTracker.

## 3. Minimal Data Layer contract

All Data Layer payloads are versioned JSON shared by the phone and watch through a small protocol module. The active-workout payload remains well below the DataItem limit; no Assets are needed.

Reserved paths:

- `/gymtracker/workout/request` — watch asks the phone to republish current canonical active-workout state; includes a changing request nonce so repeated requests generate an event.
- `/gymtracker/workout/journal` — ordered watch pending-operation journal plus a changing delivery nonce.
- `/gymtracker/workout/snapshot` — phone-published canonical active-workout snapshot plus deterministic results for handled watch operations.

Each write is urgent because it is driven by an active workout interaction. Correctness does not depend on an exact delivery latency.

### Why one journal DataItem instead of one DataItem per mutation

A single journal represents the watch's ordered pending state. If intermediate Data Layer changes are coalesced or delivered after reconnection, the newest journal still contains every operation that has not yet been acknowledged. The phone processes journal operations in sequence order. The watch removes an operation only after a phone snapshot reports a terminal result for that exact operation ID.

The watch also persists this journal locally. Process death or a watch restart therefore reconstructs pending operations and republishes them instead of silently dropping them.

## 4. Snapshot: only what the wrist needs

The phone publishes only the active workout, never History/Progress/Backup/Health Connect data.

Snapshot fields include:

- protocol version and server-generated snapshot revision/nonce;
- active workout ID/title/start time or explicit `NO_ACTIVE_WORKOUT`;
- canonical rest-timer absolute end timestamp and owning workout-exercise ID;
- ordered active workout exercises with ID, display name, position and target snapshot (set count, rep range, target RIR, rest seconds, load increment, previous-reference mode);
- ordered sets with stable canonical set ID, position, type, grams, reps, optional RIR tenths and completion timestamp;
- PREVIOUS values by matching set position, derived using the unchanged PR4 previous-reference contract;
- operation results keyed by operation ID.

The watch derives the current exercise/set as the first incomplete set after applying its still-pending local operations as an overlay. It does not maintain an independent history database.

## 5. Watch operation contract

A watch operation contains:

- stable random `operationId`;
- monotonic watch-local `sequence`;
- `workoutId` and stable `setId`;
- operation kind / explicit changed fields;
- expected value for every field the operation changes;
- desired value for every field the operation changes;
- for completion, the immutable user-action `completedAt` timestamp selected when the operation is created.

Supported PR10 mutations are only:

- edit load;
- edit reps;
- edit/clear optional RIR;
- complete the current set.

Set type, exercise/routine editing, notes, History, Progress, Backup, Health Connect and workout finish remain phone-only in PR10.

### Validation

The phone applies the same canonical ranges as the logger: non-negative grams, reps 0–1000 for editing and >0 for completion, RIR null or 0–100 tenths. Operations targeting a missing set, wrong workout or finished workout are rejected. A completion timestamp must be valid for the active workout and is never regenerated on replay.

## 6. Idempotency, ordering and conflict rules

There is deliberately no arbitrary last-write-wins clock rule.

For each operation, the phone executes a Room transaction:

1. Load current active workout/set.
2. If every changed field already equals the operation's desired value, return `APPLIED`/idempotent success without duplicating anything.
3. Otherwise, if every changed field still equals the operation's expected value, apply only those fields atomically and return `APPLIED`.
4. Otherwise return `CONFLICT` and do not overwrite canonical phone state.

Only fields explicitly changed by the watch participate in conflict detection. Therefore a phone edit to reps does not prevent a watch load-only operation, and a watch completion does not overwrite concurrent phone load/reps/RIR edits.

The phone processes one watch journal in ascending sequence order. Duplicate journal delivery is safe because operation IDs/results and expected/desired comparison make the operation idempotent. A stale/out-of-order journal cannot roll canonical values backward through blind writes.

Terminal result examples:

- `APPLIED` — canonical state now reflects the operation (including a duplicate replay that was already applied);
- `CONFLICT` — same field changed elsewhere; canonical phone state wins and the watch surfaces that it refreshed instead of silently overwriting;
- `REJECTED` — invalid/stale target such as finished/no active workout.

The watch retains pending operations until a terminal result is received. After terminal results, it removes those operations and uses the newly published canonical snapshot as its base.

## 7. Completion and rest timer

The existing phone contract remains canonical:

- `WorkoutSet.completedAt` is completion truth.
- A successful completion starts rest by writing `Workout.restTimerEndsAt = completedAt + restSeconds * 1000` and the owning workout-exercise ID.
- Remaining time is derived as `endsAt - now`; there is no second ticking timer database.

When a watch completes a set offline, its durable completion operation contains the original `completedAt`. The watch can immediately derive a provisional rest end from that timestamp plus the already-snapshotted rest duration. On reconnection the phone replays that exact completion timestamp through the canonical transaction and publishes the canonical absolute rest end.

A duplicate completion cannot create a second set or restart rest with a new time because replay never invents a new completion timestamp.

Stopping/editing the timer is not added to PR10 because the real issue only requires the rest timer to be usable/visible at the wrist and the phone already owns timer state. The watch displays the timer and completion-derived offline timer without introducing a second timer truth.

## 8. Connectivity and lifecycle behavior

### Connected

Watch receives/pulls snapshot, edits locally, persists operation to its outbox first, publishes journal, phone applies transaction, and phone republishes snapshot/result. UI can optimistically show the operation while clearly tracking pending sync.

### Watch loses connection before/during a set

The last active-workout snapshot remains on device. A mutation is appended to local durable outbox before network delivery is attempted. The workout interaction continues without the phone.

### Reconnection

The watch republishes the complete pending journal. Phone processes in sequence and publishes results + canonical snapshot. The watch clears only acknowledged terminal operations.

### Duplicate/out-of-order delivery

Handled by stable operation IDs, one ordered pending journal, field-level expected/desired compare, and transactional phone application.

### Phone process death/restart

`WearableListenerService` can receive Data Layer changes without the UI process already running. Canonical truth is loaded from Room. No in-memory ack ledger is required for correctness.

### Watch process death/restart

Pending journal is reloaded from local DataStore; DataClient's last snapshot is queried; journal/request are republished as needed. No set action is lost merely because the Activity/ViewModel died.

### Phone edit while watch is stale

A different-field edit is preserved and compatible. A same-field edit causes deterministic `CONFLICT`; the phone's canonical value is shown after refresh. No silent last-write-wins overwrite occurs.

### No active workout

The phone publishes an explicit no-active state. The watch shows a simple prompt to start/continue a workout on the phone. It does not expose the routine editor.

### Phone temporarily unavailable

If a cached active workout exists, watch logging remains available and pending operations are visibly marked as saved locally/pending sync. If no cached active workout exists, the watch cannot invent one and shows that the phone is temporarily required to load/start a workout.

## 9. Phone transaction boundary

PR10 adds transactional repository/DAO support for watch operations but no entity/table/column. The operation handler is an adapter into the existing `WorkoutRepository`/Room truth.

The phone never trusts the watch snapshot as truth. IDs are used only to address an already-existing active workout/set. Historical workouts are not mutable through Wear.

The PR4 logger remains fully functional with the Wear module uninstalled and without a connected watch.

## 10. Wrist-first UI

The Wear UI is intentionally one task-focused surface:

- exercise name and set number;
- compact PREVIOUS / TARGET / TODAY context;
- large controls to adjust load and reps without typing;
- optional RIR controls including clear/not-set;
- one prominent Complete set action;
- rest timer after completion;
- concise connected / saved-offline / syncing / conflict state only when useful;
- after all current sets are complete, direct the user to finish/manage the workout on the phone.

No charts, full History, full Progress, routine editor, exercise library, backup, recovery UI or large settings surface.

Wear-specific Compose Material 3 components are used rather than shrinking phone Material widgets. Layout is designed for round/small watch screens with readable numbers, semantics/content descriptions and large touch targets.

## 11. Packaging

A dedicated Wear application module is packaged with the same application ID as the phone app, a unique version code, and required `android.hardware.type.watch` feature. It is marked **non-standalone** because the phone/Room app is the source of truth and starting/managing the workout remains a phone responsibility. The Wear app still works through connectivity interruptions once it has an active-workout snapshot.

The phone advertises a GymTracker workout-sync capability; the watch uses capability reachability for connection UX and never assumes that any arbitrary Android node is the canonical companion.

## 12. Test contract

All fixtures are synthetic and non-identifying.

Protocol/JVM tests cover:

- serialization/version/path contract;
- deterministic ordered journal processing;
- expected/desired idempotency;
- duplicate replay;
- stale same-field conflict;
- independent-field merge;
- out-of-order/stale journal behavior;
- completion replay and unchanged timestamp;
- no duplicate set creation.

Phone/Room instrumentation covers:

- active/no-active workout snapshot;
- PREVIOUS/TARGET mapping;
- edit load/reps/RIR;
- complete set;
- rest timer canonical end;
- duplicate operation;
- conflict/rejection;
- persistence across repository/process recreation;
- existing mobile behavior without Wear.

Wear tests cover:

- no active workout;
- current exercise/set and PREVIOUS/TARGET/TODAY;
- load/reps/RIR adjustments;
- complete set and rest timer;
- locally queued offline operation;
- pending journal restoration/replay;
- conflict/sync status;
- small watch layout and accessibility semantics where emulator support permits.

No CI test needs a physical watch, real phone/watch pairing or personal workout data.

## 13. CI contract

All existing hardened mobile gates remain mandatory and unchanged in meaning:

1. mobile JVM tests;
2. semantic Room schema verification;
3. Room schema artifact;
4. mobile API 35 connected tests;
5. mobile lint;
6. mobile debug APK;
7. real minified/resource-shrunk mobile release APK;
8. mobile debug artifact;
9. mobile release artifact.

PR10 adds narrow Wear gates instead of replacing mobile gates:

- shared protocol JVM tests;
- Wear JVM tests;
- Wear lint/build;
- Wear API 35 emulator instrumentation where stable in hosted CI;
- Wear debug/release APK build and Wear artifacts.

The Room schema snapshot check must continue to prove that v1/v2 are semantically unchanged.

## 14. Explicit non-goals / safety audit checklist

PR10 must not:

- touch Pulso / pulso-finanzas;
- add cloud/backend/account requirements;
- create schema v3 without a demonstrated need (none exists in this design);
- persist a second canonical workout/history database on the watch;
- alter PR4 PREVIOUS/TARGET/TODAY, set types, finish semantics or canonical rest behavior;
- alter PR5 PR/Epley/heaviest/reps-at-load/volume rules;
- alter PR6 analytics/progression semantics;
- alter PR7 backup/restore/CSV format;
- alter PR9 Health Connect permissions or data boundary;
- commit real workout/health data, device logs, credentials, secrets or identifying fixtures;
- add health/workout analytics telemetry.
