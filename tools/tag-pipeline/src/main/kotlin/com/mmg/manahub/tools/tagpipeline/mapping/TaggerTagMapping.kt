package com.mmg.manahub.tools.tagpipeline.mapping

/**
 * Curated, hand-reviewed allowlist: Scryfall Tagger oracle-tag `slug` → `:shared:core-model`
 * `CardTag.key` (Deck Engine Unification plan, plan §8 addendum — "explicit, hand-reviewed
 * allowlist... unmapped Tagger tags are dropped, not guessed").
 *
 * Mirrors the existing `STRATEGY_OTAGS` precedent
 * (`shared/core-domain/.../feature/decks/domain/usecase/CandidatePoolGenerator.kt`), which maps the
 * OPPOSITE direction (a `CardTag` key → a Scryfall `otag:` search-query string, for building
 * external-pool queries). This table maps Tagger data INTO our taxonomy instead.
 *
 * **Every slug below was verified against a real downloaded sample of the `oracle_tags` bulk file**
 * (2026-07-20) — `grep -o '"slug":"[a-z-]*"' oracle-tags-sample.jsonl | sort -u`, cross-checked
 * against likely candidates (`removal`, `board-wipe`, `card-advantage`, `counterspell`, `tutor`,
 * `ramp`, `lifegain`, `mill`, `reanimate`, `sacrifice-outlet`, `burn`). Several plausible-sounding
 * slugs turned out NOT to exist as-guessed (there is no `"board-wipe"` slug — the real Tagger term
 * is `"sweeper"`) — this is exactly the failure mode the plan's "never guess" rule exists to catch,
 * and why every entry here was checked against real data rather than assumed from the `CardTag` key
 * naming convention.
 *
 * Seeded per the RUN 5 task brief's suggested initial set (removal, ramp, board-wipe/sweeper,
 * card-advantage, counterspell, tutor) plus three additional VERIFIED exact/near-exact matches
 * (lifegain, mill, reanimate) that were trivial to confirm from the same sample and meaningfully
 * widen day-one coverage without weakening the "hand-reviewed, no guessing" bar.
 *
 * `card-advantage` is deliberately mapped onto the narrower `card_draw` `CardTag` — Tagger's
 * "card-advantage" concept is broader than our `card_draw` role (a separate, narrower `"draw"` slug
 * also exists upstream and is EVEN closer, but `card-advantage` is what the task brief explicitly
 * named to seed, so both directions were considered and `card_draw` was kept as the deliberate,
 * reviewed choice — not a guess).
 */
val TAGGER_TAG_TO_CARD_TAG: Map<String, String> = mapOf(
    "removal" to "removal",
    "sweeper" to "board_wipe",
    "ramp" to "ramp",
    "card-advantage" to "card_draw",
    "counterspell" to "counterspell",
    "tutor" to "tutor",
    "lifegain" to "lifegain",
    "mill" to "mill",
    "reanimate" to "reanimator",
)

/**
 * Maps a set of raw Tagger slugs (as harvested per-oracle_id by
 * [com.mmg.manahub.tools.tagpipeline.scryfall.buildOracleTagIndex]) onto `CardTag` keys via
 * [TAGGER_TAG_TO_CARD_TAG]. Slugs with no allowlist entry are silently dropped — never guessed.
 */
fun mapTaggerSlugsToCardTags(slugs: Set<String>): Set<String> =
    slugs.mapNotNullTo(mutableSetOf()) { TAGGER_TAG_TO_CARD_TAG[it] }
