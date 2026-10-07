# Scanner V2 metadata ingestion checkpoint

Date: 2026-10-07 (Europe/Madrid). Task **B1-01: implementation and source-inventory validation complete**, ready for the parent's sequential security review. Owner: `android-kotlin-architect`, offline-tool scope only. Specification: [ADR-012](../adr/ADR-012-scanner-visual-recognition.md); continuation ledger: [scanner progress](../plans/scanner-v2-progress.md).

Final tool: **generator 2.0.0, projection/schema 2**, Python standard library only. Final inventory: `E:/Projects/ManaHub-build/scanner-v2/catalog/default-cards-v2`. The initial v1 inventory is preserved separately and is not the corrected result. No Kotlin, Gradle, Room, camera/UI, model, image download, photo/label mutation or publication occurred. The prior 106-test Android OCR baseline was not rerun for this offline scope. Assignment HEAD: `0b3a2a3fa75de61d07dd72385f1cb34d270ccdc7`, branch `feature/image-scanner`; unrelated files were preserved.

## Decisions and user requirement

- Stream bounded current-schema **gzip JSONL**, retaining every raw record and projecting printing, typed identity, included face, actual image surface and related-part relationships independently. No enormous card array or illustration-based representative selection.
- `printing_surface` keys such as `face:<printing>:<index>` are panel/printing surrogates, not additional game identities or independent reprint competitors. Consumers group by authoritative parent/face Oracle first and retain surface relationships separately. Four reprint panels can share one parent Oracle; no Oracle ID is invented for missing values.
- Every image-bearing reverse face retains its parent printing and `parent_printing_identity` relationship. Miguel confirmed on 2026-10-07 that supplied backs must resolve to their principal card and that, after downloading the pertinent catalog, visual identification, candidate verification, printing selection and valid-Card resolution must be **local without required backend card-check/hydration calls**. Full raw objects retained here support that later payload/mapper contract; this task does not implement the app mapper, pack or all-language coverage. Legacy OCR and ordinary post-addition sync remain separate.
- Resume is bound to exact compressed SHA, projection/schema, generator and source kind. Prevalidate complete gzip/CRC/expansion/count, commit rows and checkpoint in one transaction, replay/check every committed raw-record SHA on restart and reject mismatches. Process-lifetime OS locks exclude concurrent writers.
- `COMPLETE_SOURCE` means the source stream was fully visited without `--limit`, **not recognition, supported-family or all-language certification**. Limits always mean `INCOMPLETE`. Final v2 accepted all source records; genuine image gaps, negatives and nonpaper/nonplayable policies remain explicit.
- Fetch approved official metadata/URLs only: HTTPS/redirect allowlist, descriptive headers, 150 ms spacing, finite time/byte/attempt budgets, bounded Retry-After and exact source/strong ETag Range resume. Missing/changing validators never concatenate objects. Verify gzip, length and hash before atomic cache rename. A self-computed SHA or unsigned marker proves neither supplier authenticity nor publication rights.

Details and SQL examples: [tool README](../../tools/scanner-catalog/README.md). Defaults: 512 MiB compressed, 8 GiB expanded, 2 MiB per newline-terminated record, 2,000,000 records, 3,600 s per fetch/ingest operation, 32 panels, 1,024 related parts, 8 MiB SQLite cache and 512 MiB disk reserve. Coverage groups stream with 4,096-group/8 MiB-key bounds per dimension. Preflight reserves three times expanded source for database growth; this is an estimate, not a guaranteed SQLite multiplier. Source files remain immutable during use.

## Corrections verified before projection 2

The first real run exposed two implementation errors and two missing layout policies. They were corrected within B1-01, then **all projections were regenerated in a new output**, never repaired by relabeling old counts.

Archived official definitions at `baseline/scryfall/layouts.html` describe art-series as collectible double-faced cards, `prepare` as cards with a prepared spell part and `front_card` as an extra deck-type indicator. Actual frozen metadata confirmed:

| Layout/resource | Source evidence | Final contract |
|---|---|---|
| art_series, 2,655 records | Two panels, no root image; 2,488 have two images, five have one, 162 have none | Actual face surfaces; missing scans are coverage gaps, not corrupt layout; manual nonplayable destination |
| prepare, 122 records | Root image, two included panels, no panel image | One full physical surface, included-panel links, printing selection required |
| front_card, 315 records | Root image, no panels | Inventoried explicit negative; never playable queue destination |
| Related parts | Complete raw maximum 374; 137 valid tokens exceed v1's 256 cap (342/360/374 links) | Finite 1,024-link cap, independent 2 MiB record budget; every observed token normalized |

Complete raw maximum panel count is five; maximum record length 94,378 bytes. These measurements justify finite headroom; they do not justify unlimited parsing. Private diagnostics are archived under `catalog/default-cards/{projection-v2-prerequisites.json,new-layout-diagnostics.json}`. Source/layout conflicts and unknown future layouts remain counted rather than silently excluded.

## Tests and real six-sample smoke

