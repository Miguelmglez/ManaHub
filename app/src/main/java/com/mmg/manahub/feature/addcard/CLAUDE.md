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

