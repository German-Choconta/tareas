# GymTracker — Project Context & Continuity

> Canonical handoff file. Read this first in every new chat/session, then verify everything against the real GitHub state. GitHub is the source of truth if this file is stale.
> Last updated: 2026-08-24 (America/Bogota)

## 0. Repository, safety, and current status

- Repository: `German-Choconta/tareas`.
- Repository visibility: **public**.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, credentials, tokens, secrets, private exports, or personally identifying training fixtures. Source, docs, tests, and synthetic fixtures only.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Future active-workout UX north star: **PREVIOUS + TARGET + TODAY**.
- PR #1 — Android foundation: **merged to `main`**.
- Current PR at this update: **#11 — GymTracker PR 2: Room local data foundation**.
- Branch: `feat/room-data-foundation` → `main`.
- Issue: **#2**.
- PR #11 implementation head verified before this documentation commit: `2667f81d305b2583887d219aedf6ff7a8f4bd22d`.
- Android CI run `32780613138` completed **SUCCESS** on 2026-08-24. Every job step passed, including unit tests, committed-schema verification, schema artifact upload, KVM setup, Room instrumented database tests, lint, debug APK assembly, and APK upload.
- Verified artifacts from run `32780613138`:
  - `gymtracker-debug-apk` — 19,038,808 bytes — artifact id `9539789708`.
  - `gymtracker-room-schema` — 1,867 bytes — artifact id `9539643452`.
- After this file is updated, CI must be re-verified on the new documentation head before PR #11 is marked ready or merged.
- User explicitly authorized autonomous GitHub work and merge for this GymTracker flow once acceptance criteria are met.

## 1. Product mission

Build a private, fast, Android-first strength-training tracker that prioritizes logging speed, unlimited local history, explicit comparisons, transparent progression, and user-owned data.

Core principles:

1. Logging should require very few interactions.
2. Raw workout sets are canonical; PRs, e1RM, volume, progression suggestions, and analytics are derived.
3. Unlimited history must remain available locally.
4. Core functionality is offline-first, with no account or cloud dependency.
5. Archive historical entities rather than destructively deleting referenced data.
6. Recommendations must remain deterministic, explainable, and user-overridable.
7. Volume (`load × reps`) is descriptive, not a quality score.
8. RIR and e1RM are estimates; never imply fake precision.
9. No ads SDK, analytics SDK, remote DB, Health Connect, or Wear OS in V1 core unless their roadmap issue is explicitly reached.

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

## 3. PR #2 — Room local data foundation: final architecture

### Dependencies

- Room: `androidx.room3:room3-runtime:3.0.1`.
- Room Gradle plugin: `androidx.room3` `3.0.1`.
- Room compiler via KSP: `androidx.room3:room3-compiler:3.0.1`.
- KSP: `2.3.10`.
- SQLite bundled driver: `androidx.sqlite:sqlite-bundled:2.7.0`.
- Coroutines Android/test: `1.11.0`.
- `kotlinx-serialization-core/json`: `1.8.1`, intentionally aligned with Room 3 migration tooling to avoid Android-test consistent-resolution conflicts.
- Room testing: `androidx.room3:room3-testing:3.0.1`.

### Database

- `GymTrackerDatabase`, version **1**.
- `exportSchema = true`.
- Database name: `gymtracker.db`.
- Runtime uses `BundledSQLiteDriver`.
- Committed schema path:
  `app/schemas/com.germanchoconta.gymtracker.data.local.GymTrackerDatabase/1.json`.
- Schema v1 identity hash:
  `4419e2711112b42bfbfa3083e3499613`.
- Any future schema change requires a new exported schema and migration test.

### Stable identifiers and numeric truth

- IDs are stable `String` / UUID-compatible identifiers, not autoincrement IDs.
- Load is stored as integer grams in `Long`.
  - Example: `42.5 kg = 42500`.
- RIR is stored as integer tenths in `Int?`.
  - Example: `1.5 RIR = 15`.
- Timestamps use `Long` epoch-style values.
- `WorkoutSet` rows are the source of truth; volume, e1RM, PRs, progression, and trends must be recalculable from raw data.

