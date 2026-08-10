# ADR-006 — Daily Puzzle (Phase 0 + Phase 1)

- **Status:** Accepted
- **Date:** 2026-08-05
- **Related:** design doc v3 (Daily Puzzle, superseded a v2 with real bugs — see Context),
  `docs/plans/kmp-migration-plan.md` (Android-first, KMP-friendly directive), ADR-002
  (Gamification), ADR-005 (backend call budget — Decision 1's "gate the backend, not just the UI"
  principle informed the Home widget's `isGamification` exclusion below)

## Context

Daily Puzzle is a new engagement feature: a Cardle-style daily guessing game, server-authoritative
(the puzzle date and content are decided by a Cloudflare Worker, never the device), with local
persistence for resume-after-kill, a Home widget, and light gamification (XP, streak, one
achievement). Android-only for this pass; all new shared code lives in `commonMain` so the web port
later is additive.

The design went through a self-critique round (v2 → v3) that fixed several real bugs before any
code was written:

1. **Matching cannot be by `oracle_id`.** The answer card is identified by normalized English
   name instead — the puzzle's answer card may never have been cached/searched by a given device,
   so name + a fresh `CardRepository.getCardByExactName()` fetch is simpler and doesn't depend on
   cache state.
2. **The feature is online-first, not "100% offline."** Room is an on-demand cache, not a mirror
   of the server. The client fetches `/puzzle/today` on load and is playable offline only
   afterward, via a resumable `guesses_json` blob. Submitting a guess requires network (name
   search + verification) — documented, not silently degraded.
3. **Future puzzles must never be servable, even if already uploaded.** The generator publishes
   batches up to ~30 days ahead so the buffer never runs dry; the Worker enforces
   `date > todayUTC → 404` on every date-scoped read, independent of what's actually sitting in
   the bucket.
4. **The salted-hash answer encoding is obfuscation, not anti-cheat.** See Decision 4.
5. **The "N perfects in a row" achievement was deferred pending verification**, not assumed. The
   underlying mechanism (a `Family.COUNTER` achievement with conditional reset) turned out to
   already exist as a precedent (`WIN_STREAK_*`), but a **separate, pre-existing bug** was found
   during that investigation — see Decision 5 — that changed the recommendation anyway.

## Decision 1 — Extend the existing `manahub-draft-api` Worker; don't create a new one

`manahub-draft-api` already has the exact primitive Daily Puzzle needs: a private R2 binding
(`MANAHUB_ASSETS`, bucket `manahub-assets`) with an ETag/If-None-Match/304 conditional-read
pattern. The other candidate, `manahub-community`, has KV + D1 but **no R2 binding at all** — it
would need new infrastructure for no benefit. New routes were added under a private `puzzle/`
prefix, distinct from the existing public `draft/` prefix, in the same `src/index.js` file.

## Decision 2 — Rollover boundary is 00:00 UTC, permanent, server-computed only

The puzzle's calendar day is decided once, server-side, and is **never** derived from the device's
clock (this also kills client clock-skew as a source of bugs). `GET /puzzle/today` computes
`new Date().toISOString().slice(0, 10)`.

**This constant must never change once puzzles are published.** Moving the rollover boundary after
the fact would split a day's streak/leaderboard data across two dates. If audience skews more
North-American later, a *new* puzzle type or a *versioned* rollover scheme should be introduced
rather than mutating this one.

## Decision 3 — Future-date guard is enforced on every date-scoped route, before any R2 read

`GET /puzzle/{date}`: `if (date > todayUTC) return 404` — evaluated before touching R2, so it
holds even if the generator already uploaded that date's object (batches of up to 30 days ahead
are expected). `GET /puzzle/index` filters `entries` to `date <= todayUTC` for the same reason —
an index listing tomorrow's *existence* (even without its content) is still a leak.

`GET /puzzle/index` additionally **strips `answerHistory`** from its response. The R2-stored
`puzzle/index.json` keeps `answerHistory: [{date, normalizedName}]` for the generator's own
~90-day dedup pass, but that field must never reach end-user clients — it would let a client read
every past puzzle's answer for zero legitimate reason. The generator reads the unfiltered R2
object directly via `wrangler r2 object get`, never through the public route.

