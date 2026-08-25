# PR5 — Unlimited History and PR Engine Specification

This document defines the deterministic product/domain rules for Issue #5 before implementation.

## Product boundary

PR5 moves GymTracker from **LOG → COMPARE** toward **UNDERSTAND** while preserving `WorkoutSet` as canonical truth. History, personal records, e1RM, volume, comparisons and badges are derived and recalculable. PR5 does not persist PR/e1RM/volume truth.

## Raw history

- Historical UI includes only workouts with `finishedAt != null`.
- Raw rows include every stored set in those finished workouts, including incomplete/empty planned rows, because they are part of the recorded session truth.
- Active workouts are never historical PR evidence and never emit definitive historical records.
- Exercise history is paged from Room/Paging 3; there is no fixed 30/90-day or N-session window.
- Order is deterministic: `startedAt DESC`, then `workoutId DESC`, workout-exercise position/id, then set position/id.
- Archived exercises remain readable.
- Deleted routines do not break history: `Workout.title` remains the session context and `routineId` may be null.
- Legacy v1 rows with no TARGET snapshot remain valid history because PR5 derives from workout/set facts rather than current Routine targets.

## PR-eligible sets

A set can participate in PR calculations only when all are true:

1. parent workout is finished;
2. set has `completedAt != null`;
3. `reps > 0`;
4. `loadGrams > 0` for load-, reps-at-load-, e1RM- and volume-based metrics;
5. set type is `WORK`, `DROP`, or `FAILURE`.

`WARMUP` is excluded from PR metrics but remains visible in raw history.

`RIR` is not required. PR5 deliberately does not adjust estimates using RIR because RIR accuracy varies and the stored value may be null; adding RIR to the formula would create a different model than the repetitions-to-fatigue equations validated in the cited literature.

Unilateral exercises use the recorded `loadGrams` exactly as logged. PR5 does not double or otherwise reinterpret unilateral load because body-side semantics are not persisted.

## Deterministic chronology and ties

PR events are evaluated from oldest to newest using:

`startedAt ASC → workoutId ASC → workoutExercise.position ASC → workoutExerciseId ASC → set.position ASC → setId ASC`.

SQLite row order is never relied upon implicitly.

- A PR event requires a **strict improvement** over the prior best.
- Equal values are a tie/match, not a new PR event.
- Multiple strict improvements inside one workout can each be historical events.
- The canonical witness for a current best is the first set/session that reached that value under the deterministic chronology; later ties may be shown as matches but do not replace it.

## Heaviest Load PR

- Compare exact `loadGrams` as integer `Long` values.
- Eligible set types: `WORK`, `DROP`, `FAILURE`.
- `newLoad > previousBestLoad` emits a historical PR event.
- `newLoad == previousBestLoad` is a tie, not a PR event.

## Highest Reps at an Exact Load

- The key is exact `loadGrams`; no bucket/rounding is allowed.
- Each distinct gram value has an independent reps record.
- `reps > priorBestRepsAtSameExactLoad` emits an event.
- Ties do not emit a new event.
- Near loads such as `100000 g` and `100001 g` never share a bucket.

## Estimated 1RM (e1RM)

PR5 uses the Epley repetition equation as a pragmatic, reproducible estimate:

`e1RM = load × (1 + reps / 30)`

Implementation rules:

- Valid repetition range: **2–10 reps**.
- Comparison uses the exact rational numerator `loadGrams × (30 + reps)` with a constant denominator of 30; display rounding never determines PR ordering.
- Domain arithmetic uses overflow-safe integer/BigInteger math; no floating-point value becomes canonical truth.
- Display value is rounded **half-up to the nearest 100 g** (0.1 kg).
- RIR is deliberately not used in PR5.
- Eligible types: `WORK`, `DROP`, `FAILURE`.
- Sets outside 2–10 reps are excluded from e1RM only; they can still produce other PR types.
- It is always labeled **Estimated 1RM / e1RM**, never “1RM”.

Limitations:

- Repetition equations were developed from submaximal/repetitions-to-fatigue testing and are population/exercise dependent.
- Evidence shows prediction error increases as repetition count rises; multiple-repetition prediction work recommends staying at or below about 10 repetitions.
- A non-failure work set can underestimate the lifter’s true maximal capability because PR5 does not infer unused reps from RIR.
- Therefore e1RM is a comparable trend/estimate, not a measured maximum.

Evidence used during design:

- Reynolds JM, Gordon TJ, Robergs RA. *Prediction of one repetition maximum strength from multiple repetition maximum testing and anthropometry.* J Strength Cond Res. 2006. PMID 16937972.
- Mayhew JL et al. *Accuracy of prediction equations for determining one repetition maximum bench press in women before and after resistance training.* J Strength Cond Res. 2008. PMID 18714230.
- Mayhew JL et al. *Validation of the NFL-225 test for predicting 1-RM bench press performance in college football players.* J Sports Med Phys Fitness. 2002. PMID 12094120.

## Volume

PR5 does **not** treat “more volume” as synonymous with a better workout.

The only volume record exposed is **highest exercise-session volume**, defined as the sum of `loadGrams × reps` for PR-eligible sets of the same exercise inside one finished workout. If an exercise appears more than once in the same workout, all eligible occurrences are combined for that exercise/workout session.

- Warm-ups and zero-load rows are excluded.
- Arithmetic is overflow-safe (`BigInteger` in domain logic).
- Strictly higher session volume can emit a volume PR event.
- UI language is descriptive (“Highest session volume”), not evaluative (“best workout”).
- Set volume and whole-workout volume badges are intentionally omitted in PR5 to avoid redundant or misleading gamification.

## PR events vs current best

The engine exposes both concepts:

- **Historical event:** the set/session strictly improved the best that existed at that point in time.
- **Current best:** the highest value after the entire eligible history is processed.

Example: 100 kg → 105 kg → 110 kg creates three historical milestones; 110 kg is the current best.

## Previous-session comparison

PR5 reuses the same semantic source as the workout logger:

- `ANY_WORKOUT`: latest finished earlier workout containing the exercise.
- `SAME_ROUTINE`: latest finished earlier workout with the same routine and exercise.
- same-start-time ties use explicit workout/exercise ordering rather than SQLite incidental order.
- completed sets are compared by set position, matching PR4 behavior.

There must not be a second contradictory previous-session engine in the history feature.

## Paging / performance

- Room 3.0.1 + Paging 3 is used for the raw history stream.
- Paging version: `3.5.1`.
- Room 3 integration: `androidx.room3:room3-paging:3.0.1` plus `PagingSourceDaoReturnTypeConverter`.
- UI uses `LazyColumn`, stable set IDs as keys and paged loading.
- No artificial history retention/window is introduced.
- PR computation is deterministic O(N) over eligible facts, not O(N²); raw list composition remains paged.
- Schema v2 is retained. PR5 does not add persistent PR columns or schema v3 solely for convenience.

## Navigation / UX

With History becoming a third equal-priority top-level destination, Material 3 guidance favors a navigation bar for 3–5 primary destinations on compact devices. PR5 therefore treats **Exercises**, **Routines**, and **History** as primary destinations, while an active workout remains immersive/transient.

History prioritizes:

1. raw session facts;
2. concise current records with explanations;
3. session-to-session comparison;
4. clear e1RM estimation labeling.

No advanced charts/dashboard are added; those belong to PR6.

All interactive controls keep Material minimum touch targets and non-color-only semantics.