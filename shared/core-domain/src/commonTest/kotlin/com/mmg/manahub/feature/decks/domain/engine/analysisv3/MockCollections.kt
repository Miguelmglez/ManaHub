package com.mmg.manahub.feature.decks.domain.engine.analysisv3
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.card

/**
 * Deck Wizard Commander v3 plan (Phase 0, item 0.3) — two mock owned collections, committed
 * (real Kotlin source, no Android/browser dependency — compiles on every KMP target) so the
 * wizard's future builder tests have ground-truth fixtures rather than the real, gitignored
 * `testdata/wizard-harness/` collection export.
 *
 * Both expose a plain [MockCollectionCard] list ("the card pool + quantities") — no engine types
 * beyond [Card]/[DeckEntry], so a future `BuildWizardDeckUseCase` test can feed either straight
 * into a candidate pool without any adaptation layer.
 */
data class MockCollectionCard(val card: Card, val quantity: Int)

/**
 * The owned pool formed by the 4 v3 corpus Commander fixtures (Edgar Markov / Meren / Karlov /
 * Urza — [fixture01EdgarMarkov]/[fixture02Meren]/[fixture03Karlov]/[fixture05Urza]) plus ~150
 * identity-legal distractor cards drawn from the OTHER corpus fixtures ([AnalysisV3Fixtures.ALL]
 * minus those 4). Purpose (plan D-level ground truth): a wizard build against one of these 4
 * commanders + that fixture's own curated strategy must be able to RECONSTRUCT the fixture's own
 * decklist from this pool (every one of its non-land cards is owned at quantity 1, matching
 * Commander's singleton rule) — this is what a future builder correctness test diffs against.
 *
 * Distractors come from every OTHER corpus fixture's own non-land cards (each fixture is itself a
 * real, coherent decklist for ITS OWN commander/identity — "identity-legal" here means each
 * distractor is a legitimate real Magic card from a real deck, not filtered down to any ONE of the
 * 4 target commanders' identity; a real collection legitimately contains off-identity cards too).
 * Deduplicated by [Card.scryfallId] (fixture-authored ids are already namespaced per fixture, so
 * collisions are not expected but guarded anyway) and capped at 150, taken in a DETERMINISTIC
 * (id-sorted) order so this pool never silently changes shape between runs.
 */
object MockCollectionRich {

    /** The 4 target fixtures this pool is built around — exposed so a builder test can pin one and
     * assert against its own [AnalysisV3Fixture.mainboard]/`commanderTags`/expected macro/theme. */
    val targetFixtures: List<AnalysisV3Fixture> = listOf(
        fixture01EdgarMarkov(),
        fixture02Meren(),
        fixture03Karlov(),
        fixture05Urza(),
    )

    private const val DISTRACTOR_CAP = 150

    val ownedCards: List<MockCollectionCard> by lazy {
        val targetIds = targetFixtures.map { it.id }.toSet()
        val targetNonland = targetFixtures.flatMap { fixture -> nonLandEntries(fixture) }
        val distractorNonland = AnalysisV3Fixtures.ALL
            .filter { it.id !in targetIds }
            .flatMap { fixture -> nonLandEntries(fixture) }
            .distinctBy { it.card.scryfallId }
            .sortedBy { it.card.scryfallId }
            .take(DISTRACTOR_CAP)

        (targetNonland + distractorNonland)
            .distinctBy { it.card.scryfallId }
            .map { MockCollectionCard(it.card, it.quantity) }
    }

    /** Every basic land the 4 target fixtures' color identities could need, "unlimited" (a large
     * flat quantity — real collections never run out of basics). */
    val ownedBasics: List<MockCollectionCard> = listOf(
        MockCollectionCard(basicLand("Mountain", "R"), 40),
        MockCollectionCard(basicLand("Plains", "W"), 40),
        MockCollectionCard(basicLand("Swamp", "B"), 40),
        MockCollectionCard(basicLand("Forest", "G"), 40),
        MockCollectionCard(basicLand("Island", "U"), 40),
    )

    val all: List<MockCollectionCard> get() = ownedCards + ownedBasics
}

/**
 * A thin owned collection: one commander, 30 legal (identity-matching) non-land cards, and 8
 * basic lands. Purpose (plan): a collection that must yield HONEST declared gaps — a wizard build
 * against this pool cannot possibly fill a 99-card mainboard, so the build result's gap sections
 * are what a builder correctness test asserts on, not a completed deck.
 *
 * Reuses [fixture01EdgarMarkov]'s commander + its own first 30 real (non-commander) staples —
 * deliberately real cards, not synthetic filler, but a SMALL slice of them, mirroring a genuinely
 * under-built collection rather than a hand-wavy "30 cards" stub.
 */
object MockCollectionThin {

    val commander: Card = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card

    val ownedCards: List<MockCollectionCard> by lazy {
        nonLandEntries(fixture01EdgarMarkov())
            .filter { it.card.scryfallId != commander.scryfallId }
            .take(30)
            .map { MockCollectionCard(it.card, it.quantity) }
    }

    val ownedBasics: List<MockCollectionCard> = listOf(
        MockCollectionCard(basicLand("Mountain", "R"), 3),
        MockCollectionCard(basicLand("Plains", "W"), 3),
        MockCollectionCard(basicLand("Swamp", "B"), 2),
    )

    val all: List<MockCollectionCard> get() = listOf(MockCollectionCard(commander, 1)) + ownedCards + ownedBasics
}

/** [AnalysisV3Fixture.mainboard] minus its own single bulk basic-land closer entry (see
 * [withBasicsCommander]/[withBasicsSixty]) — the real, distinct staples (commander included). */
private fun nonLandEntries(fixture: AnalysisV3Fixture): List<DeckEntry> =
    fixture.mainboard.filterNot { BasicLandCalculator.isLand(it.card) }

private fun basicLand(name: String, symbol: String): Card = card(
    id = "mock-collection-basic-$name",
    name = name,
    typeLine = "Basic Land — $name",
    cmc = 0.0,
    colors = emptyList(),
    colorIdentity = listOf(symbol),
    producedMana = symbol,
)
