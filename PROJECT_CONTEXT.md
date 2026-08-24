# GymTracker — Project Context & Continuity

> Canonical handoff file. Read this first in every new chat/session before making changes.
> Last updated: 2026-08-24 (America/Bogota)

## 1. Mission

Build a private, fast, Android-first strength-training tracker whose core loop is:

**log → compare → understand → progress**

The product is intentionally not a social network and not a clone of Hevy. Its advantage is unlimited local history, configurable progression per exercise, transparent analytics, and later optional recovery integrations.

## 2. Product principles

1. **Logging must be frictionless.** A set should be recordable in a few taps.
2. **Raw training data is the source of truth.** Recommendations are derived and reversible.
3. **Unlimited history.** No artificial 3-month/12-month analytics limits.
4. **Offline-first.** Core functionality must work without an account, server, or internet connection.
5. **User owns the data.** Export/import is a first-class feature, not an afterthought.
6. **No fake precision.** RIR, e1RM and recovery scores are estimates; the UI must label them as such.
7. **Progression is configurable per exercise.** Different rep ranges and load increments are valid for different lifts.
8. **No mandatory subscription.** V1 must have no recurring infrastructure cost.
9. **Privacy by default.** Training data stays on-device unless the user explicitly exports or enables a future sync feature.
10. **Evidence-informed, not evidence-theater.** Recommendations should be explainable and conservative.

## 3. Competitive research summary

### Hevy
Strong current benchmark for logging UX. Useful patterns to match or beat:
- previous-set performance visible during training
- quick set logging
- warm-up/drop/failure set types
- supersets and rest timer
- PR feedback
- progress graphs
- broad exercise library
- Wear OS support

Opportunity: users still ask for more explicit target reps/current target progression, and premium gates exist around advanced/history features. We should make the progression target itself a first-class object.

### Strong
Known for simple, polished logging. The design lesson is that speed and low cognitive load beat feature density during a live workout.

### Alpha Progression
The key lesson is not its exact implementation but the product idea: progression suggestions can be a primary feature. Our version must show the rule used and allow the user to override it.

### Community feedback themes
Repeated themes in 2024–2026 workout-tracker discussions:
- fast logging matters more than social features
- seeing last session instantly is highly valued
- users dislike routine limits/paywalls
- users want target reps/load, not only a historical log
- simple trackers retain users when they do not interfere with the workout

## 3.1 Competitive benchmark matrix

| Product | Strength to learn from | Gap/opportunity for GymTracker |
|---|---|---|
| Hevy | polished logging, previous sets, timers, Wear OS, broad history/PR UX | premium history limits; social layer is irrelevant to our core goal |
| Strong | exceptionally simple logger, export, charts | progression guidance is not the main product |
| Alpha Progression | progression-first positioning | we can make the logic more transparent and locally owned |
| MacroFactor Workouts | explicit targets, RIR, smart progression, modern 2026 UX | adaptive logic can be opaque/confusing; subscription model |
| FitNotes-style minimal trackers | low friction and local simplicity | usually weaker progression intelligence and polished analytics |

**Differentiator:** show **Previous + Target + Today** together, keep the rule transparent, and retain unlimited local history.

## 4. Evidence-informed training logic

### RIR
RIR can be useful for load prescription and proximity-to-failure tracking, but accuracy changes with load, experience and context. Therefore:
- store RIR optionally per set
- never require RIR to save a set
- do not treat RIR as objective truth
- use RIR as one input in recommendations, not the only input

### Progressive overload
V1 uses a transparent **double-progression** engine:
- each exercise can have a rep floor and rep ceiling
- keep load while reps progress inside the range
- when all target work sets reach the top of the range at/above target RIR, recommend the configured load increment
- if the majority of work sets repeatedly fall below the rep floor, suggest maintaining or reducing load instead of forcing progression
- user can always override the recommendation

This is an app rule, not a medical or physiological claim.

### e1RM
Estimated 1RM is a trend metric, never a replacement for raw set history. Store raw load/reps permanently; derived e1RM can always be recalculated if the formula changes.

## 5. Technical architecture

### Platform
- Native Android
- Kotlin
- Jetpack Compose + Material 3
- Working package: `com.germanchoconta.gymtracker`

### Current toolchain decision
- Android Gradle Plugin: 9.3.0
- Gradle: 9.5.0 in CI
- JDK: 17
- compileSdk: 37
- targetSdk: 36
- minSdk: 28
- Compose BOM: 2026.08.00

Rationale: current stable Compose 1.12 requires compileSdk 37 and AGP 9+. AGP 9 uses built-in Kotlin support. Avoid KMP for V1; Android-first keeps complexity down.

### State/UI architecture
- unidirectional data flow
- immutable UI state
- ViewModel/state holder per feature once persistence is introduced
- no database calls directly from composables

### Persistence
Planned for PR #2:
- Room 3.x over SQLite
- Room schema exported and committed to Git
- migration tests required for every schema change after v1
- DataStore only for small preferences/settings

