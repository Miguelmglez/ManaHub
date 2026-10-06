### MTG Today (`feature/today/` presentation, `feature/news/` data)
Feed · Events · Trends, replacing the old News screen and the Competitive feature (ADR-011,
2026-09-24). Pure logic lives in `:shared:core-domain` `feature/news/domain/**` (commonTest-covered);
Room, OkHttp, XmlPullParser, DataStore and Custom Tabs stay in `:app`. Must-know:
- **Route + shell:** `Screen.MtgToday` = `today?tab={tab}` (`TodayTab.routeId`: feed|events|trends,
  default feed). `MtgTodayScreen` owns the top bar (Feed settings opens Sources), `ManaTabRow`, ONE
  `MagicToastHost` and the `AddSourceSheet` overlay. `FeedViewModel` is screen-scoped so cross-tab moves
  work: a followed source row → `selectSource(id)` + Feed tab; "Discover sources" → Sources sheet. Home's
  MTG_NEWS widget and Quick Start keep their persisted ids (`mtg_news`, `QuickStartAction.NEWS`).
- **Follow = `content_sources.is_enabled`.** The feed shows followed sources only (`filterFeed`); type,
  search and the selected source only narrow it. There is no language filter. Search is always visible; the Feed filter sheet owns type/source/saved mode. Saved filtering uses snapshot source identities, even after deletion.
- **Legacy migration (one-time):** DataStore flag `news_follow_migration_done`.
  `LegacyFollowMigration.computeFollowedIds` turns the retired `news_languages` / `news_filter_*` keys into
  follows (an absent language key means the old English-only default). It runs under a `Mutex` from
  `observeNews`/`observeSources` `onStart` and at the top of `refreshAll`. Removing the legacy keys and
  setting the flag happen in ONE `completeNewsFollowMigration()` edit. A failure leaves the flag unset
  (retried later) and never fails the refresh.
- **Seeding + site links:** newly seeded defaults start followed per `DefaultFollowPolicy` (English or
  the device language). Defaults take `siteUrl` from `DefaultSources`; custom sources learn it from the
  feed's channel `<link>`. Never let one overwrite the other, or refresh and reconcile will churn.
- **Saved = snapshot table `news_saved_items`** (Room v59), never a flag on the news cache: the cache
  is evicted after 7 days and sources can be deleted, so a saved row copies every displayed field.
  `insertSaved` is IGNORE (re-saving keeps the first `saved_at`). Device-local only, no Supabase sync.
- **Add Source resolve pipeline** (`NewsRepositoryImpl.resolveSource`): `SourceInputClassifier` →
  YouTube feed/channel id used directly; `@handle`, `/c/…`, `/user/…` → ONE `fetchPage` of the public
  channel page + `YouTubeChannelIdExtractor`, at add time only and never on refresh; web → the page is
  a feed, else ≤3 autodiscovery links, else the 6 `FALLBACK_FEED_PATHS` → parse (0 items =
  `EMPTY_FEED`) → dedupe by `FeedIdentity` (a matching unfollowed default is simply followed). Every
  failure is a typed `SourceResolveError`.
- **`fetchPage` rules:** HTTPS only, including the final URL after redirects; body capped with
  `peekBody(MAX_PAGE_BYTES)` (2 MB); `Accept-Language: en`, plus the `SOCS=CAI` consent cookie for
  youtube.com only (without it EU users get the consent interstitial instead of the channel page).
  The shared `OkHttpClient` interceptor rewrites `User-Agent`/`Accept`, so do not rely on the
  `Accept` header. `PageFetchException` messages never contain the URL.
- **Never log a source URL or a pasted input** (breadcrumb, custom key, non-fatal or Logcat): pass
  `e.withoutMessage()` to `recordSafeNonFatal`; the VM logs only
  `news_source_{resolve|follow}_failed:<SourceResolveError>`.
- **Events:** releases come ONLY from `CardRepository.getPlayableSets()` (cached, behind
  `ScryfallRequestQueue`) through `ReleaseCalendarBuilder` (≤3 sets released in the last 14 days, then
  upcoming ones, max 8). The latest Limited set (newest EXPANSION released on or before today) drives the
  17lands link. Never add a new Scryfall path. The Pro Tour strip is `GetProTourContentUseCase`'s
  major-event keyword filter over the cache, limited to followed sources and sorted newest first (max 12).
- **`MetagameLinkCatalog` is editorial:** opened in Custom Tabs, never fetched, no runtime liveness
  checks. When a site moves, fix its URL in the catalog. Link ids are telemetry values
  (`events_link_opened:<id>`), so keep them stable.
- The event-locator postal code keeps the DataStore key `"competitive_postal_code"`
  (`eventsPostalCodeFlow`/`setEventsPostalCode`) so users keep their saved value.
- **Telemetry:** `today_tab_selected`, `news_item_saved`/`_unsaved`, `news_source_followed`/
  `_unfollowed`/`_deleted`/`_site_opened`, `news_source_resolve_success`,
  `news_source_{resolve|follow}_failed:<ENUM>`, `events_release_opened`, `events_locator_opened`,
  `events_link_opened:<id>`. Custom keys: `today_selected_tab`, `news_followed_source_count`.
- **Tests:** helpers and use cases → commonTest `feature/news/domain/**`; ViewModels →
  `app/src/test/.../feature/today/presentation/**`; data → `NewsRepositoryImplTest`,
  `NewsFeedServiceTest` (`httpsOnly = false` only there, because MockWebServer serves plain HTTP);
  migration → `Migration58To59Test`.

- Events uses collapsible `SectionHeader` sections in locator, releases, competitive-coverage order; location search is the field's trailing action, and releases are full-width dated `DraftSetCard` rows that keep their Scryfall set-page destination. Coverage uses shared photo-backed `NewsItemCard` and card-back fallback.
- Trends starts with the multilingual Twitch Streams section, then editorial constructed-format and Limited resources; Arena-only links explicitly say Arena and do not inherit the constructed-format label.
- Public source logos are optional, cached on `content_sources.icon_url` through a targeted UPDATE after 200/304 content success or optional bounded background discovery during source observation. A once-per-process bounded public page attempt extracts website link icons or YouTube channel og:image; browser icons use origin-normalized fallbacks, while Feed source cards retain initials after image failures.
- Trends keeps origin favicons for web resources and resolves Twitch channel artwork from `og:image` on the four curated HTTPS pages through `NewsFeedService.fetchPage` and the shared optional-branding semaphore. The repository caches each curated channel lookup for the process lifetime; never use Twitch’s generic site favicon or guessed CDN URLs as a channel avatar. Trends rows show `ic_counter` while loading or after a failed/missing image and never show initials.
- `NewsFeedService.pageClient` removes HTTP logging and forbids cross-protocol redirects, retaining original timeouts/body caps. Raw source URLs or HTML never enter telemetry.

- The one MagicToastHost lives in the Sources modal while it is open, otherwise in the shell; Android sheets have a separate window, so following and action-failure feedback must be rendered there.
