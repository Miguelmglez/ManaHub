### Card tagging engine (core/tagging)
- **Analysis is English-only**: `StrategyAnalyzer` scans ONLY `card.oracleText` (no `printedText`/
  `lang`); non-English printings are resolved to English upstream first. Labels stay a
  `Map<String,String>` but only "en" is populated.
- Detection uses `DetectionRule(allOf/anyOf/noneOf + typeLineAnyOf/typeLineNoneOf, confidence?)` —
  use `allOf` for compound phrases, NEVER loosely OR'd word fragments. Reminder text (parens) is
  stripped and the card's own name is replaced with `~` before matching.
- **Tag keys are persisted user data — NEVER rename an existing key.** User overrides use the
  rule-line syntax: terms joined by ` + ` are ANDed, `!term` excludes.
- **D12 — system dictionary entries are read-only for users.** The editor shows system rows
  view-only (label + rules, no edit/delete); users can only create/edit/delete their OWN tags, under
  the `custom_` key prefix (`TagDictionaryRepository.CUSTOM_KEY_PREFIX`) — `upsert` rejects any other
  key. `StrategyAnalyzer.analyze()` returns empty on a blank `oracleText` BEFORE evaluating any rule,
  including `typeLineAnyOf`-only ones — a type-line-only detector still needs a non-blank oracle text
  fixture to be exercised in tests.
- **Collection/Deck Studio show tags from Room only — a BATCHED background worker keeps that Room
  data populated.** Search-result pages never trigger `card_strategy_tags` resolution (only opening
  Card Detail does). `CardTagHydrationWorker` (`core/sync/`) → `HydrateCollectionStrategyTagsUseCase`
  is the sole bulk path: a ~1000-candidate page per run, grouped by `oracle_id`, resolved by ONE
  `CardStrategyTagsRepository.getStrategyTagsBatch` call (chunked at 100 ids, hard cap 500 — never
  near PostgREST's silent `db-max-rows` truncation), persisted through the same
  `CardDao.updateTagsAndSuggestions` path as every other resolution site. It is enqueued (one-time,
  unique `card_tag_hydration_one_time`, never periodic) after a successful `CollectionSyncWorker`
  cycle and by `CardBackfillWorker`'s daily tick — that worker no longer runs the old
  `backfillMissingStrategyTags(40)` per-card drip, so the two never double-work the same candidates
  (`backfillMissingOracleIds(20)` still runs there first: a blank `oracleId` card can never have a
  precomputed row). Candidates come only from `CardDao.getScryfallIdsMissingStrategyTags`, which is
  self-terminating via its `NOT EXISTS card_strategy_tags_cache` check, never `cards.tags = '[]'`.
  A batch MISS writes no cache row (the on-device `submitStrategyTags` write-back is what caches
  those), so the follow-up pass is chained on `Result.retry()` ONLY after a run with ≥1 precomputed
  hit — the one signal that provably shrank the candidate list. On-device fallback is capped at 60
  cards per run.
- **Tags are oracle-wide but stored per `scryfall_id` — a resolution MUST fan out to every cached
  printing of that `oracle_id`, never just the candidate row.** The candidate query drops a card as
  soon as ANY row with its `oracle_id` is cached, so a second owned printing was excluded before it
  was ever written, permanently (8 rows measured on device, 2026-09-07). `applyHits` re-reads all
  printings via `CardDao.getByOracleIds` and writes each through
  `ResolveCardStrategyTagsUseCase.resolveWithPrefetched` with that row's OWN `tags` column — never a
  single `UPDATE ... WHERE oracle_id = ?`, which would clobber a per-printing user-confirmed tag.
  Writes go through `CardDao.updateTagsAndSuggestionsBatch` in 500-row transactions (one Room
  invalidation per chunk, not per row). `CardDao.getScryfallIdsWithUnwrittenStrategyTags` is the
  repair net for rows already stranded (also covers an interrupted run, since the batch caches every
  `oracle_id` up front); it is the ONE sanctioned `tags = '[]'` predicate — network-free, capped, and
  it must never drive the retry chain, because it is a stable fixed point, not a shrinking queue.
- **`TagDictionary.get(key)` is NOT a validity filter — a miss does not mean "unknown/drifted key".**
  The dictionary only holds hand-authored ARCHETYPE/STRATEGY/ROLE/KEYWORD entries; `TypeLineAnalyzer`
  synthesizes `TagCategory.TYPE` tags (card types + creature subtypes — "creature", "artifact", "elf",
  ...) directly from the type line and NEVER registers them in the dictionary, so a miss on a
  TYPE-shaped key is the expected/only shape, not a taxonomy-drift signal. Any code that resolves a raw
  tag key (Supabase `card_strategy_tags` payload, a persisted `cards.tags` JSON blob, etc.) via
  `TagDictionary.get()` must fall back to `CardTag(key, TagCategory.TYPE)` on a miss, never drop the
  tag — dropping silently loses every TYPE tag (`CardStrategyTagsRepositoryImpl.toFound()` did exactly
  this until 2026-07-23; see `feedback_card_strategy_tags_type_dictionary_miss` in memory).
- **Collection's TAG grouping (`CollectionGroupingMode.TAG`) is deliberately restricted to the
  "identity" categories — `TagCategory.STRATEGY`/`ARCHETYPE`/`TRIBAL`** (the same
  `IDENTITY_CATEGORIES` set used deck-engine-wide in `shared/core-domain`, e.g. `DeckScorer`,
  `InferDeckIdentityUseCase`) — a scope difference from CardDetail/Deck Studio, which show every
  category (color-coded). TYPE/KEYWORD/ROLE/CUSTOM stay excluded on purpose, ROLE included, since
  functional-role tags ("removal"/"tutor") aren't a deck-identity signal and would flood the section
  list the same way TYPE tags would. Widened from STRATEGY-only on 2026-08-17 (ARCHETYPE/TRIBAL-only
  cards were incorrectly falling into "untagged" after the Phase 0.2 ROLE-key expansion made
  STRATEGY-only cards rarer) — do not widen `groupCollection()`'s TAG branch
  (`shared/core-model/.../CollectionGrouping.kt`) or `CollectionScreen.kt`'s `collectionGroupLabel()`
  beyond this three-category set.
- → memory: `project_strategy_tag_bulk_hydration_2026-09-07`, `project_tagging_engine_v2`,
  `feedback_tag_dictionary_archetype_audit`,
  `project_strategy_tags_backfill_2026-07-22`, `feedback_card_strategy_tags_type_dictionary_miss`,
  `project_card_tag_category_colors_2026-07-23`, `feedback_collection_tag_grouping_identity_categories`

