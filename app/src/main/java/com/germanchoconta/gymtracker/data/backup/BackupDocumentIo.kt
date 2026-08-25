package com.germanchoconta.gymtracker.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupDocumentIo(private val contentResolver: ContentResolver) {
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("El proveedor de documentos no entregó un stream de lectura.")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > BackupFormat.MAX_DOCUMENT_BYTES.toLong()) {
                    throw BackupFormatException("El archivo de backup supera el límite defensivo de 128 MiB.")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val output = contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("El proveedor de documentos no entregó un stream de escritura.")
        output.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }
}
