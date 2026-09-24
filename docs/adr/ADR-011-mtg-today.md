# ADR-011 — MTG Today: one destination for news, events and competitive links

**Status:** Accepted · **Date:** 2026-09-24 · **Related:** ADR-005 (backend call budget),
`app/src/main/java/com/mmg/manahub/feature/today/CLAUDE.md` (must-know invariants)

## Context

News and Competitive were two separate features, and neither worked well:

- **News** filtered the cached feed through an advanced filter sheet (content type, language, a
  source allowlist) persisted in DataStore. The allowlist and the per-source `is_enabled` flag were
  two overlapping ways of hiding a source. The language filter duplicated what following a source
  already expresses. Sources were managed in a Settings screen that took only a raw feed URL, so a
  YouTube `@handle` could not be added. A source's site or channel could not be opened. The cache is
  evicted after 7 days, so there was nothing like "save for later".
- **Competitive** sat behind `competitiveEnabledFlow`, which defaulted to `false`, and no UI could
  turn it on, so no user could reach it. It had 14 static links in 8 categories across 3 tabs, and a
  17lands link hardcoded to one set. It also carried a dormant data layer (two Room cache tables, a
  Ktor client, DTOs, a repository) for a Cloudflare Worker (`manahub-competitive`) that the app never
  called.

## Decision

1. **One destination, MTG Today**, with the tabs **Feed · Events · Saved · Sources**. It replaces the
   News screen, the News source settings, and the Competitive screen, route, Home widget, flag and
   strings.
2. **Everything not carried over is deleted**, including the dormant Competitive data layer, its Room
   tables (dropped in v57) and the `cloudflare/manahub-competitive/` Worker source. The deployed Worker
   (if any) is removed with `wrangler delete` outside this change.
3. **Follow model instead of filters.** `content_sources.is_enabled` means "followed", and the feed
   shows followed sources only. Content type, search and a single selected source only narrow the
   feed. The filter sheet, the language filter, `NewsFilterPrefs`, `UserPreferences.newsLanguages` and
   `NewsLanguage` are removed. A one-time migration (DataStore flag `news_follow_migration_done`) turns
   each user's old language selection and allowlist into follows. An absent language key maps to the
   old English-only default, so existing users do not suddenly see Spanish or German sources. Newly
   seeded defaults start followed only for English or the device language.
4. **Saved items are a device-local snapshot table** (`news_saved_items`, Room v57), not a flag on the
   news cache. The cache is evicted after 7 days and a source can be deleted, so a saved item must copy
   every field it displays in order to outlive both. No Supabase sync: saving is a lightweight,
   per-device bookmark, and syncing it would add an RPC, RLS and conflict handling for little value.
5. **"Paste any URL" source resolution.** Input is classified (YouTube feed, channel id, handle,
   website), then resolved: RSS/Atom autodiscovery (at most 3 links), then 6 common feed paths. A YouTube
   `@handle`, `/c/…` or `/user/…` URL is resolved with **one fetch of the public channel page at add
   time** to extract the canonical `UC…` channel id (link-preview style, no API key), and it is never
   repeated on refresh. Fetches are HTTPS only (after redirects too) and capped at 2 MB, and no URL
   ever reaches logs, breadcrumbs or non-fatals.
6. **Events uses only data the app already has.** Upcoming and just-released sets come from the cached,
   rate-limited `CardRepository.getPlayableSets()` (no new Scryfall path). The Pro Tour strip is a
   keyword filter over the cached feed, limited to followed sources. The metagame links are a trimmed,
   editorial catalog of 7 links opened in Custom Tabs. The 17lands link follows the newest released
   expansion instead of a hardcoded set.

## Consequences

- One place to read, save and manage sources. Removing filters removes a whole class of "why is my
  feed empty" states. An empty feed now means "you follow nothing" (with a Discover CTA) or "nothing
  matches your search".
- The @handle page fetch has known limits. It depends on YouTube's public HTML keeping a canonical
  channel id (`<link rel="canonical">`, `itemprop="identifier"`/`"channelId"` meta, or the
  `"externalId"`/`"channelId"` JSON markers). EU networks
  can receive a consent interstitial; the `SOCS=CAI` cookie avoids it but is not guaranteed forever.
  The shared OkHttp client sends a non-browser User-Agent. When extraction fails, the user gets
  `YOUTUBE_CHANNEL_NOT_FOUND` and can paste the channel's `/channel/UC…` URL instead. None of this
  runs on refresh, so a YouTube markup change can only break adding a source, never existing follows.
- Saved rows are device-local and are never uploaded, so they are not part of cloud sync.
- Room v57 is a data-preserving migration (CREATE + additive `site_url` + DROP of two cache tables).
- The editorial link catalog needs manual upkeep when a site changes its URLs. This is accepted over
  runtime liveness checks, which would add background traffic (ADR-005).

## Alternatives considered

- **Keep the filter sheet and add follows**: two ways to hide a source again. Rejected.
- **Saved as a boolean on the cached item**: lost after 7-day eviction or source deletion. Rejected.
- **YouTube Data API for handles**: needs an API key shipped in the app and a quota. Rejected in favour
  of the one-time public page fetch.
- **Keep the Competitive Worker for live metagame data**: third-party data policies and the
  never-enabled flag made it dead weight. Rejected; links open the sources' own sites instead.
