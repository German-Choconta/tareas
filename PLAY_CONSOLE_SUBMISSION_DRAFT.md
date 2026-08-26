# GymTracker — Play Console submission draft

Prepared from the exact code-level RC tagged as `v1.0.0-rc1` at `7d640a2b88cd856bae73c0281a4fdb93eeffcea0`.

This file is operational preparation only. It is not part of the tagged RC and does not change application behavior.

## Identity and version

- Package: `com.germanchoconta.gymtracker`
- Phone versionName: `1.0.0-rc1`
- Phone versionCode: `1`
- Wear versionName: `1.0.0-rc1`
- Wear versionCode: `10001`
- Phone targetSdk: `36`
- Wear targetSdk: `36`
- Wear standalone: `false`
- Accounts/login: none
- Advertising SDK: none detected in the RC
- Developer-operated analytics/crash backend: none detected in the RC

## Privacy policy

Immutable policy source for this preparation:

`https://raw.githubusercontent.com/German-Choconta/tareas/d6554adf2cb29669952a793fca700a311c2cf318/PRIVACY_POLICY.txt`

The policy covers:
- local workout/routine/history data;
- portable backup/restore and CSV;
- Android system backup/device transfer;
- optional read-only Health Connect;
- Wear OS Data Layer;
- retention/deletion;
- security;
- contact mechanism.

Before final production submission, confirm Play Console accepts the raw text URL as the designated privacy-policy URL. If Play requires a conventional website page, host this exact substantive policy at a public non-editable HTTPS page and use that URL.

## Health Apps declaration

Recommended features to select:

1. **Activity and Fitness**
   - GymTracker records strength-training workouts and exercise routines.
   - It also displays fitness-related recovery context using resting heart rate and HRV.

2. **Sleep Management**
   - GymTracker reads sleep sessions/stages only to display informational recovery context.

Do not select a medical-device category unless the product is separately changed and regulated as such. The RC explicitly does not diagnose, treat, or prescribe medical care.

### Health Connect permissions and justifications

#### Sleep

Permission:
`android.permission.health.READ_SLEEP`

Justification draft:
> GymTracker optionally reads recent sleep sessions and available sleep stages from Health Connect to show informational recovery context next to the user's training information. Sleep data does not automatically alter loads, targets, routines, progression recommendations, or completed workout history. The feature is optional and the core workout logger works without Health Connect.

#### Resting heart rate

Permission:
`android.permission.health.READ_RESTING_HEART_RATE`

Justification draft:
> GymTracker optionally reads recent resting-heart-rate records from Health Connect to show informational recovery context. The data is read on demand for a bounded time window, is not written back to Health Connect, and does not control training prescription.

#### Heart-rate variability

Permission:
`android.permission.health.READ_HEART_RATE_VARIABILITY`

Justification draft:
> GymTracker optionally reads HRV records represented as RMSSD from Health Connect to show informational recovery context. The data is not used for diagnosis or treatment, is not persisted as canonical workout history, and does not automatically change workout targets or progression.

Health Connect characteristics verified in RC:
- read-only;
- no background health permission;
- no extended-history permission;
- no Health Connect records in Room workout history;
- no Health Connect values in portable backup/CSV;
- no developer backend for Health Connect values.

## Data Safety — recommended draft

The current RC does not operate a developer backend for user data.

The majority of user data is processed locally on the user's own devices. Google Play's current Data Safety guidance states that on-device-only processing is not declared as collection.

Recommended starting answer:
- developer collection of required user-data types: **No**, based on the current RC architecture;
- developer sharing with third parties: **No**, based on the current RC architecture;
- account creation: **No**.

Important review notes before pressing Submit:
- Android system backup is enabled for the phone Room database. The developer does not receive/access the user's Android backup. This is disclosed in the privacy policy.
- portable backup/CSV is user initiated and the user selects the destination; the developer receives no server copy.
- phone/watch state moves through the Wearable Data Layer between the user's paired devices; it is not sent to a GymTracker backend.
- Health Connect reads are local/platform-mediated and optional.

