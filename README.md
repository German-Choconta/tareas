# GymTracker

Android-first, offline-first strength training log focused on transparent progressive overload, unlimited local history, and user-owned data.

The canonical product and engineering handoff is [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

## Current stage

The V1 feature set is implemented. Post-V1 work is now focused on **release readiness and integrated acceptance** in Issue #23, not on expanding feature scope.

The acceptance goal is to prove the complete product loop:

**LOG → COMPARE → UNDERSTAND → PROGRESS**

while keeping the workout logger centered on:

**PREVIOUS + TARGET + TODAY**

## Implemented product

- exercise and routine management with explicit training targets;
- active workout logging with immediate Room autosave, recovery, notes, set types, rest timer, and explicit finish;
- deterministic PREVIOUS semantics and unlimited finished workout history;
- personal records and canonical Epley estimated 1RM;
- progress analytics for load, reps-at-load, e1RM, volume, and frequency;
- portable JSON backup/restore plus CSV export;
- optional, read-only Health Connect recovery context;
- a wrist-first Wear OS workout companion that remains phone-canonical;
- deterministic, explainable progression recommendations with explicit Apply suggested load.

## Architecture and privacy

Phone Room is the only canonical workout/history truth. The database remains schema version 2 with committed schemas v1 and v2 only.

Progression recommendations, analytics, PRs, Health Connect context, and Wear delivery state do not become a second canonical history store. Health Connect does not control prescription, and Wear does not become a progression engine.

The repository is public. Tests and fixtures use synthetic, non-identifying data only. Real workout/health data, private exports/backups, credentials, signing keys, keystores, passwords, and tokens must never be committed.

## Release candidate

Release-candidate version: **1.0.0-rc1**.

- phone: application ID `com.germanchoconta.gymtracker`, versionCode 1;
- Wear: same application ID, versionCode 10001;
- compile SDK 37, target SDK 36;
- release builds use minification and resource shrinking.

CI intentionally does **not** contain production signing material. Release APK/AAB outputs produced by GitHub Actions are unsigned build evidence and are not distribution-ready until signed through an approved external release process.

See [docs/ISSUE23_RELEASE_READINESS.md](docs/ISSUE23_RELEASE_READINESS.md) for the acceptance matrix and release procedure boundary.
