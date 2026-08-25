# GymTracker — Project Context & Continuity

> Canonical handoff file. Read this first in every new chat/session, then verify everything against the real GitHub state. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-24 (America/Bogota)

## 0. Repository, safety, and product direction

- Repository: `German-Choconta/tareas`.
- Repository visibility: **public**.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, credentials, tokens, secrets, private exports, or personally identifying training fixtures. Use source, docs, and synthetic fixtures only.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Active-workout UX north star for PR4: **PREVIOUS + TARGET + TODAY**.
- Core is Android-first, local/offline, account-free, and cloud-free.
- User has explicitly authorized autonomous GitHub work and squash-merge for GymTracker stages once all acceptance criteria are met.

## 1. Stage history

### PR #1 — Android foundation

- **MERGED** to `main`.

### PR #11 / Issue #2 — Room local data foundation

- **MERGED / COMPLETED**.
- Squash merge: `aceaadbcb4a3ea370439556780b3b674e0505350`.
- Final PR2 Android CI: `32801537677` — **SUCCESS**.
- Post-handoff `main` CI known green: `32802037655`.
- PR2 artifacts:
  - `gymtracker-debug-apk`.
  - `gymtracker-room-schema`.

### PR #12 / Issue #3 — Exercises and Routine Editor

- Branch: `feat/exercises-routine-editor`.
- PR3 implementation is complete as of the last update to this file; **verify GitHub before assuming merge status**.
- Verified implementation head before this documentation-only update: `2fd1a877145c68c5842e49d047907f011a43b89b`.
- Full Android CI on that implementation head: `32804476234` — **SUCCESS**.
- Every CI step passed on `32804476234`: unit tests, committed Room schema verification, schema artifact upload, KVM, Room instrumented tests, lint, debug APK assembly, and APK upload.
- Artifacts from `32804476234`:
  - `gymtracker-debug-apk` — artifact id `9547723490`.
  - `gymtracker-room-schema` — artifact id `9547668561`.
- **Important self-reference rule:** updating this file creates a new branch head and therefore a new CI run. The exact post-context head/run must be verified in GitHub and recorded in PR #12 / the final handoff; do not treat the pre-context head above as the final branch head after this file is committed.
- PR3 must not be considered closed until PR #12 is non-draft, mergeable, has no unresolved review threads, is squash-merged, Issue #3 is closed as completed, and `main` plus any triggered `main` CI are verified.

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

Architecture rules:

- Unidirectional data flow.
- Immutable UI state.
- Feature ViewModels/state holders own screen state.
- Composables do not access Room directly.
- Room repositories/DAOs remain the persistence boundary.
- `SavedStateHandle` / saveable state is for small navigation/recovery state, not canonical workout history.

## 3. Room v1 — canonical architecture

### Dependencies

- Room: `androidx.room3:room3-runtime:3.0.1`.
- Room Gradle plugin: `androidx.room3` `3.0.1`.
- Room compiler via KSP: `androidx.room3:room3-compiler:3.0.1`.
- KSP: `2.3.10`.
- SQLite bundled driver: `androidx.sqlite:sqlite-bundled:2.7.0`.
- Room testing: `androidx.room3:room3-testing:3.0.1`.

### Database invariants

- `GymTrackerDatabase`, version **1**.
- `exportSchema = true`.
- Database name: `gymtracker.db`.
- Runtime uses `BundledSQLiteDriver`.
- Committed schema path:
  `app/schemas/com.germanchoconta.gymtracker.data.local.GymTrackerDatabase/1.json`.
- Schema v1 identity hash:
  `4419e2711112b42bfbfa3083e3499613`.
- PR3 did **not** change the Room schema. The branch schema and `main` schema were verified to be the exact same blob (`c6b3ad52d3e253f773b79852bc141f515eb11ae3`) before this context update.
- Any future schema change requires a new exported schema and migration coverage.

### Stable identifiers and numeric truth

- IDs are stable `String` / UUID-compatible identifiers; no autoincrement IDs.
- Load is integer grams in `Long`; `42.5 kg = 42500`.
- RIR is integer tenths in `Int?`; `1.5 RIR = 15`.
- Timestamps are `Long`.
- `WorkoutSet` rows are canonical truth; volume, e1RM, PRs, progression, and trends are derived/recalculable.

### Entities

1. `ExerciseEntity`
2. `MuscleEntity`
3. `ExerciseMuscleEntity`
4. `RoutineEntity`
5. `RoutineExerciseEntity`
6. `WorkoutEntity`
7. `WorkoutExerciseEntity`
8. `WorkoutSetEntity`

