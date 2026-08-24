# Data Model Draft

This is a design draft for PR 2. `PROJECT_CONTEXT.md` remains canonical.

Relationships:

```text
Routine 1---* RoutineExercise *---1 Exercise
Workout 1---* WorkoutExercise *---1 Exercise
WorkoutExercise 1---* WorkoutSet
Workout *---0..1 Routine
```

Raw workout set values are canonical. Analytics are computed from them.
