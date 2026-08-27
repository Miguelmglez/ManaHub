package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.BasicLandDistribution
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeData
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CATEGORY_FILL_FIT_FLOOR
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.CommunityAddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.roundToInt

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.3) — the v2 builder. Fills a
 * [DeckWizardSpec] against a [DeckTemplateResolver]-resolved [DeckTemplate], emitting staged
 * [TemplateBuildProgress] (D6: long generation is fine as long as the caller gets legible progress).
 *
 * ## Deck Engine Unification plan (D1) — build = the Doctor's own Motor A/B loop
 * After seeds/commander placement, the fill is a SINGLE loop that repeatedly asks
 * [suggestAddsFromCollectionUseCase] (Motor A — the EXACT same use case the Deck Doctor Suggestions
 * tab runs) for the best-fitting owned candidates against the deck AS BUILT SO FAR, places every
 * returned candidate whose fit clears [CATEGORY_FILL_FIT_FLOOR], recomputes, and repeats until the
 * mainboard target is reached or the collection has nothing left above the floor. This REPLACES the
 * old two-stage "category quota fill (with floor) + no-floor top-up across categories" pipeline
 * (Wizard Quality Campaign B2b/Wave 3) — that pipeline was a SECOND, independent placement engine
 * that could (and did) disagree with the Doctor's own Motor A ranking. There is now exactly ONE
 * placement decision-maker for both the build and the post-build analysis, so a deck this use case
 * produces cannot rank its own placed cards as cut candidates when the Doctor re-evaluates it
 * afterward (see [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator]'s
 * provenance-aware cut exclusion, D4, which turns this structural agreement into a HARD guarantee).
 *
 * Gaps beat weak fills (D3): a card must clear [CATEGORY_FILL_FIT_FLOOR] to be placed — there is no
 * more no-floor top-up. A deck that comes out short reports [TemplateBuildResult.gaps] instead of
 * silently shipping (or padding with) a weak filler card.
 *
 * ## Ownership / collection input (deviation from the Motor A/B `List<Card>` precedent)
 * Takes [UserCardWithCard] (not a plain `List<Card>`) — unlike [SuggestAddsFromCollectionUseCase],
 * this use case needs REAL per-card owned quantity: Motor A's own `suggestedCopies` is a WANT (up to
 * the format's copy limit), not an owned-quantity-aware amount, so every placement here is
 * additionally clamped to `min(suggestedCopies, ownedCopies)` — the build must never claim to place
 * more copies of a card than the user actually owns.
 *
 * ## Zero alphabetical Scryfall searches (fixes root cause 1.2.1 for v2 specifically)
 * Every gap-resolution / basic-land-materialization lookup goes through
 * [CardRepository.getCardByExactName] (the cached, rate-limited `cards/named?exact=` endpoint) —
 * never [CardRepository.searchWithRawQuery]. DFC/split cards are normalized to their front face
 * (`substringBefore(" // ")`) before lookup, mirroring the existing convention in
 * `AddCardSheet`/`CardName`/`TagAnalyzers`.
 *
 * ## Determinism
 * [suggestAddsFromCollectionUseCase] already re-sorts its own output by an EXPLICIT comparator
 * (score desc, name asc, scryfallId asc); the Motor B merge and the final within-batch placement
 * order follow the same explicit comparator — never collection/template iteration order.
 */
