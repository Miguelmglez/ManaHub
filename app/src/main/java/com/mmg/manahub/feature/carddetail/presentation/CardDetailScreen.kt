package com.mmg.manahub.feature.carddetail.presentation


import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.CardSharedBoundsTransform
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.CardTagGroup
import com.mmg.manahub.core.ui.components.CopyBadge
import com.mmg.manahub.core.ui.components.DeckItem
import com.mmg.manahub.core.ui.components.FoilBadge
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.FullScreenImageViewer
import com.mmg.manahub.core.ui.components.LanguageBadge
import com.mmg.manahub.core.ui.components.MagicActionRow
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaSize
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicGlowCard
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.TradeSelectionSheet
import com.mmg.manahub.core.ui.components.VariantSelectorSheet
import com.mmg.manahub.core.ui.components.rememberFabVisibility
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberRateLimitCountdownSeconds
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.util.PriceFormatter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CardDetailScreen(
    onBack: () -> Unit,
    onNavigateToAddCard: () -> Unit,
    onNavigateToDeck: (String) -> Unit = {},
    onNavigateToCard: (scryfallId: String) -> Unit = {},
    onNavigateToCommunityDecks: (cardName: String) -> Unit = {},
    viewModel: CardDetailViewModel = koinViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionKey: Any? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val linkOpenFailedMessage = stringResource(R.string.carddetail_links_open_failed)
    val textSelectionController = remember { CardDetailTextSelectionController() }
    val contentScrollState = rememberScrollState()
    val isFabVisible = rememberFabVisibility(contentScrollState)
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var showActionSheet by rememberSaveable { mutableStateOf(false) }
    val collectionCopyCount = uiState.userCards.sumOf { it.userCard.quantity.toLong() }
    val onLinkOpenFailed = {
        toastState.show(linkOpenFailedMessage, MagicToastType.ERROR)
    }
    val selectCardAction: (() -> Unit) -> Unit = { action ->
        coroutineScope.launch {
            actionSheetState.hide()
            showActionSheet = false
            action()
        }
    }

    // Screen-entry breadcrumb (no PII) — CardDetail had ZERO Crashlytics breadcrumbs before the
    // edge-case/telemetry audit (2026-07-15).
    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: card_detail")
    }
    // The displayed printing can change without leaving this screen (language/variant switches),
    // so the scryfall-id custom key is kept fresh in its own effect, separate from the one-shot
    // screen_viewed breadcrumb above.
    LaunchedEffect(uiState.card?.scryfallId) {
        uiState.card?.scryfallId?.let {
            FirebaseCrashlytics.getInstance().setCustomKey("card_detail_scryfall_id", it)
        }
    }

    // Collect one-shot events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CardDetailEvent.ShowToast -> toastState.show(
                    event.message,
                    when (event.severity) {
                        ToastSeverity.SUCCESS -> MagicToastType.SUCCESS
                        ToastSeverity.INFO -> MagicToastType.INFO
                        ToastSeverity.ERROR -> MagicToastType.ERROR
                    },
                )
                is CardDetailEvent.NavigateToCard -> onNavigateToCard(event.scryfallId)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearCardDetailSelectionOnTap(textSelectionController),
    ) {

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                val mc = MaterialTheme.magicColors
                AnimatedVisibility(
                    visible = uiState.card != null && isFabVisible,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = { showActionSheet = true },
                        containerColor = mc.primaryAccent,
                        contentColor = mc.background,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.carddetail_action_sheet_title),
                        )
                    }
                }
            },
            topBar = {
                val mc = MaterialTheme.magicColors
                Surface(
                    color = mc.backgroundSecondary,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
                        }
                        CardDetailSelectableText(
                            selectionController = textSelectionController,
                            modifier = Modifier.weight(1f),
                            onOpenFailed = onLinkOpenFailed,
                        ) {
                            CardName(
                                name = uiState.card?.printedName ?: uiState.card?.name ?: "",
                                style = MaterialTheme.magicTypography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Language selector — shows the displayed print's language flag; tap opens
                        // the language picker sheet (Card Versions & Languages, Phase 1B).
                        val langButtonDescription = stringResource(R.string.carddetail_language_button_description)
                        IconButton(
                            onClick = viewModel::onOpenLanguageSelector,
                            modifier = Modifier.semantics { contentDescription = langButtonDescription },
                        ) {
                            Text(
                                text = CardConstants.getFlag(uiState.card?.lang ?: "en"),
                                style = MaterialTheme.magicTypography.titleLarge,
                            )
                        }
                    }
                }
            }

        ) { padding ->
            when {
                uiState.isLoading -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { MagicLoadingSpinner() }

                uiState.card != null -> CardDetailContent(
                    card = uiState.card!!,
                    userCards = uiState.userCards,
                    wishlistEntries = uiState.wishlistEntries,
                    tradeQuantities = uiState.tradeQuantities,
                    decksContainingCard = uiState.decksContainingCard,
                    selectionController = textSelectionController,
                    scrollState = contentScrollState,
                    onAddUserTag = viewModel::onAddUserTag,
                    onRemoveUserTag = viewModel::onRemoveUserTag,
                    onShowTagPicker = viewModel::onShowTagPicker,
                    onShowVariantSelector = viewModel::onOpenVariantSelector,
                    onEditCollectionEntry = viewModel::onEditCollectionEntry,
                    onEditWishlistEntry = viewModel::onEditWishlistEntry,
                    onRequestDelete = viewModel::onRequestDelete,
                    onRequestDeleteWishlist = viewModel::onRequestDeleteWishlist,
                    onNavigateToDeck = onNavigateToDeck,
                    onFindCommunityDecks = onNavigateToCommunityDecks,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedTransitionKey = sharedTransitionKey,
                    onLinkOpenFailed = onLinkOpenFailed,
                    modifier = Modifier.padding(padding),
                )

                // Initial load failed: offline + uncached, 404, or rate limited.
                else -> CardDetailLoadError(
                    error = uiState.error,
                    onRetry = viewModel::onRetryLoad,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        // Toast overlay — sits above the Scaffold
        MagicToastHost(state = toastState)

    } // end Box

    if (showActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            sheetState = actionSheetState,
            contentWindowInsets = { WindowInsets(0) },
            dragHandle = null,
            shape = BottomSheetShape,
            containerColor = MaterialTheme.magicColors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.carddetail_action_sheet_title),
                    style = MaterialTheme.magicTypography.titleLarge,
                    color = MaterialTheme.magicColors.textPrimary,
                )
                MagicActionRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.carddetail_add_copy),
                    subtitle = stringResource(R.string.carddetail_add_copy_action_subtitle),
                    onClick = { selectCardAction(viewModel::onShowAddSheet) },
                )
                MagicActionRow(
                    icon = Icons.Default.FavoriteBorder,
                    title = stringResource(R.string.carddetail_wishlist_sheet_title),
                    subtitle = stringResource(R.string.carddetail_wishlist_action_subtitle),
                    onClick = { selectCardAction(viewModel::onShowWishlistSheet) },
                    accentColor = MaterialTheme.magicColors.goldMtg,
                )
                MagicActionRow(
                    icon = Icons.Default.SwapHoriz,
                    title = stringResource(R.string.carddetail_offer_for_trade),
                    subtitle = stringResource(R.string.carddetail_collection_copy_count, collectionCopyCount),
                    onClick = { selectCardAction(viewModel::onShowTradeSheet) },
                    enabled = collectionCopyCount > 0,
                    accentColor = MaterialTheme.magicColors.secondaryAccent,
                )
            }
        }
    }

    // Tag picker sheet
    if (uiState.showTagPicker) {
        TagPickerSheet(
            cardAutoTags = uiState.card?.tags ?: emptyList(),
            currentUserTags = uiState.card?.userTags ?: emptyList(),
            catalogEntries = uiState.tagCatalog,
            catalogLoading = uiState.isTagCatalogLoading,
            catalogError = uiState.tagCatalogError,
            onRetryCatalog = viewModel::onRetryTagCatalog,
            suggestedTags = uiState.card?.suggestedTags?.map { it.tag }.orEmpty(),
            userDefinedTags = uiState.userDefinedTags,
            onAddUserTag = viewModel::onAddUserTag,
            onRemoveUserTag = viewModel::onRemoveUserTag,
            onDismiss = viewModel::onDismissTagPicker,
        )
    }

    // Add-to-collection sheet — also serves as the EDIT sheet for an existing collection entry
    // (Card Versions & Languages, Phase 1B) when uiState.entryBeingEdited is non-null.
    if (uiState.showAddSheet) {
        val printing = uiState.sheetPrinting ?: uiState.card
        val editingEntry = uiState.entryBeingEdited
        printing?.let { card ->
            AddCardSheet(
                cardName = card.printedName ?: card.name,
                cardImage = card.imageNormal,
                setCode = card.setCode,
                setName = card.setName,
                rarity = card.rarity,
                manaCost = card.manaCost,
                initialFoil = editingEntry?.userCard?.isFoil ?: false,
                initialCondition = editingEntry?.userCard?.condition ?: "NM",
                initialLanguage = editingEntry?.userCard?.language ?: card.lang,
                initialQty = editingEntry?.userCard?.quantity ?: 1,
                confirmButtonText = if (editingEntry != null) {
                    stringResource(R.string.carddetail_save_changes)
                } else {
                    stringResource(R.string.carddetail_add_copy)
                },
                onOpenVariantSelector = viewModel::onOpenVariantSelectorForSheet,
                onConfirm = { isFoil: Boolean, condition: String, language: String, qty: Int ->
                    if (editingEntry != null) {
                        viewModel.onUpdateCollectionEntry(isFoil, condition, language, qty)
                    } else {
                        viewModel.onAddToCollection(isFoil, condition, language, qty)
                    }
                },
                onDismiss = viewModel::onDismissAddSheet,
            )
        }
    }

    // Add-to-wishlist sheet — also serves as the EDIT sheet for an existing wishlist entry
    // (Card Versions & Languages, Phase 1B) when uiState.wishlistEntryBeingEdited is non-null.
    if (uiState.showWishlistSheet) {
        val printing = uiState.sheetPrinting ?: uiState.card
        val editingWishlistEntry = uiState.wishlistEntryBeingEdited
        printing?.let { card ->
            AddCardSheet(
                cardName = card.printedName ?: card.name,
                cardImage = card.imageNormal,
                setCode = card.setCode,
                setName = card.setName,
                rarity = card.rarity,
                manaCost = card.manaCost,
                initialFoil = editingWishlistEntry?.isFoil ?: false,
                initialCondition = editingWishlistEntry?.condition ?: "NM",
                initialLanguage = editingWishlistEntry?.language ?: card.lang,
                initialQty = editingWishlistEntry?.quantity ?: 1,
                confirmButtonText = if (editingWishlistEntry != null) {
                    stringResource(R.string.carddetail_save_changes)
                } else {
                    stringResource(R.string.carddetail_wishlist_sheet_title)
                },
                onOpenVariantSelector = viewModel::onOpenVariantSelectorForSheet,
                onConfirm = { isFoil: Boolean, condition: String, language: String, qty: Int ->
                    if (editingWishlistEntry != null) {
                        viewModel.onUpdateWishlistEntry(isFoil, condition, language, qty)
                    } else {
                        viewModel.onAddToWishlist(isFoil, condition, language, qty)
                    }
                },
                onDismiss = viewModel::onDismissWishlistSheet,
            )
        }
    }

    // Mark as tradeable sheet
    if (uiState.showTradeSheet) {
        TradeSelectionSheet(
            userCards = uiState.userCards.map { it.userCard },
            currentTradeQty = uiState.tradeQuantities,
            onConfirm = viewModel::onConfirmTradeSelection,
            onDismiss = viewModel::onDismissTradeSheet,
        )
    }

    // Delete confirmation
    uiState.cardToDelete?.let { uc ->
        MagicAlertDialog(
            onDismissRequest = viewModel::onDismissDeleteConfirm,
            title = stringResource(R.string.carddetail_delete_copy_title),
            text = stringResource(R.string.carddetail_delete_copy_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { viewModel.onDeleteCard(uc.id) },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::onDismissDeleteConfirm,
            confirmColor = MagicCtaColor.Error
        )
    }

    uiState.wishlistEntryToDelete?.let { entry ->
        MagicAlertDialog(
            onDismissRequest = viewModel::onDismissWishlistDeleteConfirm,
            title = stringResource(R.string.carddetail_remove_wishlist_title),
            text = stringResource(R.string.carddetail_remove_wishlist_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { viewModel.onDeleteWishlistEntry(entry.id) },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::onDismissWishlistDeleteConfirm,
            confirmColor = MagicCtaColor.Error
        )
    }

    // Variant (other prints) selector — opened either from the main screen ("Explore All
    // Versions") or from within the add/edit sheet's "Set / Variant" field, see
    // VariantSelectorSource. currentCardId highlights whichever printing is relevant to the
    // currently-open surface.
    if (uiState.showVariantSelector) {
        VariantSelectorSheet(
            currentCardId = (uiState.sheetPrinting ?: uiState.card)?.scryfallId ?: "",
            variants = uiState.cardVariants,
            isLoading = uiState.isLoadingVariants,
            onDismiss = viewModel::onCloseVariantSelector,
            onSelectVariant = viewModel::onSelectVariant,
            onExpandImage = viewModel::onExpandVariantImage,
        )
    }
    if (uiState.expandedVariantImageUrl != null) {
        FullScreenImageViewer(
            imageUrl = uiState.expandedVariantImageUrl!!,
            onDismiss = viewModel::onCloseExpandedImage,
        )
    }

    // Language (print) selector — Card Versions & Languages, Phase 1B.
    if (uiState.showLanguageSelector) {
        CardDetailLanguageSheet(
            currentLangCode = uiState.card?.lang ?: "en",
            prints = uiState.languagePrints,
            isLoading = uiState.isLoadingLanguages,
            onDismiss = viewModel::onCloseLanguageSelector,
            onSelectPrint = viewModel::onSelectLanguagePrint,
            onSelectFallbackLanguage = viewModel::onSelectFallbackLanguage,
        )
    }
}

@Composable
private fun CardDetailLoadError(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rate-limit exhaustion: hold the retry until the shared cooldown ends instead of re-triggering the storm.
    val rateLimitRetryAfterMs = RateLimitExhaustedException.retryAfterMsOrNull(error)
    val remainingSeconds = rememberRateLimitCountdownSeconds(rateLimitRetryAfterMs)
    FullErrorState(
        message = when {
            rateLimitRetryAfterMs != null -> stringResource(R.string.error_rate_limited_message)
            error == "SCRYFALL_404" || error?.contains("404") == true -> stringResource(R.string.addcard_server_unavailable_subtitle)
            else -> stringResource(R.string.error_scryfall)
        },
        retryLabel = if (remainingSeconds > 0) {
            stringResource(R.string.error_rate_limited_retry_countdown, remainingSeconds)
        } else {
            stringResource(R.string.retry)
        },
        onRetry = onRetry,
        enabled = remainingSeconds <= 0,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Language (print) selector sheet — Card Versions & Languages, Phase 1B.
//  Visual style mirrors AddCardScreen's LanguageSelectorSheet. Lists the REAL prints returned by
//  CardRepository.getLanguagePrints when available; falls back to the full CardConstants.languages
//  list (informational only — no navigation) when the load failed or returned no results.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailLanguageSheet(
    currentLangCode: String,
    prints: List<Card>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectPrint: (Card) -> Unit,
    onSelectFallbackLanguage: (String) -> Unit,
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
                text = stringResource(R.string.carddetail_language_sheet_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MagicLoadingSpinner()
                    }
                }

                prints.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).navigationBarsPadding()
                    ) {
                        items(prints, key = { it.scryfallId }) { print ->
                            LanguageSheetRow(
                                flag = CardConstants.getFlag(print.lang),
                                name = CardConstants.getLanguageName(print.lang),
                                isSelected = print.lang == currentLangCode,
                                onClick = { onSelectPrint(print) },
                            )
                        }
                    }
                }

                else -> {
                    // No real prints resolved (load failed or genuinely no other languages) —
                    // fall back to the full language list; only real prints navigate.
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).navigationBarsPadding()
                    ) {
                        items(CardConstants.languages, key = { it.first }) { (code, flag) ->
                            LanguageSheetRow(
                                flag = flag,
                                name = CardConstants.getLanguageName(code),
                                isSelected = code == currentLangCode,
                                onClick = { onSelectFallbackLanguage(code) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LanguageSheetRow(
    flag: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.background(mc.primaryAccent.copy(alpha = 0.08f))
                else Modifier
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = flag, style = ty.titleLarge)
        Text(
            text = name,
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

@Composable
private fun FaceFlippable(
    rotation: Float,
    modifier: Modifier = Modifier,
    content: @Composable (isBack: Boolean) -> Unit
) {
    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 12f * density
        }
    ) {
        if (rotation >= -90f) {
            content(false)
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f }) {
                content(true)
            }
        }
    }
}

private object SearchWebContextMenuKey

private class CardDetailTextSelectionController {
    private val selectionStates = mutableSetOf<SelectionState>()

    fun register(selectionState: SelectionState) {
        selectionStates += selectionState
    }

    fun unregister(selectionState: SelectionState) {
        selectionStates -= selectionState
    }

    fun clearSelections() {
        selectionStates.toList().forEach(SelectionState::clear)
    }
}

@Composable
private fun CardDetailSelectableText(
    selectionController: CardDetailTextSelectionController,
    modifier: Modifier = Modifier,
    onOpenFailed: () -> Unit,
    content: @Composable () -> Unit,
) {
    val selectionState = rememberSelectionState()
    val uriHandler = LocalUriHandler.current
    val searchWebLabel = stringResource(R.string.action_search_web)

    DisposableEffect(selectionController, selectionState) {
        selectionController.register(selectionState)
        onDispose { selectionController.unregister(selectionState) }
    }

    SelectionContainer(
        state = selectionState,
        modifier = modifier.appendTextContextMenuComponents {
            val selectedText = selectionState.selectedTexts
                .joinToString(separator = "\n") { it.text }
                .trim()
            if (selectedText.isNotEmpty()) {
                separator()
                item(key = SearchWebContextMenuKey, label = searchWebLabel) {
                    runCatching {
                        uriHandler.openUri(
                            "https://www.google.com/search?q=${Uri.encode(selectedText)}",
                        )
                    }.onFailure {
                        FirebaseCrashlytics.getInstance().log("card_detail_external_link_open_failed")
                        onOpenFailed()
                    }
                    close()
                }
            }
        },
        content = content,
    )
}

private fun Modifier.clearCardDetailSelectionOnTap(
    selectionController: CardDetailTextSelectionController,
): Modifier = pointerInput(selectionController) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val touchSlopSquared = viewConfiguration.touchSlop * viewConfiguration.touchSlop
        var shouldClearSelection = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed && it.id != down.id }) break

            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
            val dx = pointer.position.x - down.position.x
            val dy = pointer.position.y - down.position.y
            if (dx * dx + dy * dy > touchSlopSquared) break

            if (!pointer.pressed) {
                shouldClearSelection =
                    pointer.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                break
            }
        }

        if (shouldClearSelection) selectionController.clearSelections()
    }
}

