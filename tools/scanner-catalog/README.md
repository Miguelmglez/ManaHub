# Scanner V2 metadata inventory

Python 3.10+ standard library only (verified here with CPython 3.14.4). Projection/schema 2, generator 2.0.0. Scope: current-schema Scryfall gzip JSONL metadata, SQLite relationships and coverage. This tool does not download images, choose a model, create a recognition pack, evaluate photos, write Room or alter the Android scanner. ADR-012 is the execution specification.

Run from the repository root. Every generated snapshot, database, report, temporary fixture and cache belongs **outside this repository**. `-B` prevents Python bytecode artifacts. No dependency installation is needed.

```powershell
& C:/Python314/python.exe -B tools/scanner-catalog/catalog.py ingest `
  --snapshot E:/Projects/ManaHub-build/scanner-v2/catalog/cache/default-cards-20261007090547.jsonl.gz `
  --bulk-type default_cards --out E:/Projects/ManaHub-build/scanner-v2/catalog/default-cards-v2

# Resolve current official metadata and fetch one snapshot before ingestion.
& C:/Python314/python.exe -B tools/scanner-catalog/catalog.py ingest `
  --bulk-type default_cards --cache E:/Projects/ManaHub-build/scanner-v2/catalog/cache `
  --out E:/Projects/ManaHub-build/scanner-v2/catalog/default-cards-v2

# Fetch only. Supported types: default_cards, all_cards, unique_artwork.
& C:/Python314/python.exe -B tools/scanner-catalog/catalog.py fetch `
  --bulk-type default_cards --cache E:/Projects/ManaHub-build/scanner-v2/catalog/cache

# Development subset is always INCOMPLETE, even if limit exceeds file length.
& C:/Python314/python.exe -B tools/scanner-catalog/catalog.py ingest `
  --snapshot E:/Projects/ManaHub-build/scanner-v2/catalog/six-samples.jsonl.gz `
  --out E:/Projects/ManaHub-build/scanner-v2/catalog/subset --limit 3
```

Do not fetch `all_cards` just to continue the initial default-language inventory. Its wider metadata download is a separate resource/coverage decision. A local `--bulk-type` is a declaration, not independent verification; official fetch writes a bounded `.verified.json` provenance marker that ingest checks against the actual hash and type.

## Meaning of outputs

`catalog.sqlite` holds raw JSONL source bytes, normalized metadata and an atomic checkpoint. `coverage.json` holds status, snapshot evidence, counts, source boundaries and every retained issue category. `runtime.json` records successful CLI duration and measured lifetime peak desktop working set/maxrss when available. It is not Android PSS or a claim of retained/peak decoder memory. `.writer.lock` excludes concurrent writers and is released by the OS after process death.

`COMPLETE_SOURCE` means the verified source stream was fully visited without a development limit. Invalid JSON/card records and duplicate/conflicting printings remain in the inventory with dispositions; a complete source scan does **not** certify complete normalization, supported layouts, usable images, visual quality, identity accuracy or all-language coverage. Inspect rejected/conflict counts and issue reports before consuming the inventory. `INCOMPLETE` covers limits, interruption, source mismatch, corrupt/truncated gzip, budget or storage failure. Exit 0 also permits an explicitly INCOMPLETE development subset; downstream consumers must check status. No publisher is implemented.

`default_cards` contains English printings, or printed language where only that language exists; it is not all localized printings. `unique_artwork` is a source-selected artwork inventory, not an exhaustive printing inventory. `all_cards` reports only the source's all-language claim, not an independent coverage audit. Snapshots can be stale. None of these facts proves all identities or images are visually recognizable.

## Schema and identity contract

- `source_records`: immutable sequence, raw bytes/SHA, printing ID when valid, disposition/reason. Raw preserves unknown fields and absent/null distinctions, including localized titles, frame effects, image URLs, games and finishes.
- `printings`: Scryfall UUID, source sequence, title, set/collector/language/finishes, layout, destination, optional Oracle/illustration IDs and explicit presence states. No illustration-based deduplication or representative printing selection.
- `identities`: typed `oracle:<uuid>` when present; `printing:<uuid>` and `face:<printing>:<index>` otherwise. These fallbacks (`kind=printing_surface`) are panel/printing surrogates, not additional game identities, invented Oracle IDs or proof of equivalence across printings. Even when a parent has an Oracle ID, included panels without their own Oracle retain printing-specific surrogates. Retrieval consumers group first by authoritative parent/face Oracle and keep panel/appearance links separately; never treat reprint surrogates as independent competing game identities or report their count as verified game-identity count.
- `faces`: parent UUID/index and face-local metadata/identity. `card_faces` alone does not create images. Reverse references retain parent-printing identity links; reversible faces with their own Oracle IDs retain each identity.
- `image_references`: root or actual image-bearing face, full-card `normal`/`large`/`png` alternatives, optional illustration, status and eligibility. One surface is one reference regardless of three image sizes. URLs are metadata only. No `border_crop`/`art_crop` is relabeled as physical-card extent; all original URL variants remain in raw.
- `reference_identities`: many-to-many identity links, with parent, visible-face or included-face relationship explicitly named. Included split/adventure/flip panels are not separate physical images.
- `related_parts`: all typed `all_parts` UUID/component links, including meld result/parts, bounded to 1,024 links per source record. Missing targets remain unresolved rather than guessed. Join `image_references.printing_id` to `related_parts.printing_id` and filter `component='meld_part'` to obtain a meld image's candidate parent UUIDs; resolving their identity requires the target inventory row. A meld result is never assumed to identify one playable parent.
- `issues`: source sequence, scope and reason. Image status missing/placeholder/lowres/unknown, missing images/full-image URLs, unsafe URLs, unsupported/mismatched layouts, nonpaper and declared nonplayable destinations remain counted. Tokens/emblems/art-series have `manual_nonplayable`; no automatic playable-card queue destination is fabricated.

Basic queries (open SQLite read-only when using another tool):

```sql
SELECT p.id, p.name, p.set_code, p.collector_number, p.lang,
       f.face_index, f.name AS face_name, f.oracle_id AS face_oracle
