package com.mmg.manahub.feature.decks.domain.engine.analysisv3

/**
 * The full calibration corpus (spec §9's original 17 -- 16 positive, archetype-canonical
 * decklists plus fixture #17, the deliberately incoherent negative fixture -- plus 5 more,
 * fixtures #18-22, added by the 60-card-coverage-expansion workstream, 2026-08-26). Ordered
 * fixture-id-ascending; #1-17 exactly mirror the spec's own §9 table so a reviewer can diff that
 * prefix row-for-row, #18-22 are this workstream's own addition (see each fixture file's own KDoc
 * for why it was chosen -- the original 60-card slice was only 3/17 fixtures, all Modern, 0/5
 * macros with a dedicated COMBO or PRISON witness).
 */
object AnalysisV3Fixtures {
    val ALL: List<AnalysisV3Fixture> = listOf(
        fixture01EdgarMarkov(),
        fixture02Meren(),
        fixture03Karlov(),
        fixture04Omnath(),
        fixture05Urza(),
        fixture06Veyran(),
        fixture07Brago(),
        fixture08Chainer(),
        fixture09Rhys(),
        fixture10GrandArbiter(),
        fixture11UwControl(),
        fixture12Atraxa(),
        fixture13Sythis(),
        fixture14MonoRedBurn(),
        fixture15AzoriusControl(),
        fixture16Tron(),
        fixture17NegativeGoodstuff(),
        fixture18Whirza(),
        fixture19DeathAndTaxes(),
        fixture20Merfolk(),
        fixture21Jund(),
        fixture22GrixisControl(),
    )

    /** The archetype-canonical positive fixtures (everything but #17, the negative fixture). */
    val POSITIVE: List<AnalysisV3Fixture> get() = ALL.filter { it.id != 17 }

    /** Fixture #17, the negative "goodstuff pile" fixture. */
    val NEGATIVE: AnalysisV3Fixture get() = ALL.first { it.id == 17 }
}