class BuildDeckFromTemplateUseCase(
    private val deckTemplateResolver: DeckTemplateResolver,
    private val deckScorer: DeckScorer,
    private val cardRepository: CardRepository,
    private val suggestAddsFromCollectionUseCase: SuggestAddsFromCollectionUseCase,
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
    private val crashReporter: CrashReporter? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Wizard Quality Campaign B3: the SAME seed-tag inference [DeckDoctorOrchestrator] uses over
     * the finished deck, reused here to seed [DeckScorer.fit] as the deck is BUILT so the wizard and
     * the Doctor rank cards against the same basis (see [recomputeProfile]). */
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase = InferDeckIdentityUseCase(),
    // ── Motor B (Deck Engine Unification D1) — appended last, all defaulted/nullable, so no
    // existing positional-arg-free constructor call site needs to change (mirrors
    // DeckDoctorOrchestrator's own Phase-4 "append new optional params at the end" precedent). A
    // `null` dependency or `isCommunityEngineEnabled() == false` is a silent no-op: the build falls
    // back to Motor A alone, byte-identical to today. Fetched ONCE per build (mirrors
    // DeckDoctorOrchestrator.recomputeCommunityInternal's "not on every incremental step" precedent)
    // — never re-queried per loop iteration.
    private val communityAggregateRepository: CommunityAggregateRepository? = null,
    private val suggestAddsFromCommunityUseCase: SuggestAddsFromCommunityUseCase? = null,
    private val isCommunityEngineEnabled: suspend () -> Boolean = { false },
    // ── Scryfall backstop fill (Deck Wizard & Engine Rework plan, Workstream 4.1) — appended last,
    // defaulted/nullable, same "no existing positional-arg-free call site breaks" precedent as Motor
    // B above. A `null` generator or `spec.includeOutsideCollection == false` is a silent no-op: the
    // build stays exactly as it was before this workstream (Motor A/B only, honest gaps otherwise).
    private val candidatePoolGenerator: CandidatePoolGenerator? = null,
) {

    operator fun invoke(spec: DeckWizardSpec, collection: List<UserCardWithCard>): Flow<TemplateBuildProgress> = flow {
        emit(TemplateBuildProgress.Stage(BuildStage.VALIDATING))
        val validationError = validate(spec)
        if (validationError != null) {
            emit(TemplateBuildProgress.Failed(BuildStage.VALIDATING, validationError))
            return@flow
        }

        emit(TemplateBuildProgress.Stage(BuildStage.ANALYZING_COLLECTION))
        val ownedQuantityByName = collection
            .groupBy { it.card.name }
            .mapValues { (_, entries) -> entries.sumOf { it.userCard.quantity } }
        val ownedCards = analyzeCollection(spec, collection.map { it.card }.distinctBy { it.scryfallId })

        emit(TemplateBuildProgress.Stage(BuildStage.FETCHING_COMMUNITY))
        val template = try {
            deckTemplateResolver.resolve(spec)
        } catch (t: Throwable) {
            crashReporter?.recordException(RuntimeException("deck_builder_v2_template_resolve_failed", t))
            emit(TemplateBuildProgress.Failed(BuildStage.FETCHING_COMMUNITY, "Could not resolve a build template."))
            return@flow
        }

        emit(TemplateBuildProgress.Stage(BuildStage.MAPPING_CATEGORIES))
        val seedEntries = spec.seeds.map { seed ->
            DeckEntry(card = seed, quantity = 1, isOwned = ownedQuantityByName.containsKey(seed.name), isSideboard = false)
        }
        val initialProfile = recomputeProfile(spec, template, seedEntries)
        val categories = mapCategories(template, spec, initialProfile)

        val communityOwnedPool = fetchCommunityOwnedCandidates(spec, template, ownedCards)

        emit(TemplateBuildProgress.Stage(BuildStage.FILLING_FROM_COLLECTION))
        val targetMainboardSize = mainboardTargetSize(spec)
        // WS6 (One land engine): provisional seeds-only estimate, purely to size the Motor-A loop's
        // targetNonLand -- routed through the SAME LandTargetResolver the final recompute (below,
        // after the loop) and Deck Studio's calculateLandDeltas both call, instead of the old
        // private computeLandTarget. This estimate never changes after this fix; only its
        // implementation moved.
        val plannedLands = if (spec.fillLands) {
            LandTargetResolver.resolve(
                format = spec.format,
                archetypeSkeleton = resolveArchetypeSkeleton(template, initialProfile),
                profile = initialProfile,
                manaBaseAnalyzer = manaBaseAnalyzer,
            )
        } else {
            0
        }
        val targetNonLand = targetMainboardSize - plannedLands

        val state = FillState()
        state.placedEntries += seedEntries
        seedEntries.forEach { state.usedNames += it.card.name }
        runMotorALoop(
            spec = spec,
            template = template,
            ownedCards = ownedCards,
            communityOwnedPool = communityOwnedPool,
            ownedQuantityByName = ownedQuantityByName,
            targetNonLand = targetNonLand,
            state = state,
        )

        // Workstream 4.1 (F4 fix): the Scryfall backstop -- a LAST-RESORT fill source, only reached
        // once the owned-collection Motor A/B loop above has genuinely run dry. Gated on
        // spec.includeOutsideCollection; a silent no-op (state untouched) when the toggle is off or
        // no CandidatePoolGenerator was injected.
        runScryfallBackstopLoop(spec = spec, template = template, targetNonLand = targetNonLand, state = state)

        emit(TemplateBuildProgress.Stage(BuildStage.RESOLVING_GAPS))
        val gapResult = resolveCommunitySuggestions(categories, state)

        emit(TemplateBuildProgress.Stage(BuildStage.FILLING_LANDS))
        // WS6 (One land engine): the ONE authoritative land-target computation, over the FINAL
        // placed nonland entries (state.placedEntries is final at this point -- the Motor A/B loop
        // and community-gap resolution never mutate it again). This value becomes the ACTUAL number
        // of lands fillLands materializes -- not "whatever's left to hit targetMainboardSize" -- so
        // that Deck Studio's own LandTargetResolver.resolve call over this SAME finished mainboard
        // later returns the byte-identical Int (the zero-land-delta round-trip this workstream
        // exists to guarantee).
        val finalLandTarget = if (spec.fillLands) {
            val finalProfile = recomputeProfile(spec, template, state.placedEntries)
            LandTargetResolver.resolve(
                format = spec.format,
                archetypeSkeleton = resolveArchetypeSkeleton(template, finalProfile),
                profile = finalProfile,
                manaBaseAnalyzer = manaBaseAnalyzer,
            )
        } else {
            0
        }
        val landEntries = if (spec.fillLands) {
            fillLands(spec, template, state.placedEntries, finalLandTarget, collection, ownedQuantityByName, state.usedNames)
        } else {
            emptyList()
        }

        // Safety-net overshoot trim: the provisional seeds-only land estimate (plannedLands, above)
        // and the final full-mainboard land target (finalLandTarget, just computed) are each derived
        // from a DIFFERENT mainboard snapshot, so they are not guaranteed to agree -- the REAL total
        // (nonland + lands) can still land a card or two OVER targetMainboardSize with nothing
        // upstream to pull it back down (the "61/60" bug, Wizard Quality Campaign Wave 2). Shortfall
        // and overshoot are mutually exclusive by construction (the Motor A loop never places past
        // targetNonLand), so this is a pure safety net for the OPPOSITE case. Post-WS6 this should
        // fire far less often than before (the old two-different-computeLandTarget-snapshots
        // disagreement is gone -- only the seeds-only-vs-final estimate gap remains).
        val trimmedCount = trimExcess(spec, template, state, seedEntries, landEntries, targetMainboardSize)

        if (gapResult.unresolvedMisses > 0) {
            crashReporter?.log("deck_builder_v2_gap_resolution_partial")
            crashReporter?.setCustomKey("deck_builder_v2_miss_count", gapResult.unresolvedMisses.toString())
            crashReporter?.recordException(
                RuntimeException("deck_builder_v2_gap_resolution_partial: misses=${gapResult.unresolvedMisses}")
            )
        }
        if (trimmedCount > 0) {
            crashReporter?.log("deck_builder_v2_trim_excess")
            crashReporter?.setCustomKey("deck_builder_v2_trimmed_count", trimmedCount.toString())
            // WS6 canary (breadcrumb-only, NOT an error -- mirrors DeckTemplateResolver
            // .logSyntheticFallback's "frequency, not noise" pattern): the trim safety net firing
            // post-WS6 can now only mean the provisional seeds-only land estimate (plannedLands)
            // disagreed with the final full-mainboard land target (finalLandTarget) -- the
            // two-different-computeLandTarget-snapshots disagreement this workstream removed is no
            // longer a possible cause. If this fires often in practice, the ordering fix has a real
            // hole worth investigating.
            crashReporter?.log("deck_builder_v2_land_target_estimate_mismatch")
        }

        emit(TemplateBuildProgress.Stage(BuildStage.DONE))
        val finalProfile = recomputeProfile(spec, template, state.placedEntries)
        val filledCounts = filledCountsByCategory(state.placedEntries, finalProfile)
        val report = categories.map { category ->
            CategoryFill(
                category = SuggestionCategory(category.id, category.label),
                filled = filledCounts[category.id] ?: 0,
                target = category.targetCount,
            )
        }
        val trueShortfall = (targetMainboardSize - (state.placedEntries.sumOf { it.quantity } + landEntries.sumOf { it.quantity }))
            .coerceAtLeast(0)
        if (trueShortfall > 0) {
            crashReporter?.log("deck_builder_v2_gap_declared")
            crashReporter?.setCustomKey("deck_builder_v2_gap_count", trueShortfall.toString())
            crashReporter?.setCustomKey("deck_builder_v2_gap_format", spec.format.name)
            crashReporter?.setCustomKey("deck_builder_v2_gap_archetype", template.archetypeInfo.archetype?.name ?: "none")
            // Workstream 4.2: fill_source breakdown -- how many nonland copies each source placed
            // BEFORE this gap was declared, so post-release we can see where fills actually come
            // from (and how often the Scryfall backstop was even reachable).
            crashReporter?.setCustomKey("deck_builder_v2_fill_source_collection", state.collectionPlacedCopies.toString())
            crashReporter?.setCustomKey("deck_builder_v2_fill_source_community", state.communityPlacedCopies.toString())
            crashReporter?.setCustomKey("deck_builder_v2_fill_source_scryfall_backstop", state.scryfallBackstopPlacedCopies.toString())
        }
        val gaps = buildGaps(categories, filledCounts, template.colorIdentity, trueShortfall)

        val result = TemplateBuildResult(
            deckCards = state.placedEntries + landEntries,
            communitySuggestions = gapResult.suggestions,
            report = report,
            templateSource = template.source,
            archetypeInfo = template.archetypeInfo,
            archetypeOverride = template.archetypeInfo.archetype?.name,
            themesOverride = template.archetypeInfo.themes.map { it.name },
            colorConsistencyWarning = spec.format.isSixtyCardConstructed &&
                template.colorIdentity.count { it != ManaColor.C } > COLOR_DISCIPLINE_LIMIT,
            gamePlan = template.gamePlan,
            gaps = gaps,
        )
        emit(TemplateBuildProgress.Complete(result))
    }.flowOn(ioDispatcher)

    /** Mainboard target (excludes the commander itself for [DeckFormat.COMMANDER], which is written
     * to a separate `Deck.commanderCardId` slot, never a [DeckEntry] row — see
     * [com.mmg.manahub.feature.decks.presentation.wizard.DeckWizardViewModel.writeResultIntoNewDeck]). */
    private fun mainboardTargetSize(spec: DeckWizardSpec): Int =
        if (spec.format == DeckFormat.COMMANDER) spec.format.targetDeckSize - 1 else spec.format.targetDeckSize

    // ── VALIDATING ──────────────────────────────────────────────────────────────

    private fun isLegal(card: Card, format: DeckFormat): Boolean {
        fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
        return when (format) {
            DeckFormat.STANDARD -> ok(card.legalityStandard)
            DeckFormat.PIONEER -> ok(card.legalityPioneer)
            DeckFormat.MODERN -> ok(card.legalityModern)
            DeckFormat.LEGACY -> ok(card.legalityLegacy)
            DeckFormat.VINTAGE -> ok(card.legalityVintage)
            DeckFormat.PAUPER -> ok(card.legalityPauper)
            DeckFormat.COMMANDER -> ok(card.legalityCommander)
            DeckFormat.CASUAL, DeckFormat.DRAFT, DeckFormat.COMMANDER_CASUAL -> true
        }
    }

    private fun validate(spec: DeckWizardSpec): String? {
        if (spec.format == DeckFormat.COMMANDER) {
            val commander = spec.commander ?: return "A commander is required for the Commander format."
            if (!isLegal(commander, DeckFormat.COMMANDER)) {
                return "${commander.name} is not legal as a Commander."
            }
            val identity = commander.colorIdentity.toSet()
            val offIdentitySeed = spec.seeds.firstOrNull { seed -> seed.colorIdentity.any { it !in identity } }
            if (offIdentitySeed != null) {
                return "${offIdentitySeed.name} is outside ${commander.name}'s color identity."
            }
        }
        return null
    }

    // ── ANALYZING_COLLECTION ────────────────────────────────────────────────────

    private fun analyzeCollection(spec: DeckWizardSpec, ownedCards: List<Card>): List<Card> {
        val commanderIdentity = spec.commander?.colorIdentity?.toSet()
        return ownedCards
            .filterNot { BasicLandCalculator.isLand(it) }
            // Deck Engine Unification RUN 7a (BUG-1 follow-up, plan §7 acceptance criterion 4): the
            // commander itself is a legal, on-color, owned card, so without this exclusion it could
            // pass every filter below and get selected by the Motor A loop as a REGULAR mainboard
            // placement -- silently consuming one of mainboardTargetSize's 99 reserved non-commander
            // slots. writeResultIntoNewDeck then writes the commander's own qty-1 row a SECOND time
            // (its dedicated, unconditional insert), producing a genuine 2-copy violation of the
            // Commander singleton rule on the user's own commander. The commander is placed via its
            // own dedicated path, never via this candidate pool.
            .filterNot { spec.commander != null && it.scryfallId == spec.commander.scryfallId }
            .filter { isLegal(it, spec.format) }
            .filter { card ->
                when {
                    spec.format == DeckFormat.COMMANDER && commanderIdentity != null ->
                        commanderIdentity.containsAll(card.colorIdentity)
                    // D9: Casual has no identity rule -- the wizard's chosen colors are a spell
                    // COLOR filter, not a color-identity restriction. Colorless cards always pass.
                    // NOTE: spec.colorIdentity.isEmpty() is a VALID, intentional filter state (the
                    // wizard's "Colorless" pick, see deck_seeds_identity_colorless) -- it must still
                    // gate this branch (never fall through to `else -> true`), otherwise a Casual
                    // "colorless" build silently admits cards of every color.
                    spec.format.isSixtyCardConstructed -> {
                        val cardColors = card.colors.mapNotNull { s -> ManaColor.entries.firstOrNull { it.symbol == s } }
                        cardColors.isEmpty() || cardColors.all { it in spec.colorIdentity }
                    }
                    else -> true
                }
            }
    }

    // ── MAPPING_CATEGORIES ──────────────────────────────────────────────────────

    /** Merges every seed into its resolved category, creating a zero-target category for a seed
     * whose category the template did not already declare (seeds are NEVER dropped). Preserves the
     * template's own category ORDER (plan §3.3 step 5: "per category, in template order"),
     * appending seed-only categories at the end. Used for [TemplateBuildResult.report]/
     * [TemplateBuildResult.gaps] labeling only — the Motor A loop itself is category-agnostic. */
    private fun mapCategories(template: DeckTemplate, spec: DeckWizardSpec, profile: DeckProfile): List<TemplateCategory> {
        val existingIds = template.categories.mapTo(mutableSetOf()) { it.id }
        val extra = mutableListOf<TemplateCategory>()
        spec.seeds.forEach { seed ->
            val category = SuggestionCategoryResolver.resolve(seed, profile = profile)
            if (existingIds.add(category.id)) {
                extra += TemplateCategory(id = category.id, label = category.displayLabel, targetCount = 0, cards = emptyList())
            }
        }
        return template.categories + extra
    }

    // ── FILLING_FROM_COLLECTION (Motor A/B loop, Deck Engine Unification D1) ───────

    /** Workstream 4.2 telemetry: which fill source placed a given [MotorCandidate] / backstop card
     * -- reported as a `fill_source` breakdown alongside a declared gap (see [invoke]'s
     * `deck_builder_v2_gap_declared` block), never used for placement logic itself. */
    private enum class FillSource { COLLECTION, COMMUNITY, SCRYFALL_BACKSTOP }

    private class FillState {
        val placedEntries = mutableListOf<DeckEntry>()
        val usedNames = mutableSetOf<String>()
        /** Category id -> multi-copy shortfalls (an owned card placed with fewer copies than its
         * playset target because the collection didn't hold enough copies) -- folded into
         * [TemplateBuildResult.communitySuggestions] alongside genuinely-unowned template refs. */
        val sameCardShortfalls = mutableMapOf<String, MutableList<TemplateCardSuggestion>>()
        /** Workstream 4.2 telemetry counters -- nonland copies placed by each fill source, read once
         * when a gap is declared. Never consulted by any placement/scoring decision. */
        var collectionPlacedCopies = 0
        var communityPlacedCopies = 0
        var scryfallBackstopPlacedCopies = 0
    }

    /** One placement candidate, unifying Motor A ([AddSuggestion]) and Motor B
     * ([CommunityAddSuggestion]) into a single rankable shape for the merge/sort step. */
    private data class MotorCandidate(val card: Card, val score: Float, val suggestedCopies: Int, val source: FillSource)

    /**
     * The Deck Engine Unification (D1) placement loop: repeatedly asks [suggestAddsFromCollectionUseCase]
     * (Motor A) -- plus the pre-fetched [communityOwnedPool] (Motor B, when enabled) -- for the
     * best-fitting REMAINING owned candidates against [FillState.placedEntries] AS BUILT SO FAR,
     * places every returned candidate whose score clears [CATEGORY_FILL_FIT_FLOOR] (batched, capped
     * at [MOTOR_A_BATCH_LIMIT] candidates per Motor A call so the cost stays bounded), recomputes the
     * profile, and repeats. Stops when the mainboard target is reached or a whole batch clears no
     * candidate above the floor (the collection has nothing left worth placing -- the remaining slots
     * become [TemplateBuildResult.gaps], never a weak fill).
     */
    private suspend fun runMotorALoop(
        spec: DeckWizardSpec,
        template: DeckTemplate,
        ownedCards: List<Card>,
        communityOwnedPool: List<CommunityAddSuggestion>,
        ownedQuantityByName: Map<String, Int>,
        targetNonLand: Int,
        state: FillState,
    ) {
        var iterations = 0
        val maxIterations = targetNonLand + MAX_ITERATION_SLACK
        while (iterations < maxIterations) {
            iterations++
            val remaining = targetNonLand - state.placedEntries.sumOf { it.quantity }
            if (remaining <= 0) break

            val profile = recomputeProfile(spec, template, state.placedEntries)
            val resolvedSkeleton = resolveArchetypeSkeleton(template, profile)
            val batchLimit = remaining.coerceIn(1, MOTOR_A_BATCH_LIMIT)

            val motorACandidates = runCatching {
                suggestAddsFromCollectionUseCase(
                    collection = ownedCards.filterNot { it.name in state.usedNames },
                    mainboard = state.placedEntries,
                    profile = profile,
                    resolvedSkeleton = resolvedSkeleton,
                    weights = ScoreWeights(),
                    limit = batchLimit,
                )
            }.getOrElse { t ->
                crashReporter?.recordException(RuntimeException("deck_builder_v2_motor_a_failed", t))
                emptyList()
            }.map { MotorCandidate(it.fit.card, it.fit.score, it.suggestedCopies, FillSource.COLLECTION) }

            val motorBCandidates = communityOwnedPool
                .asSequence()
                .filter { it.card.name !in state.usedNames }
                .map {
                    MotorCandidate(
                        it.card, it.synergy,
                        motorBSuggestedCopies(it.card, spec, state, ownedQuantityByName),
                        FillSource.COMMUNITY,
                    )
                }
                .toList()

            val combined = (motorACandidates + motorBCandidates)
                .distinctBy { it.card.name }
                .filter { it.score >= CATEGORY_FILL_FIT_FLOOR }
                .sortedWith(
                    compareByDescending<MotorCandidate> { it.score }
                        .thenBy { it.card.name }
                        .thenBy { it.card.scryfallId }
                )

            if (combined.isEmpty()) break

            var remainingInBatch = remaining
            var placedAny = false
            for (candidate in combined) {
                if (remainingInBatch <= 0) break
                if (candidate.card.name in state.usedNames) continue
                state.usedNames += candidate.card.name

                val ownedCopies = ownedQuantityByName[candidate.card.name] ?: 1
                val placedCopies = minOf(candidate.suggestedCopies, ownedCopies, remainingInBatch).coerceAtLeast(1)
                state.placedEntries += DeckEntry(card = candidate.card, quantity = placedCopies, isOwned = true, isSideboard = false)
                remainingInBatch -= placedCopies
                placedAny = true
                when (candidate.source) {
                    FillSource.COLLECTION -> state.collectionPlacedCopies += placedCopies
                    FillSource.COMMUNITY -> state.communityPlacedCopies += placedCopies
                    FillSource.SCRYFALL_BACKSTOP -> Unit // never produced by this loop; see runScryfallBackstopLoop
                }

                val shortfall = candidate.suggestedCopies - placedCopies
                if (shortfall > 0) {
                    val categoryId = SuggestionCategoryResolver.resolve(candidate.card, profile = profile).id
                    state.sameCardShortfalls.getOrPut(categoryId) { mutableListOf() } +=
                        TemplateCardSuggestion(card = candidate.card, weight = candidate.score, suggestedCopies = shortfall)
                }
            }
            if (!placedAny) break
        }
    }

    private fun motorBSuggestedCopies(card: Card, spec: DeckWizardSpec, state: FillState, ownedQuantityByName: Map<String, Int>): Int {
        if (!spec.format.isSixtyCardConstructed) return 1
        val maxCopies = spec.format.maxCopies
        val already = state.placedEntries.filter { it.card.name == card.name }.sumOf { it.quantity }
        return (maxCopies - already).coerceIn(1, maxCopies)
    }

    /**
     * Resolves the archetype/theme skeleton Motor A's theme-role gap bonus scores against, mirroring
     * [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.resolveArchetypeSkeleton]
     * EXACTLY (same inputs: format, resolved macro/themes, color count). Returns `null` for the
     * no-macro-pin-with-no-themes / no-archetype-skeleton (Draft) cases -- Motor A then scores
     * purely off its own base ranking plus the pip multiplier, with zero theme bonus. Deck
     * Analysis Engine v3 removed `ArchetypeId.GENERIC` -- `archetype == null` is the new signal.
     */
    private fun resolveArchetypeSkeleton(template: DeckTemplate, profile: DeckProfile): ResolvedArchetypeSkeleton? {
        val archetypeFormat = ArchetypeFormat.of(profile.format) ?: return null
        val archetype = template.archetypeInfo.archetype
        val themes = template.archetypeInfo.themes
        if (archetype == null && themes.isEmpty()) return null
        return ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = archetype,
            themes = themes,
            identity = profile.colorIdentity,
        )
    }

    // ── Scryfall backstop fill (Deck Wizard & Engine Rework plan, Workstream 4.1 / F4) ──────────

    /**
     * The last-resort fill source: runs AFTER [runMotorALoop] has already exhausted the owned
     * collection (plus Motor B), and BEFORE community-template gap resolution declares the
     * remaining slots as [DeckGap]s. Gated on BOTH a non-null [candidatePoolGenerator] (constructor
     * wiring) and [DeckWizardSpec.includeOutsideCollection] (the user's own per-session toggle) — a
     * silent no-op otherwise, leaving the build byte-identical to pre-Workstream-4 behavior.
     *
     * Mirrors [runMotorALoop]'s own loop shape (recompute profile -> ask for candidates -> place
     * everything clearing [CATEGORY_FILL_FIT_FLOOR] -> repeat) but sources candidates from
     * [CandidatePoolGenerator] (a live Scryfall query, revived per plan F4) instead of the owned
     * collection, and re-scores them through the SAME [suggestAddsFromCollectionUseCase] (Motor A)
     * so the fit floor and ranking stay byte-identical in spirit to every other placement decision
     * this use case makes — quality over completion, never a weak fill just to hit the target size.
     *
     * Pagination + progressive relaxation (plan's stop-condition policy): each iteration that clears
     * no candidate above the floor first pages forward (up to [MAX_BACKSTOP_PAGES]) on the SAME
     * query set, then -- once every page is exhausted -- tries ONE relaxed round (role-gap queries
     * dropped, strategy/tribe queries kept, per [CandidatePoolGenerator]'s own `relaxed` param) before
     * giving up entirely. A remaining shortfall after that becomes an honest [DeckGap], exactly like
     * an exhausted owned collection — [TemplateBuildResult.gaps]' `gaps.sumOf { it.missingCount } ==
     * trueShortfall` invariant is untouched by this phase (it only ever ADDS placed entries or stops).
     */
    private suspend fun runScryfallBackstopLoop(
        spec: DeckWizardSpec,
        template: DeckTemplate,
        targetNonLand: Int,
        state: FillState,
    ) {
        val generator = candidatePoolGenerator ?: return
        if (!spec.includeOutsideCollection) return

        var page = 1
        var relaxed = false
        var iterations = 0
        val maxIterations = targetNonLand + MAX_BACKSTOP_ITERATION_SLACK
        while (iterations < maxIterations) {
            iterations++
            val remaining = targetNonLand - state.placedEntries.sumOf { it.quantity }
            if (remaining <= 0) return

            val profile = recomputeProfile(spec, template, state.placedEntries)
            val resolvedSkeleton = resolveArchetypeSkeleton(template, profile)
            val nonLandEntries = state.placedEntries.filterNot { BasicLandCalculator.isLand(it.card) }
            val evaluation = deckScorer.evaluate(profile, nonLandEntries)

            val freshPool = runCatching {
                generator(profile = profile, evaluation = evaluation, page = page, relaxed = relaxed)
            }.getOrElse { t ->
                crashReporter?.recordException(RuntimeException("deck_builder_v2_scryfall_backstop_query_failed", t))
                emptyList()
            }.filterNot { it.name in state.usedNames }

            val scored = if (freshPool.isEmpty()) {
                emptyList()
            } else {
                runCatching {
                    suggestAddsFromCollectionUseCase(
                        collection = freshPool,
                        mainboard = state.placedEntries,
                        profile = profile,
                        resolvedSkeleton = resolvedSkeleton,
                        weights = ScoreWeights(),
                        limit = freshPool.size,
                    )
                }.getOrElse { t ->
                    crashReporter?.recordException(RuntimeException("deck_builder_v2_scryfall_backstop_score_failed", t))
                    emptyList()
                }
            }
                .filter { it.fit.score >= CATEGORY_FILL_FIT_FLOOR }
                .sortedWith(
                    compareByDescending<AddSuggestion> { it.fit.score }
                        .thenBy { it.fit.card.name }
                        .thenBy { it.fit.card.scryfallId }
                )

            if (scored.isEmpty()) {
                // Progressive relaxation (plan's stop-condition policy): page forward on the SAME
                // query set first; only once every page is exhausted, try one relaxed round; only
                // once that ALSO comes up empty does this phase give up and let the shortfall
                // surface as an honest DeckGap downstream.
                if (page < MAX_BACKSTOP_PAGES) {
                    page++
                    continue
                }
                if (!relaxed) {
                    relaxed = true
                    page = 1
                    continue
                }
                return
            }

            var remainingInBatch = remaining
            for (candidate in scored) {
                if (remainingInBatch <= 0) break
                val card = candidate.fit.card
                if (card.name in state.usedNames) continue
                state.usedNames += card.name
                // Unlike runMotorALoop, there is no owned-quantity clamp here -- these cards are
                // genuinely NOT owned (fetched from Scryfall), so only the format's own copy limit
                // (already applied by suggestAddsFromCollectionUseCase's suggestedCopies) bounds them.
                val placedCopies = minOf(candidate.suggestedCopies, remainingInBatch).coerceAtLeast(1)
                state.placedEntries += DeckEntry(card = card, quantity = placedCopies, isOwned = false, isSideboard = false)
                remainingInBatch -= placedCopies
                state.scryfallBackstopPlacedCopies += placedCopies
            }
            // The profile/gaps just shifted with this batch's placements -- re-evaluate fresh on the
            // next iteration rather than continuing to page the now-stale query set.
            page = 1
        }
    }

    // ── Motor B (Deck Engine Unification D1) — fetched ONCE per build ─────────────

    /**
     * Best-effort, ONE-TIME fetch of Motor B's OWNED-in-collection candidates (mirrors
     * [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.recomputeCommunityInternal]'s
     * aggregate-fetch branch). Returns `emptyList()` (never throws) when Motor B's dependencies are
     * absent, [isCommunityEngineEnabled] (the global flag) is off, [DeckWizardSpec.useCommunityData]
     * (Deck Engine Unification plan §5 Phase 3.5 — the wizard's OWN per-build Review-step toggle) is
     * false, the aggregate fetch fails, or nothing resolved comes back owned -- a silent, always-safe
     * no-op that leaves the build on Motor A alone. BOTH gates must be true: the global flag is a
     * kill switch, the per-build toggle is the user's own choice for THIS deck.
     */
    private suspend fun fetchCommunityOwnedCandidates(
        spec: DeckWizardSpec,
        template: DeckTemplate,
        ownedCards: List<Card>,
    ): List<CommunityAddSuggestion> {
        val aggregateRepository = communityAggregateRepository
        val addsUseCase = suggestAddsFromCommunityUseCase
        if (aggregateRepository == null || addsUseCase == null) return emptyList()
        if (!spec.useCommunityData) return emptyList()
        if (!runCatching { isCommunityEngineEnabled() }.getOrDefault(false)) return emptyList()

        return runCatching {
            val archetypeFormat = ArchetypeFormat.of(spec.format)
            val aggregateResult: DataResult<CommunityAggregate>? = when {
                spec.format == DeckFormat.COMMANDER && spec.commander != null ->
                    aggregateRepository.getCommanderAggregate(spec.commander.name)
                archetypeFormat != null -> {
                    val signature = spec.seeds.map { it.name }.distinct().sorted().take(SIGNATURE_CARD_COUNT)
                    if (signature.isEmpty()) null else aggregateRepository.getSixtyAggregate(signature, ArchidektFormat.CUSTOM.apiId)
                }
                else -> null
            }
            val cards: List<AggregateCardEntry> = when (val agg = aggregateResult) {
                is DataResult.Success -> when (val data = agg.data) {
                    is CommunityAggregate.Commander -> data.cards
                    is CommunityAggregate.Sixty.Materialized -> data.cards
                    else -> emptyList()
                }
                else -> emptyList()
            }
            if (cards.isEmpty()) return@runCatching emptyList()

            val seedProfile = recomputeProfile(spec, template, spec.seeds.map { DeckEntry(it, 1, true, false) })
            val resolvedSkeleton = resolveArchetypeSkeleton(template, seedProfile)
            addsUseCase(
                aggregateCards = cards,
                mainboard = spec.seeds.map { DeckEntry(it, 1, true, false) },
                profile = seedProfile,
                collection = ownedCards,
                resolvedSkeleton = resolvedSkeleton,
            ).filter { it.ownedInCollection }
        }.getOrElse { t ->
            crashReporter?.recordException(RuntimeException("deck_builder_v2_motor_b_failed", t))
            emptyList()
        }
    }

    // ── RESOLVING_GAPS: unowned community-template suggestions (view-only, D8) ────

    private class GapResult(val suggestions: List<CategorySuggestions>, val unresolvedMisses: Int)

    /** Post-hoc: attaches every category's leftover UNOWNED [TemplateCardRef] suggestions (up to its
     * target, view-only, never auto-added) plus the multi-copy [FillState.sameCardShortfalls]
     * gathered during [runMotorALoop] -- structurally SEPARATE from [FillState.placedEntries] (D3:
     * collection and community suggestions are never mixed in the same list). */
    private suspend fun resolveCommunitySuggestions(categories: List<TemplateCategory>, state: FillState): GapResult {
        val suggestions = mutableListOf<CategorySuggestions>()
        var misses = 0
        val filledCounts = filledCountsByCategoryRaw(state.placedEntries)
        categories.forEach { category ->
            val filled = filledCounts[category.id] ?: 0
            val remaining = (category.targetCount - filled).coerceAtLeast(0)
            val sameCardShortfalls = state.sameCardShortfalls[category.id].orEmpty()

            val resolvedUnowned = if (remaining > 0) {
                val unownedRefs = category.cards
                    .filterNot { it.name in state.usedNames }
                    .sortedWith(compareByDescending<TemplateCardRef> { it.weight }.thenBy { it.name })
                    .take(remaining)
                unownedRefs.mapNotNull { ref ->
                    val card = resolveByExactName(ref.name)
                    if (card == null) {
                        misses++
                        null
                    } else {
                        state.usedNames += card.name
                        TemplateCardSuggestion(card = card, weight = ref.weight, suggestedCopies = ref.copies)
                    }
                }
            } else {
                emptyList()
            }

            val combined = sameCardShortfalls + resolvedUnowned
            if (combined.isNotEmpty()) {
                suggestions += CategorySuggestions(
                    category = SuggestionCategory(category.id, category.label),
                    suggestions = combined,
                )
            }
        }
        return GapResult(suggestions, misses)
    }

    /** Exact-name lookup ONLY (never [CardRepository.searchWithRawQuery]) -- DFC/split names are
     * normalized to their front face first. Returns null on any miss (card renamed/typo/etc.);
     * callers count misses and skip silently, never abort the build. */
    private suspend fun resolveByExactName(name: String): Card? {
        val normalized = name.substringBefore(" // ").trim()
        if (normalized.isEmpty()) return null
        return runCatching { cardRepository.getCardByExactName(normalized) }.getOrNull()?.getOrNull()
    }

    // ── TRIMMING_EXCESS ─────────────────────────────────────────────────────────

    /**
     * Overshoot safety net (Wizard Quality Campaign Wave 2, refined by WS6 "One land engine"): the
     * provisional seeds-only land estimate (`plannedLands`, computed before the Motor A/B loop) and
     * the final [com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver] recompute over the
     * finished nonland mainboard (`finalLandTarget`, computed just before [fillLands]) are each
     * derived from a DIFFERENT mainboard snapshot -- the two are not guaranteed monotonic, so the
     * REAL total (nonland + [landEntries]) can land a card or two OVER [targetFullSize] with nothing
     * upstream to pull it back down. Removes the WORST-fit surplus nonland card(s) -- ranked by the
     * SAME [DeckScorer.fit] the loop placed them with -- until the deck is exactly on target or the
     * trimmable pool is exhausted. Pure math delegated to [MainboardTrimmer] (kept directly
     * unit-testable without this class's coroutine/repository dependencies).
     *
     * Protected: [seedEntries] (the user's own explicit picks, by name) and [DeckWizardSpec
     * .commander] (by scryfallId -- defensive; the commander is never placed as a mainboard
     * [DeckEntry] to begin with per [mainboardTargetSize]'s KDoc). Never touches [landEntries].
     *
     * @return how many total copies were trimmed (0 when the deck was already at or under target).
     */
    private fun trimExcess(
        spec: DeckWizardSpec,
        template: DeckTemplate,
        state: FillState,
        seedEntries: List<DeckEntry>,
        landEntries: List<DeckEntry>,
        targetFullSize: Int,
    ): Int {
        val overshoot = (state.placedEntries.sumOf { it.quantity } + landEntries.sumOf { it.quantity }) - targetFullSize
        if (overshoot <= 0) return 0

        val beforeQty = state.placedEntries.sumOf { it.quantity }
        val protectedNames = seedEntries.mapTo(mutableSetOf()) { it.card.name }
        // Defense in depth (RUN 3b QA fix): same commander-is-Commander-only gate as recomputeProfile
        // above -- defensive only, since the commander is never placed as a mainboard DeckEntry to
        // begin with (mainboardTargetSize's KDoc), but keeps this set correct if that ever changes.
        val protectedIds = if (spec.format == DeckFormat.COMMANDER) setOfNotNull(spec.commander?.scryfallId) else emptySet()
        val profile = recomputeProfile(spec, template, state.placedEntries)

        val trimmed = MainboardTrimmer.trim(
            entries = state.placedEntries,
            protectedNames = protectedNames,
            protectedIds = protectedIds,
            overshootCount = overshoot,
            scoreOf = { card -> deckScorer.fit(card, profile, isOwned = true).score },
        )

        state.placedEntries.clear()
        state.placedEntries += trimmed
        return beforeQty - trimmed.sumOf { it.quantity }
    }

    // ── FILLING_LANDS (Deck Wizard & Engine Rework plan, Workstream 9.4) ────────

    /**
     * Two-stage land fill (WS9.4, F fix "the wizard's own manabase never has enough fixing"):
     * 1. NON-BASIC fixing lands first ([fillFixingLands]) -- count driven by [ArchetypeData
     *    .landMixFor]'s basics-vs-fixing composition for the deck's color count.
     * 2. Whatever land slots remain -> [BasicLandCalculator]'s existing basics split, UNCHANGED
     *    math, just now filling fewer slots than before whenever stage 1 placed anything.
     */
    private suspend fun fillLands(
        spec: DeckWizardSpec,
        template: DeckTemplate,
        placedEntries: List<DeckEntry>,
        landTarget: Int,
        collection: List<UserCardWithCard>,
        ownedQuantityByName: Map<String, Int>,
        usedNames: MutableSet<String>,
    ): List<DeckEntry> {
        val fixingLandEntries = fillFixingLands(spec, template, landTarget, collection, ownedQuantityByName, usedNames)

        val nonBasicLandSeeds = spec.seeds.filter { BasicLandCalculator.isLand(it) && !BasicLandCalculator.isBasicLand(it) }

        val mainboardDeckCards = placedEntries.map { DeckCard(it.card, it.quantity, it.isOwned) }
        // WS9.4: the newly-placed fixing lands reduce the basics slot count exactly like a
        // non-basic land SEED already did -- both are folded into the SAME `nonBasicLands` param so
        // BasicLandCalculator.calculate's `basicSlotsAvailable = totalLandTarget - nonBasicCount`
        // subtraction accounts for the fixing fill without any change to that calculator's math.
        val nonBasicLandDeckCards = nonBasicLandSeeds.map { DeckCard(it, 1, true) } +
            fixingLandEntries.map { DeckCard(it.card, it.quantity, it.isOwned) }
        // BasicLandCalculator.calculate falls back to an EVEN SPLIT across `commanderIdentity` only
        // when the mainboard has zero colored pips (totalWeight == 0) -- with a bare `null` for
        // every non-Commander format, that fallback never fires and the calculator silently returns
        // an all-ZERO BasicLandDistribution (an unplayable, land-less deck). A Casual build's filled
        // mainboard can land at zero colored pips two ways: the wizard's own "Colorless" (0-color)
        // pick, or incidentally for any color-light/artifact-heavy fill -- so the wizard's OWN chosen
        // spec.colorIdentity is the correct color-restriction fallback here, mirroring what the
        // Commander branch already does with the commander's identity.
        val landColorIdentity = when {
            spec.format == DeckFormat.COMMANDER -> spec.commander?.colorIdentity?.toSet()
            spec.colorIdentity.isNotEmpty() -> spec.colorIdentity.map { it.symbol }.toSet()
            // Genuinely 0-color ("Colorless") Casual pick: 0 basics is the best available result --
            // this calculator has no "Wastes"/colorless-land concept. Known, out-of-scope limitation
            // (adding Wastes support is a separate feature, not a fix for this bug).
            else -> null
        }

        val distribution = BasicLandCalculator.calculate(
            mainboard = mainboardDeckCards,
            nonBasicLands = nonBasicLandDeckCards,
            totalLandTarget = landTarget,
            commanderIdentity = landColorIdentity,
        )
        return fixingLandEntries + materializeBasics(distribution)
    }

    /**
     * Stage 1 of [fillLands] (WS9.4): places NON-BASIC fixing lands (duals/triomes/rainbow
     * utility lands producing >= 2 distinct colors -- the SAME "fixing land" definition
     * [com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier]'s `manaFixMatcher`
     * uses via [Card.producedMana]) before any basic is materialized. The TARGET count comes from
     * [ArchetypeData.landMixFor]'s `basicsRatio` for [template]'s color count -- `(1 -
     * midpoint(basicsRatio)) * landTarget`, rounded -- never a separate hardcoded number.
     *
     * Sourced from the user's OWNED collection first (deterministic: most colors produced desc,
     * then name/id for a stable tie-break -- never an alphabetical Scryfall search), then the
     * WS4 Scryfall backstop (mirrors [runScryfallBackstopLoop]'s shape: budget-free per D-H,
     * paginated, `ScryfallRequestQueue`-routed via [CardRepository.searchWithRawQuery]) ONLY when
     * [DeckWizardSpec.includeOutsideCollection] is on. A [DeckFormat.DRAFT] spec (no
     * [ArchetypeFormat]) or a mono-color / colorless identity (fixing ratio ~0) short-circuits to
     * an empty list -- the existing basics-only path is untouched for those cases.
     */
    private suspend fun fillFixingLands(
        spec: DeckWizardSpec,
        template: DeckTemplate,
        landTarget: Int,
        collection: List<UserCardWithCard>,
        ownedQuantityByName: Map<String, Int>,
        usedNames: MutableSet<String>,
    ): List<DeckEntry> {
        if (landTarget <= 0) return emptyList()
        val archetypeFormat = ArchetypeFormat.of(spec.format) ?: return emptyList()
        val identity = template.colorIdentity
        val colorCount = identity.count { it != ManaColor.C }
        val mix = ArchetypeData.landMixFor(archetypeFormat, colorCount)
        val fixingRatio = 1.0 - ((mix.basicsRatio.start + mix.basicsRatio.endInclusive) / 2.0)
        val fixingTarget = (fixingRatio * landTarget).roundToInt().coerceIn(0, landTarget)
        if (fixingTarget <= 0) return emptyList()

        val identitySymbols = identity.map { it.symbol }.toSet()
        val maxCopies = if (spec.format == DeckFormat.COMMANDER) 1 else spec.format.maxCopies

        val placed = mutableListOf<DeckEntry>()
        var remaining = fixingTarget

        val ownedFixingLands = collection
            .map { it.card }
            .distinctBy { it.scryfallId }
            .filter { isFixingLand(it) }
            .filter { identitySymbols.containsAll(it.colorIdentity) }
            .filter { it.name !in usedNames }
            .sortedWith(
                compareByDescending<Card> { it.producedMana.toSet().count { c -> c in "WUBRG" } }
                    .thenBy { it.name }
                    .thenBy { it.scryfallId }
            )
        for (card in ownedFixingLands) {
            if (remaining <= 0) break
            if (card.name in usedNames) continue
            usedNames += card.name
            val owned = ownedQuantityByName[card.name] ?: 1
            val qty = minOf(owned, maxCopies, remaining).coerceAtLeast(1)
            placed += DeckEntry(card = card, quantity = qty, isOwned = true, isSideboard = false)
            remaining -= qty
        }

        if (remaining > 0 && spec.includeOutsideCollection) {
            placed += fillFixingLandsFromScryfallBackstop(spec, identity, remaining, usedNames)
        }

        return placed
    }

    /** The same "fixing land" definition as
     * [com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier]'s private
     * `manaFixMatcher` (D14's [Card.producedMana], >= 2 distinct WUBRG colors) -- a non-basic land
     * that structurally produces at least two colors. */
    private fun isFixingLand(card: Card): Boolean =
        BasicLandCalculator.isLand(card) &&
            !BasicLandCalculator.isBasicLand(card) &&
            card.producedMana.toSet().count { it in "WUBRG" } >= 2

    /**
     * Stage 1's outside-collection last resort (WS9.4), mirroring [runScryfallBackstopLoop]'s
     * shape (paginated, budget-free per D-H, `CardRepository`-routed, dedup by name) but over a
     * single fixed query rather than a role/theme loop: `t:land id<=<identity> produces>=2
     * -t:basic`, `order=edhrec`. Every card this returns is genuinely NOT owned, so only the
     * format's own copy limit bounds it (no owned-quantity clamp, unlike the collection branch).
     */
    private suspend fun fillFixingLandsFromScryfallBackstop(
        spec: DeckWizardSpec,
        identity: Set<ManaColor>,
        needed: Int,
        usedNames: MutableSet<String>,
    ): List<DeckEntry> {
        val idSymbols = identity.filter { it != ManaColor.C }.map { it.symbol }.sorted().joinToString(separator = "")
        val idFragment = if (idSymbols.isEmpty()) null else "id<=$idSymbols"
        // v1 wizard scope is Commander + Casual (DeckWizardSpec's own KDoc); Casual has no
        // universal Scryfall legality token, mirroring CandidatePoolGenerator.legalityFragment.
        val legalFragment = if (spec.format == DeckFormat.COMMANDER) "legal:commander" else null
        val query = listOfNotNull(idFragment, legalFragment, "t:land", "produces>=2", "-t:basic").joinToString(separator = " ")
        val maxCopies = if (spec.format == DeckFormat.COMMANDER) 1 else spec.format.maxCopies

        val placed = mutableListOf<DeckEntry>()
        var remaining = needed
        var page = 1
        while (remaining > 0 && page <= MAX_FIXING_LAND_BACKSTOP_PAGES) {
            val results = runCatching { cardRepository.searchWithRawQuery(query, order = "edhrec", page = page) }
                .getOrElse { t ->
                    crashReporter?.recordException(RuntimeException("deck_builder_v2_fixing_land_backstop_failed", t))
                    emptyList()
                }
            if (results.isEmpty()) break
            for (card in results) {
                if (remaining <= 0) break
                if (card.name in usedNames) continue
                usedNames += card.name
                val qty = minOf(maxCopies, remaining)
                placed += DeckEntry(card = card, quantity = qty, isOwned = false, isSideboard = false)
                remaining -= qty
            }
            page++
        }
        return placed
    }

    private suspend fun materializeBasics(distribution: BasicLandDistribution): List<DeckEntry> {
        val wanted = listOf(
            "Plains" to distribution.plains,
            "Island" to distribution.islands,
            "Swamp" to distribution.swamps,
            "Mountain" to distribution.mountains,
            "Forest" to distribution.forests,
        ).filter { it.second > 0 }
        return wanted.mapNotNull { (name, qty) ->
            val card = resolveByExactName(name) ?: return@mapNotNull null
            DeckEntry(card = card, quantity = qty, isOwned = true, isSideboard = false)
        }
    }

    // ── Gap reporting (Deck Engine Unification D3) ──────────────────────────────

    /**
     * Attributes [trueShortfall] (the exact numeric slack between the final deck and the format's
     * target) across the template's categories: each category still under its own [TemplateCategory
     * .targetCount] contributes a raw [DeckGap], largest first. The raw buckets are then capped so
     * their sum never exceeds [trueShortfall]; any residual the categories don't account for (e.g. a
     * rare land-materialization shortfall, or category targets that don't sum to the full mainboard
     * target) lands in one catch-all "Other" bucket. This GUARANTEES `gaps.sumOf { it.missingCount }
     * == trueShortfall` exactly, by construction -- the HARD invariant the harness's `size` metric
     * checks (`cards + declared gaps == target`).
     */
    private fun buildGaps(
        categories: List<TemplateCategory>,
        filledCounts: Map<String, Int>,
        colorIdentity: Set<ManaColor>,
        trueShortfall: Int,
    ): List<DeckGap> {
        if (trueShortfall <= 0) return emptyList()
        val colors = colorIdentity.filterNotTo(mutableSetOf()) { it == ManaColor.C }

        val raw = categories
            .mapNotNull { category ->
                val filled = filledCounts[category.id] ?: 0
                val missing = category.targetCount - filled
                if (missing <= 0) return@mapNotNull null
                DeckGap(categoryId = category.id, categoryLabel = category.label, colors = colors, missingCount = missing)
            }
            .sortedByDescending { it.missingCount }

        val gaps = mutableListOf<DeckGap>()
        var remaining = trueShortfall
        for (gap in raw) {
            if (remaining <= 0) break
            val amount = gap.missingCount.coerceAtMost(remaining)
            gaps += gap.copy(missingCount = amount)
            remaining -= amount
        }
        if (remaining > 0) {
            gaps += DeckGap(categoryId = OTHER_GAP_CATEGORY_ID, categoryLabel = "Other", colors = colors, missingCount = remaining)
        }
        return gaps
    }

    /** Groups the FINAL nonland mainboard by [SuggestionCategoryResolver] bucket, scored against the
     * FINAL deck profile -- feeds both [TemplateBuildResult.report] and [buildGaps]. */
    private fun filledCountsByCategory(mainboard: List<DeckEntry>, profile: DeckProfile): Map<String, Int> =
        mainboard
            .filterNot { BasicLandCalculator.isLand(it.card) }
            .groupBy { SuggestionCategoryResolver.resolve(it.card, profile = profile).id }
            .mapValues { (_, entries) -> entries.sumOf { it.quantity } }

    /** Cheap variant used mid-build (before lands are filled) by [resolveCommunitySuggestions] -- a
     * fresh profile snapshot for bucketing alone would be wasted work at that point, so this groups
     * by the SuggestionCategoryResolver id already implied by each entry's placement (no profile
     * dependency: category resolution needs a profile only for a couple of profile-aware tie-breaks
     * that don't affect a placed card's OWN bucket once it's already in the deck). */
    private fun filledCountsByCategoryRaw(mainboard: List<DeckEntry>): Map<String, Int> =
        mainboard
            .filterNot { BasicLandCalculator.isLand(it.card) }
            .groupBy { SuggestionCategoryResolver.resolve(it.card, profile = null).id }
            .mapValues { (_, entries) -> entries.sumOf { it.quantity } }

    // ── Shared helpers ──────────────────────────────────────────────────────────

    /**
     * Wizard Quality Campaign B3: rebuilds the [DeckProfile] `deckScorer.fit`/Motor A rank candidates
     * against, seeded EXACTLY like [com.mmg.manahub.feature.decks.domain.orchestrator
     * .DeckDoctorOrchestrator.loadAnalysis] seeds `EvaluateDeckUseCase` for the FINISHED deck: the
     * commander (Commander only) plus [mainboardSoFar]'s own highest-identity-tag cards, run through
     * [inferDeckIdentityUseCase]. This is the fix for "the Doctor wants to cut cards the wizard just
     * placed" (B3) -- both engines now rank against the same basis, and re-deriving it from
     * [mainboardSoFar] (not a static seeds-only snapshot taken once) means the fingerprint tracks the
     * deck as it actually grows during the Motor A/B loop, not just the wizard's initial seeds.
     *
     * The wizard's own EXPLICIT resolved template identity ([DeckTemplate.archetypeInfo] -- the
     * RESOLVED archetype/themes, which for a COMMUNITY Commander template also folds in EDHREC
     * aggregate data, not just [DeckWizardSpec.strategyProfile] verbatim -- see
     * [DeckTemplateResolver.fromCommanderAggregate]) plus [DeckWizardSpec.strategyProfile]'s tribe
     * pick are layered ON TOP of the inferred tags, never replaced by them -- early in a build
     * (empty/near-empty mainboard) the explicit hints are the ONLY signal (inference has nothing to
     * work from yet); as collection cards get placed, both contribute and the two converge toward
     * exactly what the Doctor would infer from the finished mainboard.
     *
     * Deck Engine Unification plan (D2): [DeckIdentitySeedTags.forArchetype] is the SAME shared
     * bridge [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.loadAnalysis]
     * folds in from a deck's PERSISTED `archetypeOverride`/`themesOverride`/`tribeOverride` pin once
     * the build is finished -- this is what makes the build-time and post-build fit scores converge
     * for a deck with a pinned identity, not just an unpinned/GENERIC one. See
     * [DeckIdentitySeedTags]'s class KDoc for the full rationale.
     */
    private fun recomputeProfile(spec: DeckWizardSpec, template: DeckTemplate, mainboardSoFar: List<DeckEntry>): DeckProfile {
        val explicitSeedTags = DeckIdentitySeedTags.forArchetype(
            archetype = template.archetypeInfo.archetype,
            themes = template.archetypeInfo.themes,
            tribe = spec.strategyProfile.tribe,
        )
        // Defense in depth (RUN 3b QA fix): commander is only meaningful for DeckFormat.COMMANDER
        // (see DeckWizardSpec's KDoc) -- gate it here too, mirroring the same format check already
        // applied to spec.commander everywhere else in this class (analyzeCollection, validate,
        // fillLands' landColorIdentity). The wizard VM's onSelectFormat now resets selectedCommander
        // on a format switch so a non-null commander on a non-Commander spec is unreachable via
        // normal UI flow, but this use case must stay correct independent of that VM-side fix.
        val commanderSeed = spec.commander.takeIf { spec.format == DeckFormat.COMMANDER }
        val inferredSeedTags = inferDeckIdentityUseCase(inferenceSeedCards(commanderSeed, mainboardSoFar)).seedTags
        val seedTags = (explicitSeedTags + inferredSeedTags).distinct()
        return deckScorer.profile(
            mainboard = mainboardSoFar,
            format = spec.format,
            colorIdentity = template.colorIdentity,
            seedTags = seedTags,
        )
    }

    /** Mirrors [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator
     * .inferenceSeeds] EXACTLY: the commander plus the deck's own highest-weight identity cards
     * (most STRATEGY/ARCHETYPE/TRIBAL tags), capped so one off-theme card can't skew the seed. */
    private fun inferenceSeedCards(commander: Card?, mainboardSoFar: List<DeckEntry>): List<Card> {
        val ranked = mainboardSoFar
            .map { it.card }
            .filter { it.scryfallId != commander?.scryfallId }
            .map { card -> card to identityTagCount(card) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_SEED_CARDS)
            .map { it.first }
        return (listOfNotNull(commander) + ranked).distinctBy { it.scryfallId }
    }

    private fun identityTagCount(card: Card): Int =
        (card.tags + card.userTags).count { it.category in IDENTITY_CATEGORIES }

    private companion object {
        const val COLOR_DISCIPLINE_LIMIT = 2

        /** Bounds the cost of each Motor A call within the loop (plan §5 Phase 2: "batch per
         * skeleton role/category, never per single card"). */
        const val MOTOR_A_BATCH_LIMIT = 24

        /** Hard iteration cap on [runMotorALoop] -- defensive only; the loop already breaks the
         * instant a batch places nothing or clears no candidate above the floor. */
        const val MAX_ITERATION_SLACK = 20

        /** Workstream 4.1: the max Scryfall results page [runScryfallBackstopLoop] pages forward to
         * on a given query set before trying one relaxed round (see that function's KDoc). */
        const val MAX_BACKSTOP_PAGES = 5

        /** Hard iteration cap on [runScryfallBackstopLoop] -- defensive only, mirrors
         * [MAX_ITERATION_SLACK]'s role for [runMotorALoop]; the loop already returns the instant a
         * relaxed, fully-paged pass clears no candidate above the floor. */
        const val MAX_BACKSTOP_ITERATION_SLACK = 30

        /** Workstream 9.4: the max Scryfall results page [fillFixingLandsFromScryfallBackstop]
         * pages forward to before giving up (no relaxed-round lever like the nonland backstop --
         * a single `t:land id<=… produces>=2` query is broad enough that a niche identity + this
         * small a page budget is the honest failure mode, not a missing relaxation pass). */
        const val MAX_FIXING_LAND_BACKSTOP_PAGES = 3

        const val SIGNATURE_CARD_COUNT = 3

        /** Mirrors [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator]'s own
         * seed-inference cap exactly (B3 coherence). */
        const val MAX_SEED_CARDS = 8
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)
    }
}
