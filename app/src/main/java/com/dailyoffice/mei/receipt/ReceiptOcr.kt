package com.dailyoffice.mei.receipt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object ReceiptOcr {
    fun read(
        context: Context,
        uri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }
            .getOrElse {
                onFailure(it)
                return
            }

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { onSuccess(it.text) }
            .addOnFailureListener(onFailure)
    }
}
