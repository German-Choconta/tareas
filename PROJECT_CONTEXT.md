# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every new GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), after PR6 merged and its post-merge `main` CI passed.

## 0. Repository, safety, and permanent direction

- Repository: `German-Choconta/tareas`.
- Repository is public.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, credentials, tokens, secrets, private exports, or identifying fixtures. Tests/examples must be synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Android-first, offline/local-first. Accounts/cloud are not part of the current V1 roadmap.
- `WorkoutSet` is canonical truth. PRs, e1RM, volume, trends, comparisons and progression are derived/recalculable unless a future stage proves persistence is necessary.
- Work autonomously in GitHub, but do not advance stages silently. Do not start the next PR until the user explicitly continues.

## 1. Completed stages through PR6

### PR #1 — Android foundation
- MERGED.

### PR #11 / Issue #2 — Room local data foundation
- MERGED / COMPLETED.
- Squash merge: `aceaadbcb4a3ea370439556780b3b674e0505350`.
- Final PR2 Android CI: `32801537677` — SUCCESS.

### PR #12 / Issue #3 — Exercises and Routine Editor
- MERGED / COMPLETED.
- Squash merge: `4915ee7ef0dda4a1bc4b01076bec3ddbba0d5e33`.
- Issue #3: closed / completed.
- Post-merge main CI: `32805215489` — SUCCESS.

### PR #13 / Issue #4 — Workout Logger
- MERGED / COMPLETED.
- Final PR head: `1e68e65a3bec4392583c942da738989384fb451b`.
- Final PR CI: `32810018092` — SUCCESS.
- Squash merge: `12ced7e9f7838d004e2ac4cac17a23f5fa2a8529`.
- Issue #4: closed / completed.
- Post-merge main CI: `32810354122` — SUCCESS.
- Documentation-only main commit after PR4: `6bb28c9bb7d0bb1b530a21e573089be34e1efff7`.
- CI for that doc head: `32810766155` — SUCCESS.

