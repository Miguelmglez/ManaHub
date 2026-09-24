# Browse Inspirations rework — implementation plan

Branch: `feature/deck-wizard`. Status: IMPLEMENTED 2026-09-24 — Runs 1-4 landed; NOT yet compiled or
tested (see §5), owner must run the local gate before merging.
This file is gitignored (`/docs/plans/*`, AI-planning-doc rule). The durable record goes to
`app/src/main/java/com/mmg/manahub/feature/decks/CLAUDE.md` once shipped.

## 0. Goal

A user opens an EMPTY 60-card deck in Deck Studio and taps "Browse inspirations". A bottom sheet
lets them explore the synergies and combos their OWN collection supports, pick a starting set of
cards, and hand that set to the Deck Wizard, which opens directly at "Pick a strategy" (as if the
user had chosen "Build from seed" → "Start from cards" → confirmed their picks).

## 1. Decisions (locked)

| # | Decision |
|---|----------|
| D1 | Available ONLY for 60-card formats (`DeckFormat.isSixtyCardConstructed`: Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Casual) and ONLY while the deck is empty (empty-state card + overflow item). Hidden for Commander/Commander Casual/Draft and for non-empty decks. |
| D2 | Two tabs kept (Strategies / Combos), rebuilt with the shared UI kit (`ManaTabRow`, `CardRow`, `MagicCtaButton`, `EmptyState`, `InlineErrorState`, `MagicLoadingSpinner`, `SectionHeader`, `CardSectionRow`, `DeckCardQueueSheet`, `MagicCardInspectionOverlay`). |
| D3 | Strategies lists ALL synergies of the user's collection using the SAME vocabulary as the Analysis/Wizard "Synergy" section: engines (`engine:<axis>:producers` / `engine:<axis>:payoffs`) and tribes (`tribe:<x>`). Visibility = the Synergy section's D3 rule (COMPLETE, or MISSING_PAYOFFS / MISSING_PRODUCERS when one side reaches half its ideal). Tribes need ≥ 8 distinct owned member cards. Two sections — Engines, Tribes — ordered most-buildable first. No plan/strategy filtering (unlike the wizard). |
| D4 | The FlowRow/LazyRow of pickable tiles under the search bar is removed. |
| D5 | Strategies search bar filters ONLY the user's owned (format-legal) cards. While typing, the synergy list is replaced by a `CardRow` result list. Tapping a result PINS it in place of the search bar (a `CardRow` with a -/+ stepper to add it to the selection, plus a clear ✕ that restores the search bar). A pinned card only FILTERS the synergy list to the synergies containing it (it is not auto-added). |
| D6 | Every synergy card thumbnail opens `MagicCardInspectionOverlay` with "Add" / "Cancel" actions — the exact pattern of the wizard's "Pick your cards" step (`WizardCardInspectionHost`). |
| D7 | Each synergy side has a "Browse for X" `MagicCtaButton` opening the existing `CardSearchSheet` pre-filtered (`SectionSearchQuery.toAdvancedQuery` + `SectionMembership.predicate`), Collection + All Cards tabs, opening on Collection. In this mode, +/- write to the inspirations SELECTION, never to the deck. |
| D8 | ONE selection shared by both tabs (product-owner revision of the original "independent tabs" idea). Bottom of the sheet: an "N cards selected" pill (opens a `DeckCardQueueSheet` to change copies / remove) and, when N > 0, a "Start building the deck" CTA below it. |
| D9 | Dismissing the Browse inspirations sheet clears the selection, both pins and both searches. |
| D10 | Combos tab: a search bar over owned cards + an initial empty state "Search for a card in your collection to explore possible combos". Typing shows `CardRow` results; picking one pins it (same component as D5) and ONLY THEN fetches the combos that include that card (one Commander Spellbook request per page, cached 7 days). The whole-collection `find-my-combos` call is removed. |
| D11 | Combos shows EVERY combo Spellbook returns for the pinned card, regardless of ownership, grouped by missing pieces: "Ready to build" (0 missing) / "1 card away" / "2+ cards away". Two non-ownership filters stay: combos with a card illegal in the deck's format are hidden (Casual = no filter), and combos that require a card in the command zone (`mustBeCommander`) are hidden (60-card decks have no command zone). |
| D12 | A combo item's CTA is "Add to selection" (replaces "Build this"): every piece NOT yet selected is added at 1 copy; pieces already selected keep their current quantity (never incremented, never removed). The user tunes copies in the selection sheet. When every piece is already selected the CTA reads "Added to selection". Several combos + individual cards can be combined freely. |
| D13 | Combo card tiles always render: owned pieces resolve from the local collection; the rest resolve in ONE batched Scryfall `/cards/collection` lookup (`CardRepository.lookupCardsByIdentifiers`, ≤ 75 names per call, through the shared request queue). Missing pieces are visually distinguished (dimmed + "Missing" badge). Tile tap = the same inspection overlay with "Add". |
| D14 | "Start building the deck" navigates to the wizard with a new `seedCards` nav arg (`scryfallId:quantity`, `|`-joined). The wizard starts in the CARDS flow directly at `WizardPhase.STRATEGY`; Back returns to SEED_PICK ("Pick your cards") with the seeds loaded, then ENTRY. |
| D15 | Selection rules mirror the wizard's `onAddSeed` exactly: format legality via `isLegalForFormat`, name-keyed per-card cap `CopyPolicy.maxSeedCopies`, total cap 60 copies; rejections show the wizard's own toasts. Unowned cards (All Cards tab, missing combo pieces) are allowed — the wizard keeps unowned seeds (D7 of the wizard state doc). |
| D16 | Flags `FeatureFlags.Decks.DISCOVERIES_V2_ENABLED` (entry points) and `DISCOVERY_BUILD_HANDOFF_ENABLED` ("Start building the deck") flip to `true` at the end. |

