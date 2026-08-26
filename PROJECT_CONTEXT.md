# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), after Issue #9 / PR #18 merge and successful post-merge validation.

## 0. Repository, safety, and permanent product direction

- Repository: `German-Choconta/tareas`.
- Repository is public.
- GymTracker only. **Never touch `Pulso` / `pulso-finanzas`.**
- Never commit real workout, health, sleep, heart-rate or HRV data; identifying fixtures; credentials/tokens/secrets; private exports/backups; or personal information. Tests/examples/screenshots must be synthetic and non-identifying.
- Product loop: **LOG → COMPARE → UNDERSTAND → PROGRESS**.
- Workout logger principle: **PREVIOUS + TARGET + TODAY**.
- Android-first and offline/local-first.
- `WorkoutSet` remains canonical training truth.
- Room remains canonical truth for active/completed GymTracker workouts.
- PRs, e1RM, volume, trends, charts, CSV, analytics and recovery context remain derived/read-only/recalculable representations.
- Paging is query/presentation only, never a retention limit.
- Do not advance roadmap stages silently. Read the next real GitHub issue before designing it and wait for explicit user continuation before implementation.

## 1. Completed roadmap through Issue #9

### PR #1 — Android foundation
- MERGED.

### PR #11 / Issue #2 — Room local data foundation
- MERGED / COMPLETED.
- Squash `aceaadbcb4a3ea370439556780b3b674e0505350`.
- Final CI `32801537677` — SUCCESS.

### PR #12 / Issue #3 — Exercises and Routine Editor
- MERGED / COMPLETED.
- Squash `4915ee7ef0dda4a1bc4b01076bec3ddbba0d5e33`.
- Post-merge CI `32805215489` — SUCCESS.

### PR #13 / Issue #4 — Workout Logger
- MERGED / COMPLETED.
- Final head `1e68e65a3bec4392583c942da738989384fb451b`.
- Final CI `32810018092` — SUCCESS.
- Squash `12ced7e9f7838d004e2ac4cac17a23f5fa2a8529`.
- Post-merge CI `32810354122` — SUCCESS.