private fun Modifier.animateEnterIn(
    scope: AnimatedVisibilityScope?,
    enter: EnterTransition,
): Modifier = if (scope == null) this else with(scope) {
    this@animateEnterIn.animateEnterExit(enter = enter, exit = fadeOut())
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardDetailContent(
    card: Card,
    selectionController: CardDetailTextSelectionController,
    scrollState: ScrollState,
    userCards: List<UserCardWithCard>,
    wishlistEntries: List<WishlistEntry>,
    tradeQuantities: Map<String, Int>,
    decksContainingCard: List<DeckSummary>,
    onAddUserTag: (CardTag) -> Unit,
    onRemoveUserTag: (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
    onShowVariantSelector: () -> Unit,
    onEditCollectionEntry: (UserCardWithCard) -> Unit,
    onEditWishlistEntry: (WishlistEntry) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
    onRequestDeleteWishlist: (WishlistEntry) -> Unit,
    onNavigateToDeck: (String) -> Unit,
    onFindCommunityDecks: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionKey: Any? = null,
    onLinkOpenFailed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showBackFace by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (showBackFace) -180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "CardFlip"
    )

    val frontFace = card.cardFaces?.firstOrNull()
    val backFace = card.cardFaces?.getOrNull(1)
    var yourFolderExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }
    var collectionExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }
    var tradeExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }
    var wishlistExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }
    var legalityExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }
    var tagsExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }
    var decksExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }
    var externalLinksExpanded by rememberSaveable(card.scryfallId) { mutableStateOf(true) }

    // Staggered animation for content
    val staggeredEnter = remember {
        slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(400))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card image — tap to flip for DFC
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .aspectRatio(0.716f)
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(
                                        key = sharedTransitionKey ?: "card-image-${card.scryfallId}"
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(CardShape),
                                    boundsTransform = CardSharedBoundsTransform,
                                    renderInOverlayDuringTransition = true,
                                )
                            }
                        } else Modifier
                    )
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                        // Force hardware layer during transition to prevent "snapping"
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .clip(CardShape)
                    .then(
                        if (card.imageBackNormal != null)
                            Modifier.clickable { showBackFace = !showBackFace }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Front Face
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                        .data(card.imageNormal)
                        .crossfade(false) // Disable Coil fade to prioritize shared transition fade
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = card.name,
                    placeholder = painterResource(Res.drawable.mtg_card_back),
                    error = painterResource(Res.drawable.mtg_card_back),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (rotation >= -90f) 1f else 0f
                        },
                )

                // Back Face
                if (card.imageBackNormal != null) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(card.imageBackNormal)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = card.name,
                        placeholder = painterResource(Res.drawable.mtg_card_back),
                        error = painterResource(Res.drawable.mtg_card_back),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = 180f
                                alpha = if (rotation < -90f) 1f else 0f
                            },
                    )
                }
            }

            if (card.imageBackNormal != null) {
                val mc = MaterialTheme.magicColors
                Surface(
                    onClick = { showBackFace = !showBackFace },
                    shape = ChipShape,
                    color = mc.primaryAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = mc.primaryAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (showBackFace)
                                stringResource(R.string.carddetail_flip_see_front)
                            else
                                stringResource(R.string.carddetail_flip_see_back),
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = mc.primaryAccent,
                        )
                    }
                }
            }
        }

        // Staggered entrance only when hosted in an AnimatedVisibilityScope (nav destination); the Scanner overlay has none.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Name + badges
            FaceFlippable(
                rotation = rotation,
                modifier = Modifier.animateEnterIn(
                    animatedVisibilityScope,
                    staggeredEnter,
                )
            ) { isBack ->
                val name = if (isBack) {
                    backFace?.printedName ?: card.printedName ?: card.name
                } else {
                    frontFace?.printedName ?: card.printedName ?: card.name
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardDetailSelectableText(
                        selectionController = selectionController,
                        onOpenFailed = onLinkOpenFailed,
                    ) {
                        CardName(name, style = MaterialTheme.magicTypography.titleLarge)
                    }
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                    card.manaCost?.let { cost ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val costs = cost.split(" // ")
                            costs.forEachIndexed { index, singleCost ->
                                ManaCostImages(manaCost = singleCost, symbolSize = 20.dp)
                                if (index < costs.size - 1) {
                                    Text(
                                        " // ",
                                        style = MaterialTheme.magicTypography.titleMedium,
                                        color = MaterialTheme.magicColors.textSecondary,
                                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xxs)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mana cost + type
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.animateEnterIn(
                    animatedVisibilityScope,
                    slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(500, delayMillis = 100)
                    ) + fadeIn(tween(400, delayMillis = 100)),
                )
            ) {

                FaceFlippable(rotation = rotation) { isBack ->
                    val typeText = if (isBack) {
                        backFace?.typeLine ?: card.typeLine
                    } else {
                        frontFace?.typeLine ?: card.printedTypeLine.takeUnless { it.isNullOrEmpty() } ?: card.typeLine
                    }
                    CardDetailSelectableText(
                        selectionController = selectionController,
                        onOpenFailed = onLinkOpenFailed,
                    ) {
                        Text(
                            text = typeText,
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = MaterialTheme.magicColors.textSecondary,
                        )
                    }
                }

                // Set Icon + Set Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity = CardRarity.fromString(card.rarity),
                        size = 20.dp,
                    )
                    CardDetailSelectableText(
                        selectionController = selectionController,
                        onOpenFailed = onLinkOpenFailed,
                    ) {
                        Text(
                            text = card.setName,
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = MaterialTheme.magicColors.secondaryAccent,
                        )
                    }
                }
            }

            // Oracle / printed text
            FaceFlippable(
                rotation = rotation,
                modifier = Modifier.animateEnterIn(
                    animatedVisibilityScope,
                    slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(600, delayMillis = 200)
                    ) + fadeIn(tween(500, delayMillis = 200)),
                )
            ) { isBack ->
                val oracleDisplayText = if (isBack) {
                    backFace?.oracleText
                } else {
                    frontFace?.oracleText ?: card.printedText.takeUnless { it.isNullOrEmpty() } ?: card.oracleText
                }
                if (!oracleDisplayText.isNullOrEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.magicColors.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CardDetailSelectableText(
                            selectionController = selectionController,
                            onOpenFailed = onLinkOpenFailed,
                        ) {
                            OracleText(
                                text = oracleDisplayText,
                                style = MaterialTheme.magicTypography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }

            // Flavor text
            FaceFlippable(rotation = rotation) { isBack ->
                val flavorText = if (isBack) backFace?.flavorText else frontFace?.flavorText ?: card.flavorText
                flavorText?.let {
                    CardDetailSelectableText(
                        selectionController = selectionController,
                        onOpenFailed = onLinkOpenFailed,
                    ) {
                        Text(
                            text = "\"$it\"",
                            style = MaterialTheme.magicTypography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.magicColors.textSecondary,
                        )
                    }
                }
            }

            // Power/Toughness or Loyalty
            FaceFlippable(rotation = rotation) { isBack ->
                val face = if (isBack) backFace else frontFace
                val ptOrLoyalty = when {
                    face != null -> {
                        when {
                            face.power != null && face.toughness != null -> "${face.power}/${face.toughness}"
                            face.loyalty != null -> stringResource(R.string.carddetail_loyalty_value, face.loyalty!!)
                            else -> null
                        }
                    }
                    card.power != null && card.toughness != null -> "${card.power}/${card.toughness}"
                    card.loyalty != null -> stringResource(R.string.carddetail_loyalty_value, card.loyalty!!)
                    else -> null
                }

                if (ptOrLoyalty != null) {
                    val mc = MaterialTheme.magicColors
                    Surface(
                        color = mc.secondaryAccent.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, mc.secondaryAccent),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = ptOrLoyalty,
                            style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.secondaryAccent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }


            // Prices + Collection Section (grouped for fluid entry)
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.animateEnterIn(
                    animatedVisibilityScope,
                    slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(700, delayMillis = 300)
                    ) + fadeIn(tween(600, delayMillis = 300)),
                )
            ) {
                PriceSection(card = card)
                // Improved Variants & Prints Section
                MagicGlowCard {
                    Surface(
                        onClick = onShowVariantSelector,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Icon with a soft circular highlight
                            Box(
                                modifier = Modifier.size(40.dp).background(
                                        MaterialTheme.magicColors.primaryAccent.copy(alpha = 0.15f),
                                        CircleShape
                                    ), contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.magicColors.primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.carddetail_other_prints_title),
                                    style = MaterialTheme.magicTypography.titleMedium,
                                    color = MaterialTheme.magicColors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.carddetail_other_prints_desc),
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = MaterialTheme.magicColors.textSecondary
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.magicColors.textDisabled,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = CardShape,
                color = MaterialTheme.magicColors.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.magicColors.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.carddetail_your_folder),
                        icon = Icons.Default.Folder,
                        expanded = yourFolderExpanded,
                        onToggle = { yourFolderExpanded = !yourFolderExpanded },
                    )
                    AnimatedVisibility(
                        visible = yourFolderExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = SmallCardShape,
                                color = MaterialTheme.magicColors.surface.copy(alpha = if (collectionExpanded) 0.85f else 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SectionHeader(
                                        title = stringResource(R.string.carddetail_in_collection),
                                        icon = Icons.Default.ShoppingCart,
                                        expanded = collectionExpanded,
                                        onToggle = { collectionExpanded = !collectionExpanded },
                                    )
                                    AnimatedVisibility(
                                        visible = collectionExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        CollectionSection(
                                            userCards = userCards,
                                            displayedSetCode = card.setCode,
                                            tradeQuantities = tradeQuantities,
                                            onEditEntry = onEditCollectionEntry,
                                            onRequestDelete = onRequestDelete,
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = SmallCardShape,
                                color = MaterialTheme.magicColors.surface.copy(alpha = if (tradeExpanded) 0.85f else 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SectionHeader(
                                        title = stringResource(R.string.carddetail_trade_offered_header),
                                        icon = Icons.Default.SwapHoriz,
                                        expanded = tradeExpanded,
                                        onToggle = { tradeExpanded = !tradeExpanded },
                                    )
                                    AnimatedVisibility(
                                        visible = tradeExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        TradeSection(
                                            userCards = userCards,
                                            displayedSetCode = card.setCode,
                                            tradeQuantities = tradeQuantities,
                                            onEditEntry = onEditCollectionEntry,
                                            onRequestDelete = onRequestDelete,
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = SmallCardShape,
                                color = MaterialTheme.magicColors.surface.copy(alpha = if (wishlistExpanded) 0.85f else 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SectionHeader(
                                        title = stringResource(R.string.carddetail_in_wishlist),
                                        icon = Icons.Default.FavoriteBorder,
                                        expanded = wishlistExpanded,
                                        onToggle = { wishlistExpanded = !wishlistExpanded },
                                    )
                                    AnimatedVisibility(
                                        visible = wishlistExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        WishlistSection(
                                            entries = wishlistEntries,
                                            displayedSetCode = card.setCode,
                                            onEditEntry = onEditWishlistEntry,
                                            onRequestDelete = onRequestDeleteWishlist,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = CardShape,
                color = MaterialTheme.magicColors.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.magicColors.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.carddetail_legality_section_title),
                        icon = Icons.Default.Gavel,
                        expanded = legalityExpanded,
                        onToggle = { legalityExpanded = !legalityExpanded },
                    )
                    AnimatedVisibility(
                        visible = legalityExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        LegalitySection(card = card)
                    }
                }
            }

            Surface(
                shape = CardShape,
                color = MaterialTheme.magicColors.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.magicColors.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.carddetail_tags_section),
                        icon = Icons.Default.Label,
                        expanded = tagsExpanded,
                        onToggle = { tagsExpanded = !tagsExpanded },
                    )
                    AnimatedVisibility(
                        visible = tagsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        TagsSection(
                            autoTags = card.tags,
                            userTags = card.userTags,
                            isInCollection = userCards.isNotEmpty(),
                            onRemoveUserTag = onRemoveUserTag,
                            onShowTagPicker = onShowTagPicker,
                        )
                    }
                }
            }

            val foundDecksTitle = stringResource(
                R.string.carddetail_found_in_decks,
                decksContainingCard.size,
                stringResource(
                    if (decksContainingCard.size == 1) R.string.carddetail_deck else R.string.carddetail_decks
                ),
            )
            Surface(
                shape = CardShape,
                color = MaterialTheme.magicColors.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.magicColors.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(
                        title = foundDecksTitle,
                        icon = Icons.Default.Layers,
                        expanded = decksExpanded,
                        onToggle = { decksExpanded = !decksExpanded },
                    )
                    AnimatedVisibility(
                        visible = decksExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        FoundInDecksSection(
                            decks = decksContainingCard,
                            onNavigateToDeck = onNavigateToDeck,
                            onFindCommunityDecks = { onFindCommunityDecks(card.name) },
                        )
                    }
                }
            }

            Surface(
                shape = CardShape,
                color = MaterialTheme.magicColors.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.magicColors.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.carddetail_external_links_section_title),
                        icon = Icons.Default.OpenInBrowser,
                        expanded = externalLinksExpanded,
                        onToggle = { externalLinksExpanded = !externalLinksExpanded },
                    )
                    AnimatedVisibility(
                        visible = externalLinksExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        ExternalLinksSection(card = card, onOpenFailed = onLinkOpenFailed)
                    }
                }
            }

            // Extra bottom padding for FAB
            Spacer(Modifier.height(72.dp))
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Found in Decks section
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundInDecksSection(
    decks: List<DeckSummary>,
    onNavigateToDeck: (String) -> Unit,
    onFindCommunityDecks: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        if (decks.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                maxLines = 1,
            ) {
                decks.forEach { deck ->
                    key(deck.id) {
                        DeckItem(
                            deck = deck,
                            onClick = { onNavigateToDeck(deck.id) },
                            modifier = Modifier.width(160.dp),
                            cardBackPainter = painterResource(Res.drawable.mtg_card_back),
                            reduced = true,
                        )
                    }
                }
            }
        }

        MagicCtaButton(
            onClick = onFindCommunityDecks,
            style = MagicCtaStyle.Outlined,
            color = MagicCtaColor.Primary,
            size = MagicCtaSize.Normal,
            text = stringResource(R.string.community_deck_find_decks),
            icon = { Icon(Icons.Default.Group, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collection section: list of existing copies grouped by displayed set, then variants.
// ─────────────────────────────────────────────────────────────────────────────

// Edge-case audit A5 (2026-07-15): the oracle-wide Collection/Wishlist sections below render
// inside this screen's SINGLE verticalScroll Column (see CardDetailContent), not a LazyColumn —
// a card with many owned printings/languages otherwise renders every row unbounded. Both sections
// share this collapsed-row count.
private const val ORACLE_SECTION_COLLAPSED_COUNT = 5

@Composable
private fun CollectionSection(
    userCards: List<UserCardWithCard>,
    displayedSetCode: String,
    tradeQuantities: Map<String, Int>,
    onEditEntry: (UserCardWithCard) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
) {
    val availableCards = userCards.filter { (it.userCard.quantity - (tradeQuantities[it.userCard.id] ?: 0)) > 0 }
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (availableCards.isEmpty()) {
            Text(
                text = stringResource(R.string.carddetail_no_copies),
                style = MaterialTheme.magicTypography.bodySmall,
                color = MaterialTheme.magicColors.textSecondary,
            )
        } else {
            val (sameSet, otherSet) = availableCards.partition { it.card.setCode == displayedSetCode }
            var expanded by remember { mutableStateOf(false) }
            val combined = sameSet.map { it to false } + otherSet.map { it to true }
            val visible = if (expanded) combined else combined.take(ORACLE_SECTION_COLLAPSED_COUNT)
            var otherSetHeaderShown = false
            visible.forEach { (entry, isOtherSet) ->
                if (isOtherSet && !otherSetHeaderShown) {
                    Text(
                        text = stringResource(R.string.carddetail_variants_header),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = MaterialTheme.magicColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    otherSetHeaderShown = true
                }
                CollectionCopyRow(
                    entry = entry,
                    tradeQuantity = tradeQuantities[entry.userCard.id] ?: 0,
                    onEdit = onEditEntry,
                    onRequestDelete = onRequestDelete,
                )
            }
            if (combined.size > ORACLE_SECTION_COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.carddetail_show_less)
                        } else {
                            stringResource(R.string.carddetail_show_all, combined.size)
                        },
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = MaterialTheme.magicColors.primaryAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeSection(
    userCards: List<UserCardWithCard>,
    displayedSetCode: String,
    tradeQuantities: Map<String, Int>,
    onEditEntry: (UserCardWithCard) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
) {
    val tradeCards = userCards.filter { (tradeQuantities[it.userCard.id] ?: 0) > 0 }
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (tradeCards.isEmpty()) {
            Text(
                text = stringResource(R.string.carddetail_no_copies_for_trade),
                style = MaterialTheme.magicTypography.bodySmall,
                color = MaterialTheme.magicColors.textSecondary,
            )
        } else {
            val (sameSet, otherSet) = tradeCards.partition { it.card.setCode == displayedSetCode }
            var expanded by remember { mutableStateOf(false) }
            val combined = sameSet.map { it to false } + otherSet.map { it to true }
            val visible = if (expanded) combined else combined.take(ORACLE_SECTION_COLLAPSED_COUNT)
            var otherSetHeaderShown = false
            visible.forEach { (entry, isOtherSet) ->
                if (isOtherSet && !otherSetHeaderShown) {
                    Text(
                        text = stringResource(R.string.carddetail_variants_header),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = MaterialTheme.magicColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    otherSetHeaderShown = true
                }
                TradeCopyRow(
                    entry = entry,
                    tradeQuantity = tradeQuantities[entry.userCard.id] ?: 0,
                    onEdit = onEditEntry,
                    onRequestDelete = onRequestDelete,
                )
            }
            if (combined.size > ORACLE_SECTION_COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.carddetail_show_less)
                        } else {
                            stringResource(R.string.carddetail_show_all, combined.size)
                        },
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = MaterialTheme.magicColors.primaryAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCopyRow(
    entry: UserCardWithCard,
    tradeQuantity: Int,
    onEdit: (UserCardWithCard) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val userCard = entry.userCard
    val printing = entry.card
    val availableQty = (userCard.quantity - tradeQuantity).coerceAtLeast(0)
    val preferredCurrency = LocalPreferredCurrency.current
    val priceText = PriceFormatter.formatFromScryfall(
        priceUsd = if (userCard.isFoil) printing.priceUsdFoil else printing.priceUsd,
        priceEur = if (userCard.isFoil) printing.priceEurFoil else printing.priceEur,
        preferredCurrency = preferredCurrency,
    )

    Surface(
        color = mc.surface,
        shape = SmallCardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(62.dp)
                    .clip(SmallCardShape)
                    .border(BorderStroke(0.5.dp, mc.surfaceVariant.copy(alpha = 0.8f)), SmallCardShape)
            ) {
                AsyncImage(
                    model = printing.imageNormal,
                    contentDescription = printing.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Row 1 (Top): Set symbol + Set name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SetSymbol(
                        setCode = printing.setCode,
                        rarity = CardRarity.fromString(printing.rarity),
                        size = 14.dp,
                    )
                    Text(
                        text = printing.setName,
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                // Row 2 (Middle): Left: Badges. Right: Edit & Delete buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        LanguageBadge(langCode = userCard.language)
                        CopyBadge(label = userCard.condition)
                        if (userCard.isFoil) FoilBadge()
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { onEdit(entry) },
                            shape = CircleShape,
                            color = mc.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.carddetail_edit_entry_description),
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Surface(
                            onClick = { onRequestDelete(userCard) },
                            shape = CircleShape,
                            color = mc.lifeNegative.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = mc.lifeNegative,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Row 3 (Bottom): Quantity pill + Price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "x$availableQty",
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                    )
                    if (priceText != "—") {
                        Text(
                            text = priceText,
                            style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = mc.goldMtg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeCopyRow(
    entry: UserCardWithCard,
    tradeQuantity: Int,
    onEdit: (UserCardWithCard) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val userCard = entry.userCard
    val printing = entry.card
    val preferredCurrency = LocalPreferredCurrency.current
    val priceText = PriceFormatter.formatFromScryfall(
        priceUsd = if (userCard.isFoil) printing.priceUsdFoil else printing.priceUsd,
        priceEur = if (userCard.isFoil) printing.priceEurFoil else printing.priceEur,
        preferredCurrency = preferredCurrency,
    )

    Surface(
        color = mc.surface,
        shape = SmallCardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(62.dp)
                    .clip(SmallCardShape)
                    .border(BorderStroke(0.5.dp, mc.surfaceVariant.copy(alpha = 0.8f)), SmallCardShape)
            ) {
                AsyncImage(
                    model = printing.imageNormal,
                    contentDescription = printing.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Row 1 (Top): Set symbol + Set name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SetSymbol(
                        setCode = printing.setCode,
                        rarity = CardRarity.fromString(printing.rarity),
                        size = 14.dp,
                    )
                    Text(
                        text = printing.setName,
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                // Row 2 (Middle): Left: Badges. Right: Edit & Delete buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        LanguageBadge(langCode = userCard.language)
                        CopyBadge(label = userCard.condition)
                        if (userCard.isFoil) FoilBadge()
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { onEdit(entry) },
                            shape = CircleShape,
                            color = mc.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.carddetail_edit_entry_description),
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Surface(
                            onClick = { onRequestDelete(userCard) },
                            shape = CircleShape,
                            color = mc.lifeNegative.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = mc.lifeNegative,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Row 3 (Bottom): Quantity pill + Price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "x$tradeQuantity",
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                    )
                    if (priceText != "—") {
                        Text(
                            text = priceText,
                            style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = mc.goldMtg,
                        )
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Wishlist section: add button + list of existing entries. Card Versions & Languages, Phase 1B —
//  same oracle-wide / displayed-set-first grouping as the collection section above.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WishlistSection(
    entries: List<WishlistEntry>,
    displayedSetCode: String,
    onEditEntry: (WishlistEntry) -> Unit,
    onRequestDelete: (WishlistEntry) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.carddetail_no_wishlist_copies),
                style = MaterialTheme.magicTypography.bodySmall,
                color = MaterialTheme.magicColors.textSecondary,
            )
        } else {
            val (sameSet, otherSet) = entries.partition { it.card?.setCode == displayedSetCode }
            var expanded by remember { mutableStateOf(false) }
            val combined = sameSet.map { it to false } + otherSet.map { it to true }
            val visible = if (expanded) combined else combined.take(ORACLE_SECTION_COLLAPSED_COUNT)
            var otherSetHeaderShown = false
            visible.forEach { (entry, isOtherSet) ->
                if (isOtherSet && !otherSetHeaderShown) {
                    Text(
                        text = stringResource(R.string.carddetail_variants_header),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = MaterialTheme.magicColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    otherSetHeaderShown = true
                }
                WishlistEntryRow(
                    entry = entry,
                    onEdit = onEditEntry,
                    onRequestDelete = onRequestDelete,
                )
            }
            if (combined.size > ORACLE_SECTION_COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.carddetail_show_less)
                        } else {
                            stringResource(R.string.carddetail_show_all, combined.size)
                        },
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = MaterialTheme.magicColors.primaryAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistEntryRow(
    entry: WishlistEntry,
    onEdit: (WishlistEntry) -> Unit,
    onRequestDelete: (WishlistEntry) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val printing = entry.card
    val preferredCurrency = LocalPreferredCurrency.current
    val priceText = printing?.let {
        PriceFormatter.formatFromScryfall(
            priceUsd = if (entry.isFoil) it.priceUsdFoil else it.priceUsd,
            priceEur = if (entry.isFoil) it.priceEurFoil else it.priceEur,
            preferredCurrency = preferredCurrency,
        )
    }

    Surface(
        color = mc.surface,
        shape = SmallCardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(62.dp)
                    .clip(SmallCardShape)
                    .border(BorderStroke(0.5.dp, mc.surfaceVariant.copy(alpha = 0.8f)), SmallCardShape)
            ) {
                AsyncImage(
                    model = printing?.imageNormal,
                    contentDescription = printing?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (printing != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SetSymbol(
                            setCode = printing.setCode,
                            rarity = CardRarity.fromString(printing.rarity),
                            size = 14.dp,
                        )
                        Text(
                            text = printing.setName,
                            style = ty.labelSmall,
                            color = mc.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }

                // Badges row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        LanguageBadge(langCode = entry.language ?: printing?.lang ?: "en")
                        CopyBadge(label = entry.condition ?: "")
                        if (entry.isFoil) FoilBadge()
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { onEdit(entry) },
                            shape = CircleShape,
                            color = mc.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.carddetail_edit_entry_description),
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Surface(
                            onClick = { onRequestDelete(entry) },
                            shape = CircleShape,
                            color = mc.lifeNegative.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = mc.lifeNegative,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Row 3 (Bottom): Quantity pill + Price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "x${entry.quantity}",
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (priceText != null && priceText != "—") {
                        Text(
                            text = priceText,
                            style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = mc.goldMtg,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun PriceSection(card: Card) {
    val preferredCurrency = LocalPreferredCurrency.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.carddetail_market_prices),
            style = MaterialTheme.magicTypography.labelMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PricePill(
                label = stringResource(R.string.carddetail_price_normal),
                price = if (preferredCurrency == PreferredCurrency.EUR) card.priceEur else card.priceUsd,
                currency = preferredCurrency
            )
            PricePill(
                label = stringResource(R.string.carddetail_price_foil),
                price = if (preferredCurrency == PreferredCurrency.EUR) card.priceEurFoil else card.priceUsdFoil,
                currency = preferredCurrency
            )
        }
    }
}

@Composable
private fun PricePill(label: String, price: Double?, currency: PreferredCurrency) {
    val mc = MaterialTheme.magicColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textSecondary
        )
        Text(
            text = PriceFormatter.format(price, currency),
            style = MaterialTheme.magicTypography.bodyLarge,
            color = mc.goldMtg,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegalitySection(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.carddetail_legality),
            style = MaterialTheme.magicTypography.labelMedium
        )
        val formats = listOf(
            stringResource(R.string.format_standard) to card.legalityStandard,
            stringResource(R.string.format_pioneer) to card.legalityPioneer,
            stringResource(R.string.format_modern) to card.legalityModern,
            stringResource(R.string.format_legacy) to card.legalityLegacy,
            stringResource(R.string.format_vintage) to card.legalityVintage,
            stringResource(R.string.format_pauper) to card.legalityPauper,
            stringResource(R.string.format_commander) to card.legalityCommander,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            formats.forEach { (format, legality) ->
                LegalityChip(format = format, legality = legality)
            }
        }
    }
}

@Composable
private fun LegalityChip(format: String, legality: String) {
    val mc = MaterialTheme.magicColors
    // Scryfall legality values: legal / restricted (1 copy, e.g. Vintage) / banned / not_legal.
    val (statusText, accent) = when (legality) {
        "legal" -> stringResource(R.string.carddetail_legality_legal) to mc.lifePositive
        "restricted" -> stringResource(R.string.carddetail_legality_restricted) to mc.goldMtg
        "banned" -> stringResource(R.string.carddetail_legality_banned) to mc.lifeNegative
        else -> stringResource(R.string.carddetail_legality_not_legal) to null
    }
    Surface(
        color = accent?.copy(alpha = 0.15f) ?: mc.surfaceVariant,
        shape = ChipShape,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = format,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textPrimary,
                maxLines = 1
            )
            Text(
                text = statusText,
                style = MaterialTheme.magicTypography.labelSmall,
                color = accent ?: mc.textSecondary,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tags section
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    autoTags: List<CardTag>,
    userTags: List<CardTag>,
    isInCollection: Boolean,
    onRemoveUserTag: (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val hasAnyTag = autoTags.isNotEmpty() || (isInCollection && userTags.isNotEmpty())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (autoTags.isNotEmpty()) {
            Text(
                text = stringResource(R.string.carddetail_tags_auto_label),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
            CardTagGroup(
                tags = autoTags,
                tagLabel = { tag -> tag.label() }
            )
        }

        if (isInCollection) {
            if (autoTags.isNotEmpty() || userTags.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = stringResource(R.string.carddetail_tags_user_label),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
            if (userTags.isNotEmpty()) {
                CardTagGroup(
                    tags = userTags,
                    tagLabel = { tag -> tag.label() },
                    onTagRemove = { tag -> onRemoveUserTag(tag) },
                    removeContentDescription = { tag ->
                        stringResource(R.string.carddetail_tags_remove_description, tag.label())
                    }
                )
            } else {
                Text(
                    text = stringResource(R.string.carddetail_tags_user_empty),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }

            Spacer(Modifier.height(4.dp))
            MagicCtaButton(
                onClick = onShowTagPicker,
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Primary,
                size = MagicCtaSize.Normal,
                text = stringResource(R.string.carddetail_tags_add_button),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!hasAnyTag) {
            Text(
                text = stringResource(R.string.carddetail_tags_auto_empty),
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tag picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

private data class ExternalLink(
    val label: String,
    val url: String,
    val iconUrl: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExternalLinksSection(card: Card, onOpenFailed: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    // No browser / blocked intent throws ActivityNotFoundException from openUri — report instead of crashing.
    val openLink: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }.onFailure {
            FirebaseCrashlytics.getInstance().log("card_detail_external_link_open_failed")
            onOpenFailed()
        }
    }

    val referenceLinks = buildList {
        // Cache rows that predate the column can carry a blank URI.
        if (card.scryfallUri.isNotBlank()) {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_scryfall),
                url = card.scryfallUri,
                iconUrl = "https://www.google.com/s2/favicons?domain=scryfall.com&sz=64"
            ))
        }
        card.relatedUris["gatherer"]?.let {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_gatherer),
                url = it,
                iconUrl = "https://www.google.com/s2/favicons?domain=wizards.com&sz=64"
            ))
        }
        card.relatedUris["edhrec"]?.let {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_edhrec),
                url = it,
                iconUrl = "https://www.google.com/s2/favicons?domain=edhrec.com&sz=64"
            ))
        }
    }

    val communityLinks = buildList {
        card.relatedUris["tcgplayer_infinite_articles"]?.let {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_tcgplayer_articles),
                url = it,
                iconUrl = "https://www.google.com/s2/favicons?domain=tcgplayer.com&sz=64"
            ))
        }
        card.relatedUris["tcgplayer_infinite_decks"]?.let {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_tcgplayer_decks),
                url = it,
                iconUrl = "https://www.google.com/s2/favicons?domain=tcgplayer.com&sz=64"
            ))
        }
    }

    val purchaseLinks = buildList {
        card.purchaseUris["tcgplayer"]?.let {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_tcgplayer),
                url = it,
                iconUrl = "https://www.google.com/s2/favicons?domain=tcgplayer.com&sz=64"
            ))
        }
        card.purchaseUris["cardmarket"]?.let {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_cardmarket),
                url = it,
                iconUrl = "https://www.google.com/s2/favicons?domain=cardmarket.com&sz=64"
            ))
        }
        card.purchaseUris["cardhoarder"]?.let {
            add(ExternalLink(
                label = stringResource(R.string.carddetail_links_cardhoarder),
                url = it,
                iconUrl = "https://www.google.com/s2/favicons?domain=cardhoarder.com&sz=64"
            ))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (referenceLinks.isNotEmpty()) {
            LinkCategory(
                title = stringResource(R.string.carddetail_links_references),
                icon = Icons.Default.MenuBook,
                links = referenceLinks,
                onOpen = openLink,
            )
        }

        if (communityLinks.isNotEmpty()) {
            LinkCategory(
                title = stringResource(R.string.carddetail_links_community),
                icon = Icons.Default.Group,
                links = communityLinks,
                onOpen = openLink,
            )
        }

        if (purchaseLinks.isNotEmpty()) {
            LinkCategory(
                title = stringResource(R.string.carddetail_links_purchase),
                icon = Icons.Default.ShoppingCart,
                links = purchaseLinks,
                onOpen = openLink,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkCategory(
    title: String,
    icon: ImageVector,
    links: List<ExternalLink>,
    onOpen: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = ty.labelLarge,
                color = mc.textSecondary,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            links.forEach { link ->
                LinkChip(link = link, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun LinkChip(link: ExternalLink, onOpen: (String) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = { onOpen(link.url) },
        color = mc.surface,
        shape = ChipShape,
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = mc.primaryAccent.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
                if (link.iconUrl != null) {
                    AsyncImage(
                        model = link.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = link.label,
                style = ty.labelMedium,
                color = mc.textPrimary,
            )
        }
    }
}
