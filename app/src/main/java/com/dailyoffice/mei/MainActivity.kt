package com.dailyoffice.mei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyoffice.mei.ui.FinanceScreen
import com.dailyoffice.mei.ui.HomeScreen
import com.dailyoffice.mei.ui.InventoryScreen
import com.dailyoffice.mei.ui.ReceiptCaptureScreen
import com.dailyoffice.mei.ui.ReceiptReviewScreen
import com.dailyoffice.mei.viewmodel.ReceiptViewModel

private enum class Screen { HOME, CAPTURE, REVIEW, FINANCE, INVENTORY }

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
                        onReviewReady = { screen = Screen.REVIEW },
                        onFinance = { screen = Screen.FINANCE },
                        onInventory = { screen = Screen.INVENTORY }
                    )

                    Screen.CAPTURE -> ReceiptCaptureScreen(
                        onCaptured = { uri, text, hash ->
                            vm.beginReview(uri.toString(), text, hash)
                            screen = Screen.REVIEW
                        },
                        onCancel = { screen = Screen.HOME }
                    )

                    Screen.REVIEW -> ReceiptReviewScreen(
                        viewModel = vm,
                        onBack = { screen = Screen.HOME },
                        onSaved = { screen = Screen.HOME }
                    )

                    Screen.FINANCE -> FinanceScreen(
                        viewModel = vm,
                        onBack = { screen = Screen.HOME }
                    )

                    Screen.INVENTORY -> InventoryScreen(
                        viewModel = vm,
                        onBack = { screen = Screen.HOME }
                    )
                }
            }
        }
    }
}
