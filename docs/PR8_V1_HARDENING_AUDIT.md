# PR8 — V1 UX and Reliability Hardening Audit

Date: 2026-08-25 (America/Bogota)

This document records the evidence-based audit that starts Issue #8. It does not expand GymTracker product scope.

## Verified baseline

- Base `main`: `f0a5ccb4427ec133b3e7c08806eec54c2e481d37`.
- PR7 squash parent: `3b949d0127875c115d15a49ad04ae3423836374a`.
- PR7 documentation-head Android CI #152 / run `32880182711`: SUCCESS.
- Room database version remains **2**; schemas v1 and v2 are committed.
- Release build is configured with code minification and resource shrinking, but the pre-PR8 CI contract only assembles debug.

## Evidence and decisions

### Accessibility and touch targets

Material/Compose built-ins already provide useful semantics and minimum interactive sizing in many places, so PR8 will not add redundant descriptions everywhere. The audit found a small number of concrete issues:

- top-level create FABs render a raw `+` rather than exposing a specific create action;
- major long-form sections are visually headings but not marked as accessibility headings;
- some critical changing status/error content can be announced more clearly;
- critical custom/clickable rows must retain at least a 48 dp interactive target;
- the workout set editor places load/reps/RIR in three columns even on the 320 dp-wide CI viewport, which is unnecessarily cramped.

### State restoration

Room remains canonical for workouts and the rest timer. The workout ViewModel already recovers the active workout from Room, and the rest timer stores an absolute end timestamp. PR8 will not duplicate these facts in saved UI state.

Ephemeral modal state that is safe to recreate (exercise picker, replace picker, completed-set delete confirmation, set-type menu) currently uses `remember`; it can use `rememberSaveable` without changing canonical data. The PR7 import-preview limitation remains unchanged: process death can require re-selecting the file.

### Destructive actions

Archive and restore already have explicit confirmations. Backup replace already has synchronous double-confirm protection from PR7. The workout finish path flushes autosaves before mutation, but the UI state does not currently gate repeated finish confirmation taps. PR8 will add an in-flight finishing guard without changing finish semantics.

### Database/query performance

The active-workout load path contains evidenced N+1 reads:

- `WorkoutRepository.getAggregate()` loads workout exercises and then runs one set query per exercise;
- workout UI hydration looks up the current `ExerciseEntity` once per workout exercise;
- routine-editor hydration looks up exercise names one by one.

PR8 will replace only these evidenced loops with batched Room queries. No index/schema migration is justified by current evidence, so schema v2 will remain unchanged.

### Empty/loading/error states

Exercises, routines, backup and analytics already contain meaningful empty/error states. Raw paged history has an error message but no retry action and does not expose an equally clear refresh loading state. PR8 will harden that flow without changing Paging retention semantics.

### Light/dark theme

The app already follows `isSystemInDarkTheme()` and uses Material 3 color schemes. The audited critical UI uses `MaterialTheme.colorScheme` rather than fixed light-only colors. PR8 will preserve that architecture and add regression coverage rather than introduce a new theme system.

### Startup/release

Database construction is an application-context singleton. No new startup framework or Baseline Profile dependency is justified by current evidence. The concrete release-readiness gap is CI: the real minified/resource-shrunk release variant is not assembled. PR8 will add the release build to CI and publish its APK artifact if produced.

## Planned automated evidence

- Compose semantics tests for specific create actions and important state semantics.
- Narrow-width workout-set layout regression coverage.
- Workout finish in-flight guard coverage.
- Existing active-workout/rest-timer persistence coverage retained and strengthened where useful.
- Batched aggregate hydration persistence test with synthetic multi-exercise data.
- History paging error/retry UI coverage where deterministic.
- CI debug + connected tests + lint + **release** assembly.

All test data remains synthetic and non-identifying.

## Explicit non-scope

No backend, accounts, cloud sync, Health Connect, Wear OS, AI coaching, monetization, progression prescription, PR/e1RM/volume formula change, PR7 backup-format/restore semantic change, or Room schema v3.