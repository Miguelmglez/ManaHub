package com.mmg.manahub.feature.draft.domain.model

/**
 * Per-set draft decision engine, sourced from the Cloudflare Worker
 * (`/draft/{setCode}/engine.json`) and consumed by the archetype-aware bot drafter.
 *
 * Unlike the legacy colour-commitment heuristic, this model is **archetype-based**:
 * each card carries weights toward the set's archetypes (which may be 2, 3 or more
 * colours — e.g. Tarkir's wedges), so the bot can commit to a strategy rather than to
 * a colour pair. A set without an `engine.json` falls back to the heuristic drafter.
 *
 * @property setCode Lowercase set code (e.g. "tdm").
 * @property schemaVersion Schema version of the engine.json shape.
 * @property lastUpdated Content version string (mirrors sets-index `content_versions.engine`).
 * @property params Tunable scoring weights for the bot.
 * @property archetypes The set's draftable archetypes, best-to-worst.
 * @property cards Per-card signals keyed by Scryfall id (archetype weights + role flags).
 */
data class EngineConfig(
    val setCode: String,
    val schemaVersion: Int,
    val lastUpdated: String,
    val params: EngineParams,
    val archetypes: List<EngineArchetype>,
    val cards: Map<String, EngineCardSignals>,
)

/**
 * Tunable scoring weights. Defaults are sane fallbacks so a partial engine.json still works.
 *
 * @property ratingWeight Multiplier on the card's normalised tier rating.
 * @property synergyWeight Multiplier on the seat-commitment · card-archetype-weights dot product.
 * @property opennessWeight Early-pick bonus scaling by archetype openness (how open a lane looks).
 * @property fixingBonus Flat bonus for mana-fixing cards (vital in 3+ colour formats).
 * @property curveWeight Multiplier on the curve-filling nudge.
 * @property commitmentThreshold Total archetype commitment above which the seat is "committed".
 * @property speculationPicks Number of opening picks treated as pure speculation (rating-led).
 */
data class EngineParams(
    val ratingWeight: Float = 1.0f,
    val synergyWeight: Float = 1.2f,
    val opennessWeight: Float = 0.5f,
    val fixingBonus: Float = 0.3f,
    val curveWeight: Float = 0.15f,
    val commitmentThreshold: Float = 6.0f,
    val speculationPicks: Int = 5,
)

/**
 * A draftable archetype. Colours is a list so 3+ colour wedges and 5-colour decks are native.
 *
 * @property id Stable slug (e.g. "abzan-midrange").
 * @property name Display name (e.g. "Abzan Midrange").
 * @property colors Colour letters, e.g. ["W","B","G"].
 * @property tier Relative strength (1 = best); used to seed bot lane preferences.
 * @property opennessBase Base openness multiplier for this lane (stronger lanes are more contested).
 * @property keyCardIds Scryfall ids of the archetype's signpost / payoff cards.
 */
data class EngineArchetype(
    val id: String,
    val name: String,
    val colors: List<String>,
    val tier: Int,
    val opennessBase: Float,
    val keyCardIds: List<String>,
)

/**
 * Per-card decision signals.
 *
 * @property archetypeWeights Map of archetype id → membership weight in [0, 1].
 * @property rating Optional normalised rating override in [0, 1]; when null the bot uses the tier list.
 * @property fixing True for mana-fixing / mana-ramp lands and rocks.
 * @property removal True for targeted removal / board wipes.
 * @property evasion True for flyers / menace / unblockable threats.
 * @property bomb True for game-ending bombs (take-over-the-game cards).
 */
data class EngineCardSignals(
    val archetypeWeights: Map<String, Float>,
    val rating: Float? = null,
    val fixing: Boolean = false,
    val removal: Boolean = false,
    val evasion: Boolean = false,
    val bomb: Boolean = false,
)
