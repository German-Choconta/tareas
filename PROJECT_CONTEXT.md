# GymTracker — Project Context & Continuity

> Canonical handoff file. Read this first in every new chat/session, then verify everything against the real GitHub state. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-24 (America/Bogota)

## 0. Repository, safety, and product direction

- Repository: `German-Choconta/tareas`.
- Repository visibility: **public**.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, credentials, tokens, secrets, private exports, or personally identifying training fixtures. Use source, docs, and synthetic/non-identifying fixtures only.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Core is Android-first, local/offline, account-free, and cloud-free.
- The user has explicitly authorized autonomous GitHub work and squash-merge for GymTracker stages once all acceptance criteria are met.

## 1. Stage history

### PR #1 — Android foundation

- **MERGED** to `main`.

### PR #11 / Issue #2 — Room local data foundation

- **MERGED / COMPLETED**.
- Squash merge: `aceaadbcb4a3ea370439556780b3b674e0505350`.
- Final PR2 Android CI: `32801537677` — **SUCCESS**.

### PR #12 / Issue #3 — Exercises and Routine Editor

- **MERGED / COMPLETED**.
- Squash merge: `4915ee7ef0dda4a1bc4b01076bec3ddbba0d5e33`.
- Issue #3: closed with reason `completed`.
- Post-merge `main` CI: `32805215489` — **SUCCESS**.
- Post-merge artifacts:
  - `gymtracker-debug-apk` — artifact `9547977558`.
  - `gymtracker-room-schema` — artifact `9547920496`.

### PR #13 / Issue #4 — Workout Logger

- Branch: `feat/workout-logger`.
- PR: `#13 — GymTracker PR 4: Workout logger`.
- PR remains **draft until the final documentation head itself is green**.
- Last fully verified implementation head before final UX/doc closure: `6fc9b404e2027e970c78debc19206c83cb7cbde4`.
- Full Android CI on that head: `32807953830` — **SUCCESS**.
- Every step passed: schema snapshot, JVM tests, committed Room schema semantic verification, schema artifact, KVM, instrumented Room/migration tests, lint, debug APK assembly, and APK artifact.
- Artifacts from `32807953830`:
  - `gymtracker-debug-apk` — artifact `9548878511`.
  - `gymtracker-room-schema` — artifact `9548809161`.
- A final UX review after that green run corrected Finish confirmation behavior so untouched planned sets do not trigger an unnecessary dialog while any actually typed incomplete draft remains protected. The code/test head immediately before this context update is `b83bda36682747372e122f5685096188fc03cf3f`.
- PR #13 was verified mergeable before the final context cycle and had no review submissions or unresolved review threads.
- **Important self-reference rule:** this `PROJECT_CONTEXT.md` update creates a newer branch head. Do not merge PR4 until CI for the newer head that includes this file is SUCCESS and its artifacts are verified.

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

- Unidirectional data flow and immutable UI state.
- Feature ViewModels/state holders own screen state.
- Composables never access Room directly.
- Room repositories/DAOs remain the persistence boundary and canonical workout truth.
- Saveable/SavedStateHandle-style state is limited to small UI/navigation hints; canonical workout state belongs in Room.

## 3. Room — canonical architecture after PR4

### Dependencies

- Room: `androidx.room3:room3-runtime:3.0.1`.
- Room Gradle plugin: `androidx.room3` `3.0.1`.
- Room compiler via KSP: `androidx.room3:room3-compiler:3.0.1`.
- KSP: `2.3.10`.
- SQLite driver: `androidx.sqlite:sqlite-bundled:2.7.0` / `BundledSQLiteDriver`.
- Room testing: `androidx.room3:room3-testing:3.0.1`.

### Schema versions

- Schema v1 remains committed.
- PR4 evolves the database to **version 2** because v1 cannot preserve routine target settings independently inside a started/completed workout.
- v1 identity hash: `4419e2711112b42bfbfa3083e3499613`.
- v2 identity hash: `251aab4f3ed2b0175df34e37323e31cb`.
- Exported v2 schema:
  `app/schemas/com.germanchoconta.gymtracker.data.local.GymTrackerDatabase/2.json`.
