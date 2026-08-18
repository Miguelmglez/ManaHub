### Competitive (`feature/competitive/`)

A richly-presented, **100% static catalog of precise deep links** into external MTG competitive
sites (tournament decklists, trending decks/cards, standings, power rankings, Limited card
ratings, store/event hubs, live streams, deck-building tools) — see
`CompetitiveResourceCatalog.kt` — plus an official-event-locator deep link and a Pro Tour
news/videos filter over the existing News cache.

**2026-08 redesign — 3 tabs + accordion, not a flat scroll.** `CompetitiveScreen.kt` groups the 8
`ResourceCategory` values into 3 `CompetitiveTab`s (`CompetitiveUiState.kt`): **Tournaments**
(`TOURNAMENT_DECKLISTS`/`STANDINGS_STATS`/`POWER_RANKINGS`), **Metagame**
(`TRENDING_DECKS_CARDS`/`LIMITED_RATINGS`), **Hub** (`STORES_HUBS`/`WATCH_LIVE`/`DECK_BUILDING`).
`ResourceCategory` itself is unchanged — the grouping lives in `CompetitiveTab.categories`. The
format-selector chip row only renders on Tournaments/Metagame (`CompetitiveTab.showsFormatSelector`
is `false` for Hub, whose categories never branch on `CompetitiveFormat` in
`CompetitiveResourceCatalog`) — fixes the original flat-scroll design's biggest usability problem,
where the selector sat above all 8 categories but only ever visibly changed 2 of them. Each
category is a collapsible accordion (`CompetitiveUiState.expandedCategories`, in-memory only,
defaults to the first category of every tab expanded); within an expanded section the
highest-priority resolved link renders as a larger `HeroResourceCard`, the rest as compact
`ResourceCatalogCard` rows. Pro Tour news is an image-forward horizontal strip (`ProTourNewsCard`,
`coil3.compose.AsyncImage` on `NewsItem.imageUrl`, gracefully omitted when null) instead of stacked
full-width rows. `CompetitiveViewModel.onTabSelected`/`onCategoryToggled` follow the existing
`onFormatSelected` state-update + Crashlytics breadcrumb/custom-key pattern.

**`GetProTourContentUseCase`'s keyword filter dropped the bare `"Metagame"` branch** (2026-08): it
matched almost any generic deck-tech/metagame-report article, not just actual Pro Tour coverage,
making the filter feel broken. The regex is now `Pro Tour|PT [A-Z]{3}|Regional Championship|
World Championship|Arena Championship|Qualifier` — do not re-add a bare `Metagame` alternation.

**Gated behind `competitiveEnabledFlow` (default `false`)** in `UserPreferencesDataStore.kt` —
same reactive-DataStore-flag pattern as `gamificationEnabledFlow`. This gates the Home entry-point
tile and navigation; the catalog screen itself has nothing left to gate server-side (see the pivot
below) since it makes no network calls at all. Flip the flag locally to test.

**2026-08 pivot — ZERO live/REST calls to third-party MTG data services.** After Miguel reviewed
17lands/TopDeck/Spicerack's policies himself, he decided the app must not scrape or depend on any
"unstable" third-party API. The previous live-data phase (weekly constructed-metagame rankings +
LIVE 17lands Limited-ratings fetch, backed by the `cloudflare/manahub-competitive` Worker) was
REMOVED from the screen/ViewModel. Every card the user sees now opens a precise, pre-researched
URL (never a bare homepage) in a Custom Tab — same `openUrl()` pattern already used by News and
the event locator. There is no runtime liveness check of any kind (no HEAD probes, no pings): link
health is a manual/editorial concern — update `CompetitiveResourceCatalog.kt` if a site
restructures — never something code detects at runtime.

**The old Worker + its entire `commonMain` data layer stay in the codebase, completely untouched
and DORMANT**, in case Miguel revisits live data sourcing later: `cloudflare/manahub-competitive/`,
`shared/core-data/.../{remote,cache,repository,network}/Competitive*`,
`shared/core-domain/.../repository/CompetitiveRepository.kt`, `shared/core-model/.../Competitive.kt`,
and `feature/competitive/data/Competitive{Meta,LimitedRatings}CacheImpl.kt` are all still real code
— `CompetitiveKoinModule.kt` still registers every one of those bindings — but nothing in
`CompetitiveViewModel` calls them anymore (it no longer takes a `CompetitiveRepository` or
`ImportDeckCardsUseCase` dependency at all). Do not delete any of it without asking first; do not
wire it back up without asking first either.

**`CompetitiveRepositoryImpl` lives in `shared/core-data` commonMain and cannot depend on the Room
DAOs directly** — Room has no wasmJs target, and `:app` depends on `:shared:core-data`, never the
reverse. It depends on the commonMain interfaces `CompetitiveMetaCache`/`CompetitiveLimitedRatingsCache`
(`shared/core-data/.../cache/CompetitiveCache.kt`), implemented on the Android side by
`CompetitiveMetaCacheImpl`/`CompetitiveLimitedRatingsCacheImpl` (`feature/competitive/data/`)
wrapping the real Room DAOs — same split `CommunityAggregateCache`/`CommunityAggregateCacheImpl`
already establishes for the sibling Community feature. Mirror this, don't reach for the DAO type
directly from commonMain, IF this data layer is ever reactivated. → memory:
`feedback_commonmain_repo_cannot_take_app_module_room_dao`

**The "Import to Deck Studio" CTA was removed** along with the live weekly-meta section it was
attached to (there is no `RepresentativeDeck` data once nothing calls
`CompetitiveRepository.getWeeklyMeta`). `ImportDeckCardsUseCase`/`ImportSource.PastedText`
themselves were NOT touched — they remain the shared Community Decks import pipeline, used
elsewhere in the app; only this feature's call site into them was deleted.

**Pro Tour news filtering is a pure in-memory keyword predicate**
(`GetProTourContentUseCase`, `shared/core-domain/.../feature/news/domain/usecase/`) over whatever
the existing News repository already has cached — zero schema/Room changes. There is no per-source
tag/category filter field on `ContentSource`; if a future feature needs one, that's new
functionality, not something to assume exists.

**Home entry point is a self-hiding widget**, not baked into `HomeUiState`'s combine (that
combine is flagged elsewhere in this codebase as the most error-prone surface in Home) — it's an
independent `StateFlow` gated by `competitiveEnabledFlow`, same pattern as the `trendingFlow`/
`dailyPuzzleFlow` widgets. Follow that precedent for any future flag-gated Home tile, don't add a
new field to the big combine.
