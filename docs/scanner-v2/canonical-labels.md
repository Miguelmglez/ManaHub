# Scanner V2 canonical development labels

Status: **B1-02 complete**. All 97 supplied photos have verified human-to-catalog
game identities and observed face/panel mappings. Exact printing metadata is
confirmed for 30 photos; the other 67 retain candidates or edition families.
Unknown printing fields are a valid annotation outcome, not recognition failures.

This task changes private annotations and documentation only. It does not implement
the visual recognizer, measure accuracy, certify OCR independence, annotate quads,
or modify application/catalog source code.

## Accepted inputs and integrity boundary

- Immutable B0 manifest: `sample-manifest.json`, SHA-256
  `de25a6185ffbc6c74cda2ede9bbe3c98e8898e4b011d534ae139997cf5096a85`.
- Verified read-only `default-cards-v2/catalog.sqlite`: schema 2, generator 2.0.0,
  118,601 accepted source records, `COMPLETE_SOURCE`.
- Accepted catalog source snapshot SHA-256:
  `43da5fff200a7c8087eaa5b7bf80a64cc17998a7f92ad6b9d7508bafaef5f0d0`.
- Source photos remain in `E:/Projects/scanner_muster`. All 97 original hashes,
  byte counts and OpenCV decoded dimensions were rechecked.
- New manifest: `sample-manifest-v2-canonical.json`, 5,603,003 bytes, SHA-256
  `2792d7e306dce7df438040b6a75411dc4b6173c0222721634ddfe8d8152d68a9`.

Catalog hashes prove continuity with the accepted local source; they do not
authenticate physical cards or constitute a signed upstream authenticity proof.

## Completed annotation coverage

| Field or evidence | Verified | Pending / limitation |
|---|---:|---|
| Human-visible primary titles | 97 / 97 | No unresolved title questions |
| Observed language | 97 / 97 | 60 English, 32 Spanish, 5 German |
| Canonical game identity | 97 / 97 | 79 distinct parent Oracle IDs |
| Observed titles/panels mapped to parent | 102 / 102 | Source face index preserved |
| Supplied physical reverse faces | 4 / 4 | Samples 005, 022, 073, 083 |
| Exact metadata printing identity | 30 / 97 | 67 / 97 retain candidates |
| Exact printing from accepted local catalog | 20 / 97 | 10 verified printings use private supplemental metadata |
| Existing catalog appearance reference | 20 / 97 | No exact reference is invented for another printing |
| Physical finish or authentication | 0 / 97 | Unknown for every photo |
| Manually verified physical quadrilateral | 0 / 97 | All 97 remain pending |
| Verified independent capture attempt | 0 / 97 | Batch leakage grouping preserved |

Of the 97 game identity proofs, 77 use accepted local source objects and 20 use
targeted official metadata linked back to a canonical parent present in that
catalog. The verification methods are 60 English canonical-name matches,
36 localized printed-name matches with accent/case normalization, and one
explicit bilingual semantic comparison. The local-source group consists of the
60 English matches, 16 localized matches and that semantic comparison.

These are annotation completeness counts. They are not scanner predictions,
recognition accuracy, language robustness measurements or printing-selection
performance results.

## Identity, face and printing decisions

Observed text remains human evidence. The canonical identity has its own parent
Oracle ID, English name, source object and source SHA-256. Face mappings retain
each observed title, canonical face name, source face index and parent relation.
A source face Oracle is copied only when supplied; an absent face Oracle remains
null rather than being fabricated from the parent.

Candidate joins require the observed physical layout as well as eligible paper
metadata. A shared face name can otherwise identify a different parent:
sample 039 is the normal card **Seething Song**, while a prepared spell with that
name belongs to **Blazing Firesinger // Seething Song**, with a different Oracle
identity. That prepared parent is explicitly excluded for this photo. Art-series
and reversible objects are also excluded; `Plains // Plains` reversible metadata
does not establish the observed normal land's game identity.

This layout filter preserves legitimate multi-panel cards. Prepared and flip
cards retain both visible panel mappings, and physical reverse samples retain
face index 1 of their verified parent. A combined root name is never substituted
for a visible face name. The four supplied reverses are represented as observed
faces, not as generic card backs.

Sample 018 has an explicit `human_bilingual_rules_cost_stats_and_flip_comparison`:
its Spanish Student/Tobita panels match the accepted local flip object's {1}{U}
cost, 1/1 front, flying-triggered flip, 3/3 legendary reverse panel and ability
giving controlled creatures flying. The official `CHK/93/es` request returns
404. Its game identity is verified by that semantic comparison; no Spanish
printing UUID is asserted.

A printing is confirmed only when inspected edition/collector/language evidence
selects a source metadata object. Candidates record their actual source language
and `photographed_language_match`. An English printing associated with a Spanish
photo is an edition-family candidate, never that photo's exact printing. Having
one retained candidate alone does not establish an exact printing. Available
finishes in metadata do not establish the photographed physical finish.

