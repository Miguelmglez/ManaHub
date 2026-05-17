package com.mmg.manahub.feature.scanner.presentation

import android.Manifest
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer
import com.mmg.manahub.feature.scanner.data.CardRecognizer
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.cancel
import java.util.concurrent.Executors

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

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermission.status.isGranted -> {
                CameraPreview(
                    isFlashOn = uiState.isFlashOn,
                    isPaused = uiState.showQueueSheet || uiState.showSettingsSheet || uiState.showEditSheet
                        || uiState.showVariantSelector || uiState.expandedVariantImageUrl != null,
                    selectedLanguage = uiState.selectedLanguage,
                    onRecognitionResult = viewModel::onRecognitionResult,
                    onFlashAvailability = viewModel::onFlashAvailabilityChanged,
                )

                NameZoneIndicator()

                TopScannerControls(
                    onBack = onBack,
                    queueCount = uiState.scanSession.cards.sumOf { it.quantity },
                    isFlashOn = uiState.isFlashOn,
                    hasFlash = uiState.hasFlash,
                    selectedLanguage = uiState.selectedLanguage,
                    onOpenQueue = viewModel::onOpenQueue,
                    onToggleFlash = viewModel::onToggleFlash,
                    onOpenSettings = viewModel::onOpenSettings,
                    onLanguageSelected = viewModel::onLanguageSelected,
                )

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
                        isFoil = uiState.selectedIsFoil,
                        preferredCurrency = preferredCurrency,
                        onManualAdd = viewModel::onManualAddCurrentCard,
                        onOpenPriceDetail = viewModel::onOpenPriceDetail,
                        onClick = { uiState.lastDetectedCard?.scryfallId?.let(onNavigateToCardDetail) },
                    )
                }

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
            onAddAllToWishlist = viewModel::onAddAllToWishlist,
            onAddEntryToCollection = viewModel::onAddEntryToCollection,
            onAddEntryToWishlist = viewModel::onAddEntryToWishlist,
            onNavigateToCardDetail = onNavigateToCardDetail,
            onSelectVariant = viewModel::onOpenVariantSelector,
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
        val editingCard = uiState.editingCard!!
        EditScannedCardSheet(
            scannedCard = editingCard,
            availablePrints = uiState.availablePrints,
            isLoadingPrints = uiState.isLoadingPrints,
            onDismiss = viewModel::onCloseEditSheet,
            onConfirm = viewModel::onUpdateScannedCard,
            onAddAnotherCopy = { viewModel.onAddDuplicateScannedCard(editingCard) },
        )
    }

    // Variant selector sheet
    if (uiState.showVariantSelector && uiState.variantSelectorEntry != null) {
        VariantSelectorSheet(
            entry = uiState.variantSelectorEntry!!,
            variants = uiState.cardVariants,
            isLoading = uiState.isLoadingVariants,
            onDismiss = viewModel::onCloseVariantSelector,
            onSelectVariant = viewModel::onSelectVariant,
            onExpandImage = viewModel::onExpandVariantImage,
        )
    }

    // Full-screen image viewer
    if (uiState.expandedVariantImageUrl != null) {
        FullScreenImageViewer(
            imageUrl = uiState.expandedVariantImageUrl!!,
            onDismiss = viewModel::onCloseExpandedImage,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Camera preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPreview(
    isFlashOn: Boolean,
    isPaused: Boolean,
    selectedLanguage: String,
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
            selectedLanguage = selectedLanguage,
            onResult = onRecognitionResult,
            // COMMENTED OUT — embedding params replaced by OCR
            // embeddingDatabase = embeddingDatabase,
            // cardEmbeddingModel = cardEmbeddingModel,
        )
    }

    LaunchedEffect(selectedLanguage) {
        recognizer.selectedLanguage = selectedLanguage
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            recognizerScope.cancel()
            analysisExecutor.shutdown()
        }
    }

    androidx.compose.runtime.DisposableEffect(recognizer) {
        onDispose {
            recognizer.release()
        }
    }

    val frameMetadataAnalyzer = remember {
        FrameMetadataAnalyzer(
            delegate = recognizer,
            isPaused = { isPaused },
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
//  Name-zone indicator — thin horizontal strip showing where to align card name
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a minimal horizontal bracket indicating the region where the card name
 * should be placed for OCR. The zone fractions match [CardOcrAnalyzer.NAME_ZONE_TOP_FRACTION]
 * and [CardOcrAnalyzer.NAME_ZONE_BOTTOM_FRACTION] so the visual and the scan area align.
 */
@Composable
private fun NameZoneIndicator(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val accentColor = mc.primaryAccent
    val ty = MaterialTheme.magicTypography

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenH = maxHeight
        val screenW = maxWidth

        val zoneTopFrac    = 0.40f
        val zoneBottomFrac = 0.52f
        val zoneWidthFrac  = 0.72f

        val zoneTop: androidx.compose.ui.unit.Dp    = screenH * zoneTopFrac
        val zoneLeftStart: androidx.compose.ui.unit.Dp = screenW * ((1f - zoneWidthFrac) / 2f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width
            val H = size.height

            val top    = H * zoneTopFrac
            val bottom = H * zoneBottomFrac
            val left   = W * ((1f - zoneWidthFrac) / 2f)
            val right  = W - left

            val stroke      = 1.5.dp.toPx()
            val strokeBold  = 2.5.dp.toPx()
            val bracketH    = 14.dp.toPx()

            // Top border line
            drawLine(accentColor.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(right, top), stroke)
            // Bottom border line
            drawLine(accentColor.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(right, bottom), stroke)

            // Left bracket — top arm and bottom arm
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + bracketH), strokeBold)
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, bottom - bracketH), strokeBold)

            // Right bracket — top arm and bottom arm
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, top + bracketH), strokeBold)
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right, bottom - bracketH), strokeBold)

            /*// Centre scan line (dashed)
            val mid = (top + bottom) / 2f
            drawLine(
                color       = accentColor.copy(alpha = 0.18f),
                start       = androidx.compose.ui.geometry.Offset(left + 28.dp.toPx(), mid),
                end         = androidx.compose.ui.geometry.Offset(right - 28.dp.toPx(), mid),
                strokeWidth = stroke * 0.6f,
                pathEffect  = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )*/
        }

        // Label — "CARD NAME" aligned to zone left edge, just above the top line
        Text(
            text = stringResource(R.string.scanner_name_zone_label),
            style = ty.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ),
            color = accentColor.copy(alpha = 0.75f),
            modifier = Modifier
                .padding(top = zoneTop - 18.dp, start = zoneLeftStart + 2.dp),
        )
    }
}