- Runtime registers `MIGRATION_1_2`.
- Migration is additive and preserves legacy v1 workout rows without fabricating historical target values.
- Migration coverage creates a v1 database from the committed schema, writes synthetic data, migrates to v2, validates the schema, and verifies historical data is preserved.

### Stable identifiers and numeric truth

- IDs are stable `String` / UUID-compatible identifiers; no autoincrement IDs.
- Load is canonical integer grams in `Long`; `42.5 kg = 42500`.
- RIR is canonical integer tenths in `Int?`; `1.5 RIR = 15`.
- Timestamps are `Long`.
- `WorkoutSet` rows remain canonical truth. PR, e1RM, volume, trends and progression analytics remain derived/recalculable.

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
- Set types: `WARMUP`, `WORK`, `DROP`, `FAILURE`.
- Muscle roles: `PRIMARY`, `SECONDARY`.

### Historical integrity / foreign-key policy

- Exercise → historical workout references: `RESTRICT`; archive is the normal flow.
- Routine → Workout: `SET_NULL`.
- RoutineExercise → WorkoutExercise: `SET_NULL`.
- Routine → RoutineExercise may cascade because template rows are not completed-workout truth.
- Workout → WorkoutExercise and WorkoutExercise → WorkoutSet cascade only inside a workout aggregate.
- Editing/archiving a Routine or RoutineExercise after starting a workout cannot change the workout-owned target snapshot.
- Replacing an exercise during an active workout never mutates the Routine and is blocked when completed sets exist; the safe UX is to add a replacement exercise instead of silently destroying completed data.

### PR4 snapshot fields

`WorkoutExerciseEntity` now carries the workout-owned target snapshot:

- `targetSetCount`
- `repMin`
- `repMax`
- `targetRirTenths`
- `restSeconds`
- `loadIncrementGrams`
- `previousReferenceMode`

`WorkoutEntity` also carries minimal active timer recovery state:

- `restTimerEndsAt`
- `restTimerWorkoutExerciseId`

## 4. PR4 workout architecture

### Start from Routine

Starting a workout is persisted immediately and transactionally:

1. Resolve the active Routine and ordered RoutineExercise template.
2. Insert a new `WorkoutEntity` immediately.
3. Insert ordered `WorkoutExerciseEntity` rows with copied target snapshot.
4. Pre-create target set rows with stable UUID-compatible IDs.

The start transaction checks for an already-active workout, so rapid/repeated start actions do not create duplicate active workouts.

### PREVIOUS

- `ANY_WORKOUT`: latest **finished** workout before the current `startedAt` containing that exercise.
- `SAME_ROUTINE`: latest **finished** workout before current `startedAt` for the same routine and exercise.
- An unfinished current workout is never its own previous reference because previous queries require `finishedAt IS NOT NULL` and `startedAt < current.startedAt`.
- Previous completed sets are matched to Today sets by `position`.
- Different previous/current set counts are valid; unmatched Today positions simply have no previous set.
- Previous displays load/reps/RIR/type compactly per set.

### TARGET

- Target values are copied from the RoutineExercise at workout start and remain stable afterward.
- Target surface communicates target sets, rep range, RIR, rest and load increment without making the screen a fragile horizontal table.
- The architecture intentionally leaves TARGET as workout-owned inputs that a future deterministic progression engine can evolve without corrupting historical truth.

### TODAY

Each active set supports:

- load,
- reps,
- RIR,
- set type,
- completion/uncompletion.

Fast entry behavior:

- load uses decimal numeric input and exact kg→grams conversion,
- reps uses integer numeric input,
- RIR uses decimal numeric input and exact RIR→tenths conversion,
- IME traversal is load `Next` → reps `Next` → RIR `Done`,
- repeated dialogs are avoided,
- stable keys are used for editable dynamic lists,
- controls preserve Material/Compose accessibility semantics and at least 48dp interactive targets.

Per-field autosave is serialized/cancelable so rapid typing on the same field cannot allow older writes to overwrite newer values. Before completing or finishing, valid pending set inputs are flushed to Room.

### Sets

