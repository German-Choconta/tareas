# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every new GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), during PR5 finalization after the final code candidate passed full CI.

## 0. Repository, safety, and permanent direction

- Repository: `German-Choconta/tareas`.
- Repository is public.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout data, health data, credentials, tokens, secrets, private exports, or identifying fixtures. Tests/examples must be synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Android-first, offline/local-first, no accounts/cloud in the current V1 roadmap.
- `WorkoutSet` is canonical truth. PRs, e1RM, volume, trends, comparisons and progression are derived/recalculable unless a future stage proves persistence is necessary.
- Work autonomously in GitHub, but do not advance stages silently. Do not start the next PR until the user explicitly continues.

## 1. Completed stages before PR5

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
- Artifacts from that known-good main run:
  - `gymtracker-debug-apk` — `9549764881`.
  - `gymtracker-room-schema` — `9549721821`.

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
- PR5 adds Paging `3.5.1` (`paging-runtime`, `paging-compose`) and `androidx.room3:room3-paging:3.0.1`.

Architecture rules:
- unidirectional data flow and immutable UI state;
- ViewModels/state holders own screen state;
- Composables never query Room directly;
- repositories/DAOs are the persistence boundary;
- Room owns canonical active/completed workout state;
- SavedState/rememberSaveable are for UI/navigation hints, not canonical workout facts;
- DB is an application/process singleton.

## 3. Room schema and historical integrity

### Current schema
- DB version: **2**.
- Schema v1 remains committed.
- v1 identity hash: `4419e2711112b42bfbfa3083e3499613`.
- v2 identity hash: `251aab4f3ed2b0175df34e37323e31cb`.
- `MIGRATION_1_2` remains registered and tested.
- PR5 intentionally does **not** create schema v3 and does not persist PR/e1RM/volume badges or summaries.

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
- no `Double` is canonical persistence truth.

Historical integrity:
- Exercise → workout history: `RESTRICT`; archive is normal flow.
- Routine → Workout: `SET_NULL`.
- RoutineExercise → WorkoutExercise: `SET_NULL`.
- Workout → WorkoutExercise and WorkoutExercise → WorkoutSet cascade only inside the workout aggregate.
- routine edits/removals never rewrite already-started/completed workout history.

Existing v2 indices relevant to PR5 include:
- `index_workout_startedAt`;
- `index_workout_exercise_exerciseId`;
- `index_workout_exercise_workoutId`;
- unique `index_workout_exercise_workoutId_position`;
- `index_workout_set_workoutExerciseId`;
- unique `index_workout_set_workoutExerciseId_position`.

## 4. PR4 logger invariants that PR5 must preserve

PR4 implements:
- start workout transactionally from Routine;
- immediate Room persistence;
- TARGET snapshot in `WorkoutExerciseEntity`:
  - `targetSetCount`
  - `repMin`
  - `repMax`
  - `targetRirTenths`
  - `restSeconds`
  - `loadIncrementGrams`
  - `previousReferenceMode`
- PREVIOUS modes `ANY_WORKOUT` and `SAME_ROUTINE`;
- PREVIOUS completed sets matched by set position;
- load/reps/RIR and `WARMUP`/`WORK`/`DROP`/`FAILURE`;
- complete/uncomplete;
- add/remove sets;
- add/replace exercise safely;
- workout and workout-exercise notes;
- absolute-timestamp rest timer;
- autosave and write-race protection;
- active workout recovery;
- Finish behavior and immersive active-workout navigation.

PR5 additionally makes PR4 historical selectors deterministic when timestamps tie:
- active workout: `startedAt DESC, id DESC`;
- `ANY_WORKOUT`: `startedAt DESC, workout id DESC, workout-exercise position/id`;
- `SAME_ROUTINE`: same deterministic tie policy;
- legacy exercise-history ordering also receives explicit IDs/positions.

## 5. PR #14 / Issue #5 — Unlimited History and PR Engine

