# PR9 — Health Connect recovery context design

Research date: 2026-08-25 (America/Bogota)

Issue: #9 — `Post-V1 — Health Connect recovery context`

## Product boundary

Health Connect is an optional, read-only context source. It must never become a prerequisite for GymTracker's workout logger, History, Progress, Backup/Restore, or canonical Room workout state.

The source boundaries remain explicit:

1. `WorkoutSet` / Room = canonical GymTracker training truth.
2. Health Connect records = external raw health context.
3. GymTracker normalized recovery context = ephemeral read-only representation.
4. Derived correlations/insights = separate, recalculable output; PR9 intentionally introduces no composite recovery score.

PR9 does not import Health Connect exercise sessions into GymTracker. GymTracker already owns canonical workout history, and duplicating `ExerciseSessionRecord` would add ambiguous/conflicting session truth without demonstrated product value.

## Current platform research

Primary sources re-checked at implementation time:

- Health Connect overview/get started: https://developer.android.com/health-and-fitness/guides/health-connect
- Health Connect permissions: https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started
- Read data: https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data
- Sync data: https://developer.android.com/health-and-fitness/guides/health-connect/develop/sync-data
- Jetpack Health Connect releases: https://developer.android.com/jetpack/androidx/releases/health-connect
- Jetpack `HealthConnectClient`: https://developer.android.com/reference/kotlin/androidx/health/connect/client/HealthConnectClient
- Sleep sessions: https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/SleepSessionRecord
- Resting heart rate: https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/RestingHeartRateRecord
- HRV RMSSD: https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/HeartRateVariabilityRmssdRecord
- Play Health Connect policy: https://support.google.com/googleplay/android-developer/answer/12991134
- Play Health Apps declaration / publishing: https://developer.android.com/health-and-fitness/health-connect/publish
- Samsung Health ↔ Health Connect: https://developer.samsung.com/health/android/data/guide/data-types/health-connect.html

Findings used by PR9:

- Stable `androidx.health.connect:connect-client` is `1.1.0`; the feature set required here does not justify depending on an alpha release.
- Health Connect SDK supports API 26+, while the provider/app model is available from Android 9 (API 28), matching GymTracker's current `minSdk = 28`.
- Permissions can be revoked at any time, so permission state is re-checked before reads and revocation is treated as a normal state rather than an exceptional app failure.
- Standard foreground reads do not require background access. PR9 therefore does not request `READ_HEALTH_DATA_IN_BACKGROUND` and does not add WorkManager.
- Access beyond the normal historical window requires an additional history permission. PR9 does not need broad history and does not request `READ_HEALTH_DATA_HISTORY`.
- Play policy requires minimum-necessary health permissions, a user-facing health feature for each requested data type, Data Safety / Health Apps declarations, and a public privacy policy/rationale for store publication.
- Samsung documents Health Connect synchronization for sleep sessions/stages, heart rate and exercise sessions. Its current synchronized-data table does not document `RestingHeartRateRecord` or `HeartRateVariabilityRmssdRecord`; PR9 must not promise Samsung-specific RHR/HRV availability. HRV is shown only when that exact Health Connect RMSSD record is available from any compatible source.

## Permissions and records

PR9 requests read access only for active UI features:

- `android.permission.health.READ_SLEEP` → `SleepSessionRecord`
- `android.permission.health.READ_RESTING_HEART_RATE` → `RestingHeartRateRecord`
- `android.permission.health.READ_HEART_RATE_VARIABILITY` → `HeartRateVariabilityRmssdRecord`

Explicitly not requested:

- generic heart-rate reads;
- exercise-session reads;
- any write permission;
- background read;
- extended history read.

Partial permission grants are supported. The UI labels unavailable metrics instead of repeatedly requesting or blocking the app.

## Availability and permission UX

Health Connect availability is modeled explicitly:

- available;
- unavailable on this device;
- provider install/update required.

When available, the recovery screen distinguishes:

- no requested permissions granted;
- partial permissions;
- all requested permissions;
- permissions granted but no records for the selected recovery day;
- read failure;
- loaded context.

Permission dialogs are user initiated. No permission prompt is shown on app startup, workout start, or logger use. The screen provides a disconnect action that revokes GymTracker's Health Connect permissions and clears its in-memory context.

