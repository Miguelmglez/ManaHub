# Scanner V2 — Block 0 baseline and resource inventory

Updated: 2026-10-07 (Europe/Madrid). Task: **B0-01**, sequential owner `android-kotlin-architect`.
Specification: [ADR-012](../adr/ADR-012-scanner-visual-recognition.md).
Resume ledger: [execution checkpoint](../plans/scanner-v2-progress.md).

## Scope and evidence boundary

This task inventories the current scanner, recovers the interrupted OCR baseline, checks official catalog contracts and lists prerequisites. It does not implement a visual engine, choose a model, download a full catalog, publish a pack, install an app or change accounts/collections on the phone. Expected ADR metrics are not measured results.

The original session started on `feature/image-scanner`, commit `6707353f1451f2d28d6e4357c53ffea65fad0d87`. The resumed baseline observed commit `0b3a2a3fa75de61d07dd72385f1cb34d270ccdc7` on the same branch; its sole delta is the unrelated CollectionScreen fix. Preserve it. The parent verified there was no intervening scanner change. Pre-existing `scripts/__pycache__/` remains untouched. Only documentation and the parent's ignore entries are being changed by this task/session.

Navigation began with `graphify-out/wiki/index.md` and `Community_692.md`, then targeted current source. A wiki article named `Scanner_Scannerviewmodel.Kt.md` actually lists GameResultScreen; its title is not current scanner evidence. Read scanner visual-plan, August reliability and September deck/overlay memories. The old improvement memory's `unique=art` description is superseded by current source's `unique=prints` query.

## Current Android environment

| Item | Observed configuration / limitation |
|---|---|
| Wrapper / JVM | Gradle 9.4.1; project daemon requests JetBrains JDK 21. |
| Android / Kotlin | AGP 9.2.1, Kotlin 2.3.20, KSP 2.3.6 declared. |
| SDK | compileSdk 37, minSdk 29, targetSdk 36; SDK at `E:/AndroidSDK`. |
| Scanner dependencies | CameraX camera-core/camera-camera2/camera-camera2-pipe/camera-lifecycle/camera-view/camera-video resolve to 1.6.2; transitive viewfinder-core resolves to 1.5.1. ML Kit text recognition `19.0.1` declared. OpenCV and old TFLite lines are commented out, not an installed V2 runtime. |
| Existing frameworks | Hilt 2.59.2, Koin 4.2.2, Room 2.8.4, coroutines 1.10.2 declared. Preserve the scanner's legacy Hilt/Android integration; no migration or web work in this task. |
| External caches | Existing `E:/Projects/ManaHub-build/gradle-user-home` and `E:/Projects/ManaHub-build/gradle-project-cache` reused. No per-agent cache created. |
| Build outputs | Root build script derives `E:/Projects/ManaHub-build/ManaHub-<canonical-checkout-hash>/<project-path>`. Actual baseline report location is recorded below. |
| Host | Approximately 15.8 GiB RAM, Intel UHD 630, no NVIDIA GPU detected. Initial inventory: C: 78.1 GiB free; E: 818.3 GiB free. These are inventory readings, not reserved space or benchmark results. |
| Python | Python 3.14; NumPy and OpenCV available. Pillow, torch, TensorFlow and ONNX runtimes were not found in the initially inspected environment. Prepare and lock a compatible tool environment before model experiments; no arbitrary runtime/model selected. |
| Phone | User-authorized physical Pixel 6a connected; read-only inventory verified Android 17/API 37, ARM ABIs and 4 KB pages. No serial, account or other private identifier is recorded here. One phone supports an initial spike, not the full release hardware matrix. |

## Existing OCR and integration contracts

Paths below are repository-relative anchors, not permission to revive deleted historical files.

