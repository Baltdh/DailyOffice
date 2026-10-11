package com.dailyoffice.mei.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dailyoffice.mei.data.AccountingDocument
import com.dailyoffice.mei.data.AccountingDocumentType
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object AccountingArchive {
    private const val MAX_BYTES = 25L * 1024 * 1024
    private val allowedMime = setOf(
        "application/pdf", "text/xml", "application/xml", "text/csv",
        "text/plain", "application/octet-stream"
    )
    private val allowedExtensions = setOf("pdf", "xml", "csv", "txt", "ofx")

    fun import(context: Context, uri: Uri, companyId: Long, type: AccountingDocumentType): AccountingDocument {
        val resolver = context.contentResolver
        val originalName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex) && cursor.getLong(sizeIndex) > MAX_BYTES) {
                        throw IllegalArgumentException("Arquivo maior que 25 MB.")
                    }
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                }
            } ?: "documento"
        val safeName = originalName.substringAfterLast('/').substringAfterLast('\\').take(120)
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") { "Nome de arquivo inválido." }
        val extension = safeName.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri)?.lowercase()?.substringBefore(";") ?: "application/octet-stream"
        require(extension in allowedExtensions && (mime in allowedMime || mime.startsWith("text/"))) {
            "Formato não suportado. Use PDF, XML, CSV, TXT ou OFX."
        }
        require(companyId > 0) { "Selecione uma empresa válida." }
        val directory = File(context.filesDir, "accounting/$companyId")
        check(directory.isDirectory || directory.mkdirs()) { "Não foi possível preparar o arquivo." }
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_BYTES) { "Arquivo maior que 25 MB." }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                    require(total > 0L) { "Arquivo vazio." }
                }
            } ?: error("Não foi possível abrir o arquivo.")
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return AccountingDocument(
            companyId = companyId,
            type = type,
            displayName = safeName,
            mimeType = mime,
            localPath = target.absolutePath,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        )
    }
}
