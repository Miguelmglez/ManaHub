# ADR-011: Native rules reference with immutable editions

Date: 2026-10-03

## Decision

Provide the English Wizards Comprehensive Rules as an offline native reference, independent of
authentication, collection ownership and gamification. Share its models, contracts, parser, search
and repository through the existing core modules. A focused `:shared:feature-rules` module owns the
new presentation and Compose UI; Android owns navigation, packaged assets, private storage and HTTP.
Koin owns all new bindings. Existing Home and Profile implementations remain in their current modules.

Keep the downloaded official TXT unchanged alongside a manifest containing its source URL, effective
date, SHA-256, schema, byte count and node count. `scripts/verify_rules_baseline.py` independently
reproduces the manifest and validates the complete hierarchy and glossary. The verified packaged
edition is effective 2026-09-25: 977,752 bytes and 4,066 nodes, including 9 chapters, 147 sections,
3,166 numbered rules/subrules, 741 glossary entries and 3 document sections.

Install updates only after validating the complete candidate. Discover the current English TXT
from the official rules page; the packaged source URL is provenance, not a permanently current
endpoint. Downloads are HTTPS-only with official-host and redirect checks, timeouts and byte limits.
The 4 MiB byte limit and 10,000-node limit allow measured headroom without accepting unbounded input.
Preserve the last valid snapshot and activate through an atomic pointer. Private snapshots belong
under Android's backup-excluded storage and retain editions needed by restored readers.

Readers and internal references use `(edition SHA-256, reference ID)`. Installing an update does
not change an open reader until an explicit reload. Search runs locally on background dispatchers,
limits queries to 200 characters and pages results in groups of 50. Do not introduce Room/FTS for
this corpus without measured evidence that the immutable index is insufficient.

Editorial Home tips remain distinct from official wording. Stable explicit tip IDs retain the
catalog's 146-entry order and daily rotation. A reviewed reference is scoped to the verified edition;
strategy or unmapped tips use a curated query/index fallback. The durable editorial review is in
`docs/rules/rules-tip-reviewed-metadata-2026-10-03.json`.

## Rationale

The complete corpus is small enough to bundle and search locally. An immutable version identity
prevents a document update from changing the meaning of a numbered reference during reading.
Keeping authoritative text separate from editorial advice avoids implying that strategy tips are
official quotations. The focused feature module preserves Clean Architecture without moving
unrelated Home/Profile code or putting a feature screen into the reusable UI layer.

## Consequences

All entry points share one navigation contract: Home's optional `rules` shortcut, the Rules Tip
header/body and one compact Profile Overview row. Existing four shortcut defaults and selections
survive upgrades. Web adapters, routing and builds remain deferred by the 2026-09-25 pause; their
follow-up scope is recorded in `docs/web-gamification-restore-debt-2026-09-25.md`.

The feature does not add tournament policy, card-specific rulings, bookmarks, AI answers, scheduled
updates, cross-device settings or a hosted manifest. Runtime acceptance and measured search results
are recorded separately in the rules verification document.
