package com.germanchoconta.gymtracker.data.backup

import com.germanchoconta.gymtracker.data.local.ExerciseEntity
import com.germanchoconta.gymtracker.data.local.ExerciseMuscleEntity
import com.germanchoconta.gymtracker.data.local.MuscleEntity
import com.germanchoconta.gymtracker.data.local.RoutineEntity
import com.germanchoconta.gymtracker.data.local.RoutineExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutEntity
import com.germanchoconta.gymtracker.data.local.WorkoutExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

object PortableBackupCodec {
    private val json = Json {
        isLenient = false
        allowSpecialFloatingPointValues = false
    }

    fun encode(
        snapshot: BackupSnapshot,
        generatedAtEpochMillis: Long,
        appVersion: String,
        databaseSchemaVersion: Int = BackupFormat.DATABASE_SCHEMA_VERSION,
    ): ByteArray {
        require(generatedAtEpochMillis >= 0L)
        require(appVersion.isNotBlank())
        val payload = encodePayload(snapshot.normalized())
        val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
        val document = buildJsonObject {
            put("format", JsonPrimitive(BackupFormat.NAME))
            put("formatVersion", JsonPrimitive(BackupFormat.VERSION))
            put("generatedAtEpochMillis", JsonPrimitive(generatedAtEpochMillis))
            put("appVersion", JsonPrimitive(appVersion))
            put("databaseSchemaVersion", JsonPrimitive(databaseSchemaVersion))
            put("payloadSha256", JsonPrimitive(sha256Hex(payloadBytes)))
            put("payload", payload)
        }
        return document.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): DecodedBackup {
        if (bytes.isEmpty()) throw BackupFormatException("El archivo de backup está vacío.")
        if (bytes.size > BackupFormat.MAX_DOCUMENT_BYTES) {
            throw BackupFormatException("El archivo de backup supera el límite defensivo de 128 MiB.")
        }
        val text = decodeStrictUtf8(bytes)
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
                ?: throw BackupFormatException("El backup debe ser un objeto JSON.")
        } catch (error: BackupFormatException) {
            throw error
        } catch (error: SerializationException) {
            throw BackupFormatException("El archivo no contiene JSON válido.", error)
        } catch (error: IllegalArgumentException) {
            throw BackupFormatException("El archivo no contiene JSON válido.", error)
        }

        root.requireExactKeys(
            "format",
            "formatVersion",
            "generatedAtEpochMillis",
            "appVersion",
            "databaseSchemaVersion",
            "payloadSha256",
            "payload",
        )
        val format = root.requireString("format")
        if (format != BackupFormat.NAME) {
            throw BackupFormatException("El archivo no es un backup portable de GymTracker.")
        }
        val formatVersion = root.requireInt("formatVersion")
        if (formatVersion != BackupFormat.VERSION) {
            val direction = if (formatVersion > BackupFormat.VERSION) "más nueva" else "desconocida"
            throw BackupFormatException("Versión de backup $direction e incompatible: $formatVersion.")
        }
        val generatedAt = root.requireLong("generatedAtEpochMillis")
        val appVersion = root.requireString("appVersion")
        val databaseSchemaVersion = root.requireInt("databaseSchemaVersion")
        if (databaseSchemaVersion > BackupFormat.DATABASE_SCHEMA_VERSION) {
            throw BackupFormatException(
                "El backup proviene de un schema de base de datos futuro ($databaseSchemaVersion).",
            )
        }
        val declaredHash = root.requireString("payloadSha256")
        if (!declaredHash.matches(Regex("[0-9a-f]{64}"))) {
            throw BackupFormatException("El checksum del backup no tiene un formato válido.")
        }
        val payload = root.requireObject("payload")
        val snapshot = decodePayload(payload).normalized()
        val canonicalPayloadBytes = encodePayload(snapshot).toString().toByteArray(Charsets.UTF_8)
        val actualHash = sha256Hex(canonicalPayloadBytes)
        if (!MessageDigest.isEqual(
                declaredHash.toByteArray(Charsets.US_ASCII),
                actualHash.toByteArray(Charsets.US_ASCII),
            )
        ) {
            throw BackupFormatException("El checksum del backup no coincide con su contenido.")
        }

