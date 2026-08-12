### Card versions & languages (CardDetail / Collection / AddCardSheet, 2026-07-15)
Card identity across sets/languages = `Card.oracleId` (Room v46, lazy backfill; exact English `name`
is the documented fallback for blank-oracleId cache rows — every oracle-wide DAO query must guard
`:oracleId != ''`). A collection entry's `scryfall_id` points at the EXACT printing — editing
set/language rewrites it; all entry edits go through `updateEntryWithMerge` (atomic 3-branch:
live-collision merge / soft-deleted revive / in-place; returns `UpdateEntryOutcome`, never show
success on `ENTRY_NOT_FOUND`). CardDetail never forces English anymore; language sheet lists only
real prints (`getLanguagePrints`), full list only when the fetch fails. Collection groups per
(identity, set), `groupKey = "setCode|identity"`, representative print = FIRST added. Supabase
`batch_upsert_collection` is id-arbiter with tuple-collision exception-merge + PARTIAL unique index
(server) vs FULL local index — pull-side collisions resolve remote-authoritative, never sum.
→ memory: `project_card_versions_languages`

