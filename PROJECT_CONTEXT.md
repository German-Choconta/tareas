# GymTracker — Project Context & Continuity

> **Canonical handoff file. Read this first in every new chat/session before making changes.**
> Last updated: 2026-08-24 (America/Bogota)

## 0. Repository and current status

- Repository: `German-Choconta/tareas` (repurposed for GymTracker with explicit user authorization on 2026-08-24).
- Repository visibility: **public**.
- Never commit personal workout exports, health data, credentials, secrets, or other private user data. GitHub contains only source code, documentation, tests, and synthetic fixtures.
- `pulso-finanzas` is unrelated and must not be touched for GymTracker.
- Current PR: **#1 — GymTracker PR 1: Android foundation**.
- PR branch: `feat/gymtracker-bootstrap` → `main`.
- PR #1 CI status: **GREEN**.
- Verified CI run #3 (`32776997029`) on 2026-08-24:
  - Android 37 SDK install ✅
  - Gradle setup ✅
  - JVM unit tests ✅
  - Android Lint ✅
  - debug APK assembly ✅
  - debug APK artifact upload ✅
- Verified artifact: `gymtracker-debug-apk`, 16,222,379 bytes, retained for 14 days by CI.
- Do not merge PRs automatically unless the user explicitly requests autonomous merging. A green PR should be left ready for review by default.

## 1. Mission

Build a private, fast, Android-first strength-training tracker whose core loop is:

**log → compare → understand → progress**

GymTracker is deliberately not a social network and not a full Hevy clone. Its advantage is:

- unlimited local training history
- frictionless set logging
- explicit **Previous + Target + Today** workout UX
- configurable progression per exercise
- transparent, deterministic recommendations
- user-owned backup/export
- optional recovery and wearable integrations only after the core tracker is excellent

## 2. Product principles

1. **Logging must be frictionless.** A working set should be recordable in a few taps.
2. **Raw training data is canonical.** Recommendations, PRs, e1RM, volume, and trends are derived and recalculable.
3. **Unlimited history.** No artificial 3-month/12-month analytics limits.
4. **Offline-first.** Core functionality works without account, server, subscription, or internet.
5. **User owns the data.** Export/import is a first-class capability.
6. **No fake precision.** RIR, e1RM, plateau detection, and recovery correlations are estimates and must be presented as estimates.
7. **Progression is configurable per exercise.** Rep range, target RIR, rest interval, and load increment may differ by lift.
8. **No mandatory subscription.** V1 has no recurring infrastructure dependency.
9. **Privacy by default.** Data remains on-device unless the user explicitly exports it or enables a future integration.
10. **Evidence-informed, not evidence-theater.** Recommendations must be conservative and explain their rule.
11. **Volume is descriptive, not a quality score.** `load × reps` helps describe training but must never be shown as proof that a workout was “better”.
12. **The workout screen is not an analytics dashboard.** During training, prioritize the next set and remove distractions.

## 3. Research summary and product implications

### Hevy
Useful benchmark patterns:
- previous performance visible during a workout
- fast weight/reps entry
- warm-up/drop/failure set types
- supersets/rest timer
- PR feedback and exercise graphs
- Wear OS support

Opportunity for GymTracker:
- make the next progression target a first-class object
- keep historical analytics unlimited locally
- avoid social/feed complexity
- make the source of “previous” explicit: **last occurrence in any workout** vs **last occurrence in this routine**

### Strong
Main lesson: logging speed and low cognitive load matter more than feature density during a live workout.

### Alpha Progression
Main lesson: progression recommendations can be a primary product feature. GymTracker’s version must remain explainable and user-overridable.

### Training research implications
- RIR is useful but imperfect; store it optionally and never require it to save a set.
- Proximity to failure can matter for hypertrophy, but forcing every set to failure is not necessary for the app’s progression rule.
- Use volume as a descriptive workload metric only.
- e1RM is a trend estimate; preserve raw load/reps so formulas can be changed later.
- V1 uses deterministic double progression rather than an opaque “AI coach”.

Research links and implementation notes live in `docs/RESEARCH_NOTES.md`.

## 4. Technical architecture

### Platform
- Native Android
- Kotlin
- Jetpack Compose + Material 3
- Package: `com.germanchoconta.gymtracker`

### Verified/current toolchain decision
- Android Gradle Plugin: `9.3.0`
- Gradle: `9.5.0` in CI
- JDK: `17`
- compileSdk: `37`
- targetSdk: `36`
- minSdk: `28`
- Compose BOM: `2026.08.00`

Current Compose/AGP stack was verified against official Android documentation on 2026-08-24.