### GitHub state at this handoff commit
- PR: **#14 — GymTracker PR 5: Unlimited history and PR engine**.
- Branch: `feat/history-pr-engine`.
- Base: `main` at `6bb28c9bb7d0bb1b530a21e573089be34e1efff7` when last verified.
- Issue: **#5 — PR 5 — Unlimited history and PR engine**.
- Issue #5 is still open while PR finalization is in progress; it must close as `completed` only when PR5 is actually merged.
- PR remains draft until the exact head containing this updated handoff passes full CI and final reviews.
- Final code candidate before this documentation commit: `698257903290a7a8612e9aac4d7e41c3e5f2fe87`.
- Full code-candidate Android CI: run `32854385890` (#95) — **SUCCESS**.
- Code-candidate artifacts:
  - `gymtracker-debug-apk` — `9565748166`.
  - `gymtracker-room-schema` — `9565620005`.
- PR #14 had zero submitted reviews and zero inline review threads when verified after #95.
- No PR5 merge has happened yet at the time this text is committed.

### Scope implemented
- unlimited local raw history by exercise;
- Room + Paging 3 lazy retrieval;
- deterministic heaviest-load PR;
- deterministic highest-reps-at-exact-load PR;
- Estimated 1RM/e1RM PR and per-set estimate where eligible;
- meaningful exercise-session volume PR;
- historical PR events vs current best distinction;
- previous-session comparison;
- archived-exercise history;
- routine-deletion-safe history;
- deterministic timestamp ties;
- large synthetic history tests;
- History top-level destination with state restoration.

No PR6 dashboards/charts, cloud sync, accounts, login, Health Connect, Wear OS, ads, analytics SDK, AI coaching, final progression engine, backup/restore or CSV are part of PR5.

## 6. PR5 raw history architecture

Persistence/query/UI are separated:
- `HistoryDao`: raw joins and deterministic ordering;
- `HistoryRepository`: Paging/fact boundary;
- `PersonalRecordEngine`: pure deterministic domain calculations;
- `HistoryViewModel`: combines selected exercise, metrics and paged raw rows;
- Compose renders only state/paging output.

Raw history rules:
- history UI only exposes workouts with `finishedAt != null`;
- every stored set row in those sessions remains visible, including an incomplete planned/draft row;
- active workouts are never definitive historical PR evidence;
- archived exercises with finished sessions remain discoverable/readable;
- deleted Routine does not break history because `Workout.title` is the session snapshot/context and `routineId` may be null;
- old v1 historical rows remain valid even without TARGET snapshot because PR5 derives from workout/set facts rather than current Routine targets.

Deterministic raw order:
`workout.startedAt DESC → workout.id DESC → workoutExercise.position ASC → workoutExercise.id ASC → set.position ASC → set.id ASC`.

Paging:
- page size `30`;
- initial load `30`;
- prefetch distance `10`;
- placeholders disabled;
- in-memory Paging max size `150`;
- DB retains all history and older pages remain reloadable; no 30/90-day or N-session retention window exists.

The full eligible fact list for one exercise is intentionally read when calculating historical PR events/current best because these require full chronology. Domain processing sorts once and then uses bounded passes: worst-case **O(N log N)**, not O(N²). JVM coverage includes 50,000 synthetic facts; instrumented Paging coverage includes 240 synthetic workouts with bounded page loads.

## 7. PR eligibility and deterministic ties

A set participates in PR calculations only if all are true:
1. parent workout is finished;
2. `completedAt != null`;
3. `reps > 0`;
4. `loadGrams > 0`;
5. type is `WORK`, `DROP`, or `FAILURE`.

`WARMUP` stays visible in raw history but is excluded from PR/e1RM/volume records.

RIR:
- may be null;
- is displayed when available;
- is deliberately **not** used by the PR5 e1RM formula.

Unilateral exercises:
- use the exact recorded `loadGrams`;
- PR5 does not double/reinterpret load because body-side semantics are not persisted.

PR chronology:
`startedAt ASC → workoutId ASC → workoutExercise.position ASC → workoutExerciseId ASC → set.position ASC → setId ASC`.

Tie policy:
- records require **strict improvement**;
- equal values are ties/matches, not new PR events;
- multiple strict improvements within the same workout can each be historical events;
- first deterministic witness of the ultimate value remains the current-best witness; later ties do not replace it.

## 8. Exact PR definitions

### Heaviest Load
- exact integer grams;
- `newLoad > previousBestLoad` creates a historical event;
- equality is not a new event.

### Highest Reps at Exact Load
- map key is exact `loadGrams`;
- no buckets or silent load rounding;
- `100000 g` and `100001 g` are separate records;
- reps must strictly exceed the prior best for that exact gram value;
- equality is not a new event.

### Estimated 1RM / e1RM
Formula chosen for PR5: Epley

`e1RM = load × (1 + reps / 30)`

Rules:
- only 2–10 reps are eligible for e1RM;
- RIR is not used;
- exact comparison uses rational numerator `loadGrams × (30 + reps)` over constant denominator 30;
- domain arithmetic uses integer/`BigInteger`, not floating canonical truth;
- display rounds half-up to nearest 100 g / 0.1 kg;
- always label `Estimated 1RM / e1RM`, never real/measured 1RM;
- sets outside 2–10 reps can still count for other PR types.

Limitations documented in `docs/PR5_HISTORY_PR_SPEC.md`:
- repetition equations are population/exercise dependent;
- prediction error grows with higher repetition counts;
- a non-failure set may underestimate true maximal capacity;
- RIR adjustment is intentionally deferred rather than inventing a mixed model.

Primary/technical literature recorded in the spec:
- Reynolds JM, Gordon TJ, Robergs RA, J Strength Cond Res 2006, PMID 16937972;
- Mayhew JL et al., J Strength Cond Res 2008, PMID 18714230;
- Mayhew JL et al., J Sports Med Phys Fitness 2002, PMID 12094120.

### Volume
Only **exercise-session volume** is treated as a PR metric:
- sum of `loadGrams × reps` across PR-eligible sets for that exercise in one finished workout;
- multiple occurrences of the same exercise in the same workout are combined;
- `BigInteger` prevents overflow;
- strict improvement creates the historical volume event;
- UI calls it descriptive volume, never a universal quality/better-workout score;
- set-volume and whole-workout-volume badges are intentionally omitted.

## 9. Historical PR event vs current best

PR5 explicitly separates:
- **historical event**: a set/session strictly improved the record that existed at that time;
- **current best**: the highest value after processing the entire eligible history.

The UI uses distinct explanatory text for both concepts rather than one ambiguous badge.

## 10. Previous-session comparison

PR5 keeps semantics consistent with PR4:
- `ANY_WORKOUT`: latest finished earlier workout containing the exercise;
- `SAME_ROUTINE`: latest finished earlier workout with same routine + exercise;
- same-start-time ties now have explicit deterministic ordering;
- PR4 set matching stays position-based.

History’s compact comparison shows the latest two finished eligible exercise sessions and their completed-set count, max load and volume. Active workouts are excluded. Different set counts are valid.

## 11. History UX and state restoration

Compact-device primary destinations are now:
- Exercises;
- Routines;
- History.

A Material 3 `NavigationBar` is used for these three equal-priority destinations. Active workout remains immersive/transient and does not become a fourth persistent destination.

History root:
- lists exercises with finished history, including archived exercises;
- stable exercise keys;
- session count and latest date;
- no redundant back arrow because History root is top-level.

Exercise history detail:
- current record summary;
- previous-session comparison;
- raw session headers/context;
- raw set type/load/reps/RIR/completion state;
- per-set e1RM only when eligible;
- historical PR explanations;
- current-best explanations;
- raw facts remain visible and are never replaced by summaries.

State:
- top-level destination uses `rememberSaveable`;
- `rememberSaveableStateHolder` preserves saveable subtree/list state while switching destinations;
- selected History exercise is stored in `SavedStateHandle` via `CreationExtras.createSavedStateHandle()`;
- recreation test verifies selection/metrics restoration and clearing.

Interactive rows/buttons use Material minimum interactive component sizing and record meaning is expressed in text, not color alone.

## 12. PR5 test coverage

All new fixtures are synthetic/non-identifying.

### JVM/domain
- heaviest-load strict improvements and ties;
- reps-at-load exact grams and nearby-load separation;
- multiple PRs inside one session;
- current-best witness vs historical events;
- Epley exact calculation;
- e1RM display rounding;
- invalid e1RM rep ranges;
- RIR-independent e1RM behavior;
- WARMUP/incomplete/zero-load/zero-reps/active-workout exclusions;
- WORK/DROP/FAILURE inclusion;
- exercise-session volume;
- duplicate exercise occurrences within a workout;
- overflow-safe volume;
- volume ties;
- deterministic equal timestamps and reversed input;
- previous-session comparison with different set count;
- 50,000-fact synthetic history;
- History ViewModel SavedStateHandle restoration.

### Instrumented Room/Paging
- raw history only from finished workouts;
- incomplete raw rows remain visible;
- deterministic same-timestamp ordering;
- archived exercise history;
- Routine deletion + `SET_NULL` without history loss;
- fact query exposes active/incomplete facts so domain rejection itself is tested;
- 240-workout paged history, bounded pages and access beyond first page;
- PR4 `ANY_WORKOUT` and `SAME_ROUTINE` deterministic ties;
- existing PR1–PR4 Room/migration suite remains part of CI.

## 13. PR5 bugs/issues corrected during implementation

1. Large-history test initially assumed the last alternating-rep fixture had highest volume → fixture made monotonic so the test measures the intended invariant.
2. Paging could emit raw rows before PR calculation finished, temporarily omitting annotations → paged stream now depends on `(selectedExerciseId, records)` so it regenerates deterministically after metrics load.
3. Existing PR4 PREVIOUS queries relied on incomplete tie ordering → explicit workout/exercise tie breakers added without changing semantics.
4. Instrumented test imported `executeSQL` from the wrong package → corrected to Room 3 `androidx.room3.executeSQL`; subsequent instrumented run passed.
5. Design spec originally claimed O(N) despite an intentional deterministic sort → corrected to O(N log N).
6. `BigInteger.TWO` is API 33 while minSdk is 28 → replaced with `BigInteger.valueOf(2L)` in production/test code.
7. History root initially had redundant back navigation despite being top-level → removed; detail retains back-to-History behavior.
8. History selection initially did not survive state recreation → moved to `SavedStateHandle` and added recreation test.
9. Top-level tab replacement risked losing saveable list state → added `rememberSaveableStateHolder`.
10. `BigInteger.longValueExact()` is API 31 while minSdk is 28 → replaced with explicit `Long.MIN_VALUE`/`Long.MAX_VALUE` range validation followed by `toLong()`, preserving overflow-safe null behavior on all supported Android versions.

## 14. CI contract

Android CI runs on PRs and pushes to `main`:
1. JDK/Android/Gradle setup;
2. snapshot committed Room schemas;
3. JVM tests;
4. semantic Room schema verification;
5. upload `gymtracker-room-schema`;
6. enable KVM;
7. instrumented Room/database/migration/Paging tests on emulator API 35;
8. lint;
9. assemble debug APK;
10. upload `gymtracker-debug-apk` (14 days).

Before PR5 can merge:
- exact head containing this updated `PROJECT_CONTEXT.md` must pass every step;
- both artifacts must exist;
- PR must be mergeable and review threads resolved;
- privacy/scope/query/determinism/UX reviews must be complete;
- then mark ready and squash merge;
- verify Issue #5 closes as `completed`;
- verify post-merge main CI + artifacts;
- update this handoff on main with the actual squash/main CI/artifacts and verify the documentation head’s CI too.

## 15. Roadmap

- PR #1 — Android foundation — MERGED.
- PR #2 / Issue #2 — Room local data foundation — MERGED / COMPLETED.
- PR #3 / Issue #3 — Exercises and Routine Editor — MERGED / COMPLETED.
- PR #4 / Issue #4 — Workout Logger — MERGED / COMPLETED.
- PR #5 / Issue #5 — Unlimited History and PR Engine — **IMPLEMENTED; CODE CI #95 GREEN; FINAL HANDOFF CI / MERGE PENDING**.
- PR #6 / Issue #6 — Progress Analytics — **DO NOT START until PR5 is fully closed and the user explicitly continues**.
- PR #7 / Issue #7 — Backup, Restore, CSV Export.
- PR #8 / Issue #8 — V1 UX / reliability hardening.
- Issue #9 — post-V1 Health Connect recovery context.
- Issue #10 — post-V1 Wear OS companion.

## 16. Next action from this handoff

1. Verify real GitHub state first.
2. Finish PR #14 only; do not start PR6.
3. Confirm CI on this exact documentation head is SUCCESS and both artifacts exist.
4. Reconfirm mergeability/reviews/threads, synchronize PR status, mark ready and squash merge.
5. Confirm Issue #5 closed/completed and post-merge main CI/artifacts.
6. Update this file on main with exact final PR5 head, squash commit, CI IDs and artifact IDs; verify that final documentation head is green.
7. Only after PR5 is fully closed may the next chat inspect Issue #6 and design PR6, and only when the user explicitly continues.
