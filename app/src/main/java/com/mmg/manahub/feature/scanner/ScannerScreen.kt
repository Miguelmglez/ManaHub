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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
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
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val toastState = rememberMagicToastState()

    // Flash hardware availability — populated once after the camera binds.
    // Defaults to true so the button is visible until confirmed by the hardware query.
    var hasFlash by remember { mutableStateOf(true) }

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
                // Fullscreen camera — always visible, never interrupted
                CameraPreview(
                    isFlashOn = uiState.isFlashOn,
                    detectedCorners = uiState.detectedCorners,
                    hashDatabase = viewModel.hashDatabase,
                    onRecognitionResult = viewModel::onRecognitionResult,
                    onFlashAvailability = { available ->
                        hasFlash = available
                        viewModel.onFlashAvailabilityChanged(available)
                    },
                )

                // Back arrow — top-left floating overlay
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(top = 40.dp, start = 8.dp)
                        .align(Alignment.TopStart)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Discrete warning when the hash database is not loaded yet
                if (viewModel.hashDatabase.cardCount == 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_hash_db_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                // Right-side floating controls column
                ScannerSideControls(
                    queueCount = uiState.scanSession.cards.size,
                    isFlashOn = uiState.isFlashOn,
                    hasFlash = hasFlash,
                    onOpenQueue = viewModel::onOpenQueue,
                    onToggleFlash = viewModel::onToggleFlash,
                    onOpenSettings = viewModel::onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                )

                // Bottom overlay stack: mode bar + card bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    ModesBar(
                        isFoil = uiState.selectedIsFoil,
                        language = uiState.selectedLanguage,
                        condition = uiState.selectedCondition,
                        quantity = uiState.selectedQuantity,
                        lockedSetCode = uiState.lockedSetCode,
                        onToggleFoil = viewModel::onToggleFoil,
                        onLanguageSelected = viewModel::onLanguageSelected,
                        onQuantitySelected = viewModel::onQuantitySelected,
                        onSetLockSelected = viewModel::onSetLockSelected,
                    )
                    Box {
                        DetectedCardBar(
                            card = uiState.lastDetectedCard,
                            isSearching = uiState.isSearching,
                            error = uiState.error,
                            isQuickMode = uiState.isQuickMode,
                            isLookupOnly = uiState.isLookupOnly,
                            languageMismatch = uiState.languageMismatch,
                            onManualAdd = viewModel::onManualAddCurrentCard,
                            onNavigateToDetail = onNavigateToCardDetail,
                            onOpenPriceDetail = viewModel::onOpenPriceDetail,
                        )
                        // Ambiguity selector anchored to the card bar
                        val ambiguousCard = uiState.lastDetectedCard
                        if (uiState.showAmbiguitySelector && ambiguousCard != null) {
                            AmbiguityDropdown(
                                cardName = ambiguousCard.name,
                                onConfirm = {
                                    viewModel.onManualAddCurrentCard()
                                    viewModel.onDismissAmbiguitySelector()
                                },
                                onSkip = viewModel::onDismissAmbiguitySelector,
                            )
                        }
                    }
                }

                // Discrete central searching indicator (only when no card yet)
                if (uiState.isSearching && uiState.lastDetectedCard == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
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

    // Queue bottom sheet — only opened by user action
    if (uiState.showQueueSheet) {
        ScanQueueSheet(
            session = uiState.scanSession,
            multiSelectedIds = uiState.multiSelectedIds,
            onDismiss = viewModel::onCloseQueue,
            onRemoveCard = viewModel::onRemoveSessionCard,
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

    // Price detail sheet — Lookup Only mode only
    val priceDetailCard = uiState.lastDetectedCard
    if (uiState.showPriceDetailSheet && priceDetailCard != null) {
        PriceDetailSheet(
            card = priceDetailCard,
            onDismiss = viewModel::onClosePriceDetail,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Camera preview — fullscreen, always live
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fullscreen camera composable that:
 * - Binds [CardRecognizer] as the [ImageAnalysis] analyzer.
 * - Draws a yellow quadrilateral overlay from [detectedCorners] (sourced from [ScannerUiState]).
 * - Forwards [RecognitionResult] to [onRecognitionResult] (handled by [ScannerViewModel]).
 * - Reports flash hardware availability once via [onFlashAvailability] after camera bind.
 *
 * @param isFlashOn           Whether the camera torch should be on.
 * @param detectedCorners     Four corner points from the ViewModel state (null = no card).
 * @param hashDatabase        Singleton hash database used by [CardRecognizer].
 * @param onRecognitionResult Callback for each frame's recognition outcome.
 * @param onFlashAvailability Invoked once after the camera binds with whether the hardware
 *                            has a flash unit ([androidx.camera.core.CameraInfo.hasFlashUnit]).
 */
@Composable
private fun CameraPreview(
    isFlashOn: Boolean,
    detectedCorners: List<PointF>?,
    hashDatabase: HashDatabase,
    onRecognitionResult: (RecognitionResult) -> Unit,
    onFlashAvailability: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Retrieve CardRepository from the Hilt component via EntryPoint
    val cardRepository = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ScannerEntryPoint::class.java)
            .cardRepository()
    }

    // Frame dimensions — needed to map corner coordinates to canvas coordinates
    var lastFrameWidth by remember { mutableStateOf(0) }
    var lastFrameHeight by remember { mutableStateOf(0) }
    var lastRotationDegrees by remember { mutableStateOf(0) }

    // CardRecognizer wraps OpenCV detection + pHash lookup
    // rememberUpdatedState is not needed here because onRecognitionResult is
    // a stable ViewModel function reference.
    val recognizer = remember {
        CardRecognizer(
            hashDatabase = hashDatabase,
            cardRepository = cardRepository,
            scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
            onResult = onRecognitionResult,
        )
    }

    // Attach a wrapper analyzer that captures frame metadata and delegates to recognizer
    val frameMetadataAnalyzer = remember {
        FrameMetadataAnalyzer(
            delegate = recognizer,
            onFrameMetadata = { w, h, r ->
                lastFrameWidth = w
                lastFrameHeight = h
                lastRotationDegrees = r
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
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(720, 1280))
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                ContextCompat.getMainExecutor(ctx),
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

        // Card outline overlay — drawn only when corners are available in the ViewModel state
        Canvas(modifier = Modifier.fillMaxSize()) {
            val corners = detectedCorners ?: return@Canvas
            val frameWidth = lastFrameWidth
            val frameHeight = lastFrameHeight
            val rotationDegrees = lastRotationDegrees

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

        // Transparent tap overlay: focus on tap
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

// ─────────────────────────────────────────────────────────────────────────────
//  FrameMetadataAnalyzer — captures frame dimensions and delegates to CardRecognizer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thin [ImageAnalysis.Analyzer] wrapper that captures per-frame metadata
 * (width, height, rotation) and forwards the frame to [delegate] without closing it.
 *
 * [CardRecognizer] is responsible for closing the [ImageProxy].
 *
 * @param delegate         The [CardRecognizer] that owns the frame lifecycle.
 * @param onFrameMetadata  Callback invoked with (width, height, rotationDegrees) per frame.
 */
private class FrameMetadataAnalyzer(
    private val delegate: CardRecognizer,
    private val onFrameMetadata: (width: Int, height: Int, rotationDegrees: Int) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        onFrameMetadata(
            imageProxy.width,
            imageProxy.height,
            imageProxy.imageInfo.rotationDegrees,
        )
        // Delegate owns the ImageProxy lifecycle (calls close() in all paths)
        delegate.analyze(imageProxy)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Hilt EntryPoint — inject CardRepository into a non-ViewModel composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Hilt entry point used to retrieve [CardRepository] inside [CameraPreview]
 * where constructor injection is not available.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ScannerEntryPoint {
    /** Provides the [CardRepository] singleton from the Hilt graph. */
    fun cardRepository(): CardRepository
}

// ─────────────────────────────────────────────────────────────────────────────
//  Coordinate mapping helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps a point from camera frame pixel coordinates to Compose canvas coordinates,
 * accounting for the rotation applied by CameraX to portrait Android devices.
 *
 * CameraX back camera in portrait orientation reports [rotationDegrees] = 90,
 * meaning the raw frame is landscape with the top of the image on the right.
 *
 * @param point           Point in frame pixel coordinates (origin = top-left of frame).
 * @param frameWidth      Width of the camera frame in pixels (before rotation).
 * @param frameHeight     Height of the camera frame in pixels (before rotation).
 * @param rotationDegrees Rotation degrees from [androidx.camera.core.ImageProxy.imageInfo].
 * @param canvasSize      Compose canvas [Size] in pixels.
 * @return [Offset] in canvas coordinates.
 */
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
//  Right-side floating controls
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Right-side floating controls column.
 *
 * @param queueCount    Number of cards currently in the scan queue (shown as a badge).
 * @param isFlashOn     Whether the torch is currently on.
 * @param hasFlash      Whether the device has a flash unit. When false, the flash button
 *                      is omitted entirely from the column.
 * @param onOpenQueue   Called when the queue button is tapped.
 * @param onToggleFlash Called when the flash button is tapped.
 * @param onOpenSettings Called when the settings button is tapped.
 * @param modifier      [Modifier] applied to the root [Column].
 */
@Composable
private fun ScannerSideControls(
    queueCount: Int,
    isFlashOn: Boolean,
    hasFlash: Boolean,
    onOpenQueue: () -> Unit,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Queue button with badge
        BadgedBox(
            badge = {
                if (queueCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = queueCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            },
        ) {
            FloatingControlButton(onClick = onOpenQueue) {
                Icon(
                    imageVector = Icons.Default.Queue,
                    contentDescription = stringResource(R.string.scanner_queue_button_desc),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Flash toggle — only shown when the hardware supports it
        if (hasFlash) {
            FloatingControlButton(onClick = onToggleFlash) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = stringResource(
                        if (isFlashOn) R.string.action_flash_off else R.string.action_flash_on,
                    ),
                    tint = if (isFlashOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Settings
        FloatingControlButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.nav_settings),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Reusable semi-transparent circular button container for side controls. */
@Composable
private fun FloatingControlButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mode bar — chips for foil/normal, set lock, language, quantity
// ─────────────────────────────────────────────────────────────────────────────

private val SUPPORTED_LANGUAGES = listOf("en", "es", "de", "fr", "it", "pt", "ja", "ko", "ru", "zhs", "zht")
private val QUANTITY_OPTIONS = listOf(1, 2, 3, 4)

@Composable
private fun ModesBar(
    isFoil: Boolean,
    language: String,
    condition: String,
    quantity: Int,
    lockedSetCode: String?,
    onToggleFoil: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onQuantitySelected: (Int) -> Unit,
    onSetLockSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLanguageDropdown by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Foil / Normal toggle
            item {
                FilterChip(
                    selected = !isFoil,
                    onClick = { if (isFoil) onToggleFoil() },
                    label = { Text(stringResource(R.string.scanner_normal)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
            item {
                FilterChip(
                    selected = isFoil,
                    onClick = { if (!isFoil) onToggleFoil() },
                    label = { Text(stringResource(R.string.scanner_foil)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }

            // Set lock chip
            item {
                FilterChip(
                    selected = lockedSetCode != null,
                    onClick = { if (lockedSetCode != null) onSetLockSelected(null) },
                    label = {
                        Text(
                            text = lockedSetCode?.uppercase()
                                ?: stringResource(R.string.scanner_set_lock),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }

            // Language chip
            item {
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { showLanguageDropdown = true },
                        label = { Text(language.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = showLanguageDropdown,
                        onDismissRequest = { showLanguageDropdown = false },
                    ) {
                        SUPPORTED_LANGUAGES.forEach { lang ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(lang.uppercase()) },
                                onClick = {
                                    onLanguageSelected(lang)
                                    showLanguageDropdown = false
                                },
                            )
                        }
                    }
                }
            }

            // Quantity chips (1 / 2 / 3 / 4)
            items(QUANTITY_OPTIONS) { qty ->
                FilterChip(
                    selected = quantity == qty,
                    onClick = { onQuantitySelected(qty) },
                    label = { Text("×$qty") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Detected card bottom bar — always visible, never modal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetectedCardBar(
    card: Card?,
    isSearching: Boolean,
    error: String?,
    isQuickMode: Boolean,
    isLookupOnly: Boolean,
    languageMismatch: Boolean,
    onManualAdd: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onOpenPriceDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                card != null -> {
                    // Card info row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Thumbnail
                        AsyncImage(
                            model = card.imageArtCrop ?: card.imageNormal,
                            contentDescription = card.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )

                        // Name + price + optional mismatch indicator
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            if (languageMismatch) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.scanner_language_mismatch),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else {
                                val priceText = remember(card) {
                                    val eur = card.priceEur
                                    val usd = card.priceUsd
                                    when {
                                        eur != null -> "%.2f€".format(eur)
                                        usd != null -> "$%.2f".format(usd)
                                        else -> "—"
                                    }
                                }
                                Text(
                                    text = priceText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Searching spinner (while still resolving)
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // In non-quick / non-lookup mode: manual add button
                        if (!isQuickMode && !isLookupOnly) {
                            IconButton(onClick = onManualAdd) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.scanner_add_to_collection),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Navigate to detail OR open price sheet (Lookup Only mode)
                        IconButton(
                            onClick = {
                                if (isLookupOnly) {
                                    onOpenPriceDetail()
                                } else {
                                    onNavigateToDetail(card.scryfallId)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForwardIos,
                                contentDescription = stringResource(R.string.scanner_view_detail),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                isSearching -> {
                    // No card yet but a search is running
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.scanner_searching),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.scanner_searching_idle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scan Queue ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanQueueSheet(
    session: ScanSession,
    multiSelectedIds: Set<String>,
    onDismiss: () -> Unit,
    onRemoveCard: (ScannedCard) -> Unit,
    onToggleSelect: (ScannedCard) -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSession: () -> Unit,
    onAddAllToCollection: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val isMultiSelectActive = multiSelectedIds.isNotEmpty()

    val filtered = remember(session.cards, searchQuery) {
        if (searchQuery.isBlank()) session.cards
        else session.cards.filter { it.card.name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Header
            if (isMultiSelectActive) {
                // Contextual top bar for multi-select
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${multiSelectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.scanner_queue_title, session.cards.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // Search filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.scanner_queue_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Card list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(filtered, key = { it.timestamp }) { entry ->
                    val isSelected = entry.card.scryfallId in multiSelectedIds
                    QueueCardRow(
                        entry = entry,
                        isSelected = isSelected,
                        onRemove = { onRemoveCard(entry) },
                        onLongPress = { onToggleSelect(entry) },
                        onClick = {
                            if (isMultiSelectActive) onToggleSelect(entry)
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Sticky bottom actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onClearSession,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.scanner_clear_session),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onAddAllToCollection,
                    enabled = session.cards.isNotEmpty(),
                    modifier = Modifier.weight(2f),
                ) {
                    Text(stringResource(R.string.scanner_add_all))
                }
            }
        }
    }
}

@Composable
private fun QueueCardRow(
    entry: ScannedCard,
    isSelected: Boolean,
    onRemove: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Thumbnail
        AsyncImage(
            model = entry.card.imageArtCrop ?: entry.card.imageNormal,
            contentDescription = entry.card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp)),
        )

        // Info column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${entry.quantity}× ${entry.card.name}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(entry.setCode.uppercase())
                    append(" · ")
                    append(entry.language.uppercase())
                    append(" · ")
                    append(entry.condition)
                    if (entry.isFoil) append(" · Foil")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // Price
        val priceLabel = remember(entry.card) {
            val eur = entry.card.priceEur
            val usd = entry.card.priceUsd
            when {
                eur != null -> "%.2f€".format(eur)
                usd != null -> "$%.2f".format(usd)
                else -> "—"
            }
        }
        Text(
            text = priceLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Delete button
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.action_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scanner Settings ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerSettingsSheet(
    isQuickMode: Boolean,
    isLookupOnly: Boolean,
    isSoundEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleQuickMode: () -> Unit,
    onToggleLookupOnly: () -> Unit,
    onToggleSound: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Quick Mode toggle
            SettingsToggleRow(
                title = stringResource(R.string.scanner_quick_mode),
                subtitle = stringResource(R.string.scanner_quick_mode_desc),
                checked = isQuickMode,
                onCheckedChange = { onToggleQuickMode() },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Lookup Only toggle
            SettingsToggleRow(
                title = stringResource(R.string.scanner_lookup_only),
                subtitle = stringResource(R.string.scanner_lookup_only_desc),
                checked = isLookupOnly,
                onCheckedChange = { onToggleLookupOnly() },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Sound effects toggle
            SoundToggleRow(
                isSoundEnabled = isSoundEnabled,
                onToggle = onToggleSound,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sound toggle row — used inside ScannerSettingsSheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Settings row with a leading volume icon that toggles sound effects.
 *
 * @param isSoundEnabled Current sound state.
 * @param onToggle       Called when the switch is tapped.
 */
@Composable
private fun SoundToggleRow(
    isSoundEnabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.scanner_sound_effects),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = isSoundEnabled,
            onCheckedChange = { onToggle() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ambiguity dropdown — anchored to DetectedCardBar, non-modal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Inline [DropdownMenu] shown when an ambiguous card match is detected in normal mode.
 *
 * Anchored at the top of the [DetectedCardBar] (alignment = TopStart of the containing Box).
 * The user can confirm the match (which triggers [onConfirm]) or skip it ([onSkip]).
 * The camera is never interrupted.
 *
 * @param cardName  Name of the ambiguously matched card.
 * @param onConfirm Called when the user taps "Confirm".
 * @param onSkip    Called when the user taps "Skip".
 */
@Composable
private fun AmbiguityDropdown(
    cardName: String,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onSkip,
    ) {
        DropdownMenuItem(
            enabled = false,
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.scanner_ambiguous_match),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = cardName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            onClick = {},
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.scanner_confirm),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = onConfirm,
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.scanner_skip),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            onClick = onSkip,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Price detail ModalBottomSheet — Lookup Only mode
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-price [ModalBottomSheet] shown when the user taps `>` in Lookup Only mode.
 *
 * Displays:
 * - Normal and foil prices in EUR and USD.
 * - Purchase link buttons for TCGPlayer, Cardmarket, and Card Kingdom (from [Card.purchaseUris]).
 *
 * Purchase links open in a [CustomTabsIntent] (Chrome Custom Tab) when available,
 * falling back to [Intent.ACTION_VIEW] otherwise.
 *
 * @param card      The card whose prices and purchase links are displayed.
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceDetailSheet(
    card: Card,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title: card name + set
            Text(
                text = "${card.name} · ${card.setCode.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Normal prices
            Text(
                text = stringResource(R.string.scanner_price_detail_normal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PricePill(
                    label = "EUR",
                    value = card.priceEur?.let { "%.2f€".format(it) }
                        ?: stringResource(R.string.scanner_price_na),
                    modifier = Modifier.weight(1f),
                )
                PricePill(
                    label = "USD",
                    value = card.priceUsd?.let { "$%.2f".format(it) }
                        ?: stringResource(R.string.scanner_price_na),
                    modifier = Modifier.weight(1f),
                )
            }

            // Foil prices
            Text(
                text = stringResource(R.string.scanner_price_detail_foil),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PricePill(
                    label = "EUR",
                    value = card.priceEurFoil?.let { "%.2f€".format(it) }
                        ?: stringResource(R.string.scanner_price_na),
                    modifier = Modifier.weight(1f),
                )
                PricePill(
                    label = "USD",
                    value = card.priceUsdFoil?.let { "$%.2f".format(it) }
                        ?: stringResource(R.string.scanner_price_na),
                    modifier = Modifier.weight(1f),
                )
            }

            // Buy links
            if (card.purchaseUris.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.scanner_price_detail_buy),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                card.purchaseUris.forEach { (vendor, url) ->
                    TextButton(
                        onClick = {
                            val uri = Uri.parse(url)
                            try {
                                CustomTabsIntent.Builder().build().launchUrl(context, uri)
                            } catch (_: Exception) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = vendor.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Small pill composable that displays a price [label] + [value] pair inside a
 * container background. Used in [PriceDetailSheet].
 *
 * @param label    Short currency label, e.g. "EUR" or "USD".
 * @param value    Formatted price string or "N/A".
 * @param modifier [Modifier] applied to the root [Surface].
 */
@Composable
private fun PricePill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
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
    onRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isPermanentlyDenied) {
                    stringResource(R.string.scanner_permission_denied_settings)
                } else {
                    stringResource(R.string.scanner_permission_rationale)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!isPermanentlyDenied) {
                Button(onClick = onRequest) {
                    Text(stringResource(R.string.scanner_grant_permission))
                }
            }
        }
    }
}