Sample 056's official Spanish-language metadata lacks a localized printed title.
Its printing evidence uses its visible edition/collector, source language and
verified parent, with `localized_title_metadata_available=false`. Its human title
and identity translation have separate localized evidence.

## Corrected B0 observations

The B0 file is preserved byte-for-byte. Seven field correction records across
five photos are stored in the new manifest: one each for 008, 067 and 080,
and two each for 042 and 054. The corrected fields appear in that version:

- 080: proposed **Obsidian Golem** corrected to canonical **Obsianus Golem**,
  supported by the catalog's Spanish printed name.
- 008: collector transcription corrected from `0006` to readable `0005`.
- 042: expansion-symbol interpretation corrected from eighth to ninth edition;
  border corrected to white. John Avon 331/333 edition families remain candidates.
- 054: newly transcribed Salvat `12/60`, Bob Eggleton, with white border replacing
  the previous broad black/borderless observation. A12/F12 remain candidates.
- 067: the rechecked footer is Salvat `29/60`, not `201/360`; it selects A29.

The verified catalog layout status replaces the inherited pending-layout status.
Appearance-reference uncertainty remains separate from verified layout/face
mapping, so an unknown exact image reference cannot invalidate a verified parent.

## Bounded official metadata supplement

Only known set/collector/language routes were queried after local joins. There
were **27 requests**, **129,483 response bytes**, and **21.98 seconds** recorded:
26 card objects and the single preserved 404 response for sample 018. Responses
and their SHA-256 values are frozen privately. The task stayed below its limits
of 150 requests, 30 MiB and 1,800 seconds.

Requests used HTTPS `api.scryfall.com`, an explicit User-Agent and JSON Accept
header, a redirect allowlist, at least 150 ms spacing, and bounded retry/backoff
handling. No card-name enumeration, additional bulk source, image or model was
downloaded. No photos or private label payloads were sent, and EXIF location was
not read. A complete cached evidence set is reused without new requests or
overwriting the original ledger.

The private supplement is preparation evidence, not an application runtime
dependency. `default_cards` is not an all-language printing catalog. The ten
confirmed supplemental printings are not yet ingested into the app catalog.
Future local packages must include the necessary accepted metadata before those
exact-language printings can be offered offline. Missing metadata must remain
an explicit local limitation; the scanner must not silently call a backend.

## Private artifacts and reproduction

All private artifacts are under
`E:/Projects/ManaHub-build/scanner-v2/dataset`:

- `sample-manifest-v2-canonical.json`: enriched 97-record manifest.
- `manual-observations-v2.json`: inspected constraints, correction history and
  semantic comparison evidence.
- `canonical-review-v2.md`: complete private 97-row review and pending cases.
- `source-evidence-v2/`: 4,010 original raw local source objects, hashed against
  their accepted catalog rows; shared candidates reuse source files.
- `official-evidence-v2/`: 27 exact response bodies and request ledger.
- `canonical-validation-v2.json`: final validation result.
- `artifact-index-v2.json`: private artifact byte counts and hashes.
- `enrich-sample-manifest-v2.py`, `validate-canonical-labels-v2.py`,
  `fetch-official-evidence-v2.py`, `prepare-manual-observations-v2.py`, and
  `prepare-canonical-review-v2.py`: reproducible private scripts.
- `local-candidate-evidence-v2.json`: frozen join input for reproducing enrichment.

Copy the dataset folder, accepted sibling catalog and original photo corpus to
the next device. Evidence paths are relative to the dataset folder. Scripts
resolve their own folder rather than depending on the original Windows root;
the validator accepts explicit relocated photo/catalog paths. Its environment
was Python 3.14.4, OpenCV 4.13.0 and NumPy 2.4.4.

```powershell
& C:/Python314/python.exe -B E:/Projects/ManaHub-build/scanner-v2/dataset/validate-canonical-labels-v2.py --photo-root E:/Projects/scanner_muster --catalog E:/Projects/ManaHub-build/scanner-v2/catalog/default-cards-v2/catalog.sqlite
```

Run with `--report <private-output.json>` to save a new validation report. The
validator performs no network requests or database writes. Enrichment can be
reproduced with the frozen join input, manual observations and metadata evidence;
the original B0 manifest remains an immutable input.

## Verification and next task

Final validation: **PASS**. It checked all 97 original hashes/dimensions,
4,036 used source objects, all 102 panel mappings, parent existence in the
accepted catalog, exact/candidate languages, 30 chosen printing memberships,
67 explicit pending states, the four reverse mappings and 20 appearance
references. Eight mapping regression tests pass, including wrong parent,
wrong-language claims, invented finish, missing chosen candidate, prepared-name
collision, multi-panel/reverse preservation, missing localized metadata and
single-candidate uncertainty.

No human identity questions remain. The 67 unknown exact printings have retained
candidates; unreadable or indistinguishable footer distinctions are not guessed.
All records retain `development_smoke`, the supplied-batch leakage group and
`evaluation_allowed=false`. This corpus has no negatives and does not meet the
ADR's final evaluation dataset requirements.

The next separate task is B1-03: manually verified physical-card geometry with
the frozen decoded coordinate contract. It has not been started in this task.
