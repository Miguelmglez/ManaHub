# Scanner V2 execution checkpoint

Last updated: 2026-10-07 (Europe/Madrid).

## Resume here

Authoritative specification: [ADR-012](../adr/ADR-012-scanner-visual-recognition.md).
The Desktop prompt is historical input; ADR-012 supersedes its algorithm, acceptance and integration assumptions.

User direction: execute with the project agents, one agent per task, sequentially. Close and verify each bounded task before assigning the next. Keep this file updated so another session or device can resume from evidence.

Current checkpoint: **B1-01 closed**, with final corrected schema2 inventory and40 passing tool tests. B0-01 and B0-02 are also closed. Staged security review passed; next: B1-02 canonical label enrichment. Production visual recognition is not implemented or validated; no scanner source, reference images or models have been changed/downloaded.

## Starting state

- Branch: `feature/image-scanner`.
- Starting commit: `6707353f1451f2d28d6e4357c53ffea65fad0d87`.
- Resumed baseline commit: `0b3a2a3fa75de61d07dd72385f1cb34d270ccdc7`. Another session committed a CollectionScreen change; only that file differs from the starting commit. The scanner baseline passed against the resumed commit; preserve the unrelated commit.
- Pre-existing unrelated untracked artifact: `scripts/__pycache__/`. Preserve it.
- Android scanner remains excluded from the KMP migration; web work remains paused.
- Miguel confirmed an attached Pixel 6a is available for tests. Read-only adb verification found Android 17/API 37 and 4 KB pages. No additional physical devices have been supplied.
- Private photo sample supplied by Miguel: `E:/Projects/scanner_muster`. Inventory and visual inspection completed: 97 JPEG files, 381,329,720 bytes, all decodable, no byte-identical duplicates. This is a development sample with observed titles/languages annotated; canonical identity, exact printing, quads and independence remain separate pending fields.
- Prior measurements in the Desktop prompt have not been reproduced and are not acceptance evidence.

## Task ledger

| Task | Owner | Status | Evidence / next action |
|---|---|---|---|
| B0-01: inventory, OCR baseline tests, source/schema and resource checks | android-kotlin-architect | Closed: baseline PASS | [Report](../scanner-v2/block-0-baseline.md): 106 tests, exit 0; resources and source contracts inventoried. Physical acceptance remains pending. |
| B0-02: development-sample labels and annotation contract | android-unit-test-writer | Closed: annotation validation PASS | [Report](../scanner-v2/sample-labels.md): 97 observed titles/languages, 102 title fields; no user questions. Catalog/printing/quad ground truth pending. |
| B1-01: bounded streaming metadata ingestion tool | android-kotlin-architect | Closed: corrected inventory PASS | Schema2/generator2.0 full rebuild and replay passed: 118,601 accepted raw/normalized records, SQL integrity/FK checks passed. Live Art Series/token/prepare/front_card corrections verified; Final [report](../scanner-v2/catalog-ingest.md) closes the task; final replay8.157s preserved counts/hash. Separate from image downloads/model experiments. |
| B1-01R: staged security review | android-security-auditor | Closed: PASS | Ten owned paths checked; no secrets/forced ignored files, parser/transport/recovery reviewed. No Android visual/offline certification. |
| B1-02: canonical development-label enrichment | Unassigned | Ready for sequential assignment | Resolve identity/visible-face relationships from verified metadata, preserve ambiguous printing candidates, validate all 97 records. |
| Block 1: remaining catalog and comparative benchmark | Unassigned | Pending label/quad/reference contracts | Separate bounded tasks for coordinates, references, localizers and A/B/C comparison; no algorithm selection in advance. |
| Block 2: Android viability and pipeline selection | Unassigned | Pending benchmark evidence | Physical runtime, parity, memory and latency measurements required. |
| Block 3: pack and operation | Unassigned | Pending selected recipe | Signed compatibility unit, transactional activation, rollback and kill switch. |
| Block 4: scanner integration | Unassigned | Pending technical gates | Preserve OCR, lifecycle, queue and owner contracts. |
| Block 5: quality and opt-in beta | Unassigned | Pending integration | Full-catalog, physical, no-name evaluation with denominators. |
| Block 6: promotion | Unassigned | Pending quality gates | Visual becomes default only after ADR acceptance evidence. |

## Decisions and boundaries

