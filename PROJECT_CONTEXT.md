# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every new GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), during final closure of PR6 after the implementation head passed full Android CI.

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

## 1. Completed stages through PR5

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

### Current schema
- DB version: **2**.
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

## 4. Logger and raw-history invariants inherited by PR6

PR4 logger preserves:
- transactional Routine → Workout start;
- immediate Room persistence/autosave and write-race protection;
- TARGET snapshot in `WorkoutExerciseEntity`;
- PREVIOUS modes `ANY_WORKOUT` and `SAME_ROUTINE` with deterministic ties;
- set types `WARMUP`, `WORK`, `DROP`, `FAILURE`;
- complete/uncomplete, add/remove sets, add/replace exercise, notes, rest timer;
- active-workout recovery and finish behavior.

PR5 raw history preserves:
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

## 5. PR5 personal-record engine invariants reused exactly by PR6

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
- Exercise-session volume: sum of eligible `loadGrams × reps` across all occurrences of the exercise in one finished workout, using `BigInteger`.
- Volume is descriptive, never a universal workout-quality score.

## 6. PR #15 / Issue #6 — Progress Analytics

### Current GitHub state at this handoff
- PR: **#15 — GymTracker PR 6: Progress analytics**.
- Branch: `feat/progress-analytics`.
- Base: `main` at `a2f094ac68e86317a6b2392dc0aeb0495369bc42` when PR opened.
- Issue: #6 — Progress Analytics.
- PR remains **draft** until this documentation head itself passes full Android CI.
- No submitted reviews and no inline review threads were present in the latest pre-handoff check.
- `Pulso` / `pulso-finanzas` has not been touched.
- No real/private workout, health, credential, token or export data has been committed.

