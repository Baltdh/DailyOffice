package com.dailyoffice.mei.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dailyoffice.mei.receipt.ReceiptArchive
import com.dailyoffice.mei.receipt.ReceiptOcr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptCaptureScreen(
    onCaptured: (Uri, String, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted = it }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fotografar comprovante") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Voltar") }
                }
            )
        }
    ) { pad ->
        if (!permissionGranted) {
            Column(
                Modifier.padding(pad).padding(24.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("A câmera é necessária para fotografar o comprovante.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir câmera")
                }
            }
        } else {
            Box(Modifier.padding(pad).fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                runCatching {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture
                                    )
                                    imageCapture = capture
                                }.onFailure {
                                    error = it.message ?: "Falha ao abrir a câmera."
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    }
                )

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    error?.let {
                        Card {
                            Text(
                                it,
                                Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = imageCapture != null && !busy,
                        onClick = {
                            val capture = imageCapture ?: return@Button
                            busy = true
                            error = null
                            val file = ReceiptArchive.newCameraFile(context)
                            val options = ImageCapture.OutputFileOptions.Builder(file).build()
                            capture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                        val uri = Uri.fromFile(file)
                                        val hash = runCatching {
                                            ReceiptArchive.sha256(context, uri)
                                        }.getOrElse {
                                            busy = false
                                            error = it.message ?: "Não foi possível validar a foto."
                                            return
                                        }
                                        ReceiptOcr.read(
                                            context,
                                            uri,
                                            onSuccess = { text ->
                                                busy = false
                                                onCaptured(uri, text, hash)
                                            },
                                            onFailure = {
                                                busy = false
                                                error = it.message ?: "Não foi possível ler o comprovante."
                                            }
                                        )
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        busy = false
                                        error = exception.message ?: "Falha ao salvar a foto."
                                    }
                                }
                            )
                        }
                    ) {
                        Text(if (busy) "Lendo comprovante..." else "Capturar e ler")
                    }
                }
            }
        }
    }
}
