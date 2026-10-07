package com.dailyoffice.mei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyoffice.mei.ui.HomeScreen
import com.dailyoffice.mei.ui.ReceiptCaptureScreen
import com.dailyoffice.mei.ui.ReceiptReviewScreen
import com.dailyoffice.mei.viewmodel.ReceiptViewModel

private enum class Screen { HOME, CAPTURE, REVIEW }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: ReceiptViewModel = viewModel()
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

                when (screen) {
                    Screen.HOME -> HomeScreen(
                        viewModel = vm,
                        onCapture = { screen = Screen.CAPTURE },
                        onReviewReady = { screen = Screen.REVIEW }
                    )
                    Screen.CAPTURE -> ReceiptCaptureScreen(
                        onCaptured = { uri, text ->
                            vm.beginReview(uri.toString(), text)
                            screen = Screen.REVIEW
                        },
                        onCancel = { screen = Screen.HOME }
                    )
                    Screen.REVIEW -> ReceiptReviewScreen(
                        viewModel = vm,
                        onBack = { screen = Screen.HOME },
                        onSaved = { screen = Screen.HOME }
                    )
                }
            }
        }
    }
}