/**
 * Thin [ImageAnalysis.Analyzer] wrapper that forwards frames to [CardRecognizer].
 */
private class FrameMetadataAnalyzer(
    private val delegate: CardRecognizer,
    private val isPaused: () -> Boolean,
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        if (isPaused()) {
            imageProxy.close()
            return
        }
        try {
            if (com.mmg.manahub.BuildConfig.DEBUG) {
                android.util.Log.d("ScannerScreen", "FrameMetadataAnalyzer.analyze: frame=${imageProxy.width}x${imageProxy.height}")
            }
            delegate.analyze(imageProxy)
        } catch (e: Exception) {
            if (com.mmg.manahub.BuildConfig.DEBUG) {
                android.util.Log.e("ScannerScreen", "FrameMetadataAnalyzer failed", e)
            } else {
                android.util.Log.e("ScannerScreen", "FrameMetadataAnalyzer failed: ${e.javaClass.simpleName}")
            }
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

// ─────────────────────────────────────────────────────────────────────────────
//  Top Scanner Controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopScannerControls(
    onBack: () -> Unit,
    queueCount: Int,
    isFlashOn: Boolean,
    hasFlash: Boolean,
    selectedLanguage: String,
    onOpenQueue: () -> Unit,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(mc.background.copy(0.6f), CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = mc.textPrimary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            /*// Language Selector — shows 2-letter code on button, full name in dropdown
            var showLanguageMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showLanguageMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(0.4f), CircleShape)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = selectedLanguage.uppercase(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp,
                        )
                    }
                }
                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false },
                    modifier = Modifier.background(Color(0xFF1C1C1E))
                ) {
                    CardConstants.languages.forEach { (code, _) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = code.uppercase(),
                                        color = Color(0xFFFFC107),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(28.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = CardConstants.getLanguageName(code),
                                        color = Color.White,
                                    )
                                }
                            },
                            onClick = {
                                onLanguageSelected(code)
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            }*/

            // Queue button with badge
            Box {
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(40.dp)
                        .background(mc.background.copy(0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.AddToPhotos, contentDescription = null, tint = mc.textPrimary)
                }
                if (queueCount > 0) {
                    Surface(
                        color = mc.secondaryAccent,
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
                                color = mc.background,
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
                        .background(mc.background.copy(0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = null,
                        tint = if (isFlashOn) mc.goldMtg else mc.textPrimary
                    )
                }
            }

            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .background(mc.background.copy(0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = mc.textPrimary)
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
    isFoil: Boolean,
    preferredCurrency: PreferredCurrency,
    onManualAdd: () -> Unit,
    onOpenPriceDetail: () -> Unit,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.background.copy(alpha = 0.85f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                                color = mc.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SetSymbol(
                                    setCode = card.setCode,
                                    rarity = CardRarity.fromString(card.rarity),
                                    size = 16.dp,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = card.setName,
                                    style = ty.bodySmall,
                                    color = mc.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = PriceFormatter.formatFromScryfall(
                                    if (isFoil) card.priceUsdFoil else card.priceUsd,
                                    if (isFoil) card.priceEurFoil else card.priceEur,
                                    preferredCurrency,
                                ),
                                style = ty.labelSmall.copy(letterSpacing = 0.sp),
                                color = mc.primaryAccent
                            )
                        }
                    }

                    // Attribute Pill (Normal, Set, Lang, Qty)
                    Surface(
                        color = mc.textPrimary.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SetSymbol(
                                    setCode = card.setCode,
                                    rarity = CardRarity.fromString(card.rarity),
                                    size = 14.dp,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "#${card.collectorNumber}",
                                    style = ty.labelSmall,
                                    color = mc.textPrimary
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(mc.textPrimary.copy(alpha = 0.2f)))
                            Text(
                                text = card.lang.uppercase(),
                                style = ty.labelSmall,
                                color = mc.textPrimary,
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
                    Text(text = stringResource(R.string.scanner_searching_indicator), style = ty.bodySmall, color = mc.textPrimary)
                }
            } else {
                Text(
                    text = stringResource(R.string.scanner_point_at_card),
                    style = ty.bodySmall,
                    color = mc.textSecondary
                )
            }
        }
    }
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
    onAddAllToWishlist: () -> Unit,
    onAddEntryToCollection: (ScannedCard) -> Unit,
    onAddEntryToWishlist: (ScannedCard) -> Unit,
    onNavigateToCardDetail: (scryfallId: String) -> Unit,
    onSelectVariant: (ScannedCard) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    
    val filtered = remember(session.cards, searchQuery) {
        if (searchQuery.isBlank()) session.cards
        else session.cards.filter { it.card.name.contains(searchQuery, ignoreCase = true) }
    }

    // Local toast state for actions inside the sheet
    val sheetToastState = rememberMagicToastState()
    val viewModel: ScannerViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            sheetToastState.show(it)
            viewModel.onToastDismissed()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textPrimary.copy(0.3f)) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        color = mc.textPrimary
                    )
                    IconButton(onClick = onClearSession) {
                        Icon(Icons.Default.Delete, null, tint = mc.textSecondary)
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.scanner_queue_search_placeholder), style = ty.bodyMedium, color = mc.textDisabled) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = mc.textDisabled) },
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = mc.textPrimary.copy(0.05f),
                        focusedContainerColor = mc.textPrimary.copy(0.05f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Card List
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { "${it.card.scryfallId}_${it.timestamp}" }) { entry ->
                        QueueCardItem(
                            entry = entry,
                            preferredCurrency = preferredCurrency,
                            onEdit = { onEditCard(entry) },
                            onDelete = { onRemoveCard(entry) },
                            onAddToCollection = { onAddEntryToCollection(entry) },
                            onAddToWishlist = { onAddEntryToWishlist(entry) },
                            onClick = { onNavigateToCardDetail(entry.card.scryfallId) },
                            onSelectVariant = { onSelectVariant(entry) },
                        )
                    }
                }

                // Bulk Actions Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(mc.backgroundSecondary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddAllToCollection,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent, contentColor = mc.background)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scanner_queue_add_all), style = ty.bodyLarge)
                    }

                    OutlinedButton(
                        onClick = onAddAllToWishlist,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.textPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, mc.textPrimary.copy(0.2f))
                    ) {
                        Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scanner_queue_add_all_wishlist), style = ty.bodyLarge)
                    }
                }
            }

            // MagicToastHost for this sheet, positioned above the footer
            MagicToastHost(
                state = sheetToastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp) // Adjusted to be above the bulk action buttons
            )
        }
    }
}

