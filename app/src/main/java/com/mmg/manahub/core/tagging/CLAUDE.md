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
- **Collection/Deck Studio show tags from Room only — a one-time startup backfill keeps that Room
  data populated.** Search-result pages no longer trigger `card_strategy_tags` resolution (only
  opening Card Detail does); `CardRepository.backfillMissingStrategyTags(40)` runs at startup right
  after `backfillMissingOracleIds(20)` (same `appScope.launch` block, same order — a blank
  `oracleId` card can never have a precomputed row) to resolve owned-but-never-resolved cards
  exactly once. Self-terminating via `CardDao.getScryfallIdsMissingStrategyTags`'s `NOT EXISTS
  card_strategy_tags_cache` check, never `cards.tags = '[]'`.
- **`TagDictionary.get(key)` is NOT a validity filter — a miss does not mean "unknown/drifted key".**
  The dictionary only holds hand-authored ARCHETYPE/STRATEGY/ROLE/KEYWORD entries; `TypeLineAnalyzer`
  synthesizes `TagCategory.TYPE` tags (card types + creature subtypes — "creature", "artifact", "elf",
  ...) directly from the type line and NEVER registers them in the dictionary, so a miss on a
  TYPE-shaped key is the expected/only shape, not a taxonomy-drift signal. Any code that resolves a raw
  tag key (Supabase `card_strategy_tags` payload, a persisted `cards.tags` JSON blob, etc.) via
  `TagDictionary.get()` must fall back to `CardTag(key, TagCategory.TYPE)` on a miss, never drop the
  tag — dropping silently loses every TYPE tag (`CardStrategyTagsRepositoryImpl.toFound()` did exactly
  this until 2026-07-23; see `feedback_card_strategy_tags_type_dictionary_miss` in memory).
- **Collection's TAG grouping (`CollectionGroupingMode.TAG`) is deliberately restricted to
  `TagCategory.STRATEGY` tags only** — a scope difference from CardDetail/Deck Studio, which show
  every category (color-coded). This is intentional, not an oversight left over from the
  TYPE-tag-miss fix above: do not widen `groupCollection()`'s TAG branch
  (`shared/core-model/.../CollectionGrouping.kt`) or `CollectionScreen.kt`'s `collectionGroupLabel()`
  to all categories.
- → memory: `project_tagging_engine_v2`, `feedback_tag_dictionary_archetype_audit`,
  `project_strategy_tags_backfill_2026-07-22`, `feedback_card_strategy_tags_type_dictionary_miss`,
  `project_card_tag_category_colors_2026-07-23`, `feedback_collection_tag_grouping_strategy_only`

