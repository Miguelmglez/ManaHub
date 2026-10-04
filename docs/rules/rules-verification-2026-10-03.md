# Native rules verification

Date: 2026-10-03

## Official baseline

The [official Rules page](https://magic.wizards.com/en/rules) linked the English
[2026-09-25 TXT](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt)
when inspected on 2026-10-03. The downloaded bytes strictly decode as UTF-8. The document header
confirms the effective date; original wording, introduction and copyright notices remain packaged.

| Property | Verified value |
| --- | --- |
| Byte count | 977,752 |
| SHA-256 | `8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca` |
| Schema | 1 |
| Chapters | 9 |
| Body sections | 147 |
| Rules/subrules | 3,166 |
| Glossary entries | 741 |
| Introduction/glossary index/credits nodes | 3 |
| Total generated nodes | 4,066 |

Reproduce the independent inventory and manifest check with:

```powershell
python -X utf8 scripts/verify_rules_baseline.py
```

The verifier accepts BOM/CRLF variants and rejects invalid UTF-8, a half-document truncation,
duplicate rule identifiers and a changed TOC/body heading. Its local inventory run took 32.55 ms;
this Python host result is separate from Android parsing, indexing and search acceptance.

The verifier also rejects a truncated final notice paragraph. Whole-paragraph omissions cannot be
certified from syntax/counts alone: the accepted hash detects them, and changing an existing manifest
requires the explicit `--reviewed-change` acknowledgement after content/count/notice review. Runtime
candidate validation separately compares the incoming edition with the last accepted edition.

The 4 MiB source bound provides over four times the current source size; 10,000 nodes provide over
twice the current inventory. Candidates must pass complete structural validation rather than
truncate at a cap. The current download URL is provenance, not a permanently current endpoint.

## Home editorial review

`rules-tip-reviewed-metadata-2026-10-03.json` preserves the 146-entry original title/category inventory.
Its ordered UTF-8 `${category}:${originalTitle}` lines retain SHA-256
`8341e5cc63d5f5af40300f2d54d7660341e065168b0dd9eb4cd83a5830b56f43`.
All 146 literal IDs are unique. Thirty-five documented wording corrections preserve their original
text and reasons in the review artifact. All 83 reference occurrences exist in the verified edition;
105 entries use explicit curated search fallbacks without claiming an official quotation.

Representative corrections concern the removed combat damage assignment order, Foretell timing,
Suspend exile versus casting, Commander reminder text, mana abilities and Convoke, Amass choosing
an Army, Crime targets and speed increases. The artifact retains exact before/after text and references.
The obsolete title `Multiple blockers, ordered` is explicitly corrected to `Multiple blockers,
flexible damage`; its literal stable ID, original review title, catalog position and daily rotation
remain intact. Runtime rotation checks use stable IDs so correcting wording cannot change identity.

## Runtime acceptance

Targeted Kotlin verification passed 56 tests: 11 real-corpus/parser/repository tests, 5 shared
state-holder tests and 40 Android unit tests for editorial metadata, shortcut compatibility and
source boundaries. Final `assembleDebug` and `assembleDebugAndroidTest` completed after Kotlin
sources were frozen, including the saved initial-highlight flag and human-readable glossary links.

Device acceptance uses API36 x86_64 (`ManaHubTransferApi36`, 4 GiB emulator RAM, 1080 x 2280,
440 dpi) on `emulator-5556`. The dedicated framework user is
`ManaHubRules-c1fb048a-8eb6-45a4-96ea-bb16959c20f3`; no physical-device account or existing
emulator-user data is used. The packaged and installed runner was verified as
`androidx.test.runner.AndroidJUnitRunner`, targeting `com.mmg.manahub`.

Installed application APK SHA-256:
`80d049d1744c4e1074b0027e122e3db4a38757bb34a64a446446ea7d5982accb`.
The two `RulesOfflineAcceptanceTest` tests passed. They exercised the real packaged corpus,
edition-store reopen, abandoned staging cleanup, atomic-pointer backup recovery and tampered hash
rejection. Staging/backup recovery is a filesystem simulation; it does not claim a process killed
at an arbitrary durable-write boundary.

The first Android measurement reported parse 1,447 ms, index construction 933 ms and warm search
p95 12.3732 ms for 80 operations across eight queries (`702.5a`, `702.5`, `deathtouch`, `commander`,
`priority`, `state based actions`, `trample`, `the`). Pages were bounded to 50 and exact `702.5a`
ranked first. These timings exclude debounce and UI rendering. The initial uncollected heap delta
was negative because GC crossed the observation window; it is not an index-memory measurement.
The corrected instrumentation APK SHA-256 is
`d99ed742b6c5838250dee7e8385bf7ebc05710e5f109e92e11abdedf74625f42`.
Both tests passed again with stabilized measurements:

| Android measurement | Result |
| --- | --- |
| Complete parse | 1,385 ms |
| Index construction | 862 ms |
| First `commander` query | 32.069651 ms |
| Warm-query p95 (80 operations) | 13.169021 ms |
| Post-GC live heap before parse/index | 12,509,184 bytes |
| Post-GC live heap with edition/index retained | 16,015,360 bytes |
| Approximate additional retained heap | 3,506,176 bytes |

The initial source snapshot already exists at the heap baseline; this incremental retained value
is not total application memory or peak allocation. The 150 ms warm-query target passes. Cold parse
and index work are separate and run on the background dispatcher in production.

The emulator host exited during theme inspection and was restarted from the same persistent AVD.
An instrumentation attempt during cold boot never entered its tests: Android timed out startup
amid simultaneous SystemUI, Messaging, GMS and Dialer ANRs. The attempt was not counted as a passing
run. After boot settled, the same APK/runner completed both tests in 3.703 seconds. The original
isolated user and existing transfer fixture user were preserved.

Observed guest UI checks include the compact Profile entry and offline index; exact `702.5a`
ranking/highlight; nested reference navigation to section `303`; repeated taps without an extra
destination; and Back to the prior reader, search and Profile context. Deliberately scrolling the
reader to its section header at position zero survives a nested reader and Back without another
automatic jump to the highlighted subrule.

Broad search `the` loaded a second page and preserved the observed result position after opening
`103.1b` and returning. No-results guidance and a nonblocking offline update failure were observed;
the verified edition stayed readable. Shortcut customization replaced only the fourth slot with
Rules, retaining Scan, My Decks and Search, and the shortcut opened the same index.
The same four choices remained after process recreation. The Home Rules Tip header opened the
index; its displayed body opened the curated `empty library draw` query and Back returned to the
same Home board and tip. Refresh independently rolled the tip to Boast while staying on Home;
tapping that new body opened its own `boast` query, with glossary and `702.142` results.

Saved-state process recreation was tested on the existing Rules task, not with force-stop. This
fixture user had no launcher capable of stopping the app on HOME, so system Settings was brought
forward. After confirming `STOPPED` / `mAppStopped=true`, the exact ManaHub process owned by
`u10_a216` (UID 1010216) was killed. Task `1000003` resumed with PID changing from 7805 to 8283;
query `the`, page two and the observed `103.1c` / `103.2` row bounds were preserved. An earlier
`am kill` was a no-op and is not counted. This test covers Android saved UI restoration, not a
process killed during edition activation.

The final visual checks use the same persistent API36 AVD with `-gpu swangle -feature -Vulkan`.
Windows Application Error recorded repeated native QEMU access violations (`c0000005`); its
foreground session also exited with code 1. These host exits are separate from an Android Java
exception. Local SDK help and the [Android emulator documentation](https://developer.android.com/studio/run/emulator-acceleration)
identify the software ANGLE mode; Vulkan was disabled for this additional visual acceptance.
The search benchmark above was measured before that graphics override.

Observed additional checks passed at font scale 2.0 and an 880 x 2280 pixel override with 440 dpi
(320 dp width): index/search controls remained visible, the exact `702.5a` result was readable
above the displayed IME, and the expanded document-information sheet exposed the complete update
action. A validated Wi-Fi network then completed the real official-page/TXT update check and showed
`Rules are up to date`, retaining the effective 2026-09-25 edition. Native selection handles and the
Copy/Select all toolbar were also observed. Direct Back from Rules returned to the actual Profile
Overview, including its Rules and rating links.

NeonVoid was selected through the actual Settings palette picker. Its index and `702.5a` reader
were inspected in screenshots, including font scale 2.0 at 320 dp width; the wrapped official text
and Back/information controls remained readable. Screenshots were deleted after inspection.
TalkBack was enabled only in the test user and verified as bound; with the service active,
`Open reference 303` opened the native `303. Enchantments` reader. This establishes service-on
activation, not a complete focus-order or spoken-quality audit; the emulator runs without audio.
Accessibility preferences were restored after this check.

The software ANGLE fixture also later exited natively. A final additional palette check uses the
same AVD with `-gpu host -feature -Vulkan`, whose log identifies Intel UHD Graphics 630. This second
graphics override does not change the benchmark environment reported above.
HallowedPrint was selected through Settings on that final host-GPU run. The `702.5a` search and
reader screenshot showed cream surfaces, dark official text, readable selected-state contrast,
the section `303` link and Back/information controls without clipping at font scale 1.0 and the
physical 1080 pixel width. The two requested palette extremes therefore have actual runtime
evidence; compatibility across all twelve palettes also received a source-token audit.

Read-only security, edge-case and telemetry reviews passed with no remaining rules findings.
Telemetry uses closed source/result/failure categories and count buckets; it excludes queries,
document text, remote/local paths, identifiers and external throwable causes. No commit, push or
PR was requested or created, so a later publication must still run its staged security gate.

Coverage limits: no signed-in account was used; the ungated account/gamification entry behavior was
reviewed in source alongside guest runtime checks. Main-frame latency and peak memory were not
quantitatively measured. TalkBack spoken quality and complete focus order were not certified.
Atomic-store crash recovery used staging/backup simulation, separately from the real saved-UI
process recreation.

Cleanup verified Wi-Fi/mobile data/airplane mode restored to 0/0/0, font scale 1.0, physical
1080 x 2280 at 440 dpi, and accessibility disabled with no enabled-service key for the test user.
Temporary screenshots, UI dumps and diagnostic logs were removed. The UUID-checked Rules fixture
was stopped and removed after restoring framework user 0; the pre-existing transfer fixture user
11 was preserved. The emulator started for this task was then closed. The temporary execution
plan is retired; this record and ADR-011 are the durable implementation handoff.

No web implementation or build is included. Web adapter/routing work remains recorded in the
existing restoration debt document. The graphify CLI is unavailable in this environment; normal
graph refresh is deferred, and current source was checked against the existing orientation graph.