### UI/state architecture
- unidirectional data flow
- immutable UI state
- ViewModel/state holder per feature after persistence lands
- composables do not talk directly to the database
- canonical workout data persists in the data layer; `SavedStateHandle`/saveable UI state stores only small recovery/navigation state

### Persistence — PR #2 decision
Use **Room 3.0.1** with KSP/coroutines.

Important rules:
- Room schema exported and committed to Git
- migration tests required for every schema version change after v1
- do not use Robolectric as the primary Room verification strategy
- prefer Room’s supported Android/device tests; use `BundledSQLiteDriver` where appropriate in JVM/KMP-style database tests if introduced
- DataStore is reserved for small preferences/settings, not training history

### Future integrations
Not part of V1 core:
- Health Connect — issue #9
- Wear OS companion — issue #10
- cloud sync only if a concrete multi-device requirement appears later

## 5. V1 data-model decisions

### Stable identifiers
Use UUID/string stable IDs rather than local autoincrement IDs. This makes export/import and a future optional sync layer safer.

### Numeric storage
- Store load canonically as **integer grams** (`Long`) rather than `Double` to avoid floating-point equality problems.
- Display can later convert to kg or lb without changing stored workout truth.
- Store RIR in an integer representation that supports half-step precision (e.g. tenths: `15 = 1.5 RIR`) while exposing a friendly UI.
- Repetition counts and ordering remain integers.
- Timestamps use a stable instant/epoch representation.

### Muscle modeling
Normalize exercise↔muscle relationships instead of storing secondary muscles as one serialized blob. This enables later per-muscle hard-set/frequency analytics without a painful schema redesign.

### Planned entities

#### Exercise
- id
- name
- equipment
- unilateral flag
- notes
- archived flag
- default progression settings

#### Muscle
- id
- name

#### ExerciseMuscle
- exerciseId
- muscleId
- role/weight: primary or secondary

#### Routine
- id
- name
- position
- notes
- archived flag

#### RoutineExercise
- id
- routineId
- exerciseId
- position
- targetSetCount
- repMin
- repMax
- targetRirTenths nullable
- restSeconds
- loadIncrementGrams
- previousReferenceMode: ANY_WORKOUT | SAME_ROUTINE

#### Workout
- id
- routineId nullable
- title
- startedAt
- finishedAt nullable
- notes

#### WorkoutExercise
- id
- workoutId
- exerciseId
- routineExerciseId nullable
- position
- notes

#### WorkoutSet
- id
- workoutExerciseId
- position
- type: WARMUP | WORK | DROP | FAILURE
- loadGrams
- reps
- rirTenths nullable
- completedAt nullable

### Deletion rules
- Preserve workout history.
- Archive exercises/routines with historical references instead of destructive deletion.
- Avoid cascade behavior that could erase completed workout history.

### Indexing priorities
Index history-critical foreign keys and ordering/time fields, especially:
- workout.startedAt
- workoutExercise.workoutId / exerciseId
- workoutSet.workoutExerciseId
- routineExercise.routineId / exerciseId
- exercise-muscle join keys

Derived metrics such as volume, PR status, and e1RM never replace the raw set rows.

## 6. Core V1 user flows

### A. Start workout
Home → choose routine or empty workout → active workout screen.

### B. Log a set
The exercise surface should emphasize:
1. exercise name
2. target rep range + suggested load
3. previous comparable set/session
4. current editable load/reps/optional RIR
5. complete-set action
6. rest timer state
7. notes/settings behind secondary actions

The user can select how “previous” is resolved:
- last occurrence in **any workout**, or
- last occurrence in the **same routine**.

### C. Finish workout
Show:
- duration
- completed work sets
- PRs
- comparison with previous comparable session
- next-session recommendations
- total volume only as a secondary descriptive metric

### D. Exercise history
Provide:
- raw sessions
- best sets
- load/reps trend
- e1RM trend
- volume trend
- PR timeline
- arbitrary/all-time date ranges

### E. Backup
Settings → Export → versioned portable file.
Settings → Import → integrity validation → preview counts → explicit restore action.
Never silently overwrite the existing database.
CSV export is included for human readability.
Android Auto Backup may be an additional convenience, never the only backup strategy.

## 7. Progression engine V1

Inputs:
- current load
- previous comparable **work** sets
- rep range
- target RIR
- load increment
- recent under-target streak

Outputs:
- `INCREASE_LOAD`
- `KEEP_LOAD`
- `REDUCE_LOAD`
- suggested load
- human-readable reason

Current rule:
- keep load while reps progress within the configured range
- if every target work set reaches the top of the rep range without exceeding effort target, recommend the configured increment
- if the majority of work sets repeatedly fall below the rep floor, recommend maintaining/reducing rather than forcing progression
- user can always override the recommendation