@Composable
private fun QueueCardItem(
    entry: ScannedCard,
    preferredCurrency: PreferredCurrency,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToCollection: () -> Unit,
    onAddToWishlist: () -> Unit,
    onClick: () -> Unit,
    onSelectVariant: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Larger top-aligned image
            AsyncImage(
                model = entry.card.imageArtCrop ?: entry.card.imageNormal,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.quantity}x ${entry.card.name}",
                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    color = mc.textPrimary
                )
                
                Spacer(Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetSymbol(
                        setCode = entry.card.setCode,
                        rarity = CardRarity.fromString(entry.card.rarity),
                        size = 16.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${entry.card.setName} #${entry.card.collectorNumber}",
                        style = ty.labelMedium,
                        color = mc.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AttrTag(entry.language.uppercase())
                    AttrTag(entry.condition)
                    
                    if (entry.isFoil) {
                        AttrTag(stringResource(R.string.scanner_foil))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = PriceFormatter.formatFromScryfall(
                        if (entry.isFoil) entry.card.priceUsdFoil else entry.card.priceUsd,
                        if (entry.isFoil) entry.card.priceEurFoil else entry.card.priceEur,
                        preferredCurrency,
                    ),
                    style = ty.labelLarge.copy(fontWeight = FontWeight.Bold, color = mc.goldMtg),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Full-width action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAddToCollection,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.action_add), style = ty.labelSmall, color = mc.textSecondary)
                }
            }
            IconButton(
                onClick = onAddToWishlist,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FavoriteBorder, null, tint = mc.secondaryAccent, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.carddetail_add_to_wishlist), style = ty.labelSmall, color = mc.textSecondary)
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Edit, null, tint = mc.textSecondary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.action_edit), style = ty.labelSmall, color = mc.textSecondary)
                }
            }
            IconButton(
                onClick = onSelectVariant,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Collections, null, tint = mc.textSecondary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.scanner_select_variant), style = ty.labelSmall, color = mc.textSecondary)
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Clear, null, tint = mc.textSecondary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.action_remove), style = ty.labelSmall, color = mc.textSecondary)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = mc.textPrimary.copy(alpha = 0.05f)
        )
    }
}

