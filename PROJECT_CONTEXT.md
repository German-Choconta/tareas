# GymTracker — Project Context & Continuity

> Canonical handoff. Read this first in every GymTracker session, then verify everything directly in GitHub. **GitHub is the source of truth if this file is stale.**
> Last updated: 2026-08-25 (America/Bogota), during Issue #9 / PR #18 implementation.

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
- Do not advance roadmap stages silently. Do not start Issue #10 during Issue #9.

## 1. Completed roadmap through core V1

### PR #1 — Android foundation
- MERGED.

### PR #11 / Issue #2 — Room local data foundation
- MERGED / COMPLETED.
- Squash: `aceaadbcb4a3ea370439556780b3b674e0505350`.
- Final CI `32801537677` — SUCCESS.

### PR #12 / Issue #3 — Exercises and Routine Editor
- MERGED / COMPLETED.
- Squash: `4915ee7ef0dda4a1bc4b01076bec3ddbba0d5e33`.
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
- Issue #9 work verified that `48a4e30...` was the real `main` head and a direct descendant of PR8 squash before branching.

## 2. Android/toolchain baseline entering PR9

- Native Android / Kotlin / Jetpack Compose / Material 3.
- Package `com.germanchoconta.gymtracker`.
- AGP `9.3.0`; Kotlin Compose plugin `2.3.21`; Gradle CI `9.5.0`; JDK `17`.
- compileSdk `37`; targetSdk `36`; minSdk `28`.
- Compose BOM `2026.08.00`; Activity Compose `1.13.0`; Lifecycle `2.11.0`.
- Room `3.0.1`; KSP `2.3.10`; bundled SQLite `2.7.0`.
- Paging `3.5.1`; Room Paging `3.0.1`; Vico `3.2.3`.
- `kotlinx-serialization-json/core` `1.8.1`.
- PR9 adds stable `androidx.health.connect:connect-client:1.1.0` only; no WorkManager/background-sync dependency.

Architecture rules:
- unidirectional data flow and immutable UI state;
- ViewModels/state holders own screen state;
- Composables do not query Room directly;
- repositories/DAOs remain persistence boundaries;
- Room owns canonical workout truth;
- `SavedStateHandle` / `rememberSaveable` are UI recovery, not canonical workout storage;
- health context must never be silently fused into GymTracker workout truth.

## 3. Room / workout invariants — unchanged by PR9

- DB version remains **2**; schemas v1/v2 committed; no schema v3 is justified by PR9.
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
- Health Connect raw/normalized data is **not** added to portable backup or CSV by PR9.
- PR9 does not alter backup format/version, restore semantics, or schema.

## 5. PR8 hardening / CI contract — must remain green

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

Never accept an older SHA as evidence for a newer PR head.

## 6. Issue #9 / PR #18 — Health Connect recovery context

Issue #9: `Post-V1 — Health Connect recovery context`.

Verified GitHub issue goal:
optionally import health/recovery context through Health Connect with explicit user permission.

Verified issue guardrails:
- logger works with zero health permissions;
- no medical diagnosis framing;
- no automatic prescribed-load changes from recovery;
- raw health input remains separate from derived context/correlations;
- permission/deletion controls.

### Real branch / PR

- Branch: `feat/health-connect-recovery-context`, created from verified main `48a4e30ae4dc1c5556c2d301c05e79534fee4258`.
- Draft PR #18: `GymTracker PR 9: Health Connect recovery context`.
- PR remains draft until exact-head full CI and final review/scope audit are green.
- Do not start Issue #10 from this branch.

### Current research-backed PR9 decisions

Research was re-done on 2026-08-25 using current Android Developers / Jetpack Health Connect, Google Play Health policy/publishing guidance, and Samsung developer interoperability documentation. Details and source URLs are in `docs/PR9_HEALTH_CONNECT_DESIGN.md`.

Decisions:
- stable Health Connect client `1.1.0`;
- Health Connect optional/read-only and accessed only after the user explicitly opens the recovery feature;
- foreground/on-demand reads only;
- **no** `READ_HEALTH_DATA_IN_BACKGROUND`;
- **no** `READ_HEALTH_DATA_HISTORY`;
- **no** WorkManager;
- requested read permissions only:
  - `READ_SLEEP` → `SleepSessionRecord`;
  - `READ_RESTING_HEART_RATE` → `RestingHeartRateRecord`;
  - `READ_HEART_RATE_VARIABILITY` → `HeartRateVariabilityRmssdRecord`;
- no generic heart-rate permission, no exercise-session permission, no Health Connect write permission;
- no `ExerciseSessionRecord` import because GymTracker already owns canonical workout history and duplicate session truth has no demonstrated PR9 value;
- no composite recovery score in PR9; display individual metrics and limitations instead;
- Samsung Health is not a dependency. Current Samsung documentation supports Health Connect sleep/stages, generic heart rate and exercise synchronization but does not document `RestingHeartRateRecord` or HRV RMSSD interoperability, so PR9 does not promise those Samsung-specific records.

