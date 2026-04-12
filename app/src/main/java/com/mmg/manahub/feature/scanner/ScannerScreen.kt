package com.mmg.manahub.feature.scanner

import android.Manifest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.*
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.AddToCollectionSheet
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack:    () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val toastState       = rememberMagicToastState()

    // Auto-launch the system permission dialog the first time the screen opens,
    // only when the permission has not been granted yet.
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Show MagicToast when a card has been successfully added.
    val cardAddedMessage = stringResource(R.string.scanner_card_added)
    LaunchedEffect(uiState.addedSuccessfully) {
        if (uiState.addedSuccessfully) {
            toastState.show(cardAddedMessage, MagicToastType.SUCCESS)
            viewModel.onSuccessDismissed()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color    = Color.Black,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint               = Color.White
                        )
                    }

                    Text(
                        text     = stringResource(R.string.scanner_title),
                        style    = MaterialTheme.typography.titleLarge,
                        color    = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    IconButton(onClick = viewModel::onToggleFlash) {
                        Icon(
                            imageVector = if (uiState.isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = stringResource(
                                if (uiState.isFlashOn) R.string.action_flash_off else R.string.action_flash_on
                            ),
                            tint = Color.White
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            when {
                cameraPermission.status.isGranted -> {
                    CameraPreview(
                        isFlashOn          = uiState.isFlashOn,
                        onCardNameDetected = viewModel::onCardNameDetected,
                        onToggleOcr        = viewModel::onToggleOcr,
                        isOcrEnabled       = uiState.isOcrEnabled,
                    )
                    ScannerOverlay(
                        detectedName = uiState.detectedName,
                        isSearching  = uiState.isSearching,
                        error        = uiState.error,
                        isOcrEnabled = uiState.isOcrEnabled,
                        onToggleOcr  = viewModel::onToggleOcr,
                    )
                }
                // Permission permanently denied — shouldShowRationale stays false after a denial.
                cameraPermission.status.shouldShowRationale -> {
                    CameraPermissionRequest(
                        isPermanentlyDenied = false,
                        onRequest           = { cameraPermission.launchPermissionRequest() },
                    )
                }
                else -> {
                    // Not granted and not asking for rationale: permanently denied.
                    CameraPermissionRequest(
                        isPermanentlyDenied = true,
                        onRequest           = { cameraPermission.launchPermissionRequest() },
                    )
                }
            }

            // MagicToast host replaces the old Snackbar
            MagicToastHost(state = toastState)
        }
    }

    // Confirm sheet after successful OCR + Scryfall lookup
    if (uiState.showConfirmSheet && uiState.foundCard != null) {
        AddToCollectionSheet(
            cardName  = uiState.foundCard!!.name,
            onConfirm = { isFoil: Boolean, _: Boolean, condition: String, language: String, qty: Int ->
                viewModel.onConfirmAdd(
                    scryfallId = uiState.foundCard!!.scryfallId,
                    isFoil     = isFoil,
                    condition  = condition,
                    language   = language,
                    quantity   = qty,
                )
            },
            onDismiss = viewModel::onDismissConfirmSheet,
            manaCost  = uiState.foundCard!!.manaCost,
            cardImage = uiState.foundCard!!.imageArtCrop,
            closeButton = true
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Camera preview wired to CardNameAnalyzer
//  - Camera is bound once and held in state; flash uses a separate LaunchedEffect.
//  - CardNameAnalyzer is created once via remember to avoid costly rebinds.
//  - A transparent tap overlay drives focus metering and OCR toggle.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPreview(
    isFlashOn:          Boolean,
    onCardNameDetected: (String) -> Unit,
    onToggleOcr:        () -> Unit,
    isOcrEnabled:       Boolean,
) {
    val context              = LocalContext.current
    val lifecycleOwner       = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Hold camera and PreviewView references so we can control them without rebinding.
    var camera        by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Stable reference to the latest callback — avoids recreating the analyzer on recomposition.
    val currentOnDetected by rememberUpdatedState(onCardNameDetected)
    val analyzer = remember { CardNameAnalyzer { name -> currentOnDetected(name) } }

    // Apply torch changes reactively without touching the camera binding.
    LaunchedEffect(isFlashOn) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { pv ->
                    previewViewRef = pv
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(pv.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                ContextCompat.getMainExecutor(ctx),
                                analyzer,
                            )
                        }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    } catch (_: Exception) { }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Transparent tap overlay: focus camera at tap point + toggle OCR.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val point = previewViewRef
                            ?.meteringPointFactory
                            ?.createPoint(offset.x, offset.y)
                        if (point != null) {
                            val action = FocusMeteringAction.Builder(point).build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                        }
                        if (!isOcrEnabled) {
                            onToggleOcr()
                        }
                    }
                }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scanner overlay — guide frame + status feedback + OCR toggle button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScannerOverlay(
    detectedName: String?,
    isSearching:  Boolean,
    error:        String?,
    isOcrEnabled: Boolean,
    onToggleOcr:  () -> Unit,
) {
    val accentColor = Color(0xFFC77DFF)

    Box(modifier = Modifier.fillMaxSize()) {

        // Corner guide frame
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth  = size.width * 0.85f
            val frameHeight = frameWidth * 0.72f   // MTG card proportion
            val left        = (size.width - frameWidth) / 2
            val top         = size.height * 0.20f

            val cornerLen   = 40f
            val strokeWidth = 3f

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
            text     = stringResource(R.string.scanner_instruction_point),
            style    = MaterialTheme.typography.bodyMedium,
            color    = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // Bottom status area
        Column(
            modifier          = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // OCR toggle button — sits above the status chip
            OcrToggleButton(
                isOcrEnabled = isOcrEnabled,
                accentColor  = accentColor,
                onClick      = onToggleOcr,
            )

            // Status chip
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
                                color       = accentColor,
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
                            text     = stringResource(R.string.scanner_instruction_steady),
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
//  OCR toggle button — shows scanning state and lets user pause/resume OCR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OcrToggleButton(
    isOcrEnabled: Boolean,
    accentColor:  Color,
    onClick:      () -> Unit,
) {
    val iconTint   = if (isOcrEnabled) accentColor else Color.White.copy(alpha = 0.40f)
    val labelColor = if (isOcrEnabled) accentColor else Color.White.copy(alpha = 0.40f)
    val label      = stringResource(
        if (isOcrEnabled) R.string.scanner_ocr_scanning else R.string.scanner_ocr_paused
    )

    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(20.dp),
        color   = Color.Black.copy(alpha = 0.65f),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Default.CropFree,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Camera permission request
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPermissionRequest(
    isPermanentlyDenied: Boolean,
    onRequest:           () -> Unit,
) {
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
        Text(
            text  = if (isPermanentlyDenied) {
                stringResource(R.string.scanner_permission_denied_settings)
            } else {
                stringResource(R.string.scanner_permission_rationale)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        // Only show the button when the user can still be prompted (not permanently denied).
        if (!isPermanentlyDenied) {
            Button(onClick = onRequest) {
                Text(stringResource(R.string.scanner_grant_permission))
            }
        }
    }
}
