# Collection import / export

Entry point: the 3-dot overflow in `CollectionTopBar`, visible only on `CollectionTab.CARDS`.

## Invariants

- **The import review queue is PRIVATE.** It is a second `PersistentCardQueueRepository` over the
  `collection_import_queue_v1` key of its OWN `collection_import_prefs` file (never `scanner_prefs`:
  SharedPreferences rewrites the whole file per write), registered `named(COLLECTION_IMPORT_QUEUE)`
  in `collectionKoinModule` with its own `CardQueueActions`. The shared AddCard/Scanner queue
  (unqualified `CardQueueRepository`) must never be read, shown or cleared by this feature. Both
  queues are single instances per store — never construct a second one over the same key.
- **Nothing on this path may run on the main thread.** The queue is built with a `persistenceScope`,
  so every mutation encodes + writes through a conflated channel on the app scope (and `commit()`s,
  so `onPause`'s QueuedWork drain never blocks); `ManaHubApp` warms the single at start-up so the
  restore never lands in composition; parsing and `ResolveCollectionImportUseCase` run on
  `parseDispatcher` with progress throttled to one update per 250 ms. The queue is capped at
  `MAX_IMPORT_QUEUE_ENTRIES` (2,000) — `MAX_FILE_BYTES` is a byte cap, not a row cap.
- **Unresolved lines are persisted with the queue** (`CollectionImportUnresolvedStore`, same
  preference file, capped at `MAX_PERSISTED_UNRESOLVED_LINES`): the resume dialog offers them, so
  they must survive process death exactly as the queue does.
- **Imports grant no XP.** The import `CardQueueActions` is built with `CommitImportedCardsUseCase`
  (a `CardBatchCommitter`), never `CommitScannedCardsUseCase` — an import is neither a scan nor a
  manual add. Large adds go through `UserCardRepository.addOrIncrementBatch`, ONE Room transaction
  per 500-entry slice (never N single writes: Room invalidation storm, Stats OOM precedent).
- **Resolution is batched.** `ResolveCollectionImportUseCase` sends up to 75 identifiers per
  Scryfall `/cards/collection` call (id > set+number > name+set > name) and only falls back to a
  fuzzy `searchCardByName` for what came back `not_found` (the response's own `notFound` list, not
  an inferred miss), capped at `MAX_NAME_FALLBACKS`. An identifier Scryfall answered that `CardIndex`
  could not map back still falls back, but is counted separately
  (`collection_import_unmatched_response`) so the two never blur together. Never resolve a list one
  name at a time. Rate-limit exhaustion is a typed `RateLimited` outcome.
- **The importer must survive a file a real exporter wrote.** Detection skips an Excel `sep=,`
  preamble, comment lines and a title row, and retries on the second non-blank line — otherwise a
  re-saved CSV parses as TEXT and every row is rejected. The picker keeps `*/*` (providers mislabel
  `.csv`), so a picked image is rejected AFTER the read by `CollectionImportParser.looksBinary`.
- **The line format is shared with deck import/export.** `DeckImportExportHelper.formatLine` omits
  the whole `(SET) number` group unless BOTH halves are present: the importer's regex only accepts a
  set code followed by a collector number, so `(SET)` alone would be swallowed into the card NAME.
  Guarded by `DeckImportExportHelperLineTest`.
- **Nothing is dropped silently.** Unparseable lines and unresolved identifiers are reported back as
  `unresolvedLines` and shown with a copy action; copies dropped by the 9,999-per-row cap come back
  as `clampedCopies` in a WARNING toast; an export that could not hydrate a row reports the skipped
  count, and a TEXT export reports `loosePrintingRows` (rows with no `(SET) number`, which re-import
  as an arbitrary printing). TEXT also drops condition + language entirely — CSV is the lossless
  format, and both facts belong in the format's description string.
- **Export follows the Cards tab.** It serialises `visibleRows` (the per-printing rows behind the
  visible groups: source + search + advanced filters), hydrating `pending_hydration` placeholders
  first. Keep `visibleRows` in sync with `applyFilters` if the filter pipeline changes.
- **Sharing goes through the FileProvider** `${applicationId}.fileprovider`, limited to
  `cache/exports/` (`res/xml/file_provider_paths.xml`). Each export writes into its own timestamped
  sub-directory and prunes older ones (>24 h, or beyond 5) BEFORE writing — never wipe the directory
  on write: a receiver such as Gmail reads the stream when the message is sent, not when it is
  attached. Never put the file content in `EXTRA_TEXT` (1 MB Binder limit).
- Sheets render above the NavHost: they mount only while the destination is RESUMED, with their
  open state in the ViewModel. The share chooser is part of that rule — launching it while the
  destination is not RESUMED is dropped by Android 10+, so `pendingShare` is only cleared once
  `startActivity` actually succeeded.
