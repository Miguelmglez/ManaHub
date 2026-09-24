package com.mmg.manahub.feature.decks.domain.inspirations

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.AxisKey
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.SynergyEngineState
import com.mmg.manahub.feature.decks.domain.engine.SynergyGraph
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One owned card on one side of a collection synergy. [ownedCopies] is summed by name across printings. */
data class SynergyMember(
    val card: Card,
    val ownedCopies: Int,
    val confidence: Float,
)

/** A collection engine keyed by the Analysis tab's own [AxisKey]; [isProducerOnly] axes (MILL_OPP, LOCK) have no payoff side. */
data class CollectionSynergyEngine(
    val axis: AxisKey,
    val producers: List<SynergyMember>,
    val payoffs: List<SynergyMember>,
    val state: SynergyEngineState,
    val isProducerOnly: Boolean,
    val buildability: Float,
) {
    val producersSectionId: String get() = "engine:$axis:producers"
    val payoffsSectionId: String get() = "engine:$axis:payoffs"

    fun contains(cardName: String): Boolean =
        producers.any { it.card.name == cardName } || payoffs.any { it.card.name == cardName }
}

/** [cards] follows the `tribe:<x>` section membership (payoffs first); [memberCount] counts own-subtype cards only. */
data class CollectionTribeSynergy(
    val tribeKey: String,
    val cards: List<SynergyMember>,
    val memberCount: Int,
    val payoffCount: Int,
) {
    val sectionId: String get() = tribeKey
    val subtype: String get() = tribeKey.removePrefix(TribeDeriver.TRIBE_PREFIX)

    fun contains(cardName: String): Boolean = cards.any { it.card.name == cardName }
}

data class CollectionSynergies(
    val engines: List<CollectionSynergyEngine>,
    val tribes: List<CollectionTribeSynergy>,
) {
    val isEmpty: Boolean get() = engines.isEmpty() && tribes.isEmpty()

    fun containing(cardName: String): CollectionSynergies = CollectionSynergies(
        engines = engines.filter { it.contains(cardName) },
        tribes = tribes.filter { it.contains(cardName) },
    )

    companion object {
        val EMPTY = CollectionSynergies(emptyList(), emptyList())
    }
}

