# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), after PR7 / Issue #7 merged and the post-merge `main` CI passed.

## 0. Repository, safety, and permanent direction

- Repository: `German-Choconta/tareas`.
- Repository is public.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, identifying names/fixtures, credentials, tokens, secrets, private exports, or personal backups. Tests/examples must be synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Android-first, offline/local-first. Accounts/cloud are outside current V1 scope.
- `WorkoutSet` remains canonical truth. PRs, e1RM, volume, trends, charts, CSV and analytics are derived/recalculable/read-only representations.
- Work autonomously in GitHub, but do not advance stages silently. Do not start the next PR until the user explicitly continues.

## 1. Completed stages through PR7

### PR #1 — Android foundation
- MERGED.

### PR #11 / Issue #2 — Room local data foundation
- MERGED / COMPLETED.
- Squash merge: `aceaadbcb4a3ea370439556780b3b674e0505350`.
- Final PR2 Android CI: `32801537677` — SUCCESS.

### PR #12 / Issue #3 — Exercises and Routine Editor
- MERGED / COMPLETED.
- Squash merge: `4915ee7ef0dda4a1bc4b01076bec3ddbba0d5e33`.
- Issue #3 closed/completed.
- Post-merge main CI: `32805215489` — SUCCESS.

### PR #13 / Issue #4 — Workout Logger
- MERGED / COMPLETED.
- Final PR head: `1e68e65a3bec4392583c942da738989384fb451b`.
- Final PR CI: `32810018092` — SUCCESS.
- Squash merge: `12ced7e9f7838d004e2ac4cac17a23f5fa2a8529`.
- Issue #4 closed/completed.
- Post-merge main CI: `32810354122` — SUCCESS.
- Documentation-only main commit: `6bb28c9bb7d0bb1b530a21e573089be34e1efff7`.
- Documentation CI: `32810766155` — SUCCESS.

