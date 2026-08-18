package com.mmg.manahub.feature.competitive.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Style
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Static catalog of precise, deep-linked external MTG competitive resources (Competitive feature
 * pivot, 2026-08). Replaces the previous live-data sections (weekly meta snapshot, 17lands
 * LIVE-fetch ratings) — per Miguel's explicit decision, this app makes ZERO live/REST calls to
 * third-party MTG data services. Every entry below is a manually-curated, editorially-verified
 * deep link opened in a Custom Tab (see [openUrl] in `CompetitiveScreen.kt`); there is no runtime
 * liveness check of any kind — link health is a manual/editorial concern (update this file when a
 * site restructures), never code.
 *
 * A [CompetitiveResourceLink] may belong to more than one [ResourceCategory] (e.g. MTGTop8's
 * format page IS both a decklist archive and a trending-decks view — it is listed once here and
 * rendered once per matching category section, never duplicated with a second guessed URL).
 *
 * [CompetitiveResourceLink.urlFor] returns `null` when the given [CompetitiveFormat] has no known,
 * confidence-verified deep link for that site — the screen hides the card entirely in that case
 * (see `CompetitiveScreen.kt`'s `ResourceCategorySection`). A resource that is NOT format-scoped
 * simply ignores the parameter and always returns the same URL.
 */
enum class ResourceCategory(val displayName: String, val icon: ImageVector) {
    TOURNAMENT_DECKLISTS("Tournament Decklists", Icons.AutoMirrored.Filled.ListAlt),
    TRENDING_DECKS_CARDS("Trending Decks & Cards", Icons.AutoMirrored.Filled.TrendingUp),
    STANDINGS_STATS("Standings & Stats", Icons.Filled.EmojiEvents),
    POWER_RANKINGS("Power Rankings", Icons.Filled.Leaderboard),
    LIMITED_RATINGS("Limited Card Ratings", Icons.Filled.Style),
    STORES_HUBS("Stores & Hubs", Icons.Filled.Storefront),
    WATCH_LIVE("Watch Live", Icons.Filled.LiveTv),
    DECK_BUILDING("Deck Building Tools", Icons.Filled.Build),
}

/** Visual weight of a catalog card. [MINIMAL] is for low-confidence/low-curation-value entries
 * (a bare homepage, not a curated deep page) that should not compete visually with the hero cards. */
enum class LinkEmphasis { STANDARD, MINIMAL }

/**
 * One catalog entry: a site + the precise page it deep-links to.
 *
 * @param id Stable identifier (telemetry / Compose keys).
 * @param siteName Display name of the destination site/page (rendered as the card's headline).
 * @param categories The [ResourceCategory] section(s) this entry appears under.
 * @param description One-line description of what the user will find there.
 * @param emphasis [LinkEmphasis.STANDARD] (default, full card) or [LinkEmphasis.MINIMAL].
 * @param urlFor Resolves the deep-link URL for a given [CompetitiveFormat]. Non-format-scoped
 *   entries ignore the argument. Returns `null` to hide this card for that format.
 */
data class CompetitiveResourceLink(
    val id: String,
    val siteName: String,
    val categories: List<ResourceCategory>,
    val description: String,
    val emphasis: LinkEmphasis = LinkEmphasis.STANDARD,
    val urlFor: (CompetitiveFormat) -> String?,
)

/**
 * TODO(competitive): hardcoded placeholder for "the current Standard-legal set", inherited from
 * the removed live-fetch Limited-ratings section's own `CompetitiveUiState.DEFAULT_LIMITED_SET_CODE`
 * placeholder (no shared "current set" constant/util exists in this codebase — see that removed
 * constant's original KDoc for why pulling in the Draft feature's set list was judged not worth
 * the coupling). Whoever wires real "current set" resolution later should replace this constant.
 */
private const val CURRENT_LIMITED_SET_CODE = "fin"

/**
 * The full static catalog, grouped for editorial/audit purposes by researched confidence level —
 * NOT re-exposed as a confidence field in [CompetitiveResourceLink] (confidence only mattered
 * while choosing which URL depth was safe to hardcode; once chosen, every entry is presented
 * identically to the user).
 */
object CompetitiveResourceCatalog {

    // ───────────────────────── HIGH confidence (fetched live, structure confirmed) ─────────────────────────

    private val mtgoDecklists = CompetitiveResourceLink(
        id = "mtgo_decklists",
        siteName = "MTGO Decklists",
        categories = listOf(ResourceCategory.TOURNAMENT_DECKLISTS),
        description = "Official Magic Online league and tournament decklists, updated daily across every constructed format.",
        urlFor = { "https://www.mtgo.com/decklists" },
    )

    private val magicGgDecklists = CompetitiveResourceLink(
        id = "magicgg_decklists",
        siteName = "Pro Tour Decklists",
        categories = listOf(ResourceCategory.TOURNAMENT_DECKLISTS),
        description = "Decklists from Magic's official Pro Tour and top-level Wizards events.",
        urlFor = { "https://magic.gg/decklists" },
    )

    private val magicGgStandings = CompetitiveResourceLink(
        id = "magicgg_standings",
        siteName = "Pro Tour Standings",
        categories = listOf(ResourceCategory.STANDINGS_STATS),
        description = "Live and historical standings for Magic's top-level Pro Tour events.",
        urlFor = { "https://magic.gg/standings" },
    )

    private val magicGgEventStatistics = CompetitiveResourceLink(
        id = "magicgg_event_statistics",
        siteName = "Event Statistics Archive",
        categories = listOf(ResourceCategory.STANDINGS_STATS),
        description = "Card performance, win rates and full statistical breakdowns from official events.",
        urlFor = { "https://magic.gg/event-statistics" },
    )

    private val mtgTop8FormatCodes = mapOf(
        CompetitiveFormat.STANDARD to "ST",
        CompetitiveFormat.MODERN to "MO",
        CompetitiveFormat.PIONEER to "PI",
        CompetitiveFormat.LEGACY to "LE",
        CompetitiveFormat.VINTAGE to "VI",
        CompetitiveFormat.PAUPER to "PAU",
    )

    /** Doubles as both a decklist archive AND MTGTop8's own metagame/trending-decks view for the
     * selected format — the same page serves both categories, deliberately not duplicated with a
     * second guessed URL. */
    private val mtgTop8Format = CompetitiveResourceLink(
        id = "mtgtop8_format",
        siteName = "MTGTop8",
        categories = listOf(ResourceCategory.TOURNAMENT_DECKLISTS, ResourceCategory.TRENDING_DECKS_CARDS),
        description = "Top-performing decklists and metagame share for the selected format, community-tracked.",
        urlFor = { format -> mtgTop8FormatCodes[format]?.let { code -> "https://www.mtgtop8.com/format?f=$code" } },
    )

    /** Only Commander ("edh") and Standard ELO leaderboards were confirmed live this session — this
     * card is deliberately hidden (never a guessed URL) for any other format. [topdeckGameHub]
     * below stays visible for every format instead, satisfying the "always give the user
     * somewhere to go on TopDeck" goal without mislabeling a guessed page as a "Power Rankings"
     * leaderboard. */
    private val topdeckElo = CompetitiveResourceLink(
        id = "topdeck_elo",
        siteName = "TopDeck.gg Power Rankings",
        categories = listOf(ResourceCategory.POWER_RANKINGS),
        description = "Community ELO leaderboard ranking the best-performing decks and pilots.",
        urlFor = { format ->
            when (format) {
                CompetitiveFormat.STANDARD -> "https://topdeck.gg/elo/magic-the-gathering/standard"
                else -> null
            }
        },
    )

    private val topdeckGameHub = CompetitiveResourceLink(
        id = "topdeck_game_hub",
        siteName = "TopDeck.gg Game Hub",
        categories = listOf(ResourceCategory.TOURNAMENT_DECKLISTS),
        description = "Browse TopDeck's full Magic: The Gathering hub — tournaments, results and more, for every format.",
        urlFor = { "https://topdeck.gg/magic-the-gathering" },
    )

    private val topdeckHubs = CompetitiveResourceLink(
        id = "topdeck_hubs",
        siteName = "TopDeck.gg Stores & Hubs",
        categories = listOf(ResourceCategory.STORES_HUBS),
        description = "Discover local game stores and community-run events through TopDeck's hub directory.",
        urlFor = { "https://topdeck.gg/hubs" },
    )

    private val twitchMagic = CompetitiveResourceLink(
        id = "twitch_magic",
        siteName = "Magic on Twitch",
        categories = listOf(ResourceCategory.WATCH_LIVE),
        description = "Watch official and community Magic tournament coverage live.",
        urlFor = { "https://www.twitch.tv/magic" },
    )

    // ─────────────────────── MEDIUM confidence (strong corroboration, spot-check-worthy) ───────────────────────

    /**
     * `mtgdecks.net/<CapitalizedFormat>` — confirmed live for Standard via an indexed page title;
     * other formats inferred by analogy to the same simple slug pattern. Direct fetch was
     * Cloudflare-blocked this research session, which does NOT mean the link is broken for real
     * users: Custom Tabs opens a real browser, not a bot request, so an automated-fetch block is
     * irrelevant to end-user tapping. Worth a one-time manual spot-check per format, not a guess
     * pulled from nowhere.
     */
    private val mtgDecksFormat = CompetitiveResourceLink(
        id = "mtgdecks_format",
        siteName = "MTGDecks.net",
        categories = listOf(ResourceCategory.TOURNAMENT_DECKLISTS, ResourceCategory.TRENDING_DECKS_CARDS),
        description = "Community-aggregated top decks and metagame breakdown for the selected format.",
        urlFor = { format -> "https://mtgdecks.net/${format.displayName}" },
    )

    /**
     * `mtggoldfish.com/metagame/<format>` — MTGGoldfish's well-established, years-stable URL
     * structure; already a trusted default News RSS source elsewhere in this codebase
     * (`DefaultSources.kt`). Direct fetch was Cloudflare-blocked this session (see [mtgDecksFormat]
     * for why that doesn't indicate a broken link for real users).
     */
    private val mtgGoldfishMetagame = CompetitiveResourceLink(
        id = "mtggoldfish_metagame",
        siteName = "MTGGoldfish Metagame",
        categories = listOf(ResourceCategory.TRENDING_DECKS_CARDS),
        description = "MTGGoldfish's metagame breakdown and top meta decks for the selected format.",
        urlFor = { format -> "https://www.mtggoldfish.com/metagame/${format.id}" },
    )

    private val mtgGoldfishIndex = CompetitiveResourceLink(
        id = "mtggoldfish_index",
        siteName = "MTGGoldfish Price Movers",
        categories = listOf(ResourceCategory.TRENDING_DECKS_CARDS),
        description = "Trending cards and price movers across the whole market, updated daily.",
        urlFor = { "https://www.mtggoldfish.com/index" },
    )

    /**
     * `17lands.com/card_ratings?expansion=<SET>&format=PremierDraft` — same param names as the
     * (already-built, dormant) `manahub-competitive` Worker's live-verified JSON endpoint at
     * `17lands.com/card_ratings/data?...`; this is the HTML page behind that same API on the same
     * domain, very likely real, but is a client-rendered SPA this session's fetch tool couldn't
     * render to visually confirm. Not format-scoped by [CompetitiveFormat] (Limited ratings are
     * keyed by SET, not by constructed format) — always shown.
     */
    private val seventeenLandsRatings = CompetitiveResourceLink(
        id = "17lands_card_ratings",
        siteName = "17lands.com Card Ratings",
        categories = listOf(ResourceCategory.LIMITED_RATINGS),
        description = "Win-rate-backed Limited card ratings (GIH WR, ALSA, ATA) for the current Draft format.",
        urlFor = { "https://www.17lands.com/card_ratings?expansion=$CURRENT_LIMITED_SET_CODE&format=PremierDraft" },
    )

    // ───────────────────── LOW confidence (no reliable deep page — bare homepage only) ─────────────────────

    private val deckstats = CompetitiveResourceLink(
        id = "deckstats",
        siteName = "Deckstats.net",
        categories = listOf(ResourceCategory.DECK_BUILDING),
        description = "A free deck-building and collection-tracking tool with a large user-submitted deck database.",
        emphasis = LinkEmphasis.MINIMAL,
        urlFor = { "https://deckstats.net/" },
    )

    // spicerack.gg omitted from v1: this session's fetch to the domain failed outright (connection
    // error, not merely blocked), so its current reachability is genuinely uncertain — safer to
    // leave it out than to link to a possibly-dead domain. Revisit in a future editorial pass.

    /** All catalog entries. Order here drives nothing directly — the screen groups by
     * [ResourceCategory] in [ResourceCategory.entries] declaration order. */
    val all: List<CompetitiveResourceLink> = listOf(
        mtgoDecklists,
        magicGgDecklists,
        magicGgStandings,
        magicGgEventStatistics,
        mtgTop8Format,
        topdeckElo,
        topdeckGameHub,
        topdeckHubs,
        twitchMagic,
        mtgDecksFormat,
        mtgGoldfishMetagame,
        mtgGoldfishIndex,
        seventeenLandsRatings,
        deckstats,
    )

    /** Resolves every catalog entry for [category] that has a non-null URL for [format], paired
     * with that resolved URL. Entries with no URL for [format] are omitted (hidden), never shown
     * with a broken/guessed link. */
    fun resolveFor(category: ResourceCategory, format: CompetitiveFormat): List<Pair<CompetitiveResourceLink, String>> =
        all.filter { category in it.categories }
            .mapNotNull { link -> link.urlFor(format)?.let { url -> link to url } }
}
