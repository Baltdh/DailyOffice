package com.dailyoffice.mei.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object ReceiptOcr {
    suspend fun read(context: Context, uri: Uri): Pair<String, ParsedReceipt> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(image).await()
            result.text to ReceiptParser.parse(result.text)
        } finally {
            recognizer.close()
        }
    }
}