| Current source | Verified behavior and implication |
|---|---|
| `app/src/main/java/com/mmg/manahub/feature/scanner/data/CardRecognizer.kt` | 800 ms minimum OCR interval, 2,500 ms OCR timeout, 5,000 ms watchdog, score gate 45, two normalized-name reads before resolution, local-first/cache ladder and six lookup attempts per 10 seconds, each at most two HTTP calls. Generation and 4,000 ms result-age checks discard late resolved results. Preserve OCR behavior; visual requires its own monotonic capture/generation contract. |
| `.../presentation/ScannerScreen.kt`, `CameraPreview` | `KEEP_ONLY_LATEST`; user pause clears analyzer while preview stays live; covering overlays clear analyzer/unbind camera after 250 ms debounce; rebind explicitly restores torch. Disposal cancels recognizer scope and shuts down executor after detaching. The visual path must prove frame/buffer ownership on cancellation and native timeout instead of assuming coroutine cancellation stops readers. |
| `.../presentation/ScannerScreen.kt`, `FrameMetadataAnalyzer` | Delegates analysis and closes on emergency exception; does not capture the obsolete `isPaused` lambda described in the old draft. No historical double-close claim is treated as a reproduced finding. |
| `.../presentation/ScannerViewModel.kt:338` | Uses `HIGH_CONFIDENCE_FRAMES = 1` after OCR resolution. A comment mentioning adaptive stability does not implement visual stability. V2 must decide identity with separate calibrated evidence and distinct captures. |
| `.../domain/model/RecognitionResult.kt` | `Identified(Card, similarity, ambiguous, corners, languageFallback)` is an OCR final-result contract with Android PointF. It has no candidate list, package version, offline identity or printing certainty. Keep it for OCR; do not use Hamming/cosine as its release confidence. |
| `.../presentation/ScannerViewModel.kt`, queue paths | Collection uses shared CardQueueRepository/CardQueueActions; deck has a persistent deck-specific queue. Existing commits, owners, quantity/attributes and XP remain authoritative. Recognition experiments must not mutate collection or reinterpret repeated captures as extra copies. |
| `shared/core-data/src/commonMain/kotlin/com/mmg/manahub/core/data/remote/ScryfallRemoteDataSource.kt:362` | `getCardArtVariants` currently requests `unique=prints`, paper only, and consumes a single `.data` page. It has neither exhaustive pagination nor explicit all-language coverage. Do not reuse it as V2's complete appearance-to-printing inventory. No unrelated source fix made here. |
| `.../feature/scanner/di/ScannerModule.kt` | OCR and sound are process-wide Hilt singletons. UI cannot close them. August removed the previous embedding runtime; historical tooling is not proof of a working current engine. |

V2 contract boundary, following ADR §4 (not a new production API yet):

- **Game identity:** typed Oracle identity plus face identity where relevant; an explicit typed fallback for genuinely absent Oracle IDs. Names and UUIDs do not replace that distinction.
- **Visual appearance:** image-bearing parent/face, layout and presentation coordinates, optional illustration ID and many-to-many identity links. Preserve indistinguishable conflicts.
- **Printing:** Scryfall UUID, set, collector number, language and available finishes; foil, condition and quantity remain user choices. A matched appearance does not prove edition/language.
- **Decision evidence:** candidates and reference/face links, geometry/quality, monotonic capture timestamp, track/camera/mode/pack generations, recipe/model/package identifiers and temporal evidence. Separate identity acceptance from printing selection and hydration.
- Only **ReadyForQueue**, with a valid complete cached/hydrated Card and explicitly resolved printing, adapts to existing queue actions. Offline identity/NeedsPrinting/NeedsDetails never manufactures missing Card fields.
- DFC reverse maps to its parent; split/adventure/flip face metadata is not a separate physical image by default; meld relationships can require multiple parents/manual choice. Reversible faces may have separate Oracle identities. Generic backs and unsupported objects remain explicit negatives, not guessed playable cards.

## User-supplied physical-photo sample

Source: `E:/Projects/scanner_muster`, supplied by Miguel for testing. Originals stay unchanged and private, outside Git. Parent inventory and inspection evidence: `E:/Projects/ManaHub-build/scanner-v2/photo-inventory/inventory.json`.

- 97 JPEG files, total **381,329,720 bytes**; all primary still images decoded. Decoded dimensions are 3024×4032 or 4032×3024. `.MP.jpg` motion payload does not create additional presentations.
- Zero byte-identical SHA-256 duplicates; repeated card identities/printings are visibly present. File count is not identity count or independent attempt count.
- Parent inspected all images: single-card positive scenes; white/dark/textured green backgrounds; retro/white-border/showcase/borderless/full-art; English/Spanish/German; rotation, sleeves and glare; a flip card and DFC fronts. These are observed categories, not scored per-family coverage.
- No supplied ground-truth identity/face/printing/session/physical-object manifest or physical quadrilateral annotations. No observed explicit negative scenes, generic backs, held-out capture sessions or frozen final evaluation corpus.
- Treat the whole inspected sample as **development smoke**. Do not infer train/calibration/final splits or independence from filename timestamps. Annotation may read printed text; runtime evaluation remains OCR-off and requires separately defined masked-text evaluation.

This removes the earlier lack-of-photos prerequisite for initial tool development. It does not satisfy ADR §10's 500 physical cards, 3,000 independent known presentations, 3,000 negative presentations, or release statistical denominators.

## Official Scryfall verification before ingestion

Verified on 2026-10-07 with small HTTPS requests and descriptive `User-Agent: ManaHub-ScannerV2-Baseline/0.1`, `Accept: application/json;q=0.9,*/*;q=0.8`, and at least 150 ms between API requests. The web reader still returned 403 for schema pages; direct correctly headed requests returned HTTP 200. No full bulk/image ingestion was performed.