FROM printings p LEFT JOIN faces f ON f.printing_id=p.id
WHERE p.name=? OR f.name=?;

SELECT reason, count(DISTINCT seq) FROM issues GROUP BY reason;

SELECT i.key, r.target_printing_id, p.identity_key
FROM image_references i JOIN related_parts r ON r.printing_id=i.printing_id
LEFT JOIN printings p ON p.id=r.target_printing_id
WHERE r.component='meld_part';
```

Projection 2 corrects the initial source-discovered limits. The complete raw snapshot's maximum is 374 related parts, five panels and 94,378 bytes per record; the cap of 1,024 links has measured headroom while remaining finite under the independent record/stream budgets. Art-series are double-sided collectible cards: preserve actual face images, missing-image states and manual nonplayable destination. Missing art-series face scans are coverage gaps, not contradictory layouts. Prepared spells use one root image with included text panels, like adventure; they do not create a second image. Front cards indicate deck types and have a root image; preserve them as `explicit_negative`, never a playable queue destination. These metadata contracts follow the archived official layout definitions and actual source shapes; they do not promise recognition quality. The initial v1 output (137 cap rejections and art-series/prepare/front-card policy gaps) remains privately archived and is not reinterpreted as corrected. A schema/generator change requires a new output directory.

## Bounds, transport and recovery

Defaults are finite: compressed 512 MiB, decompressed 8 GiB, record 2 MiB including newline, 2,000,000 lines, 3,600 seconds per fetch/ingest operation, batches 250 and SQLite page cache 8 MiB. Normalization caps are 32 panels and 1,024 related parts per record; larger relationships remain in rejected raw inventory. Coverage dimensions stream groups with caps of 4,096 distinct groups and 8 MiB of key bytes per dimension; exceeding either stops completion with explicit INCOMPLETE coverage error rather than allocate a huge array or silently truncate. Override numeric bounds explicitly with `--max-compressed`, `--max-decompressed`, `--max-record`, `--max-records`, `--max-seconds`. Batch size is bounded to 10,000. A 512 MiB disk reserve is checked before fetch; ingestion also reserves three times decompressed source length for raw inventory/normalized relationships and checks free space between batches. This is a conservative preflight estimate, not a guaranteed database-size multiplier. OS/storage errors stop the operation.

Input must have a final newline for every record, including the last. A full streaming prepass verifies gzip CRC/trailer, finite expansion/records and compressed SHA-256 before any new inventory rows. Ingestion rereads bounded lines and checks compressed SHA again before its final commit. JSON duplicate keys, NaN/Inf, numeric overflow and malformed metadata are rejected per record with raw retained; oversized lines/decompression/count/time violations abort the source. A self-computed SHA detects changes; it does **not** authenticate the supplier. Official HTTPS is transport provenance, not a signed catalog manifest.

Fetch resolves only `https://api.scryfall.com/bulk-data`, requires the current `jsonl_download_uri`/`compressed_size` schema and supports only internally generated approved gzip paths on `data.scryfall.io`. Redirects remain on the same approved host/path family; no arbitrary URL option exists. Full-image metadata eligibility accepts HTTPS `cards.scryfall.io` URLs only, without fetching them. Requests use descriptive User-Agent/Accept, identity transfer encoding and at least 150 ms spacing, including redirects. Socket timeout is at most 30 s; retry budget is three attempts for transport/429/selected 5xx. Valid Retry-After dates/seconds are honored; a wait beyond 60 s or the remaining operation budget aborts so the user can retry later. No API enumeration by card name.

