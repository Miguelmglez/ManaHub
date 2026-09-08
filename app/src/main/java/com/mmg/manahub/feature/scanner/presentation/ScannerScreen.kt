package com.mmg.manahub.feature.scanner.presentation

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.FullScreenImageViewer
import com.mmg.manahub.core.ui.components.LanguageBadge
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.VariantSelectorSheet
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberRateLimitCountdownSeconds
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.carddetail.presentation.CardDetailScreen
import com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer
import com.mmg.manahub.feature.scanner.data.CardRecognizer
import com.mmg.manahub.feature.scanner.domain.ScannerZone
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.cancel
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

// ── Private helpers for copy ambiguity ──────────────────────────────────────

/**
 * W3.3 (scanner-reliability-plan.md, 2026-08-24): debounce applied before UNBINDING the camera
 * when a covering sheet/overlay opens, so a fast open→close does not thrash the camera session (a
 * rebind can cost ~200-600 ms on some devices). Rebinding itself is always immediate — see
 * [CameraPreview]'s KDoc.
 */
private const val CAMERA_STOP_DEBOUNCE_MS = 250L

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
                // W3.1 (scanner-reliability-plan.md, 2026-08-24): a covering sheet/overlay is a
                // STRONGER condition than the top-bar recognition-pause toggle — it fully stops
                // the camera (see isCameraActive below) rather than just detaching the analyzer.
                // WS4 (2026-08-25): overlayReason is derived from the SAME when-ordering so it can
                // never disagree with isCameraActive — it exists purely as a closed-set breadcrumb
                // payload for scanner_camera_stopped_for_sheet (see CameraPreview), never as an
                // independent source of truth.
                val overlayReason: String? = when {
                    uiState.showQueueSheet -> "queue"
                    uiState.showEditSheet -> "edit"
                    uiState.showVariantSelector -> "variant_selector"
                    uiState.expandedVariantImageUrl != null -> "expanded_image"
                    uiState.selectedCardDetailId != null -> "card_detail"
                    uiState.showPriceDetailSheet -> "price_detail"
                    else -> null
                }
                val isCameraActive = overlayReason == null

                CameraPreview(
                    isFlashOn = uiState.isFlashOn,
                    isRecognitionPausedByUser = uiState.isRecognitionPausedByUser,
                    isCameraActive = isCameraActive,
                    overlayReason = overlayReason,
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // W2.10: rate-limit cooldown badge. initialRetryAfterMs is captured ONCE
                        // per distinct rateLimitedUntilMs value (remember-keyed) so the countdown
                        // ticks down locally instead of restarting every recomposition — see
                        // rememberRateLimitCountdownSeconds's KDoc.
                        val rateLimitedUntilMs = uiState.rateLimitedUntilMs
                        val initialRetryAfterMs = remember(rateLimitedUntilMs) {
                            rateLimitedUntilMs?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) }
                        }
                        val rateLimitedRemainingSeconds = rememberRateLimitCountdownSeconds(initialRetryAfterMs)
                        if (rateLimitedUntilMs != null && rateLimitedRemainingSeconds > 0) {
                            RateLimitedBadge(remainingSeconds = rateLimitedRemainingSeconds)
                        }

                        DetectedCardOverlay(
                            card = uiState.lastDetectedCard,
                            isSearching = uiState.isSearching,
                            error = uiState.error,
                            languageMismatch = uiState.languageMismatch,
                            selectedLanguage = uiState.selectedLanguage,
                            isFoil = uiState.selectedIsFoil,
                            preferredCurrency = preferredCurrency,
                            onClick = { uiState.lastDetectedCard?.scryfallId?.let { viewModel.onOpenCardDetail(it, fromQueue = false) } },
                            onRemove = viewModel::onRemoveLastDetectedCard,
                        )
                    }
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
            onIncrementQuantity = viewModel::onIncrementSessionCardQuantity,
            onDecrementQuantity = viewModel::onDecrementSessionCardQuantity,
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
                onNavigateToDeck ={ id ->
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

/**
 * Binds and renders the CameraX preview + [ImageAnalysis] pipeline.
 *
 * ### Pause vs. stop contract (W3, `scanner-reliability-plan.md`, 2026-08-24)
 * Two independent, differently-scoped conditions:
 * - [isRecognitionPausedByUser] (top-bar toggle) — the preview STAYS bound and live (the user
 *   still wants a viewfinder); only the [ImageAnalysis] analyzer is detached
 *   ([ImageAnalysis.clearAnalyzer]) and re-attached ([ImageAnalysis.setAnalyzer]) on resume, via
 *   the `LaunchedEffect(isRecognitionPausedByUser, boundImageAnalysis)` below.
 * - [isCameraActive] (`false` while a covering sheet/overlay — queue, edit, variant selector,
 *   expanded image, card detail, price detail — is open, computed by the caller) — a STRONGER
 *   condition: the camera session is FULLY unbound ([ProcessCameraProvider.unbindAll]) after a
 *   [CAMERA_STOP_DEBOUNCE_MS] debounce, which actually stops the sensor/ISP/preview surface
 *   (privacy-dot off, real power savings), not just frame delivery. Rebinding on `isCameraActive`
 *   flipping back to `true` is immediate (no debounce) so the user is not left staring at a black
 *   preview. See [ScannerUiState.isRecognitionPausedByUser]'s KDoc for the full rationale.
 *
 * @param isFlashOn                 Torch on/off — also restored immediately after a rebind
 *                                  ([Camera.cameraControl.enableTorch]) since the separate
 *                                  `LaunchedEffect(isFlashOn)` below will not re-fire on a rebind
 *                                  (the value itself did not change).
 * @param isRecognitionPausedByUser See "Pause vs. stop" above.
 * @param isCameraActive            See "Pause vs. stop" above.
 * @param overlayReason             WS4 (2026-08-25): which covering sheet/overlay caused
 *                                  [isCameraActive] to be `false` (`"queue"`, `"edit"`,
 *                                  `"variant_selector"`, `"expanded_image"`, `"card_detail"`,
 *                                  `"price_detail"`), or `null` while the camera is active. A
 *                                  fixed closed set computed by the caller — never user input —
 *                                  used only as the payload for the `scanner_camera_stopped_for_sheet`
 *                                  breadcrumb below.
 */
@Composable
private fun CameraPreview(
    isFlashOn: Boolean,
    isRecognitionPausedByUser: Boolean,
    isCameraActive: Boolean,
    overlayReason: String?,
    selectedLanguage: String,
    onRecognitionResult: (RecognitionResult) -> Unit,
    onFlashAvailability: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mc = MaterialTheme.magicColors

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    // Retained so the merged DisposableEffect below can unbind/clear them on dispose —
    // previously only [camera] was retained, so dispose could never reach these (F11/W1.3).
    var boundImageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // WS4: tracks whether the camera was actually stopped for a covering overlay, so
    // scanner_camera_resumed only fires on a rebind that follows a REAL stop — never on the
    // initial screen bind (which carries no diagnostic value).
    var wasStoppedForOverlay by remember { mutableStateOf(false) }

    val entryPoint = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ScannerEntryPoint::class.java)
    }
    val cardRepository = remember { entryPoint.cardRepository() }
    // recreateIfNeeded() ensures the process-wide ML Kit client is live on every scanner
    // (re)entry — it is a no-op when the client is already alive (W1.1).
    val cardOcrAnalyzer = remember {
        entryPoint.cardOcrAnalyzer().also { it.recreateIfNeeded() }
    }
    // W2.7: local-first Room lookup so a card the user already owns/has cached resolves with
    // zero network calls — see CardRecognizer's KDoc "Call-budget pipeline".
    val cardDao = remember { entryPoint.cardDao() }

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
            cardDao = cardDao,
            initialLanguage = selectedLanguage,
            onResult = onRecognitionResult,
        )
    }

    // The custom setter on CardRecognizer.selectedLanguage (W2.11) transparently resets the
    // negative cache, the resolution generation, the pre-resolution stability buffer, the local
    // 3s memo, and any active rate-limit cooldown whenever the value actually changes — this
    // effect only needs to forward the new value, no extra reset call needed here.
    LaunchedEffect(selectedLanguage) {
        recognizer.selectedLanguage = selectedLanguage
    }

    // W2.9: bump the resolution generation the moment the user pauses via the top-bar toggle, so
    // an in-flight resolution attempt's result is dropped instead of surfacing late after the
    // user has moved on. The covering-sheet/overlay case is handled separately below by
    // recognizer.resetForCameraStop() when isCameraActive flips to false (W3.5) — it is a
    // stronger condition (full camera stop) with its own reset, not just a generation bump.
    LaunchedEffect(isRecognitionPausedByUser) {
        if (isRecognitionPausedByUser) recognizer.bumpGeneration()
    }

    // Single dispose path (W1.3) — order matters:
    // 1. clearAnalyzer() stops new frames being delivered to the recognizer.
    // 2. unbindAll() releases the camera session (previously only called before binding,
    //    never on dispose — the camera stayed bound past screen exit).
    // 3. recognizerScope.cancel() cancels any in-flight OCR/resolution coroutine.
    // 4. analysisExecutor.shutdown() runs LAST so it outlives the last delivered frame —
    //    shutting it down first risks RejectedExecutionException on a frame already
    //    in-flight to the executor.
    // Note: CardOcrAnalyzer is a process-wide singleton and must never be closed here —
    // see CardOcrAnalyzer's KDoc lifecycle contract (W1.1).
    DisposableEffect(Unit) {
        onDispose {
            boundImageAnalysis?.clearAnalyzer()
            boundCameraProvider?.unbindAll()
            recognizerScope.cancel()
            analysisExecutor.shutdown()
        }
    }

    // W3.2: rememberUpdatedState so the bind LaunchedEffect always restores the freshest torch
    // value after a rebind (see this composable's "Pause vs. stop" KDoc).
    val currentIsFlashOn by rememberUpdatedState(isFlashOn)

    val frameMetadataAnalyzer = remember {
        FrameMetadataAnalyzer(delegate = recognizer)
    }

    // W3.1: the top-bar pause toggle only detaches/re-attaches the ImageAnalysis analyzer —
    // clearAnalyzer() stops CameraX from ever invoking FrameMetadataAnalyzer.analyze() at all
    // (not merely dropping frames after delivery, as before this workstream), while the preview
    // stays bound and live. Keyed on [boundImageAnalysis] too so the correct attach/detach state
    // is re-applied every time the camera use case changes identity — in particular after a full
    // stop/resume cycle driven by [isCameraActive] below.
    LaunchedEffect(isRecognitionPausedByUser, boundImageAnalysis) {
        val analysis = boundImageAnalysis ?: return@LaunchedEffect
        if (isRecognitionPausedByUser) {
            analysis.clearAnalyzer()
        } else {
            analysis.setAnalyzer(analysisExecutor, frameMetadataAnalyzer)
        }
    }

    // Bind (or fully unbind) the camera asynchronously so we never block the main thread.
    // W3.2 (2026-08-24): the effect now also keys on [isCameraActive] — flipping it re-runs this
    // whole block, cancelling whatever the previous run was doing (including a pending unbind
    // delay below, which is how W3.3's debounce gets cancelled for free on a fast resume).
    LaunchedEffect(lifecycleOwner, previewViewRef, isCameraActive) {
        val pv = previewViewRef ?: return@LaunchedEffect

        if (!isCameraActive) {
            // W3.3: debounce the UNBIND only — a fast open→close of a covering sheet must not
            // thrash the camera session (a rebind can cost ~200-600 ms on some devices).
            // Cancelled automatically if isCameraActive flips back to true before this elapses,
            // because that flip re-keys this LaunchedEffect and cancels this very coroutine.
            kotlinx.coroutines.delay(CAMERA_STOP_DEBOUNCE_MS)
            boundImageAnalysis?.clearAnalyzer()
            boundCameraProvider?.unbindAll()
            boundImageAnalysis = null
            boundCameraProvider = null
            camera = null
            // W3.5: reset the recognizer's own transient pipeline state (bumps the resolution
            // generation — reusing bumpGeneration(), not a parallel mechanism — and clears the
            // pre-resolution stability buffer) so a resumed session never silently "completes" a
            // match against OCR text collected before the stop.
            recognizer.resetForCameraStop()
            // WS4: bounded by human interaction (opening a sheet), not frame rate — a real
            // log() breadcrumb is correct here, unlike the per-frame counters in CardRecognizer.
            wasStoppedForOverlay = true
            FirebaseCrashlytics.getInstance().log("scanner_camera_stopped_for_sheet: reason=$overlayReason")
            return@LaunchedEffect
        }

        // Suspend without blocking the main thread until the provider is ready.
        val cameraProvider = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resumeWith(runCatching { future.get() }) },
                androidx.core.content.ContextCompat.getMainExecutor(context),
            )
        }
        boundCameraProvider = cameraProvider

        // ResolutionSelector replaces the deprecated setTargetResolution API (CameraX 1.3+).
        // FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER lets CameraX pick the nearest
        // available resolution when 720×1280 is not supported by the sensor.
        //
        // W2.2 (2026-08-24): PreviewView uses ImplementationMode.COMPATIBLE with the default
        // FILL_CENTER scale type, while ImageAnalysis previously requested only a target
        // resolution with no aspect-ratio pin — if the sensor's chosen resolution for preview
        // and analysis ended up with different aspect ratios, PreviewView's FILL_CENTER crop
        // meant the visible frame was NOT the same rectangle ImageAnalysis delivered, so
        // NameZoneIndicator's on-screen fractions did not map 1:1 onto CardOcrAnalyzer's zone
        // fractions (finding F3). Pinning both use cases to the same AspectRatioStrategy makes
        // preview and analysis share one aspect ratio, so ScannerZone's fractions mean the same
        // rectangle in both places. RATIO_16_9_FALLBACK_AUTO_STRATEGY was chosen over a hard
        // 4:3/16:9 requirement because it degrades gracefully (AUTO fallback) on sensors that
        // cannot deliver 16:9, rather than failing use-case binding outright.
        val aspectRatioStrategy = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(720, 1280),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()
        val previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(previewResolutionSelector)
            .build()
            .also {
                it.setSurfaceProvider(pv.surfaceProvider)
            }
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor, frameMetadataAnalyzer)
            }
        boundImageAnalysis = imageAnalysis

        runCatching {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
            onFlashAvailability(camera?.cameraInfo?.hasFlashUnit() ?: false)
            // W3.2: restore torch state immediately after a (re)bind. The separate
            // LaunchedEffect(isFlashOn) below only fires when isFlashOn itself CHANGES, so a
            // rebind triggered by isCameraActive (torch value unchanged throughout) would
            // otherwise silently come back with the torch off. currentIsFlashOn is read via
            // rememberUpdatedState so this always restores the freshest value even though this
            // LaunchedEffect isn't keyed on isFlashOn.
            camera?.cameraControl?.enableTorch(currentIsFlashOn)
            // WS4: only when this rebind follows a REAL stop (never the initial screen bind),
            // and only inside runCatching so it fires solely on a genuinely successful rebind.
            if (wasStoppedForOverlay) {
                FirebaseCrashlytics.getInstance().log("scanner_camera_resumed")
                wasStoppedForOverlay = false
            }
        }
    }

    // Live torch toggling while the camera stays continuously bound (does not fire on a rebind
    // since isFlashOn itself doesn't change then — see the restore call right after bindToLifecycle
    // above for that case).
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

        // W3.4: PreviewView goes solid black the instant the camera is unbound, and that can be
        // visible for a moment before/around a covering sheet's own surface finishes drawing over
        // it. A themed scrim in the same spot turns that into a themed fade instead of a raw
        // black flash — ManaHub tokens only (mc.background), no hardcoded colour.
        AnimatedVisibility(
            visible = !isCameraActive,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(mc.background))
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