## 2. Architecture

Pure logic lands in `commonMain` (KMP directive); the Studio presentation stays in `:app` because
`feature/decks/presentation` is not migrated yet (Hilt-free, Koin VM, same as today).

### 2.1 Domain — `:shared:core-domain` commonMain

1. **`SynergyEngineState.resolve(...)`** (DeckAnalysis.kt): extract the D3 visibility rule out of
   `AnalysisEngine.evaluateSynergy` into one function; `AnalysisEngine` calls it (pure extraction,
   golden/calibration/corpus suites stay byte-identical). The discovery use case calls the same
   function — no second heuristic.
2. **`feature/decks/domain/inspirations/DiscoverCollectionSynergiesUseCase`** (replaces
   `DiscoverSynergiesV2UseCase` + `DiscoverySearchFilter`):
   - input: collection snapshot (`List<UserCardWithCard>`) + deck `DeckFormat`.
   - pool: format-legal owned cards (`isLegalForFormat`), deduped by name, owned copies summed by name.
   - engines: `SynergyGraph.cardAxisProfile(card, SIXTY)` per card (O(n), no edge graph); axis
     producers/payoffs = cards with `produces[axis] > 0` / `consumes[axis] > 0` — the SAME test
     `SectionMembership.enginePredicate` applies, so a "Browse for X" Collection result set equals
     what the card shows. Generic `TRIBE*` axes are skipped (tribes are handled below).
     Payoff-optional axes (`MILL_OPP`, `LOCK`) render producers-only.
     Rule copies = Σ `CopyPolicy.maxPlaceable(card, format, owned)`; ideals =
     `SynergyGraph.axisIdeals(ArchetypeFormat.SIXTY)`.
   - tribes: `TribeDeriver.tribeKeys(card)` membership (= `SectionMembership` `tribe:<x>` predicate);
     members = own subtype (`subtypeKeys`), payoffs = `payoffTribeKeys`; visible at ≥ 8 members;
     payoffs listed first.
   - ordering: engines COMPLETE first, then by min(side copies / side ideal) desc, then distinct
     card count; tribes by members desc, then payoffs desc. Cards within a side: confidence desc,
     rarity desc, name.
   - `CollectionSynergies.containing(cardName)` pure filter for the pinned card (D5).
