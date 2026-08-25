# PR8 — V1 UX and Reliability Hardening Audit

Date: 2026-08-25 (America/Bogota)

This document records the evidence-based audit for Issue #8. It does not expand GymTracker product scope.

## Verified baseline

- Base `main`: `f0a5ccb4427ec133b3e7c08806eec54c2e481d37`.
- PR7 squash parent: `3b949d0127875c115d15a49ad04ae3423836374a`.
- PR7 documentation-head Android CI #152 / run `32880182711`: SUCCESS.
- PR7 documentation-head artifacts: debug APK `9575588354`; Room schema `9575490321`.
- Room database version remains **2**; schemas v1 and v2 are committed.
- The release variant is already configured with code minification and resource shrinking, but the pre-PR8 CI contract only assembled debug.

## Evidence and decisions

### Accessibility and touch targets

Material/Compose built-ins already provide useful semantics and minimum interactive sizing in many places, so PR8 does not add redundant descriptions everywhere. The audit found concrete issues and hardened them:

- top-level create FABs exposed only a visual `+`; they now expose specific “Crear ejercicio” / “Crear rutina” actions;
- the workout exercise title is explicitly a semantic heading;
- initial app loading and recoverable history errors expose meaningful live status instead of an unlabeled spinner/error-only line;
- critical custom/clickable controls retain Material minimum interactive sizing;
- the workout set editor no longer forces load/reps/RIR into three narrow columns on the 320 dp-wide CI-sized viewport; below 360 dp it stacks the three inputs vertically;
- critical Compose tests opt into the official Compose accessibility-test bridge on the API 35 CI device, covering labels, traversal/semantics, contrast and touch-target checks supported by the framework.

No production accessibility dependency was introduced; the accessibility bridge is `androidTestImplementation` only.

### State restoration

Room remains canonical for workouts and the rest timer. The workout ViewModel recovers the active workout from Room, and the rest timer stores an absolute end timestamp. PR8 does not duplicate those facts in saved UI state.

Ephemeral modal state that is safe to recreate (exercise picker, replace picker, completed-set delete confirmation and set-type menu) now uses `rememberSaveable`. Existing `SavedStateHandle` state in History/Progress remains responsible for selected exercise, detail tab and appropriate progress filters. The PR7 import-preview limitation is unchanged: process death during an import preview can require re-selecting the file, because the decoded replacement dataset is deliberately not copied into UI saved state.

### Destructive actions

Archive and restore already had explicit confirmations. Backup replace already had synchronous double-confirm protection from PR7.

Workout finish now has two additional safeguards without changing historical semantics:

- the ViewModel sets an in-flight `finishing` guard synchronously before launching the finish coroutine, so repeated taps cannot queue competing finish operations;
- the Room update is checked by affected-row count (`WHERE finishedAt IS NULL`), so only the first valid transition can report success.

Pending set/note autosaves are still flushed before `finishedAt` is written.

### Database/query performance

The active-workout hot path contained two evidenced N+1 patterns and both were removed without a schema change:

- `WorkoutRepository.getAggregate()` previously loaded one set list per workout exercise; it now performs one ordered set query for the whole workout and groups the result in memory;
- workout UI hydration previously loaded the current `ExerciseEntity` once per workout exercise; it now batches current exercise rows with a single `IN (...)` query.

The routine editor also resolves exercise names item-by-item, but it is an explicit editor-open path over the already bounded routine template rather than the startup/logger hot path. No benchmark or latency evidence currently justifies widening PR8 further there, so that read pattern is documented rather than changed. Likewise, no new index or schema migration is justified by the audited queries. Room schema **v2** therefore remains unchanged.

PREVIOUS lookup is intentionally still per workout exercise because each exercise can have a different reference mode and routine context; collapsing that logic without evidence would risk changing logger semantics.

### Empty/loading/error states

Exercises, routines, backup and analytics already had meaningful empty/error states. Raw paged history now adds:

- explicit refresh loading;
- explicit empty-detail state;
- recoverable refresh error with a `retry()` action;
- append loading and append-error retry.

These changes do not alter Paging retention semantics or canonical history.

### Light/dark theme

The app already follows `isSystemInDarkTheme()` and uses Material 3 light/dark color schemes. The audited critical UI relies on `MaterialTheme.colorScheme` rather than fixed light-only colors. PR8 preserves that architecture instead of introducing another theme system. Automated accessibility checks run against critical Material content, while the release audit verifies there are no new hard-coded theme-breaking colors.

### Startup and release

Database construction remains an application-context singleton. No new startup framework or Baseline Profile dependency is justified by current evidence; adding one solely because it exists would increase build/runtime surface without a measured startup bottleneck.

The concrete release-readiness gap is fixed in CI: after debug assembly, CI now assembles the real minified/resource-shrunk release variant and uploads `gymtracker-release-apk`. A missing or failing release APK therefore fails the PR contract.

## Automated evidence added in PR8

Synthetic tests cover:

- specific accessible top-level create actions;
- official Compose accessibility checks on critical UI;
- 320×640-class narrow workout layout with reachable load/reps/RIR, complete and delete controls;
- batched multi-exercise aggregate hydration while preserving deterministic set ordering;
- atomic/idempotent workout finish, preserving the first exact `finishedAt`;
- all pre-existing PR4–PR7 persistence, history, analytics, backup/restore and UI tests remain part of the same connected suite.

The CI contract now includes JVM tests, semantic Room schema verification, API 35 connected tests, lint, debug assembly, **release assembly**, and Room/debug/release artifacts.

All test data is synthetic and non-identifying.

## Explicit non-scope

No backend, accounts, cloud sync, Health Connect, Wear OS, AI coaching, monetization, progression prescription, PR/e1RM/volume formula change, PR7 backup-format/restore semantic change, or Room schema v3.