### Data boundary / privacy

PR9 data flow:
`Health Connect raw records → minimal raw DTO boundary → deterministic normalized recovery context → read-only UI`.

Canonical training flow remains separate:
`Room Workout / WorkoutExercise / WorkoutSet → GymTracker canonical workout truth`.

Data minimization:
- no Health Connect record persistence in Room;
- no schema v3;
- no portable backup/CSV health data;
- no backend/cloud health upload;
- no health values in logging/analytics/crash text;
- no record IDs, notes, device identifiers or unused payload fields retained by GymTracker;
- only source package, relevant timestamps/zone offsets/stages and metric values cross the adapter boundary;
- disconnect revokes GymTracker Health Connect permissions and clears ephemeral in-memory context;
- PR9 does not delete another app's source records.

`docs/HEALTH_CONNECT_PRIVACY_NOTICE.md` and the in-app Health Connect rationale state the same substantive policy. Before a Play production publication using health permissions, that policy must also exist at the public Play Console privacy-policy URL and required Health Apps/Data Safety declarations must be completed; PR9 does not invent a production URL.

### Time/source semantics

- Instant RHR/HRV records are grouped using the current `ZoneId` calendar day.
- Day boundaries use `LocalDate.atStartOfDay(zone)` and are tested across 23-hour/25-hour DST days.
- Sleep is read from a bounded previous-day-start → next-day-start interval and assigned to the calendar day on which the session ends.
- A sleep record's own `endZoneOffset` wins for end-day assignment; current zone is fallback only.
- Naps/multiple sleep sessions remain separate.
- Multiple Health Connect origins remain separate. No heuristic cross-source deduplication is invented.
- RHR and HRV UI uses the latest record per source for the selected current recovery day.
- HRV is explicitly RMSSD in milliseconds and is never conflated with SDNN/other HRV metrics.

### UX/state semantics

- Access is a secondary action from History, not a bottom-navigation redesign.
- Active workout still has screen priority; PR4 logger behavior is unchanged.
- Permission dialog is user-initiated only; no prompt on app startup or workout start.
- Explicit states: provider unavailable, provider install/update required, no permissions, partial permissions, granted/no records, loaded context, permission changed/revoked during read, provider read error + retry.
- No diagnosis/prescription language and no automatic change to PREVIOUS/TARGET/TODAY.
- Rationale hooks for Android 13− / Android 14+ are declared in the manifest.

### Test boundary

All new fixtures are synthetic/non-identifying.

Coverage added includes:
- DST-safe day windows;
- sleep crossing midnight and recorded-end-offset assignment;
- clipped/deterministic sleep stage duration normalization;
- multiple origins kept separate;
- latest RHR/HRV per origin;
- unavailable / denied / partial / granted-empty / loaded states;
- permission revocation during read;
- recoverable provider error/retry;
- disconnect clears ephemeral context;
- narrow-screen recovery UI + accessibility checks;
- existing PR4–PR8 suites remain required by CI and Health Connect is isolated behind a testable interface so CI needs no real provider.

## 7. Current PR9 execution status

At this handoff update:
- Issue #9 is still open, as expected while draft PR #18 is unmerged.
- PR #18 is draft and mergeable.
- Implementation/docs/tests are on `feat/health-connect-recovery-context`.
- Latest code before this handoff commit moved Health Connect refresh to explicit recovery-screen open; construction of the root app no longer checks Health Connect permissions/data automatically.
- An earlier exact-head CI #190 (`32911644093`) began on `0fc511fc3023c18192793bba8009629ced94679c`, but subsequent privacy-hardening commits make that run stale for final evidence even if it succeeds.
- Final readiness still requires a new exact-head full CI, artifact verification, diff/security/scope audit, and review-thread audit.

## 8. Required completion sequence for Issue #9

1. Verify exact current PR head and full CI.
2. Fix any compile/test/lint/release failures on the exact head.
3. Verify no schema v3, no backup semantic changes, no Pulso paths, no real/sensitive data, no unnecessary permissions and no review threads.
4. Verify Room schema + debug APK + release APK artifacts for the exact head.
5. Only then mark PR #18 ready.
6. Squash merge using `expected_head_sha`.
7. Verify Issue #9 closed/completed.
8. Verify post-merge `main` Android CI and all three artifacts.
9. Update this handoff on merged `main` with exact final PR head, CI/artifact IDs and squash SHA.
10. Verify the documentation-head Android CI and all three artifacts.
11. Read real Issue #10 directly from GitHub, but **do not implement it**.
12. Deliver the full prompt for the actual next stage defined by GitHub.

## 9. Roadmap boundary

- PR1–PR8 / Issues #2–#8: completed as documented above.
- **Issue #9: current active stage; do not declare complete until merge + post-merge + documentation-head validation are done.**
- Issue #10: post-V1 Wear OS companion is currently known to exist/open, but its exact current body must be re-read after Issue #9 completion before preparing the next prompt. Do not begin Issue #10 silently.
