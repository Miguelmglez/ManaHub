package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.MagicTheme

@Preview(showBackground = true)
@Composable
fun CommanderBannerPreview() {
    val sampleCommander = Card(
        scryfallId = "1",
        name = "Kenrith, the Returned King",
        printedName = null,
        manaCost = "{4}{W}",
        cmc = 5.0,
        colors = listOf("W"),
        colorIdentity = listOf("W", "U", "B", "R", "G"),
        typeLine = "Legendary Creature — Human Noble",
        printedTypeLine = null,
        oracleText = "...",
        printedText = null,
        keywords = emptyList(),
        power = "5",
        toughness = "5",
        loyalty = null,
        setCode = "eld",
        setName = "Throne of Eldraine",
        collectorNumber = "303",
        rarity = "mythic",
        releasedAt = "2019-10-04",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = 0.0,
        priceUsdFoil = null,
        priceEur = 0.0,
        priceEurFoil = null,
        legalityStandard = "not_legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = "Kieran Yanner",
        scryfallUri = "https://scryfall.com/card/eld/303"
    )

    val noCostCommander = sampleCommander.copy(
        manaCost = null
    )

    MagicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                CommanderBanner(commander = sampleCommander)
                Spacer(Modifier.height(16.dp))
                CommanderBanner(commander = noCostCommander)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CardRowPreview() {
    val sampleCard = Card(
        scryfallId = "1",
        name = "Kenrith, the Returned King",
        printedName = null,
        manaCost = "{4}{W}",
        cmc = 5.0,
        colors = listOf("W", "U", "B", "R", "G"),
        colorIdentity = listOf("W", "U", "B", "R", "G"),
        typeLine = "Legendary Creature — Human Noble",
        printedTypeLine = null,
        oracleText = "...",
        printedText = null,
        keywords = emptyList(),
        power = "5",
        toughness = "5",
        loyalty = null,
        setCode = "eld",
        setName = "Throne of Eldraine",
        collectorNumber = "303",
        rarity = "mythic",
        releasedAt = "2019-10-04",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = 0.0,
        priceUsdFoil = null,
        priceEur = 0.0,
        priceEurFoil = null,
        legalityStandard = "not_legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = "Kieran Yanner",
        scryfallUri = "https://scryfall.com/card/eld/303"
    )

    MagicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardRow(
                    card = sampleCard,
                    isInCollection = true,
                    onClick = {},
                    onRemove = {},
                    quantity = 1
                )
                Spacer(Modifier.height(8.dp))
                CardRow(
                    card = sampleCard.copy(colors = listOf("R")),
                    isInCollection = false,
                    onClick = {},
                    onRemove = {},
                    quantity = 4
                )
                Spacer(Modifier.height(8.dp))
                CardRow(
                    card = sampleCard.copy(colors = emptyList()),
                    isInCollection = false,
                    onClick = {},
                    onRemove = null,
                    selected = true
                )
            }
        }
    }
}