1. **Follow the revised ADR, not the July pHash-only implementation sequence.** Compare the pHash baseline, classical retrieval/local verification and mobile embeddings/local verification before freezing a production pipeline.
2. **Keep identity, visual appearance/face and printing separate.** Recognition of an illustration does not establish an edition, language or finish. Only a resolved valid `Card` and selected printing may reach the existing queue.
3. **Track task completion separately from product acceptance.** Tooling/unit tests can be verified without physical resources; missing photographs, catalog coverage or phone measurements remain explicit pending gates. Synthetic fixtures do not certify recognition quality.
4. **Do not expand product UI/distribution before retrieval and mobile viability evidence.** The existing OCR remains the default; no live collection mutation or automatic addition during experiments.
5. **Use one agent at a time for scoped execution and reviews.** Kotlin/Gradle implementation belongs to `android-kotlin-architect`; reviewers report fixes back to that owner. Build outputs, downloaded data and private photos stay outside the checkout.
6. **Resolve visual scans locally after the catalog download (Miguel, 2026-10-07).** Identity lookup, candidate verification, reverse-face/parent mapping and printing selection must not query a backend. The future pack must carry a versioned validated payload sufficient for an eligible complete `Card`; missing data fails explicitly instead of triggering a scanner backend lookup. Verify with ordinary card cache empty, network off and zero-call remote spies. Existing post-add owner/persistence/sync contracts still apply.

## Verification log

The recovered baseline completed successfully in 18m 21s, exit 0: **106 scanner tests passed**, no failures/errors/skips. Suites: ScannerViewModelTest 47, CardRecognizerTest 24, CardOcrAnalyzerTest 7, CardOcrAnalyzerExtractionTest 28. Exact invocation, XML paths and private log are persisted in [B0-01](../scanner-v2/block-0-baseline.md). Tested/final commit `0b3a2a3fa75de61d07dd72385f1cb34d270ccdc7`; checked scanner source hashes remained unchanged. This verifies the unit baseline and compilation, not real-camera OCR accuracy or visual recognition.

Dependency inspection passed: principal CameraX components resolved to 1.6.2; viewfinder-core resolved to 1.5.1. No Kotlin/Gradle or scanner behavior changes were made.


B1-01 first-pass live-data review (historical; corrected before closure): the first inventory traversed all 118,601 raw records and its final-code replay preserved checkpoint cardinality without duplicates; 36 tests passed. SQL integrity/FK checks passed. It retained 137 valid tokens as rejected raw records because all_parts exceeded 256, and all 2,655 art_series objects hit an incorrect provisional root-only layout rule. These are normalization/coverage defects, not evidence of corrupt Scryfall source or product exclusions. The same implementation agent corrected these rules, added regressions and produced a separately versioned projection against the exact local snapshot before closure. Keep first-run evidence for comparison; do not count its provisional eligibility as benchmark-ready coverage.

B1-01 corrected projection evidence (2026-10-07): schema2/generator2.0 was regenerated in a new output directory against the exact same immutable snapshot. 40 offline tests passed. Coverage reports 118,601 source records and accepted printings, 122,517 physical image surfaces, 108,830 eligible image references, 38,708 actual Oracle IDs and 55,841 distinct surface illustration IDs. SQL integrity is ok, FK violations0, checkpoint118601, missing face-parent identity links0 and raw-parent mismatches0. Source limits remain finite (all_parts1024 versus observed maximum374); Art Series has zero policy conflicts, prepare has122 root surfaces and all315 front_card records remain explicit negatives. Initial corrected invocation310.049s, lifetime peak working set45,694,976 bytes; SQLite883,347,456 bytes. These are desktop tool measurements, not recognition or Android performance. Detailed final replay/runtime/source hashes belong to the task report; future consumers must inspect lowres/missing/nonpaper/nonplayable/language/unresolved-related-target counts.
Security review (2026-10-07): android-security-auditor passed the staged tools/contracts checkpoint, including secret scan, ignored-file check, parameterized parser/SQLite, finite resource/transport bounds and resumable source-prefix validation. google-services.json stayed ignored/unstaged; private photos, snapshots and databases were not staged. diff whitespace check passed. No Android visual/offline quality claim is made by this gate.

## Prerequisites to prepare