// ─────────────────────────────────────────────────────────────────────────────
//  Name-zone indicator — thin horizontal strip showing where to align card name
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a minimal horizontal bracket indicating the region where the card name
 * should be placed for OCR. The zone fractions come from [ScannerZone] — the single source of
 * truth shared with [CardOcrAnalyzer], which restricts its OCR candidates to the same band (plus
 * an analyzer-only tolerance) so the visual guide and the scan area align (WS2.2, 2026-08-24).
 */
@Composable
private fun NameZoneIndicator(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val accentColor = mc.primaryAccent
    val ty = MaterialTheme.magicTypography

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenH = maxHeight
        val screenW = maxWidth

        val zoneTopFrac    = ScannerZone.TOP
        val zoneBottomFrac = ScannerZone.BOTTOM
        val zoneWidthFrac  = ScannerZone.WIDTH

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
 *
 * W3.1 (scanner-reliability-plan.md, 2026-08-24): no longer takes an `isPaused` lambda — pausing
 * is now enforced UPSTREAM by detaching this analyzer entirely (`ImageAnalysis.clearAnalyzer()`
 * in [CameraPreview]), so CameraX never invokes [analyze] at all while paused, instead of this
 * class dropping frames it was still being handed.
 */
private class FrameMetadataAnalyzer(
    private val delegate: CardRecognizer,
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
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
    /** W2.7: local-first Room lookup for [CardRecognizer] — already Hilt-provided for [com.mmg.manahub.core.data.repository.CardRepositoryImpl]. */
    fun cardDao(): com.mmg.manahub.core.data.local.dao.CardDao
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
 * keep offering every language Scryfall indexes, ja/ko/ru/zhs/zht included; that's independent of
 * what [com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer] can recognize on a physical card).
 *
 * ja/ko excluded 2026-07-22 (Android 16 / API 36 migration): the ML Kit `-japanese`/`-korean`
 * text-recognition artefacts were removed end-to-end (native libs not 16KB-page-size aligned,
 * no fixed release upstream) — see [com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer].
 *
 * ru/zhs/zht ALSO excluded (W2.12, scanner-reliability-plan.md finding F10, 2026-08-24): the
 * bundled recognizer is [com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS]
 * -- Latin script ONLY. Cyrillic and Chinese glyphs can never be read from a physical card
 * regardless of which language is selected here, so offering them was pure wasted Scryfall
 * requests (every scan would fall through the same failing-lookup path as F6). The remaining set
 * (en/es/de/fr/it/pt) matches exactly the language set [com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer]'s
 * keyword-penalty scoring understands.
 */
private val OCR_SUPPORTED_LANGUAGES = CardConstants.languages.filter { (code, _) ->
    code in setOf("en", "es", "de", "fr", "it", "pt")
}

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
    selectedLanguage: String,
    isFoil: Boolean,
    preferredCurrency: PreferredCurrency,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.background.withAlpha(0.85f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (card != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 0.dp, end = 0.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = mc.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // W2.11: purely informational — the card is added regardless. languageMismatch
                    // here means "no <selectedLanguage> printing exists, added the English print".
                    if (languageMismatch) {
                        Surface(
                            color = mc.goldMtg.withAlpha(0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.scanner_language_fallback_added_as_en,
                                    selectedLanguage.uppercase(),
                                ),
                                style = ty.labelSmall,
                                color = mc.goldMtg,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
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

/**
 * W2.10 badge: shown above [DetectedCardOverlay] while [CardRecognizer]'s active rate-limit
 * cooldown ([ScannerUiState.rateLimitedUntilMs]) is running. OCR keeps highlighting text in the
 * name zone underneath — only Scryfall lookups are suspended — so this reads as a status strip,
 * not a full-screen block.
 */
@Composable
private fun RateLimitedBadge(remainingSeconds: Int, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.lifeNegative.withAlpha(0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.scanner_rate_limited_retrying, remainingSeconds),
            style = ty.labelSmall,
            color = mc.lifeNegative,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
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
    onIncrementQuantity: (ScannedCard) -> Unit,
    onDecrementQuantity: (ScannedCard) -> Unit,
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
            sheetToastState.show(it, uiState.toastType)
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                    Text(
                        text = stringResource(R.string.scanner_queue_title, session.cards.size),
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f)
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
                    items(filtered, key = { it.id }) { entry ->
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
                            onIncrement = { onIncrementQuantity(entry) },
                            onDecrement = { onDecrementQuantity(entry) },
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
                        enabled = !uiState.isCommittingQueue,
                        isLoading = uiState.isCommittingQueue,
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
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
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
                    text = entry.card.name,
                    style = ty.titleMedium.withWeight(FontWeight.Bold).withFontSize(18.sp),
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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

                    QuantitySelector(
                        quantity = entry.quantity,
                        onIncrement = onIncrement,
                        onDecrement = onDecrement
                    )
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
            QueueActionButton(
                icon = Icons.Rounded.Style,
                label = stringResource(R.string.action_add),
                tint = mc.primaryAccent,
                onClick = onAddToCollection,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.FavoriteBorder,
                label = stringResource(R.string.carddetail_add_to_wishlist),
                tint = mc.secondaryAccent,
                onClick = onAddToWishlist,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.Clear,
                label = stringResource(R.string.action_remove),
                tint = mc.lifeNegative,
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.action_edit),
                tint = mc.textSecondary,
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.ContentCopy,
                label = stringResource(R.string.scanner_duplicate_entry),
                tint = mc.textSecondary,
                onClick = onDuplicate,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = mc.textPrimary.withAlpha(0.05f)
        )
    }
}

@Composable
private fun QuantitySelector(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = modifier
            .background(mc.textPrimary.withAlpha(0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onDecrement, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Remove, null, tint = mc.textPrimary, modifier = Modifier.size(16.dp))
        }
        Text(
            text = quantity.toString(),
            style = ty.titleLarge.withWeight(FontWeight.Bold),
            color = mc.secondaryAccent,
            modifier = Modifier.widthIn(min = 20.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, null, tint = mc.textPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QueueActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ty = MaterialTheme.magicTypography
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = ty.labelSmall.withFontSize(9.sp),
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
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
