package com.mmg.manahub.tools.tagpipeline.pipeline

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.edhrec.harvestCardSignals
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.feature.decks.domain.engine.EDHREC_SLUG_TO_ARCHETYPE_ID
import com.mmg.manahub.feature.decks.domain.engine.EDHREC_SLUG_TO_THEME_ID
import com.mmg.manahub.tools.tagpipeline.edhrec.EdhrecThemeClient
import com.mmg.manahub.tools.tagpipeline.engine.CardTagEngine
import com.mmg.manahub.tools.tagpipeline.mapping.mapTaggerSlugsToCardTags
import com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow
import com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagSuggestion
import com.mmg.manahub.tools.tagpipeline.model.buildCardStrategyTagsRow
import com.mmg.manahub.tools.tagpipeline.scryfall.OracleTagDto
import com.mmg.manahub.tools.tagpipeline.scryfall.buildOracleTagIndex
import java.security.MessageDigest

/**
 * Bump whenever [com.mmg.manahub.tools.tagpipeline.engine.CardTagEngine]'s inputs change in a way
 * that could change a card's tags — i.e. `TagDictionary`'s base entries, `TribeDeriver`'s logic, or
 * either curated mapping table in `mapping/`. This is what makes `--since` ([SinceWatermark])
 * meaningful: a version bump forces every card to be reprocessed on the next run, even ones already
 * present in the watermark manifest.
 */
const val PIPELINE_VERSION: Int = 2

/**
 * Orchestrates the full pipeline (plan §5 Phase 5a steps 2, 3, 4, 5, 6): rule-engine tags + curated
 * Tagger-tag mapping + EDHREC theme harvesting, merged per `oracle_id` into [CardStrategyTagsRow]s.
 *
 * Pure with respect to I/O beyond what's injected: [buildThemeIndexByCardName] is the only method
 * that performs network I/O (via [EdhrecThemeClient]); [buildRows] takes already-fetched data as
 * plain in-memory structures, so it is directly unit-testable against fixtures with zero network or
 * disk access (see `TagPipelineSmokeTest`).
 */
class TagPipeline(private val cardTagEngine: CardTagEngine = CardTagEngine()) {

    /**
     * Step 4/5: fetches every mapped EDHREC theme page and returns `card name -> (ThemeId.name ->
     * weight)`. EDHREC only ever gives card NAMES (never `oracle_id`) — see [buildRows] for how the
     * name is resolved back onto an `oracle_id` via the same in-memory index the current run's
     * `oracle_cards` pass already built. A theme this pipeline has no mapping for, or whose fetch
     * fails, simply contributes nothing (plan D6 fallibility contract — never aborts the run).
     */
    fun buildThemeIndexByCardName(edhrecClient: EdhrecThemeClient): Map<String, Map<String, Float>> =
        buildEdhrecIndexByCardName(edhrecClient, EDHREC_SLUG_TO_THEME_ID) { it.name }

    /**
     * Archetype counterpart of [buildThemeIndexByCardName] (added 2026-07-21 — see
     * [com.mmg.manahub.feature.decks.domain.engine.EDHREC_SLUG_TO_ARCHETYPE_ID]'s KDoc for why the
     * `archetypes` field was hardcoded empty before this). Same endpoint, same page shape, same
     * fallibility contract — only the curated slug table differs.
     */
    fun buildArchetypeIndexByCardName(edhrecClient: EdhrecThemeClient): Map<String, Map<String, Float>> =
        buildEdhrecIndexByCardName(edhrecClient, EDHREC_SLUG_TO_ARCHETYPE_ID) { it.name }

    /**
     * Shared implementation behind [buildThemeIndexByCardName]/[buildArchetypeIndexByCardName]: for
     * every (slug, id) in [slugToId], fetches the EDHREC tag page and harvests its card signals,
     * keeping the HIGHEST weight per (card name, [idKey] result) pair across every mapped slug.
     */
    private fun <T> buildEdhrecIndexByCardName(
        edhrecClient: EdhrecThemeClient,
        slugToId: Map<String, T>,
        idKey: (T) -> String,
    ): Map<String, Map<String, Float>> {
        val result = HashMap<String, MutableMap<String, Float>>()
        slugToId.forEach { (slug, id) ->
            val page = edhrecClient.fetchThemePageOrNull(slug)
                ?: error("EDHREC source unavailable for $slug; refusing incomplete publication")
            val key = idKey(id)
            harvestCardSignals(page).forEach { signal ->
                val byId = result.getOrPut(signal.cardName) { mutableMapOf() }
                val existing = byId[key]
                if (existing == null || signal.weight > existing) byId[key] = signal.weight
            }
        }
        return result
    }

