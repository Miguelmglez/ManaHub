# Collection import / export

Entry point: the 3-dot overflow in `CollectionTopBar`, visible only on `CollectionTab.CARDS`.

## Invariants

- **The import review queue is PRIVATE.** It is a second `PersistentCardQueueRepository` over the
  `collection_import_queue_v1` preference key, registered `named(COLLECTION_IMPORT_QUEUE)` in
  `collectionKoinModule` with its own `CardQueueActions`. The shared AddCard/Scanner queue
  (unqualified `CardQueueRepository`) must never be read, shown or cleared by this feature. Both
  queues are single instances per store — never construct a second one over the same key.
- **Imports grant no XP.** The import `CardQueueActions` is built with `CommitImportedCardsUseCase`
  (a `CardBatchCommitter`), never `CommitScannedCardsUseCase` — an import is neither a scan nor a
  manual add. Large adds go through `UserCardRepository.addOrIncrementBatch`, ONE Room transaction
  per 500-entry slice (never N single writes: Room invalidation storm, Stats OOM precedent).
- **Resolution is batched.** `ResolveCollectionImportUseCase` sends up to 75 identifiers per
  Scryfall `/cards/collection` call (id > set+number > name+set > name) and only falls back to a
  fuzzy `searchCardByName` for what came back `not_found`, capped at `MAX_NAME_FALLBACKS`. Never
  resolve a list one name at a time. Rate-limit exhaustion is a typed `RateLimited` outcome.
- **Nothing is dropped silently.** Unparseable lines and unresolved identifiers are reported back as
  `unresolvedLines` and shown with a copy action; an export that could not hydrate a row reports the
  skipped count in its toast.
- **Export follows the Cards tab.** It serialises `visibleRows` (the per-printing rows behind the
  visible groups: source + search + advanced filters), hydrating `pending_hydration` placeholders
  first. Keep `visibleRows` in sync with `applyFilters` if the filter pipeline changes.
- **Sharing goes through the FileProvider** `${applicationId}.fileprovider`, limited to
  `cache/exports/` (`res/xml/file_provider_paths.xml`), which is wiped on each export. Never put the
  file content in `EXTRA_TEXT` (1 MB Binder limit).
- Sheets render above the NavHost: they mount only while the destination is RESUMED, with their
  open state in the ViewModel.
