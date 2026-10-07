# Scanner V2 development physical geometry

B1-03 is complete: all 97 photographs have an agent-reviewed geometry decision. There are 96 complete physical-card quadrilaterals with explicit estimated uncertainty and one partial target. Integrity and coordinate validation passed, including 11 synthetic contract tests. These annotations support development experiments; they do not certify detector accuracy or final held-out acceptance.

Authoritative acceptance: [ADR-012](../adr/ADR-012-scanner-visual-recognition.md) and the [execution checkpoint](../plans/scanner-v2-progress.md). Canonical identity and printing annotations remain in the separate immutable [B1-02 version](canonical-labels.md).

## Results and denominators

| Result | Count |
|---|---:|
| Source photographs and completed agent reviews | 97 / 97 |
| Complete reviewed physical-card quad estimates | 96 / 97 |
| Partial target with full quad null | 1 / 97 |
| Visible nominal corner estimates | 387 / 388 corner decisions |
| Missing corner, explicitly unknown | 1 / 388 |
| Unreviewed photos or outstanding review decisions | 0 |
| Complete quads carrying estimated uncertainty | 96 / 96 |
| Automatic proposals retained unchanged | 0 |
| Independently reviewed human annotations | 0 |
| Quads with certified pixel accuracy | 0 |

The visible-corner uncertainty distribution is 188 estimates at 40 px, 8 at 50 px, 116 at 60 px and 75 at 80 px. These are agent visual estimates in decoded-image pixels, **not statistical confidence intervals or guaranteed containment bounds**. Two decimal places preserve coordinate transformations rather than imply subpixel precision. Glare, rounded corners, diffuse white borders, dark backgrounds and sleeves limit precision. Every complete quad remains an uncertain estimate.

All records preserve `development_smoke`, the supplied batch leakage group and `evaluation_allowed=false`. Capture independence is unverified. This task measured neither recognition quality nor detector/localizer accuracy. Miguel confirmed no independent negative corpus is currently available; this remains a separate acceptance gap.

## Physical extent and coordinate contract

Coordinates refer to the exact OpenCV `IMREAD_COLOR` EXIF-aware decoded primary JPEG still, matching the immutable v2 dimensions. The source primary image is not the embedded motion-video sequence. Do not apply EXIF orientation a second time; no location metadata was inspected.

The coordinate origin is the image's upper-left, x grows right and y grows down. Complete quads have four distinct finite in-bounds points, clockwise in this Y-down system, with positive shoelace area. `q0` is the point with the smallest lexicographic `(y,x)` pair; remaining points continue clockwise. `seed_corner_indices` preserves each corner's review and uncertainty association through cyclic reordering. `canonical_orientation.clockwise_quarter_turns_to_upright` is a separate observed title orientation, not the image-order starting corner.

The target is physical cardboard. Rounded corners use the estimated virtual intersection of their adjacent straight physical edges rather than a point on the rounded arc. The sleeve, artwork frame and camera guide are excluded. Limited boundary confidence is recorded where sleeve/glare/low contrast makes that distinction uncertain. Photographed perspective is validated for convexity and noncrossing geometry without a fixed aspect-ratio rejection.

Sample `smoke-094` is `annotated_partial_target`: the lower-left physical corner extends below the decoded source frame. Its full quad is null, the three other visible corners have uncertain estimates and the missing corner is `unknown_not_extrapolated`. Its new scene override is `single_partial_card`, with `rejection_expected_full_card_scope` for a future whole-card localizer test. The entire source was inspected as a resized full image, and native corner tiles confirm that the missing boundary is not a review crop. The source size is 3024 × 4032; its source SHA-256 is `34c38a0164f7145dbd5cf04ba7c1a31da3e835321b58df47937111428ac93311`.

That geometry finding corrects the earlier coarse `single_card_positive` scene assessment in a new provenance-linked version; B0/v2 are unchanged. A known identity on a partial photo neither establishes a whole-card positive nor supplies an independent negative/open-set denominator.

## Review provenance

Review was performed by the agent (`agent_visual_inspection`, `manually_reviewed_by_agent_with_estimated_uncertainty`). `independent_human_review=false` is explicit throughout. Miguel authorized annotation preparation but has not independently adjudicated these coordinates.

The preserved workflow is:

1. Generate 97 Canny/contour or central-placeholder proposals, all marked pending review.
2. Inspect all full-photo overviews and place approximate manual physical outlines. All 97 automated proposals are replaced; the seeds themselves remain unaccepted proposals.
3. Inspect four native 256 × 256 corner tiles per photograph and record decoded-pixel corrections and uncertainty. For 094, one tile confirms a missing boundary rather than supplying a coordinate.
4. Inspect all 25 corrected sheets at native scale and revisit discrepancies. Upper corners in 023 and the upper-left corner in 081 were corrected in second review; uncertainty was increased for 009/018/027. These five follow-ups are preserved in the private review record, and affected corrected overlays were inspected again.
5. Freeze the separate manifest, verify hashes and geometry, and run synthetic contract tests.

