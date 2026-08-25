# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), after PR8 / Issue #8 merge and successful post-merge validation.

## 0. Repository, safety, and permanent product direction

- Repository: `German-Choconta/tareas`.
- Repository is public.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, identifying names/fixtures, credentials, tokens, secrets, private exports, personal backups, or other personal information. Tests/examples/benchmarks/screenshots must be synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Android-first and offline/local-first.
- `WorkoutSet` remains canonical truth.
- PRs, e1RM, volume, trends, charts, CSV and analytics remain derived/recalculable/read-only representations.
- Room remains canonical for active and completed workouts.
- Paging is presentation/query only and never a history-retention limit.
- Do not advance roadmap stages silently. Read the next real GitHub issue before designing it and wait for explicit user continuation before beginning implementation.

## 1. Completed roadmap through V1

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

### PR #14 / Issue #5 — Unlimited History and PR Engine
- MERGED / COMPLETED.
- Final PR head: `a0381dac73709108a4935dd63379c39cd5958503`.
- Final PR Android CI: `32855092000` (#96) — SUCCESS.
- Squash merge: `406b627a8c339523cd5ec121e9aeca9a973cc4ee`.
- Issue #5 closed/completed.

### PR #15 / Issue #6 — Progress Analytics
- MERGED / COMPLETED.
- Final PR head: `4fa2a1abc01f5dde8c0c0f3e26437fb3a3d348d5`.
- Final PR Android CI: `32868139154` (#129) — SUCCESS.
- Squash merge: `f4076f6d9d7fd09dc735a758c85f60a9a924a93d`.
- Issue #6 closed/completed.
- Final PR6 documentation commit: `1905412984ad4b8c04b9937e47844f8b149c4b9f`.
- Documentation CI: `32871077072` (#131) — SUCCESS.

### PR #16 / Issue #7 — Backup, Restore and CSV Export
- MERGED / COMPLETED.
- Final PR head: `b35f8008a3bb787501095a15730de835621f45c1`.
- Final PR Android CI: `32878707782` (#150) — SUCCESS.
- Final PR artifacts: debug APK `9575115156`; Room schema `9574981591`.
- Squash merge: `3b949d0127875c115d15a49ad04ae3423836374a`.
- Issue #7 closed/completed.
- Post-merge `main` CI: `32879392178` (#151) — SUCCESS.
- Post-merge artifacts: debug APK `9575355388`; Room schema `9575229164`.
- Final PR7 merged-state documentation commit: `f0a5ccb4427ec133b3e7c08806eec54c2e481d37`.
- Documentation-head Android CI: `32880182711` (#152) — SUCCESS.
- Documentation-head artifacts: debug APK `9575588354`; Room schema `9575490321`.

### PR #17 / Issue #8 — V1 UX and Reliability Hardening
- **MERGED / COMPLETED. GymTracker core V1 is release-ready under the repository's current automated contract.**
- Branch used: `feat/v1-ux-reliability-hardening`.
- Verified base: PR7 documentation head `f0a5ccb4427ec133b3e7c08806eec54c2e481d37`.
- Audit/evidence document: `docs/PR8_V1_HARDENING_AUDIT.md`.
- Final PR head: `fd217b46ed9bef8b2f1c10a5b37d58dfc0467bce`.
- Final PR Android CI: `32903380126` (#170) — SUCCESS.
- Final PR artifacts:
  - `gymtracker-room-schema` — `9583952491`;
  - `gymtracker-debug-apk` — `9584120582`;
  - `gymtracker-release-apk` — `9584121122`.
- Final PR contract passed on the exact head: JVM tests, semantic Room schema verification, API 35 connected tests (52 tests), lint, debug APK, minified/resource-shrunk release APK, and all artifact uploads.
- Final audit before merge: 13 changed files; GymTracker/CI/docs only; no Pulso paths; no schema v3; only synthetic/non-identifying test data; no pending reviews or review threads; mergeable cleanly.
- PR was marked ready only after the exact head was green.
- Squash merge with `expected_head_sha` protection: `feb06cc7459a12178fbd2067e5227c716b89ecd1`.
- Issue #8 closed/completed automatically by the merge.
- `main` after merge pointed exactly to `feb06cc7459a12178fbd2067e5227c716b89ecd1`.
- Post-merge `main` Android CI: `32904132021` (#171) — SUCCESS.
- Post-merge artifacts:
  - `gymtracker-room-schema` — `9584194772`;
  - `gymtracker-debug-apk` — `9584380233`;
  - `gymtracker-release-apk` — `9584380774`.
- PR #17 description was updated after merge with exact final evidence; this metadata-only edit did not alter repository code.

## 2. Android/toolchain baseline after PR8

- Native Android / Kotlin / Jetpack Compose / Material 3.
- Package: `com.germanchoconta.gymtracker`.
- Android Gradle Plugin `9.3.0`.
- Kotlin Compose plugin `2.3.21`.
- Gradle CI `9.5.0`.
- JDK `17`.
- compileSdk `37`; targetSdk `36`; minSdk `28`.
- Compose BOM `2026.08.00`.
- Activity Compose `1.13.0`.
- Lifecycle `2.11.0`.
- Room `3.0.1`.
- KSP `2.3.10`.
- bundled SQLite `2.7.0`.
- Paging `3.5.1`; Room Paging `3.0.1`.
- Vico `3.2.3`.
- `kotlinx-serialization-json/core` `1.8.1`.

Architecture rules:
- unidirectional data flow and immutable UI state;
- ViewModels/state holders own screen state;
- Composables do not query Room directly or parse portable files;
- repositories/DAOs are persistence boundaries;
- Room owns canonical active/completed workout state;
- `SavedStateHandle` / `rememberSaveable` are UI/navigation recovery mechanisms, never canonical workout fact storage;
- DB is an application/process singleton;
- chart/export/backup encodings never become canonical metric truth.

## 3. Room schema and historical integrity

- Current DB version: **2**.
- PR5, PR6, PR7 and PR8 intentionally did **not** create schema v3.
- Schemas v1/v2 remain committed.
- `MIGRATION_1_2` remains registered and tested.
- v1 identity hash: `4419e2711112b42bfbfa3083e3499613`.
- v2 identity hash: `251aab4f3ed2b0175df34e37323e31cb`.

Canonical entities remain:
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
- workout aggregate children cascade only inside the workout aggregate.
- routine edits/removals never rewrite already-started/completed history.
- archived exercises, null historical routine references and duplicate exercise occurrences remain representable.

If any future stage genuinely requires a schema change, first demonstrate the need and assess migration, historical compatibility, backup/restore compatibility and rollback consequences. Never introduce schema v3 accidentally.

## 4. Logger, History, PR and Progress invariants

PR4 logger preserves transactional Routine → Workout start, immediate Room autosave, TARGET snapshots in `WorkoutExerciseEntity`, deterministic PREVIOUS modes (`ANY_WORKOUT`, `SAME_ROUTINE`), set types `WARMUP`, `WORK`, `DROP`, `FAILURE`, rest timer, notes, active-workout recovery and finish behavior.

PR5 History preserves unlimited DB history. Raw deterministic order remains:
`workout.startedAt DESC → workout.id DESC → workoutExercise.position ASC → workoutExercise.id ASC → set.position ASC → set.id ASC`.

PR/analytics eligibility remains:
finished workout + completed set + reps > 0 + load > 0 + type in `WORK/DROP/FAILURE`.
`WARMUP` remains visible in raw history but excluded from PR/e1RM/volume analytics.

PR chronology remains:
`startedAt ASC → workoutId ASC → workoutExercise.position ASC → workoutExerciseId ASC → set.position ASC → setId ASC`.

Tie policy remains strict improvement only. Current-best witness is the first deterministic witness of the ultimate value.

Definitions retained:
- heaviest load = exact integer grams;
- reps-at-load = exact load, no bucketing;
- e1RM = Epley, reps 2–10 only, RIR excluded, exact rational/`BigInteger` comparison, display rounded to 0.1 kg;
- exercise-session volume = overflow-safe `BigInteger` sum across all occurrences in the workout;
- volume is descriptive, not a universal quality score.

PR6 Progress remains derived/recalculable. It offers Load, Reps at exact load, e1RM, Volume and Frequency. Custom dates are inclusive in UI and represented internally as `[startOfDay(start), startOfDay(end + 1))`. Missing performance dates are not synthetic zeroes; finite frequency buckets may contain explicit zeroes. Vico/`Double` remain renderer-only.

## 5. PR7 backup/restore invariants — do not regress

Design spec: `docs/PR7_BACKUP_RESTORE_SPEC.md`.

Manual backup:
`Room v2 canonical truth → coherent Room read transaction → BackupSnapshot → portable V1 JSON → SAF output stream`

Manual restore:
`SAF input → bounded UTF-8 read → strict decode → SHA-256 integrity → pure validation → preview → explicit replace confirmation → single Room write transaction → equality verification`

CSV:
`Room snapshot → export-only CSV → SAF output stream`

Retained semantics:
- portable format V1 covers all eight canonical tables and preserves exact IDs/grams/RIR/timestamps/null relations;
- restore V1 is replace-all only; merge remains out of scope;
- no destructive mutation before validated preview + separate confirmation;
- restore is atomic and rolls back on failure/mismatch;
- CSV is never restore input;
- no broad storage permission;
- SHA-256 is corruption/integrity detection, not authenticity;
- manual backup is the durable user-owned path; OS Auto Backup is best-effort convenience;
- decoded import preview lives in ViewModel memory only;
- **process death during import preview may require file re-selection; PR8 deliberately did not change this limitation.**

## 6. PR8 hardening that is now part of V1

Accessibility / ergonomics:
- Exercise and Routine create FABs expose specific TalkBack descriptions instead of ambiguous `+` semantics;
- critical Material controls retain minimum interactive sizing;
- workout exercise titles expose heading semantics;
- loading/error states expose useful accessible text/status where appropriate;
- workout set editor adapts below 360 dp by stacking load/reps/RIR vertically;
- completed-state semantics and explicit destructive set-delete labels are retained;
- critical accessibility checks run in instrumented Compose tests.

Light/dark:
- `isSystemInDarkTheme()` plus Material 3 light/dark schemes remain the theme source;
- no second theme system and no light-only hardcoded palette were introduced;
- representative content is exercised under both schemes by accessibility tests.

Empty/loading/error:
- raw Paging history has explicit refresh loading, empty detail, recoverable refresh error + retry, append loading and append-error retry;
- existing Exercises/Routines/Progress/Backup empty/error behavior remains intact.

Recreation/restoration:
- active workout and rest-timer canonical recovery remains Room-owned;
- History/Progress use `SavedStateHandle` for appropriate selection/filter UI state;
- safe ephemeral workout modal state uses `rememberSaveable` for add/replace picker, completed-set delete confirmation and set-type menu;
- PR7 import-preview process-death limitation remains explicit.

Destructive safeguards:
- workout finish has a synchronous ViewModel `finishing` in-flight guard;
- Room finish uses affected-row count with `finishedAt IS NULL`, so repeated confirmation cannot report a second successful transition;
- pending canonical autosaves/notes are flushed before finish;
- PR7 restore confirmation semantics are unchanged.

Database/query performance:
- active workout aggregate uses one ordered workout-set read grouped in memory rather than one set query per workout exercise;
- current exercise metadata during logger hydration uses one batched `IN (...)` query rather than one read per exercise;
- PREVIOUS lookup remains contextual per exercise to preserve reference semantics;
- no new index or schema migration was justified by evidence;
- no Baseline Profile/startup dependency was added without measured startup evidence.

## 7. Hardened Android CI contract after PR8

PRs and pushes to `main` run:
1. JDK/Android/Gradle setup;
2. snapshot committed Room schemas;
3. JVM tests;
4. semantic Room schema verification;
5. upload `gymtracker-room-schema`;
6. enable KVM;
7. API 35 `connectedDebugAndroidTest` using the 320×640-class emulator setup;
8. lint;
9. assemble debug APK;
10. assemble **release APK** with the project's actual release minification/resource shrinking;
11. upload `gymtracker-debug-apk`;
12. upload `gymtracker-release-apk`.

A future PR must not be treated as green using an older SHA. Validate the exact head and its artifacts before ready/merge.

## 8. V1 release-readiness statement

As of the PR8 squash `feb06cc7459a12178fbd2067e5227c716b89ecd1` and post-merge CI `32904132021` (#171):

- core Exercises, Routine Editor, Workout Logger, unlimited History, PR engine, Progress analytics, Backup/Restore and CSV functionality are present;
- critical accessibility and narrow-screen regressions have automated coverage;
- system light/dark theme behavior remains Material 3 based;
- critical loading/error recovery has explicit UI handling;
- canonical workout recovery is Room-based and safe UI recreation state is separated from canonical state;
- destructive workout finish is guarded/idempotent;
- evidenced logger N+1 reads were reduced without schema changes;
- DB schema remains v2;
- debug and real minified/resource-shrunk release builds succeed;
- exact PR and exact post-merge CI are green;
- all automated fixtures used for PR8 are synthetic/non-identifying;
- Issue #8 is closed/completed.

This means **the core local-first GymTracker V1 is release-ready under the current repository test/build contract**. This is not a claim of Play Store publication, medical validation, cloud sync, Health Connect support or Wear OS support.

## 9. Post-V1 roadmap — do not start silently

- PR #1 — Android foundation — MERGED.
- PR #2 / Issue #2 — Room local data foundation — MERGED / COMPLETED.
- PR #3 / Issue #3 — Exercises and Routine Editor — MERGED / COMPLETED.
- PR #4 / Issue #4 — Workout Logger — MERGED / COMPLETED.
- PR #5 / Issue #5 — Unlimited History and PR Engine — MERGED / COMPLETED.
- PR #6 / Issue #6 — Progress Analytics — MERGED / COMPLETED.
- PR #7 / Issue #7 — Backup, Restore and CSV Export — MERGED / COMPLETED.
- PR #8 / Issue #8 — V1 UX / reliability hardening — MERGED / COMPLETED.
- **Issue #9 — `Post-V1 — Health Connect recovery context` — OPEN, NEXT ROADMAP STAGE, NOT STARTED.**
- Issue #10 — post-V1 Wear OS companion — OPEN/DEFERRED; do not start during Issue #9 unless the roadmap is explicitly changed.

Verified Issue #9 goal:
optionally import health/recovery context through Health Connect with explicit user permission.

Issue #9 candidate data currently listed by GitHub:
- sleep duration/stages where available;
- resting heart rate;
- HRV where supported;
- exercise/session context where useful.

Issue #9 guardrails currently listed by GitHub:
- workout logging must work with zero health permissions;
- never present a recovery estimate as a medical diagnosis;
- do not automatically change prescribed load from one day's recovery score;
- keep raw imported health data separate from derived correlations;
- provide deletion/permission controls.

Before Issue #9 implementation, verify current official Health Connect documentation, permissions, background-read requirements, history-read limits, Play policy requirements and Samsung-specific interoperability. Do not infer these from older documentation.

## 10. Next action from this handoff

PR8 is closed. Do **not** reopen it or change PR4–PR8 semantics without a concrete, demonstrated regression.

When the user explicitly continues to the next roadmap stage:
1. verify the exact current `main` head and the final documentation-head CI;
2. read this file;
3. read Issue #9 directly from GitHub again;
4. research current official Health Connect and Play policy documentation before making architecture decisions;
5. define Issue #9 scope from real findings without coupling core workout logging to health permissions;
6. keep raw health records separate from GymTracker's canonical workout truth and from derived correlations;
7. create the Issue #9 branch/PR only after the above verification;
8. do not start Issue #10 silently.
