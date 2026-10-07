package com.dailyoffice.mei.receipt

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object ReceiptArchive {
    fun newCameraFile(context: Context): File {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        return File(dir, "receipt_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
    }

    fun archiveImported(context: Context, source: Uri): Uri {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val target = File(dir, "import_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Não foi possível abrir a imagem selecionada." }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(target)
    }

    fun sha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = if (uri.scheme == "file") {
            val path = requireNotNull(uri.path) { "Arquivo do comprovante sem caminho." }
            File(path).inputStream()
        } else {
            requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Não foi possível abrir o comprovante para calcular a assinatura."
            }
        }

        input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun deleteArchived(uriString: String) {
        runCatching {
            val uri = Uri.parse(uriString)
            val path = uri.path ?: return
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }
}
