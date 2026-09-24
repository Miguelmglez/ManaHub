package com.mmg.manahub.feature.today.presentation.events

import androidx.annotation.StringRes
import com.mmg.manahub.R

/** Constructed formats of the metagame links; [mtgTop8Code]/[mtgDecksSlug] are those sites' URL segments. */
enum class MetagameFormat(val id: String, @StringRes val labelRes: Int, val mtgTop8Code: String, val mtgDecksSlug: String) {
    STANDARD("standard", R.string.today_format_standard, "ST", "Standard"),
    PIONEER("pioneer", R.string.today_format_pioneer, "PI", "Pioneer"),
    MODERN("modern", R.string.today_format_modern, "MO", "Modern"),
    LEGACY("legacy", R.string.today_format_legacy, "LE", "Legacy"),
    VINTAGE("vintage", R.string.today_format_vintage, "VI", "Vintage"),
    PAUPER("pauper", R.string.today_format_pauper, "PAU", "Pauper"),
    ;

    companion object {
        fun fromId(id: String?): MetagameFormat = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** A Metagame & decklists deep link; [formatScoped] links use (and describe) the selected format. */
data class MetagameLink(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val formatScoped: Boolean,
    val urlFor: (MetagameFormat) -> String,
)

/** Editorial catalog opened in the browser, never fetched: no runtime liveness checks, fix URLs here if a site moves. */
object MetagameLinkCatalog {

    val links: List<MetagameLink> = listOf(
        MetagameLink(
            id = "mtgo_decklists",
            titleRes = R.string.today_link_mtgo_title,
            descriptionRes = R.string.today_link_mtgo_desc,
            formatScoped = false,
            urlFor = { "https://www.mtgo.com/decklists" },
        ),
        MetagameLink(
            id = "magicgg_decklists",
            titleRes = R.string.today_link_magicgg_decklists_title,
            descriptionRes = R.string.today_link_magicgg_decklists_desc,
            formatScoped = false,
            urlFor = { "https://magic.gg/decklists" },
        ),
        MetagameLink(
            id = "magicgg_standings",
            titleRes = R.string.today_link_magicgg_standings_title,
            descriptionRes = R.string.today_link_magicgg_standings_desc,
            formatScoped = false,
            urlFor = { "https://magic.gg/standings" },
        ),
        MetagameLink(
            id = "mtgtop8_format",
            titleRes = R.string.today_link_mtgtop8_title,
            descriptionRes = R.string.today_link_mtgtop8_desc,
            formatScoped = true,
            urlFor = { format -> "https://www.mtgtop8.com/format?f=${format.mtgTop8Code}" },
        ),
        MetagameLink(
            id = "mtggoldfish_metagame",
            titleRes = R.string.today_link_goldfish_meta_title,
            descriptionRes = R.string.today_link_goldfish_meta_desc,
            formatScoped = true,
            urlFor = { format -> "https://www.mtggoldfish.com/metagame/${format.id}" },
        ),
        MetagameLink(
            id = "mtgdecks_format",
            titleRes = R.string.today_link_mtgdecks_title,
            descriptionRes = R.string.today_link_mtgdecks_desc,
            formatScoped = true,
            urlFor = { format -> "https://mtgdecks.net/${format.mtgDecksSlug}" },
        ),
        MetagameLink(
            id = "mtggoldfish_price_movers",
            titleRes = R.string.today_link_goldfish_movers_title,
            descriptionRes = R.string.today_link_goldfish_movers_desc,
            formatScoped = false,
            urlFor = { "https://www.mtggoldfish.com/index" },
        ),
    )

    const val TWITCH_URL = "https://www.twitch.tv/magic"
    const val MAGIC_GG_NEWS_URL = "https://magic.gg/news"

    fun scryfallSetUrl(setCode: String): String = "https://scryfall.com/sets/${setCode.lowercase()}"

    /** 17lands keys ratings by the uppercase set code. */
    fun seventeenLandsRatingsUrl(setCode: String): String =
        "https://www.17lands.com/card_ratings?expansion=${setCode.uppercase()}&format=PremierDraft"

    fun eventLocatorUrl(encodedPostalCode: String): String =
        "https://locator.wizards.com/search?searchType=magic-events&query=$encodedPostalCode&distance=10&unit=km&page=1"
}