Android's required Health Connect permission-rationale activity/alias are part of PR9. Before Play publication, the same substantive privacy policy shown to the user must be hosted at the public URL declared in Play Console. PR9 does not invent a production privacy-policy URL.

## Data minimization, persistence and backup

PR9 does **not** persist Health Connect records or normalized recovery context in Room.

Reasoning:

- The source data remains owned by Health Connect and is re-readable while permission exists.
- The first product feature only needs a bounded current-day context view.
- Persisting health records would increase sensitivity, require retention/deletion semantics, force a Room migration, and complicate portable backup without delivering necessary product value.
- Normalization is deterministic and can be recomputed.

Consequences:

- Room remains schema v2; no schema v3.
- Portable backup V1 remains unchanged and contains only GymTracker canonical tables.
- CSV export remains unchanged.
- Health Connect data is not copied into OS/manual GymTracker backup by PR9.
- Disconnect/revocation clears ephemeral UI state; there is no separate persisted health cache to delete.
- PR9 never deletes another application's source records. Health Connect deletion APIs are not a substitute for source-owner deletion.

## Foreground read window

No background job is added. The recovery screen refreshes on demand/foreground.

For a requested local recovery day:

- Resting HR and HRV use `[startOfDay(day), startOfDay(day + 1))` computed with the current `ZoneId` so DST days are handled correctly.
- Sleep is read from a bounded two-day interval around the requested day and associated with the calendar day on which the sleep session ends.
- If a sleep record carries an `endZoneOffset`, that offset determines its end-local-date; otherwise the refresh `ZoneId` is used.
- Naps/multiple sleep sessions remain distinct.
- Multiple origins remain distinct; PR9 does not invent cross-app deduplication.

The UI states this day-bucketing rule. Travel can therefore change the current local day for instant records, while a sleep record with its own end offset retains that recorded local end day.

## Raw → normalized boundary

The Health Connect adapter maps SDK records into minimal raw DTOs containing only fields used by GymTracker:

- source package / data origin;
- relevant timestamps and zone offsets;
- sleep stage intervals/types;
- resting BPM;
- HRV RMSSD milliseconds.

No record IDs, notes, device identifiers, payload dumps or unused metadata are retained by GymTracker.

A pure normalizer converts those DTOs to UI/domain context. No health value is logged, sent to analytics, included in crash text, or written to CI output.

## Recovery presentation

PR9 intentionally does not implement a composite recovery score. It presents individual context metrics because:

- the available record set varies by source/device/permission;
- a single score would require arbitrary weighting and missing-data semantics;
- the issue requires context, not progression prescription or medical interpretation.

Displayed metrics:

- sleep session duration and available stage durations;
- latest resting-heart-rate value per source for the recovery day;
- latest Health Connect HRV **RMSSD** value per source, explicitly labeled in milliseconds.

The screen states that the values are informational context, not a diagnosis and not an instruction to change workout load. `PREVIOUS`, `TARGET` and `TODAY` remain untouched.

## Error handling

The adapter/UI handle:

- Health Connect unavailable;
- provider install/update required;
- no permissions;
- partial permissions;
- zero records;
- permission revoked between check and read (`SecurityException`);
- provider/service/I/O failure;
- retry without mutating workout state.

No exception message containing health payloads is surfaced or logged.

## Testing boundary

Tests use only synthetic, non-identifying fixtures.

High-value coverage:

- deterministic day boundaries including DST-safe `startOfDay`;
- sleep crossing midnight and end-day assignment;
- multiple sleep sessions/origins remain separate;
- latest-per-origin RHR and HRV selection;
- unavailable / denied / partial / granted-empty / loaded states;
- permission revocation during read;
- retry after read failure;
- disconnect clears ephemeral context;
- Compose recovery empty/error/partial/data states and accessibility semantics where valuable;
- existing logger/History/Progress/Backup suites remain green with no Health Connect dependency.

The SDK is behind a small interface so JVM tests do not require a real Health Connect provider in GitHub Actions.

## Out of scope / future reconsideration

- Health Connect `ExerciseSessionRecord` import;
- background sync;
- broad history access;
- persistent health cache / Room schema change;
- health data in portable backup/CSV;
- recovery score;
- correlation/causality claims;
- automatic workout or progression changes;
- Wear OS;
- AI coaching;
- monetization.

Any future proposal that changes these decisions must re-check current Health Connect/Play/Samsung documentation and separately assess privacy, retention, deletion, backup and schema consequences.