The initial sheets are 2080 × 562 and were displayed by the tool at 2048 × 553; their small display reduction is recorded. Final corrected sheets are exactly 2048 × 562 and were displayed without reduction. Each final tile stores its decoded crop origin, sheet origin and scale 1:

`decoded_x = sheet_x - sheet_origin_x + tile_origin_x`, with the equivalent y formula.

Gray padding is outside the original image. Final partial-094 review draws no closed extrapolated quad and no nominal marker for the missing corner. Additional full-source views of 081/094 use a uniform scale of 900/4032 to produce 675 × 900 images; they are full-image resizes for extent decisions, not fine-precision evidence.

## Reproducible validation

Private artifacts reside at `E:/Projects/ManaHub-build/scanner-v2/dataset/geometry-v3`. Originals, derived images and coordinate files remain outside Git. The tracked artifact is this report.

```powershell
C:/Python314/python.exe E:/Projects/ManaHub-build/scanner-v2/dataset/geometry-v3/validate-geometry.py
```

The final command exited 0 and wrote `geometry-validation-v3.json`, status **PASS**. It checked all 97 original hashes, 381,329,720 source bytes, 97 decoded dimensions, 25 final overlay hashes, 388 corner decisions, 387 nominal coordinate transforms, correction provenance, uncertainty counts, cyclic associations, full/partial eligibility and development-only usage. Both parent annotation manifests retain their accepted hashes. No supplied source had a dimension swap between EXIF-aware and orientation-ignored decoding; a synthetic rotated JPEG tests the orientation contract independently.

The 11 synthetic tests cover clockwise/cyclic ordering and provenance, counterclockwise conversion, lexicographic starting corners, nonfinite/bounds/count/boolean rejection, duplicate/degenerate/concave/crossed shapes, perspective without an aspect-ratio gate, rounded-corner virtual intersections, native/scaled coordinate mappings, a three-visible/one-missing partial target with full quad null, rejection of missing corners on full targets, and EXIF-aware JPEG dimensions without double rotation. Synthetic fixtures use generated in-memory data; they do not certify physical annotations or recognition.

Runtime: Python 3.14.4, OpenCV 4.13.0, NumPy 2.4.4. No Android production code changed, so no additional Android build or scanner baseline run was required for this annotation-only task.

## Frozen artifacts and transfer

| Private artifact | Bytes | SHA-256 |
|---|---:|---|
| `sample-geometry-v3.json` | 474626 | `8d0772cf047d6dd1f1adc892b294b38159997a4a89cdde04f9daee4b93dca188` |
| `manual-corner-review.json` | 133558 | `0607e43575cb2d29a779170b0e62d2733d4a3c99c8d39b77fb8a263597844b28` |
| `validate-geometry.py` | 8074 | `108612e05d418e3651b67b733ba8c9422abf9955c03de36ad8ced4b251854ce3` |
| `test_geometry_contract.py` | 4746 | `93d769e256739ab20b285051b0e4708c5f5351f9f1236deacb3e02d158119fd8` |
| `geometry-validation-v3.json` | 1460 | `b5def40215d4c0387fc1a5b726eaf57c7e898c513f80681aa1829c5fb09979d4` |
| `geometry-artifact-index.json` | 14256 | `db4099486f62a1e55f9bc715a99c037fb4b7b6cdf4617891847dea7e1ed21155` |

Immutable parents: original `sample-manifest.json` SHA-256 `de25a6185ffbc6c74cda2ede9bbe3c98e8898e4b011d534ae139997cf5096a85`; canonical `sample-manifest-v2-canonical.json` SHA-256 `2792d7e306dce7df438040b6a75411dc4b6173c0222721634ddfe8d8152d68a9`.

The geometry index inventories 83 transferable files totaling 35,590,922 bytes, excluding the index itself and Python cache. Carry the entire indexed geometry directory, immutable parent manifests and all original photos privately to another device. The private `annotation-review.md` explains the files, reconstruction and interpretation. Keep `geometry-v3` beside both parent manifests; run the validator with the destination Python and optional `--photo-root <new-photo-directory>` / `--output <new-report-path>`. Do not regenerate annotations to hide failed checksums. Changing review decisions or regenerating affected images requires fresh visual review before freezing a new version.

## Remaining acceptance and next task

No user question remains for this bounded annotation task. Independent human adjudication, tighter corner uncertainty, independent captures, negatives and final held-out geometry remain pending. Development localization/warp experiments must report sensitivity to these estimated radii; these labels cannot alone certify ADR localization acceptance.

The next scoped task is B1-04: define reference coverage, rights/cache and preprocessing/experiment contracts before acquiring reference images. No reference download, model experiment, recognition benchmark or Android pipeline implementation was started in B1-03.
