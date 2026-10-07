package com.dailyoffice.mei.receipt

import android.content.Context
import android.net.Uri
import java.io.File
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
}