```powershell
$env:SCANNER_CATALOG_TEST_TMP='E:/Projects/ManaHub-build/scanner-v2/catalog/test-tmp'
New-Item -ItemType Directory -Force -Path $env:SCANNER_CATALOG_TEST_TMP | Out-Null
& C:/Python314/python.exe -B -m unittest discover -s tools/scanner-catalog/tests -v
```

**PASS: 40 tests, zero failures/errors, 44.483 s**, CPython 3.14.4 Windows AMD64. Final-code log: `catalog/unittest-v2.log`. Covers bounded gzip/JSONL/CRC, malformed/overflow/raw metadata, null/absent, normal/DFC/reversible/split/adventure/flip/meld/art-series/prepare/front-card relations, reprint panel grouping, real maximum-part cardinality plus overflow, duplicate/conflict audit, transaction rollback/resume with prefix hashes, changed sources/kinds, limits, disk/deadline/report bounds, writer exclusion, URL/redirect abuse, current metadata schema, Retry-After/three-attempt budget, ETag/Range restart and cache corruption. Tests are synthetic and are not a visual benchmark.

The initial sandbox temporary root allowed directory creation but denied child writes; no result was accepted. External temporary fixtures resolved this environment limitation. A fixture read-connection cleanup bug was corrected before passing Windows runs. Earlier v1 runs passed 36 tests; the projection-2 run above supersedes them.

Six B0 archived official objects (normal, transform, split, adventure, flip, meld) were serialized privately with deterministic gzip header (`filename=''`, `mtime=0`), without changing originals:

```python
import gzip, json
from pathlib import Path
root = Path('E:/Projects/ManaHub-build/scanner-v2')
(root/'catalog').mkdir(parents=True, exist_ok=True)
paths = sorted(p for p in (root/'baseline/scryfall').glob('*.json')
               if p.name.startswith(('normal-', 'transform-', 'split-', 'adventure-', 'flip-', 'meld-')))
with (root/'catalog/six-samples.jsonl.gz').open('wb') as sink:
    with gzip.GzipFile(filename='', fileobj=sink, mode='wb', mtime=0) as stream:
        for path in paths:
            row = json.loads(path.read_bytes())
            stream.write(json.dumps(row, ensure_ascii=False, separators=(',', ':')).encode() + b'\n')
```

```powershell
& C:/Python314/python.exe -B tools/scanner-catalog/catalog.py ingest `
  --snapshot E:/Projects/ManaHub-build/scanner-v2/catalog/six-samples.jsonl.gz `
  --out E:/Projects/ManaHub-build/scanner-v2/catalog/six-samples-v2
```

**PASS, projection 2, `COMPLETE_SOURCE`:** six records/printings, eight panels, seven image surfaces, six actual Oracle keys plus eight panel surrogates (**not 14 game identities**), 15 reference/identity links, 11 related links, seven surface illustration IDs, zero issues/rejections. SHA: `ceec9050b1293f44ae51b9326f8dba473c67beafa5392c14a20844d088de8157`; compressed 6,256 bytes, expanded 36,535 bytes. Measured CLI 1.331 s, peak Windows working set 27,082,752 bytes. `six-samples-v2/{catalog.sqlite,coverage.json,runtime.json}` contains the private evidence.

## Complete default_cards projection 2

Only one full official metadata snapshot was downloaded. Its immutable cache was reused locally for both projections and final replay; **no all_cards snapshot or images** were fetched.

Source: `https://data.scryfall.io/default-cards/default-cards-20261007090547.jsonl.gz`; official `updated_at=2026-10-07T09:05:47.951+00:00`. SHA: `43da5fff200a7c8087eaa5b7bf80a64cc17998a7f92ad6b9d7508bafaef5f0d0`. Compressed **78,779,281 bytes**, expanded **633,902,189 bytes**, full gzip CRC verified. `default_cards` is English or original printed language when only that language exists; **not every localized printing**.

Initial approved fetch/ingest used v1 in `catalog/default-cards`. Final regeneration command:

```powershell
& C:/Python314/python.exe -B tools/scanner-catalog/catalog.py ingest `
  --snapshot E:/Projects/ManaHub-build/scanner-v2/catalog/cache/default-cards-20261007090547.jsonl.gz `
  --bulk-type default_cards --out E:/Projects/ManaHub-build/scanner-v2/catalog/default-cards-v2 `
  --batch-size 2000