No LLM is required for V1 progression.

## 8. Metrics

### V1
- work-set count
- load × reps volume (descriptive only)
- heaviest load
- best reps at a given load
- e1RM trend
- workout/session frequency
- deterministic PRs
- unlimited per-exercise history

### V1.1
- hard sets by muscle/week
- rolling 4-week trends
- progression velocity
- rule-based plateau flags

### Later
- optional recovery correlations from Health Connect
- readiness context
- personalized models only after sufficient user-specific history exists

## 9. Privacy/security

V1 contains:
- no account
- no analytics SDK
- no ads SDK
- no remote database
- no automatic upload of workout data
- local Room database only
- explicit export/import

Never commit real workout exports or imported health data to this public repository.

## 10. GitHub workflow

1. One roadmap slice per `feat/...` branch.
2. Open PR to `main`.
3. Require Android CI green.
4. Keep this file updated in every PR.
5. PR description must include scope, tests, data-migration impact, and continuation note.
6. No unrelated refactors.
7. Do not auto-merge unless explicitly requested by the user.

### CI definition
Every PR currently runs:
- JVM unit tests
- Android Lint
- debug APK assembly
- upload `gymtracker-debug-apk` artifact for 14 days

Later add:
- Room database/migration tests
- critical Compose UI tests
- release build
- macrobenchmark/baseline profile when the app is mature enough to benefit

## 11. Current implementation state — PR #1

Implemented on `feat/gymtracker-bootstrap`:
- Android project initialized
- Compose Material 3 shell
- sample workout card
- domain training models
- deterministic double-progression engine
- Epley e1RM helper
- unit tests covering increase/keep/reduce/e1RM
- GitHub Actions CI
- Android 37 SDK package resolution fixed (`platforms;android-37.0`)
- tests/lint/APK verified green
- debug APK uploaded as CI artifact
- `docs/RESEARCH_NOTES.md`
- `docs/DATA_MODEL.md`
- canonical `PROJECT_CONTEXT.md`

PR #1 is ready for review/merge. Persistence has intentionally not been introduced yet.

## 12. Backlog/issues

- Issue #2 — **PR 2: Room local data foundation**
- Issue #3 — **PR 3: Exercises and routine editor**
- Issue #4 — **PR 4: Active workout logger**
- Issue #5 — **PR 5: Unlimited history and PR engine**
- Issue #6 — **PR 6: Progress analytics**
- Issue #7 — **PR 7: Backup, restore, and CSV export**
- Issue #8 — **PR 8: V1 UX and reliability hardening**
- Issue #9 — **Post-V1: Health Connect recovery context**
- Issue #10 — **Post-V1: Wear OS workout companion**

## 13. Delivery roadmap

### PR 1 — Android foundation — READY
Acceptance verified:
- unit tests ✅
- lint ✅
- debug APK ✅
- APK artifact ✅
- no secrets/personal data ✅

### PR 2 — Local data foundation
Goal: Room 3.0.1 schema + repositories + database/migration test foundation.
Acceptance:
- persist exercises/routines/workouts/sets
- raw database rows remain source of truth
- exported Room schemas committed
- supported Room tests green
- CI green

### PR 3 — Exercise library and routine editor
Goal: create/edit/archive exercises; create unlimited routines; configure progression per exercise.

### PR 4 — Active workout logger
Goal: fast `Previous + Target + Today` set entry, set types, optional RIR, rest timer, save/resume.

### PR 5 — History and PR engine
Goal: unlimited history, deterministic PR detection, exercise/session comparisons.

### PR 6 — Progress analytics
Goal: all-time/arbitrary-range load, reps, e1RM, volume, frequency charts without a server.

### PR 7 — Backup/import
Goal: versioned portable export, safe restore, CSV export, round-trip tests.

### PR 8 — V1 hardening
Goal: accessibility, TalkBack, 48dp+ touch targets, dark mode, state restoration, destructive-action safeguards, performance, critical UI tests, release readiness.

### V1 definition
A user can install the APK, create exercises/routines, log indefinitely, inspect all historical progress, receive transparent progression suggestions, and safely export/restore data without a subscription.

## 14. Canonical implementation prompts

These prompts exist so a new chat can continue without reconstructing intent.

### PROMPT 01 — Bootstrap
Implement PR 1 for GymTracker using `PROJECT_CONTEXT.md` as the source of truth. Use native Android, AGP 9.3.0, compileSdk 37, targetSdk 36, minSdk 28, Jetpack Compose Material 3 and Compose BOM 2026.08.00. Keep V1 offline-first and dependency-light. Add deterministic double progression with unit tests and CI for unit tests, lint, debug APK assembly and artifact upload. Do not add auth, analytics, cloud, Health Connect, or Wear OS. Update `PROJECT_CONTEXT.md` before opening/completing the PR.