If Play Console's current questionnaire treats any of those platform transfers differently from the general on-device/user-controlled exceptions, follow the wording shown in the live Play form rather than forcing this draft answer.

## App access

Recommended:
- all core functionality is accessible without an account;
- Health Connect is optional and must not be presented as required to reviewers;
- Wear is a non-standalone companion and requires the paired phone app for the companion workflow.

## Store listing draft — Spanish

### App name

GymTracker

### Short description

Registra fuerza, compara sesiones y progresa con recomendaciones transparentes

### Full description

GymTracker es un registro de entrenamiento de fuerza offline-first diseñado para ayudarte a registrar, comparar, entender y progresar sin convertir tus datos en una caja negra.

**LOG**
Registra tus entrenamientos con carga, repeticiones, RIR, tipos de serie, notas y descansos. Los cambios se guardan localmente mientras entrenas.

**COMPARE**
Consulta PREVIOUS + TARGET + TODAY para comparar lo que hiciste antes, el objetivo de la rutina y lo que estás realizando hoy.

**UNDERSTAND**
Revisa tu historial, récords personales, e1RM mediante la fórmula de Epley y métricas de progreso como carga, repeticiones a una carga, volumen y frecuencia.

**PROGRESS**
Recibe recomendaciones de progresión deterministas y explicables. Aplicar una sugerencia es siempre una acción explícita y modifica únicamente la carga de TODAY.

También incluye:
- historial local sin depender de una cuenta GymTracker;
- backup/restauración portátil y exportación CSV;
- contexto de recuperación opcional y de solo lectura mediante Health Connect;
- companion para Wear OS con controles de entrenamiento desde la muñeca;
- funcionamiento principal sin Health Connect ni reloj.

El teléfono mantiene la fuente canónica del historial de entrenamiento. Health Connect es opcional y no controla automáticamente tu prescripción de entrenamiento.

GymTracker no es una aplicación médica y el contexto de recuperación es únicamente informativo.

## Required Play listing assets still missing from the repository

Current repository audit found no dedicated Play-store asset set.

Prepare externally before publication:

- Play app icon: 512 × 512 PNG, max 1024 KB;
- feature graphic: 1024 × 500 JPEG or 24-bit PNG;
- at least two phone screenshots meeting Play dimensions;
- at least one Wear OS screenshot of the current app;
- Wear screenshot must be 1:1, show only the actual app UI, and not be placed in a device frame.

The Wear listing must explicitly mention **Wear OS**.

## Signing and upload boundary

Do not put signing material in the public repository.

For public Play distribution:
- enroll/configure Play App Signing;
- establish the external upload-signing process;
- phone and Wear must use the same app-signing key because they share the same package name;
- keep version codes unique across form factors;
- upload the signed/Play-accepted phone artifact and the independently managed Wear artifact through Play Console.

Current GitHub Actions release outputs are unsigned acceptance evidence and must not be uploaded as if already distribution signed.

## Closed testing

If the Play developer account is a personal account created after November 13, 2023, current Play rules require a closed test with at least 12 opted-in testers for 14 continuous days before requesting production access.

Account-specific applicability must be checked inside the developer's Play Console.

## Remaining human / account-bound steps

These cannot be completed from the repository alone:

1. Create/select the app in Play Console for package `com.germanchoconta.gymtracker`.
2. Configure Play App Signing and external upload signing.
3. Enter the privacy-policy URL.
4. Complete Data Safety.
5. Complete Health Apps declaration.
6. Complete content rating and target-audience declarations.
7. Upload store icon, feature graphic and screenshots.
8. Add Wear OS form factor and its screenshot.
9. Upload release artifacts to the relevant test track.
10. Review the Play pre-launch report.
11. Complete any account-specific closed-test requirement.
12. Request production review only after all policy and signing gates are green.
