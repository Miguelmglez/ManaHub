# ADR-004: Community aggregate API contracts (Archidekt, EDHREC, deckstats)

- Status: Accepted
- Date: 2026-07-11
- Context: Phase 3.0 of `docs/claude-code-prompt-deck-doctor-community.md` (plan is gitignored/deleted
  once the work ships; this ADR is the durable record of what was verified).

## Context

D3/D16/D17 (the plan's locked decisions) require the Cloudflare Worker to aggregate community deck
data from Archidekt (60-card + "decks like yours") and EDHREC (Commander suggestion aggregates),
with deckstats.net supported only as an import-by-URL source later (Phase 6). The plan document
carried several *assumed* shapes for these three unofficial, undocumented APIs. Before writing the
Worker, all three were probed live (read-only GET requests against public unauthenticated endpoints,
2026-07-11) to confirm or correct those assumptions. This ADR records the verified ground truth and
the resulting deviations from the plan.

Fixtures captured during this spike (trimmed, PII-free) live at
`cloudflare/manahub-community/test/fixtures/`.

## 1. Archidekt `GET https://archidekt.com/api/decks/v3/` (search)

Confirmed:
- Repeated `cardName` params are **AND**, not OR, contrary to the plan's assumption — see
  "Deviation" below. A single `cardName` works as documented.
- `deckFormat=<int>` filters by format (verified `3` = Commander; all returned decks had
  `"deckFormat":3`).
- `orderBy=-<field>` works Django-style; verified `-viewCount` (descending, correctly sorted) and
  `-createdAt`. Ascending (no `-` prefix) was not separately verified but is assumed symmetric.
- `pageSize` is **accepted and echoed back verbatim in the `next` URL, but the server silently caps
  the actual page at 60 results** regardless of the requested `pageSize` (tested 3, 5, 50, 1000 — all
  returned exactly 60 rows when total matches exceeded 60). **Deviation from the plan**: do not rely
  on `pageSize` to control batch size: fetch multiple pages if more than 60 rows are needed.
- `count` caps at `1000` (confirmed) even when far more decks match — it's a hard slice, not a true
  total.
- A popular `cardName` (e.g. `Sol Ring`) reliably triggers the documented statement-timeout signal:
  `{"count":-1,"next":null,"results":[],"message":"canceling statement due to statement timeout\n"}`.
  The existing `CommunityDecksRepositoryImpl` already handles `count < 0`; the Worker must handle the
  same shape.
- Search summary fields confirmed present exactly as the plan listed
  (`id,name,size,deckFormat,viewCount,owner,colors,tags,edhBracket,private,theorycrafted,updatedAt`),
  plus additional fields not previously modeled: `unlisted`, `createdAt`, `featured`,
  `customFeatured`, `game`, `parentFolderId/Name/CreatedAt`, `hasPrimer`, `description`,
  `cardPackage`, `contest`. `tags` is a list of `{id, tag, deck, name, position}` objects (not bare
  strings) — the tag NAME is at `.name`.
- **CORS**: `access-control-allow-origin: http://localhost:3000` (Archidekt's own dev origin only) —
  the API does **not** grant CORS to arbitrary origins. **This confirms D3's server-side-only
  architecture is not optional for the web target**: a browser (`wasmJs`) client can never call
  Archidekt directly, even as a "fallback" — only a native app (Android, no CORS enforcement) or the
  Worker (server-to-server, no CORS enforcement) can. See "Client integration" note below.

**Deviation**: the plan's D3/3.2 language ("intersect summary deck-ids across searches to approximate
AND") assumed repeated `cardName` params are OR'd server-side and the Worker does the AND via
intersection. Live testing shows Archidekt already treats multiple `cardName` params as an **implicit
AND at the database level** — and that query reliably **times out** (`count:-1`) even for a
2–3-signature-card combination that is individually common. Consequence for 3.2's incremental build:
issue **one search per signature card** (not one combined multi-`cardName` query) and intersect the
returned deck-id sets client-side (in the Worker) exactly as originally planned — just skip ever
trying the combined-param request, since it is confirmed to timeout rather than degrade gracefully.

## 2. Archidekt `GET https://archidekt.com/api/decks/{id}/` (deck detail)

Confirmed present at the top level: `deckFormat`, `edhBracket`, `private`, `theorycrafted`,
`unlisted`, `deckTags` (array, name-only tag list distinct from category tags), plus `cards[]` and
`categories[]`.

Each `cards[].card.oracleCard` carries `id, cmc, colorIdentity, colors, edhrecRank, faces, layout,
uid, legalities` — **full per-format legality strings** (`"legal" | "not_legal" | "banned" |
"restricted" | null`) are embedded, confirming the plan's hope that the Worker/Motor-B path needs
**zero client-side Scryfall resolution** for legality or color identity.

**Deviation (important)**: `colorIdentity`/`colors` are **full color names** (`["Blue","Green"]`),
**not WUBRG letters**. ManaHub's internal `Card.colorIdentity`/`colors` model is a compact WUBRG-letter
string per D14/Phase 0. The Worker's Archidekt normalizer and the client-side DTO mapper both need an
explicit `COLOR_NAME_TO_LETTER` table (`White→W, Blue→U, Black→B, Red→R, Green→G`; colorless = absent
from the list). The existing `ArchidektOracleCardDto.colorIdentity: List<String>` in
`shared/core-data` was written **before** this was verified and currently passes the raw strings
through unmapped — flagged for a follow-up fix (not in Phase 3 scope; `ImportCommunityDeckUseCase`
does not currently consume `colorIdentity` for scoring, only for display, so this is not a regression
introduced by this ADR, just a pre-existing latent gap now documented).

## 3. EDHREC `GET https://json.edhrec.com/pages/commanders/<slug>.json`

Confirmed reachable, open, no key. Served from S3 via CloudFront:
`Cache-Control: public, max-age=1620, stale-while-revalidate=180` — this is a **statically
pre-generated JSON dump**, not a live query API. It refreshes on EDHREC's own schedule (roughly
hourly based on `max-age`), so the Worker's own 7-day KV TTL is the binding constraint, not
EDHREC's freshness.

**The real shape differs substantially from the plan's flat assumption** — data is nested:

- Top level: `header` (display name), `creature/instant/sorcery/artifact/enchantment/battle/
  planeswalker/land/basic/nonbasic` (plain **ints** = average count of that type across the sample —
  this IS the "average type distribution incl. average land count" the plan wanted, just not grouped
  under one key), `bracket_counts` (`{bracketNumber: deckCount}`), `budget_counts`
  (`{budget/middle/expensive: deckCount}`), `tag_counts` (`{tagName: count}` — flat duplicate of
  `panels.taglinks`), `similar` (a plain **list of commander name strings**, not objects — simpler
  than assumed), `container.json_dict.card` (the commander's OWN EDHREC metadata: `num_decks` [total
  sample size — this is the `num_decks_avg` signal, just named differently and singular, not
  "avg"], `rank`, `cmc`, `color_identity`, `legal_commander`, `combos`, `precon`), and
  `container.json_dict.cardlists` (the per-category card lists).
