package com.mmg.manahub.feature.scanner

import android.Manifest
import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.cancel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// ─────────────────────────────────────────────────────────────────────────────
//  Root screen — entry point from navigation
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onNavigateToCardDetail: (scryfallId: String) -> Unit = {},
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val toastState = rememberMagicToastState()
    val preferredCurrency = LocalPreferredCurrency.current

    // Auto-launch permission dialog on first composition
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // One-shot toast when a card is auto-added in Quick Mode
    val addedPrefix = stringResource(R.string.scanner_toast_added_prefix)
    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            toastState.show("$addedPrefix $msg", MagicToastType.SUCCESS)
            viewModel.onToastDismissed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermission.status.isGranted -> {
                when {
                    else -> {
                        // OCR pipeline — no DB download required; camera starts immediately
                        CameraPreview(
                            isFlashOn = uiState.isFlashOn,
                            detectedCorners = uiState.detectedCorners,
                            onRecognitionResult = viewModel::onRecognitionResult,
                            onFlashAvailability = viewModel::onFlashAvailabilityChanged,
                        )

                        // COMMENTED OUT — banner only relevant for embedding DB pipeline
                        // if (!uiState.embeddingDbLoaded) {
                        //     EmbeddingDbNotLoadedBanner(isUpdating = uiState.isEmbeddingDbUpdating)
                        // }

                        // Top bar with back button and right-side controls
                        TopScannerControls(
                            onBack = onBack,
                            queueCount = uiState.scanSession.cards.sumOf { it.quantity },
                            isFlashOn = uiState.isFlashOn,
                            hasFlash = uiState.hasFlash,
                            onOpenQueue = viewModel::onOpenQueue,
                            onToggleFlash = viewModel::onToggleFlash,
                            onOpenSettings = viewModel::onOpenSettings,
                        )

                        // Bottom floating card info
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                        ) {
                            DetectedCardOverlay(
                                card = uiState.lastDetectedCard,
                                isSearching = uiState.isSearching,
                                error = uiState.error,
                                isQuickMode = uiState.isQuickMode,
                                isLookupOnly = uiState.isLookupOnly,
                                languageMismatch = uiState.languageMismatch,
                                preferredCurrency = preferredCurrency,
                                onManualAdd = viewModel::onManualAddCurrentCard,
                                onOpenPriceDetail = viewModel::onOpenPriceDetail,
                            )
                        }

                        // Ambiguity selector
                        if (uiState.showAmbiguitySelector && uiState.lastDetectedCard != null) {
                            AmbiguityDropdown(
                                cardName = uiState.lastDetectedCard!!.name,
                                onConfirm = {
                                    viewModel.onManualAddCurrentCard()
                                    viewModel.onDismissAmbiguitySelector()
                                },
                                onSkip = viewModel::onDismissAmbiguitySelector,
                            )
                        }
                    }
                }
            }

            cameraPermission.status.shouldShowRationale -> {
                CameraPermissionRequest(
                    isPermanentlyDenied = false,
                    onRequest = { cameraPermission.launchPermissionRequest() },
                )
            }

            else -> {
                CameraPermissionRequest(
                    isPermanentlyDenied = true,
                    onRequest = { cameraPermission.launchPermissionRequest() },
                )
            }
        }

        MagicToastHost(state = toastState)
    }

    // Queue bottom sheet
    if (uiState.showQueueSheet) {
        ScanQueueSheet(
            session = uiState.scanSession,
            multiSelectedIds = uiState.multiSelectedIds,
            preferredCurrency = preferredCurrency,
            onDismiss = viewModel::onCloseQueue,
            onRemoveCard = viewModel::onRemoveSessionCard,
            onEditCard = viewModel::onEditScannedCard,
            onToggleSelect = viewModel::onToggleMultiSelect,
            onDeleteSelected = viewModel::onDeleteSelected,
            onClearSession = viewModel::onClearSession,
            onAddAllToCollection = viewModel::onAddAllToCollection,
        )
    }

    // Settings bottom sheet
    if (uiState.showSettingsSheet) {
        ScannerSettingsSheet(
            isQuickMode = uiState.isQuickMode,
            isLookupOnly = uiState.isLookupOnly,
            isSoundEnabled = uiState.isSoundEnabled,
            onDismiss = viewModel::onCloseSettings,
            onToggleQuickMode = viewModel::onToggleQuickMode,
            onToggleLookupOnly = viewModel::onToggleLookupOnly,
            onToggleSound = viewModel::onToggleSound,
        )
    }

    // Edit sheet
    if (uiState.showEditSheet && uiState.editingCard != null) {
        EditScannedCardSheet(
            scannedCard = uiState.editingCard!!,
            availablePrints = uiState.availablePrints,
            isLoadingPrints = uiState.isLoadingPrints,
            onDismiss = viewModel::onCloseEditSheet,
            onConfirm = viewModel::onUpdateScannedCard,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Camera preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPreview(
    isFlashOn: Boolean,
    detectedCorners: List<PointF>?,
    // COMMENTED OUT — embeddingDatabase no longer needed with ML Kit OCR pipeline
    // embeddingDatabase: EmbeddingDatabase,
    onRecognitionResult: (RecognitionResult) -> Unit,
    onFlashAvailability: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val entryPoint = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ScannerEntryPoint::class.java)
    }
    val cardRepository = remember { entryPoint.cardRepository() }
    val cardOcrAnalyzer = remember { entryPoint.cardOcrAnalyzer() }
    // COMMENTED OUT — TFLite model no longer used in OCR pipeline
    // val cardEmbeddingModel = remember { entryPoint.cardEmbeddingModel() }

    val lastFrameWidth = remember { AtomicInteger(0) }
    val lastFrameHeight = remember { AtomicInteger(0) }
    val lastRotationDegrees = remember { AtomicInteger(0) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val recognizerScope = remember {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        )
    }
    val recognizer = remember {
        CardRecognizer(
            cardRepository = cardRepository,
            cardOcrAnalyzer = cardOcrAnalyzer,
            scope = recognizerScope,
            onResult = onRecognitionResult,
            // COMMENTED OUT — embedding params replaced by OCR
            // embeddingDatabase = embeddingDatabase,
            // cardEmbeddingModel = cardEmbeddingModel,
        )
    }

    androidx.compose.runtime.DisposableEffect(recognizer) {
        onDispose {
            recognizerScope.cancel()
            recognizer.release()
            analysisExecutor.shutdown()
        }
    }

    val frameMetadataAnalyzer = remember {
        FrameMetadataAnalyzer(
            delegate = recognizer,
            onFrameMetadata = { w, h, r ->
                lastFrameWidth.set(w)
                lastFrameHeight.set(h)
                lastRotationDegrees.set(r)
            },
        )
    }

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
                        .setTargetResolution(android.util.Size(720, 1280))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                analysisExecutor,
                                frameMetadataAnalyzer,
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
                        onFlashAvailability(camera?.cameraInfo?.hasFlashUnit() ?: false)
                    } catch (_: Exception) {}
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val corners = detectedCorners ?: return@Canvas
            val frameWidth = lastFrameWidth.get()
            val frameHeight = lastFrameHeight.get()
            val rotationDegrees = lastRotationDegrees.get()

            if (frameWidth <= 0 || frameHeight <= 0 || corners.size < 4) return@Canvas

            val mapped = corners.map { pt ->
                mapFrameToCanvas(pt, frameWidth, frameHeight, rotationDegrees, size)
            }

            val path = Path().apply {
                moveTo(mapped[0].x, mapped[0].y)
                for (i in 1 until mapped.size) lineTo(mapped[i].x, mapped[i].y)
                close()
            }

            drawPath(
                path = path,
                color = Color(0xFFFFC107),
                style = Stroke(width = 4.dp.toPx()),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val point = previewViewRef?.meteringPointFactory?.createPoint(
                            offset.x,
                            offset.y,
                        )
                        if (point != null) {
                            val action = FocusMeteringAction.Builder(point).build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                        }
                    }
                },
        )
    }
}

