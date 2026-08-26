# Issue #21 — Deterministic progression recommendations

## Scope

Phone-only active-workout guidance. Room remains canonical workout/history truth. Recommendations are derived and recalculated; they are never persisted as canonical history or routine configuration. Wear OS, Health Connect prescription, cloud sync, AI coaching, schema v3, and automatic routine mutation are out of scope.

## Canonical units and evidence

- load: integer grams (`Long`)
- RIR: integer tenths (`Int?`)
- evidence: completed `WORK` sets from finished workouts only
- comparison mode: existing `ANY_WORKOUT` / `SAME_ROUTINE`
- comparison unit: same exercise + same set position
- deterministic order: newest `startedAt`, then workout id, then set id
- current active workout is never evidence
- legacy workout exercises without a complete TARGET snapshot produce no recommendation

## Per-set decision rule

For a current WORK set, take the newest comparable observation for that set position, with at most one observation per prior workout.

### No baseline

If no comparable completed WORK observation exists, return `NO_BASELINE`. No load or reps are invented.

### Zero/bodyweight baseline

If the newest comparable set has `loadGrams == 0`, return `HOLD_LOAD` with rep guidance only. Never manufacture a positive external load.

### Increase

If the newest observation reaches or exceeds `repMax`:

1. if target RIR exists and actual RIR exists but actual RIR is lower than target RIR, hold the load because the set was harder than target;
2. otherwise increase by the exact configured `loadIncrementGrams` using checked integer arithmetic;
3. if checked addition overflows, return `REVIEW` rather than wrap or clamp silently.

Missing actual RIR does not block an increase. The rationale explicitly states that the decision is reps-based when effort was not recorded.

### Hold / add reps

If newest reps are within `[repMin, repMax)`, hold the same load and suggest `min(repMax, reps + 1)` as a non-canonical next-rep aim.

If newest reps are below `repMin`, do not reduce from one session.

### Reduce

A reduction requires two different consecutive comparable finished workouts for the same set position where:

- both observations are below `repMin`;
- both used the exact same positive `loadGrams`.

Then reduce by one configured increment, floored at zero.

If both are below range but their loads differ, return `REVIEW`: a deliberate load change must not be interpreted as repeated failure.

## User-control contract

The UI may expose `Apply suggested load`. Applying only updates the active TODAY set load through the existing canonical autosave path. It never writes reps, RIR, completion, history, or routine targets. Every recommendation can be ignored or manually overridden.

## e1RM contract

Issue #21 does not own a second Epley implementation. PR5 `PersonalRecordEngine.estimatedOneRepMax` remains the sole canonical e1RM implementation.

## Scientific boundary

This is a transparent conservative double-progression policy, not a claim of physiological optimality. Progressive resistance is the general principle; individual response varies. RIR is useful contextual information but is subjective, so it is an optional guardrail rather than canonical truth.