### Last known fully green implementation head before this documentation commit
- Code head: `00c93b4c7e06d7809c541052822e1ce09f9d75e6`.
- Android CI: `32867422398` (#128) — **SUCCESS**.
- Every step passed: JVM tests, semantic Room schema verification, schema artifact, KVM, all 33 connected instrumented tests, lint, `assembleDebug`, APK artifact.
- Artifacts:
  - `gymtracker-debug-apk` — `9570852910`.
  - `gymtracker-room-schema` — `9570718163`.
- This proves the complete PR6 implementation before the final documentation-only head. The final PR head must independently pass the same CI contract before ready/merge.

### Architecture

PR6 turns finished raw history into local/offline descriptive progress analytics without creating new canonical persisted truth:

`Room WorkoutSet truth → bounded/full fact query → pure ProgressAnalyticsEngine → immutable/restorable ProgressUiState → Compose/Vico renderer`

Rules:
- `WorkoutSet` remains canonical truth.
- analytics are derived and recalculable;
- no analytics cache is persisted as truth;
- schema remains v2;
- Paging remains for raw History only;
- charts never replace, truncate, or alter raw historical data;
- Vico is renderer-only and no Vico types cross into domain/state/persistence.

### UX

- Top-level navigation remains Exercises / Routines / History.
- Selecting an exercise in History opens a detail surface with Material 3 primary tabs:
  - `Historial`
  - `Progreso`
- No fourth bottom-navigation destination was added.
- Paging collection for raw history occurs only while the Historial section is visible.
- Metric chips are horizontally scrollable on compact screens; instrumentation verifies an off-screen `Volumen` chip can be scrolled into view and selected on the 320 px CI emulator.
- Exact-load choices use `LazyRow` so large exact-load sets are not eagerly composed.
- Every chart has a textual metric/unit/meaning explanation and an exact point-detail surface with previous/next navigation; interpretation does not rely only on pixels or color.
- Empty, loading, invalid-range and error states are explicit.

### Date/range semantics

- `ALL_TIME` has no artificial historical bound.
- Custom range uses inclusive local calendar dates.
- Internally custom range resolves with a captured `ZoneId` to half-open epoch bounds:
  `[startOfDay(start), startOfDay(end + 1))`.
- Same-day custom range is valid.
- Reversed custom range is invalid and does not issue a bounded Room query.
- Missing trend dates are not synthesized as zero.
- Finite frequency ranges may include explicit zero calendar buckets because absence in a calendar bucket is meaningful for frequency.

### Progress metrics

1. **Load**
   - maximum exact eligible load per distinct finished workout.

2. **Reps at exact load**
   - maximum reps per workout at one exact selected load;
   - workouts at other loads are absent, never synthetic zeroes;
   - default exact load = most represented eligible sessions, tie → higher exact load.

3. **Estimated 1RM / e1RM**
   - best PR5 Epley estimate per workout;
   - reps 2–10 only;
   - RIR excluded;
   - exact rational/`BigInteger` comparison retained.

4. **Volume**
   - exercise-session volume per finished workout across every occurrence of the exercise;
   - overflow-safe `BigInteger`;
   - descriptive only, not a quality score.

5. **Frequency**
   - distinct workouts containing at least one eligible set;
   - duplicate exercise occurrences in one workout count once;
   - buckets are Monday-start weeks or calendar months.

### Chart representation and long histories

- Domain values stay exact integer/`BigInteger` values.
- `Double` is only a rendering coordinate for Vico.
- X positions preserve relative calendar spacing instead of assigning equal ordinal spacing to every session.
- Same-day points retain deterministic order using bounded fractional presentation coordinates.
- Long visual series use deterministic **presentation-only** sampling.
- Sampling preserves first/last points, extrema and record witnesses.
- Sampling never removes raw Room history or changes canonical/domain analytics.
- Existing v2 indices are sufficient for the bounded analytics query; instrumented tests include `EXPLAIN QUERY PLAN` coverage, so schema v3 was not justified.

### Query/performance behavior

- Custom ranges query Room by exercise and epoch bounds using existing v2 indices.
- Opening an exercise in All-time does not perform duplicate full-fact queries for PR5 metrics and PR6 analytics: one transient per-selection `Deferred<List<PrSetFact>>` is shared.
- That shared result is process-memory state only, reset when the selection changes/closes, and is never persisted as truth.
- Unit coverage explicitly requires one All-time facts query on restoration.
- Metric changes, exact-load changes and frequency-bucket changes rebuild from already-loaded analytics and do not requery Room.

### Synthetic/non-identifying test coverage

Domain/JVM coverage includes:
- all five analytics series;
- strict ties and deterministic shuffled input;
- exact gram loads;
- WORK/DROP/FAILURE inclusion and WARMUP/incomplete/active exclusions;
- e1RM 2–10 vs outside range and RIR independence;
- duplicate exercise occurrences and frequency dedupe;
- timezone/week/month boundaries;
- `BigInteger` overflow-safe volume;
- all-time, custom, same-day, empty and invalid ranges;
- sparse/dense and 5,000-session histories;
- deterministic downsampling;
- temporal chart spacing and same-day deterministic X values;
- SavedState restoration;
- invalid range no-query behavior;
- metric/exact-load switching without Room requery;
- one shared All-time facts query.

Connected/instrumented coverage includes:
- real Room bounded analytics facts;
- archived exercise history;
- routine-deletion-safe history;
- eight-year synthetic history with arbitrary historical access;
- query-plan/index checks;
- History/Progress tab semantics;
- explicit Progress empty state;
- compact horizontal metric scrolling and selection.

## 7. PR6 implementation issues corrected before closure

1. Initial analytics UI existed but was not wired into History detail → added `Historial / Progreso` Material 3 tabs.
2. Chart X initially used ordinal session index → replaced with calendar-relative coordinates.
3. Exact-load selector could eagerly compose many chips → changed to `LazyRow`.
4. All-time selection initially queried the same facts separately for PR5 metrics and PR6 analytics → now shares one transient deferred query.
5. Compose UI empty-state test used a fragile text containment assertion → switched to exact semantics.
6. Migration to Compose test rule v2 exposed synchronization assumptions → assertions moved to observable UI semantics.
7. Compact CI emulator revealed `Volumen` was physically outside the horizontal viewport during the test → test now scrolls it into view before clicking, validating the compact UX rather than injecting an impossible off-screen touch.

## 8. PR6 non-scope

PR6 does **not** include:
- backup/restore/CSV export;
- cloud sync;
- accounts/login;
- Health Connect;
- Wear OS;
- AI coaching/recommendations;
- progression prescription;
- ads or paid features.

These remain later-stage work.

## 9. CI contract

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
- never call a PR complete because an older head was green;
- the **exact final PR head** must pass all steps and expose both artifacts;
- review threads/scope/privacy/schema/UX/performance/mergeability are rechecked before ready;
- merge with expected final head SHA;
- verify issue closure and post-merge `main` CI/artifacts;
- update this handoff on `main` with final IDs;
- verify the resulting documentation head CI before treating the closure record as fully known-good.

## 10. Roadmap

- PR #1 — Android foundation — MERGED.
- PR #2 / Issue #2 — Room local data foundation — MERGED / COMPLETED.
- PR #3 / Issue #3 — Exercises and Routine Editor — MERGED / COMPLETED.
- PR #4 / Issue #4 — Workout Logger — MERGED / COMPLETED.
- PR #5 / Issue #5 — Unlimited History and PR Engine — MERGED / COMPLETED.
- PR #6 / Issue #6 — Progress Analytics — **IMPLEMENTATION GREEN; FINAL DOCUMENTATION CI / READY / MERGE STILL PENDING at this handoff**.
- PR #7 / Issue #7 — Backup, Restore, CSV Export — **NEXT AFTER PR6, BUT DO NOT START until the user explicitly continues**.
- PR #8 / Issue #8 — V1 UX / reliability hardening.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 11. Next action from this handoff

1. Verify real GitHub state first; GitHub is the source of truth.
2. For PR #15, wait for the `PROJECT_CONTEXT.md` documentation head created from this handoff to pass full Android CI.
3. Recheck exact final head, artifacts, changed-file scope, privacy, schema v2, review threads and mergeability.
4. Update PR #15 description with the exact final-head CI/artifact IDs.
5. Mark PR #15 ready only after all gates are green.
6. Squash merge with `expected_head_sha` pinned to that exact green final head.
7. Verify Issue #6 is closed/completed and post-merge `main` CI + both artifacts pass.
8. Update `PROJECT_CONTEXT.md` on `main` with final PR6 merge/post-merge identifiers and verify the documentation-only `main` CI.
9. **Do not start PR7 silently.** After PR6 is fully closed, provide the complete PR7 continuation prompt and wait for explicit user continuation.
