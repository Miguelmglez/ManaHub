package feature.scanner


import android.Manifest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import core.domain.model.Card
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack:    () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan card") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            when {
                !cameraPermission.status.isGranted -> {
                    CameraPermissionRequest(onRequest = { cameraPermission.launchPermissionRequest() })
                }
                uiState.isLoadingCard -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    CameraPreview(
                        isActive       = uiState.isScanning,
                        onBarcodeFound = viewModel::onBarcodeDetected,
                    )
                    // Scanning overlay
                    ScanOverlay()
                }
            }

            // Error
            uiState.error?.let { err ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::onErrorDismissed) { Text("OK") }
                    },
                ) { Text(err) }
            }

            // Success
            if (uiState.addedSuccessfully) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    viewModel.onSuccessDismissed()
                }
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                ) { Text("Card added to collection!") }
            }
        }
    }

    // Confirm sheet after scan
    if (uiState.showConfirmSheet && uiState.scannedCard != null) {
        AddCardConfirmSheet(
            card      = uiState.scannedCard!!,
            onConfirm = { isFoil, condition, language, qty ->
                viewModel.onConfirmAdd(
                    scryfallId = uiState.pendingScryfallId!!,
                    isFoil     = isFoil,
                    condition  = condition,
                    language   = language,
                    quantity   = qty,
                )
            },
            onDismiss = viewModel::onDismissConfirmSheet,
        )
    }
}

@Composable
private fun CameraPreview(
    isActive:       Boolean,
    onBarcodeFound: (String) -> Unit,
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor      = remember { Executors.newSingleThreadExecutor() }
    val scanner       = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (!isActive) { imageProxy.close(); return@setAnalyzer }
                            val mediaImage = imageProxy.image ?: run { imageProxy.close(); return@setAnalyzer }
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let { onBarcodeFound(it) }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ScanOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.Center).size(240.dp),
            color    = androidx.compose.ui.graphics.Color.Transparent,
            border   = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shape    = MaterialTheme.shapes.medium,
        ) {}
        Text(
            text     = "Point at a card's barcode",
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun CameraPermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Camera access needed to scan cards", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardConfirmSheet(
    card:      Card,
    onConfirm: (isFoil: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val conditions = listOf("NM", "LP", "MP", "HP", "DMG")
    val languages  = listOf("en", "ja", "de", "fr", "es", "pt", "it", "ko", "ru")
    var isFoil    by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf("NM") }
    var language  by remember { mutableStateOf("en") }
    var qty       by remember { mutableIntStateOf(1) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(card.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text  = "${card.setName} · ${card.rarity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            card.priceUsd?.let {
                Text(
                    text  = "Market price: $${String.format("%.2f", it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            HorizontalDivider()

            // Foil toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foil", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isFoil, onCheckedChange = { isFoil = it })
            }

            // Quantity
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { if (qty > 1) qty-- }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text("$qty", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { qty++ }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }

            // Condition
            Text("Condition", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(selected = c == condition, onClick = { condition = c }, label = { Text(c) })
                }
            }

            // Language
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value         = language.uppercase(),
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Language") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text    = { Text(lang.uppercase()) },
                            onClick = { language = lang; expanded = false },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onConfirm(isFoil, condition, language, qty) }) { Text("Add to collection") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}