Partial downloads are external `.part` files plus an atomic source/ETag journal. Resume uses Range/If-Range only with the exact metadata fingerprint and a strong ETag; a missing validator restarts, HTTP 200 replaces rather than appends, and HTTP 206 must match exact offset/total/ETag. Byte length, complete gzip CRC and snapshot hash are checked before atomic cache rename and verification marker. On interrupted ingestion, SQLite rolls back the active batch while committed rows/checkpoint survive. Repeat the exact local snapshot/out command: the gzip stream replays to the last committed sequence, verifies each committed raw-record hash and checkpoint cardinality, then continues without duplicates. A changed hash, schema, generator or source kind requires a **new output directory**. There is no arbitrary force/reset option. Source files must remain immutable while used; multiple adversarial mutations during an invocation are outside this local tooling contract.

An unexpected cached hash/CRC/length or Range mismatch stops instead of automatically deleting evidence. Inspect/copy that private cache before a deliberate restart in a new cache directory. No automatic source-photo mutation or cleanup occurs.

## Tests and handoff

All checked-in test images/records are synthetic; only metadata is synthesized. Tests create external temporary directories and remove their own fixtures. On this Windows sandbox, use the existing external build root:

```powershell
$env:SCANNER_CATALOG_TEST_TMP='E:/Projects/ManaHub-build/scanner-v2/catalog/test-tmp'
New-Item -ItemType Directory -Force -Path $env:SCANNER_CATALOG_TEST_TMP | Out-Null
& C:/Python314/python.exe -B -m unittest discover -s tools/scanner-catalog/tests -v
```

Covered: gzip JSONL/CRC/truncation, malformed/overflow records, byte/count/deadline/disk limits, raw preservation, null/absent, layouts/DFC/reversible/meld, conflicts/duplicates, transaction rollback/resume, source changes, limit status, output boundaries, writer locks, official URL/redirect policy, current metadata schema, Retry-After/budgets, ETag/Range restarts and cache verification. Real end-to-end evidence is in `docs/scanner-v2/catalog-ingest.md`.

Move the immutable snapshot, `.verified.json` marker, SQLite database and JSON reports privately to another device; Git transfers only code/contracts/reports. Preserve filenames/hashes; change only local path roots. An incomplete database must be paired with its exact source snapshot and schema/tool version. Resume offline first, then review source date/coverage before another live fetch. No database migration between tool schema versions is implemented.

Code dependency inventory: CPython standard library only. Scryfall data/image rights, model-weight rights and later training/redistribution/publication permissions remain separate pending checks. Fetching accessible metadata is not approval to publish a pack. No R2, model or image publication exists in this task.

Updated user requirement (2026-10-07): supplied reverse faces resolve to their principal parent printing. After the relevant catalog is downloaded, visual candidate verification, printing selection and valid `Card` resolution must use local metadata, without required backend card-check/hydration calls. The raw source payload and parent/face/printing links here support that later pack contract; this tool does not implement the application mapper or certify all-language payload coverage. Preserve full payload rather than substituting lookup URIs or incomplete card placeholders. Legacy OCR and normal post-addition sync are separate unchanged contracts.