### PROMPT 02 — Room data layer
Implement issue #2 / PR 2 using this file as source of truth. Use **Room 3.0.1**, KSP/coroutines, stable UUID IDs, integer grams for load, half-step-capable integer RIR representation, normalized exercise↔muscle relations, and explicit indexes for history-critical queries. Persist Exercise, Muscle, ExerciseMuscle, Routine, RoutineExercise, Workout, WorkoutExercise, and WorkoutSet. Preserve raw set data as canonical. Archive historical entities instead of cascading away workout history. Export and commit Room schemas. Use supported Room Android/device or BundledSQLiteDriver-based tests rather than Robolectric as the primary database strategy. Add migration-test infrastructure. Do not add cloud/auth/Health Connect/Wear OS. Update `PROJECT_CONTEXT.md` before opening the PR.

### PROMPT 03 — Exercises and routines
Implement issue #3 / PR 3. Build unlimited exercise and routine management. Include search, custom exercises, normalized muscle assignment, archive instead of destructive deletion when history exists, ordered routine exercises, per-exercise rep range, target RIR, rest seconds, load increment, and previous-reference mode (`ANY_WORKOUT` / `SAME_ROUTINE`). Optimize for one-handed Android use and minimum 48dp interactive targets. Update tests and `PROJECT_CONTEXT.md`.

### PROMPT 04 — Active workout
Implement issue #4 / PR 4. Build the core live workout experience around **Previous + Target + Today**. Show the selected previous-reference source explicitly. Provide fast editing of load, reps, optional RIR; warm-up/work/drop/failure set types; configurable rest timer; and robust save/resume. Persist canonical workout/set data in Room; save only small UI/navigation state in `SavedStateHandle`/saveable state. Add tests for save/resume, process recreation where practical, and edge cases. Update `PROJECT_CONTEXT.md`.

### PROMPT 05 — History and PRs
Implement issue #5 / PR 5. Add unlimited per-exercise history and deterministic PR detection. Preserve raw history and calculate e1RM on demand. Support heaviest load, highest reps at a load, estimated 1RM, and useful session/set volume records while labeling estimates clearly. Update tests and `PROJECT_CONTEXT.md`.

### PROMPT 06 — Analytics
Implement issue #6 / PR 6. Add all-time and arbitrary-date progress charts: load, reps, e1RM, descriptive volume, and training frequency. Do not turn volume into a workout-quality score. Optimize Room queries for years of local history and add realistic performance/query tests. Update `PROJECT_CONTEXT.md`.

### PROMPT 07 — Backup
Implement issue #7 / PR 7. Add a versioned export/import format with schema version, generated timestamp, app version, and integrity validation. Include CSV workout export. Import must preview counts and never silently overwrite existing data. Add round-trip and corrupt/incompatible-file tests. Evaluate Android Auto Backup only as an additional convenience. Update `PROJECT_CONTEXT.md`.

### PROMPT 08 — V1 hardening
Implement issue #8 / PR 8. Audit accessibility, TalkBack labels, minimum touch targets, light/dark mode, empty/loading/error states, state restoration, destructive actions, database/query performance, startup, and release build. Fix issues without expanding V1 scope. Add critical Compose UI tests. Update `PROJECT_CONTEXT.md` with V1 release readiness.

## 15. Deferred prompts

### Health Connect — issue #9
Only after V1. Re-check current Health Connect permissions/policies at implementation time. Read sleep/HRV/resting heart rate only with explicit permission. Workout logging must remain fully usable without health permissions. Recovery information contextualizes trends and must not act as a medical diagnosis or automatically change training load from a single-day score.

### Wear OS — issue #10
Only after phone logging is stable. Wrist flow should prioritize current exercise/set, previous/target summary, load/reps confirmation, optional RIR, complete set, and rest timer. Do not replicate the entire phone UI on the watch.

## 16. Definition of done for every PR

- scope matches one roadmap slice
- no unrelated refactors
- CI green
- new business logic tested
- no secrets or personal data committed
- no hidden destructive schema changes
- `PROJECT_CONTEXT.md` updated
- PR description explains scope, validation, migration impact, and next action

## 17. Next action

**Current gate: PR #1 is green and ready for merge.**

After PR #1 is merged into `main`:
1. create `feat/room-data-foundation` from updated `main`
2. implement issue #2 / PROMPT 02
3. run full CI + Room database tests
4. update this file with actual schema/test results
5. open PR #2 ready for review