        return DecodedBackup(
            metadata = BackupMetadata(
                formatVersion = formatVersion,
                generatedAtEpochMillis = generatedAt,
                appVersion = appVersion,
                databaseSchemaVersion = databaseSchemaVersion,
                payloadSha256 = declaredHash,
            ),
            snapshot = snapshot,
        )
    }

    internal fun canonicalPayload(snapshot: BackupSnapshot): String =
        encodePayload(snapshot.normalized()).toString()

    private fun encodePayload(snapshot: BackupSnapshot): JsonObject = buildJsonObject {
        put("exercises", buildJsonArray { snapshot.exercises.forEach { add(encodeExercise(it)) } })
        put("muscles", buildJsonArray { snapshot.muscles.forEach { add(encodeMuscle(it)) } })
        put(
            "exerciseMuscles",
            buildJsonArray { snapshot.exerciseMuscles.forEach { add(encodeExerciseMuscle(it)) } },
        )
        put("routines", buildJsonArray { snapshot.routines.forEach { add(encodeRoutine(it)) } })
        put(
            "routineExercises",
            buildJsonArray { snapshot.routineExercises.forEach { add(encodeRoutineExercise(it)) } },
        )
        put("workouts", buildJsonArray { snapshot.workouts.forEach { add(encodeWorkout(it)) } })
        put(
            "workoutExercises",
            buildJsonArray { snapshot.workoutExercises.forEach { add(encodeWorkoutExercise(it)) } },
        )
        put("workoutSets", buildJsonArray { snapshot.workoutSets.forEach { add(encodeWorkoutSet(it)) } })
    }

    private fun decodePayload(payload: JsonObject): BackupSnapshot {
        payload.requireExactKeys(
            "exercises",
            "muscles",
            "exerciseMuscles",
            "routines",
            "routineExercises",
            "workouts",
            "workoutExercises",
            "workoutSets",
        )
        return BackupSnapshot(
            exercises = payload.requireArray("exercises").map(::decodeExercise),
            muscles = payload.requireArray("muscles").map(::decodeMuscle),
            exerciseMuscles = payload.requireArray("exerciseMuscles").map(::decodeExerciseMuscle),
            routines = payload.requireArray("routines").map(::decodeRoutine),
            routineExercises = payload.requireArray("routineExercises").map(::decodeRoutineExercise),
            workouts = payload.requireArray("workouts").map(::decodeWorkout),
            workoutExercises = payload.requireArray("workoutExercises").map(::decodeWorkoutExercise),
            workoutSets = payload.requireArray("workoutSets").map(::decodeWorkoutSet),
        )
    }

    private fun encodeExercise(value: ExerciseEntity) = buildJsonObject {
        put("id", JsonPrimitive(value.id))
        put("name", JsonPrimitive(value.name))
        put("equipment", value.equipment?.let(::JsonPrimitive) ?: JsonNull)
        put("unilateral", JsonPrimitive(value.unilateral))
        put("notes", value.notes?.let(::JsonPrimitive) ?: JsonNull)
        put("archived", JsonPrimitive(value.archived))
        put("defaultRepMin", value.defaultRepMin?.let(::JsonPrimitive) ?: JsonNull)
        put("defaultRepMax", value.defaultRepMax?.let(::JsonPrimitive) ?: JsonNull)
        put("defaultTargetRirTenths", value.defaultTargetRirTenths?.let(::JsonPrimitive) ?: JsonNull)
        put("defaultRestSeconds", value.defaultRestSeconds?.let(::JsonPrimitive) ?: JsonNull)
        put("defaultLoadIncrementGrams", value.defaultLoadIncrementGrams?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun decodeExercise(element: JsonElement): ExerciseEntity {
        val value = element.requireObject("exercise")
        value.requireExactKeys(
            "id", "name", "equipment", "unilateral", "notes", "archived", "defaultRepMin",
            "defaultRepMax", "defaultTargetRirTenths", "defaultRestSeconds", "defaultLoadIncrementGrams",
        )
        return ExerciseEntity(
            id = value.requireString("id"),
            name = value.requireString("name"),
            equipment = value.requireNullableString("equipment"),
            unilateral = value.requireBoolean("unilateral"),
            notes = value.requireNullableString("notes"),
            archived = value.requireBoolean("archived"),
            defaultRepMin = value.requireNullableInt("defaultRepMin"),
            defaultRepMax = value.requireNullableInt("defaultRepMax"),
            defaultTargetRirTenths = value.requireNullableInt("defaultTargetRirTenths"),
            defaultRestSeconds = value.requireNullableInt("defaultRestSeconds"),
            defaultLoadIncrementGrams = value.requireNullableLong("defaultLoadIncrementGrams"),
        )
    }

    private fun encodeMuscle(value: MuscleEntity) = buildJsonObject {
        put("id", JsonPrimitive(value.id))
        put("name", JsonPrimitive(value.name))
    }

    private fun decodeMuscle(element: JsonElement): MuscleEntity {
        val value = element.requireObject("muscle")
        value.requireExactKeys("id", "name")
        return MuscleEntity(id = value.requireString("id"), name = value.requireString("name"))
    }

    private fun encodeExerciseMuscle(value: ExerciseMuscleEntity) = buildJsonObject {
        put("exerciseId", JsonPrimitive(value.exerciseId))
        put("muscleId", JsonPrimitive(value.muscleId))
        put("role", JsonPrimitive(value.role))
    }

    private fun decodeExerciseMuscle(element: JsonElement): ExerciseMuscleEntity {
        val value = element.requireObject("exerciseMuscle")
        value.requireExactKeys("exerciseId", "muscleId", "role")
        return ExerciseMuscleEntity(
            exerciseId = value.requireString("exerciseId"),
            muscleId = value.requireString("muscleId"),
            role = value.requireString("role"),
        )
    }

    private fun encodeRoutine(value: RoutineEntity) = buildJsonObject {
        put("id", JsonPrimitive(value.id))
        put("name", JsonPrimitive(value.name))
        put("position", JsonPrimitive(value.position))
        put("notes", value.notes?.let(::JsonPrimitive) ?: JsonNull)
        put("archived", JsonPrimitive(value.archived))
    }

    private fun decodeRoutine(element: JsonElement): RoutineEntity {
        val value = element.requireObject("routine")
        value.requireExactKeys("id", "name", "position", "notes", "archived")
        return RoutineEntity(
            id = value.requireString("id"),
            name = value.requireString("name"),
            position = value.requireInt("position"),
            notes = value.requireNullableString("notes"),
            archived = value.requireBoolean("archived"),
        )
    }

    private fun encodeRoutineExercise(value: RoutineExerciseEntity) = buildJsonObject {
        put("id", JsonPrimitive(value.id))
        put("routineId", JsonPrimitive(value.routineId))
        put("exerciseId", JsonPrimitive(value.exerciseId))
        put("position", JsonPrimitive(value.position))
        put("targetSetCount", JsonPrimitive(value.targetSetCount))
        put("repMin", JsonPrimitive(value.repMin))
        put("repMax", JsonPrimitive(value.repMax))
        put("targetRirTenths", value.targetRirTenths?.let(::JsonPrimitive) ?: JsonNull)
        put("restSeconds", JsonPrimitive(value.restSeconds))
        put("loadIncrementGrams", JsonPrimitive(value.loadIncrementGrams))
        put("previousReferenceMode", JsonPrimitive(value.previousReferenceMode))
    }

    private fun decodeRoutineExercise(element: JsonElement): RoutineExerciseEntity {
        val value = element.requireObject("routineExercise")
        value.requireExactKeys(
            "id", "routineId", "exerciseId", "position", "targetSetCount", "repMin", "repMax",
            "targetRirTenths", "restSeconds", "loadIncrementGrams", "previousReferenceMode",
        )
        return RoutineExerciseEntity(
            id = value.requireString("id"),
            routineId = value.requireString("routineId"),
            exerciseId = value.requireString("exerciseId"),
            position = value.requireInt("position"),
            targetSetCount = value.requireInt("targetSetCount"),
            repMin = value.requireInt("repMin"),
            repMax = value.requireInt("repMax"),
            targetRirTenths = value.requireNullableInt("targetRirTenths"),
            restSeconds = value.requireInt("restSeconds"),
            loadIncrementGrams = value.requireLong("loadIncrementGrams"),
            previousReferenceMode = value.requireString("previousReferenceMode"),
        )
    }

    private fun encodeWorkout(value: WorkoutEntity) = buildJsonObject {
        put("id", JsonPrimitive(value.id))
        put("routineId", value.routineId?.let(::JsonPrimitive) ?: JsonNull)
        put("title", JsonPrimitive(value.title))
        put("startedAt", JsonPrimitive(value.startedAt))
        put("finishedAt", value.finishedAt?.let(::JsonPrimitive) ?: JsonNull)
        put("notes", value.notes?.let(::JsonPrimitive) ?: JsonNull)
        put("restTimerEndsAt", value.restTimerEndsAt?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "restTimerWorkoutExerciseId",
            value.restTimerWorkoutExerciseId?.let(::JsonPrimitive) ?: JsonNull,
        )
    }

    private fun decodeWorkout(element: JsonElement): WorkoutEntity {
        val value = element.requireObject("workout")
        value.requireExactKeys(
            "id", "routineId", "title", "startedAt", "finishedAt", "notes", "restTimerEndsAt",
            "restTimerWorkoutExerciseId",
        )
        return WorkoutEntity(
            id = value.requireString("id"),
            routineId = value.requireNullableString("routineId"),
            title = value.requireString("title"),
            startedAt = value.requireLong("startedAt"),
            finishedAt = value.requireNullableLong("finishedAt"),
            notes = value.requireNullableString("notes"),
            restTimerEndsAt = value.requireNullableLong("restTimerEndsAt"),
            restTimerWorkoutExerciseId = value.requireNullableString("restTimerWorkoutExerciseId"),
        )
    }

    private fun encodeWorkoutExercise(value: WorkoutExerciseEntity) = buildJsonObject {
        put("id", JsonPrimitive(value.id))
        put("workoutId", JsonPrimitive(value.workoutId))
        put("exerciseId", JsonPrimitive(value.exerciseId))
        put("routineExerciseId", value.routineExerciseId?.let(::JsonPrimitive) ?: JsonNull)
        put("position", JsonPrimitive(value.position))
        put("notes", value.notes?.let(::JsonPrimitive) ?: JsonNull)
        put("targetSetCount", value.targetSetCount?.let(::JsonPrimitive) ?: JsonNull)
        put("repMin", value.repMin?.let(::JsonPrimitive) ?: JsonNull)
        put("repMax", value.repMax?.let(::JsonPrimitive) ?: JsonNull)
        put("targetRirTenths", value.targetRirTenths?.let(::JsonPrimitive) ?: JsonNull)
        put("restSeconds", value.restSeconds?.let(::JsonPrimitive) ?: JsonNull)
        put("loadIncrementGrams", value.loadIncrementGrams?.let(::JsonPrimitive) ?: JsonNull)
        put("previousReferenceMode", value.previousReferenceMode?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun decodeWorkoutExercise(element: JsonElement): WorkoutExerciseEntity {
        val value = element.requireObject("workoutExercise")
        value.requireExactKeys(
            "id", "workoutId", "exerciseId", "routineExerciseId", "position", "notes", "targetSetCount",
            "repMin", "repMax", "targetRirTenths", "restSeconds", "loadIncrementGrams", "previousReferenceMode",
        )
        return WorkoutExerciseEntity(
            id = value.requireString("id"),
            workoutId = value.requireString("workoutId"),
            exerciseId = value.requireString("exerciseId"),
            routineExerciseId = value.requireNullableString("routineExerciseId"),
            position = value.requireInt("position"),
            notes = value.requireNullableString("notes"),
            targetSetCount = value.requireNullableInt("targetSetCount"),
            repMin = value.requireNullableInt("repMin"),
            repMax = value.requireNullableInt("repMax"),
            targetRirTenths = value.requireNullableInt("targetRirTenths"),
            restSeconds = value.requireNullableInt("restSeconds"),
            loadIncrementGrams = value.requireNullableLong("loadIncrementGrams"),
            previousReferenceMode = value.requireNullableString("previousReferenceMode"),
        )
    }

    private fun encodeWorkoutSet(value: WorkoutSetEntity) = buildJsonObject {
        put("id", JsonPrimitive(value.id))
        put("workoutExerciseId", JsonPrimitive(value.workoutExerciseId))
        put("position", JsonPrimitive(value.position))
        put("type", JsonPrimitive(value.type))
        put("loadGrams", JsonPrimitive(value.loadGrams))
        put("reps", JsonPrimitive(value.reps))
        put("rirTenths", value.rirTenths?.let(::JsonPrimitive) ?: JsonNull)
        put("completedAt", value.completedAt?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun decodeWorkoutSet(element: JsonElement): WorkoutSetEntity {
        val value = element.requireObject("workoutSet")
        value.requireExactKeys(
            "id", "workoutExerciseId", "position", "type", "loadGrams", "reps", "rirTenths", "completedAt",
        )
        return WorkoutSetEntity(
            id = value.requireString("id"),
            workoutExerciseId = value.requireString("workoutExerciseId"),
            position = value.requireInt("position"),
            type = value.requireString("type"),
            loadGrams = value.requireLong("loadGrams"),
            reps = value.requireInt("reps"),
            rirTenths = value.requireNullableInt("rirTenths"),
            completedAt = value.requireNullableLong("completedAt"),
        )
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: CharacterCodingException) {
        throw BackupFormatException("El backup no está codificado como UTF-8 válido.", error)
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun JsonElement.requireObject(label: String): JsonObject = this as? JsonObject
        ?: throw BackupFormatException("$label debe ser un objeto JSON.")

    private fun JsonObject.requireObject(name: String): JsonObject = this[name] as? JsonObject
        ?: throw BackupFormatException("Falta el objeto obligatorio '$name'.")

    private fun JsonObject.requireArray(name: String): JsonArray = this[name] as? JsonArray
        ?: throw BackupFormatException("Falta el arreglo obligatorio '$name'.")

    private fun JsonObject.requireExactKeys(vararg expected: String) {
        val expectedKeys = expected.toSet()
        if (keys != expectedKeys) {
            val missing = expectedKeys - keys
            val unknown = keys - expectedKeys
            val details = buildList {
                if (missing.isNotEmpty()) add("faltan: ${missing.sorted().joinToString()}")
                if (unknown.isNotEmpty()) add("desconocidos: ${unknown.sorted().joinToString()}")
            }.joinToString("; ")
            throw BackupFormatException("Campos incompatibles en el backup${if (details.isEmpty()) "." else ": $details."}")
        }
    }

    private fun JsonObject.requireString(name: String): String {
        val primitive = this[name] as? JsonPrimitive
            ?: throw BackupFormatException("'$name' debe ser texto.")
        if (!primitive.isString) throw BackupFormatException("'$name' debe ser texto.")
        return primitive.content
    }

    private fun JsonObject.requireNullableString(name: String): String? {
        val value = this[name] ?: throw BackupFormatException("Falta '$name'.")
        if (value === JsonNull) return null
        val primitive = value as? JsonPrimitive
            ?: throw BackupFormatException("'$name' debe ser texto o null.")
        if (!primitive.isString) throw BackupFormatException("'$name' debe ser texto o null.")
        return primitive.content
    }

    private fun JsonObject.requireBoolean(name: String): Boolean {
        val primitive = this[name] as? JsonPrimitive
            ?: throw BackupFormatException("'$name' debe ser booleano.")
        if (primitive.isString) throw BackupFormatException("'$name' debe ser booleano.")
        return primitive.booleanOrNull ?: throw BackupFormatException("'$name' debe ser booleano.")
    }

    private fun JsonObject.requireInt(name: String): Int {
        val primitive = this[name] as? JsonPrimitive
            ?: throw BackupFormatException("'$name' debe ser entero.")
        if (primitive.isString) throw BackupFormatException("'$name' debe ser entero.")
        return primitive.intOrNull ?: throw BackupFormatException("'$name' debe ser entero.")
    }

    private fun JsonObject.requireNullableInt(name: String): Int? {
        val value = this[name] ?: throw BackupFormatException("Falta '$name'.")
        if (value === JsonNull) return null
        val primitive = value as? JsonPrimitive
            ?: throw BackupFormatException("'$name' debe ser entero o null.")
        if (primitive.isString) throw BackupFormatException("'$name' debe ser entero o null.")
        return primitive.intOrNull ?: throw BackupFormatException("'$name' debe ser entero o null.")
    }

    private fun JsonObject.requireLong(name: String): Long {
        val primitive = this[name] as? JsonPrimitive
            ?: throw BackupFormatException("'$name' debe ser entero largo.")
        if (primitive.isString) throw BackupFormatException("'$name' debe ser entero largo.")
        return primitive.longOrNull ?: throw BackupFormatException("'$name' debe ser entero largo.")
    }

    private fun JsonObject.requireNullableLong(name: String): Long? {
        val value = this[name] ?: throw BackupFormatException("Falta '$name'.")
        if (value === JsonNull) return null
        val primitive = value as? JsonPrimitive
            ?: throw BackupFormatException("'$name' debe ser entero largo o null.")
        if (primitive.isString) throw BackupFormatException("'$name' debe ser entero largo o null.")
        return primitive.longOrNull ?: throw BackupFormatException("'$name' debe ser entero largo o null.")
    }
}