### Entities

1. `ExerciseEntity`
   - `id`, `name`, `equipment`, `unilateral`, `notes`, `archived`.
   - defaults: rep min/max, target RIR tenths, rest seconds, load increment grams.
2. `MuscleEntity`
   - stable id + unique name.
3. `ExerciseMuscleEntity`
   - normalized many-to-many link with `PRIMARY` / `SECONDARY` role.
4. `RoutineEntity`
   - id, name, position, notes, archived.
5. `RoutineExerciseEntity`
   - id, routineId, exerciseId, position, targetSetCount, repMin, repMax, targetRirTenths, restSeconds, loadIncrementGrams, previousReferenceMode.
6. `WorkoutEntity`
   - id, optional routineId, title, startedAt, optional finishedAt, notes.
7. `WorkoutExerciseEntity`
   - id, workoutId, exerciseId, optional routineExerciseId, position, notes.
8. `WorkoutSetEntity`
   - id, workoutExerciseId, position, type, loadGrams, reps, optional rirTenths, optional completedAt.

Constants:

- Previous reference: `ANY_WORKOUT`, `SAME_ROUTINE`.
- Set type: `WARMUP`, `WORK`, `DROP`, `FAILURE`.
- Muscle role: `PRIMARY`, `SECONDARY`.

### Historical integrity / foreign-key policy

- Exercise → historical workout references use `RESTRICT`; normal user flow is archive, not deletion.
- Routine → Workout uses `SET_NULL`, so historical workouts survive routine removal.
- RoutineExercise → WorkoutExercise uses `SET_NULL`, so completed workout history survives template changes.
- Routine → RoutineExercise may cascade because routine exercises are template rows, not completed workout truth.
- Workout → WorkoutExercise and WorkoutExercise → WorkoutSet may cascade only within destruction of a workout aggregate; normal product UX should avoid accidental destructive flows.
- Exercise-Muscle links can cascade with their owning master data because completed workout truth does not depend on those join rows.

### DAOs / repositories

- `ExerciseDao` / `ExerciseRepository`:
  - observe active exercises,
  - get by id,
  - save/upsert,
  - archive,
  - observe/link muscles.
- `RoutineDao` / `RoutineRepository`:
  - observe active routines,
  - observe ordered routine exercises,
  - save/upsert routine,
  - save/upsert routine exercise,
  - archive.
- `WorkoutDao` / `WorkoutRepository`:
  - save workout/exercise/set,
  - fetch workout/exercises/sets,
  - observe full exercise history,
  - previous comparable workout resolution.

Previous-session behavior:

- `previousAnyWorkout(exerciseId, beforeStartedAt)` returns the latest finished workout containing the exercise before the current start time.
- `previousSameRoutine(exerciseId, routineId, beforeStartedAt)` restricts that lookup to the same routine.
- Repository dispatches by `PreviousReferenceMode`.

## 4. PR #2 tests and CI acceptance

Implemented/verified tests cover:

- complete workout/exercise/set persistence,
- exact `42.5 kg ↔ 42500 g`,
- exact `1.5 RIR ↔ 15`,
- Exercise ↔ Muscle relations,
- ordered `RoutineExercise`,
- ordered `WorkoutSet`,
- exercise history query,
- `ANY_WORKOUT`,
- `SAME_ROUTINE`,
- archiving Exercise without losing historical workout data,
- archiving Routine without losing historical workout data,
- close/reopen an on-disk Room DB,
- Room `MigrationTestHelper` baseline for schema v1.

CI workflow currently runs on pull requests and pushes to `main`:

1. Unit tests.
2. Verify committed Room schema (`git diff --exit-code -- app/schemas`).
3. Upload `gymtracker-room-schema`.
4. Enable KVM.
5. Run Room instrumented DB tests on Android emulator API 35 / Google APIs / x86_64.
6. Lint.
7. Assemble debug APK.
8. Upload `gymtracker-debug-apk` with 14-day retention.

## 5. Next stage after PR #2 is merged — Issue #3 / PR #3

Do **not** begin PR #3 until PR #11 is merged and issue #2 is completed.