Private raw evidence, including source HTML, bulk index, six card JSON examples and SHA-256 inventory: `E:/Projects/ManaHub-build/scanner-v2/baseline/scryfall/`. Preserve these privately when moving to another device; live sizes and dates can change.

**Current bulk format differs from the old generator:** exports are gzip **JSONL**, with `jsonl_download_uri` and `compressed_size`; the sampled objects do not expose the old `download_uri`/`size`/`content_type` fields. Stream decompression and one bounded JSON record at a time; do not assume a giant JSON array. Validate metadata rather than silently choosing a fallback URL. [Official bulk schema](https://scryfall.com/docs/api/bulk-data), [live metadata endpoint](https://api.scryfall.com/bulk-data).

| Export | Sample metadata compressed bytes | Meaning verified from official description |
|---|---:|---|
| `default_cards` | 78,779,281 | All card objects in English, or original printed language if only one language exists. Not every localized printing. |
| `all_cards` | 395,442,038 | Every card object in every language. Required when declaring that printing/language coverage. |
| `unique_artwork` | 37,840,193 | Objects representing unique artworks, preferring better scans. Useful for a baseline, not a complete printing inventory. |

These are transfer sizes, not number of references, installed pack size or heap demand. The metadata `updated_at` value is preserved in raw evidence; no bulk content hash exists until the snapshot is downloaded and hashed.

Official [card objects](https://scryfall.com/docs/api/cards) distinguish printing `id`/`lang`, nullable artwork IDs, root and face images, `finishes`, `image_status`, and typed `all_parts` links. `oracle_id` can be absent on a reversible-card parent and present on its faces. [Layouts/faces](https://scryfall.com/docs/api/layouts) document physical arrangement; `card_faces` alone does not imply two physical images.

Six sampled live card objects confirm:

| Sample layout | Parent image | Face images | Relevant observation |
|---|---|---|---|
| normal (Lightning Bolt) | Yes | No faces | Printing can offer multiple finishes. |
| transform (Delver of Secrets) | No | Two | Both face images retain the parent's printing and game identity relationship. |
| split (Fire // Ice) | Yes | None | Face illustration IDs are not uniformly present. |
| adventure (Brazen Borrower) | Yes | None | Separate face text does not imply separate image downloads. |
| flip (Nezumi Shortfang) | Yes | None | Full physical image must be oriented before region extraction. |
| meld (Brisela) | Yes | No faces | `all_parts` contains meld result and two parts; selection cannot assume a unique playable parent. |

The reversible-card null-Oracle case is verified in documentation, not claimed as an additional live sample.

[Official imagery](https://scryfall.com/docs/api/images) specifies normal 488×680 JPG, large 672×936 JPG, PNG 744×1040 with rounded transparency, border_crop 480×680 with much of the border removed, and variable art_crop that may be unsuitable for unusual frames. Thus border_crop is not physical-card extent, and art_crop has no universal coordinate equivalence. Image status distinguishes missing/placeholder/lowres/highres_scan; inventory exclusions must retain reasons/counts.

[Official access FAQ](https://scryfall.com/docs/faqs/i-m-having-trouble-accessing-the-scryfall-api-or-i-m-blocked-17) requires appropriate headers, HTTPS and reduced traffic after 429; use bulk rather than per-name enumeration. Future tooling must honor Retry-After and bounded retries. Code licenses, model-weight licenses and image/data training/redistribution rights are separate pending checks; HTTP access is not publication authorization.

## Earlier embedding attempt

`tools/embedding-generator/` still contains a 228,433,949-byte binary and old tooling. Its README describes MHEV v1 with 576 dimensions, while `version.json` declares 1,024 dimensions and 55,553 entries. Its old JSON-array download description also differs from the now verified JSONL schema. No provenance/parity/physical-accuracy reproduction was performed; this is historical inventory only, not V2 benchmark evidence or a compatible pack to reactivate.

## OCR baseline execution

Before repeating the interrupted invocation, inspected external daemon log and host Java processes. The interrupted external daemon PID was no longer running; surviving daemons used another global Gradle home. No duplicate scanner test build was launched. The abandoned log ends after daemon startup and has no test summary, so it provides no PASS evidence.

Exact resumed command:

```powershell
$env:GRADLE_USER_HOME = 'E:\Projects\ManaHub-build\gradle-user-home'
.\gradlew.bat --project-cache-dir 'E:\Projects\ManaHub-build\gradle-project-cache' --console=plain :app:testDebugUnitTest --tests 'com.mmg.manahub.feature.scanner.*'
```

Full command output is retained privately at `E:/Projects/ManaHub-build/scanner-v2/baseline/scanner-unit-2026-10-07.log`. The tests comprise `ScannerViewModelTest`, `CardRecognizerTest`, `CardOcrAnalyzerTest`, `CardOcrAnalyzerExtractionTest`. The wildcard still compiles all app test sources; an unrelated compile failure cannot be called scanner PASS.

**PASS: exit 0, BUILD SUCCESSFUL in 18m 21s; 106 tests, 0 failures, 0 errors, 0 skipped.** The wrapper reported 94 actionable tasks (41 executed, 53 up-to-date). Kotlin/Java application sources and all app test sources compiled with existing warnings and no compile error; only the selected scanner tests executed.

| Suite | Tests | Failures / errors / skipped |
|---|---:|---|
| ScannerViewModelTest | 47 | 0 / 0 / 0 |
| CardRecognizerTest | 24 | 0 / 0 / 0 |
| CardOcrAnalyzerTest | 7 | 0 / 0 / 0 |
| CardOcrAnalyzerExtractionTest | 28 | 0 / 0 / 0 |

Fresh XML reports: `E:/Projects/ManaHub-build/ManaHub-b76adea31bb5078c/app/test-results/testDebugUnitTest/TEST-com.mmg.manahub.feature.scanner*.xml`; HTML summary: `E:/Projects/ManaHub-build/ManaHub-b76adea31bb5078c/app/reports/tests/testDebugUnitTest/index.html`. Test suite timestamps are 2026-10-07 12:03 Europe/Madrid, distinct from prior unrelated results. End HEAD remained `0b3a2a3fa75de61d07dd72385f1cb34d270ccdc7`; tracked diff remained only `.gitignore`. Captured scanner/test/build-source hashes in `E:/Projects/ManaHub-build/scanner-v2/baseline/source-hashes.json` were unchanged on the final check. No real-camera OCR accuracy, autofocus, temperature or latency result is implied by unit tests. No APK was installed and no physical account/collection state was changed.

After the test invocation completed, resolved CameraX dependencies sequentially with the same external caches:

```powershell
.\gradlew.bat --project-cache-dir 'E:\Projects\ManaHub-build\gradle-project-cache' --console=plain :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.camera
```

Exit 0, BUILD SUCCESSFUL in 11 s. Effective versions are listed in the environment table; no conflicting CameraX version was selected. Output: `E:/Projects/ManaHub-build/scanner-v2/baseline/camerax-dependency-insight-2026-10-07.log`.

## Prerequisites and task gate

| Needed | Available now / pending | Earliest blocking gate |
|---|---|---|
| Consented private photos and phone | 97-photo smoke sample + Pixel 6a available. | Enables initial annotation, baseline tools and mobile spike. |
| Verified annotation manifest | Pending identity/face/printing/physical-object/session, conditions and quads; mark unknown fields rather than guess. | Before scoring locator/retrieval/decision. |
| Independent splits and negatives | Pending; current inspected sample is development only. | Before tuning/calibration claims and held-out quality comparison. |
| Full snapshot and coverage inventory | Official current schema verified; actual hashed snapshot/ingestion not yet run. | Before complete-catalog benchmark. |
| Locked tool/runtime environment | NumPy/OpenCV present; supported Python/runtime versions and license registry pending. | Before experiments/export/parity. |
| Candidate model/weights and training resources | No production model chosen; CPU-only host inventory. CPU smoke experiments possible; measured compute budget or additional GPU resources may be needed for training. | Before trained alternatives/selection, not a reason to prematurely select pHash. |
| Three-phone release matrix / native 16 KB compatibility | Initial Pixel 6a only; low-capacity API29 and high-end/other SoC device pending. | Final physical validation; 4 KB Pixel does not prove 16 KB compatibility. |
| Expanded physical corpus and statistical attempts | Pending ADR denominators and family coverage; smoke photos do not substitute. | Acceptance/promoting visual to default. |
| R2/custom domain, signed channels, keys and publication rights | Later operation gate; no credentials needed for private Block 1 tooling. | Pack/beta publication after measured recipe selection. |

**B0-01 closed:** current integration/resource inventory and official schemas recorded; existing scanner unit-test baseline passes. The full Block 0 resource gate remains partial because verified labels/splits, model/runtime/license inventory and the wider evaluation resources are not yet available. This does not block the next private annotation task.

**Next authorized bounded task, B0-02:** prepare the private sample manifest, annotation conventions and verified labels, owned by one project agent. Miguel authorized preparing labels and consulting only unresolved doubts. Keep all inspected photos development/smoke, retain uncertain ground truth explicitly, and record object/session grouping limits. After that task closes, assign current-schema streaming metadata ingestion separately; do not leave simultaneous tool and annotation tasks unfinished. No product UI/distribution expansion or claim of a viable visual candidate is allowed before the later benchmark/mobile gates.