3. **`feature/decks/domain/inspirations/InspirationSelection`** — pure reducer over
   `List<InspirationPick(card, quantity)>`: `add`, `decrement`, `remove`, `addMissing(cards)` (D12),
   returning a typed outcome (`Added`, `Unchanged`, `Illegal`, `CopyCap(max)`, `TotalCap(cap)`).
   Name-keyed for non-basics, scryfallId-keyed for basics (mirrors `DeckWizardViewModel.onAddSeed`).
4. **Combos for one card**:
   - `CommanderSpellbookApiContract.findVariantsWithCard(cardName, limit, offset)` →
     `GET variants/?q=card="<name>"&limit=&offset=` (DRF page: `count`, `next`, `results`).
     `VariantDto` gains defensive `legalities: Map<String, Boolean?>?`.
   - `CommanderSpellbookRepository.findCombosWithCard(cardName, page)` → `DataResult<CardComboPage>`
     with the existing `ComboCache` (key `combo-card:<fnv(name|page)>`), 7-day TTL, stale-then-empty
     degrade, never throws.
   - `FindCombosWithCardUseCase(repository)(cardName, format, page)`: drops `mustBeCommander`
     variants, drops variants whose Spellbook legality for the format is `false` (Casual: none),
     returns `CardComboPage(combos, totalCount, hasMore)`.
   - `ComboOwnership.missingPieces(combo, ownedNames)` pure helper (case-insensitive, front-face
     aware for `A // B` names) + grouping into the three D11 sections.
   - `FindCombosUseCase` (whole-collection `find-my-combos`) loses its only consumer → deleted with
     its test. `CommanderSpellbookRepository.findCombos` stays (tested infrastructure; follow-up).

### 2.2 Wizard hand-off — `:app`

- `Screen.DeckWizard`: new optional `seedCards` query arg; `createRoute(seedCards: List<WizardSeedArg>?)`;
  `navArgument("seedCards")` in `AppNavGraph`; the legacy name-based `seeds` arg stays untouched.
- `DeckWizardViewModel.init`: `seedCards` non-empty on a 60-card format ⇒ `entryFlow = CARDS`,
  `phase = STRATEGY`, strategy list loading; after the collection snapshot loads, resolve each id
  (owned snapshot → `CardRepository.getCardById`) and add it `quantity` times through `onAddSeed`
  (all validation reused), then `recomputeStrategyRecommendations()`. Zero resolved seeds ⇒ SEED_PICK
  + toast. The resolve job is cancelled by `cancelDirectionSearchJobs`.
- `DeckStudioScreen` gains `onNavigateToWizardWithSeeds(deckId, format, seedCards)` wired in `AppNavGraph`
  (`replaceConfirmed = false`: only reachable on an empty deck; the wizard re-checks at persist).

### 2.3 Deck Studio — `:app`

- `DeckStudioUiState.inspirations: InspirationsUiState` (one sub-state replacing the 12 flat
  discovery/combo fields): open flag, tab, selection, queue-sheet flag, strategies (synergies,
  loading/error, query, results, pinned card), combos (query, results, pinned card, grouped items,
  loading/error, paging, `comboCardsByName`).
- `DeckStudioViewModel`: synergies computed lazily on first open (collection snapshot + format),
  kept for the session; search over owned legal cards (name contains, distinct by name, capped);
  pin/unpin; combos fetch + load-more; batched card resolution (D13); selection mutations with
  toasts; `closeInspirations()` resets everything (D9); `inspirationSeedCards()` for the hand-off.
- New files under `feature/decks/presentation/inspirations/`: `BrowseInspirationsSheet.kt` (sheet
  scaffold, tabs, selection footer, queue sheet, inspection host), `InspirationsStrategiesTab.kt`,
  `InspirationsCombosTab.kt`, `InspirationComboCard.kt`, `CollectionCardPicker.kt` (search field /
  pinned `CardRow`). `SynergyEngineCard` becomes `internal` and accepts a nullable payoff side.
