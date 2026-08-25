# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), during PR8 / Issue #8 implementation.

## 0. Repository, safety, and permanent direction

- Repository: `German-Choconta/tareas`.
- Repository is public.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, identifying names/fixtures, credentials, tokens, secrets, private exports or personal backups. Tests/examples must be synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Android-first, offline/local-first. Accounts/cloud are outside V1 scope.
- `WorkoutSet` remains canonical truth. PRs, e1RM, volume, trends, charts, CSV and analytics are derived/recalculable/read-only representations.
- Room remains canonical for active and completed workouts.
- Paging is presentation/query only and never a history-retention limit.
- Work autonomously in GitHub, but do not advance stages silently. Do not start the next issue until the user explicitly continues.

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
- Final PR6 documentation commit: `1905412984ad4b8c04b9937e47844f8b149c4b9f`; CI `32871077072` (#131) — SUCCESS.

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

- Current DB version: **2**. PR5, PR6, PR7 and PR8 intentionally do **not** create schema v3.
- Schemas v1/v2 remain committed; `MIGRATION_1_2` remains registered and tested.
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

## 4. Logger, History, PR and Progress invariants

PR4 logger preserves transactional Routine → Workout start, immediate Room autosave, TARGET snapshots in `WorkoutExerciseEntity`, deterministic PREVIOUS modes (`ANY_WORKOUT`, `SAME_ROUTINE`), set types `WARMUP`, `WORK`, `DROP`, `FAILURE`, rest timer, notes, active-workout recovery and finish behavior.

PR5 History preserves unlimited DB history. Raw deterministic order remains:
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

PR6 Progress remains derived/recalculable. It offers Load, Reps at exact load, e1RM, Volume and Frequency; custom dates are inclusive in UI and represented internally as `[startOfDay(start), startOfDay(end + 1))`; missing performance dates are not synthetic zeroes; finite frequency buckets may contain explicit zeroes. Vico/`Double` remain renderer-only.

## 5. PR7 backup/restore invariants — do not regress

Design spec: `docs/PR7_BACKUP_RESTORE_SPEC.md`.

Manual backup:
`Room v2 canonical truth → coherent Room read transaction → BackupSnapshot → portable V1 JSON → SAF output stream`

Manual restore:
`SAF input → bounded UTF-8 read → strict decode → SHA-256 integrity → pure validation → preview → explicit replace confirmation → single Room write transaction → equality verification`

CSV:
`Room snapshot → export-only CSV → SAF output stream`

Important retained semantics:
- portable format V1 covers all eight canonical tables and preserves exact IDs/grams/RIR/timestamps/null relations;
- restore V1 is replace-all only; merge remains out of scope;
- no destructive mutation before validated preview + separate confirmation;
- restore is atomic and rolls back on failure/mismatch;
- CSV is never restore input;
- no broad storage permission;
- SHA-256 is corruption/integrity detection, not authenticity;
- manual backup is the durable user-owned path; OS Auto Backup is best-effort convenience;
- decoded import preview lives in ViewModel memory only. **Process death during import preview may require file re-selection. PR8 does not change this limitation.**

## 6. PR8 / Issue #8 — V1 UX and reliability hardening — IN PROGRESS

- Issue #8: `PR 8 — V1 UX and reliability hardening` — OPEN until merge.
- Draft PR: **#17 — `GymTracker PR 8: V1 UX and reliability hardening`**.
- Branch: `feat/v1-ux-reliability-hardening`.
- Verified base: PR7 documentation head `f0a5ccb4427ec133b3e7c08806eec54c2e481d37`.
- Evidence/audit: `docs/PR8_V1_HARDENING_AUDIT.md`.
- Latest implementation/audit head before this handoff update: `ae564890ef67a17ab9bfd8b645f6b714fda988b6`.
- Keep PR #17 draft until the **exact final head** passes the hardened CI contract and final scope/review/schema audit.

### PR8 changes implemented so far

Accessibility / ergonomics:
- top-level Exercise/Routine create FABs expose specific TalkBack descriptions instead of an ambiguous visual `+`;
- critical Material controls retain minimum interactive sizing;
- workout exercise heading has heading semantics;
- app loading and history error status expose useful accessible text/live status;
- workout set editor adapts on narrow widths: below 360 dp, load/reps/RIR stack vertically instead of being squeezed into three columns;
- completed-state semantics and explicit destructive set-delete labels remain available.

Light/dark:
- existing `isSystemInDarkTheme()` + Material 3 light/dark schemes remain the source of theme behavior;
- no new hard-coded light-only colors were introduced;
- no second theme system was added.

Empty/loading/error:
- raw Paging history now has explicit refresh loading, empty detail, recoverable refresh error + retry, append loading and append-error retry;
- existing Exercises/Routines/Progress/Backup empty/error states remain intact.

Recreation/restoration:
- canonical active workout and rest-timer recovery still come from Room;
- History/Progress continues using `SavedStateHandle` for appropriate selection/filter state;
- safe ephemeral workout modal state uses `rememberSaveable` (add/replace picker, completed-set delete confirmation, set-type menu);
- PR7 import preview process-death limitation is deliberately unchanged.

Destructive safeguards:
- workout finish now has a synchronous ViewModel `finishing` in-flight guard;
- Room finish is checked by affected-row count with `finishedAt IS NULL`, so repeated confirmation cannot report a second successful transition;
- pending canonical autosaves/notes are still flushed before finish;
- PR7 restore double-confirm protection remains unchanged.

Database/query performance:
- active workout aggregate no longer issues one set query per workout exercise: one ordered workout-set query is grouped in memory;
- current exercise metadata during logger hydration uses one batched `IN (...)` query instead of one read per exercise;
- PREVIOUS lookup remains contextual per exercise to preserve reference semantics;
- routine-editor name hydration is documented but not changed because no measured hot-path evidence justifies broader churn;
- no new index/schema migration was justified; DB remains v2.

Startup/release:
- DB remains application-context singleton;
- no Baseline Profile/startup dependency was added without measured evidence;
- CI now assembles the real minified/resource-shrunk `release` variant in addition to debug;
- CI uploads `gymtracker-release-apk`; missing/failing release output fails the contract.

Tests:
- new synthetic persistence coverage verifies deterministic batched aggregate hydration and first-write-only/idempotent workout finish;
- new critical Compose tests cover specific TalkBack actions and 320×640-class logger ergonomics;
- official Compose accessibility-test bridge is `androidTestImplementation` only and runs on CI API 35;
- all older PR4–PR7 tests remain part of the suite.

### PR8 non-scope still enforced

No backend/accounts/cloud sync, Health Connect, Wear OS, AI coaching, monetization, progression prescription, PR/e1RM/volume formula changes, PR7 portable-format/restore semantic changes, or Room schema v3.

## 7. Hardened Android CI contract

PRs and pushes to `main` now run:
1. JDK/Android/Gradle setup;
2. snapshot committed Room schemas;
3. JVM tests;
4. semantic Room schema verification;
5. upload `gymtracker-room-schema`;
6. enable KVM;
7. API 35 `connectedDebugAndroidTest`;
8. lint;
9. assemble debug APK;
10. assemble **release APK** with the project’s actual release minification/resource shrinking;
11. upload `gymtracker-debug-apk`;
12. upload `gymtracker-release-apk`.

PR8 closure requires the exact final PR head to pass every step and expose all three artifacts. Before ready/merge, audit changed paths (no Pulso), privacy/synthetic fixtures, schema v2, review threads, mergeability and Issue #8 scope. Merge must be squash-protected by the expected exact head SHA.

After merge, verify linked Issue #8 closed/completed and exact post-merge `main` CI + all artifacts. Then commit the final merged-state handoff to `main` and verify the exact documentation-head CI under the same hardened contract.

## 8. Roadmap

- PR #1 — Android foundation — MERGED.
- PR #2 / Issue #2 — Room local data foundation — MERGED / COMPLETED.
- PR #3 / Issue #3 — Exercises and Routine Editor — MERGED / COMPLETED.
- PR #4 / Issue #4 — Workout Logger — MERGED / COMPLETED.
- PR #5 / Issue #5 — Unlimited History and PR Engine — MERGED / COMPLETED.
- PR #6 / Issue #6 — Progress Analytics — MERGED / COMPLETED.
- PR #7 / Issue #7 — Backup, Restore and CSV Export — MERGED / COMPLETED.
- PR #8 / Issue #8 — V1 UX / reliability hardening — **IN PROGRESS — DRAFT PR #17**.
- Issue #9 — post-V1 Health Connect recovery context — **DO NOT START DURING PR8**.
- Issue #10 — post-V1 Wear OS companion — **DO NOT START DURING PR8**.

## 9. Next action from this handoff

1. Verify CI on the exact current PR #17 head; do not reuse a green result from an older head.
2. Fix any instrumented/accessibility/lint/release failure on the PR8 branch.
3. Complete final diff/scope/privacy/schema/review/mergeability audit.
4. Update PR #17 body with actual evidence and exact final CI/artifacts.
5. Only when exact head is green and audit complete, mark PR #17 ready.
6. Squash merge with `expected_head_sha` protection.
7. Verify Issue #8 closed/completed and post-merge `main` CI/artifacts.
8. Write final PR8 merged-state handoff to `main` and verify its exact documentation-head CI.
9. **Do not start Issue #9 silently.** After PR8 closure, read the real Issue #9 and provide the user the complete standalone next-stage prompt.