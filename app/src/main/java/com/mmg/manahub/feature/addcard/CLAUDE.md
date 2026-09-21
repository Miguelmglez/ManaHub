### Add Card (`feature/addcard/`)
Search-first entry point (redesigned 2026-07-13). Idle state (no query, no filters) shows a
`LazyVerticalGrid` discovery surface of shuffled cards from one Scryfall set at a time
(`GetSpotlightFeedUseCase`, paginates to the next set on scroll) — mirrors Home's Discover Cards
widget, not a text list. The camera scanner is a `FloatingActionButton` on the `Scaffold`, not an
inline list item or a `SearchSurface` trailing icon. The search field's trailing icon (query empty)
is the flag emoji for the active search language (`CardConstants.getFlag`/`languages`) and opens a
`ModalBottomSheet` (`LanguageSelectorSheet`) — this replaced the old `ManaHubSelector` dropdown row.
"Recent searches" was removed end-to-end (not just hidden): `GetRecentSearchesUseCase`/
`SaveRecentSearchUseCase`/`ClearRecentSearchesUseCase` and `RecentSearchRepository` are gone: only
delete a use case's Koin binding — always grep the feature's OWN `di/` module too (`AddCardKoinModule.kt`
had its own reference the main task list didn't mention). Both the spotlight grid tiles and the
results-list `SearchResultItem` thumbnails participate in the shared-element transition into
`CardDetailScreen` via the shared key `"card-image-${card.scryfallId}"` — any new card-image surface
in this screen must reuse that exact key format to stay connected to the transition.
→ memory: `project_addcard_redesign_2026-07-13`

**"Select multiple" mode (2026-09-21).** Top-bar `Checklist` toggle (or nav args) turns AddCard into a
multi-select browser. **Tap = toggle selection, long-press = CardDetail** (same shared-element key);
normal mode keeps tap → CardDetail with no long-press. "Selected" means *the scryfallId is in the
queue*: tapping a selected card removes EVERY queue entry with that id, scanned ones included. The
bottom `MagicCtaButton` ("Proceed with N selected") opens the shared `CardQueueSheet`
(`core/ui/components/`, stateless — also used by the Scanner).
- **One queue, one instance.** `CardQueueRepository` (commonMain) is a single `single<CardQueueRepository>`
  in `CoreBridgeKoinModule`, bridged into Hilt from `GlobalContext` so the Hilt `ScannerViewModel` and the
  Koin `AddCardViewModel` share it. Never construct a second one. It persists the legacy scanner payload
  (`scanner_prefs` / `scanner_queue_v1`, same JSON keys) so existing scan queues survive. The repository
  is NOT synchronized: mutate it on the main thread (AddCard commits run in `appScope` +
  `Dispatchers.Main.immediate` so they outlive the screen). Commits go through `CardQueueActions`
  (→ `CommitScannedCardsUseCase`, so AddCard adds count as scans for XP).
- **Deck source mode is local-only.** `Screen.CollectionAddCard.createRoute(multi, source, sourceId)`
  (`Source.DECK` = local deckId, `Source.COMMUNITY` = Archidekt id; no args → plain `collection/add`).
  The VM loads the deck once through the batch path (`warmCacheForIds` + `getCardsByIds`), then the
  search bar and Advanced Search filter that list locally (`AdvancedSearchCardMatcher`, lenient) — zero
  Scryfall search calls while a deck list is active. "Clear deck cards" returns to Scryfall search and
  sets `AddCardRestorableState.isDeckSourceCleared` (SavedStateHandle-backed) so a process restore does
  not reload the deck from the still-present nav args. `AddCardLaunchArgs` stays a pure data class.
- Telemetry: `AddCardTelemetry` (`addcard_multiselect_*` breadcrumbs, count buckets only; deck-source load
  failure → `recordSafeNonFatal`). Tests need `mockkStatic(FirebaseCrashlytics::class)`.
→ memory: `project_addcard_multi_select_2026-09`
