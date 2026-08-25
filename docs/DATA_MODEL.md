# GymTracker Data Model — Room v1

> `PROJECT_CONTEXT.md` is canonical. This file documents the implemented Room v1 schema from PR #11 / issue #2.

## Goals

- Unlimited local history.
- Raw workout sets are canonical.
- Exact numeric storage for load and RIR.
- Safe archive semantics for historical exercises/routines.
- Index-friendly previous-session and history queries.
- Exported Room schema versioned in Git for future migration tests.

## Canonical representations

- IDs: stable UUID-compatible strings (`TEXT`).
- Load: integer grams (`Long`). `42.5 kg = 42500`.
- RIR: integer tenths (`Int?`). `1.5 RIR = 15`; null = not recorded.
- Reps/order: integers.
- Time: epoch-compatible `Long`.
- Set type: `WARMUP`, `WORK`, `DROP`, `FAILURE`.
- Previous reference: `ANY_WORKOUT` or `SAME_ROUTINE`.
- Muscle role: `PRIMARY` or `SECONDARY`.

Derived volume, e1RM, PRs, plateau flags, and progression recommendations never replace raw rows.

## Implemented tables

### `exercise`
Primary key: `id`.
Fields: name, equipment, unilateral, notes, archived, default rep/RIR/rest/load-increment settings.
Index: name.

### `muscle`
Primary key: `id`.
Unique index: name.

### `exercise_muscle`
Composite primary key: (`exerciseId`, `muscleId`).
FKs: Exercise and Muscle, cascading only the join row when a non-historical parent can actually be deleted.
Indexes: exerciseId, muscleId.

### `routine`
Primary key: `id`.
Fields: name, position, notes, archived.
Index: position.

### `routine_exercise`
Primary key: `id`.
FKs:
- Routine -> CASCADE for configuration rows.
- Exercise -> RESTRICT so an exercise referenced by configuration cannot be silently removed.

Fields include target set count, rep range, target RIR tenths, rest seconds, load increment grams, and previous reference mode.
Indexes: routineId, exerciseId, unique (`routineId`, `position`).

### `workout`
Primary key: `id`.
Nullable Routine FK uses SET_NULL, so deleting routine configuration cannot delete completed workout history.
Indexes: routineId, startedAt.

### `workout_exercise`
Primary key: `id`.
FKs:
- Workout -> CASCADE (explicit workout deletion owns its child rows).
- Exercise -> RESTRICT (historical exercise identity is protected).
- RoutineExercise -> SET_NULL (routine configuration can disappear without losing workout history).

Indexes: workoutId, exerciseId, routineExerciseId, unique (`workoutId`, `position`).

### `workout_set`
Primary key: `id`.
FK: WorkoutExercise -> CASCADE.
Fields: position, type, loadGrams, reps, rirTenths, completedAt.
Indexes: workoutExerciseId, unique (`workoutExerciseId`, `position`).

## Historical deletion policy

The public repositories expose archive operations for Exercise and Routine, not destructive deletion. This is the normal product path.

The FK graph provides a second safety layer:
- deleting a historical Exercise is blocked by `workout_exercise.exerciseId` RESTRICT;
- deleting a Routine does not erase Workouts because `workout.routineId` is SET_NULL;
- deleting RoutineExercise configuration only nulls historical `workout_exercise.routineExerciseId`;
- deleting an explicit Workout may cascade to its own WorkoutExercise/WorkoutSet rows.

## Previous-session semantics

`WorkoutDao.previousAnyWorkout`:
- same Exercise;
- completed Workout only;
- before supplied start timestamp;
- newest start time wins.

`WorkoutDao.previousSameRoutine` adds the Routine constraint.

`WorkoutRepository.previousWorkout` selects the correct query from `ANY_WORKOUT` / `SAME_ROUTINE`.

## Observable/query boundaries

- Writes and point reads: `suspend` DAO functions.
- Observable collections/history: `Flow`.
- UI/composables must not call Room directly; repositories are the boundary for future ViewModels.

## Test contract

Instrumented tests cover:
- exercise + muscle relation round trip;
- routine configuration;
- complete workout/set persistence;
- exact `42500 g` representation for 42.5 kg;
- exact `15` representation for 1.5 RIR;
- stable set ordering;
- chronological exercise history;
- `ANY_WORKOUT` vs `SAME_ROUTINE` resolution;
- archiving Exercise/Routine without losing completed workout rows.

`GymTrackerMigrationFoundationTest` uses Room 3.0.1 `MigrationTestHelper` and the exported schema as the baseline for future schema-version migrations.

## Room stack

- Room 3.0.1 (`androidx.room3`)
- KSP 2.3.10
- sqlite-bundled 2.7.0 / `BundledSQLiteDriver`
- coroutines 1.11.0
- schema directory: `app/schemas`

Do not enable destructive migration as a normal upgrade strategy. Every schema version after v1 must add and test a migration path.