private class FrameMetadataAnalyzer(
    private val delegate: CardRecognizer,
    private val onFrameMetadata: (width: Int, height: Int, rotationDegrees: Int) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        // use try-finally to ensure imageProxy is always closed if CardRecognizer fails
        try {
            android.util.Log.d("ScannerScreen", "FrameMetadataAnalyzer.analyze START: frame=${imageProxy.width}x${imageProxy.height}")
            onFrameMetadata(
                imageProxy.width,
                imageProxy.height,
                imageProxy.imageInfo.rotationDegrees,
            )
            delegate.analyze(imageProxy)
        } catch (e: Exception) {
            android.util.Log.e("ScannerScreen", "FrameMetadataAnalyzer failed", e)
            imageProxy.close()
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ScannerEntryPoint {
    fun cardRepository(): com.mmg.manahub.core.domain.repository.CardRepository
    fun cardOcrAnalyzer(): CardOcrAnalyzer
    // COMMENTED OUT — TFLite embedding model replaced by ML Kit OCR
    // fun cardEmbeddingModel(): CardEmbeddingModel
}

private fun mapFrameToCanvas(
    point: PointF,
    frameWidth: Int,
    frameHeight: Int,
    rotationDegrees: Int,
    canvasSize: Size,
): Offset {
    val w = frameWidth.toFloat()
    val h = frameHeight.toFloat()
    return when (rotationDegrees) {
        90 -> Offset(
            x = (1f - point.y / h) * canvasSize.width,
            y = (point.x / w) * canvasSize.height,
        )
        270 -> Offset(
            x = (point.y / h) * canvasSize.width,
            y = (1f - point.x / w) * canvasSize.height,
        )
        180 -> Offset(
            x = (1f - point.x / w) * canvasSize.width,
            y = (point.y / h) * canvasSize.height,
        )
        else -> Offset(
            x = (point.x / w) * canvasSize.width,
            y = (point.y / h) * canvasSize.height,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Hash DB not-loaded banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmbeddingDbNotLoadedBanner(isUpdating: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC1A1A1A))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color(0xFFFFA000),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFA000),
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = stringResource(R.string.scanner_db_not_loaded),
                style = MaterialTheme.magicTypography.labelSmall,
                color = Color.White,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Embedding DB setup screen — shown on first launch before the DB is downloaded
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmbeddingDbSetupScreen(
    onBack: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
) {
    val accentColor = MaterialTheme.magicColors.primaryAccent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 8.dp),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }

        // Centered content
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(56.dp),
            )

            Text(
                text = stringResource(R.string.scanner_db_setup_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.scanner_db_setup_desc),
                style = MaterialTheme.magicTypography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            val trackColor = accentColor.copy(alpha = 0.25f)
            if (isDownloading && downloadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accentColor,
                    trackColor = trackColor,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = accentColor,
                    trackColor = trackColor,
                )
            }

            val statusText = if (isDownloading) {
                if (downloadProgress > 0f) {
                    stringResource(R.string.scanner_db_setup_downloading, (downloadProgress * 100).toInt())
                } else {
                    stringResource(R.string.scanner_db_setup_waiting)
                }
            } else {
                stringResource(R.string.scanner_db_setup_waiting)
            }
            Text(
                text = statusText,
                style = MaterialTheme.magicTypography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.scanner_db_setup_size_hint),
                style = MaterialTheme.magicTypography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top Scanner Controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopScannerControls(
    onBack: () -> Unit,
    queueCount: Int,
    isFlashOn: Boolean,
    hasFlash: Boolean,
    onOpenQueue: () -> Unit,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(Color.Black.copy(0.4f), CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Queue button with badge
            Box {
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color.White)
                }
                if (queueCount > 0) {
                    Surface(
                        color = Color(0xFFFFA000), // Amber
                        shape = CircleShape,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = queueCount.toString(),
                                style = MaterialTheme.magicTypography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 0.sp
                                ),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Flash button
            if (hasFlash) {
                IconButton(
                    onClick = onToggleFlash,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = null,
                        tint = if (isFlashOn) Color(0xFFFFD54F) else Color.White
                    )
                }
            }

            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Detected Card Overlay (Floating Box)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetectedCardOverlay(
    card: Card?,
    isSearching: Boolean,
    error: String?,
    isQuickMode: Boolean,
    isLookupOnly: Boolean,
    languageMismatch: Boolean,
    preferredCurrency: PreferredCurrency,
    onManualAdd: () -> Unit,
    onOpenPriceDetail: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (card != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card Art Thumbnail
                        AsyncImage(
                            model = card.imageArtCrop ?: card.imageNormal,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        // Name and Set Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.name,
                                style = ty.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ManaSymbolImage(token = card.setCode, size = 16.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = card.setName,
                                    style = ty.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.scanner_trend_price_prefix,
                                    PriceFormatter.formatFromScryfall(card.priceUsd, card.priceEur, preferredCurrency),
                                ),
                                style = ty.labelSmall.copy(letterSpacing = 0.sp),
                                color = Color(0xFF64B5F6) // Light Blue
                            )
                        }

                        // Action arrow / manual add
                        if (!isQuickMode && !isLookupOnly) {
                            IconButton(
                                onClick = onManualAdd,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(mc.primaryAccent, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(onClick = onOpenPriceDetail) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Attribute Pill (Normal, Set, Lang, Qty)
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.scanner_price_detail_normal),
                                style = ty.labelSmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color.White.copy(alpha = 0.2f)))
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ManaSymbolImage(token = card.setCode, size = 14.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "#${card.collectorNumber}",
                                    style = ty.labelSmall,
                                    color = Color.White
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color.White.copy(alpha = 0.2f)))
                            Text(
                                text = card.lang.uppercase(),
                                style = ty.labelSmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color.White.copy(alpha = 0.2f)))
                            Text(
                                text = "+1",
                                style = ty.labelSmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (isSearching) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
                    Text(text = stringResource(R.string.scanner_searching_indicator), style = ty.bodySmall, color = Color.White)
                }
            } else {
                Text(
                    text = stringResource(R.string.scanner_point_at_card),
                    style = ty.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color.White.copy(alpha = 0.2f)))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scan Queue Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanQueueSheet(
    session: ScanSession,
    multiSelectedIds: Set<String>,
    preferredCurrency: PreferredCurrency,
    onDismiss: () -> Unit,
    onRemoveCard: (ScannedCard) -> Unit,
    onEditCard: (ScannedCard) -> Unit,
    onToggleSelect: (ScannedCard) -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSession: () -> Unit,
    onAddAllToCollection: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    
    val filtered = remember(session.cards, searchQuery) {
        if (searchQuery.isBlank()) session.cards
        else session.cards.filter { it.card.name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.scanner_queue_title, session.cards.size),
                    style = ty.titleMedium,
                    color = Color.White
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.scanner_queue_search_placeholder), color = Color.White.copy(0.4f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(0.4f)) },
                trailingIcon = { Icon(Icons.Default.Add, null, tint = Color.White) },
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White.copy(0.1f),
                    focusedContainerColor = Color.White.copy(0.1f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(16.dp))

            // Card List
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.timestamp }) { entry ->
                    QueueCardItem(
                        entry = entry,
                        preferredCurrency = preferredCurrency,
                        onEdit = { onEditCard(entry) },
                        onDelete = { onRemoveCard(entry) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClearSession,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f))
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scanner_queue_clear))
                }

                Button(
                    onClick = onAddAllToCollection,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000), contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scanner_queue_add_all))
                }
            }
        }
    }
}