## Decision 4 — The salted-hash answer scheme is obfuscation, stated plainly, not real anti-cheat

The payload ships `attributes` in cleartext (needed for the guess-feedback UI) and identifies the
answer as `sha256(normalizedName + dailySalt)`, with `dailySalt` traveling in the same payload.
This prevents a plaintext spoiler on casual inspection (opening the JSON in devtools doesn't hand
you the name), but MTG's ~30,000 card names are a small, entirely public dictionary — an attacker
can brute-force the hash against every name locally in well under a second. This is an **accepted,
documented limitation**, proportionate for a solo-dev feature with no real-money stakes at this
phase. The actual defense against a determined cheater, when it matters, is leaderboard-side
(Phase 2, out of scope here: auth-gated submission, server-side sanity checks, first-solve-wins) —
not attempting to make the puzzle payload itself tamper-proof.

## Decision 5 — Only `puzzle_solver` ships this pass; `puzzle_streak` and the `STREAK_` bug are deferred together

Investigating whether the achievement engine supports a "N-in-a-row, reset on break" counter
(needed for a hypothetical `puzzle_flawless`/`puzzle_streak` achievement) surfaced a **pre-existing,
unrelated bug**: `AchievementEvaluator.counterNextValue()` hard-codes every `STREAK_`-prefixed
`Family.COUNTER` id to always resolve to `0` — a permanently-stubbed no-op that means the
*already-shipped* `STREAK_3/7/30` achievements can never unlock today, for any player.

Fixing that stub is real, valuable work — but it's a separate change with its own blast radius (it
would retroactively start unlocking `STREAK_3/7/30` for existing users, a behavior change deserving
its own review pass), not something to bundle silently into this feature's diff. **This pass ships
only `puzzle_solver`** — `id = "PUZZLE_SOLVER"`, `Family.DERIVED` (a `COUNT(*) FROM puzzle_results
WHERE solved = 1` resolver, not a counter — `puzzle_results` already durably stores every solve, so
DERIVED gets free retroactive backfill with zero double-count risk), tiers `1 → 50xp`,
`7 → 150xp`, `30 → 400xp`. `puzzle_streak` and the `STREAK_` stub fix are tracked together as
follow-up work, not part of Phase 0/1.

The streak itself (a separate mechanism from the achievement) still ships this pass:
`StreakTracker` gains a `TYPE_PUZZLE` streak, advanced by the puzzle's own server-authoritative
`puzzleDate` (never the device's `clock.now()`) — a deliberate "two clocks" design: the puzzle's
global date and the existing daily-activity streak's local-day clock are independent by
construction, not an oversight.

## Scope

**This pass (Phase 0 + Phase 1):** `GUESS_CARD` puzzle type only, local Room persistence with
resume, the Worker routes above, the `tools/puzzle-generator/` Node pipeline (zero dependencies,
following `tools/collation/build-booster.mjs` conventions — plain `fetch()`, hand-rolled schema
validation via thrown `Error`s rather than adding the repo's first npm dependency), an opt-in Home
widget (`WidgetAudience.ALL`, gallery-only, not gamification-gated so it stays playable with the
gamification master toggle off), and the gamification hookup (XP, the `TYPE_PUZZLE` streak, the
`PUZZLE_SOLVER` achievement).

**Explicitly deferred:** the Supabase leaderboard (Phase 2 — auth-gated submission, RLS, sanity
checks, first-solve-wins, emoji-grid sharing), additional puzzle types (`ART_REVEAL`,
`ORACLE_REDACTED`, `CONNECTIONS`, `SYNERGY_ODD_ONE_OUT`, `MANA_MATH`, `FIND_LETHAL`), the
`puzzle_streak` achievement + the `STREAK_` counter-stub fix (Decision 5), and a GitHub Action to
automate weekly generation (Phase 0 generation stays manual: `node tools/puzzle-generator/generate.mjs
--days 30`, run by hand).
