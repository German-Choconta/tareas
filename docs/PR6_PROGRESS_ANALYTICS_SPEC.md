# PR6 — Progress Analytics Specification

Status: design locked before implementation on 2026-08-25 (America/Bogota).

## Product boundary

PR6 implements the **UNDERSTAND** step of `LOG → COMPARE → UNDERSTAND → PROGRESS`.

It is descriptive analytics only. It does not prescribe progression, infer causality, score workout quality, add coaching, or change PR5 record semantics.

`WorkoutSet` remains canonical Room truth. Analytics are derived and recalculable.

Architecture:

`Room raw truth → range query/repository → pure domain analytics → immutable UI state → Compose chart renderer`

No analytics summary/cache becomes canonical persistence truth in PR6.

## Existing PR5 eligibility reused exactly

A set is analytics-eligible only when all PR5 record rules are true:

1. workout is finished (`finishedAt != null`);
2. set is completed (`completedAt != null`);
3. `reps > 0`;
4. `loadGrams > 0`;
5. type is `WORK`, `DROP`, or `FAILURE`.

`WARMUP` and incomplete sets remain visible in raw History but do not participate in analytics. Active workouts are excluded. Exact grams remain exact. RIR does not alter e1RM.

The implementation must call/reuse `PersonalRecordEngine.isEligibleForRecords` and `PersonalRecordEngine.estimatedOneRepMax` instead of copying a second semantic implementation.

## Metric semantics

### Load trend

One point represents one distinct finished workout containing at least one eligible set for the selected exercise.

Value: maximum exact `loadGrams` among eligible sets for that exercise in the workout, combining multiple occurrences of the exercise in the same workout.

No load buckets, smoothing, interpolation of missing sessions, or synthetic zero points.

### Repetition trend

A single line mixing rep counts from unrelated loads would be misleading. PR6 therefore uses an exact-load series.

For one exact selected `loadGrams`, each point is the maximum reps achieved at that exact load in one workout. Sessions that did not use the selected load have no point; they are not represented as zero.

The selectable loads come from observed eligible exact loads in the active range. Default selection is deterministic:

1. choose the exact load represented in the largest number of distinct sessions;
2. if tied, choose the higher exact load.

This keeps the question explicit: **“How are my reps changing at this exact load?”**

### Estimated 1RM / e1RM trend

One point per distinct workout is the maximum PR5 Epley estimate among eligible sets in that workout.

PR5 rules remain unchanged:

- Epley: `load × (1 + reps / 30)`;
- eligible only for 2–10 reps;
- exact comparison uses the existing rational numerator / denominator and `BigInteger` semantics;
- RIR remains outside the formula;
- display rounds with the existing PR5 rule;
- UI must say **Estimated 1RM / e1RM**, never measured `1RM`.

### Volume trend

One point per distinct workout is exercise-session volume:

`Σ(loadGrams × reps)`

The sum combines all eligible sets across every occurrence of the exercise within the workout and uses `BigInteger`/overflow-safe arithmetic.

Volume is descriptive only. It is not a quality score and does not imply that a higher-volume session was a better session.

### Training frequency

A session is one distinct finished workout containing at least one eligible set for the exercise. Multiple occurrences of the same exercise within that workout count once.

PR6 exposes:

- total eligible exercise sessions in the active range;
- calendar-week buckets, Monday-start;
- calendar-month buckets.

For finite custom ranges, zero-count frequency buckets inside the selected range may be materialized because absence is meaningful for frequency. Performance trends never synthesize zero points on dates without a session.

For all-time frequency, the displayed bucket span begins at the first observed session bucket and ends at the last observed session bucket rather than inventing empty years outside the stored history.

## Date and time-zone semantics

UI custom dates are inclusive calendar dates.

Before querying, capture an explicit `ZoneId` and convert a valid custom range `[startDate, endDate]` to half-open epoch bounds:

`[startDate.atStartOfDay(zone), (endDate + 1 day).atStartOfDay(zone))`

This avoids ambiguous `23:59:59.999` logic and keeps DST transitions deterministic.

Rules:

- All time: no artificial start/end bound.
- Same-day custom range: valid.
- `startDate > endDate`: invalid; do not query.
- Empty valid range result: explicit empty UI state.
- Time-zone conversion and week/month bucketing must be pure/testable with an explicit `ZoneId`.

## Query and schema strategy

PR6 starts and should finish on Room schema **v2** unless a concrete query-plan/performance test proves otherwise.

Relevant existing indices:

- `workout(startedAt)`;
- `workout_exercise(exerciseId)`;
- `workout_exercise(workoutId)`;
- unique `workout_exercise(workoutId, position)`;
- `workout_set(workoutExerciseId)`;
- unique `workout_set(workoutExerciseId, position)`.

The analytics DAO path should retrieve only compact PR-style facts and apply exercise/range filtering in SQL. Eligibility remains in the domain layer so PR5 and PR6 cannot silently drift.

Paging remains appropriate for raw History lists. It is not applied mechanically to chart derivation because session-level analytics require deterministic grouping across the selected range.

No schema v3/index/migration may be added for convenience. A schema change requires a measured problematic query, demonstrated plan/benefit, migration cost analysis, migration tests, and historical compatibility proof.

## Long-history presentation

Raw Room history is never truncated for charts.

The domain layer may derive the full selected-range session series and then apply deterministic **presentation-only** sampling before sending a very large series to the renderer. Sampling must not alter stored truth, current-best/PR semantics, or raw History availability.

If sampling is necessary it must:

- preserve chronological order;
- preserve first and last points;
- preserve extrema and record/progression witnesses where applicable;
- be deterministic for shuffled equivalent input;
- be tested independently;
- be documented as display reduction, never data deletion.

Correctness takes priority over a hard visual-point cap: if required witnesses exceed the target, witnesses remain.

The visual x-axis preserves relative calendar spacing instead of assigning an equal ordinal gap to every observed session. Same-day points remain distinct in deterministic order. The temporal x transform is presentation-only and normalized to Vico's supported four-decimal precision; exact dates and metric values remain in immutable UI state and the accessible point-detail surface.

## Chart renderer decision

PR6 uses **Vico 3.2.3** Compose modules (`compose` and `compose-m3`).

Technical reasons:

- Compose-native chart host rather than a View interoperability wrapper;
- Android requirement is minSdk 23; GymTracker is minSdk 28;
- built-in scrolling, zooming, markers, axes, and custom components reduce the amount of gesture/rendering infrastructure GymTracker must own;
- Apache-2.0 license;
- active project;
- Vico 3.2.3 is published against Kotlin 2.3.21, matching GymTracker's current Kotlin toolchain;
- Vico 3.3.x moves to Kotlin 2.4.x, so adopting it in PR6 would force an unrelated toolchain migration.

Alternatives evaluated:

- **KoalaPlot:** Compose-first and flexible, but current 0.x documentation explicitly treats API/binary compatibility as developmental/unstable.
- **MPAndroidChart:** capable chart library but primarily View-based and a poorer architectural fit for a Compose-first screen.
- **Native Compose Canvas:** removes a dependency and provides maximum control, but PR6 would then need to implement/maintain chart scrolling, zooming, hit testing/markers, axes and accessibility semantics itself. That cost/risk outweighs the dependency benefit for this stage.

Vico remains a renderer only. Domain models do not depend on Vico types.

References checked for this decision:

- Android Compose accessibility and semantics: https://developer.android.com/develop/ui/compose/accessibility and https://developer.android.com/develop/ui/compose/accessibility/semantics
- Android Compose graphics: https://developer.android.com/develop/ui/compose/graphics/draw/overview
- Android Compose gestures: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures
- Material 3 navigation guidance: https://developer.android.com/develop/ui/compose/layouts/material
- Vico getting started: https://github.com/patrykandpatrick/vico/blob/master/guide/getting-started.md
- Vico source/releases/license: https://github.com/patrykandpatrick/vico
- Maven Central Vico artifacts: https://central.sonatype.com/search?q=com.patrykandpatrick.vico
- KoalaPlot: https://github.com/KoalaPlot/koalaplot-core
- MPAndroidChart: https://github.com/PhilJay/MPAndroidChart

## Accessibility contract

The visual chart alone is never the only representation of information.

The Progress UI must provide Compose semantics for controls and explicit textual access to:

- metric name and unit;
- what the series represents;
- range;
- exact selected point date/value;
- selected exact load for rep trend;
- frequency bucket mode.

Do not encode meaning only by color. Provide accessible previous/next point navigation or an equivalent semantic exact-value surface. TalkBack must not need to interpret pixels on a Canvas/chart layer.

## Navigation and mobile UX

Keep the current compact top-level destinations:

- Exercises;
- Routines;
- History.

Do not add a fourth bottom-navigation item. Inside a selected exercise's History detail, expose **History / Progress** as peer views.

Progress minimum UI:

- selected exercise context;
- All time / custom range selection;
- metric selector;
- exact-load selector for repetition trend;
- weekly/monthly selector for frequency;
- units and semantic explanation;
- chart;
- exact point detail;
- loading, empty, error, and invalid-range states;
- restoration of relevant selected state.

## Required test coverage

### JVM/domain

- every series/trend;
- all-time/custom/same-day/empty/invalid ranges;
- deterministic ties;
- reversed/shuffled input determinism;
- WARMUP exclusion;
- incomplete exclusion;
- active workout exclusion;
- WORK/DROP/FAILURE inclusion;
- e1RM 2–10 reps and outside range;
- RIR independence;
- exact grams;
- duplicate exercise occurrence in one workout;
- frequency dedupe;
- time-zone/day/week/month boundaries;
- volume overflow;
- sparse, dense, and long history;
- deterministic presentation sampling;
- relative temporal chart spacing and same-day deterministic point ordering.

### Room/instrumented

- real range queries;
- archived exercise history;
- routine-deletion-safe history;
- multi-year synthetic history large enough to reveal obvious query regressions;
- schema v2 integrity;
- existing migration tests stay green;
- index/query behavior where reasonably verifiable.

### UI/state

- range selection/restoration;
- same-day and invalid-range state;
- empty state;
- metric switching;
- exact-load switching;
- frequency bucket switching;
- chart state produced from domain output;
- History / Progress tab selection.

All fixtures are synthetic and non-identifying.

## CI contract

Do not weaken the existing workflow. Final PR head must pass:

- JVM tests;
- semantic Room schema check;
- Room schema artifact upload;
- KVM setup;
- `connectedDebugAndroidTest`;
- lint;
- `assembleDebug`;
- APK artifact upload.

No baseline/suppression may hide a real failure.

## Explicitly out of scope

- PR7 backup / restore / CSV;
- cloud sync;
- accounts/login;
- Health Connect;
- Wear OS;
- AI coaching/recommendations;
- progression prescription engine;
- paid features/ads;
- persisted analytics-as-truth;
- silent PR5 rule changes.