@Composable
private fun QueueCardItem(
    entry: ScannedCard,
    preferredCurrency: PreferredCurrency,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = entry.card.imageArtCrop ?: entry.card.imageNormal,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${entry.quantity}x ${entry.card.name}",
                style = ty.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ManaSymbolImage(token = entry.card.setCode, size = 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${entry.card.setName} #${entry.card.collectorNumber}",
                    style = ty.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttrTag(entry.language.uppercase())
                AttrTag(entry.condition)
            }
            Text(
                text = PriceFormatter.formatFromScryfall(entry.card.priceUsd, entry.card.priceEur, preferredCurrency),
                style = ty.bodySmall.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(R.string.scanner_recently_added),
                style = ty.bodySmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.4f)
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, tint = Color.White.copy(0.6f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.White.copy(0.6f))
            }
        }
    }
}

@Composable
private fun AttrTag(text: String) {
    Surface(
        color = Color.White.copy(0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Edit Scanned Card Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScannedCardSheet(
    scannedCard: ScannedCard,
    availablePrints: List<Card>,
    isLoadingPrints: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ScannedCard) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var quantity by remember { mutableStateOf(scannedCard.quantity) }
    var selectedCard by remember { mutableStateOf(scannedCard.card) }
    var isFoil by remember { mutableStateOf(scannedCard.isFoil) }
    var language by remember { mutableStateOf(scannedCard.language) }
    var condition by remember { mutableStateOf(scannedCard.condition) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = selectedCard.name, style = ty.titleLarge, color = Color.White)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card Image
                AsyncImage(
                    model = selectedCard.imageNormal,
                    contentDescription = null,
                    modifier = Modifier
                        .width(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Set Picker (simplified dropdown for now)
                    AttributeDropdown(
                        label = stringResource(R.string.scanner_edit_set_label),
                        value = selectedCard.setName,
                        icon = { ManaSymbolImage(token = selectedCard.setCode, size = 16.dp) },
                        onClick = { /* Open detailed print picker */ }
                    )

                    // Quantity Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.scanner_edit_quantity), style = ty.bodyMedium, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (quantity > 1) quantity-- }) {
                                Icon(Icons.Default.Remove, null, tint = Color.White)
                            }
                            Text("$quantity", style = ty.titleMedium, color = Color.White)
                            IconButton(onClick = { quantity++ }) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                        }
                    }

                    // Foil Toggle (simplified as text for now)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.scanner_edit_foil_label), style = ty.bodyMedium, color = Color.White)
                        Text(
                            text = if (isFoil) stringResource(R.string.scanner_edit_foil_value) else stringResource(R.string.scanner_edit_normal_value),
                            style = ty.bodyMedium,
                            color = mc.primaryAccent,
                            modifier = Modifier.clickable { isFoil = !isFoil }
                        )
                    }

                    // Language Dropdown
                    AttributeDropdown(
                        label = stringResource(R.string.scanner_edit_language_label),
                        value = language.uppercase(),
                        onClick = { /* Open lang picker */ }
                    )

                    // Condition Dropdown
                    AttributeDropdown(
                        label = stringResource(R.string.scanner_edit_condition_label),
                        value = condition,
                        onClick = { /* Open condition picker */ }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = {
                    onConfirm(scannedCard.copy(
                        card = selectedCard,
                        quantity = quantity,
                        isFoil = isFoil,
                        language = language,
                        condition = condition
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000), contentColor = Color.Black)
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scanner_edit_save))
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttributeDropdown(
    label: String,
    value: String,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.magicTypography.labelSmall, color = Color.White.copy(0.6f))
        Surface(
            color = Color.White.copy(0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        icon()
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text = value, style = MaterialTheme.magicTypography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(0.6f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Price detail / Settings / Permission — reused or simplified
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerSettingsSheet(
    isQuickMode: Boolean,
    isLookupOnly: Boolean,
    isSoundEnabled: Boolean,
    // COMMENTED OUT — embedding DB params replaced by ML Kit OCR (no DB required)
    // embeddingDbVersion: Int,
    // embeddingDbCardCount: Int,
    // isEmbeddingDbUpdating: Boolean,
    onDismiss: () -> Unit,
    onToggleQuickMode: () -> Unit,
    onToggleLookupOnly: () -> Unit,
    onToggleSound: () -> Unit,
) {
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1C1C1E)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = stringResource(R.string.scanner_settings_title), style = ty.titleMedium, color = Color.White)
            SettingsToggleRow(stringResource(R.string.scanner_quick_mode), stringResource(R.string.scanner_quick_mode_desc), isQuickMode, { onToggleQuickMode() })
            SettingsToggleRow(stringResource(R.string.scanner_lookup_only), stringResource(R.string.scanner_lookup_only_desc), isLookupOnly, { onToggleLookupOnly() })
            SettingsToggleRow(stringResource(R.string.scanner_sound_effects), stringResource(R.string.scanner_sound_effects_desc), isSoundEnabled, { onToggleSound() })

            // COMMENTED OUT — embedding DB status row replaced by ML Kit OCR (always ready)
            // HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            // Row(...) { /* embeddingDbVersion, embeddingDbCardCount, isEmbeddingDbUpdating UI */ }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.magicTypography.bodyMedium, color = Color.White)
            Text(text = subtitle, style = MaterialTheme.magicTypography.bodySmall, color = Color.White.copy(0.6f))
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CameraPermissionRequest(isPermanentlyDenied: Boolean, onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.scanner_permission_camera_required), color = Color.White)
            Button(onClick = onRequest) { Text(stringResource(R.string.scanner_permission_grant)) }
        }
    }
}

@Composable
private fun AmbiguityDropdown(cardName: String, onConfirm: () -> Unit, onSkip: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onSkip,
        title = {
            Text(
                text = stringResource(R.string.scanner_ambiguous_match),
                style = ty.titleMedium,
                color = Color.White,
            )
        },
        text = {
            Text(
                text = cardName,
                style = ty.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    contentColor = Color.Black,
                ),
            ) {
                Text(stringResource(R.string.scanner_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.scanner_skip), color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1C1C1E),
    )
}
