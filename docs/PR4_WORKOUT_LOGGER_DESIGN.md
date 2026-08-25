# PR4 Workout Logger — Design before implementation

Date: 2026-08-24 (America/Bogota)

## Product goal

GymTracker remains centered on **LOG → COMPARE → UNDERSTAND → PROGRESS**. The active workout logger must make **PREVIOUS + TARGET + TODAY** understandable at a glance and quick to operate between sets.

This repository is public. All examples and tests must use synthetic, non-identifying data only.

## Official Android guidance reviewed

Current Android Developers guidance was reviewed before implementation for:

- Compose text input, `KeyboardOptions`, numeric/decimal keyboards and IME actions.
- Focus traversal and explicit movement to the next field.
- Compose state saving and `SavedStateHandle` for small UI/navigation state only.
- Process recreation and persistent local storage.
- `LazyColumn` stable keys.
- Compose accessibility semantics and minimum 48dp interactive targets.
- Material 3 navigation: persistent navigation is for stable top-level destinations; an active workout is modeled as a transient/immersive destination instead of a permanent third management tab.
- Room 3.0.1 migrations and `MigrationTestHelper` with committed exported schemas.

## Navigation

The existing Exercises/Routines `PrimaryTabRow` remains the management surface. Starting a routine enters a dedicated active-workout screen. A workout is not a persistent third tab.

On app launch, if Room contains an unfinished workout, the app may offer/resume that active workout directly rather than reconstructing it from transient ViewModel state.

## PREVIOUS + TARGET + TODAY

Each workout exercise is presented as one vertical card/section rather than a horizontally scrolling table.

- **PREVIOUS**: compact reference line for the comparable completed workout. For each Today set position, show the corresponding previous set when available; if counts differ, unmatched sets explicitly show no previous value.
- **TARGET**: stable copied routine target: target sets, rep range, RIR, rest and load increment. This is workout-owned snapshot data.
- **TODAY**: direct editable load, reps, RIR, set type and completion state.

The visual hierarchy prioritizes TODAY input while keeping PREVIOUS and TARGET immediately adjacent.

## Fast entry

- Load uses decimal numeric keyboard and is converted exactly to integer grams.
- Reps uses integer numeric keyboard.
- RIR uses decimal numeric keyboard and is converted exactly to integer tenths.
- IME `Next` advances through load → reps → RIR; `Done` dismisses/clears focus on the last editable field.
- Repeated dialogs are avoided.
- Set completion is a single large action with clear completed/uncompleted state.
- All interactive controls keep at least 48dp targets.

## Room schema decision

PR4 requires schema **v2**. Schema v1 cannot represent a stable TARGET snapshot because `WorkoutExerciseEntity` only references the mutable `RoutineExerciseEntity` and stores no target fields.

The v2 `workout_exercise` row will own copied values:

- targetSetCount
- repMin
- repMax
- targetRirTenths
- restSeconds
- loadIncrementGrams
- previousReferenceMode

The active rest timer uses an absolute end timestamp rather than an incremented counter. Minimal timer recovery state will also be persisted so background/recomposition does not change elapsed time and process/activity recreation can reconstruct remaining time.

Schema v1 remains committed. A v1→v2 migration and migration test are mandatory.

## Start workout transaction

Starting from a routine is atomic:

1. Read the active Routine and ordered RoutineExercise rows.
2. Insert Workout immediately.
3. Insert ordered WorkoutExercise rows with copied target snapshot.
4. Pre-create targetSetCount WorkoutSet rows with stable UUID-compatible String IDs.

Later edits/removals/reorders to Routine/RoutineExercise cannot alter the started workout's targets or ordering.

## Previous resolution

- `ANY_WORKOUT`: latest **finished** workout before current startedAt containing the same exercise.
- `SAME_ROUTINE`: latest **finished** workout before current startedAt with the same routine and exercise.
- The current unfinished workout can never reference itself because queries require `finishedAt IS NOT NULL` and `startedAt < current.startedAt`.
- Previous set matching is positional; a different number of sets is valid and unmatched positions have no previous row.

## Sets and destructive actions

- Completing and uncompleting persist immediately.
- Adding a set appends at the next position transactionally.
- Removing an uncompleted session-created set is immediate.
- Removing a completed set requires explicit confirmation; no completed data is silently destroyed.
- Renumbering uses collision-safe temporary positions when required.
- Reordering sets is not implemented in PR4 because it adds interaction cost without a clear gym-use benefit.

## Exercise changes during a workout

Adding an exercise creates workout-owned rows and does not alter the routine.

Replacing an exercise with no completed sets is allowed. If completed sets exist, replacement must not silently destroy them; the UI requires an explicit destructive confirmation or keeps the completed exercise and adds the replacement separately.

## Notes

- Workout notes update `WorkoutEntity.notes`.
- Exercise-in-workout notes update `WorkoutExerciseEntity.notes`.
- Master `ExerciseEntity.notes` is never overwritten by workout notes.

## Finish

Finishing sets `finishedAt` and clears active timer recovery state. Incomplete/empty sets are not converted into completed history. If meaningful uncompleted input exists, finishing asks for confirmation before leaving it incomplete.

Completed workouts are historical records; later routine edits do not mutate workout-owned snapshot/set rows.

## Autosave and recovery

Room is canonical. Important edits are persisted as they happen. ViewModels reconstruct from Room after recreation.

`SavedStateHandle`/saveable state is limited to small identifiers or UI-only selection/focus hints and is not the workout database.

## Out of scope

No accounts, cloud sync, Health Connect, Wear OS, ads/analytics SDK, advanced PR/e1RM analytics, AI recommendations or PR5 work.
