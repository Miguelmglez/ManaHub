package com.mmg.manahub.feature.carddetail.presentation


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import org.jetbrains.compose.resources.painterResource
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.mtg_card_back
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.UserDefinedTag
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.CardTagChip
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.CopyBadge
import com.mmg.manahub.core.ui.components.FoilBadge
import com.mmg.manahub.core.ui.components.FullScreenImageViewer
import com.mmg.manahub.core.ui.components.LanguageBadge
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.StaleBadge
import com.mmg.manahub.core.ui.components.TradeSelectionSheet
import com.mmg.manahub.core.ui.components.VariantSelectorSheet
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.ui.theme.Spacing
import com.mmg.manahub.core.ui.theme.spacing

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

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            contentWindowInsets = WindowInsets(0),
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
                        CardName(
                            name = uiState.card?.printedName ?: uiState.card?.name ?: "",
                            style = MaterialTheme.magicTypography.titleLarge,
                            modifier = Modifier
                                .weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    isStale = uiState.isStale,
                    onRemoveAutoTag = viewModel::onRemoveTag,
                    onAddUserTag = viewModel::onAddUserTag,
                    onRemoveUserTag = viewModel::onRemoveUserTag,
                    onShowTagPicker = viewModel::onShowTagPicker,
                    onConfirmSuggestedTag = viewModel::onConfirmSuggestedTag,
                    onDismissSuggestedTag = viewModel::onDismissSuggestedTag,
                    onShowAddSheet = viewModel::onShowAddSheet,
                    onShowWishlistSheet = viewModel::onShowWishlistSheet,
                    onShowTradeSheet = viewModel::onShowTradeSheet,
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
                    modifier = Modifier.padding(padding),
                )
            }
        }

        // Toast overlay — sits above the Scaffold
        MagicToastHost(state = toastState)

    } // end Box

    // Tag picker sheet
    if (uiState.showTagPicker) {
        TagPickerSheet(
            cardAutoTags = uiState.card?.tags ?: emptyList(),
            cardSuggestedTags = uiState.card?.suggestedTags ?: emptyList(),
            currentUserTags = uiState.card?.userTags ?: emptyList(),
            userDefinedTags = uiState.userDefinedTags,
            onAddUserTag = viewModel::onAddUserTag,
            onSaveAndAddCustomTag = viewModel::onSaveAndAddCustomTag,
            onDeleteUserDefinedTag = viewModel::onDeleteUserDefinedTag,
            onUpdateUserDefinedTag = viewModel::onUpdateUserDefinedTag,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardDetailContent(
    card: Card,
    userCards: List<UserCardWithCard>,
    wishlistEntries: List<WishlistEntry>,
    tradeQuantities: Map<String, Int>,
    decksContainingCard: List<Deck>,
    isStale: Boolean,
    onRemoveAutoTag: (CardTag) -> Unit,
    onAddUserTag: (CardTag) -> Unit,
    onRemoveUserTag: (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
    onConfirmSuggestedTag: (CardTag) -> Unit,
    onDismissSuggestedTag: (CardTag) -> Unit,
    onShowAddSheet: () -> Unit,
    onShowWishlistSheet: () -> Unit,
    onShowTradeSheet: () -> Unit,
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

    // Staggered animation for content
    val staggeredEnter = remember {
        slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(400))
    }

    // High-quality curve for the shared element bounds
    val sharedBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
        tween(durationMillis = 400, easing = FastOutSlowInEasing)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                        // Force hardware layer during transition to prevent "snapping"
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .clip(CardShape)
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(
                                        key = sharedTransitionKey ?: "card-image-${card.scryfallId}"
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(CardShape),
                                    boundsTransform = sharedBoundsTransform,
                                    renderInOverlayDuringTransition = true,
                                )
                            }
                        } else Modifier
                    )
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
                Text(
                    text = if (showBackFace)
                        stringResource(R.string.carddetail_flip_see_front)
                    else
                        stringResource(R.string.carddetail_flip_see_back),
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = MaterialTheme.magicColors.primaryAccent,
                )
            }
        }

        // Animated Content Wrapper for staggering
        if (animatedVisibilityScope != null) {
            with(animatedVisibilityScope) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Name + badges
                    FaceFlippable(
                        rotation = rotation,
                        modifier = Modifier.animateEnterExit(
                            enter = staggeredEnter,
                            exit = fadeOut()
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
                            CardName(name, style = MaterialTheme.magicTypography.titleLarge)
                            if (isStale) StaleBadge()
                        }
                    }

                    // Mana cost + type
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.animateEnterExit(
                            enter = slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(500, delayMillis = 100)
                            ) + fadeIn(tween(400, delayMillis = 100)),
                            exit = fadeOut()
                        )
                    ) {


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
                        FaceFlippable(rotation = rotation) { isBack ->
                            val typeText = if (isBack) {
                                backFace?.typeLine ?: card.typeLine
                            } else {
                                frontFace?.typeLine ?: card.printedTypeLine.takeUnless { it.isNullOrEmpty() } ?: card.typeLine
                            }
                            Text(
                                text = typeText,
                                style = MaterialTheme.magicTypography.bodyMedium,
                                color = MaterialTheme.magicColors.textSecondary,
                            )
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
                            Text(
                                text = card.setName,
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = MaterialTheme.magicColors.textSecondary,
                            )
                        }
                    }

                    // Oracle / printed text
                    FaceFlippable(
                        rotation = rotation,
                        modifier = Modifier.animateEnterExit(
                            enter = slideInVertically(
                                initialOffsetY = { it / 3 },
                                animationSpec = tween(600, delayMillis = 200)
                            ) + fadeIn(tween(500, delayMillis = 200)),
                            exit = fadeOut()
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
                                OracleText(
                                    text = oracleDisplayText,
                                    style = MaterialTheme.magicTypography.bodyMedium,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }

                    // Flavor text
                    FaceFlippable(rotation = rotation) { isBack ->
                        val flavorText = if (isBack) backFace?.flavorText else frontFace?.flavorText ?: card.flavorText
                        flavorText?.let {
                            Text(
                                text = "\"$it\"",
                                style = MaterialTheme.magicTypography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.magicColors.textSecondary,
                            )
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
                        modifier = Modifier.animateEnterExit(
                            enter = slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(700, delayMillis = 300)
                            ) + fadeIn(tween(600, delayMillis = 300)),
                            exit = fadeOut()
                        )
                    ) {
                        PriceSection(card = card)
                        HorizontalDivider()
                        // Improved Variants & Prints Section
                        Surface(
                            onClick = onShowVariantSelector,
                            color = MaterialTheme.magicColors.primaryAccent.copy(alpha = 0.08f),
                            shape = CardShape,
                            border = BorderStroke(1.dp, MaterialTheme.magicColors.primaryAccent.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Icon with a soft circular highlight
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            MaterialTheme.magicColors.primaryAccent.copy(alpha = 0.15f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
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
                        HorizontalDivider()

                        CollectionSection(
                            userCards = userCards,
                            displayedSetCode = card.setCode,
                            tradeQuantities = tradeQuantities,
                            onShowAddSheet = onShowAddSheet,
                            onShowTradeSheet = onShowTradeSheet,
                            onEditEntry = onEditCollectionEntry,
                            onRequestDelete = onRequestDelete,
                        )
                    }
                }
            }
        } else {
            // Fallback for cases without scope
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Name + badges
                FaceFlippable(rotation = rotation) { isBack ->
                    val name = if (isBack) {
                        backFace?.printedName ?: card.printedName ?: card.name
                    } else {
                        frontFace?.printedName ?: card.printedName ?: card.name
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CardName(name, style = MaterialTheme.magicTypography.titleLarge)
                        if (isStale) StaleBadge()
                    }
                }
                // Mana cost + type
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.manaCost?.let {
                        ManaCostImages(manaCost = it, symbolSize = 20.dp)
                    }
                    FaceFlippable(rotation = rotation) { isBack ->
                        val typeText = if (isBack) {
                            backFace?.typeLine ?: card.typeLine
                        } else {
                            frontFace?.typeLine ?: card.printedTypeLine.takeUnless { it.isNullOrEmpty() } ?: card.typeLine
                        }
                        Text(
                            text = typeText,
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = MaterialTheme.magicColors.textSecondary,
                        )
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
                        Text(
                            text = card.setName,
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = MaterialTheme.magicColors.textSecondary,
                        )
                    }
                }
                // Oracle
                FaceFlippable(rotation = rotation) { isBack ->
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
                            OracleText(
                                text = oracleDisplayText,
                                style = MaterialTheme.magicTypography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                PriceSection(card = card)
                HorizontalDivider()
                CollectionSection(
                    userCards = userCards,
                    displayedSetCode = card.setCode,
                    tradeQuantities = tradeQuantities,
                    onShowAddSheet = onShowAddSheet,
                    onShowTradeSheet = onShowTradeSheet,
                    onEditEntry = onEditCollectionEntry,
                    onRequestDelete = onRequestDelete,
                )
            }
        }

        // Common sections that don't need staggering or are too far down
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider()

            // Wishlist section
            WishlistSection(
                entries = wishlistEntries,
                displayedSetCode = card.setCode,
                onShowWishlistSheet = onShowWishlistSheet,
                onEditEntry = onEditWishlistEntry,
                onRequestDelete = onRequestDeleteWishlist,
            )

            HorizontalDivider()

            // Legalities
            LegalitySection(card = card)

            HorizontalDivider()

            // Tags
            TagsSection(
                autoTags = card.tags,
                userTags = card.userTags,
                isInCollection = userCards.isNotEmpty(),
                onRemoveAutoTag = onRemoveAutoTag,
                onRemoveUserTag = onRemoveUserTag,
                onShowTagPicker = onShowTagPicker,
            )

            // Suggested tags — only visible when card is in the user's collection
            if (card.suggestedTags.isNotEmpty() && userCards.isNotEmpty()) {
                SuggestedTagsSection(
                    suggestions = card.suggestedTags,
                    onConfirm = onConfirmSuggestedTag,
                    onDismiss = onDismissSuggestedTag,
                )
            }

            // Found in decks section
            if (decksContainingCard.isNotEmpty()) {
                HorizontalDivider()
                FoundInDecksSection(
                    decks = decksContainingCard,
                    onNavigateToDeck = onNavigateToDeck,
                )
            }

            // Community Decks entry point
            HorizontalDivider()
            run {
                val mc = MaterialTheme.magicColors
                OutlinedButton(
                    onClick = { onFindCommunityDecks(card.name) },
                    shape = ButtonShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.community_deck_find_decks),
                        style = MaterialTheme.magicTypography.labelLarge,
                    )
                }
            }

            HorizontalDivider()

            // External links — References, Community, Where to Buy
            ExternalLinksSection(card = card)

            // Extra bottom padding for FAB
            Spacer(Modifier.height(72.dp))
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Found in Decks section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FoundInDecksSection(
    decks: List<Deck>,
    onNavigateToDeck: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(
                    R.string.carddetail_found_in_decks,
                    decks.size,
                    if (decks.size == 1) stringResource(R.string.carddetail_deck) else stringResource(
                        R.string.carddetail_decks
                    )
                ),
                style = MaterialTheme.magicTypography.labelMedium,
                color = mc.textPrimary,
            )
        }

        decks.forEach { deck ->
            DeckChip(deck = deck, onClick = { onNavigateToDeck(deck.id) })
        }
    }
}

