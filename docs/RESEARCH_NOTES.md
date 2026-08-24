# Research Notes — 2026-08-24

## Official Android references
- Compose architecture/UDF: https://developer.android.com/develop/ui/compose/architecture
- Compose August 2026 / BOM 2026.08.00: https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Compose compiler setup: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- AGP 9.3.0: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Built-in Kotlin in AGP 9: https://developer.android.com/build/migrate-to-built-in-kotlin
- Android 17 / API 37 setup: https://developer.android.com/about/versions/17/setup-sdk
- Room: https://developer.android.com/training/data-storage/room
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Health Connect: https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started
- Wear OS Health Services: https://developer.android.com/health-and-fitness/health-services

Verified on 2026-08-24:
- Compose BOM `2026.08.00` is the current stable BOM.
- Compose 1.12 targets compileSdk 37 and requires AGP 9.1.2+.
- AGP 9.3.0 supports API 37 and uses Gradle 9.5.0 / JDK 17.
- AndroidX Activity 1.13.0 and Lifecycle 2.11.0 are stable.

## Competitor references
- Hevy features: https://www.hevyapp.com/features/
- Hevy feature guide: https://help.hevyapp.com/hc/en-us/articles/33106320824727-Everything-You-Need-to-Know-About-the-Hevy-App-2025-Features-Guide
- Hevy public API: https://api.hevyapp.com/docs/
- Alpha Progression: https://alphaprogression.com/
- Strong: https://www.strong.app/

## Training research references
- Lovegrove et al. Repetitions in Reserve Is a Reliable Tool for Prescribing Resistance Training Load. J Strength Cond Res. PubMed 36135029.
- Hughes et al. Estimating Repetitions in Reserve in Four Commonly Used Resistance Exercises. PubMed 33337690.
- Jukic et al. Modeling the repetitions-in-reserve-velocity relationship. Physiol Rep. 2024. PubMed 38418370.
- Scoping review on RIR feasibility/usefulness. PubMed 38563729.

## Product implications
1. Keep RIR optional.
2. Keep raw sets as the source of truth so derived metrics can be recalculated.
3. Prefer transparent deterministic progression for V1.
4. Prioritize speed of logging over social or AI features.
5. Make historical analytics unlimited and local.
6. Treat Health Connect and Wear OS as later integrations, not blockers for V1.
7. The active workout surface should emphasize `Previous + Target + Today`.

## UX benchmark conclusion
The strongest recurring pattern across leading workout trackers is a table-like active-workout surface centered on set number, previous performance, current load/reps, effort, and completion. GymTracker should preserve that familiar mental model while making the next target explicit.

Recommended live-workout information hierarchy:
1. exercise name
2. target rep range + suggested load
3. previous comparable set
4. current editable load/reps/RIR
5. complete-set control
6. rest timer status
7. notes/settings behind secondary actions

Avoid analytics dashboards, social content, or dense coaching prose inside the live set-entry loop.
