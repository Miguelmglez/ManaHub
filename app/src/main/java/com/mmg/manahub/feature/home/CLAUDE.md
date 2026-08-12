### Home dashboard (`feature/home/`)
Free-first, account-enhanced start screen. Fully implemented (2026-06-08). Must-know:
- **Start destination is `Screen.Home`** (not `Screen.Collection`). BottomBar is 3-slot: [Home] [⚔ FAB] [Library].
- `HomeViewModel` uses `combine(8 flows).stateIn(WhileSubscribed(5_000))` — no new DB tables. `avatarUrlFlow` MUST be subscribed/mocked in tests (blocks `accountFlow` combine if missing).
- Quick Start: 4 shortcuts persisted in DataStore via `UserPreferencesDataStore.observeQuickStartActions()`; partial-restore pads with defaults rather than discarding valid entries.
- Account nudge: 5-priority system (ACTION_REQUIRED > COLLECTION_MILESTONE ≥10 > DECK_MILESTONE ≥2 > GAME_MILESTONE ≥3 > SYNC_PENDING); 48h cooldown; ACTION_REQUIRED bypasses cooldown.
- `onBackHome`: `popUpTo(0) { inclusive = true }` (not `popUpTo(Screen.Collection.route)`) to avoid back-stack corruption on fresh install.
- → memory: `project_home_dashboard_redesign`, `feedback_home_stateIn_test_pattern`

### Home widget board (`feature/home/`, overhauled 2026-07-13 — no-stub rule now in force)
The dashboard is a **fully customizable widget board** (`LazyVerticalGrid` of 2 cols). 17 widget
types in `HomeWidgetType` (each carries `persistedId`, `defaultTitleRes`, `supportedSizes`,
`category`, `audience`, `isAlwaysPresent`). Layout = ordered `List<WidgetInstance>(type,size)`.
**THE NO-STUB RULE (Home invariant, added 2026-07-13): a widget/slide may only ship if its data
path is real end-to-end (upstream source → repo flow → ViewModel → UI); anything that can't be
wired must be deleted (composable + model + copy + DI binding), never left as a placeholder.**
Every widget/slide is real as of the 2026-07-13 overhaul except `RULES_TIP` (intentionally static
content). Must-know:
- **Layout persists in DataStore only** (`home_widget_layout` = ordered `"persistedId:SIZE"` tokens;
  unknown id/size tokens are silently skipped on decode; empty → auth-appropriate default). No new
  Room tables. `homeLayoutFlow(default)` takes the default as a param so the DataStore stays unaware
  of auth; the VM picks `defaultLayoutSignedIn/Out` via `isAuthenticatedFlow.flatMapLatest` — the
  2026-07-13 overhaul replaced both default lists (Phase 2.3); `TRENDING_COMMANDERS` is intentionally
  NOT in either default (gallery-only, board-length discipline).
