package com.mmg.magicfolder.feature.scanner

import android.Manifest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.*
import com.mmg.magicfolder.core.domain.model.Card

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack:    () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            when {
                !cameraPermission.status.isGranted -> {
                    CameraPermissionRequest(onRequest = { cameraPermission.launchPermissionRequest() })
                }
                else -> {
                    CameraPreview(onCardNameDetected = viewModel::onCardNameDetected)
                    ScannerOverlay(
                        detectedName = uiState.detectedName,
                        isSearching  = uiState.isSearching,
                        error        = uiState.error,
                    )
                }
            }

            // Success snackbar
            if (uiState.addedSuccessfully) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1_500)
                    viewModel.onSuccessDismissed()
                }
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                ) { Text("Card added to collection!") }
            }
        }
    }

    // Confirm sheet after successful OCR + Scryfall lookup
    if (uiState.showConfirmSheet && uiState.foundCard != null) {
        AddCardConfirmSheet(
            card      = uiState.foundCard!!,
            onConfirm = { isFoil, condition, language, qty ->
                viewModel.onConfirmAdd(
                    scryfallId = uiState.foundCard!!.scryfallId,
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

// ─────────────────────────────────────────────────────────────────────────────
//  Camera preview wired to CardNameAnalyzer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPreview(onCardNameDetected: (String) -> Unit) {
    val context          = LocalContext.current
    val lifecycleOwner   = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        ContextCompat.getMainExecutor(previewView.context),
                        CardNameAnalyzer { name -> onCardNameDetected(name) },
                    )
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer,
                )
            } catch (_: Exception) { }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scanner overlay — guide frame + status feedback
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScannerOverlay(
    detectedName: String?,
    isSearching:  Boolean,
    error:        String?,
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Corner guide frame
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth  = size.width * 0.85f
            val frameHeight = frameWidth * 0.72f   // MTG card proportion
            val left        = (size.width - frameWidth) / 2
            val top         = size.height * 0.20f

            val cornerLen   = 40f
            val strokeWidth = 3f
            val accentColor = Color(0xFFC77DFF)

            // Top-left
            drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
            drawLine(accentColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)
            // Top-right
            drawLine(accentColor, Offset(left + frameWidth, top), Offset(left + frameWidth - cornerLen, top), strokeWidth)
            drawLine(accentColor, Offset(left + frameWidth, top), Offset(left + frameWidth, top + cornerLen), strokeWidth)
            // Bottom-left
            drawLine(accentColor, Offset(left, top + frameHeight), Offset(left + cornerLen, top + frameHeight), strokeWidth)
            drawLine(accentColor, Offset(left, top + frameHeight), Offset(left, top + frameHeight - cornerLen), strokeWidth)
            // Bottom-right
            drawLine(accentColor, Offset(left + frameWidth, top + frameHeight), Offset(left + frameWidth - cornerLen, top + frameHeight), strokeWidth)
            drawLine(accentColor, Offset(left + frameWidth, top + frameHeight), Offset(left + frameWidth, top + frameHeight - cornerLen), strokeWidth)

            // Dashed guide line — marks the card name zone (top 18% of card)
            val nameZoneBottom = top + frameHeight * 0.18f
            drawLine(
                color       = accentColor.copy(alpha = 0.35f),
                start       = Offset(left + 20f, nameZoneBottom),
                end         = Offset(left + frameWidth - 20f, nameZoneBottom),
                strokeWidth = 1f,
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }

        // Top instruction label
        Text(
            text     = "Point at the card name",
            style    = MaterialTheme.typography.bodyMedium,
            color    = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // Bottom status area
        Box(
            modifier        = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE63946).copy(alpha = 0.85f),
                    ) {
                        Text(
                            text     = error,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
                isSearching && detectedName != null -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                    ) {
                        Row(
                            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                color       = Color(0xFFC77DFF),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text  = "\"$detectedName\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.60f),
                    ) {
                        Text(
                            text     = "Hold card steady in good light",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Camera permission request
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Camera access needed to scan cards", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Confirm sheet — reused after OCR identifies the card
// ─────────────────────────────────────────────────────────────────────────────

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
            modifier            = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
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
            card.priceEur?.let {
                Text(
                    text  = "Market price: ${String.format("%.2f", it)}€",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foil", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isFoil, onCheckedChange = { isFoil = it })
            }

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

            Text("Condition", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(selected = c == condition, onClick = { condition = c }, label = { Text(c) })
                }
            }

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

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onConfirm(isFoil, condition, language, qty) }) {
                    Text("Add to collection")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