### PR #14 / Issue #5 — Unlimited History and PR Engine
- MERGED / COMPLETED.
- Final head `a0381dac73709108a4935dd63379c39cd5958503`.
- Final CI `32855092000` (#96) — SUCCESS.
- Squash `406b627a8c339523cd5ec121e9aeca9a973cc4ee`.

### PR #15 / Issue #6 — Progress Analytics
- MERGED / COMPLETED.
- Final head `4fa2a1abc01f5dde8c0c0f3e26437fb3a3d348d5`.
- Final CI `32868139154` (#129) — SUCCESS.
- Squash `f4076f6d9d7fd09dc735a758c85f60a9a924a93d`.
- Final docs commit `1905412984ad4b8c04b9937e47844f8b149c4b9f`; docs CI `32871077072` (#131) — SUCCESS.

### PR #16 / Issue #7 — Backup, Restore and CSV Export
- MERGED / COMPLETED.
- Final head `b35f8008a3bb787501095a15730de835621f45c1`.
- Final CI `32878707782` (#150) — SUCCESS.
- Squash `3b949d0127875c115d15a49ad04ae3423836374a`.
- Post-merge CI `32879392178` (#151) — SUCCESS.
- Final docs commit `f0a5ccb4427ec133b3e7c08806eec54c2e481d37`; docs CI `32880182711` (#152) — SUCCESS.

### PR #17 / Issue #8 — V1 UX and Reliability Hardening
- **MERGED / COMPLETED. Core V1 is release-ready under the repository's automated contract.**
- Final PR head `fd217b46ed9bef8b2f1c10a5b37d58dfc0467bce`.
- Final PR CI `32903380126` (#170) — SUCCESS.
- Final PR artifacts: Room schema `9583952491`; debug APK `9584120582`; release APK `9584121122`.
- Squash `feb06cc7459a12178fbd2067e5227c716b89ecd1`.
- Issue #8 closed/completed.
- Post-merge main CI `32904132021` (#171) — SUCCESS; artifacts Room `9584194772`, debug `9584380233`, release `9584380774`.
- Final V1 release-readiness handoff commit `48a4e30ae4dc1c5556c2d301c05e79534fee4258`.
- Documentation-head CI `32904960039` (#172) — SUCCESS; artifacts Room `9584461954`, debug `9584543663`, release `9584544633`.

### PR #18 / Issue #9 — Health Connect recovery context
- **MERGED / COMPLETED.**
- Branch used: `feat/health-connect-recovery-context` from verified main `48a4e30ae4dc1c5556c2d301c05e79534fee4258`.
- Final PR head: `84a4107ff53b56364780ad8c92e54d88f3de5420`.
- Final PR Android CI: `32924890756` (#201) — SUCCESS.
- Final PR contract passed on the exact head: JVM tests, semantic Room schema verification, API 35 connected tests (56 tests), lint, debug APK, minified/resource-shrunk release APK, and all artifact uploads.
- Final PR artifacts: Room schema `9591172894`; debug APK `9591318921`; release APK `9591319691`.
- Final scope/review audit before ready: 17 changed files, GymTracker/docs only; no Pulso paths; no schema files or backup files changed; no schema v3; only the three intended Health Connect read permissions; no real/sensitive data; workout logger files untouched; active workout retains navigation priority; 0 reviews and 0 review threads.
- PR was kept draft until the exact final head passed the complete contract, then marked ready.
- Squash merge used `expected_head_sha=84a4107ff53b56364780ad8c92e54d88f3de5420` and produced `5136ce2a3939c691f8d6ab81f4e102e63ca52481`.
- PR #18 merged/closed on 2026-08-25 America/Bogota (2026-08-26 UTC).
- Issue #9 closed automatically with `state_reason=completed`.
- `main` immediately after merge pointed exactly to `5136ce2a3939c691f8d6ab81f4e102e63ca52481`.
- Post-merge main Android CI `32926429035` (#202) — SUCCESS.
- Post-merge main artifacts: Room schema `9591706046`; debug APK `9591852116`; release APK `9591852485`.
- This `PROJECT_CONTEXT.md` update is the final merged-state handoff for Issue #9; its own documentation-head CI/artifacts must be verified before moving to the next stage.

## 2. Android/toolchain baseline after PR9

- Native Android / Kotlin / Jetpack Compose / Material 3.
- Package `com.germanchoconta.gymtracker`.
- AGP `9.3.0`; Kotlin Compose plugin `2.3.21`; Gradle CI `9.5.0`; JDK `17`.
- compileSdk `37`; targetSdk `36`; minSdk `28`.
- Compose BOM `2026.08.00`; Activity Compose `1.13.0`; Lifecycle `2.11.0`.
- Room `3.0.1`; KSP `2.3.10`; bundled SQLite `2.7.0`.
- Paging `3.5.1`; Room Paging `3.0.1`; Vico `3.2.3`.
- `kotlinx-serialization-json/core` `1.8.1`.
- Health Connect stable client `androidx.health.connect:connect-client:1.1.0`.
- No WorkManager/background-sync dependency was added by PR9.

Architecture rules:
- unidirectional data flow and immutable UI state;
- ViewModels/state holders own screen state;
- Composables do not query Room directly;
- repositories/DAOs remain persistence boundaries;
- Room owns canonical workout truth;
- `SavedStateHandle` / `rememberSaveable` are UI recovery, not canonical workout storage;
- health context must never be silently fused into GymTracker workout truth.

## 3. Room / workout invariants — unchanged by PR9

- DB version remains **2**; schemas v1/v2 remain committed; PR9 created no schema v3.
- Canonical entities remain Exercise, Muscle, ExerciseMuscle, Routine, RoutineExercise, Workout, WorkoutExercise and WorkoutSet.
- Stable String IDs; load truth Long grams; RIR nullable Int tenths; timestamps Long epoch millis; Double never canonical metric truth.
- Exercise → workout history uses RESTRICT/archive flow; routine historical references preserve SET_NULL semantics; workout aggregate children cascade only inside the workout aggregate.
- Routine edits/removals never rewrite started/completed history.

Logger/History/PR/Progress semantics remain exactly PR4–PR6:
- transactional Routine → Workout start, immediate Room autosave, TARGET snapshots, deterministic PREVIOUS modes, set types, rest timer, notes, active-workout recovery and guarded finish;
- unlimited History with deterministic raw/PR chronology;
- PR eligibility remains finished workout + completed set + reps > 0 + load > 0 + type WORK/DROP/FAILURE; WARMUP remains raw-only;
- heaviest load exact grams; reps-at-load exact load; e1RM Epley reps 2–10 with exact comparison; exercise-session volume overflow-safe BigInteger;
- Progress remains derived, with DST-safe calendar range semantics and no synthetic missing-performance zeroes.

## 4. PR7 backup/restore contract — unchanged by PR9

- Portable V1 contains all eight canonical Room tables and exact canonical IDs/grams/RIR/timestamps/null relations.
- Restore remains validated preview + explicit replace-all confirmation + one atomic Room write transaction + equality verification.
- CSV is export-only and never restore input.
- No broad storage permission.
- Health Connect raw/normalized data is **not** in portable backup or CSV.
- PR9 did not alter backup format/version, restore semantics, or Room schema.

## 5. PR8 hardening / CI contract — still mandatory

PR8 accessibility, Material light/dark behavior, 320×640-class small-screen support, loading/error/empty states, recreation safeguards, destructive finish guards and evidenced query improvements remain part of V1.

Every PR/main push must preserve:
1. JVM tests;
2. semantic Room schema verification;
3. Room schema artifact;
4. API 35 connected tests on 320×640-class emulator;
5. lint;
6. debug APK;
7. real minified/resource-shrunk release APK;
8. debug APK artifact;
9. release APK artifact.

Never accept an older SHA as evidence for a newer head.

## 6. Final Issue #9 design contract

Issue #9 goal was optional health/recovery context through Health Connect with explicit user permission. The final implementation intentionally remains narrower than all data Health Connect could expose.

### Health Connect access

- Health Connect is optional and read-only.
- Foreground/on-demand reads only.
- GymTracker does not query Health Connect during normal startup; access occurs only when the recovery route is explicitly opened.
- Permission UI is user-initiated only; no prompt on app startup or workout start.
- Requested reads only:
  - `READ_SLEEP` → `SleepSessionRecord`;
  - `READ_RESTING_HEART_RATE` → `RestingHeartRateRecord`;
  - `READ_HEART_RATE_VARIABILITY` → `HeartRateVariabilityRmssdRecord`.
- No `READ_HEALTH_DATA_IN_BACKGROUND`.
- No `READ_HEALTH_DATA_HISTORY`.
- No generic heart-rate permission.
- No Health Connect write permission.
- No `ExerciseSessionRecord` permission/import.
- No WorkManager/background reads.
- Samsung Health is not a dependency and GymTracker does not promise Samsung HRV/RHR interoperability where current Samsung documentation does not guarantee it.

### Data boundary / privacy

Final PR9 flow:
`Health Connect raw records → minimal raw DTO boundary → deterministic normalized recovery context → read-only UI`.

Canonical training flow remains separate:
`Room Workout / WorkoutExercise / WorkoutSet → GymTracker canonical workout truth`.

Data minimization:
- no Health Connect persistence in Room;
- no schema v3;
- no portable backup/CSV health data;
- no backend/cloud health upload;
- no health values in logs;
- no record IDs, notes, device identifiers or unused payload retained;
- only source package, relevant timestamps/zone offsets/stages and metric values cross the adapter boundary;
- disconnect clears ephemeral context first and revokes GymTracker Health Connect permissions; local context remains cleared even if provider revocation fails;
- PR9 does not delete another app's source records.

`docs/HEALTH_CONNECT_PRIVACY_NOTICE.md`, the in-app permission rationale and `docs/PR9_HEALTH_CONNECT_DESIGN.md` document the same boundary. A future Play production publication using health permissions still requires the corresponding public privacy-policy URL and Play Health Apps/Data Safety declarations; PR9 does not invent a production URL.

### Time/source semantics

- Instant RHR/HRV records use the current `ZoneId` calendar day.
- Day boundaries use `LocalDate.atStartOfDay(zone)` and remain DST-safe across 23-hour/25-hour days.
- Sleep is read from a bounded previous-day-start → next-day-start interval and assigned to the calendar day on which the session ends.
- A sleep record's own `endZoneOffset` wins for end-day assignment; current zone is fallback only.
- Naps/multiple sleep sessions remain separate.
- Multiple Health Connect origins remain separate; no heuristic cross-source deduplication is invented.
- RHR and HRV UI uses the latest record per source for the selected recovery day.
- HRV is explicitly RMSSD in milliseconds and is never conflated with SDNN/other HRV metrics.

### UX/state semantics

- Recovery context is a secondary action from History, not a bottom-navigation redesign.
- Active workout still has screen priority; PR4 logger behavior is unchanged and the logger works completely with zero health permissions.
- Explicit states remain provider unavailable, provider install/update required, no permissions, partial permissions, granted/no records, loaded context, permission changed/revoked during read, and provider read error + retry.
- No aggressive permission loop.
- No diagnosis/prescription language.
- No composite recovery score.
- No automatic change to PREVIOUS/TARGET/TODAY or progression prescription.
- Rationale hooks for Android 13− / Android 14+ remain declared in the manifest.
- Recovery route refresh survives recreation/process restoration by refreshing when that explicitly selected route is composed.

### Test boundary

All PR9 fixtures are synthetic/non-identifying. Coverage includes:
- DST-safe day windows;
- sleep crossing midnight and recorded-end-offset assignment;
- clipped/deterministic sleep-stage duration normalization;
- multiple origins kept separate;
- latest RHR/HRV per origin;
- unavailable / denied / partial / granted-empty / loaded states;
- permission revocation during read;
- recoverable provider error/retry;
- disconnect clears ephemeral context;
- narrow-screen recovery UI + accessibility checks;
- existing PR4–PR8 suites under the same CI contract;
- Health Connect isolated behind a testable interface so CI requires no real provider or real health data.

## 7. Issue #9 closure evidence

Final PR evidence:
- PR #18 final head `84a4107ff53b56364780ad8c92e54d88f3de5420`.
- CI #201 / run `32924890756` — SUCCESS.
- Artifacts: schema `9591172894`, debug `9591318921`, release `9591319691`.
- Reviews: 0.
- Review threads: 0.
- PR was ready only after exact-head green evidence.

Merge evidence:
- squash `5136ce2a3939c691f8d6ab81f4e102e63ca52481` using expected-head protection;
- Issue #9 closed/completed automatically;
- main pointed exactly to the squash immediately after merge.

Post-merge evidence:
- Android CI #202 / run `32926429035` — SUCCESS;
- all required steps passed: JVM, semantic Room schemas, API 35 connected tests, lint, debug build, release build, uploads;
- artifacts: schema `9591706046`, debug `9591852116`, release `9591852485`.

No Issue #10 implementation has started.

## 8. Immediate next action after this handoff commit

1. Verify Android CI on the exact documentation head created by this `PROJECT_CONTEXT.md` update.
2. Verify its Room schema, debug APK and release APK artifacts.
3. Confirm Issue #9 remains closed/completed and PR #18 remains merged.
4. Only after those checks, read Issue #10 directly from GitHub.
5. Do **not** implement Issue #10 in the same stage.
6. Deliver the complete continuation prompt for the actual Issue #10 body currently in GitHub.

## 9. Roadmap boundary

- PR1–PR8 / Issues #2–#8: completed.
- **Issue #9 / PR #18: merged/completed and post-merge validated.**
- The documentation-head CI generated by this final handoff update is the last validation gate for the Issue #9 stage.
- Issue #10 has not been started. Its exact current title/body must be read from GitHub only after the documentation-head validation is green.