### Future integrations
Not part of V1 core:
- Health Connect for optional health/recovery import
- Wear OS companion for set logging/rest timer
- cloud sync only if a concrete multi-device need appears

## 6. V1 data model

Planned entities:

### Exercise
- id
- name
- primaryMuscle
- secondaryMuscles
- equipment
- unilateral flag
- notes
- archived flag
- default progression profile

### Routine
- id
- name
- position
- notes

### RoutineExercise
- routineId
- exerciseId
- position
- targetSetCount
- repMin
- repMax
- targetRir
- restSeconds
- loadIncrementKg

### Workout
- id
- routineId nullable
- title
- startedAt
- finishedAt nullable
- notes

### WorkoutExercise
- id
- workoutId
- exerciseId
- position
- notes

### WorkoutSet
- id
- workoutExerciseId
- position
- type: WARMUP | WORK | DROP | FAILURE
- loadKg
- reps
- rir nullable
- completedAt

Important: derived stats such as volume/e1RM/PR status should not replace raw set data.

## 7. V1 user flows

### A. Start workout
Home → choose routine or empty workout → active workout screen.

### B. Log a set
Exercise card shows:
- previous comparable session beside current entry
- target rep range
- suggested load
- editable load / reps / optional RIR
- complete-set action
- rest timer begins immediately after completion if enabled

### C. Finish workout
Show:
- duration
- work sets
- total volume (secondary metric, not a score)
- PRs
- changes versus previous comparable session
- recommendations for next session

### D. Exercise history
Tabs/sections:
- raw sessions
- best sets
- e1RM trend
- load/reps trend
- volume trend
- PR timeline

### E. Backup
Settings → Export → portable versioned file.
Settings → Import → validate schema + preview + restore.
Never silently overwrite an existing local database.

## 8. Progression engine V1

Inputs:
- current load
- previous comparable work sets
- rep range
- target RIR
- load increment
- recent under-target streak

Outputs:
- INCREASE_LOAD / KEEP_LOAD / REDUCE_LOAD
- suggested load
- human-readable reason

Rules must remain deterministic and unit-tested. No LLM is required for V1 progression.

## 9. Metrics

V1:
- total work sets
- load × reps volume
- best load
- best reps at a load
- e1RM trend
- session frequency
- PRs
- per-exercise history

V1.1:
- hard sets by muscle/week
- rolling 4-week trend
- progression velocity
- plateaus (rule-based)

Later:
- recovery correlations from Health Connect
- session readiness context
- user-specific models only after enough data exists

## 10. Privacy / security

V1 has:
- no account
- no analytics SDK
- no ads SDK
- no remote database
- no training-data upload
- local database only
- explicit export/import

Do not commit personal training exports to GitHub. GitHub contains source code, tests and synthetic fixtures only.

## 11. GitHub workflow

Repository: **`German-Choconta/tareas`** (repurposed with explicit user approval on 2026-08-24).

Repository visibility is currently **public**. Source code, tests and synthetic fixtures may be committed; never commit personal workout exports or health data.

Workflow:
1. develop each roadmap slice on a dedicated `feat/...` branch
2. open a PR to `main`
3. require Android CI green
4. keep this file updated in every development PR
5. every PR description includes: scope, tests, data migration impact, continuation note
6. do not merge automatically unless explicitly requested

## 12. CI definition

Every PR should run:
- JVM unit tests
- Android lint
- debug APK assembly

Later add:
- Room migration tests
- Compose UI tests for critical workout flow
- release build
- baseline profile / macrobenchmark as project matures

## 13. Current implementation state

PR 1 bootstrap is in progress on branch `feat/gymtracker-bootstrap` in `German-Choconta/tareas`:
- repository initialized for GymTracker
- Android project skeleton pushed
- Compose Material 3 shell pushed
- sample workout card pushed
- domain training models pushed
- deterministic double-progression engine pushed
- unit tests for increase/keep/reduce + Epley e1RM pushed
- GitHub Actions Android CI workflow pushed
- research/data-model docs pushed

Next gate: open PR 1, run CI, fix any failures, then update this section with the verified result.

## 14. Delivery roadmap

### PR 1 — Bootstrap Android foundation
Goal: project builds, CI passes, initial shell + progression tests.
Acceptance:
- `gradle testDebugUnitTest`
- `gradle lintDebug`
- `gradle assembleDebug`
- no secrets
- `PROJECT_CONTEXT.md` included

### PR 2 — Local data foundation
Goal: Room 3 schema + repositories + migration/testing foundation.
Acceptance:
- persist exercises/routines/workouts/sets
- database is source of truth
- schemas committed
- DAO/repository tests

### PR 3 — Exercise library and routine editor
Goal: create/edit/archive exercises; create unlimited routines.

### PR 4 — Active workout logger
Goal: fast set entry, previous session, set types, RIR, rest timer.