### PR #14 / Issue #5 — Unlimited History and PR Engine
- MERGED / COMPLETED.
- Final PR head: `a0381dac73709108a4935dd63379c39cd5958503`.
- Final PR Android CI: `32855092000` (#96) — SUCCESS.
- Final PR artifacts: `gymtracker-debug-apk` `9566003937`; `gymtracker-room-schema` `9565887051`.
- Squash merge: `406b627a8c339523cd5ec121e9aeca9a973cc4ee`.
- Issue #5 closed/completed.
- Post-merge main CI: `32855708244` (#97) — SUCCESS.
- Post-merge artifacts: `gymtracker-debug-apk` `9566270897`; `gymtracker-room-schema` `9566130291`.

### PR #15 / Issue #6 — Progress Analytics
- MERGED / COMPLETED.
- Final PR head: `4fa2a1abc01f5dde8c0c0f3e26437fb3a3d348d5`.
- Final PR Android CI: `32868139154` (#129) — SUCCESS.
- Final PR artifacts: `gymtracker-debug-apk` `9571147279`; `gymtracker-room-schema` `9571010747`.
- Squash merge: `f4076f6d9d7fd09dc735a758c85f60a9a924a93d`.
- Issue #6 closed/completed.
- Post-merge main Android CI: `32870321302` (#130) — SUCCESS.
- Post-merge artifacts: `gymtracker-debug-apk` `9571975063`; `gymtracker-room-schema` `9571833494`.
- Final PR6 documentation commit on `main`: `1905412984ad4b8c04b9937e47844f8b149c4b9f`.
- Documentation-head CI: `32871077072` (#131) — SUCCESS.

### PR #16 / Issue #7 — Backup, Restore and CSV Export
- MERGED / COMPLETED.
- Branch: `feat/backup-restore-csv`, based on PR6 documentation head `1905412984ad4b8c04b9937e47844f8b149c4b9f`.
- Last green implementation head before final handoff: `2085fb359ced6a1141eacb785a26aee8e44004a5`.
- Implementation CI: `32877992647` (#149) — SUCCESS.
- Implementation artifacts: `gymtracker-debug-apk` `9574836962`; `gymtracker-room-schema` `9574707561`.
- Final PR head: `b35f8008a3bb787501095a15730de835621f45c1`.
- Final PR Android CI: `32878707782` (#150) — SUCCESS.
- Final PR artifacts: `gymtracker-debug-apk` `9575115156`; `gymtracker-room-schema` `9574981591`.
- Final audit: 24 GymTracker/documentation files; no Pulso changes; Room schema v2 preserved; no submitted reviews; no inline review threads; mergeable before merge; no broad storage permission added.
- Squash merge: `3b949d0127875c115d15a49ad04ae3423836374a`.
- Issue #7 closed/completed.
- Post-merge `main` Android CI: `32879392178` (#151) — SUCCESS.
- Post-merge artifacts: `gymtracker-debug-apk` `9575355388`; `gymtracker-room-schema` `9575229164`.
- This merged-state handoff is documentation-only. Verify the CI for the exact documentation head before treating the final closure record as fully known-good.

## 2. Android/toolchain baseline

- Native Android / Kotlin / Jetpack Compose / Material 3.
- Package: `com.germanchoconta.gymtracker`.
- Android Gradle Plugin `9.3.0`; Kotlin Compose plugin `2.3.21`; Gradle CI `9.5.0`; JDK `17`.
- compileSdk `37`; targetSdk `36`; minSdk `28`.
- Compose BOM `2026.08.00`; Activity Compose `1.13.0`; Lifecycle `2.11.0`.
- Room `3.0.1`; KSP `2.3.10`; bundled SQLite `2.7.0`.
- Paging `3.5.1`; Room Paging `3.0.1`; Vico `3.2.3`.
- `kotlinx-serialization-json/core` `1.8.1`.

Architecture rules:
- unidirectional data flow and immutable UI state;
- ViewModels/state holders own screen state;
- Composables never query Room directly or parse portable files;
- repositories/DAOs are persistence boundaries;
- Room owns canonical active/completed workout state;
- SavedState/rememberSaveable are UI/navigation hints, never canonical workout facts;
- DB is an application/process singleton;
- chart/export/backup encodings never become canonical metric truth.

## 3. Room schema and historical integrity

- Current DB version: **2**. PR5, PR6 and PR7 intentionally do **not** create schema v3.
- Schemas v1/v2 remain committed; `MIGRATION_1_2` remains registered and tested.
- v1 identity hash: `4419e2711112b42bfbfa3083e3499613`.
- v2 identity hash: `251aab4f3ed2b0175df34e37323e31cb`.

Canonical entities:
1. `ExerciseEntity`
2. `MuscleEntity`
3. `ExerciseMuscleEntity`
4. `RoutineEntity`
5. `RoutineExerciseEntity`
6. `WorkoutEntity`
7. `WorkoutExerciseEntity`
8. `WorkoutSetEntity`

Numeric invariants:
- stable `String` IDs;
- load truth is `Long` grams;
- RIR truth is nullable `Int` tenths;
- timestamps are `Long` epoch milliseconds;
- `Double` is never canonical metric truth.

Historical integrity:
- Exercise → workout history uses `RESTRICT`; archive is normal flow.
- Routine → Workout uses `SET_NULL`.
- RoutineExercise → WorkoutExercise uses `SET_NULL`.
- Workout aggregate children cascade only inside the workout aggregate.
- routine edits/removals never rewrite already-started/completed history.
- archived exercises, null historical routine references and duplicate exercise occurrences remain representable.

## 4. Logger, History, PR and Progress invariants

PR4 logger preserves transactional Routine → Workout start, immediate Room autosave, TARGET snapshots in `WorkoutExerciseEntity`, deterministic PREVIOUS modes (`ANY_WORKOUT`, `SAME_ROUTINE`), set types `WARMUP`, `WORK`, `DROP`, `FAILURE`, rest timer, notes, active-workout recovery and finish behavior.

PR5 History preserves unlimited DB history. Paging is presentation/query only and never truncates stored history. Raw deterministic order remains:
`workout.startedAt DESC → workout.id DESC → workoutExercise.position ASC → workoutExercise.id ASC → set.position ASC → set.id ASC`.

PR/analytics eligibility remains: finished workout + completed set + reps > 0 + load > 0 + type in `WORK/DROP/FAILURE`. `WARMUP` remains visible in raw history but excluded from PR/e1RM/volume analytics.

PR chronology remains:
`startedAt ASC → workoutId ASC → workoutExercise.position ASC → workoutExerciseId ASC → set.position ASC → setId ASC`.

Tie policy remains strict improvement only. Current-best witness is the first deterministic witness of the ultimate value.

Definitions retained:
- heaviest load = exact integer grams;
- reps-at-load = exact load, no bucketing;
- e1RM = Epley, reps 2–10 only, RIR excluded, exact rational/`BigInteger` comparison, display rounded to 0.1 kg;
- exercise-session volume = overflow-safe `BigInteger` sum across all occurrences in the workout;
- volume is descriptive, not a universal quality score.

PR6 Progress keeps analytics derived/recalculable. It offers Load, Reps at exact load, e1RM, Volume and Frequency; custom dates are inclusive in UI and represented internally as `[startOfDay(start), startOfDay(end + 1))`; missing performance dates are not synthetic zeroes; finite frequency buckets may contain explicit zeroes. Vico and `Double` remain renderer-only. All-time fact loading is shared transiently between PR5 and PR6 calculations, never persisted as truth.

## 5. PR7 / Issue #7 — Backup, Restore and CSV Export — COMPLETED

Design spec: `docs/PR7_BACKUP_RESTORE_SPEC.md`.

Manual backup pipeline:
`Room v2 canonical truth → coherent Room read transaction → BackupSnapshot → V1 portable JSON encoder → SAF output stream`

Manual restore pipeline:
`SAF input stream → bounded UTF-8 read → strict JSON decode → SHA-256 integrity check → pure validation → preview → explicit replace confirmation → single Room write transaction → in-transaction equality verification`

CSV pipeline:
`Room snapshot → human/analysis CSV exporter → SAF output stream`

### Portable format V1

- identifier `gymtracker-backup`;
- independent `formatVersion = 1`;
- generated epoch timestamp, app version and source Room schema metadata;
- payload covers all eight canonical tables;
- exact IDs, grams, RIR tenths, timestamps, archive state, logger target snapshots and nullable historical references;
- deterministic canonical payload ordering;
- SHA-256 over exact canonical compact payload UTF-8 bytes.

The checksum detects accidental corruption/modification only. It is **not authentication** and does not prove who generated the backup.

V1 is uncompressed JSON. The import path materializes a bounded document in memory to keep full validation simple and auditable. Maximum document size is 128 MiB. Entity/string limits are defensive import limits, not history-retention limits; no stored history is silently truncated.

### Restore semantics V1

Only **replace entire local dataset** is implemented. Merge is intentionally out of scope because there is no deterministic conflict policy for equivalent entities, overlapping histories, IDs, positions or partially duplicated workout aggregates.

No destructive action occurs before preview + separate explicit confirmation. Replacement runs in one Room `withWriteTransaction`; deletes run child→parent, inserts parent→child, foreign keys remain enabled, and the restored snapshot is reread and compared with the validated expected snapshot inside that transaction. Any exception or mismatch rolls back to the old dataset.

The validator rejects malformed/unknown/future formats, future unsupported DB schema metadata, checksum mismatch, required-field/type errors, oversized input, blank/oversized IDs, duplicate primary/composite keys, broken required and non-null optional references, invalid positions, malformed enums, impossible ranges/timestamps, invalid rest-timer ownership and more than one active workout.

### CSV semantics

CSV is export-only and is never accepted as restore input. It is UTF-8 with CRLF row separators and RFC-4180-style escaping. Stable English machine headers include workout/session context, routine references, workout-exercise snapshot targets, set identity/position/type/completion, exact epoch timestamps + UTC text, exact grams + readable kg, exact RIR tenths + readable RIR, notes, and `exercise_name_current` explicitly marked current because exercise names are not historically snapshotted.

### Storage Access Framework and Auto Backup

- backup export: `ActivityResultContracts.CreateDocument(application/json)`;
- CSV export: `CreateDocument(text/csv)`;
- restore selection: `OpenDocument`;
- `ContentResolver` streams only; no filesystem path assumptions;
- picker cancellation is an explicit no-op with dedicated coverage;
- no broad/legacy external-storage permission was added.

Auto Backup is additional best-effort convenience only. Legacy `fullBackupContent` and Android 12+ `dataExtractionRules` explicitly include the database domain for cloud backup/device transfer. Manual portable backup remains the durable user-owned recovery mechanism. Auto Backup's platform quota/availability means it is never treated as the sole recovery path.

### UI/state

Data management is reachable from History without adding a fourth bottom-navigation destination. Import preview shows format/app/schema metadata, exercise/routine/workout/set counts, workout date range and whether an active workout is included. Destructive replace requires a separate accessible confirmation. A validated decoded dataset stays in ViewModel memory only; ordinary configuration recreation retains the ViewModel, while process death requires file re-selection.

After leaving data management the logger re-queries Room for an active workout, so a restored active session can recover through the existing logger path.

### PR7 synthetic test coverage

JVM/pure coverage includes:
- encode/decode canonical round trip and deterministic payload/checksum;
- all eight tables and nullable relations;
- exact grams/RIR/timestamps;
- active + completed workouts, archived exercise, null historical routine refs, duplicate exercise occurrences;
- unknown/future format, future DB schema, malformed/missing fields, integer overflow, checksum mismatch;
- duplicate IDs, broken references, invalid enum/range/position/timestamp cases;
- CSV commas/quotes/newlines/Unicode and machine-time representations;
- multi-year synthetic history with thousands of sessions, without truncation.

Instrumented/state/UI coverage includes:
- DB → portable backup → fresh DB restore → exact equality;
- rollback on deliberately invalid FK restore;
- corrupt portable input leaves original DB unchanged;
- archived/null-history/duplicate-occurrence/active-workout cases;
- preview before mutation and explicit confirmation required;
- double-confirm protection and safe corrupt/IO error state;
- SAF actions/MIME types and picker cancellation no-op;
- accessible destructive confirmation;
- API 35 connected suite contains 45 tests and passed 45/45 on the green PR7 CI path.

All fixtures are synthetic and non-identifying.

### PR7 issues corrected during implementation/finalization

1. Removed an invalid top-level Compose test `assertExists` import; the valid member assertion remains on `SemanticsNodeInteraction`.
2. Moved action mutex acquisition to synchronous `tryLock()` before coroutine launch so rapid taps cannot enqueue competing operations.
3. Centralized picker URI dispatch and added dedicated cancellation no-op coverage.
4. Split a flaky synthetic recomposition/dialog assertion into deterministic UI-state tests; `BackupViewModelTest` independently proves preview → confirmation → single restore.
5. On the API 35 320×640 CI viewport, the destructive preview action required `performScrollTo().performClick()` plus Compose synchronization; the final connected suite passed.

### Known deliberate limitations

- Portable V1 restore is replace-only; merge/conflict resolution is not implemented.
- SHA-256 is integrity/corruption detection, not authenticity.
- Import is materialized in memory but bounded to 128 MiB; no artificial history retention limit is introduced.
- Auto Backup is OS-managed convenience and subject to platform/user/quota behavior.
- Process death during an import preview requires selecting the backup file again.
- A provider-side failure while writing an external SAF document may leave a partial external file; canonical Room data is unaffected.
- CSV can only expose the current exercise name because no historical exercise-name snapshot exists; this does not alter canonical workout facts.
- No ZIP/compression, cloud sync, accounts or third-party importer is part of PR7.

## 6. CI contract

Android CI runs on PRs and pushes to `main`:
1. JDK/Android/Gradle setup;
2. snapshot committed Room schemas;
3. JVM tests;
4. semantic Room schema verification;
5. upload `gymtracker-room-schema`;
6. enable KVM;
7. `connectedDebugAndroidTest` on emulator API 35;
8. lint;
9. assemble debug APK;
10. upload `gymtracker-debug-apk`.

Closure rule for completed stages:
- exact final PR head must pass every step and expose both artifacts;
- review threads, scope, privacy, schema, atomicity, UX, performance and mergeability are audited before ready;
- merge uses squash with `expected_head_sha`;
- linked issue closure and post-merge `main` CI/artifacts are verified;
- the final merged-state handoff is committed to `main`, and that exact documentation head CI must pass.

## 7. Roadmap

- PR #1 — Android foundation — MERGED.
- PR #2 / Issue #2 — Room local data foundation — MERGED / COMPLETED.
- PR #3 / Issue #3 — Exercises and Routine Editor — MERGED / COMPLETED.
- PR #4 / Issue #4 — Workout Logger — MERGED / COMPLETED.
- PR #5 / Issue #5 — Unlimited History and PR Engine — MERGED / COMPLETED.
- PR #6 / Issue #6 — Progress Analytics — MERGED / COMPLETED.
- PR #7 / Issue #7 — Backup, Restore and CSV Export — **MERGED / COMPLETED**.
- PR #8 / Issue #8 — V1 UX / reliability hardening — **NEXT STAGE; DO NOT START UNTIL THE USER EXPLICITLY CONTINUES**.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 8. Next action from this handoff

1. Verify the Android CI for the exact documentation-only `main` head created by this handoff update.
2. Do not treat the final PR7 closure record as fully known-good until that CI passes the full contract and exposes both artifacts.
3. **Do not start PR8 silently.**
4. After PR7 closure verification is complete, read Issue #8 and provide the user the full standalone PR8 continuation prompt.
5. Only when the user explicitly continues PR8: re-read this file, verify GitHub source of truth, inspect Issue #8, then branch and implement from the verified current `main` head.