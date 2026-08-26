# GymTracker

Android-first, offline-first strength training log focused on transparent progressive overload, unlimited local history, and user-owned data.

The canonical product and engineering handoff is [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

## Current stage

The V1 feature set and the Post-V1 integrated release-readiness gate are complete. Issue #23 is **CLOSED / completed** and PR #24 is **CLOSED / MERGED**.

GymTracker is now a **code-level release candidate** at **1.0.0-rc1**. The post-merge gate passed on squash commit `0122cf0fb557e10de51e6e3fbf0cf75b15d74957` in Android CI #230 / run `33016383780`.

The validated product loop remains:

**LOG → COMPARE → UNDERSTAND → PROGRESS**

with the workout logger centered on:

**PREVIOUS + TARGET + TODAY**

No new feature scope is active. A `v1.0.0-rc1` tag/GitHub prerelease is a separate checkpoint and requires explicit approval.

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