Constants:

- Previous reference: `ANY_WORKOUT`, `SAME_ROUTINE`.
- Set type: `WARMUP`, `WORK`, `DROP`, `FAILURE`.
- Muscle role: `PRIMARY`, `SECONDARY`.

### Historical integrity / foreign-key policy

- Exercise → historical workout references: `RESTRICT`; archive is the normal product flow.
- Routine → Workout: `SET_NULL`.
- RoutineExercise → WorkoutExercise: `SET_NULL`.
- Routine → RoutineExercise may cascade because template rows are not completed-workout truth.
- Workout → WorkoutExercise and WorkoutExercise → WorkoutSet cascade only inside a workout aggregate.
- Exercise-Muscle links may cascade because completed-workout truth does not depend on those join rows.

### Database lifecycle

- `GymTrackerDatabase.build(applicationContext)` is a process/application singleton.
- `MainActivity` must **not** close the singleton in `onDestroy`; ViewModels may survive Activity recreation and must not retain DAOs backed by a closed database.
- Tests that verify physical close/reopen persistence must create independent file-backed Room instances directly instead of closing the production singleton.

## 4. PR3 final architecture and behavior

### Exercises

Implemented:

- searchable active exercise library,
- unlimited custom exercises,
- create/edit,
- equipment,
- unilateral flag,
- notes,
- default rep range,
- default target RIR,
- default rest seconds,
- default load increment,
- normalized primary/secondary muscle assignments,
- archive instead of destructive deletion,
- historical workouts preserved.

### Routines

Implemented:

- unlimited routines,
- create/edit/archive,
- add/remove exercises,
- deterministic reorder,
- `targetSetCount`,
- `repMin`,
- `repMax`,
- `targetRirTenths`,
- `restSeconds`,
- `loadIncrementGrams`,
- `previousReferenceMode`: `ANY_WORKOUT` / `SAME_ROUTINE`.

Persistence details:

- Muscle assignments are replaced transactionally through DAO/repository operations.
- Routine save replaces the template transactionally.
- Routine reorder first moves existing rows to unique negative temporary positions, preventing collisions with the unique `(routineId, position)` index before final upserts.
- Removing a `RoutineExercise` from a template does not destroy historical `WorkoutExercise` / `WorkoutSet` rows; `routineExerciseId` becomes null through `SET_NULL`.

## 5. PR3 UI / UX decisions

Current official Android / Material 3 guidance was re-checked on 2026-08-24.

- Compose + Material 3.
- ViewModel/state with UDF.
- Inline field errors via `OutlinedTextField`.
- `LazyColumn` items use stable IDs/keys.
- Interactive targets are at least 48dp or use Material components with compliant built-in sizing.
- Routine reorder intentionally uses explicit accessible up/down controls rather than treating the generic Android drag-and-drop API as a list-reordering pattern.
- The original two-item `NavigationBar` was replaced in PR3 because official Android guidance specifies navigation bars for approximately 3–5 primary destinations.
- `Exercises` and `Routines` are two closely related management views, so PR3 uses a Material 3 `PrimaryTabRow` under the top app bar.
- Creation remains a context-sensitive FAB for one-hand reachability and low interaction count.
- Deprecated `Icons.Filled.ArrowBack` usage was replaced with `Icons.AutoMirrored.Filled.ArrowBack`.
- No workout logger, account, cloud, Health Connect, Wear OS, ads SDK, or analytics SDK in PR3.

Official guidance used for the final navigation decision:

- Android Developers — Navigation bar: intended for 3–5 destinations.
- Android Developers — Tabs / `PrimaryTabRow`: primary fixed tabs switch quickly between related main content destinations.

## 6. PR3 tests and verification

### JVM/state tests

Verified behavior includes:

- case-insensitive exercise search by name or equipment,
- exact decimal conversion (`42.5 kg ↔ 42500 g`, `1.5 RIR ↔ 15`),
- invalid/incomplete exercise progression defaults,
- valid and invalid routine target configuration,
- rep-range validation,
- RIR validation,
- rest validation,
- load-increment validation,
- reorder state behavior and out-of-bounds protection.

### Instrumented Room tests

Verified behavior includes:

- complete workout round-trip and ordered sets,
- `ANY_WORKOUT` vs `SAME_ROUTINE`,
- create/edit/archive Exercise,
- replace primary/secondary muscle assignments,
- create/edit/reorder/remove/archive Routine data,
- configured targets persist after reorder,
- removing `RoutineExercise` preserves historical `WorkoutExercise` / `WorkoutSet`,
- archive Exercise/Routine preserves completed workout history,
- file-backed DB close/reopen preserves exercise/routine data.