- Completing and uncompleting persist immediately.
- Completion requires valid meaningful reps.
- Completing starts the configured rest timer when applicable.
- Adding sets appends transactionally with a unique next position.
- Rapid concurrent add-set actions are tested and preserve unique ordered positions.
- Removing a completed set is blocked unless explicitly confirmed by the caller/UI.
- Position compaction uses collision-safe temporary negative positions.
- Set reorder is intentionally not implemented because it adds gym interaction cost without a clear PR4 benefit.

### Exercises during a workout

- An exercise can be added to the active workout without modifying the Routine.
- Manually added workout exercises receive sensible defaults from the Exercise master record/fallbacks.
- Appending exercises is transactional to preserve unique positions under rapid taps.
- Replacing an exercise is allowed only while the exercise has no completed sets.
- If completed sets exist, the UI directs the user to add the replacement as a new exercise instead of silently deleting completed data.

### Notes

- Workout notes write only `WorkoutEntity.notes`.
- Exercise-in-workout notes write only `WorkoutExerciseEntity.notes`.
- Master `ExerciseEntity.notes` is never overwritten.
- Notes autosave with a short debounce, but finishing flushes pending note writes before setting `finishedAt`, preventing a note typed immediately before Finish from being lost.

### Finish

- Finish sets `finishedAt` and clears rest timer recovery state.
- Untouched planned sets do **not** trigger an unnecessary confirmation dialog.
- If any incomplete set contains typed load/reps/RIR input or a non-default set type, Finish asks for confirmation before leaving that draft incomplete. Invalid-but-typed text is also treated as meaningful draft input so it cannot disappear silently.
- Incomplete/empty sets are not silently converted to completed history.
- Once finished, repository mutations that require an active workout are rejected.

## 5. PR4 navigation and UX decisions

Official Android / Jetpack Compose / Material 3 / Room guidance was reviewed before implementation and recorded in `docs/PR4_WORKOUT_LOGGER_DESIGN.md`.

Key decisions:

- Exercises/Routines remain the related management surface using `PrimaryTabRow`.
- An active workout is a dedicated transient/immersive destination, **not** a permanent third tab.
- Routine rows expose a direct Start action.
- App launch/recreation detects an unfinished workout from Room and reconstructs the logger from persisted state.
- The workout screen uses vertical exercise cards/sections rather than a horizontal PREVIOUS/TARGET/TODAY table.
- TODAY receives the strongest visual/input priority; PREVIOUS and TARGET remain adjacent and immediately readable.
- Material touch target/accessibility conventions are preserved.
- Room, not ViewModel memory, is the recovery source of truth.

## 6. Rest timer, lifecycle, autosave and recovery

### Rest timer

- Timer truth is an **absolute end timestamp** (`restTimerEndsAt`), not a counter incremented once per second.
- Remaining seconds are derived from `endsAt - now`.
- Recomposition/background time therefore does not desynchronize the timer.
- The active timer survives Activity/ViewModel reconstruction because the end timestamp is persisted in Room.
- Finishing clears timer recovery state.
- No unnecessary foreground service/background complexity was introduced in PR4.

### Recovery

- Active workout is identified by `finishedAt IS NULL`.
- ViewModel creation/recreation reloads the active aggregate from Room.
- Activity rotation/navigation/relaunch does not make in-memory ViewModel state canonical.
- Process-death-oriented recovery is achieved by persisting the workout, set values, completion state, notes and timer target in Room.

## 7. PR4 tests and verification

All committed fixtures are synthetic and non-identifying.

### JVM/state coverage

Includes:

- exact/invalid load validation,
- exact/invalid reps validation,
- exact/invalid RIR validation,
- timer remaining-time math,
- meaningful incomplete-data detection,
- untouched planned sets do not count as destructive draft loss,
- any typed incomplete draft—including invalid text—is protected before Finish,
- UI state helper behavior used by the logger.

### Instrumented Room/repository coverage

Includes:

- start workout from Routine,
- immediate active-workout persistence,
- copied target snapshot,
- snapshot independence after later Routine/RoutineExercise edits,
- `ANY_WORKOUT` previous selection,
- `SAME_ROUTINE` previous selection,
- previous session with a different number of sets,
- Today load/reps/RIR/type persistence,
- complete/uncomplete,
- rest timer end timestamp,
- add/remove set and ordered unique positions,
- rapid concurrent set append integrity,
- add exercise and ordered unique positions,
- rapid concurrent exercise append integrity,
- replace exercise only when completed data is not at risk,
- workout notes,
- workout-exercise notes,
- active-workout recovery,
- finish behavior,
- historical workout preservation,
- v1→v2 migration and historical data preservation.

## 8. Bugs / risks found and corrected during PR4

1. **Schema v1 could not preserve TARGET snapshots.**
   - Fixed with justified Room schema v2 + migration rather than reading mutable RoutineExercise rows during an active/completed workout.
2. **The old CI `git diff -- app/schemas` check could miss newly generated untracked schema files.**
   - CI now snapshots committed schemas before code generation and performs semantic JSON/file-set comparison afterward.
3. **Whole-row set autosave could overwrite another quickly edited field.**
   - Replaced routine input autosave with per-field updates.
4. **Multiple rapid writes to the same field could complete out of order.**
   - Added cancelable/serialized pending field autosaves plus explicit flush before completion/finish.
5. **A note typed immediately before Finish could lose the race with `finishedAt`.**
   - Finish now flushes pending workout/exercise notes before finalizing.
6. **Rapid double-start could create more than one active workout.**
   - Start is guarded/transactional around active-workout detection.
7. **Rapid add-set/add-exercise taps could contend for the same unique position.**
   - Append position calculation + insert are transactional and have concurrency coverage.
8. **Finish confirmation initially triggered for every untouched planned set and could miss invalid-but-typed draft text.**
   - Finish now confirms only when an incomplete set actually contains user-entered draft data or a changed set type; any nonblank typed value counts, even if validation fails.

## 9. CI contract after PR4

Android CI runs on pull requests and pushes to `main`:

1. Set up JDK/Android/Gradle.
2. Snapshot committed Room schemas.
3. Run JVM unit tests.
4. Verify generated Room schema file set + semantic contents exactly match committed schemas.
5. Upload `gymtracker-room-schema`.
6. Enable KVM.
7. Run instrumented Room/database/migration tests on emulator API 35 / Google APIs / x86_64.
8. Run lint.
9. Assemble debug APK.
10. Upload `gymtracker-debug-apk` for 14 days.

PR4 must only merge after CI for the **current final PR head including this context file** is SUCCESS, artifacts are present, the PR is mergeable and there are no unresolved review threads.

## 10. Roadmap

- PR #1 — Android foundation — **MERGED**.
- PR #2 / Issue #2 — Room local data foundation — **MERGED / COMPLETED**.
- PR #3 / Issue #3 — Exercises and Routine Editor — **MERGED / COMPLETED**.
- PR #4 / Issue #4 — Workout Logger — **IMPLEMENTED; final context-head CI + merge closure pending at time of this file update**.
- PR #5 / Issue #5 — Unlimited History and PR Engine — **NEXT, only after PR4 is fully merged/closed and `main` is green**.
- PR #6 / Issue #6 — Progress Analytics.
- PR #7 / Issue #7 — Backup, Restore, CSV Export.
- PR #8 / Issue #8 — V1 UX / reliability hardening.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 11. Next-stage direction — PR5

PR5 is **Unlimited History and PR Engine**. It must build on `WorkoutSet` as source of truth and on PR4's immutable target snapshots. Do not start PR5 until GitHub confirms PR #13 merged, Issue #4 completed, the merge commit is on `main`, and post-merge `main` CI is SUCCESS.

PR5 should research and define transparent, deterministic history/PR logic before implementation. It should provide unlimited local workout history and derived PR/e1RM/volume comparisons without storing unnecessary derived truth. Any estimate must be labeled honestly; volume is descriptive, not a quality score; progression suggestions must be deterministic, explainable and user-overridable. Preserve **LOG → COMPARE → UNDERSTAND → PROGRESS** and use PR4's **PREVIOUS + TARGET + TODAY** history as the raw basis for understanding progress.
