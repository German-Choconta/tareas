# GymTracker Data Model — PR 2 Design

> `PROJECT_CONTEXT.md` is canonical. This document expands the Room schema design for issue #2.

## Design goals

- Unlimited local history.
- Raw workout sets remain canonical.
- Safe export/import and future optional sync.
- No floating-point equality problems for load.
- No cascade path may erase completed workout history.
- Queries for “previous set/session”, all-time history, PRs, and per-muscle analytics must be index-friendly.

## Canonical representations

- IDs: UUID strings.
- Load: integer grams (`Long`). Example: 42.5 kg = `42500`.
- RIR: integer tenths (`Int?`). Example: 1.5 RIR = `15`; null means not recorded.
- Reps: integer.
- Time: epoch/Instant-compatible integer representation.
- Positions/order: integer.

The UI may display kg or lb, but stored workout truth remains unit-independent grams.

## Relationships

```text
Exercise 1---* ExerciseMuscle *---1 Muscle

Routine 1---* RoutineExercise *---1 Exercise

Routine 0..1---* Workout
Workout 1---* WorkoutExercise *---1 Exercise
RoutineExercise 0..1---* WorkoutExercise
WorkoutExercise 1---* WorkoutSet
```

## Tables

### exercises

- `id TEXT PRIMARY KEY`
- `name TEXT NOT NULL`
- `equipment TEXT NULL`
- `is_unilateral INTEGER NOT NULL`
- `notes TEXT NULL`
- `is_archived INTEGER NOT NULL DEFAULT 0`
- default progression fields as appropriate for exercises without a routine override
- created/updated timestamps

Suggested indexes:
- normalized/searchable name
- archived state if query plans justify it

Historical exercises are archived, not hard-deleted.

### muscles

- `id TEXT PRIMARY KEY`
- `name TEXT NOT NULL UNIQUE`

### exercise_muscles

- `exercise_id TEXT NOT NULL`
- `muscle_id TEXT NOT NULL`
- `role TEXT NOT NULL` (`PRIMARY` / `SECONDARY` initially)
- optional contribution/weight field only if later analytics genuinely require it
- composite primary key (`exercise_id`, `muscle_id`)

Indexes:
- `exercise_id`
- `muscle_id`

### routines

- `id TEXT PRIMARY KEY`
- `name TEXT NOT NULL`
- `position INTEGER NOT NULL`
- `notes TEXT NULL`
- `is_archived INTEGER NOT NULL DEFAULT 0`
- created/updated timestamps

Historical routines are archived rather than destructively removed.

### routine_exercises

- `id TEXT PRIMARY KEY`
- `routine_id TEXT NOT NULL`
- `exercise_id TEXT NOT NULL`
- `position INTEGER NOT NULL`
- `target_set_count INTEGER NOT NULL`
- `rep_min INTEGER NOT NULL`
- `rep_max INTEGER NOT NULL`
- `target_rir_tenths INTEGER NULL`
- `rest_seconds INTEGER NOT NULL`
- `load_increment_grams INTEGER NOT NULL`
- `previous_reference_mode TEXT NOT NULL` (`ANY_WORKOUT` / `SAME_ROUTINE`)

Indexes:
- (`routine_id`, `position`)
- `exercise_id`

Constraints/checks in domain validation:
- `rep_min > 0`
- `rep_max >= rep_min`
- `target_set_count > 0`
- `rest_seconds >= 0`
- `load_increment_grams > 0`
- RIR null or non-negative

### workouts

- `id TEXT PRIMARY KEY`
- `routine_id TEXT NULL`
- `title TEXT NOT NULL`
- `started_at INTEGER NOT NULL`
- `finished_at INTEGER NULL`
- `notes TEXT NULL`

Indexes:
- `started_at`
- `routine_id`

A workout may exist without a routine.

### workout_exercises

- `id TEXT PRIMARY KEY`
- `workout_id TEXT NOT NULL`
- `exercise_id TEXT NOT NULL`
- `routine_exercise_id TEXT NULL`
- `position INTEGER NOT NULL`
- `notes TEXT NULL`

Indexes:
- (`workout_id`, `position`)
- (`exercise_id`, `workout_id`)
- `routine_exercise_id`

`routine_exercise_id` captures the routine context used at workout time while `exercise_id` remains the exercise identity.

### workout_sets

- `id TEXT PRIMARY KEY`
- `workout_exercise_id TEXT NOT NULL`
- `position INTEGER NOT NULL`
- `type TEXT NOT NULL` (`WARMUP`, `WORK`, `DROP`, `FAILURE`)
- `load_grams INTEGER NOT NULL`
- `reps INTEGER NOT NULL`
- `rir_tenths INTEGER NULL`
- `completed_at INTEGER NULL`

Indexes:
- (`workout_exercise_id`, `position`)
- `completed_at` only if query plans justify it

Domain validation:
- `load_grams >= 0`
- `reps >= 0`
- RIR null or non-negative

## “Previous” query semantics

The active logger must not ambiguously mix routine defaults with workout history.

### ANY_WORKOUT
Find the latest completed `workout_exercise` for the same `exercise_id`, excluding the current workout, ordered by workout/session time descending.

### SAME_ROUTINE
Find the latest completed comparable occurrence for the same exercise under the same routine context. Prefer matching the stable routine/routine-exercise context when possible.

The UI must label which mode is active.

## Deletion / foreign-key policy

Completed workout history takes precedence over cleanup convenience.

- Deleting/archiving an Exercise must never cascade into `workout_exercises` or `workout_sets`.
- Deleting/archiving a Routine must never delete workouts already performed from it.
- Routine configuration rows can be removed only when safe; historical workout rows preserve the workout-time references/values needed for interpretation.
- Prefer `RESTRICT`/`NO ACTION` semantics where cascade could destroy history.

Exact Room foreign-key actions must be validated with database tests before PR 2 is marked complete.

## Derived data

Do **not** persist these as the sole source of truth:
- total volume
- e1RM
- PR flags
- plateau state
- progression recommendation
- weekly muscle-set totals

Compute them from raw history. If caching is introduced later for performance, cache must be disposable/rebuildable.

## Room 3.0.1 implementation requirements

- Use `androidx.room3` APIs.
- KSP/coroutines integration.
- Export schemas into a version-controlled directory.
- Add migration-test foundation at schema v1.
- Primary database tests should use a supported Room testing strategy; do not make Robolectric the database correctness gate.
- Keep repositories between UI/ViewModel and DAOs.
- DAO read streams use `Flow` where observation is useful; writes use suspend functions/transactions.

## PR 2 acceptance examples

Tests should prove at minimum:
1. an exercise/routine graph can be written and read back losslessly;
2. a workout with multiple exercises/sets survives database reopen;
3. archiving an exercise does not erase historical workout sets;
4. deleting/changing a routine cannot erase completed workouts;
5. previous-session queries distinguish `ANY_WORKOUT` from `SAME_ROUTINE`;
6. 42.5 kg round-trips exactly as 42,500 grams;
7. 1.5 RIR round-trips exactly as integer tenths;
8. exported Room schema is generated and committed;
9. the migration-test harness can validate schema v1 as the baseline for future migrations.