- `DeckStudioScreen`: removes `InspirationsSheetContentV2`/`StrategiesTabContent`/`CombosTabContent`;
  the sheet is gated on `isDestinationResumed` (so a card-detail navigation from `CardSearchSheet`
  does not leave it floating above the NavHost; state lives in the VM); the existing Build-tab
  `CardSearchSheet` instance gains an `inspirationsBrowse` mode (rememberSaveable) routing
  onAdd/onRemove to the selection and remapping row quantities to selected copies.
- Deleted: `DiscoveryRow.kt`, `ComboRow.kt`, `DiscoverSynergiesV2UseCase` (+ Koin binding, tests),
  `DiscoverySearchFilter`, unused strings.

### 2.4 Telemetry (no PII — ids/counts/enums only)

Breadcrumbs: `deck_inspirations_opened`, `deck_inspirations_tab_selected` (+key tab),
`deck_inspirations_card_pinned` (+key tab), `deck_inspirations_synergies_loaded` (+engine/tribe
counts), `deck_inspirations_browse_opened` (+section id), `deck_inspirations_selection_added`
(+key source: browse/thumbnail/pinned/combo), `deck_inspirations_combos_loaded` (+count, page),
`deck_inspirations_start_building` (+distinct cards, copies), `deck_inspirations_dismissed_with_selection`
(abandonment). Non-fatals: `deck_inspirations_synergies_failed`, `deck_inspirations_combos_failed`,
`deck_inspirations_combo_cards_unresolved` (count only), `deck_wizard_seed_cards_handoff_unresolved`.
Removed (dead with the old code): `deck_studio_discovery_v2_seeding_failed`, `deck_studio_combos_load_failed`.

## 3. Runs

1. **Domain** (commonMain + commonTest): D3 extraction, discovery use case, selection reducer,
   combos-for-card (API/DTO/repository/use case/ownership), Koin bindings.
2. **Wizard hand-off**: route + nav arg + VM init + tests.
3. **Studio**: state, VM, new sheet UI, CardSearchSheet mode, entry gating, deletions, strings,
   telemetry, flags.
4. **Verify + record**: targeted tests, graph/doc updates, decks `CLAUDE.md`, pre-push secret gate,
   commit + push.

## 4. Tests (targeted)

- commonTest: `DiscoverCollectionSynergiesUseCaseTest` (visibility rule, producer-only axes,
  tribe threshold, legality filter, name dedupe, `containing`, parity with `SectionMembership`),
  `InspirationSelectionTest` (caps, name keying, basics, `addMissing` idempotence),
  `FindCombosWithCardUseCaseTest` (commander-only + legality filters), `ComboOwnershipTest`,
  `CommanderSpellbookRepositoryImplTest` (+ per-card cache/API/degrade cases),
  `AnalysisEngine` suites unchanged (extraction must be byte-identical).
- app unit tests: `DeckStudioViewModelTest` (open/close reset, pin filters, selection, combos
  fetch + resolution, seed args), `DeckWizardViewModelTest` (seedCards ⇒ STRATEGY with quantities,
  unresolved ⇒ SEED_PICK), `DeckStudioScreenLogicTest` (entry gate: 60-card + empty only).

## 5. Environment constraints (this cloud session)

- No Android SDK (`dl.google.com` blocked) ⇒ `:app` cannot compile/test here. Only the JVM targets
  of the shared modules can run (`:shared:core-domain:jvmTest`, `:shared:core-data:jvmTest`).
- `backend.commanderspellbook.com` is blocked ⇒ the `variants` endpoint shape is written from the
  public OpenAPI knowledge, parsed defensively (`ignoreUnknownKeys`, nullable/defaulted fields) and
  must be smoke-tested on a device.
- Owner must run locally: `./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.*"`
  and `./gradlew assembleDebug`.

## 6. Follow-ups (not in scope)

- Delete `CommanderSpellbookRepository.findCombos` (whole-list `find-my-combos`) if no consumer
  appears (e.g. a future "combos in this deck" Analysis section could reuse it).
- Move the inspirations presentation to `commonMain` when `feature/decks` presentation migrates.