| Resource | When required | Status / responsibility |
|---|---|---|
| Consented, private physical-card photos with identity/face and capture labels | Before comparative recognition experiments | 97 photos supplied in `E:/Projects/scanner_muster` and inspected; ground-truth/quad annotation remains pending. This debugging sample does not pass the quality gate. |
| Dataset splits and annotation conventions | Before benchmark implementation/evaluation | Prepare with tooling; separate capture sessions, physical objects and illustration families to avoid leakage. |
| 500 physical cards, at least 3,000 independent known presentations and 3,000 negative/unknown presentations | Development evaluation in ADR §10 | Pending; promotion requires enough independent samples for the statistical error bounds. |
| Private external workspace for reference images, snapshots, models and reports | Before ingestion | E: has approximately 818.3 GiB free at inventory time; C: 78.1 GiB. Choose an external location before downloading; final storage demand must be measured. Never place camera photos or bulk image downloads in Git. |
| Official Scryfall schema/policy verification and reproducible catalog snapshot | Before mass ingestion | Official sources and six small layout samples verified. Current exports use gzip JSONL and `jsonl_download_uri`/`compressed_size`, contrary to the old generator's array assumptions. One default_cards snapshot has passed download/hash/gzip validation and schema2 full ingestion/replay (118,601 JSONL records); normalization accepts all source records. It does not contain every printing language. See B0-01 and B1-01 source evidence. |
| JVM/Python dependencies and compatible model/code licenses | Before tool/model experiments | Host: approximately 15.8 GiB RAM, Intel UHD 630; no NVIDIA GPU detected. Python 3.14 has NumPy/OpenCV; model runtimes and Pillow were not found. Bundled Codex Python 3.12.14 also exists with NumPy/Pillow but without OpenCV, torch, TensorFlow, ONNX or ONNX Runtime (read-only check 2026-10-07). A compatible isolated model environment can be prepared later; no production model chosen. |
| Pixel 6a | Initial Android parity/performance and capture tests | Verified physical device: Android 17/API 37, arm64-v8a and 32-bit ARM ABIs reported, 4 KB pages. No emulator connected. |
| Lower-capacity Android 10/API29 device and high-end device; native 16 KB page compatibility evidence | Final physical matrix | Later gate; emulators do not certify autofocus, thermal behavior or physical recognition. |
| R2 public custom domain, signed pack/channel workflow, licenses for publication | Pack/beta distribution | Later gate; keep private upload/signing credentials outside repository/client. |

## Private photo sample checkpoint

- Originals remain unchanged outside the repository. Private generated inventory: `E:/Projects/ManaHub-build/scanner-v2/photo-inventory/inventory.json`; inspection sheets are in that directory. These artifacts must travel privately to another machine if needed; Git contains only this handoff.
- Decoded dimensions: 3024×4032 or 4032×3024. `.MP.jpg` files are treated as one primary still image, not extra presentations from their motion payload.
- Visual inspection of all 97 images shows single-card positive scenes, white/dark/textured-green backgrounds, old/modern/white-border/showcase/borderless/full-art presentations, English/Spanish/German, landscape rotations, sleeve/glare cases and a flip layout. This is an observed inventory, not a certified coverage count by family.
- Repeated identities/printings appear in the sample. Zero byte-identical files does not prove independent presentations; grouping by physical object/session/illustration requires annotation.
- No label or quadrilateral manifest was supplied. Exact identity, face, printing and corners require verified ground truth before scoring. Text can be read for annotation; evaluation must still run with OCR off and include independent masked-text tests.
- Miguel authorized preparing the labels from the photos and consulting only uncertain cases. Unreadable edition/collector/language fields remain unknown until verified; do not turn a plausible visual identification into exact-print ground truth.
- No explicit negative/unknown scenes or generic card backs were observed. Add negatives and separately captured held-out sessions before quality evaluation.
- All inspected images belong to development/smoke use. Do not repurpose them as an untouched final evaluation set or infer statistical independence from filenames.
- B0-02 observed-label annotation is complete and independently validated against all originals: 97 primary titles, 102 visible title fields, languages 60 English / 32 Spanish / 5 German. No clarification needed from Miguel. Canonical English names are proposals pending catalog verification; verified catalog identities/exact printings remain 0 and all 97 quads remain pending. See [annotation report](../scanner-v2/sample-labels.md).
- Private annotated manifest: `E:/Projects/ManaHub-build/scanner-v2/dataset/sample-manifest.json`, SHA-256 `de25a6185ffbc6c74cda2ede9bbe3c98e8898e4b011d534ae139997cf5096a85`. Run its private `validate-sample-manifest.py` with `C:/Python314/python.exe` after moving artifacts. Preserve original bytes; update machine-local roots explicitly.