- **`mana_curve` and `rank_over_time` are NOT top-level** — they live under `panels.mana_curve`
  (`{cmcString: count}`) and `panels.rank_over_time` (`{"YYYY-MM-01": {rank, commander_count,
  perc_of_decks_overall, perc_of_decks_overall_ma, rank_ma}}`, **monthly** granularity, not weekly).
  `panels.taglinks` is the structured form of `tag_counts` (`[{count, slug, value}]`).
- `container.json_dict.cardlists` confirmed 13 categories on Atraxa: `newcards, highsynergycards,
  topcards, gamechangers, creatures, instants, sorceries, utilityartifacts, enchantments,
  planeswalkers, utilitylands, manaartifacts, lands` — `gamechangers` ("Game Changers") is present
  exactly as D13 needs.
- **Deviation**: each cardview is `{id, name, sanitized, slug, url, synergy, num_decks,
  potential_decks, trend_zscore}` — there is **no `inclusion` field**. The plan assumed
  `name, synergy, inclusion, num_decks, potential_decks` per cardview. `inclusionPct` must be
  **computed by the Worker** as `num_decks / potential_decks`, not read directly.
  `trend_zscore` (present, `0.0` in the sample — likely nonzero on more today's cards) is a
  per-card signal usable for card-level trending, additive to the commander-level
  `panels.rank_over_time` monthly signal.
- No weekly `rank_over_time` granularity exists on EDHREC — only monthly. **The Worker's `/v1/trending`
  therefore leans primarily on its own D1 weekly request counters (as already planned)**; the EDHREC
  blend uses the monthly `rank_over_time` delta (`rank` decreasing month-over-month = rising) as a
  slow-moving secondary signal, not a weekly one. This is a scope-preserving deviation, not a gap: the
  plan's own "blend with EDHREC rank_over_time" language did not specify granularity.

`GET .../average-decks/<slug>.json` returns the same top-level shape **plus** `archidekt` (a
compact `[{c,f,q,u}]` card-quantity array unrelated to our Archidekt DTOs — `c`=category letter,
`f`=foil flag, `q`=quantity, `u`=Scryfall uid) and `deck` (`["1 Cardname", ...]` plain text lines) —
this is EDHREC's own "the average decklist" feature, not needed for Phase 3's aggregate snapshot and
not consumed by the Worker.

## 4. deckstats `api.php?action=get_deck&id_type=saved&owner_id=<o>&id=<d>&response_type=json`

Confirmed **reachable** (`https://deckstats.net/api.php?...` responds) but a valid `(owner_id, id)`
pair was not available to probe the success-path shape live — both probed placeholder ids correctly
returned an HTTP 400 with a plain-text (not JSON, despite `response_type=json`) `"Deck not found (N)."`
body. Per D17, deckstats has **no aggregate integration in v1** and the import-by-URL adapter is
explicitly **Phase 6 scope, not Phase 3** — this Worker phase does not call deckstats at all. The
`CommunityDeckSource` provider interface (client + Worker) reserves a slot for it; the exact
success-path payload shape will be verified when Phase 6 implements the adapter (a public deck URL
will be sourced from the user or a known public deck at that time). Documented here only so Phase 6
does not have to re-discover that error responses are plain text, not JSON, even when
`response_type=json` is requested — callers must not assume `Content-Type: application/json` and
must guard JSON parsing.

## Consequences for the Worker (3.1–3.2) and client (3.3)

1. Archidekt 60-card canonical-key search fans out to **one search per signature card**, never a
   combined `cardName` query (confirmed to timeout).
2. `inclusionPct` for the Commander (EDHREC) path is computed as `num_decks / potential_decks`
   per cardview, not read from a field.
3. A `COLOR_NAME_TO_LETTER` table is required anywhere Archidekt's `oracleCard.colorIdentity/colors`
   feed a WUBRG-letter-based comparison (Worker snapshot enrichment and any future client mapper
   fix).
4. The Worker's `/v1/trending` is counter-primary (own D1 weekly counts), with EDHREC's monthly
   `rank_over_time` as a slow secondary blend — not a weekly EDHREC feed.
5. On the web target, Archidekt (and by extension the "direct `ArchidektClient` reduced-sample
   fallback" in 3.3) **cannot be called from the browser** (CORS locked to `localhost:3000`). The
   commonMain `CommunityAggregateRepositoryImpl`'s fallback tier is therefore **Android-reachable
   only in practice**; on wasmJs the fallback silently fails the same way a network-down Worker would
   (caught, surfaced as `building`/error, never a hard crash) — this is not a code branch difference,
   just an observed runtime consequence of calling the same `ArchidektClient` from a browser engine.
6. deckstats stays untouched until Phase 6; its endpoint contract is only spot-verified for
   reachability and error-shape, not the success path.

## Fixtures

Trimmed, PII-scrubbed captures backing the Worker's vitest suite:
`cloudflare/manahub-community/test/fixtures/archidekt-search-page.json`,
`archidekt-search-timeout.json`, `archidekt-deck-detail.json`, `edhrec-commander.json`.