    /**
     * Step 2/3/6: the main per-card merge. [oracleTagRows] is the (possibly empty, per plan D6/D5
     * fallibility) stream of Scryfall Oracle Tags; [themeIndexByCardName] is
     * [buildThemeIndexByCardName]'s output; [since] applies the `--since` filter when present.
     *
     * Lazy end-to-end (a `Sequence`, not a `List`) — the caller decides whether to drain it fully
     * (a production run) or `.take(n)` it (the smoke-test slice), and the whole pipeline never
     * materializes more than one card's intermediate state at a time.
     */
    fun buildRows(
        cardDtos: Sequence<CardDto>,
        oracleTagRows: Sequence<OracleTagDto>,
        themeIndexByCardName: Map<String, Map<String, Float>>,
        generatedAt: String,
        pipelineVersion: Int = PIPELINE_VERSION,
        since: SinceFilter? = null,
        /** `oracle_id -> CardTag key`, from [com.mmg.manahub.tools.tagpipeline.archidekt
         *  .resolveDominantCategories]. Empty by default (Archidekt enrichment is optional/
         *  best-effort — see `archidekt/ArchidektCategoryMapping.kt`'s KDoc for why). */
        archidektCategoryByOracleId: Map<String, String> = emptyMap(),
        /** `card name -> (ArchetypeId.name -> weight)`, from [buildArchetypeIndexByCardName]. Empty
         *  by default (added 2026-07-21 alongside [buildArchetypeIndexByCardName] — see
         *  [com.mmg.manahub.feature.decks.domain.engine.EDHREC_SLUG_TO_ARCHETYPE_ID]'s KDoc). */
        archetypeIndexByCardName: Map<String, Map<String, Float>> = emptyMap(),
        requireOracleTags: Boolean = false,
    ): Sequence<CardStrategyTagsRow> {
        val oracleTagIndex = buildOracleTagIndex(oracleTagRows)
        check(!requireOracleTags || oracleTagIndex.isNotEmpty()) {
            "oracle_tags source decoded no card associations; refusing incomplete publication"
        }

        return cardDtos.mapNotNull { dto ->
            val card = dto.toDomain()
            val oracleId = card.oracleId.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val taggerSlugs = oracleTagIndex[oracleId].orEmpty()
            val taggerTags = mapTaggerSlugsToCardTags(taggerSlugs)

            val themes = themeIndexByCardName[card.name].orEmpty()
            val archetypes = archetypeIndexByCardName[card.name].orEmpty()

            val archidektCategory = archidektCategoryByOracleId[oracleId]
            val inputFingerprint = fingerprintOf(
                card.name,
                card.typeLine,
                card.oracleText.orEmpty(),
                card.keywords.sorted().joinToString(","),
                taggerSlugs.sorted().joinToString(","),
                themes.toSortedMap().toString(),
                archetypes.toSortedMap().toString(),
                archidektCategory.orEmpty(),
                pipelineVersion.toString(),
            )
            if (since != null && !SinceWatermark.shouldReprocess(
                    oracleId, pipelineVersion, since.manifest, inputFingerprint,
                    since.fingerprints, since.requireFingerprint,
                )) return@mapNotNull null

            val analyzedTags = cardTagEngine.analyze(card)
            val ruleEngineTags = analyzedTags.confirmed.map { it.key }.toSet()
            val suggestions = analyzedTags.suggested.map {
                CardStrategyTagSuggestion(it.tag.key, it.confidence)
            }
            val tribes = cardTagEngine.tribeWords(card)

            val sources = buildSet {
                add("rule_engine")
                if (taggerSlugs.isNotEmpty()) add("oracle_tags")
                if (themes.isNotEmpty() || archetypes.isNotEmpty()) add("edhrec")
                if (archidektCategory != null) add("archidekt")
            }

            buildCardStrategyTagsRow(
                oracleId = oracleId,
                tags = ruleEngineTags + taggerTags + setOfNotNull(archidektCategory),
                suggestions = suggestions.filterNot { it.key in taggerTags || it.key == archidektCategory },
                inputFingerprint = inputFingerprint,
                tribes = tribes,
                themes = themes,
                archetypes = archetypes,
                sources = sources,
                archidektCategory = archidektCategory,
                generatedAt = generatedAt,
                pipelineVersion = pipelineVersion,
            )
        }
    }
}

private fun fingerprintOf(vararg parts: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(0.toByte())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Bundles the loaded `--since` manifest so [TagPipeline.buildRows] takes one nullable param
 *  instead of two (a manifest map + a "is --since even active" flag). */
data class SinceFilter(
    val manifest: Map<String, Int>,
    val fingerprints: Map<String, String> = emptyMap(),
    val requireFingerprint: Boolean = false,
)