### CI bugs found and corrected during PR3

1. `ManagementPersistenceTest.kt` initially omitted required `WorkoutSetEntity.type`.
   - Fixed with `type = SetTypes.WORK`.
   - Known fix commit: `7ade2a2cbf19a2d4b1176229fac8ad8cfa02cbd8`.
2. After making the runtime database a process singleton, the legacy `fileDatabaseSurvivesCloseAndReopen` test closed that singleton and then requested it again, producing `Connection pool is closed` in run `32802934273`.
   - Production lifecycle was correct; the persistence test lifecycle was wrong for the new singleton contract.
   - Fixed by having the test open independent file-backed Room instances directly.
   - Fix commit: `0f4aef092b9d32bc31d4f46c57ca95aecdca68d1`.
3. Final UX review found a two-item `NavigationBar`, which did not match current official guidance, plus deprecated back-arrow icons.
   - Replaced with `PrimaryTabRow` and `Icons.AutoMirrored.Filled.ArrowBack`.
   - Fix commit: `2fd1a877145c68c5842e49d047907f011a43b89b`.

## 7. CI contract

Android CI runs on pull requests and pushes to `main`:

1. Unit tests.
2. Verify committed Room schema.
3. Upload `gymtracker-room-schema`.
4. Enable KVM.
5. Room instrumented DB tests on emulator API 35 / Google APIs / x86_64.
6. Lint.
7. Assemble debug APK.
8. Upload `gymtracker-debug-apk` for 14 days.

PR3 must only merge after the CI for the **current final PR head including this context file** is green.

## 8. Roadmap

- PR #1 — Android foundation — **MERGED**.
- PR #2 / Issue #2 — Room local data foundation — **MERGED / COMPLETED**.
- PR #3 / Issue #3 — Exercises and Routine Editor — **IMPLEMENTED; verify final GitHub closure state**.
- PR #4 / Issue #4 — Workout Logger — **NEXT ONLY AFTER PR3 IS FULLY CLOSED**.
- PR #5 / Issue #5 — Unlimited History and PR Engine.
- PR #6 / Issue #6 — Progress Analytics.
- PR #7 / Issue #7 — Backup, Restore, CSV Export.
- PR #8 / Issue #8 — V1 UX / reliability hardening.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 9. Canonical next prompt — PR4 Workout Logger

Only use this prompt after GitHub confirms PR #12 is merged, Issue #3 is closed/completed, `main` contains PR3, and the resulting `main` CI is green.

Continue GymTracker from the real GitHub state in `German-Choconta/tareas`. Read `PROJECT_CONTEXT.md` first, then verify all state directly in GitHub; GitHub is the source of truth. Do not touch `Pulso` / `pulso-finanzas`, and never commit real workout/health data, credentials, tokens, secrets, or personal fixtures because the repository is public. Confirm PR #12 / Issue #3 are fully merged/completed and `main` is green before creating or changing PR4 code.

PR4 is **Workout Logger** and must preserve the product loop **LOG → COMPARE → UNDERSTAND → PROGRESS**. Its workout-screen direction is **PREVIOUS + TARGET + TODAY**. Research current official Android / Jetpack Compose / Material 3 guidance before implementation, especially for rapid numeric entry, keyboard behavior, focus, accessibility, list editing, timers, state restoration, process-death resilience, and one-handed interaction.

Design before coding, then implement starting a workout from a Routine by copying the routine template into workout-owned state/rows so an active or completed workout never depends on future edits to the routine. For each exercise/set, expose the previous comparable result, the target, and today's actual input clearly. Support load, reps, RIR, set type (`WARMUP`, `WORK`, `DROP`, `FAILURE`), rest timer, complete/uncomplete set, add/remove sets during the session, correct set ordering, exercise notes, workout notes, changing/replacing an exercise during a session without corrupting prior history, and editing the current workout without mutating older workouts. Finish and persist workouts locally with autosave. Evaluate and implement reasonable crash/process-death resilience without introducing cloud/account dependencies.

Keep `WorkoutSet` as source of truth. Do not add advanced analytics, PR dashboards, e1RM trend analysis, cloud sync, accounts, Health Connect, or Wear OS in PR4; those belong to later roadmap stages. Preserve Room history semantics and change schema only if PR4 genuinely requires it; if schema changes, increment the version, export the new schema, add migrations, and test migrations. Add comprehensive state, persistence, lifecycle, and validation tests, keep CI fully green, update `PROJECT_CONTEXT.md`, and stop after PR4 is ready/merged according to the explicit stage instructions of that session.