- **Sizes are vestigial but only PARTLY cleaned up**: every widget renders at a single MEDIUM width
  (`CARD_OF_THE_DAY` now only declares `WidgetSize.MEDIUM`, matching the rest); the board's old
  bounds-registry/drag-hit-testing API (`HomeWidgetContainer.onRegisterBounds`, module-level
  `findTargetIndex()`, `HomeScreen`'s `itemBounds` map) was DELETED as dead code — the gallery sheet
  owns add/remove/reorder via its own local drag state, the board itself is static. Removing
  `WidgetSize` from `WidgetInstance`/`PersistedWidget` entirely (and making the decoder legacy-token
  tolerant) is DEFERRED — do this as its own tested pass, not bundled into a larger change.
- **ViewModel combine arity**: slices are bundled (`CoreSnapshot` 7 flows, `DataBundle{layout,stats,
  discover,social,gamification,recentlyAdded}` 6 flows) via the vararg-destructure `combine(vararg){
  args -> @Suppress("UNCHECKED_CAST") ... }` pattern once a bundle exceeds 5 flows (Kotlin's typed
  combine overloads cap at 5); inner sub-bundles (`socialSnapshotFlow`'s `TradesSnapshot`/
  `SocialExtras`) stay on the typed non-vararg overloads. `performanceFlow` MUST be declared before
  `statsSnapshotFlow` (property init order). Every data flow is `.catch{emit(empty/null)}`-isolated
  so one source failing never collapses the board.
- **Every data source is real** (2026-07-13 overhaul, Phase 1); Trades Hub wired to
  `TradesRepository`/`OpenForTradeRepository`/`TradeSuggestionsRepository`; `friendCount` wired to
  `FriendRepository.observeFriendCount()`; tournament round wired to a read-only
  `TournamentRepository.observeCurrentRound()`. Account-gated widgets gate on
  `HomeUiState.authResolved` FIRST (never flash the placeholder while the session is still
  `SessionState.Loading`), then `AccountGatedPlaceholder` (→ `CreateAccount`) when `!isAuthenticated`.
  `CommunityStatsRepositoryImpl`/`ArchidektTrendingRepository` are DORMANT Koin registrations since the
  2026-07-18 widget board overhaul (see below) — Home no longer consumes either.
- `HomeWidgetHost` dispatches type→composable; `HomeWidgetContainer` just wraps it (no bounds
  registry, see above); all widgets share `WidgetShell` (flat Column, no card surface). No
  `success`/`error` tokens exist — win=`lifePositive`, loss=`lifeNegative`. `WidgetSectionHeader`'s
  icon+label area is an optional ≥48dp tap target (`onClick`/`onClickLabel`) resolved per widget type
  by `widgetHeaderTitleClickAction` — same destination as that widget's internal "See more" tile.
- **`RECENTLY_ADDED`** (new 2026-07-13): newest local collection additions,
  `UserCardRepository.observeRecentlyAdded(10)` — no Room migration (`created_at`/`updated_at`
  already existed on `UserCardCollectionEntity`). Keyed by the `user_card_collection` row id (NOT
  scryfallId — duplicate copies of the same card would collide on scryfallId). Nullable
  (`List<RecentlyAddedCard>?`, null = loading) since the 2026-07-18 overhaul, same as `decks`,
  `friends`, `recentTrades`, `tradeSuggestionPreviews`, `openForTradePreview`.
- **`TRENDING_COMMANDERS` (Deck Doctor Community/Archetype plan Phase 5)** and **`COMMUNITY_DECKS`**
  (2026-07-18 overhaul, TASK 5b — a category-selectable Archidekt deck browser, `WidgetAudience.ALL`):
  both keep their data OUTSIDE the `HomeUiState` combine chain (independent `HomeViewModel.trendingFlow`
  / `communityDecksFlow`+`communityDecksCategoryFlow` `stateIn`s, threaded as extra params through
  `HomeScreen`→`HomeWidgetContainer`→`HomeWidgetHost`) — see `project_community_hub_seedbuild_trending`
  memory for why. `TRENDING_COMMANDERS` is silently hidden (never an error state) on any
  failure/flag-off. Distinct backends: Cloudflare Worker (`TRENDING_COMMANDERS`) vs. Archidekt search API
  directly via `SearchCommunityDecksUseCase` (`COMMUNITY_DECKS`) — never conflate the two.
- **`SOCIAL_HUB` was retired 2026-07-18**, split into `FRIENDS` (friend list + pending-request
  headline) + `COMMUNITY_DECKS`; its MostWishlisted/Milestones slides were dropped (low value), its
  ActiveTournament slide moved into `GAME_STATS_HUB`. A legacy persisted `"social_hub"` layout token
  is migrated to `[FRIENDS, COMMUNITY_DECKS]` at decode time
  (`WidgetInstance.toInstancesWithMigration()`), never silently dropped.
- **Trades Hub** (2026-07-18 redesign): real card thumbnails, not counts — Suggestions/Open-for-Trade
  render matched/owned cards via `DiscoverCardThumb`. `TradesRepository.refreshProposals()` only ever
  fetches proposal METADATA (never items) — `HomeViewModel.hydrateTradeItemCounts()` fans
  `refreshProposalThread()` out over the newest ≤5 distinct threads after the metadata refresh
  succeeds, or `TradeSummary.latestItemCount` silently reads 0. See
  `feedback_trades_refresh_proposals_metadata_only` memory.
- **First Steps carousel**: tap = CTA only (2026-07-13 — no longer also dismisses); a dedicated
  top-right dismiss affordance (`Icons.Default.Close`, ≥48dp) calls the same `SkipFirstStep`/
  `observeSkippedFirstSteps` DataStore mechanism as before. Most step conditions are data-driven
  (see `ALL_FIRST_STEPS` in `FirstStepItem.kt` for the per-step DATA-DRIVEN/DISMISS-ONLY doc). The
  "You're all set!" completion card (empty-`Welcome` hero) shows ONCE — gated by
  `UserPreferencesDataStore.firstStepsCompletionSeenFlow`/`markFirstStepsCompletionSeen()` — then the
  hero falls through to `Summary` permanently (2026-07-18).
- Top bar = deterministic, MTG-flavored greeting pool (4 time bands × 3-4 variants, seeded by epoch
  day — `HomeScreen.resolveGreetingVariant`, 2026-07-18) + avatar (→ `OpenProfile`). A board-level
  loading skeleton (`HomeBoardSkeleton`) renders while `HomeUiState.isLoading`, replacing the old
  empty/partial-grid flash.
- → memory: `project_home_widget_board`, `project_home_feature_overhaul_2026-07-13`,
  `feedback_home_dashboard_audit_fixes_2026-07-13`, `feedback_archidekt_trending_stale_cache_bug`,
  `project_home_widget_board_overhaul_2026-07`, `feedback_trades_refresh_proposals_metadata_only`