### Observed-label acceptance (B0-02 closed)

- Produce one private record per supplied file, linked by sample ID, SHA-256, dimensions and original filename; validate all 97 source hashes before using the inventory.
- Keep observed printed name/language distinct from verified canonical game identity/face and exact printing. Each uncertain field remains null or explicitly proposed; identity metrics cannot score unverified labels as ground truth.
- Record annotation provenance/status and only ask Miguel about unresolved cases after inspecting the original image. User-authorized reading of text to prepare ground truth is separate from the OCR-off recognition experiment.
- Use a conservative supplied-batch leakage group until physical-object and capture-session membership are verified. Do not invent independently held-out splits.
- Physical-card quadrilateral labels must be manually verified in the declared decoded-image coordinate system; detector predictions cannot label their own accuracy. Missing corners remain pending, not generated ground truth.
- Deliver a shareable summary with exact completed/pending counts and a private manifest location, preserving originals and keeping images/EXIF/private labels outside Git.

### Canonical enrichment acceptance (B1-02)

- Consume only a verified completed metadata inventory; retain the original B0-02 manifest and hash as immutable provenance.
- Produce a versioned private enriched manifest for all 97 samples, mapping observed titles to authoritative parent/visible-face identity where evidence supports it. Reverse faces, split/adventure/flip panels and meld relationships must preserve their actual physical-image contract. Miguel explicitly confirmed supplied reverse faces must be recognized and linked locally to their parent; samples 005/022/073/083 need this mapping.
- Keep printing-specific face surrogates separate from Oracle game identities. Reprints of the same panel cannot become competing game identities just because source faces lack their own Oracle IDs.
- Store source snapshot/object evidence and verification status for each resolved field. Unavailable localized printings, unreadable collector/set clues and multiple eligible editions remain unknown or explicit candidates; default_cards is not exhaustive for Spanish/German.
- Targeted official metadata verification may resolve localized labels; no source photographs are uploaded. Do not download images/models or run recognition in this task.
- Validate source hashes, parent/face links, printing identity/language consistency, completed/pending counts and preserved development split. Ask Miguel only about unresolved visible evidence after inspection.
- Close with a tracked summary and private reproducible artifacts. Quads, negative scenes, independent captures and recognition quality remain separate subsequent tasks.

## Portable artifact handoff

Git carries the tool source, contracts and evidence summaries, not the private photographs or generated databases. Transfer these external artifacts privately when changing devices; preserve bytes/hashes and update local path roots explicitly.

| Artifact | Current external location | Resume requirement |
|---|---|---|
| Original development photographs | `E:/Projects/scanner_muster` | Preserve all 97 originals and validate against the inventory before annotation/evaluation. |
| Photo inventory and observed-label manifest | `E:/Projects/ManaHub-build/scanner-v2/{photo-inventory,dataset}` | Carry the inventory, manifest, annotation review and validator; the label manifest hash is recorded above. |
| Immutable default_cards metadata | `E:/Projects/ManaHub-build/scanner-v2/catalog/cache/default-cards-20261007090547.jsonl.gz` | Carry its `.verified.json` marker; SHA-256 `43da5fff200a7c8087eaa5b7bf80a64cc17998a7f92ad6b9d7508bafaef5f0d0`. Source date 2026-10-07T09:05:47.951Z; 78,779,281 compressed bytes, 633,902,189 expanded bytes, 118,601 records. Download and schema2 full ingestion/replay are verified; recognition/coverage acceptance remains separate. |
| Catalog database, coverage and runtime reports | `E:/Projects/ManaHub-build/scanner-v2/catalog/default-cards-v2` | Current schema2/generator2.0 inventory; historical v1 remains at default-cards for comparison. Copy only after the writer has closed; pair with the exact snapshot/tool schema. Use the local snapshot command in the ingestion report to validate/replay. |
| Baseline XML/log/source evidence | Paths in B0-01 and B1-01 reports | Keep privately if detailed reproduction/audit is needed; Android acceptance claims still require physical evidence. |

## Next-session procedure

1. Read this checkpoint, ADR-012 and the latest completed task report.
2. Check current Git status/commit and preserve unrelated changes.
3. Resolve the outstanding resource/test gates; do not assume files under another machine's Desktop or external build directory are available.
4. Assign exactly one bounded task to its project owner; record scope, artifacts and acceptance before execution.
5. Update the task ledger, evidence and next action when the task closes. Record durable learnings under the project's memory protocol.