### Exercises

- searchable library,
- unlimited custom exercises,
- create/edit,
- primary and secondary muscles,
- equipment,
- unilateral flag,
- notes,
- default progression settings,
- archive instead of historical destruction.

### Routines

- unlimited routines,
- create/edit/archive,
- ordered exercises,
- add/remove/reorder `RoutineExercise`,
- `targetSetCount`,
- `repMin`, `repMax`,
- `targetRirTenths`,
- `restSeconds`,
- `loadIncrementGrams`,
- `previousReferenceMode` = `ANY_WORKOUT` / `SAME_ROUTINE`.

### PR #3 UX requirements

- Native Compose + Material 3.
- Research current official Android / Material 3 guidance before implementation.
- Optimize for one-handed use and very low interaction count.
- Interactive touch targets at least 48dp.
- Offline only; no account/cloud.
- Do not implement the workout logger in PR #3; active workout is PR #4.
- Prefer persistent top-level navigation only when justified by the current information architecture; do not add navigation chrome just to fill space.
- Use clear active/archived states and reversible archive flows where practical.
- Search should be immediately useful for growing local libraries.
- Forms should validate in place and avoid allowing invalid routine-progression combinations to reach Room.

### PR #3 required tests

- create/edit/archive Exercise,
- search,
- primary/secondary muscle assignments,
- create/edit/archive Routine,
- add/remove/reorder RoutineExercise,
- persistence after DB reopen,
- validation for rep range, RIR, rest, and load increment,
- preserve historical data,
- relevant ViewModel/state tests,
- full CI green.

Update this file before PR #3 is considered complete.

## 6. Roadmap

- PR #1 — Android foundation — **MERGED**.
- PR #2 / issue #2 — Room local data foundation — **current closing stage at time of this update**.
- PR #3 / issue #3 — Exercises and Routine Editor.
- PR #4 / issue #4 — Active Workout Logger: **PREVIOUS + TARGET + TODAY**.
- PR #5 / issue #5 — Unlimited History and PR Engine.
- PR #6 / issue #6 — Progress Analytics.
- PR #7 / issue #7 — Backup, Restore, CSV Export.
- PR #8 / issue #8 — V1 UX / reliability hardening.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 7. Canonical next prompt — PR #3

Continue GymTracker from the real GitHub state in `German-Choconta/tareas`. Read `PROJECT_CONTEXT.md` first, then verify `main`, issue #3, and all relevant code directly in GitHub; GitHub is the source of truth. Do not touch Pulso/pulso-finanzas, and never commit real workout/health data or secrets because the repository is public. Confirm PR #11 / issue #2 are fully merged/completed before changing product code. Then implement issue #3 / PR3: Exercises and Routine Editor on a dedicated `feat/...` branch. Before coding, research current official Android/Jetpack Compose/Material 3 UX guidance for searchable libraries, forms, one-handed interaction, accessibility, 48dp touch targets, list reordering, archive flows, and state handling. Build a searchable unlimited exercise library with create/edit/archive, custom exercises, equipment, unilateral, notes, default progression settings, and normalized primary/secondary muscle assignments. Build unlimited routines with create/edit/archive and ordered routine exercises supporting targetSetCount, repMin, repMax, targetRirTenths, restSeconds, loadIncrementGrams, and previousReferenceMode ANY_WORKOUT/SAME_ROUTINE. Keep Room as the canonical persistence layer, preserve all historical workout data, and do not implement the workout logger yet. Add robust validation plus tests for Exercise CRUD/archive/search/muscles, Routine CRUD/archive, add/remove/reorder RoutineExercise, invalid rep/RIR/rest/load-increment input, persistence after DB reopen, history preservation, and relevant ViewModel/state behavior. Keep UI native Compose + Material 3, offline, account-free, one-hand friendly, low-interaction, and >=48dp targets. Update `PROJECT_CONTEXT.md`, ensure complete CI is green, and leave the PR ready/mergeable. When PR3 is finished, stop and provide a concrete summary plus the complete prompt for PR4 Active Workout Logger centered on PREVIOUS + TARGET + TODAY.
