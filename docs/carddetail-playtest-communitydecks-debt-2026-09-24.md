# CardDetail + Playtest + CommunityDecks audit — learnings and open debt (2026-09-24)

Edge-case audit of `feature/carddetail`, `feature/playtest` (+ its `:shared` domain use cases) and
`feature/communitydecks` (+ `CommunityDecksRepositoryImpl`). All fixes landed on `feature/deck-wizard`.
The main memory store was not reachable from the cloud session, so the learnings below still need to be
copied into memory files per the `memory-protocol` skill.

## 1. Learnings to move into memory
- **A state field no composable renders is a silent failure.** `CardDetailUiState.error` was written by
  ~15 paths (tag edits, deletes, trade offers, adds) but never displayed; an initial-load failure rendered
  a blank screen. Every VM failure path must end in a rendered state or a toast.
  → `feedback_unrendered_error_state` (new)
- **A "load once" guard cannot double as retry.** Discover's Retry called `onSelectHubTab`, which only
  loads when `!discoverLoaded` — always true after the first (failed) load, so Retry was a no-op. Give
  retry its own entry point. → `feedback_retry_behind_load_once_guard` (new)
- **Lists keyed by a server id must dedupe on append.** Archidekt pages overlap when the sort order shifts;
  `results + page` with `key = archidektId` crashes `LazyVerticalGrid`. Same for trending tiles keyed by
  `scryfallId`. → `feedback_paged_list_duplicate_keys` (new)
- **One key, one category.** A custom tag was created as `CardTag(key, CUSTOM)` but re-applied from the
  picker as `CardTag(key, <chosen category>)`; `CardTag` is a data class, so the card ended up with two
  chips for one key. Map the chosen category at creation and dedupe user tags by key.
  → append to `project_card_versions_languages` or a new `feedback_user_tag_category_identity`
- **Every cache table needs an eviction call site.** `CommunityDeckCacheDao.evictOlderThan` existed but
  nothing called it, so every opened Archidekt deck (full JSON blob) stayed in Room forever. Now pruned
  on insert with `CachePolicy.EVICT_MS`. A failed cache write must also never turn a successful fetch into
  an error. → `feedback_cache_eviction_call_site` (new)
- **Playtest keep/bottom-N must use the protection-aware count everywhere.** `onKeep` used
  `computeRequiredBottomCount` while the selector confirmed against `effectiveRequiredBottomCount`, so a
  fully forced hand opened a selector with nothing selectable. → append to
  `feedback_playtest_edge_case_audit_2026-07-22`

## 2. Release gates not run
- [ ] **Build + unit tests of `:app`.** The session's network policy blocks `dl.google.com` (Android SDK
      and Google Maven), so Gradle could not configure the project. Verified instead: all 23 changed
      Kotlin files parse cleanly with `kotlinc` 2.3.20; the shared playtest domain compiles and its 5 test
      classes pass (80 tests, 3 new). Run locally:
      `./gradlew testDebugUnitTest --tests "com.mmg.manahub.feature.carddetail.*" --tests "com.mmg.manahub.feature.playtest.*" --tests "com.mmg.manahub.feature.communitydecks.*"`
- [ ] **Pre-push security gate via `android-security-auditor`** — that agent is not configured in this
      checkout; only a local regex secret scan of the staged diff was run (no hits).
- [ ] **Telemetry review (`crashlytics-ux-auditor`).** New breadcrumbs: `card_detail_load_failed`,
      `card_detail_load_retry`, `card_detail_trade_update_failed`,
      `card_detail_external_link_open_failed`, `playtest_build_library_failed`,
      `community_discover_retry`, `community_deck_cache_write_failed`, `community_deck_cache_evict_failed`;
      new non-fatals `community_deck_owned_keys_flow_error`, `community_deck_import_crashed`.
      Changed Analytics params (PII fix): `save_custom_tag`/`error_save_custom_tag` send `label_length` +
      category enum or `"custom"`; `update_custom_tag` sends `label_length`; `delete_custom_tag` sends
      `key_length`; tag add/remove/confirm/dismiss send `"custom"` for non-canonical tags. Dashboards keyed
      on the old `label`/`key` params need updating.
- [ ] **On-device check:** Card Detail from the Scanner overlay now shows Other prints, flavor text and
      P/T; nav-destination staggered entrance should be unchanged. Legality chips now show
      Restricted/Banned. Check NeonVoid + HallowedPrint.

## 3. Open debt (seen, not fixed)
- [ ] CardDetail one-shot events use `MutableSharedFlow(replay = 0)`; events emitted while the screen is
      not composed are dropped (Playtest's buffered `Channel` pattern would not drop them).
- [ ] CardDetail still uses raw M3 `OutlinedButton`/`Button`/`TextButton`/`SuggestionChip`, hardcoded
      `dp`/`RoundedCornerShape` — component-inventory findings, pre-existing.
- [ ] CardDetail retry after an initial-load failure skips the one-shot English-first redirect (cosmetic:
      the shared-element transition has already run by then).
- [ ] Web `CardDetailScreen` error state has no retry (owner: `kmp-web-fullstack-dev`).
- [ ] `PlaytestSetupViewModel` re-runs `getByIds` + identity inference on every
      `observeAllDeckSummaries` emission (any deck change), and a DAO failure there is unguarded.
- [ ] `BuildLibraryUseCase` silently drops uncached cards (telemetry only; the user sees a smaller deck).
- [ ] A Community Deck import that finishes while the user is elsewhere only surfaces its toast and
      navigation when they reopen that same deck.
- [ ] Community search `loadMore` errors show the raw repository message; a non-numeric `deckSize`
      counts as an active filter but is dropped from the request.