### PR #14 / Issue #5 — Unlimited History and PR Engine
- MERGED / COMPLETED.
- Final PR head: `a0381dac73709108a4935dd63379c39cd5958503`.
- Final PR Android CI: `32855092000` (#96) — SUCCESS.
- Final PR artifacts:
  - `gymtracker-debug-apk` — `9566003937`.
  - `gymtracker-room-schema` — `9565887051`.
- Squash merge: `406b627a8c339523cd5ec121e9aeca9a973cc4ee`.
- Issue #5: closed / completed.
- Post-merge main CI: `32855708244` (#97) — SUCCESS.
- Post-merge main artifacts:
  - `gymtracker-debug-apk` — `9566270897`.
  - `gymtracker-room-schema` — `9566130291`.

### PR #15 / Issue #6 — Progress Analytics
- MERGED / COMPLETED.
- Final PR head: `4fa2a1abc01f5dde8c0c0f3e26437fb3a3d348d5`.
- Final PR Android CI: `32868139154` (#129) — SUCCESS.
- Final PR artifacts:
  - `gymtracker-debug-apk` — `9571147279`.
  - `gymtracker-room-schema` — `9571010747`.
- Final scope audit: 15 GymTracker/documentation files, no Pulso, no Room schema migration, 0 submitted reviews, 0 inline review threads, mergeable before merge.
- Squash merge: `f4076f6d9d7fd09dc735a758c85f60a9a924a93d`.
- Issue #6: closed / completed.
- Post-merge main Android CI: `32870321302` (#130) — SUCCESS.
- Post-merge main artifacts:
  - `gymtracker-debug-apk` — `9571975063`.
  - `gymtracker-room-schema` — `9571833494`.
- This handoff update is documentation-only. Verify the resulting `main` CI before treating the closure record as fully known-good.

## 2. Android/toolchain baseline

- Native Android / Kotlin / Jetpack Compose / Material 3.
- Package: `com.germanchoconta.gymtracker`.
- Android Gradle Plugin: `9.3.0`.
- Kotlin Compose plugin: `2.3.21`.
- Gradle CI: `9.5.0`.
- JDK: `17`.
- compileSdk: `37`.
- targetSdk: `36`.
- minSdk: `28`.
- Compose BOM: `2026.08.00`.
- Activity Compose: `1.13.0`.
- Lifecycle runtime/viewmodel Compose: `2.11.0`.
- Room: `androidx.room3:room3-runtime:3.0.1`.
- Room compiler/Gradle plugin: `3.0.1`.
- KSP: `2.3.10`.
- SQLite: `androidx.sqlite:sqlite-bundled:2.7.0` with `BundledSQLiteDriver`.
- Room testing: `androidx.room3:room3-testing:3.0.1`.
- Paging: `3.5.1` (`paging-runtime`, `paging-compose`) plus `androidx.room3:room3-paging:3.0.1`.
- PR6 chart renderer: Vico Compose + Material 3 `3.2.3`.

Architecture rules:
- unidirectional data flow and immutable UI state;
- ViewModels/state holders own screen state;
- Composables never query Room directly;
- repositories/DAOs are the persistence boundary;
- Room owns canonical active/completed workout state;
- SavedState/rememberSaveable are UI/navigation hints, not canonical workout facts;
- DB is an application/process singleton;
- chart/rendering-library types must not leak into domain/persistence truth.

## 3. Room schema and historical integrity

- Current DB version: **2**.
- Schema v1 and v2 remain committed.
- v1 identity hash: `4419e2711112b42bfbfa3083e3499613`.
- v2 identity hash: `251aab4f3ed2b0175df34e37323e31cb`.
- `MIGRATION_1_2` remains registered and tested.
- PR5 and PR6 intentionally do **not** create schema v3.
- PR/e1RM/volume/trend/chart values are not persisted as canonical summaries.

Entities:
1. `ExerciseEntity`
2. `MuscleEntity`
3. `ExerciseMuscleEntity`
4. `RoutineEntity`
5. `RoutineExerciseEntity`
6. `WorkoutEntity`
7. `WorkoutExerciseEntity`
8. `WorkoutSetEntity`

Numeric invariants:
- stable `String`/UUID IDs;
- load truth is `Long` grams (`42.5 kg = 42500`);
- RIR truth is nullable `Int` tenths (`1.5 = 15`);
- timestamps are `Long`;
- no `Double` is canonical persistence/domain metric truth.

Historical integrity:
- Exercise → workout history: `RESTRICT`; archive is normal flow.
- Routine → Workout: `SET_NULL`.
- RoutineExercise → WorkoutExercise: `SET_NULL`.
- Workout → WorkoutExercise and WorkoutExercise → WorkoutSet cascade only inside the workout aggregate.
- routine edits/removals never rewrite already-started/completed workout history.

Relevant v2 indices:
- `index_workout_startedAt`;
- `index_workout_exercise_exerciseId`;
- `index_workout_exercise_workoutId`;
- unique `index_workout_exercise_workoutId_position`;
- `index_workout_set_workoutExerciseId`;
- unique `index_workout_set_workoutExerciseId_position`.

## 4. Workout Logger and raw History invariants

PR4 logger preserves:
- transactional Routine → Workout start;
- immediate Room persistence/autosave and write-race protection;
- TARGET snapshot in `WorkoutExerciseEntity`;
- PREVIOUS modes `ANY_WORKOUT` and `SAME_ROUTINE` with deterministic ties;
- set types `WARMUP`, `WORK`, `DROP`, `FAILURE`;
- complete/uncomplete, add/remove sets, add/replace exercise, notes, rest timer;
- active-workout recovery and finish behavior.

PR5 raw History preserves:
- History as a top-level destination alongside Exercises and Routines;
- raw history only from finished workouts;
- incomplete planned rows remain visible in raw history;
- archived exercise history remains discoverable;
- Routine deletion does not break historical sessions;
- deterministic ordering for equal timestamps;
- Room + Paging for raw history with page size 30, initial 30, prefetch 10, placeholders off, in-memory max 150;
- the DB retains all history: there is no 30/90-day or N-session retention limit.

Raw deterministic order:
`workout.startedAt DESC → workout.id DESC → workoutExercise.position ASC → workoutExercise.id ASC → set.position ASC → set.id ASC`.

## 5. PR5 personal-record engine invariants reused by PR6

A set participates in PR/analytics calculations only if all are true:
1. parent workout is finished;
2. `completedAt != null`;
3. `reps > 0`;
4. `loadGrams > 0`;
5. type is `WORK`, `DROP`, or `FAILURE`.

`WARMUP` remains visible in raw history but is excluded from PR/e1RM/volume analytics.

PR chronology:
`startedAt ASC → workoutId ASC → workoutExercise.position ASC → workoutExerciseId ASC → set.position ASC → setId ASC`.

Tie policy:
- records require strict improvement;
- equal values are ties/matches, not new PR events;
- first deterministic witness of an ultimate value remains the current-best witness.

PR definitions retained:
- Heaviest load: exact integer grams.
- Highest reps at exact load: exact `loadGrams`, no bucketing/rounding.
- Estimated 1RM/e1RM: Epley `load × (1 + reps/30)`, only 2–10 reps, RIR excluded, exact rational/`BigInteger` comparison, display rounded half-up to 0.1 kg.
- Exercise-session volume: sum eligible `loadGrams × reps` across all occurrences of the exercise in one finished workout using `BigInteger`.
- Volume is descriptive, never a universal workout-quality score.

## 6. PR6 Progress Analytics — final behavior

Architecture:
`Room WorkoutSet truth → bounded/full fact query → pure ProgressAnalyticsEngine → immutable/restorable ProgressUiState → Compose/Vico renderer`

Rules:
- `WorkoutSet` remains canonical truth;
- analytics are derived/recalculable;
- no analytics cache is persisted as truth;
- Room schema stays v2;
- Paging remains for raw History only;
- charts never replace/truncate raw history;
- Vico is renderer-only; no Vico types cross into domain/state/persistence.

UX:
- top-level navigation stays Exercises / Routines / History;
- selected exercise History detail exposes Material 3 `Historial / Progreso` tabs;
- no fourth bottom-nav destination;
- raw Paging collection occurs only while Historial is visible;
- metric chips are horizontally scrollable on compact screens;
- exact-load choices use `LazyRow`;
- charts provide textual metric/unit/meaning plus exact point detail and previous/next navigation;
- loading/empty/error/invalid-range states are explicit.

Date/range semantics:
- All time has no artificial bound;
- custom UI dates are inclusive;
- internally custom ranges become `[startOfDay(start), startOfDay(end + 1))` using a captured `ZoneId`;
- same-day ranges are valid;
- reversed ranges are invalid and do not query Room;
- missing performance dates are never synthesized as zero;
- finite frequency ranges may include explicit zero calendar buckets.

Metrics:
1. **Load** — maximum exact eligible load per distinct finished workout.
2. **Reps at exact load** — maximum reps per workout at one exact selected load; unrelated loads are absent, never synthetic zeroes; default exact load = most represented eligible sessions, tie → higher load.
3. **Estimated 1RM / e1RM** — best PR5 Epley estimate per workout, reps 2–10 only, RIR excluded, exact rational/`BigInteger` comparison.
4. **Volume** — exercise-session volume per workout across all exercise occurrences using overflow-safe `BigInteger`; descriptive only.
5. **Frequency** — distinct eligible workouts; duplicate exercise occurrences count once; Monday-start weekly or calendar-month buckets.

Long-history/rendering behavior:
- domain values remain exact integer/`BigInteger` values;
- `Double` is rendering coordinate only;
- chart X positions preserve relative calendar spacing;
- same-day points retain deterministic order;
- deterministic presentation-only sampling preserves first/last, extrema and record witnesses;
- raw Room history and domain truth are never sampled/truncated;
- existing v2 indices were validated with `EXPLAIN QUERY PLAN`; schema v3 was not justified.

Query behavior:
- custom ranges query by exercise and epoch bounds;
- All-time PR5 metrics + PR6 analytics share one transient per-selection `Deferred<List<PrSetFact>>`;
- the shared result is process-memory only and resets when selection changes/closes;
- metric/exact-load/frequency-bucket changes rebuild from loaded facts without Room requery;
- unit coverage explicitly requires one All-time facts query on restoration.

Synthetic/non-identifying coverage includes all five series, deterministic ties/shuffled input, exact grams, eligibility exclusions/inclusions, e1RM boundaries/RIR independence, duplicate occurrences, frequency dedupe, timezone/week/month boundaries, overflow-safe volume, all-time/custom/same-day/empty/invalid ranges, sparse/dense/5,000-session histories, deterministic downsampling, temporal X spacing, SavedState restoration, no-query invalid ranges, no-requery metric switches, eight-year Room history, query-plan checks, History/Progress semantics, explicit empty state and compact off-screen metric scrolling.

## 7. PR6 implementation issues corrected before closure

1. Analytics UI initially existed but was not wired into History detail → added `Historial / Progreso` Material 3 tabs.
2. Chart X initially used ordinal session index → replaced with calendar-relative coordinates.
3. Exact-load selector could eagerly compose many chips → changed to `LazyRow`.
4. All-time initially queried the same facts separately for PR5 metrics and PR6 analytics → now shares one transient deferred query.
5. Compose empty-state test used fragile text containment → changed to exact semantics.
6. Compose test rule v2 exposed synchronization assumptions → assertions moved to observable UI semantics.
7. Compact CI emulator revealed `Volumen` was outside the horizontal viewport → test now scrolls it into view before clicking, validating real compact UX.

## 8. CI contract

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
10. upload `gymtracker-debug-apk` (14 days).

Closure rule:
- exact final PR head must pass all steps and expose both artifacts;
- review threads/scope/privacy/schema/UX/performance/mergeability are checked before ready;
- merge uses expected final head SHA;
- issue closure and post-merge `main` CI/artifacts are verified;
- final handoff is committed to `main` and that documentation-only head CI must pass.

## 9. Roadmap

- PR #1 — Android foundation — MERGED.
- PR #2 / Issue #2 — Room local data foundation — MERGED / COMPLETED.
- PR #3 / Issue #3 — Exercises and Routine Editor — MERGED / COMPLETED.
- PR #4 / Issue #4 — Workout Logger — MERGED / COMPLETED.
- PR #5 / Issue #5 — Unlimited History and PR Engine — MERGED / COMPLETED.
- PR #6 / Issue #6 — Progress Analytics — **MERGED / COMPLETED**.
- PR #7 / Issue #7 — Backup, Restore, CSV Export — **NEXT, BUT DO NOT START until the user explicitly continues**.
- PR #8 / Issue #8 — V1 UX / reliability hardening.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 10. PR7 known contract — not started

Issue #7 remains open. Current scope:
- versioned portable export format;
- include schema version, generated timestamp and app version;
- integrity validation;
- import preview with record counts;
- safe restore that never silently overwrites existing data;
- human-readable CSV workout export;
- evaluate Android Auto Backup only as additional convenience, never the sole backup path;
- round-trip tests;
- corrupt/incompatible imports fail safely;
- CI + `PROJECT_CONTEXT.md` update required.

No PR7 branch or implementation is created by this handoff.

## 11. Next action from this handoff

1. Verify real GitHub state first; `main` is the source of truth.
2. PR6 is closed. Do not reopen or rewrite its semantics unless a concrete regression is found.
3. Verify the CI triggered by this documentation-only `main` commit; PR6 closure is fully known-good only when that run passes.
4. **Do not start PR7 silently.** Wait for explicit user continuation.
5. When the user continues, inspect Issue #7 and current `main`, research current official Android/Storage Access Framework/backup guidance as necessary, design PR7 before implementation, preserve all PR1–PR6 invariants, and keep export/restore user-owned, offline-first and safe by default.