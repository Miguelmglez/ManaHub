# Scanner V2 supplied sample annotations

Date: 2026-10-07. Task: **B0-02**. Status: **closed for observed-label annotation**; verified catalog identities, exact printings and physical quads remain separate pending work.

This report follows ADR-012 sections 4, 10 and 11. The supplied sample is ready for development tooling, not a final recognition benchmark or verified identity/printing golden set. No production Kotlin, Gradle, scanner UI or recognition behavior changed in this task.

## Private artifacts

| Artifact | Location |
|---|---|
| Immutable supplied originals | `E:/Projects/scanner_muster` |
| Original inventory and contact sheets | `E:/Projects/ManaHub-build/scanner-v2/photo-inventory` |
| Annotated manifest | `E:/Projects/ManaHub-build/scanner-v2/dataset/sample-manifest.json` |
| Full private annotation index and pending-field review | `E:/Projects/ManaHub-build/scanner-v2/dataset/annotation-review.md` |
| Reproducible validator | `E:/Projects/ManaHub-build/scanner-v2/dataset/validate-sample-manifest.py` |
| Validation evidence | `E:/Projects/ManaHub-build/scanner-v2/dataset/validation-report.json` |

Manifest SHA256: `de25a6185ffbc6c74cda2ede9bbe3c98e8898e4b011d534ae139997cf5096a85`.

Source inventory SHA256: `db866154ebb717fb1df143ac89ffdea9c898e23fbdc9c221a088dccc377e31ba`.

The private photos and labels are outside Git. A continuation on another device needs those artifacts and the original inventory; this repository report alone does not transfer the corpus. Preserve all original names and bytes, update machine-local roots explicitly and rerun validation.

## Evidence and counts

All 97 supplied JPEGs retain their original hashes, byte sizes and decoded dimensions. There are no exact file duplicates. All 97 scenes were inspected through contact sheets, with directed decoded-image crops for 48 samples. Crops and rotations were inspection aids and do not define geometric ground truth. No images were uploaded, originals modified or EXIF location read.

| Field | Result |
|---|---:|
| Structurally validated sample records | 97 |
| Human-verified primary printed titles | 97 |
| Human-verified visible title fields, including secondary panels | 102 |
| Human-verified observed languages | 97 |
| English / Spanish / German photos | 60 / 32 / 5 |
| Proposed canonical English names, pending catalog verification | 97 |
| Catalog-verified game identities / exact printings | 0 / 0 |
| Pending exact printing mappings | 97 |
| Records with transcribed printing/symbol hints | 44 |
| Records retaining visible-but-untranscribed printing hints | 53 |
| Raw observed set-code / collector-number strings | 16 / 28 |
| Manually verified physical quads / pending quads | 0 / 97 |
| Positive single-target scenes / supplied negative scenes | 97 / 0 |
| Unreadable primary titles or languages / questions for Miguel | 0 / 0 |

Observed layout coverage: 82 ordinary single visible faces, three prepared-spell presentations on one face, four DFC fronts, four DFC reverses, two flip cards with both halves visible, and two other Saga presentations. One DFC front is also a Saga. These categories describe photographed presentation; authoritative catalog layout mappings remain pending.

Visual coverage includes modern/retro/white-border/showcase/borderless/full-art presentations, basic lands with different artwork, multiple colors, light/dark/mixed/textured backgrounds, sleeves, reflective sheen and sideways cards. Reflective appearance is not verified foil finish. Capture device, capture sessions and physical-object identities were not inferred from filenames or availability of the connected Pixel 6a.

## Annotation contract

Each record separates human observations from game identity, appearance and printing. Printed titles remain original-language data. English canonical names and translated flip combinations are explicitly proposals; no Oracle ID, Scryfall UUID, illustration ID or exact printing is invented. Raw visible set/collector text is stored under printing hints, while resolved printing fields remain null.

Reverse-face labels must resolve to their parent card before queue integration. The four reverse presentations retain an explicit pending parent-identity field; the private review lists their sample IDs and filenames. Prepared spell titles and flip halves stay attached to the same photographed surface and do not imply separate reference images. A proposed front/reverse relationship remains a proposal until verified against the catalog.

All records use `split=development_smoke` and `leakage_group=supplied-batch2026-10-07`. Four manually supported repeated-appearance pairs are recorded: 002/014, 004/011, 010/013 and 019/020. They establish similar visible art/frame, not identical physical objects or independent attempts. Different basic-land artwork and reprint appearances were not merged simply by name. The entire batch remains excluded from final held-out evaluation.

Quad fields are explicitly pending and reference the exact default EXIF-aware OpenCV decoded primary still dimensions. A future manual annotation must verify all four physical card corners in that coordinate space; detector predictions and inspection crop coordinates cannot become ground truth automatically. Motion-photo payloads do not add independent presentations.

Conditions are qualitative observations: `not_confirmed` or an unmarked reflective flag means the condition was not established, not that the underlying card lacks a sleeve or foil finish. Observable presentation and exact catalog layout are deliberately separate fields.

## Validation

```powershell
& C:/Python314/python.exe E:/Projects/ManaHub-build/scanner-v2/dataset/validate-sample-manifest.py
```

Result: **PASS**, 97 records. Environment: Python 3.14.4, OpenCV 4.13.0, NumPy 2.4.4. The validator independently rereads all originals, checks hashes/byte sizes/decoded dimensions against the inventory, verifies IDs and required statuses, and rejects invented resolved identities, printing values, quads or independence claims.

This is structural and source-integrity validation; it does not validate a recognizer, calibrate thresholds, measure accuracy or demonstrate recognition without text. Reading visible text for human annotation does not satisfy the no-text gate.

## Closure and remaining gates

No user clarification is needed for the readable names and languages. All 97 records are complete under this observed-label contract; pending fields are explicit rather than silently omitted. The next catalog task can use the private manifest to verify canonical identities, parent/face relations and eligible printings.

Before quality evaluation, complete manual quads and catalog mappings, add negative/open-set and independent masked-text captures, record physical-object/capture-session provenance, and freeze independent splits. The sample does not establish 500 distinct physical cards, 3,000 independent positive presentations or 3,000 negative presentations required by ADR-012's development minimum. It also does not meet the final physical hardware/statistical matrix. Those acceptance gates remain pending.