/** Every engine and tribe a collection supports for a 60-card format, via the same per-card axis profile and D3 rule the Synergy section uses. */
class DiscoverCollectionSynergiesUseCase(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend operator fun invoke(collection: List<UserCardWithCard>, format: DeckFormat): CollectionSynergies =
        withContext(dispatcher) { discover(collection, format) }

    internal fun discover(collection: List<UserCardWithCard>, format: DeckFormat): CollectionSynergies {
        val ownedByName = collection
            .filter { !BasicLandCalculator.isBasicLand(it.card) && isLegalForFormat(it.card, format) }
            .groupBy { it.card.name }
        if (ownedByName.isEmpty()) return CollectionSynergies.EMPTY

        val pool = ownedByName.map { (_, rows) ->
            val representative = rows.maxBy { it.userCard.quantity }.card
            representative to rows.sumOf { it.userCard.quantity }.coerceAtLeast(1)
        }

        val producersByAxis = mutableMapOf<AxisKey, MutableList<SynergyMember>>()
        val payoffsByAxis = mutableMapOf<AxisKey, MutableList<SynergyMember>>()
        val tribeCards = mutableMapOf<String, MutableList<SynergyMember>>()
        val tribeMembers = mutableMapOf<String, Int>()
        val tribePayoffNames = mutableMapOf<String, MutableSet<String>>()

        pool.forEach { (card, owned) ->
            val profile = SynergyGraph.cardAxisProfile(card, ArchetypeFormat.SIXTY)
            profile.produces.forEach { (axis, confidence) ->
                if (confidence > 0f && !axis.startsWith(TRIBE_AXIS)) producersByAxis.getOrPut(axis) { mutableListOf() } += SynergyMember(card, owned, confidence)
            }
            profile.consumes.forEach { (axis, confidence) ->
                if (confidence > 0f && !axis.startsWith(TRIBE_AXIS)) payoffsByAxis.getOrPut(axis) { mutableListOf() } += SynergyMember(card, owned, confidence)
            }
            val subtypes = TribeDeriver.subtypeKeys(card)
            val payoffTribes = TribeDeriver.payoffTribeKeys(card)
            (subtypes + payoffTribes).forEach { key -> tribeCards.getOrPut(key) { mutableListOf() } += SynergyMember(card, owned, 1f) }
            subtypes.forEach { key -> tribeMembers[key] = (tribeMembers[key] ?: 0) + 1 }
            payoffTribes.forEach { key -> tribePayoffNames.getOrPut(key) { mutableSetOf() } += card.name }
        }

        val ideals = SynergyGraph.axisIdeals(ArchetypeFormat.SIXTY)
        // Clamped like the engine's own placement, so 30 copies of a bulk common count as the 4 a deck can run.
        fun List<SynergyMember>.ruleCopies(): Int = sumOf { CopyPolicy.maxPlaceable(it.card, format, it.ownedCopies) }

        val engines = (producersByAxis.keys + payoffsByAxis.keys)
            .mapNotNull { axis ->
                val ideal = ideals[axis] ?: return@mapNotNull null
                val producers = producersByAxis[axis].orEmpty().sortedForDisplay()
                val isProducerOnly = axis in PAYOFF_OPTIONAL_AXES
                val payoffs = if (isProducerOnly) emptyList() else payoffsByAxis[axis].orEmpty().sortedForDisplay()
                val producerCopies = producers.ruleCopies()
                val payoffCopies = payoffs.ruleCopies()
                val state = if (isProducerOnly) {
                    SynergyEngineState.COMPLETE.takeIf { producerCopies >= 1 && producerCopies * 2 >= ideal.producerIdeal }
                } else {
                    SynergyEngineState.resolve(producerCopies, payoffCopies, ideal.producerIdeal, ideal.payoffIdeal)
                } ?: return@mapNotNull null
                val producerRatio = producerCopies.toFloat() / ideal.producerIdeal
                val payoffRatio = payoffCopies.toFloat() / ideal.payoffIdeal
                CollectionSynergyEngine(
                    axis = axis,
                    producers = producers,
                    payoffs = payoffs,
                    state = state,
                    isProducerOnly = isProducerOnly,
                    buildability = (if (isProducerOnly) producerRatio else minOf(producerRatio, payoffRatio)).coerceAtMost(1f),
                )
            }
            .sortedWith(
                compareBy<CollectionSynergyEngine> { it.state != SynergyEngineState.COMPLETE }
                    .thenByDescending { it.buildability }
                    .thenByDescending { it.producers.size + it.payoffs.size }
                    .thenBy { it.axis },
            )

        val tribes = tribeCards
            .filter { (key, _) -> (tribeMembers[key] ?: 0) >= MIN_TRIBE_MEMBERS }
            .map { (key, cards) ->
                val payoffNames = tribePayoffNames[key].orEmpty()
                CollectionTribeSynergy(
                    tribeKey = key,
                    cards = cards.sortedForDisplay().sortedByDescending { it.card.name in payoffNames },
                    memberCount = tribeMembers[key] ?: 0,
                    payoffCount = payoffNames.size,
                )
            }
            .sortedWith(
                compareByDescending<CollectionTribeSynergy> { it.memberCount }
                    .thenByDescending { it.payoffCount }
                    .thenBy { it.tribeKey },
            )

        return CollectionSynergies(engines = engines, tribes = tribes)
    }

    private fun List<SynergyMember>.sortedForDisplay(): List<SynergyMember> = sortedWith(
        compareByDescending<SynergyMember> { it.confidence }
            .thenByDescending { rarityRank(it.card.rarity) }
            .thenBy { it.card.name },
    )

    private fun rarityRank(rarity: String): Int = when (rarity.lowercase()) {
        "mythic" -> 4
        "rare" -> 3
        "uncommon" -> 2
        else -> 1
    }

    companion object {
        /** Distinct owned cards carrying the subtype on their own type line. */
        const val MIN_TRIBE_MEMBERS = 8

        // AnalysisEngine.evaluateSynergy excludes these from engines: they have no payoff role.
        internal val PAYOFF_OPTIONAL_AXES = setOf("MILL_OPP", "LOCK")

        private const val TRIBE_AXIS = "TRIBE"
    }
}
