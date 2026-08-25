# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every new GymTracker session, then verify all status directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-24 (America/Bogota), after PR4 merge and post-merge verification.

## 0. Repository, safety, and permanent product direction

- Repository: `German-Choconta/tareas`.
- Repository is **public**.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, credentials, tokens, secrets, private exports, or personally identifying fixtures. Tests/examples must be synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Android-first, offline/local-first, account-free and cloud-free for the current roadmap.
- `WorkoutSet` is canonical truth. PRs, e1RM, volume, trends and progression are derived/recalculable unless a future stage proves persistence is necessary.
- The user has authorized autonomous GitHub work and squash-merge for GymTracker stages after acceptance criteria are met.

## 1. Completed stages and real GitHub closure

### PR #1 — Android foundation

- **MERGED**.

### PR #11 / Issue #2 — Room local data foundation

- **MERGED / COMPLETED**.
- Squash merge: `aceaadbcb4a3ea370439556780b3b674e0505350`.
- Final PR2 Android CI: `32801537677` — **SUCCESS**.

### PR #12 / Issue #3 — Exercises and Routine Editor

- **MERGED / COMPLETED**.
- Squash merge: `4915ee7ef0dda4a1bc4b01076bec3ddbba0d5e33`.
- Issue #3: **closed / completed**.
- Post-merge `main` CI: `32805215489` — **SUCCESS**.
- Post-merge artifacts:
  - `gymtracker-debug-apk` — artifact `9547977558`.
  - `gymtracker-room-schema` — artifact `9547920496`.

### PR #13 / Issue #4 — Workout Logger

- **MERGED / COMPLETED**.
- PR title: `GymTracker PR 4: Workout logger`.
- Final PR head: `1e68e65a3bec4392583c942da738989384fb451b`.
- Final PR CI: `32810018092` — **SUCCESS**.
- Final PR artifacts:
  - `gymtracker-debug-apk` — artifact `9549548586`.
  - `gymtracker-room-schema` — artifact `9549496787`.
- Squash merge commit on `main`: `12ced7e9f7838d004e2ac4cac17a23f5fa2a8529`.
- Issue #4: **closed / completed**.
- Post-merge `main` CI on that squash: `32810354122` — **SUCCESS**.
- Post-merge artifacts:
  - `gymtracker-debug-apk` — artifact `9549663102`.
  - `gymtracker-room-schema` — artifact `9549603797`.
- All substantive CI steps passed post-merge: JVM tests, schema verification, Room/migration instrumentation, lint, debug APK assembly and both artifact uploads.
- No unresolved PR review threads or review submissions blocked merge.

> This file update itself is documentation-only and occurs after the verified PR4 squash. If it creates a newer `main` head/run, verify that newer run in GitHub at the start of the next session; it does not change the PR4 application/schema implementation.

## 2. Android/toolchain baseline

- Native Android / Kotlin.
- Jetpack Compose + Material 3.
- Package: `com.germanchoconta.gymtracker`.
- Android Gradle Plugin: `9.3.0`.
- Kotlin Compose plugin: `2.3.21`.
- Gradle in CI: `9.5.0`.
- JDK: `17`.
- compileSdk: `37`.
- targetSdk: `36`.
- minSdk: `28`.
- Compose BOM: `2026.08.00`.
- Activity Compose: `1.13.0`.
- Lifecycle runtime/viewmodel Compose: `2.11.0`.
- Room: `androidx.room3:room3-runtime:3.0.1`.
- Room Gradle/compiler: `3.0.1`.
- KSP: `2.3.10`.
- SQLite: `androidx.sqlite:sqlite-bundled:2.7.0` with `BundledSQLiteDriver`.
- Room testing: `androidx.room3:room3-testing:3.0.1`.

Architecture rules:

- Unidirectional data flow and immutable UI state.
- Feature ViewModels/state holders own screen state.
- Composables do not access Room directly.
- Repositories/DAOs are the persistence boundary.
- Room is canonical for active/completed workout state.
- Saveable/SavedStateHandle-style state is only for small UI/navigation hints, never canonical workout history.
- `GymTrackerDatabase.build(applicationContext)` is process/application singleton and must not be closed by `MainActivity.onDestroy`.

## 3. Room schema after PR4

### Version and hashes

- Current DB version: **2**.
- Schema v1 remains committed.
- v1 identity hash: `4419e2711112b42bfbfa3083e3499613`.
- v2 identity hash: `251aab4f3ed2b0175df34e37323e31cb`.
- v2 schema path: `app/schemas/com.germanchoconta.gymtracker.data.local.GymTrackerDatabase/2.json`.
- Runtime registers `MIGRATION_1_2`.
- Migration is additive and preserves legacy v1 workout data without inventing target snapshot values for history that never contained them.
- Migration tests create a DB from committed v1 schema, write synthetic data, migrate to v2, validate schema and verify history preservation.

### Entities

1. `ExerciseEntity`
2. `MuscleEntity`
3. `ExerciseMuscleEntity`
4. `RoutineEntity`
5. `RoutineExerciseEntity`
6. `WorkoutEntity`
7. `WorkoutExerciseEntity`
8. `WorkoutSetEntity`

### Numeric and identity invariants

- Stable `String` / UUID-compatible IDs; no autoincrement IDs.
- Load truth: integer grams in `Long`; `42.5 kg = 42500`.
- RIR truth: integer tenths in `Int?`; `1.5 RIR = 15`.
- Timestamps: `Long`.
- Set types: `WARMUP`, `WORK`, `DROP`, `FAILURE`.
- Previous reference modes: `ANY_WORKOUT`, `SAME_ROUTINE`.

### Historical integrity

- Exercise → historical workout refs: `RESTRICT`; archive is normal flow.
- Routine → Workout: `SET_NULL`.
- RoutineExercise → WorkoutExercise: `SET_NULL`.
- Routine template edits/removals never mutate completed or already-started workout truth.
- Workout → WorkoutExercise and WorkoutExercise → WorkoutSet cascade only inside the workout aggregate.

## 4. PR3 management foundation still in force

### Exercises

- Searchable active library.
- Unlimited custom exercises.
- Create/edit/archive.
- Equipment, unilateral flag, master notes.
- Default rep range, target RIR, rest and load increment.
- Normalized primary/secondary muscle assignments.
- Archive preserves historical workouts.

### Routines

- Unlimited create/edit/archive.
- Add/remove/reorder exercises.
- `targetSetCount`, `repMin`, `repMax`, `targetRirTenths`, `restSeconds`, `loadIncrementGrams`, `previousReferenceMode`.
- Reorder/template replacement is transactional and collision-safe.
- `PrimaryTabRow` remains the related management navigation for Exercises/Routines.

## 5. PR4 Workout Logger architecture

### Start from Routine and TARGET snapshot

Starting a workout is immediate and transactional:

1. Resolve active Routine + ordered RoutineExercise rows.
2. Insert `WorkoutEntity` immediately.
3. Insert ordered `WorkoutExerciseEntity` rows with copied target snapshot.
4. Pre-create target `WorkoutSetEntity` rows with stable IDs.

PR4 required schema v2 because v1 could not hold a stable TARGET independently of later Routine edits.

`WorkoutExerciseEntity` snapshot fields:

- `targetSetCount`
- `repMin`
- `repMax`
- `targetRirTenths`
- `restSeconds`
- `loadIncrementGrams`
- `previousReferenceMode`

A started/completed workout therefore remains historically stable even if the Routine is later edited, reordered or removed.

### PREVIOUS

