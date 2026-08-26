# Issue #23 — Release readiness and integrated acceptance

Status: active Post-V1 release-candidate gate.

## Verified baseline

Issue #23 was created only after GitHub confirmed:

- Issue #21 closed/completed;
- PR #22 closed/merged;
- exact main SHA 26ba5f5d1c7e167310755203383067305ac2a338;
- tree 841138b5719e52b443718c91e74567dc6ed1ecef;
- Android CI #227 / run 33008372588 SUCCESS on that exact SHA;
- zero open issues and zero open pull requests before Issue #23 was created;
- zero GitHub Releases;
- no equivalent release-readiness issue.

Branch: feat/release-readiness-integrated-acceptance.

## Product acceptance strategy

Issue #23 does not duplicate every focused test. It adds one integrated synthetic Room/repository/ViewModel acceptance path and keeps the already hardened focused suites as specialized gates.

### Integrated path

ReleaseIntegratedAcceptanceTest proves one continuous synthetic flow:

1. create and edit an exercise;
2. create a routine with TARGET snapshot configuration;
3. log and finish a baseline workout;
4. derive History, PR/e1RM, and Progress analytics from canonical finished Room truth;
5. start the next workout through the real WorkoutLoggerViewModel;
6. observe PREVIOUS + TARGET + TODAY plus deterministic progression guidance;
7. autosave TODAY values and recover the active workout through a fresh ViewModel;
8. explicitly Apply suggested load and prove only TODAY load changes;
9. complete and finish the workout;
10. re-derive History/PR/Progress;
11. portable-backup encode/decode/validate and restore into a fresh Room database;
12. export CSV from the same synthetic canonical snapshot.

### Existing focused gates retained

- V1HardeningUiTest: TalkBack/accessibility checks, 320 dp critical workout reachability, touch actions, theme and saved UI state.
- ProgressionApplicationStateTest: explicit Apply suggested load mutates only TODAY load and legacy targets degrade safely.
- HistoryPersistenceTest / ProgressAnalyticsPersistenceTest: raw history, deterministic ordering and analytics persistence.
- BackupRestorePersistenceTest and backup unit tests: atomic restore, format/integrity validation, CSV semantics.
- RecoveryContextScreenTest plus Health Connect JVM tests: unavailable/denied/partial/error states remain optional and do not block core.
- WearWorkoutSyncPersistenceTest, phone receipt tests, Wear JVM tests and Wear connected instrumentation: phone-canonical idempotency/conflict/recovery and disconnected operation remain locked.

All fixtures are synthetic and non-identifying.

## Release configuration

Release candidate: 1.0.0-rc1.

| Form factor | applicationId | versionCode | minSdk | targetSdk | compileSdk |
| --- | --- | ---: | ---: | ---: | ---: |
| Phone | com.germanchoconta.gymtracker | 1 | 28 | 36 | 37 |
| Wear OS | com.germanchoconta.gymtracker | 10001 | 30 | 36 | 37 |

The existing separate version-code ranges are retained for the first unpublished release candidate. A future upload must increment the relevant code; phone and Wear codes must remain unique across artifacts for the shared package.

Release variants keep R8 minification and resource shrinking enabled.

## Signing and distribution boundary

No keystore, signing password, private key, upload key, or signing secret belongs in this public repository.

The Gradle release build types intentionally have no signingConfig. Therefore CI release APK/AAB outputs are unsigned build evidence, not installable/distribution-ready release binaries.

Current Android guidance requires every installable APK to be cryptographically signed. Google Play uses Android App Bundles for new apps and Play App Signing is required for new Play apps. Wear OS is uploaded independently, must share the phone package name and signing key, and requires unique version codes across form factors.

Primary guidance:

- https://developer.android.com/guide/app-bundle/faq
- https://developer.android.com/build/build-variants
- https://developer.android.com/google/play/requirements/target-sdk
- https://developer.android.com/training/wearables/packaging
- https://developer.android.com/develop/adaptive-apps/quality-guidelines/wear-app-quality
- https://developer.android.com/training/wearables/data/overview

With targetSdk 36, both modules meet the Android 16 target level required for new phone apps/updates from August 31, 2026; Wear exceeds its API 35 exception threshold.

## CI release evidence

The hardened CI remains intact and gains additive release evidence:

- assemble the existing phone/Wear debug and release APKs;
- assemble the phone release AAB;
- validate package/version metadata of release APKs;
- assert that repository-produced release APKs are not silently distribution-signed;
- record SHA-256 checksums for phone release APK, Wear release APK, and phone AAB;
- upload the unsigned AAB and release metadata in addition to the existing Room/APK artifacts.

This does not introduce or request signing secrets.

## First real distribution checklist

Before a real external release, outside this PR:

1. choose/secure the production signing and Play App Signing/upload-key process;
2. sign the upload artifact outside the repository/CI secret-free boundary;
3. configure Play Console and complete required app content/data-safety/Health Connect declarations;
4. host the already-required substantive Health Connect privacy notice at the public URL used for store publication;
5. complete phone and Wear store assets/listing requirements and closed/pre-release testing;
6. validate signed phone/Wear artifacts use the same app-signing key;
7. only after merge approval and healthy main, create a prerelease tag such as v1.0.0-rc1 and GitHub prerelease if desired.

No tag or GitHub Release is created by Issue #23 before explicit merge/release approval.

## Release-candidate decision

GymTracker can be declared **code-level release-candidate ready** when the exact final PR head passes the full hardened CI plus the integrated acceptance test, Room remains v2 with schemas 1/2 only, scope/privacy audits are clean, and PR review/mergeability is clean.

That decision does not mean a production-distributable signed binary already exists. Signing, store configuration, policy declarations, and distribution remain explicit pre-release operations outside repository source control.
