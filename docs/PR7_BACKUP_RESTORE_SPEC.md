# PR7 — Backup, Restore, and CSV Export Design

Status: implementation design for Issue #7.

## Product constraints

PR7 adds durable user ownership of local data without changing GymTracker's canonical model.

- `WorkoutSet` remains canonical truth.
- Room database remains schema v2.
- PR/e1RM/volume/progress analytics remain derived and recalculable.
- Historical `Routine` / `RoutineExercise` nullable references remain nullable.
- Archived exercises and duplicate exercise occurrences must survive round trips.
- Active workouts are included because they are canonical local state and the logger already supports recovery.
- No cloud account, backend, Health Connect, Wear OS, AI, progression prescription, or third-party import is added.

## Official Android guidance reviewed

- Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Activity Result contracts: https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts
- Auto Backup overview: https://developer.android.com/identity/data/backup
- Android 12 backup/data-extraction rules: https://developer.android.com/about/versions/12/behavior-changes-12
- Room 3.0 transactions: https://developer.android.com/reference/kotlin/androidx/room3/RoomDatabase
- Room DAO access/transactions: https://developer.android.com/training/data-storage/room/accessing-data

Decisions from that guidance:

1. Manual export/import uses the system document picker (`CreateDocument` / `OpenDocument`) and `ContentResolver` streams. No broad storage permission is needed.
2. Manual files are portable application documents, not copies of the SQLite file.
3. Room 3.0.1 `withReadTransaction` provides a coherent snapshot and `withWriteTransaction` provides an IMMEDIATE atomic replacement transaction.
4. Android Auto Backup remains an additional convenience only. It is system-controlled, file-based, normally capped at 25 MB per app, and is not a substitute for the manual portable format.

## Portable format V1

MIME type: `application/json`.
Suggested filename: `gymtracker-backup-YYYY-MM-DD.json`.
Encoding: UTF-8.
Compression: none in V1.

Envelope:

```text
{
  "format": "gymtracker-backup",
  "formatVersion": 1,
  "generatedAtEpochMillis": <long>,
  "appVersion": <string>,
  "databaseSchemaVersion": 2,
  "payloadSha256": <lowercase hex SHA-256>,
  "payload": {
    "exercises": [...],
    "muscles": [...],
    "exerciseMuscles": [...],
    "routines": [...],
    "routineExercises": [...],
    "workouts": [...],
    "workoutExercises": [...],
    "workoutSets": [...]
  }
}
```

The payload mirrors domain meaning, not SQLite files or Room schema JSON. Field names are explicitly versioned portable contract fields. Every canonical scalar required to reconstruct the eight current Room tables is represented, including IDs, exact grams, RIR tenths, timestamps, archive state, target snapshots, notes, nullable routine references, and set completion/type.

### Determinism

For a given Room snapshot, payload arrays are sorted by stable primary-key/relationship order and object keys are emitted in a fixed order. Compact JSON is used for the canonical payload bytes.

The overall document is intentionally not byte-identical between exports because `generatedAtEpochMillis` changes. The payload itself is deterministic.

### Integrity hash

`payloadSha256` is SHA-256 over the exact UTF-8 bytes of the compact canonical JSON representation of the `payload` object produced by the V1 encoder.

This hash detects accidental corruption/truncation or modification of the payload. It is **not authentication**, does not prove who created the file, and does not protect against an attacker who can modify the payload and recompute the hash.

## Defensive parsing limits

The portable file is untrusted input. V1 materializes the JSON document in memory for a simpler, auditable implementation. Defensive limits protect the app from pathological input while remaining far above expected personal-use histories:

- maximum document bytes: 128 MiB;
- maximum exercises: 100,000;
- maximum muscles: 10,000;
- maximum exercise-muscle links: 500,000;
- maximum routines: 100,000;
- maximum routine exercises: 500,000;
- maximum workouts: 250,000;
- maximum workout exercises: 1,000,000;
- maximum workout sets: 5,000,000;
- maximum ID length: 256 characters;
- maximum name/title length: 4,096 characters;
- maximum notes length: 65,536 characters.