- `ANY_WORKOUT`: latest **finished** workout before current `startedAt` containing that exercise.
- `SAME_ROUTINE`: latest **finished** workout before current `startedAt` with same routine + exercise.
- Current unfinished workout can never be its own previous reference.
- Previous **completed** sets are matched to Today sets by position.
- Different previous/current set counts are valid; unmatched Today sets simply have no previous row.
- Previous exposes useful load/reps/RIR/type context.

### TARGET

- Stable copied template target for the workout.
- Includes sets, rep range, RIR, rest, load increment and previous-reference mode.
- Designed so a future deterministic progression layer can evolve target generation without corrupting workout history.

### TODAY

Each set supports:

- load,
- reps,
- RIR,
- set type,
- complete/uncomplete.

Fast-entry UX:

- decimal numeric load input → exact grams,
- integer reps input,
- decimal RIR input → exact tenths,
- IME flow: load `Next` → reps `Next` → RIR `Done`,
- no fragile horizontal PREVIOUS/TARGET/TODAY table,
- stable list keys,
- minimum Material/Compose interaction targets,
- TODAY has strongest input priority while PREVIOUS/TARGET remain adjacent and immediately readable.

### Set and exercise mutation safety

- Complete/uncomplete persists immediately.
- Completion requires meaningful valid reps.
- Add-set append is transactional with unique position; rapid concurrent taps are tested.
- Removing completed sets requires explicit destructive confirmation at the UX boundary.
- Position compaction uses temporary negative positions to avoid unique-index collisions.
- Set reordering is intentionally not included in PR4.
- Exercises can be added during an active workout without changing the Routine.
- Added exercise defaults come from Exercise defaults/fallbacks.
- Exercise append is transactional under rapid taps.
- Exercise replacement is blocked when completed sets exist; safer flow is to add a replacement exercise instead of silently destroying completed data.

## 6. Timer, notes, autosave and recovery

### Rest timer

- `WorkoutEntity` persists `restTimerEndsAt` and `restTimerWorkoutExerciseId`.
- Timer truth is an absolute end timestamp, not an incrementing counter.
- Remaining time is derived from `endsAt - now`, so recomposition/background does not desynchronize it.
- Timer reconstructs from Room after Activity/ViewModel recreation.
- Finish clears timer recovery state.
- No unnecessary foreground service/background complexity was added.

### Notes

- Workout notes → `WorkoutEntity.notes`.
- Workout-exercise notes → `WorkoutExerciseEntity.notes`.
- Master `ExerciseEntity.notes` is never overwritten by workout notes.
- Notes use a short autosave debounce.
- Finish flushes pending note writes before `finishedAt` so “type → Finish” does not lose notes.

### Set autosave

- Fast set entry uses per-field writes instead of whole-row writes.
- Latest writes for the same field are cancelable/serialized to prevent older writes landing after newer ones.
- Pending set autosaves are flushed before completion, destructive replacement/removal and Finish where needed.

### Recovery / process death

- Active workout = latest workout with `finishedAt IS NULL`.
- Important state is persisted in Room, not only ViewModel memory.
- On recreation/relaunch, the logger reconstructs workout/exercises/sets/notes/completion/timer from Room.

## 7. Navigation and Finish UX after PR4

- Exercises/Routines remain the management `PrimaryTabRow`.
- Active workout is a dedicated transient/immersive screen, not a third permanent tab.
- Routine rows expose direct Start.
- Untouched planned incomplete sets do **not** force an unnecessary Finish confirmation.
- If any incomplete set has user-entered load/reps/RIR text or changed set type, Finish asks for confirmation.
- Invalid-but-typed text also counts as meaningful draft input so it cannot disappear silently.
- Incomplete/empty sets are not silently converted to completed sets.
- After `finishedAt` is set, active-workout mutations are rejected.

## 8. PR4 validation coverage

All committed fixtures are synthetic/non-identifying.

### JVM/state tests

