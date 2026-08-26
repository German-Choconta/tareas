package com.germanchoconta.gymtracker.wear

import android.util.AtomicFile
import com.germanchoconta.gymtracker.wear.protocol.WearOperationResult
import com.germanchoconta.gymtracker.wear.protocol.WearOperationStatus
import java.io.File
import java.util.Base64
import java.util.Properties

/**
 * Durable delivery metadata for the currently active workout.
 *
 * This file deliberately lives under noBackupFilesDir in production. It stores
 * operation/workout IDs and terminal delivery results only: never load, reps,
 * RIR, timestamps, names, notes, health data, or canonical workout state.
 * Room remains the sole workout truth.
 */
internal class PhoneWearOperationReceiptStore(
    directory: File,
    fileName: String = "wear_operation_receipts_v1.properties",
) {
    private val file = AtomicFile(File(directory.apply { mkdirs() }, fileName))

    @Synchronized
    fun get(operationId: String): WearOperationResult? =
        readEntries()[operationId]?.result

    @Synchronized
    fun put(workoutId: String, result: WearOperationResult) {
        val entries = readEntries().toMutableMap()
        entries[result.operationId] = Entry(workoutId, result)
        writeEntries(entries)
    }

    @Synchronized
    fun retainOnly(workoutId: String?) {
        val entries = readEntries()
        val retained = if (workoutId == null) {
            emptyMap()
        } else {
            entries.filterValues { it.workoutId == workoutId }
        }
        if (retained != entries) writeEntries(retained)
    }

    private fun readEntries(): Map<String, Entry> {
        if (!file.baseFile.exists()) return emptyMap()
        val properties = Properties()
        return runCatching {
            file.openRead().use { input -> properties.load(input) }
            properties.stringPropertyNames().mapNotNull { operationId ->
                decodeEntry(operationId, properties.getProperty(operationId))?.let { operationId to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun writeEntries(entries: Map<String, Entry>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        val properties = Properties()
        entries.toSortedMap().forEach { (operationId, entry) ->
            properties.setProperty(operationId, encodeEntry(entry))
        }
        val output = file.startWrite()
        try {
            properties.store(output, null)
            file.finishWrite(output)
        } catch (throwable: Throwable) {
            file.failWrite(output)
            throw throwable
        }
    }

    private fun encodeEntry(entry: Entry): String = listOf(
        encode(entry.workoutId),
        entry.result.status.name,
        encode(entry.result.reason.orEmpty()),
    ).joinToString(".")

    private fun decodeEntry(operationId: String, encoded: String?): Entry? {
        val parts = encoded?.split('.', limit = 3) ?: return null
        if (parts.size != 3) return null
        val workoutId = decode(parts[0]) ?: return null
        val status = runCatching { WearOperationStatus.valueOf(parts[1]) }.getOrNull() ?: return null
        val decodedReason = decode(parts[2]) ?: return null
        return Entry(
            workoutId = workoutId,
            result = WearOperationResult(operationId, status, decodedReason.ifEmpty { null }),
        )
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()

    private data class Entry(
        val workoutId: String,
        val result: WearOperationResult,
    )
}
