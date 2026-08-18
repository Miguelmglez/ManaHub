package com.mmg.manahub.feature.scanner.presentation

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.FullScreenImageViewer
import com.mmg.manahub.core.ui.components.LanguageBadge
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.VariantSelectorSheet
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.carddetail.presentation.CardDetailScreen
import com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer
import com.mmg.manahub.feature.scanner.data.CardRecognizer
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.cancel
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

// ── Private helpers for copy ambiguity ──────────────────────────────────────

private fun Color.withAlpha(alpha: Float): Color = this.copy(alpha = alpha)

private fun TextStyle.withFontSize(size: TextUnit): TextStyle = this.copy(fontSize = size)
private fun TextStyle.withWeight(weight: FontWeight): TextStyle = this.copy(fontWeight = weight)
private fun TextStyle.withColor(color: Color): TextStyle = this.copy(color = color)
private fun TextStyle.withLetterSpacing(spacing: TextUnit): TextStyle = this.copy(letterSpacing = spacing)

// ─────────────────────────────────────────────────────────────────────────────
//  Root screen — entry point from navigation
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onNavigateToCardDetail: (scryfallId: String) -> Unit = {},
    onNavigateToAddCard: () -> Unit = {},
    onNavigateToDeck: (String) -> Unit = {},
    onNavigateToCommunityDecks: (cardName: String) -> Unit = {},
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val toastState = rememberMagicToastState()
    val preferredCurrency = LocalPreferredCurrency.current
    val queueListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-launch permission dialog on first composition
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Back handler for the sheet-scoped CardDetail overlay
    BackHandler(enabled = uiState.selectedCardDetailId != null) {
        viewModel.onCloseCardDetail()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermission.status.isGranted -> {
                val isRecognitionPaused = uiState.isRecognitionPausedByUser || uiState.showQueueSheet
                        || uiState.showEditSheet || uiState.showVariantSelector || uiState.expandedVariantImageUrl != null
                        || uiState.selectedCardDetailId != null || uiState.showPriceDetailSheet

                CameraPreview(
                    isFlashOn = uiState.isFlashOn,
                    isPaused = isRecognitionPaused,
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
                    isSoundEnabled = uiState.isSoundEnabled,
                    isRecognitionPausedByUser = uiState.isRecognitionPausedByUser,
                    selectedLanguage = uiState.selectedLanguage,
                    onOpenQueue = viewModel::onOpenQueue,
                    onToggleFlash = viewModel::onToggleFlash,
                    onToggleSound = viewModel::onToggleSound,
                    onToggleRecognitionPaused = viewModel::onToggleRecognitionPaused,
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
                        languageMismatch = uiState.languageMismatch,
                        isFoil = uiState.selectedIsFoil,
                        preferredCurrency = preferredCurrency,
                        onClick = { uiState.lastDetectedCard?.scryfallId?.let { viewModel.onOpenCardDetail(it, fromQueue = false) } },
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
            ownedCardIdentityKeys = uiState.ownedCardIdentityKeys,
            isAutoDeleteOnAddEnabled = uiState.isAutoDeleteOnAddEnabled,
            listState = queueListState,
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
            onNavigateToCardDetail = viewModel::onOpenCardDetail,
            onDuplicateCard = viewModel::onDuplicateSessionCard,
            onToggleAutoDeleteOnAdd = viewModel::onToggleAutoDeleteOnAdd,
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
            onOpenVariantSelector = { viewModel.onOpenVariantSelector(editingCard) },
        )
    }

    // Variant selector sheet
    if (uiState.showVariantSelector && uiState.variantSelectorEntry != null) {
        VariantSelectorSheet(
            currentCardId = uiState.variantSelectorEntry!!.card.scryfallId,
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

    // Sheet-scoped CardDetail overlay (same stacking pattern as variant selector).
    // Enter/exit matches the app-wide CardDetail transition (see AppNavGraph's
    // Screen.CollectionCardDetail composable) — a scale+fade, NOT a bottom-sheet slide, since this
    // overlay represents a full "screen", not a sheet.
    AnimatedVisibility(
        visible = uiState.selectedCardDetailId != null,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(450)),
        exit = fadeOut(tween(300)),
    ) {
        if (uiState.selectedCardDetailId != null) {
            CardDetailScreen(
                onBack = viewModel::onCloseCardDetail,
                onNavigateToAddCard = {
                    viewModel.onCloseCardDetail()
                    onNavigateToAddCard()
                },
                onNavigateToCard = { id ->
                    // Cross-navigation within the overlay: just update the ID
                    viewModel.onOpenCardDetail(id)
                },
                // Pass existing navigation callbacks to ensure features like "Find Community Decks"
                // or "Open in Deck" still work from within the scanner-scoped overlay.
                onNavigateToDeck = { id ->
                    viewModel.onCloseCardDetail()
                    onNavigateToDeck(id)
                },
                onNavigateToCommunityDecks = { name ->
                    viewModel.onCloseCardDetail()
                    onNavigateToCommunityDecks(name)
                },
                // Use Koin's parameter-based injection (updated in CardDetailKoinModule). An explicit
                // `key` tied to the card id is REQUIRED here: this call site's ViewModelStoreOwner never
                // changes (this overlay is not a real nav destination, unlike every other place
                // CardDetailScreen is opened via navController.navigate(...), which gets a fresh
                // NavBackStackEntry/ViewModelStore per route). Without a distinct key, Compose's
                // ViewModelProvider returns the SAME cached CardDetailViewModel on every recomposition
                // and silently ignores the new parametersOf(...) after the first construction — i.e. the
                // "always shows the same card" bug. selectedCardDetailId is non-null here (guarded above).
                viewModel = koinViewModel(
                    key = uiState.selectedCardDetailId,
                ) { org.koin.core.parameter.parametersOf(uiState.selectedCardDetailId) }
            )
        }
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

    // Wrap the isPaused state in a remembered updated state to ensure the analyzer
    // lambda always reads the fresh value without triggering a re-allocation of
    // the analyzer or re-binding of the camera use case.
    val currentIsPaused by rememberUpdatedState(isPaused)
    val frameMetadataAnalyzer = remember {
        FrameMetadataAnalyzer(
            delegate = recognizer,
            isPaused = { currentIsPaused },
        )
    }

    // Bind the camera asynchronously so we never block the main thread waiting for
    // ProcessCameraProvider. The effect keys on both [lifecycleOwner] and [previewViewRef]:
    // it will not run until AndroidView.factory has assigned a non-null PreviewView, and
    // it re-runs on configuration change (new lifecycleOwner) so use cases are re-bound.
    LaunchedEffect(lifecycleOwner, previewViewRef) {
        val pv = previewViewRef ?: return@LaunchedEffect

        // Suspend without blocking the main thread until the provider is ready.
        val cameraProvider = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resumeWith(runCatching { future.get() }) },
                androidx.core.content.ContextCompat.getMainExecutor(context),
            )
        }

        // ResolutionSelector replaces the deprecated setTargetResolution API (CameraX 1.3+).
        // FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER lets CameraX pick the nearest
        // available resolution when 720×1280 is not supported by the sensor.
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(720, 1280),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(pv.surfaceProvider)
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor, frameMetadataAnalyzer)
            }

        runCatching {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
            onFlashAvailability(camera?.cameraInfo?.hasFlashUnit() ?: false)
        }
    }

    LaunchedEffect(isFlashOn) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                // The PreviewView is created immediately; camera binding happens in
                // the LaunchedEffect above once ProcessCameraProvider is ready.
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { pv ->
                    previewViewRef = pv
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
            drawLine(accentColor.withAlpha(0.55f), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(right, top), stroke)
            // Bottom border line
            drawLine(accentColor.withAlpha(0.55f), androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(right, bottom), stroke)

            // Left bracket — top arm and bottom arm
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + bracketH), strokeBold)
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, bottom - bracketH), strokeBold)

            // Right bracket — top arm and bottom arm
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, top + bracketH), strokeBold)
            drawLine(accentColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right, bottom - bracketH), strokeBold)

            /*// Centre scan line (dashed)
            val mid = (top + bottom) / 2f
            drawLine(
                color       = accentColor.withAlpha(0.18f),
                start       = androidx.compose.ui.geometry.Offset(left + 28.dp.toPx(), mid),
                end         = androidx.compose.ui.geometry.Offset(right - 28.dp.toPx(), mid),
                strokeWidth = stroke * 0.6f,
                pathEffect  = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )*/
        }

        // Label — "CARD NAME" aligned to zone left edge, just above the top line
        Text(
            text = stringResource(R.string.scanner_name_zone_label),
            style = ty.labelSmall.withFontSize(9.sp).withLetterSpacing(1.5.sp).withWeight(FontWeight.Bold),
            color = accentColor.withAlpha(0.75f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopScannerControls(
    onBack: () -> Unit,
    queueCount: Int,
    isFlashOn: Boolean,
    hasFlash: Boolean,
    isSoundEnabled: Boolean,
    isRecognitionPausedByUser: Boolean,
    selectedLanguage: String,
    onOpenQueue: () -> Unit,
    onToggleFlash: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleRecognitionPaused: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var showLanguageSelector by remember { mutableStateOf(false) }
    var isSettingsExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(mc.background.withAlpha(0.6f), CircleShape)
                .border(1.dp, mc.textPrimary.withAlpha(0.12f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = mc.textPrimary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            // Recognition pause/resume toggle — independent of sheet-driven pauses
            IconButton(
                onClick = onToggleRecognitionPaused,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isRecognitionPausedByUser) mc.goldMtg.withAlpha(0.9f) else mc.background.withAlpha(0.6f),
                        CircleShape
                    )
                    .border(1.dp, mc.textPrimary.withAlpha(0.12f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isRecognitionPausedByUser) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = if (isRecognitionPausedByUser) mc.background else mc.textPrimary,
                )
            }

            // Queue button with badge
            Box {
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(40.dp)
                        .background(mc.background.withAlpha(0.6f), CircleShape)
                        .border(1.dp, mc.textPrimary.withAlpha(0.12f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Style, contentDescription = null, tint = mc.textPrimary)
                }
                if (queueCount > 0) {
                    Surface(
                        color = mc.secondaryAccent,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(18.dp)
                            .widthIn(min = 18.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .border(1.5.dp, mc.background, CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(
                                text = if (queueCount > 99) "+99" else queueCount.toString(),
                                style = ty.labelSmall.withFontSize(10.sp).withLetterSpacing(0.sp),
                                color = mc.background,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Language selector — flag icon opens LanguageSelectorSheet (item 2, 2026-07-17 UX pass)
            IconButton(
                onClick = { showLanguageSelector = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(mc.background.withAlpha(0.6f), CircleShape)
                    .border(1.dp, mc.textPrimary.withAlpha(0.12f), CircleShape)
            ) {
                Text(text = CardConstants.getFlag(selectedLanguage), style = ty.titleLarge)
            }

            // Settings group (Vertical downward expansion)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gear button - toggles expansion (Must be the top element to expand "downwards")
                IconButton(
                    onClick = { isSettingsExpanded = !isSettingsExpanded },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isSettingsExpanded) mc.primaryAccent.withAlpha(0.9f) else mc.background.withAlpha(0.6f),
                            CircleShape
                        )
                        .border(1.dp, mc.textPrimary.withAlpha(0.12f), CircleShape)
                        .zIndex(1f) // Keep gear on top during animation
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = if (isSettingsExpanded) mc.background else mc.textPrimary
                    )
                }

                AnimatedVisibility(
                    visible = isSettingsExpanded,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Sound toggle
                        IconButton(
                            onClick = onToggleSound,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isSoundEnabled) mc.goldMtg.withAlpha(0.9f) else mc.background.withAlpha(0.6f),
                                    CircleShape
                                )
                                .border(1.dp, mc.textPrimary.withAlpha(0.12f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSoundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = null,
                                tint = if (isSoundEnabled) mc.background else mc.textPrimary,
                            )
                        }

                        // Flash button
                        if (hasFlash) {
                            IconButton(
                                onClick = onToggleFlash,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isFlashOn) mc.goldMtg.withAlpha(0.9f) else mc.background.withAlpha(0.6f),
                                        CircleShape
                                    )
                                    .border(1.dp, mc.textPrimary.withAlpha(0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                    contentDescription = null,
                                    tint = if (isFlashOn) mc.background else mc.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLanguageSelector) {
        LanguageSelectorSheet(
            selectedLanguage = selectedLanguage,
            onDismiss = { showLanguageSelector = false },
            onSelectLanguage = { code ->
                onLanguageSelected(code)
                showLanguageSelector = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scanner language selector sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Language codes for which on-device OCR recognition is available. Scanner-only restriction —
 * [CardConstants.languages] itself stays untouched (AddCard's Scryfall search-by-language must
 * keep offering every language Scryfall indexes, ja/ko included; that's independent of what
 * [com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer] can recognize on a physical card).
 *
 * ja/ko excluded 2026-07-22 (Android 16 / API 36 migration): the ML Kit `-japanese`/`-korean`
 * text-recognition artefacts were removed end-to-end (native libs not 16KB-page-size aligned,
 * no fixed release upstream) — see [com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer].
 */
private val OCR_SUPPORTED_LANGUAGES = CardConstants.languages.filterNot { (code, _) -> code == "ja" || code == "ko" }

/**
 * Language selector bottom sheet — visual/behavioral twin of `AddCardScreen`'s private
 * `LanguageSelectorSheet` (same flag + name + checkmark [LazyColumn] row pattern). Duplicated
 * here rather than extracted into a shared composable: both screens' sheets are `private` inside
 * their own file/package and this scanner UX pass is scoped to files that don't include
 * `AddCardScreen.kt` (see the 2026-07-17 scanner UX pass memory note).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectorSheet(
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.scanner_language_sheet_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(OCR_SUPPORTED_LANGUAGES, key = { it.first }) { (code, flag) ->
                    val isSelected = code == selectedLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelectLanguage(code) }
                            .then(
                                if (isSelected) Modifier.background(mc.primaryAccent.withAlpha(0.08f))
                                else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = flag, style = ty.titleLarge)
                        Text(
                            text = CardConstants.getLanguageName(code),
                            style = ty.bodyMedium,
                            color = if (isSelected) mc.primaryAccent else mc.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = mc.primaryAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
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
    languageMismatch: Boolean,
    isFoil: Boolean,
    preferredCurrency: PreferredCurrency,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.background.withAlpha(0.85f),
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
                                style = ty.labelSmall.withLetterSpacing(0.sp),
                                color = mc.primaryAccent
                            )
                        }
                    }

                    // Attribute Pill (Normal, Set, Lang, Qty)
                    Surface(
                        color = mc.textPrimary.withAlpha(0.1f),
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
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(mc.textPrimary.withAlpha(0.2f)))
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
                    MagicLoadingSpinner(size = MagicLoadingSize.XSmall)
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
    ownedCardIdentityKeys: Set<String>,
    isAutoDeleteOnAddEnabled: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
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
    onNavigateToCardDetail: (scryfallId: String, fromQueue: Boolean) -> Unit,
    onDuplicateCard: (ScannedCard) -> Unit,
    onToggleAutoDeleteOnAdd: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
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
        dragHandle = null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                }
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
                        Icon(Icons.Rounded.Delete, null, tint = mc.lifeNegative)
                    }
                }

                // Sticky "Auto-delete on add" switch — stays visible above the scrollable list
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.scanner_queue_auto_delete_title),
                        subtitle = stringResource(R.string.scanner_queue_auto_delete_desc),
                        checked = isAutoDeleteOnAddEnabled,
                        onCheckedChange = { onToggleAutoDeleteOnAdd() },
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.scanner_queue_search_placeholder), style = ty.bodyMedium, color = mc.textDisabled) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = mc.textDisabled) },
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = mc.textPrimary.withAlpha(0.05f),
                        focusedContainerColor = mc.textPrimary.withAlpha(0.05f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Card List
                LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                    items(filtered, key = { "${it.card.scryfallId}_${it.timestamp}" }) { entry ->
                        QueueCardItem(
                            entry = entry,
                            preferredCurrency = preferredCurrency,
                            isInCollection = entry.card.oracleId.ifBlank { entry.card.name } in ownedCardIdentityKeys,
                            onEdit = { onEditCard(entry) },
                            onDelete = { onRemoveCard(entry) },
                            onAddToCollection = { onAddEntryToCollection(entry) },
                            onAddToWishlist = { onAddEntryToWishlist(entry) },
                            onClick = { onNavigateToCardDetail(entry.card.scryfallId, true) },
                            onDuplicate = { onDuplicateCard(entry) },
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
                    MagicCtaButton(
                        onClick = onAddAllToCollection,
                        text = stringResource(R.string.scanner_queue_add_all),
                        icon = @Composable { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    MagicCtaButton(
                        onClick = onAddAllToWishlist,
                        text = stringResource(R.string.scanner_queue_add_all_wishlist),
                        icon = @Composable { Icon(Icons.Rounded.FavoriteBorder, null, modifier = Modifier.size(18.dp)) },
                        style = MagicCtaStyle.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
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
    isInCollection: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToCollection: () -> Unit,
    onAddToWishlist: () -> Unit,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
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
                    style = ty.titleMedium.withWeight(FontWeight.Bold).withFontSize(18.sp),
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
                    LanguageBadge(langCode = entry.language)
                    AttrTag(entry.condition)

                    if (entry.isFoil) {
                        AttrTag(stringResource(R.string.scanner_foil))
                    }

                    // "Already in collection" badge — any printing/language of the same oracle
                    // identity (Card Versions & Languages convention: oracleId.ifBlank { name })
                    if (isInCollection) {
                        Icon(
                            imageVector = Icons.Rounded.CollectionsBookmark,
                            contentDescription = stringResource(R.string.scanner_already_in_collection),
                            tint = mc.primaryAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = PriceFormatter.formatFromScryfall(
                        if (entry.isFoil) entry.card.priceUsdFoil else entry.card.priceUsd,
                        if (entry.isFoil) entry.card.priceEurFoil else entry.card.priceEur,
                        preferredCurrency,
                    ),
                    style = ty.labelLarge.withWeight(FontWeight.Bold).withColor(mc.goldMtg),
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
                    Icon(Icons.Rounded.Style, null, tint = mc.primaryAccent, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.action_add), style = ty.labelSmall, color = mc.primaryAccent)
                }
            }
            IconButton(
                onClick = onAddToWishlist,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FavoriteBorder, null, tint = mc.secondaryAccent, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.carddetail_add_to_wishlist), style = ty.labelSmall, color = mc.secondaryAccent)
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Edit, null, tint = mc.textSecondary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.action_edit), style = ty.labelSmall, color = mc.textSecondary)
                }
            }
            IconButton(
                onClick = onDuplicate,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = mc.textSecondary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.scanner_duplicate_entry), style = ty.labelSmall, color = mc.textSecondary)
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Clear, null, tint = mc.lifeNegative, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.action_remove), style = ty.labelSmall, color = mc.lifeNegative)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = mc.textPrimary.withAlpha(0.05f)
        )
    }
}

@Composable
private fun AttrTag(text: String) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.textPrimary.withAlpha(0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.labelSmall.withFontSize(10.sp).withLetterSpacing(0.sp),
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
    onOpenVariantSelector: () -> Unit,
) {
    AddCardSheet(
        cardName = scannedCard.card.name,
        onConfirm = { foil: Boolean, cond: String, lang: String, q: Int ->
            onConfirm(scannedCard.copy(
                isFoil = foil,
                condition = cond,
                language = lang,
                quantity = q,
            ))
        },
        onDismiss = onDismiss,
        cardImage = scannedCard.card.imageNormal,
        initialFoil = scannedCard.isFoil,
        initialCondition = scannedCard.condition,
        initialLanguage = scannedCard.language,
        initialQty = scannedCard.quantity,
        confirmButtonText = stringResource(R.string.scanner_edit_save),
        setCode = scannedCard.card.setCode,
        setName = scannedCard.card.setName,
        rarity = scannedCard.card.rarity,
        // Variant selection moved into the Edit sheet (item 7, 2026-07-17 UX pass) — replaces the
        // old standalone "Variants" queue action. VariantSelectorSheet renders on top of this
        // still-open sheet (same stacking pattern as CardDetailScreen's sheet-scoped variant flow).
        onOpenVariantSelector = onOpenVariantSelector,
        // Reserves the card-art footprint immediately with the card-back art so switching between
        // scanned entries never causes a visible layout jump while Coil loads the real image.
        cardImagePlaceholder = painterResource(Res.drawable.mtg_card_back),
    )
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
            MagicCtaButton(
                onClick = onRequest,
                text = stringResource(R.string.scanner_permission_grant),
            )
        }
    }
}

@Composable
private fun AmbiguityDropdown(cardName: String, onConfirm: () -> Unit, onSkip: () -> Unit) {
    MagicAlertDialog(
        onDismissRequest = onSkip,
        title = stringResource(R.string.scanner_ambiguous_match),
        text = cardName,
        confirmLabel = stringResource(R.string.scanner_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.scanner_skip),
        onDismiss = onSkip,
    )
}