@Composable
private fun AttrTag(text: String) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.textPrimary.copy(0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Edit Scanned Card Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditScannedCardSheet(
    scannedCard: ScannedCard,
    availablePrints: List<Card>,
    isLoadingPrints: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ScannedCard) -> Unit,
    onAddAnotherCopy: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    AddCardSheet(
        cardName = scannedCard.card.name,
        onConfirm = { foil: Boolean, _: Boolean, cond: String, lang: String, q: Int ->
            onConfirm(scannedCard.copy(
                isFoil = foil,
                condition = cond,
                language = lang,
                quantity = q,
            ))
        },
        onDismiss = onDismiss,
        manaCost = scannedCard.card.manaCost,
        cardImage = scannedCard.card.imageNormal,
        initialFoil = scannedCard.isFoil,
        initialCondition = scannedCard.condition,
        initialLanguage = scannedCard.language,
        initialQty = scannedCard.quantity,
        confirmButtonText = stringResource(R.string.scanner_edit_save),
        setCode = scannedCard.card.setCode,
        setName = scannedCard.card.setName,
        rarity = scannedCard.card.rarity,
        extraContent = {
            OutlinedButton(
                onClick = onAddAnotherCopy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scanner_add_another_copy), style = ty.bodyMedium)
            }
        },
    )
}