```

**PASS, exit 0, `COMPLETE_SOURCE`, no development limit:**

| Inventory | Count |
|---|---:|
| Raw records / accepted normalized printings | 118,601 / 118,601 |
| Rejected, duplicate, conflicting or pending records | 0 |
| Included face panels | 10,447 |
| Full-image surfaces / eligible under declared inventory policy | 122,517 / 108,830 |
| Typed keys / actual Oracle keys | 49,072 / 38,708 |
| Distinct surface illustration IDs | 55,841 |
| Reference/identity links | 132,635 |
| Related-part / meld-component links | 166,287 / 245 |
| Related links with absent target inventory row | 2,111 |
| Printings without image reference | 162 |

Destinations: printing selection required 111,775; declared manual nonplayable 6,511; explicit negatives 315. No observed layout remains unknown or mapping-conflicted in this snapshot. Eligibility is an inventory policy, not accuracy or a claim that every layout already has a recognizer.

Layouts: normal 108,444; token 3,036; art_series 2,655; transform 1,060; adventure 456; saga 414; split 351; planar 330; modal_dfc 326; front_card 315; mutate 146; emblem 140; prepare 122; double_faced_token 121; vanguard 119; scheme 110; reversible_card 83; meld 72; class 71; leveler 63; prototype 49; flip 45; host 29; case 27; augment 17.

Languages: English 115,956; Spanish 1,207; Japanese 662; French 430; Italian 194; other 14 language codes total 152. This observed mixed-language count does not imply all localized printing coverage.

| Retained coverage/policy issue | Source records | Scoped occurrences |
|---|---:|---:|
| Low-resolution image status | 6,726 | 6,900 |
| Placeholder image status | 575 | 575 |
| Missing face scan | 167 | 329 |
| No image reference | 162 | 162 |
| Nonpaper metadata | 9,136 | 18,407 |
| Manual nonplayable destination | 6,511 | 6,511 |
| Explicit negative front card | 315 | 630 |
| Missing root Oracle | 83 | 83 |

Categories overlap and scope counts can include parent/face; they are not additive exclusions. Reference statuses: highres_scan 115,042; lowres 6,900; placeholder 575. Printing statuses also retain 162 `missing`. Surface illustration IDs are absent on 824 references. Face Oracle IDs are absent on 10,281 panels and present on 166; absent fields retain typed surrogates and parent links, never invented Oracle IDs.

Measured regeneration: **310.049 s** (5 min 10 s), lifetime peak working set **45,694,976 bytes**, SQLite size **883,347,456 bytes**. Batch size 2,000 used an existing bounded option; v1 used 250 and ran alongside test I/O. This is observed desktop tool evidence, not a controlled batch-performance comparison, Android PSS, retained heap or recognition latency.

Repeating the identical local regeneration command was the final replay: **PASS, exit 0, identical counts/hash, 8.157 s**, peak working set **45,211,648 bytes**. It checked each committed raw hash and checkpoint cardinality without rewriting projections. Projection rules were frozen before the new v2 regeneration; no earlier v1 rows were reinterpreted. `runtime-initial-full.json` and `coverage-initial-full.json` preserve initial v2 metrics, while `runtime.json`/`coverage.json` reflect replay.

Readonly SQL verification after replay: **integrity_check=ok**, zero FK violations, count/min/max/checkpoint **118,601 / 1 / 118,601 / 118,601**, every disposition accepted, zero raw parent-ID mismatches, zero image-bearing face references missing their parent identity, zero art-series policy conflicts, all 315 front cards explicit negatives and exactly 122 prepare surfaces. Evidence: `default-cards-v2/consistency.json`.

Final normalize-source SHA: `f041c61aa2a92e749b781ca3973cb45ace6f1fd1e7b5624df561a1cca0c3e5f0`. Identity-source SHA: `409ff8d9dc42a82d7403c15cc1db73ad1bbaf984cba61b02494c0d838ce509d0`. DDL-string SHA: `0248e0b69d2b71ec345f48de90c66b06db81d3e267d1b3e13bc1d8140d08aa0b`. Full file hashes are in private `tool-source-hashes.json`; hashes are integrity evidence, not authenticity.

## Archived v1 and handoff

`catalog/default-cards` remains unchanged for comparison: 118,601 raw records, 118,464 normalized printings, 137 cap-rejected valid tokens, 122,380 surfaces and 2,655 art-series policy warnings. Original runtime 1,491.024 s, peak 55,001,088 bytes; final v1 replay 8.294 s. Private `report-v1-before-final.md`, coverage/runtime archives, diagnostics, consistency and source hashes preserve this history. **It is not the final corrected inventory.**

Carry the immutable gzip and `.verified.json` marker, corrected v2 database/reports and tool revision privately to another device. Git transfers code/contracts/reports, not the catalog, photos or labels. Adjust only machine-local roots; a new hash/schema/generator requires a new output. The supplied 97-photo manifest remains unchanged. No new bytecode/cache artifacts were created in the checkout; parent owns graph update, AGENTS/memory, progress ledger and security gate. No commit/push/PR occurred in this task.

Next bounded task: **canonical label enrichment** against the downloaded local metadata, including reverse-to-principal-parent mappings and local printing candidates for the 97 development records. Preserve ambiguous/absent localized printings instead of guessing editions; downloaded pertinent language coverage may need a later separately authorized wider snapshot. Manual quads, negative/open-set/masked-text captures, independent splits, benchmark/model selection, physical Android/memory/thermal validation and wider statistical hardware gates remain pending. No recognizer, final pack, full-language payload guarantee, training/data-image rights clearance or publication is certified here.