These are import-safety limits, not retention limits. Room retains unlimited history; no query or export truncation is introduced. If real-world histories approach these limits, a future format can move to bounded streaming rather than silently dropping history.

## Pure validation before restore

Validation happens after parse/hash verification and before any write transaction.

The validator rejects at least:

- wrong format identifier;
- unknown/future `formatVersion`;
- future database schema metadata that V1 cannot safely interpret;
- malformed JSON / non-object roots;
- missing required fields or wrong primitive types;
- checksum mismatch;
- blank/oversized IDs;
- duplicate primary IDs within a table;
- duplicate composite exercise-muscle links;
- broken required references;
- non-null optional references that point nowhere;
- unknown muscle roles;
- unknown set types;
- unknown previous-reference modes;
- negative positions or duplicate positions where Room has a unique parent-position index;
- impossible target ranges (`repMin > repMax`);
- reps/RIR/rest/set-count values outside application-safe ranges;
- negative loads/load increments;
- negative timestamps;
- workout finish before start;
- completed-set timestamps before workout start;
- rest-timer owner not belonging to the same workout;
- more than one active workout, preserving the logger's single-active-workout invariant;
- counts or strings beyond the defensive limits.

Validation intentionally does not treat analytics, PRs, charts, or CSV rows as canonical input.

## Restore policy V1

V1 supports one restore mode: **replace the entire local canonical dataset**.

Merge is explicitly out of scope because GymTracker currently has no deterministic product policy for equivalent-but-different IDs, overlapping historical sessions, conflicting positions, or partially duplicated aggregates.

Flow:

`SELECT FILE → READ/PARSE → HASH CHECK → PURE VALIDATE → PREVIEW → EXPLICIT REPLACE CONFIRMATION → ATOMIC RESTORE → IN-TRANSACTION VERIFY → SUCCESS`

Before confirmation the UI shows:

- format version;
- generated timestamp;
- app version that generated the file;
- source database schema version;
- exercises count;
- routines count;
- workouts count;
- sets count;
- earliest/latest workout start when present;
- whether an active workout is present.

No existing data is changed during preview.

### Atomic replacement

A dedicated backup DAO exposes deterministic full-table reads, reverse-FK-order deletes, and parent-before-child inserts. `BackupRepository.replaceAll()` executes all deletes/inserts inside Room 3.0.1 `withWriteTransaction`.

Order:

1. delete `workout_set`;
2. delete `workout_exercise`;
3. delete `workout`;
4. delete `routine_exercise`;
5. delete `routine`;
6. delete `exercise_muscle`;
7. delete `muscle`;
8. delete `exercise`;
9. insert exercises and muscles;
10. insert exercise-muscle links;
11. insert routines and routine exercises;
12. insert workouts, workout exercises, and workout sets;
13. re-read canonical snapshot inside the same transaction and compare it to the expected snapshot.

Any exception or verification mismatch aborts the transaction, leaving the old dataset intact. Foreign keys are never disabled.

## CSV export

CSV is a human/analysis export only and is never accepted for restore.

- UTF-8;
- RFC-4180-style quoting and CRLF row separators;
- stable English machine-oriented headers;
- timestamps include exact epoch milliseconds and UTC ISO-8601 text;
- exact `load_grams` is preserved and a readable `load_kg` is derived;
- exact `rir_tenths` is preserved and readable `rir` is derived;
- nullable fields remain empty;
- strings containing commas, quotes, CR/LF, or Unicode are preserved with correct CSV escaping.

One row represents one `WorkoutSet` and includes workout context, workout-exercise target snapshot/context, current exercise name (explicitly named as current because exercise names are not snapshotted historically), and set truth.

Planned stable columns:

```text
workout_id
workout_started_at_epoch_ms
workout_started_at_utc
workout_finished_at_epoch_ms
workout_finished_at_utc
workout_title_snapshot
routine_id
workout_notes
workout_exercise_id
routine_exercise_id
exercise_id
exercise_name_current
exercise_position
exercise_notes
snapshot_target_set_count
snapshot_rep_min
snapshot_rep_max
snapshot_target_rir_tenths
snapshot_target_rir
snapshot_rest_seconds
snapshot_load_increment_grams
snapshot_previous_reference_mode
set_id
set_position
set_type
set_completed
set_completed_at_epoch_ms
set_completed_at_utc
load_grams
load_kg
reps
rir_tenths
rir
```

## SAF / Android file boundary

Compose owns Activity Result launchers only. Parsing, Room access, and stream handling are outside Composables.

- Backup export: `ActivityResultContracts.CreateDocument("application/json")`.
- CSV export: `ActivityResultContracts.CreateDocument("text/csv")`.
- Backup import: `ActivityResultContracts.OpenDocument()` filtered to JSON/document-compatible MIME types.
- `ContentResolver.openInputStream()` / `openOutputStream()` are used directly; no filesystem path is assumed.
- Picker cancellation is a no-op.
- null streams, `IOException`, `SecurityException`, oversized/empty input, and partial read/write failures become explicit safe UI errors.

## UI/state contract

Data management is reachable from the existing History top app bar without adding a fourth bottom-navigation destination.

The state holder/ViewModel owns:

- idle/exporting/reading/restoring state;
- pending validated import snapshot (memory only);
- preview metadata;
- explicit replace-confirmation dialog state;
- success/error messages;
- mutex protection against duplicate import/restore actions.

The pending decoded dataset is never placed in `SavedStateHandle`/Bundle. A ViewModel survives ordinary configuration recreation; process death requires re-selecting the backup rather than serializing megabytes into saved state.

## Auto Backup policy

GymTracker will configure explicit backup rules instead of relying on defaults.

- Include the Room database domain for cloud backup and device transfer.
- No secrets currently exist in GymTracker.
- User-selected export/import URIs are not persisted as backup state.
- Manual portable backup remains the documented ownership/recovery path.
- Auto Backup is best-effort convenience: system-controlled cadence/transport, Google account/device settings, restore timing, device-manufacturer behavior, and the approximately 25 MB app quota are outside GymTracker's control.

Because `minSdk=28`, both legacy `fullBackupContent` rules (Android 11 and lower) and `dataExtractionRules` (Android 12+) are supplied.

## Test plan

### JVM / pure

- canonical encode/decode round trip;
- deterministic payload/hash;
- all eight entity types and nullable relations;
- exact grams/RIR/timestamps;
- active + completed workouts;
- archived exercise;
- null historical routine/routine-exercise references;
- duplicate exercise occurrences;
- unknown/future format version;
- malformed/missing fields;
- duplicate IDs/composite links;
- broken references;
- invalid enum/ranges/timestamps;
- checksum mismatch;
- CSV commas/quotes/newlines/Unicode;
- timezone-independent machine columns;
- large multi-year synthetic dataset sanity.

### Room / instrumented

- DB → snapshot → encoded backup → fresh DB restore → exact snapshot equality;
- complex synthetic history;
- archived exercise;
- deleted/null routine references;
- duplicate exercise occurrences;
- active + completed workouts;
- restore atomicity on injected failure;
- invalid import never reaches mutation;
- foreign-key-valid restored data;
- v1→v2 migration suite remains green.

### UI/state / instrumented or state-level

- backup/CSV actions are wired to the correct document contracts;
- import selection produces preview;
- preview counts are correct;
- cancellation does nothing;
- explicit confirmation is required;
- corrupt input produces safe error;
- success is explicit;
- double restore is blocked;
- destructive confirmation has accessible semantics;
- ordinary configuration recreation keeps ViewModel state.

## Performance rationale

V1 takes one coherent in-memory snapshot and uses compact JSON. This keeps the implementation auditable and makes pure validation possible before any destructive action. It does not add any history retention limit or database truncation.

Synthetic multi-year tests will exercise thousands of workouts/sets. If measured/observed V1 memory pressure becomes unacceptable, V2 should introduce a streaming portable format with bounded validation staging rather than weaken integrity checks or limit stored history.