- Exact and invalid load conversion/validation.
- Reps validation and completion rules.
- Exact RIR tenths and invalid values.
- Absolute-deadline timer math.
- Meaningful incomplete draft detection, including invalid-but-typed text.
- Untouched planned sets do not count as destructive draft loss.

### Instrumented Room/repository tests

- Start workout from Routine and immediate persistence.
- Stable target snapshot and independence from later Routine edits.
- Previous `ANY_WORKOUT`.
- Previous `SAME_ROUTINE`.
- Previous session with different set count.
- Today load/reps/RIR/type persistence.
- Complete/uncomplete.
- Timer end timestamp.
- Add/remove set and ordered unique positions.
- Rapid concurrent set append integrity.
- Add exercise and ordered positions.
- Rapid concurrent exercise append integrity.
- Safe exercise replacement.
- Workout notes and workout-exercise notes.
- Active-workout recovery.
- Finish behavior.
- Historical workout preservation.
- v1→v2 migration/history preservation.

## 9. Important bugs/risks corrected in PR4

1. **v1 could not preserve TARGET snapshot** → justified schema v2 + migration.
2. **Old CI schema check could miss newly generated untracked schema files** → snapshot committed schemas before build and compare generated file-set + semantic JSON afterward.
3. **Whole-row autosave could overwrite another field** → per-field persistence.
4. **Rapid same-field writes could complete out of order** → cancelable/serialized latest-write jobs + flush.
5. **Note typed immediately before Finish could race `finishedAt`** → flush pending notes first.
6. **Rapid double-start could create duplicate active workouts** → transactional/guarded start.
7. **Rapid add-set/add-exercise could contend for unique positions** → transactional append + concurrency tests.
8. **Finish confirmation was initially too broad and could miss invalid typed draft** → confirmation now depends on any actual nonblank draft/change, not merely incomplete planned rows.

## 10. CI contract

Android CI runs on PRs and pushes to `main`:

1. Set up JDK/Android/Gradle.
2. Snapshot committed Room schemas.
3. JVM unit tests.
4. Verify generated schema file-set + semantic contents exactly match committed schemas.
5. Upload `gymtracker-room-schema`.
6. Enable KVM.
7. Instrumented Room/database/migration tests on emulator API 35 / Google APIs / x86_64.
8. Lint.
9. Assemble debug APK.
10. Upload `gymtracker-debug-apk` for 14 days.

## 11. Roadmap

- PR #1 — Android foundation — **MERGED**.
- PR #2 / Issue #2 — Room local data foundation — **MERGED / COMPLETED**.
- PR #3 / Issue #3 — Exercises and Routine Editor — **MERGED / COMPLETED**.
- PR #4 / Issue #4 — Workout Logger — **MERGED / COMPLETED**.
- PR #5 / Issue #5 — Unlimited History and PR Engine — **NEXT**.
- PR #6 / Issue #6 — Progress Analytics.
- PR #7 / Issue #7 — Backup, Restore, CSV Export.
- PR #8 / Issue #8 — V1 UX / reliability hardening.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 12. PR5 — next-stage guardrails

Issue #5 is `PR 5 — Unlimited history and PR engine` and is currently open.

Issue #5 scope from GitHub:

- raw session history,
- heaviest load PR,
- highest reps at a given load,
- estimated 1RM trend/PR with estimates clearly labeled,
- meaningful volume PRs where applicable,
- previous-session comparisons.

Constraints from Issue #5:

- never replace raw set history with derived metrics,
- no artificial history window,
- PR detection must be deterministic and tested,
- large histories must remain usable.

Before PR5 implementation, research current official Android/Compose/Material guidance for history/list/detail UX and performance, and research evidence/limitations for e1RM formulas. Define PR semantics explicitly before coding. Keep estimates labeled, do not imply fake precision, treat volume as descriptive rather than a universal performance score, and keep progression/PR logic transparent and explainable.

Do **not** start PR5 until a session explicitly continues after verifying this file against the real GitHub state.
