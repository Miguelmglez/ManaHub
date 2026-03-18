package feature.carddetail


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import core.domain.model.Card
import core.domain.model.UserCard
import core.ui.components.FoilBadge
import core.ui.components.RarityDot
import core.ui.components.StaleBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    onBack:    () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.card?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.userCard?.let {
                        IconButton(onClick = viewModel::onShowEditDialog) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = viewModel::onShowDeleteConfirm) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.card != null -> CardDetailContent(
                card     = uiState.card!!,
                userCard = uiState.userCard,
                isStale  = uiState.isStale,
                modifier = Modifier.padding(padding),
            )
        }
    }

    // Edit bottom sheet
    if (uiState.showEditDialog) {
        uiState.userCard?.let { uc ->
            EditCardSheet(
                userCard       = uc,
                onDismiss      = viewModel::onDismissEditDialog,
                onUpdateQty    = { viewModel.onUpdateQuantity(uc.id, it) },
                onUpdateCond   = { viewModel.onUpdateCondition(uc, it) },
            )
        }
    }

    // Delete confirmation
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDeleteConfirm,
            title   = { Text("Remove card") },
            text    = { Text("Remove ${uiState.card?.name} from your collection?") },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteCard(uiState.userCard!!.id) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDeleteConfirm) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CardDetailContent(
    card:     Card,
    userCard: UserCard?,
    isStale:  Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showBackFace by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card image — tap to flip for DFC
        Box(
            modifier          = Modifier.fillMaxWidth(),
            contentAlignment  = Alignment.Center,
        ) {
            val imageUrl = if (showBackFace && card.imageBackNormal != null)
                card.imageBackNormal else card.imageNormal

            AsyncImage(
                model              = imageUrl,
                contentDescription = card.name,
                contentScale       = ContentScale.FillWidth,
                modifier           = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(MaterialTheme.shapes.medium)
                    .then(
                        if (card.imageBackNormal != null)
                            Modifier.clickable { showBackFace = !showBackFace }
                        else Modifier
                    ),
            )

            // DFC flip hint
            if (card.imageBackNormal != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape    = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text     = if (showBackFace) "Tap to see front" else "Tap to flip",
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // Name + badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RarityDot(rarity = card.rarity)
            Text(card.name, style = MaterialTheme.typography.headlineSmall)
            if (userCard?.isFoil == true) FoilBadge()
            if (isStale) StaleBadge()
        }

        // Mana cost + type
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            card.manaCost?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(card.typeLine, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Oracle text
        card.oracleText?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text     = it,
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Flavor text
        card.flavorText?.let {
            Text(
                text      = "\"$it\"",
                style     = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Power/Toughness or Loyalty
        if (card.power != null && card.toughness != null) {
            Text(
                text  = "${card.power}/${card.toughness}",
                style = MaterialTheme.typography.titleMedium,
            )
        } else card.loyalty?.let {
            Text("Loyalty: $it", style = MaterialTheme.typography.titleMedium)
        }

        HorizontalDivider()

        // Prices
        PriceSection(card = card, userCard = userCard)

        HorizontalDivider()

        // Collection info
        userCard?.let { uc ->
            CollectionInfoSection(userCard = uc)
        }

        // Legalities
        LegalitySection(card = card)

        // Scryfall link
        TextButton(onClick = { uriHandler.openUri(card.scryfallUri) }) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("View on Scryfall")
        }
    }
}

@Composable
private fun PriceSection(card: Card, userCard: UserCard?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Market prices", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PricePill(label = "Regular", price = card.priceUsd, currency = "$")
            PricePill(label = "Foil",    price = card.priceUsdFoil, currency = "$")
            PricePill(label = "EUR",     price = card.priceEur, currency = "€")
        }
    }
}

@Composable
private fun PricePill(label: String, price: Double?, currency: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text  = if (price != null) "$currency${String.format("%.2f", price)}" else "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun CollectionInfoSection(userCard: UserCard) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("In your collection", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoPill(label = "Quantity",  value = "×${userCard.quantity}")
            InfoPill(label = "Condition", value = userCard.condition)
            InfoPill(label = "Language",  value = userCard.language.uppercase())
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LegalitySection(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Legality", style = MaterialTheme.typography.titleSmall)
        val formats = listOf(
            "Standard"  to card.legalityStandard,
            "Pioneer"   to card.legalityPioneer,
            "Modern"    to card.legalityModern,
            "Commander" to card.legalityCommander,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            formats.forEach { (format, legality) ->
                LegalityChip(format = format, legality = legality)
            }
        }
    }
}

@Composable
private fun LegalityChip(format: String, legality: String) {
    val isLegal = legality == "legal"
    Surface(
        color = if (isLegal) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(format, style = MaterialTheme.typography.labelSmall)
            Text(
                text  = if (isLegal) "Legal" else "Not legal",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLegal) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCardSheet(
    userCard:     UserCard,
    onDismiss:    () -> Unit,
    onUpdateQty:  (Int) -> Unit,
    onUpdateCond: (String) -> Unit,
) {
    val conditions = listOf("NM", "LP", "MP", "HP", "DMG")
    var qty by remember { mutableIntStateOf(userCard.quantity) }
    var cond by remember { mutableStateOf(userCard.condition) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Edit card", style = MaterialTheme.typography.titleMedium)

            // Quantity stepper
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Quantity", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (qty > 1) qty-- }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text("$qty", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { qty++ }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }

            // Condition selector
            Text("Condition", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = c == cond,
                        onClick  = { cond = c },
                        label    = { Text(c) },
                    )
                }
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    onUpdateQty(qty)
                    onUpdateCond(cond)
                    onDismiss()
                }) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}