### PR 5 — History and PR engine
Goal: unlimited exercise history, PR detection, session comparisons.

### PR 6 — Progress analytics
Goal: charts for load/reps/e1RM/volume/frequency and date filters with no artificial time limit.

### PR 7 — Backup/import
Goal: versioned portable export + safe restore + CSV export.

### PR 8 — UX hardening
Goal: accessibility, empty states, destructive-action safeguards, performance and offline edge cases.

### V1 release
A user can install the APK, define routines, log indefinitely, inspect all historical progress, receive transparent progression suggestions and back up data without any subscription.

## 15. Execution prompts

These are the canonical prompts to use for each implementation step. They exist so a new chat can continue without reconstructing intent.

### PROMPT 01 — Bootstrap
Implement PR 1 for GymTracker using this `PROJECT_CONTEXT.md` as the source of truth. Use native Android, AGP 9.3.0, compileSdk 37, targetSdk 36, minSdk 28, Jetpack Compose with Material 3 and the Compose 2026.08.00 BOM. Keep V1 offline-first and dependency-light. Add the current deterministic double-progression domain engine with unit tests. Add GitHub Actions for unit tests, lint and debug APK assembly. Do not add auth, analytics, cloud, Health Connect or Wear OS yet. Update `PROJECT_CONTEXT.md` with the exact completed state before opening the PR.

### PROMPT 02 — Room data layer
Implement PR 2. Add Room 3.x + KSP and persist Exercise, Routine, RoutineExercise, Workout, WorkoutExercise and WorkoutSet. Keep raw set data canonical; computed metrics must remain derived. Use repository boundaries and Flow/suspend DAO APIs. Export and commit Room schemas. Add database/repository tests and the migration-test foundation. Do not build screens beyond what is needed to verify the data layer. Update `PROJECT_CONTEXT.md` before opening the PR.

### PROMPT 03 — Exercises and routines
Implement PR 3. Build unlimited exercise and routine management. Include search, custom exercises, archive instead of destructive deletion when history exists, ordered routine exercises, per-exercise rep range, target RIR, rest seconds and load increment. Optimize for one-handed Android use. Update `PROJECT_CONTEXT.md` and tests.

### PROMPT 04 — Active workout
Implement PR 4. Build the core live workout experience. Each exercise must show the previous comparable session beside current values. Provide fast editing of load, reps and optional RIR; support warm-up/work/drop/failure set types; start a configurable rest timer after set completion; preserve an in-progress workout across app recreation/process death where practical. Keep interaction count low. Add tests for save/resume and edge cases. Update `PROJECT_CONTEXT.md`.

### PROMPT 05 — History and PRs
Implement PR 5. Add unlimited per-exercise history and deterministic PR detection. Preserve raw history and calculate derived e1RM on demand. Support PR types including heaviest load, highest reps at a load, estimated 1RM and best volume set/session where meaningful. Clearly label estimates. Update tests and `PROJECT_CONTEXT.md`.

### PROMPT 06 — Analytics
Implement PR 6. Add progress charts and filters for all-time and arbitrary date ranges: load, reps, e1RM, volume and training frequency. Avoid misleading aggregate scores. Make charts usable with years of data and no server. Add query/performance tests for realistic history sizes. Update `PROJECT_CONTEXT.md`.

### PROMPT 07 — Backup
Implement PR 7. Add a versioned export/import format with schema version, generated timestamp, application version and integrity validation. Include CSV export for human-readable workouts. Import must preview counts and never silently overwrite existing data. Add round-trip tests. Update `PROJECT_CONTEXT.md`.

### PROMPT 08 — V1 hardening
Implement PR 8. Audit accessibility, TalkBack labels, touch targets, dark mode, empty/loading/error states, orientation/process recreation, destructive actions, database performance, app startup and release build. Fix issues without expanding V1 scope. Add critical UI tests. Update `PROJECT_CONTEXT.md`.

## 16. Deferred prompts

### Health Connect
Only after V1. Read sleep/HRV/resting heart rate only with explicit permission. Never block workout logging when permissions/data are absent. Recovery data may contextualize trends but must not automatically reduce or increase prescribed load from a single-day score.

### Wear OS
Only after mobile logging is stable. Wrist flow should prioritize: current exercise, current set, reps/load confirmation, complete set, rest timer.

## 17. Definition of done for every PR

- scope matches one roadmap slice
- no unrelated refactors
- CI green
- new business logic unit-tested
- no secrets or personal data committed
- no hidden breaking schema changes
- `PROJECT_CONTEXT.md` updated
- PR description explains what changed and what remains next

## 18. Next action

Finish PR 1 on `feat/gymtracker-bootstrap`: ensure all bootstrap files and this context file are committed, open the PR to `main`, inspect Android CI, fix failures until green, and leave the PR ready for user review. After PR 1 is complete, begin PR 2 (Room data layer).