@Composable
private fun DeckChip(deck: Deck, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        color = mc.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deck.name,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Surface(
                    color = mc.primaryAccent.copy(alpha = 0.12f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = deck.format.replaceFirstChar { it.uppercase() },
                        style = ty.labelSmall,
                        color = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = mc.textDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collection section: add button + list of existing copies. Card Versions & Languages, Phase 1B —
//  now ORACLE-WIDE (every printing/language the user owns), grouped displayed-set-first with a
//  "Variants" subheader for copies from other sets.
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
    onShowAddSheet: () -> Unit,
    onShowTradeSheet: () -> Unit,
    onEditEntry: (UserCardWithCard) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        // Header: title + Add button

        Text(
            stringResource(R.string.carddetail_in_collection),
            style = MaterialTheme.magicTypography.labelMedium,
            color = MaterialTheme.magicColors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onShowAddSheet,
                border = BorderStroke(1.dp, MaterialTheme.magicColors.primaryAccent),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.magicColors.primaryAccent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.carddetail_add_copy),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = MaterialTheme.magicColors.primaryAccent
                )
            }
        }
    }


    if (userCards.isEmpty()) {
        Text(
            text = stringResource(R.string.carddetail_no_copies),
            style = MaterialTheme.magicTypography.bodySmall,
            color = MaterialTheme.magicColors.textSecondary,
        )
    } else {
        val (sameSet, otherSet) = userCards.partition { it.card.setCode == displayedSetCode }
        // A5: same-set entries first, then cross-set — capped at ORACLE_SECTION_COLLAPSED_COUNT
        // rows by default, with a "Show all (N)" / "Show less" toggle.
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

        // "Offer for trade" button — only shown when there are collection copies
        OutlinedButton(
            onClick = onShowTradeSheet,
            border = BorderStroke(1.dp, MaterialTheme.magicColors.secondaryAccent),
            modifier = Modifier.height(32.dp),
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.magicColors.secondaryAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.carddetail_offer_for_trade),
                style = MaterialTheme.magicTypography.labelSmall,
                color = MaterialTheme.magicColors.secondaryAccent
            )
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
    val preferredCurrency = LocalPreferredCurrency.current
    val priceText = PriceFormatter.formatFromScryfall(
        priceUsd = if (userCard.isFoil) printing.priceUsdFoil else printing.priceUsd,
        priceEur = if (userCard.isFoil) printing.priceEurFoil else printing.priceEur,
        preferredCurrency = preferredCurrency,
    )

    Surface(
        color = mc.surface,
        shape = CardShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = printing.imageNormal,
                contentDescription = printing.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(44.dp)
                    .height(60.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Set icon + set name for this specific printing
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    )
                }

                // Badges row
                Row(
                    modifier = Modifier.heightIn(min = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguageBadge(langCode = userCard.language)
                    CopyBadge(label = userCard.condition)
                    if (userCard.isFoil) FoilBadge()
                    if (tradeQuantity > 0) {
                        Surface(
                            color = mc.secondaryAccent.copy(alpha = 0.15f),
                            shape = ChipShape,
                        ) {
                            Text(
                                text = stringResource(R.string.carddetail_for_trade_badge),
                                style = ty.labelSmall,
                                color = mc.secondaryAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Static quantity + per-printing price (steppers removed — edit via the Edit button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "x${userCard.quantity}",
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

            IconButton(
                onClick = { onEdit(entry) },
                // 40dp keeps the row compact while staying above the minimum touch target.
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.carddetail_edit_entry_description),
                    tint = mc.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { onRequestDelete(userCard) },
                // 40dp keeps the row compact while staying above the minimum touch target.
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = mc.lifeNegative,
                    modifier = Modifier.size(18.dp),
                )
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
    onShowWishlistSheet: () -> Unit,
    onEditEntry: (WishlistEntry) -> Unit,
    onRequestDelete: (WishlistEntry) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            stringResource(R.string.carddetail_in_wishlist),
            style = MaterialTheme.magicTypography.labelMedium,
            color = MaterialTheme.magicColors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = onShowWishlistSheet,
            border = BorderStroke(1.dp, MaterialTheme.magicColors.goldMtg),
            modifier = Modifier.height(32.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.magicColors.goldMtg
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.carddetail_add_to_wishlist),
                style = MaterialTheme.magicTypography.labelSmall,
                color = MaterialTheme.magicColors.goldMtg
            )
        }
    }

    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.carddetail_no_wishlist_copies),
            style = MaterialTheme.magicTypography.bodySmall,
            color = MaterialTheme.magicColors.textSecondary,
        )
    } else {
        val (sameSet, otherSet) = entries.partition { it.card?.setCode == displayedSetCode }
        // A5: same as CollectionSection above — capped at ORACLE_SECTION_COLLAPSED_COUNT rows
        // with a "Show all (N)" / "Show less" toggle.
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
        shape = CardShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = printing?.imageNormal,
                contentDescription = printing?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(44.dp)
                    .height(60.dp)
                    .clip(CardShape),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (printing != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        )
                    }
                }

                // Badges row
                Row(
                    modifier = Modifier.heightIn(min = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguageBadge(langCode = entry.language ?: printing?.lang ?: "en")
                    CopyBadge(label = entry.condition ?: "")
                    if (entry.isFoil) FoilBadge()
                }

                // Static quantity + per-printing price (steppers removed — edit via the Edit button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "x${entry.quantity}",
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                    )
                    if (priceText != null && priceText != "—") {
                        Text(
                            text = priceText,
                            style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = mc.goldMtg,
                        )
                    }
                }
            }

            IconButton(
                onClick = { onEdit(entry) },
                // 40dp keeps the row compact while staying above the minimum touch target.
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.carddetail_edit_entry_description),
                    tint = mc.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { onRequestDelete(entry) },
                // 40dp keeps the row compact while staying above the minimum touch target.
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = mc.lifeNegative,
                    modifier = Modifier.size(18.dp),
                )
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
    val isLegal = legality == "legal"
    Surface(
        color = if (isLegal) mc.lifePositive.copy(alpha = 0.15f) else mc.surfaceVariant,
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
                text = if (isLegal) stringResource(R.string.carddetail_legality_legal)
                       else stringResource(R.string.carddetail_legality_not_legal),
                style = MaterialTheme.magicTypography.labelSmall,
                color = if (isLegal) mc.lifePositive else mc.textSecondary,
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
    onRemoveAutoTag: (CardTag) -> Unit,
    onRemoveUserTag: (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val hasAnyTag = autoTags.isNotEmpty() || (isInCollection && userTags.isNotEmpty())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.carddetail_tags_section),
                style = MaterialTheme.magicTypography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        val mc = MaterialTheme.magicColors
        if (expanded) {
            // ── Auto-generated tags ──────────────────────────────────────────
            if (autoTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.carddetail_tags_auto_label),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    autoTags.forEach { tag ->
                        // Decorative-only (no onClick) — both the collected and non-collected
                        // renderings were previously no-op-clickable InputChip/SuggestionChip.
                        CardTagChip(label = tag.label(), category = tag.category)
                    }
                }
            }

            // ── User tags (only when in collection) ──────────────────────────
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        userTags.forEach { tag ->
                            CardTagChip(
                                label = tag.label(),
                                category = tag.category,
                                onRemove = { onRemoveUserTag(tag) },
                                removeContentDescription = stringResource(
                                    R.string.carddetail_tags_remove_description,
                                    tag.label()
                                ),
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.carddetail_tags_user_empty),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onShowTagPicker,
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.carddetail_tags_add_button),
                        style = MaterialTheme.magicTypography.labelSmall
                    )
                }
            } else if (!hasAnyTag) {
                Text(
                    text = stringResource(R.string.carddetail_tags_auto_empty),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Suggested tags section — full-width cards with proper tap targets
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuggestedTagsSection(
    suggestions: List<SuggestedTag>,
    onConfirm: (CardTag) -> Unit,
    onDismiss: (CardTag) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Accent dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.carddetail_tags_suggested_label),
                style = MaterialTheme.magicTypography.labelMedium,
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = mc.textDisabled,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.carddetail_tags_suggested_desc),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
                suggestions.forEach { sug ->
                    SuggestedTagCard(
                        suggestion = sug,
                        onConfirm = { onConfirm(sug.tag) },
                        onDismiss = { onDismiss(sug.tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedTagCard(
    suggestion: SuggestedTag,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val pct = (suggestion.confidence * 100).toInt()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        border = BorderStroke(0.5.dp, mc.primaryAccent.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Tag name + confidence bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Small tag icon dot
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(mc.primaryAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Label,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(suggestion.tag.label(), style = ty.bodyMedium, color = mc.textPrimary)
                    Text(
                        text = stringResource(R.string.carddetail_tags_confidence_value, pct),
                        style = ty.labelSmall,
                        color = mc.textDisabled
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action buttons — large enough to tap comfortably
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.lifeNegative),
                    border = BorderStroke(0.8.dp, mc.lifeNegative.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_discard), style = ty.labelSmall)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.lifePositive.copy(alpha = 0.15f),
                        contentColor = mc.lifePositive,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.carddetail_tags_suggested_confirm),
                        style = ty.labelSmall
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tag picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

private data class TagItem(
    val key: String,
    val label: String,
    val category: TagCategory,
    val isUserDefined: Boolean = false,
    val isApplied: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSheet(
    cardAutoTags: List<CardTag>,
    cardSuggestedTags: List<SuggestedTag>,
    currentUserTags: List<CardTag>,
    userDefinedTags: List<UserDefinedTag>,
    onAddUserTag: (CardTag) -> Unit,
    onSaveAndAddCustomTag: (label: String, categoryKey: String) -> Unit,
    onDeleteUserDefinedTag: (key: String) -> Unit,
    onUpdateUserDefinedTag: (key: String, newLabel: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val userTagKeys = currentUserTags.map { it.key }.toSet()

    // Built-in categories (excluding CUSTOM which is the fallback for raw custom tags)
    val builtInCategories = TagCategory.entries.filter { it != TagCategory.CUSTOM }

    // User-created category keys that don't map to built-in categories
    val userCustomCategoryKeys = userDefinedTags
        .map { it.categoryKey }
        .filter { key -> builtInCategories.none { it.name == key } }
        .distinct()

    // ── Custom tag creator state ──────────────────────────────────────────────
    var customLabel by remember { mutableStateOf("") }
    var selectedCategoryKey by remember { mutableStateOf(TagCategory.STRATEGY.name) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    // ── Edit user-defined tag state ───────────────────────────────────────────
    var editingTagKey by remember { mutableStateOf<String?>(null) }
    var editingTagLabel by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = MaterialTheme.magicColors.textSecondary
                        )
                    }
                    Text(
                        stringResource(R.string.carddetail_tags_picker_title),
                        style = MaterialTheme.magicTypography.titleMedium
                    )
                }
            }

            // ── Custom tag creator ───────────────────────────────────────────
            item {
                CustomTagCreatorSection(
                    label = customLabel,
                    onLabelChange = { customLabel = it },
                    selectedCategoryKey = selectedCategoryKey,
                    onCategorySelected = { selectedCategoryKey = it },
                    builtInCategories = builtInCategories,
                    userCustomCategoryKeys = userCustomCategoryKeys,
                    onNewCategoryClick = { showNewCategoryDialog = true },
                    onAdd = {
                        if (customLabel.isNotBlank()) {
                            onSaveAndAddCustomTag(customLabel, selectedCategoryKey)
                            customLabel = ""
                            onDismiss()
                        }
                    },
                )
            }

            // ── Auto-generated tags for this card ────────────────────────────
            if (cardAutoTags.isNotEmpty()) {
                item {
                    TagPickerSection(
                        title = stringResource(R.string.carddetail_tags_picker_auto),
                        tags = cardAutoTags.map { TagItem(it.key, it.label(), category = it.category) },
                        onAdd = { key ->
                            val tag = cardAutoTags.find { it.key == key } ?: return@TagPickerSection
                            onAddUserTag(tag); onDismiss()
                        },
                    )
                }
            }

            // ── Suggested tags for this card ─────────────────────────────────
            val availableSuggestions = cardSuggestedTags.filter { it.tag.key !in userTagKeys }
            if (availableSuggestions.isNotEmpty()) {
                item {
                    TagPickerSection(
                        title = stringResource(R.string.carddetail_tags_picker_suggested),
                        tags = availableSuggestions.map { sug ->
                            TagItem(
                                sug.tag.key,
                                "${sug.tag.label()}  ${(sug.confidence * 100).toInt()}%",
                                category = sug.tag.category,
                            )
                        },
                        onAdd = { key ->
                            val tag = availableSuggestions.find { it.tag.key == key }?.tag
                                ?: return@TagPickerSection
                            onAddUserTag(tag); onDismiss()
                        },
                    )
                }
            }

            // ── Built-in categories ──────────────────────────────────────────
            builtInCategories.forEach { category ->
                val canonical =
                    CardTag.canonical.filter { it.category == category && it.key !in userTagKeys }
                val userDefined =
                    userDefinedTags.filter { it.categoryKey == category.name }
                val items = canonical.map {
                    TagItem(it.key, it.label(), category = it.category, isUserDefined = false)
                } + userDefined.map {
                    TagItem(
                        it.key,
                        it.label,
                        category = category,
                        isUserDefined = true,
                        isApplied = it.key in userTagKeys
                    )
                }
                if (items.isNotEmpty()) {
                    item(key = "cat_${category.name}") {
                        TagPickerSection(
                            title = category.name,
                            tags = items,
                            onAdd = { key ->
                                val tag = canonical.find { it.key == key }
                                    ?: CardTag(key, category)
                                onAddUserTag(tag); onDismiss()
                            },
                            onEdit = { key ->
                                val lbl = userDefinedTags.find { it.key == key }?.label ?: key
                                editingTagKey = key
                                editingTagLabel = lbl
                            },
                            onDelete = onDeleteUserDefinedTag,
                        )
                    }
                }
            }

            // ── User custom categories ────────────────────────────────────────
            userCustomCategoryKeys.forEach { categoryKey ->
                val items = userDefinedTags
                    .filter { it.categoryKey == categoryKey }
                    .map {
                        TagItem(
                            it.key,
                            it.label,
                            category = TagCategory.CUSTOM,
                            isUserDefined = true,
                            isApplied = it.key in userTagKeys
                        )
                    }
                if (items.isNotEmpty()) {
                    item(key = "custom_$categoryKey") {
                        TagPickerSection(
                            title = categoryKey,
                            tags = items,
                            onAdd = { key ->
                                onAddUserTag(CardTag(key, TagCategory.CUSTOM)); onDismiss()
                            },
                            onEdit = { key ->
                                val lbl = userDefinedTags.find { it.key == key }?.label ?: key
                                editingTagKey = key
                                editingTagLabel = lbl
                            },
                            onDelete = onDeleteUserDefinedTag,
                        )
                    }
                }
            }
        }
    }

    // ── Edit tag label dialog ─────────────────────────────────────────────────
    editingTagKey?.let { key ->
        MagicAlertDialog(
            onDismissRequest = { editingTagKey = null },
            title = stringResource(R.string.carddetail_rename_tag),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                if (editingTagLabel.isNotBlank()) {
                    onUpdateUserDefinedTag(key, editingTagLabel)
                }
                editingTagKey = null
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { editingTagKey = null },
            content = {
                OutlinedTextField(
                    value = editingTagLabel,
                    onValueChange = { editingTagLabel = it },
                    placeholder = { Text(stringResource(R.string.carddetail_new_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    if (showNewCategoryDialog) {
        NewCategoryDialog(
            onDismiss = { showNewCategoryDialog = false },
            onConfirm = { name ->
                if (name.isNotBlank()) selectedCategoryKey = name.trim()
                showNewCategoryDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTagCreatorSection(
    label: String,
    onLabelChange: (String) -> Unit,
    selectedCategoryKey: String,
    onCategorySelected: (String) -> Unit,
    builtInCategories: List<TagCategory>,
    userCustomCategoryKeys: List<String>,
    onNewCategoryClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val allCategories: List<String> = builtInCategories.map { it.name } + userCustomCategoryKeys

    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.carddetail_new_custom_tag),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
            )

            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.carddetail_tag_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )

            Text(
                stringResource(R.string.carddetail_tag_type_label),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )

            // Horizontally scrollable category chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(allCategories, key = { it }) { cat ->
                    FilterChip(
                        selected = cat == selectedCategoryKey,
                        onClick = { onCategorySelected(cat) },
                        label = { Text(cat, style = MaterialTheme.magicTypography.labelSmall) },
                    )
                }
                item {
                    SuggestionChip(
                        onClick = onNewCategoryClick,
                        label = {
                            Text(
                                stringResource(R.string.carddetail_new_tag_button),
                                style = MaterialTheme.magicTypography.labelSmall
                            )
                        },
                    )
                }
            }

            Button(
                onClick = onAdd,
                enabled = label.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_add))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSection(
    title: String,
    tags: List<TagItem>,
    onAdd: (key: String) -> Unit,
    onEdit: ((key: String) -> Unit)? = null,
    onDelete: ((key: String) -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = ty.labelLarge,
            color = mc.textSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
                if (tag.isUserDefined && (onEdit != null || onDelete != null)) {
                    // User-defined tag: chip + optional "applied" check + edit/delete icons.
                    // The whole chip is tappable-to-add (matching the original label-only
                    // clickable region's intent, just with a larger — CLAUDE.md-preferred —
                    // touch target); the icon buttons still consume their own taps first via
                    // Compose's normal nested-clickable resolution.
                    CardTagChip(
                        label = tag.label,
                        category = tag.category,
                        onClick = if (!tag.isApplied) ({ onAdd(tag.key) }) else null,
                        trailing = {
                            if (tag.isApplied) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = mc.lifePositive,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(13.dp),
                                )
                            }
                            if (onEdit != null) {
                                IconButton(
                                    onClick = { onEdit(tag.key) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(
                                            R.string.carddetail_edit_tag_description,
                                            tag.label
                                        ),
                                        tint = mc.textSecondary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                            if (onDelete != null) {
                                IconButton(
                                    onClick = { onDelete(tag.key) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.carddetail_delete_tag_description,
                                            tag.label
                                        ),
                                        tint = mc.lifeNegative.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        },
                    )
                } else {
                    // Built-in tag: tap to add.
                    CardTagChip(label = tag.label, category = tag.category, onClick = { onAdd(tag.key) })
                }
            }
        }
    }
}

@Composable
private fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.carddetail_new_category_title),
        confirmLabel = stringResource(R.string.carddetail_create_button),
        onConfirm = { onConfirm(name) },
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.carddetail_category_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  External Links section
// ─────────────────────────────────────────────────────────────────────────────

private data class ExternalLink(val label: String, val url: String)

@Composable
private fun ExternalLinksSection(card: Card) {
    val uriHandler = LocalUriHandler.current

    val referenceLinks = buildList {
        add(ExternalLink(stringResource(R.string.carddetail_links_scryfall), card.scryfallUri))
        card.relatedUris["gatherer"]?.let {
            add(ExternalLink(stringResource(R.string.carddetail_links_gatherer), it))
        }
        card.relatedUris["edhrec"]?.let {
            add(ExternalLink(stringResource(R.string.carddetail_links_edhrec), it))
        }
    }

    val communityLinks = buildList {
        card.relatedUris["tcgplayer_infinite_articles"]?.let {
            add(ExternalLink(stringResource(R.string.carddetail_links_tcgplayer_articles), it))
        }
        card.relatedUris["tcgplayer_infinite_decks"]?.let {
            add(ExternalLink(stringResource(R.string.carddetail_links_tcgplayer_decks), it))
        }
    }

    val purchaseLinks = buildList {
        card.purchaseUris["tcgplayer"]?.let {
            add(ExternalLink(stringResource(R.string.carddetail_links_tcgplayer), it))
        }
        card.purchaseUris["cardmarket"]?.let {
            add(ExternalLink(stringResource(R.string.carddetail_links_cardmarket), it))
        }
        card.purchaseUris["cardhoarder"]?.let {
            add(ExternalLink(stringResource(R.string.carddetail_links_cardhoarder), it))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.carddetail_links_title),
            style = MaterialTheme.magicTypography.labelMedium,
        )

        if (referenceLinks.isNotEmpty()) {
            LinkCategory(
                title = stringResource(R.string.carddetail_links_references),
                icon = Icons.Default.MenuBook,
                links = referenceLinks,
                onOpen = { uriHandler.openUri(it) },
            )
        }

        if (communityLinks.isNotEmpty()) {
            LinkCategory(
                title = stringResource(R.string.carddetail_links_community),
                icon = Icons.Default.Group,
                links = communityLinks,
                onOpen = { uriHandler.openUri(it) },
            )
        }

        if (purchaseLinks.isNotEmpty()) {
            LinkCategory(
                title = stringResource(R.string.carddetail_links_purchase),
                icon = Icons.Default.ShoppingCart,
                links = purchaseLinks,
                onOpen = { uriHandler.openUri(it) },
            )
        }
    }
}

@Composable
private fun LinkCategory(
    title: String,
    icon: ImageVector,
    links: List<ExternalLink>,
    onOpen: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = title,
                style = ty.labelLarge,
                color = mc.textSecondary,
            )
        }
        links.forEach { link ->
            LinkRow(link = link, onOpen = onOpen)
        }
    }
}

@Composable
private fun LinkRow(link: ExternalLink, onOpen: (String) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = { onOpen(link.url) },
        color = mc.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = link.label,
                style = ty.bodyMedium,
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = mc.textDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