// ─────────────────────────────────────────────────────────────────────────────
//  Variant Selector Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSelectorSheet(
    entry: ScannedCard,
    variants: List<Card>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectVariant: (Card) -> Unit,
    onExpandImage: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue -> if (newValue == SheetValue.Hidden) !isLoading else true },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textPrimary.copy(0.3f)) },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.scanner_variant_selector_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = mc.textPrimary)
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = mc.primaryAccent)
                    }
                }
                variants.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_variant_no_results),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                        items(variants, key = { it.scryfallId }) { variant ->
                            VariantCardItem(
                                card = variant,
                                isFoil = entry.isFoil,
                                onSelectVariant = { onSelectVariant(variant) },
                                onExpandImage = {
                                    val url = variant.imageNormal ?: variant.imageArtCrop
                                    if (!url.isNullOrBlank()) onExpandImage(url)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantCardItem(
    card: Card,
    isFoil: Boolean,
    onSelectVariant: () -> Unit,
    onExpandImage: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val preferredCurrency = LocalPreferredCurrency.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectVariant() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                AsyncImage(
                    model = card.imageNormal ?: card.imageArtCrop,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(80.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onExpandImage() },
                )
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = card.name,
                    style = ty.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity = CardRarity.fromString(card.rarity),
                        size = 14.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${card.setName} #${card.collectorNumber}",
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.rarity.isNotBlank()) AttrTag(card.rarity.replaceFirstChar { it.uppercaseChar() })
                Text(
                    text = PriceFormatter.formatFromScryfall(
                        if (isFoil) card.priceUsdFoil else card.priceUsd,
                        if (isFoil) card.priceEurFoil else card.priceEur,
                        preferredCurrency,
                    ),
                    style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = mc.goldMtg,
                )
            }
        }
    }
    HorizontalDivider(color = mc.textPrimary.copy(alpha = 0.05f))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Full-screen image viewer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
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
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = mc.backgroundSecondary) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = stringResource(R.string.scanner_settings_title), style = ty.titleMedium, color = mc.textPrimary)
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
    val mc = MaterialTheme.magicColors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
            Text(text = subtitle, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CameraPermissionRequest(isPermanentlyDenied: Boolean, onRequest: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Box(modifier = Modifier.fillMaxSize().background(mc.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.scanner_permission_camera_required), style = ty.bodyMedium, color = mc.textPrimary)
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent, contentColor = mc.background)
            ) {
                Text(stringResource(R.string.scanner_permission_grant), style = ty.labelLarge)
            }
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
                color = mc.textPrimary,
            )
        },
        text = {
            Text(
                text = cardName,
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    contentColor = mc.background,
                ),
            ) {
                Text(stringResource(R.string.scanner_confirm), style = ty.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.scanner_skip), style = ty.labelLarge, color = mc.textDisabled)
            }
        },
        containerColor = mc.backgroundSecondary,
    )
}

