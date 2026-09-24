# ManaHub — Google Play ASO & Discoverability Improvement Plan

**Status date:** 2026-09-24 · **Listing:** `https://play.google.com/store/apps/details?id=com.mmg.manahub`
**Scope:** everything that affects how often ManaHub is *shown* on Google Play and how often a view turns
into an install that sticks: metadata, visual assets, ratings, technical quality, localization,
off-store traffic, and in-app growth loops.
**Out of scope:** iOS App Store, large paid user-acquisition budgets, and monetization.

---

## 0. Executive summary

ManaHub barely appears in Google Play search because of how it is set up, not because of the product.
Four findings explain most of the problem:

1. **The title is just `ManaHub`.** It uses 7 of the 30 available characters and contains no word that
   players search for. Every direct competitor puts **"MTG"** plus a job ("Life Counter", "Card Scanner",
   "Deck Builder") in the title. The title is the strongest indexing signal on Google Play, so today
   ManaHub realistically ranks only for its own brand name, and that name is shared with unrelated
   projects (manahub.org, Instagram `@mana.hub`, …).
2. **The listing opens with the wrong story.** As far as it could be reconstructed (see §1), the
   description opens with developer vocabulary ("built local-first"). It gives the widget board and news
   (low search demand) more space than the features people search for (scanner, collection value,
   Commander life counter). It also describes the **v1 deck builder** (mainboard/sideboard, basic-land
   calculator) and leaves out the flagship **Deck Wizard, which builds a deck from the cards you own**.
   That is the one thing no Android competitor leads with.
3. **Almost nothing brings traffic to the listing from outside the store.** The GitHub README still says
   *"Screenshots: Coming soon"* and lists *"Play Store release"* as **planned**. There is no website. The
   invite and trade-list links point to a personal `github.io` host. Share texts carry no store link or
   referrer. So install velocity, a first-order ranking signal, stays close to zero.
4. **The ratings engine works against itself.** The in-app review API is fired from buttons (Profile
   "Rate app" row, Home "first steps" card). Google says not to do this, because its quota can silently
   suppress the dialog. The card is also headed *"Enjoying ManaHub?"*, and Google's guidelines say not to
   ask opinion questions before the review flow. Nothing triggers a review prompt at a moment of delight.

**Positioning to adopt:** *"Build MTG decks from the cards you already own — plus scanner, collection
value, Commander life counter and trades. No ads, no account needed."*

**Top 10 actions, ranked by impact ÷ effort**

| # | Action | Section | Effort | Expected impact |
|---|--------|---------|--------|-----------------|
| 1 | New title: **`ManaHub: MTG Deck & Collection`** (30/30) | §7.1 | 5 min | Very high: first generic-keyword indexing |
| 2 | New short description (80/80) + full description led by the Deck Wizard | §7.2–7.3 | 1 h | High: indexing + conversion |
| 3 | Check that **countries/regions** and the **production track** cover every major MTG market | §1.3 | 15 min | Potentially very high if availability is restricted |
| 4 | Designed 8-screenshot set + feature graphic (benefit captions, Deck Wizard first) | §8 | 1–2 days | High: conversion (and CR feeds ranking) |
| 5 | Fix the review flow: store deep link for buttons, contextual API prompts, neutral copy | §11, §20 | 0.5–1 day dev | High: rating volume and average |
| 6 | Update the GitHub README (Play badge, screenshots, stale roadmap) + repo topics | §14.2 | 1 h | Medium: branded search, backlinks |
| 7 | Static landing site on a branded domain with a Play badge, UTM links and one page per intent | §14.1 | 2–3 days | Medium–high: web SEO + referral installs |
| 8 | Seed launch posts (Reddit/Discord/LGS/creators) with UTM-tagged links | §14.3–14.5 | Ongoing | High: install velocity |
| 9 | Store listing experiments + keyword-targeted custom store listings once traffic allows | §9–10 | Ongoing | Medium–high: conversion |
| 10 | Localize the store listing (ES/DE/FR/PT-BR/IT), a **decision for the owner** | §13 | 1–2 days per locale | Medium–high: new search surfaces |

**90-day targets** (set relative to the baseline captured in §1.3):
3× store-listing visitors from Google Play search; +30 % relative visitor→installer conversion;
≥ 10 non-brand keywords in the top 10 for at least one major country; ≥ 50 ratings with an average ≥ 4.5;
crash rate < 1.09 % and ANR rate < 0.47 % (hard gates).

---

## 1. Method, data sources and limitations

### 1.1 What was analysed

- **Product:** the repository at `feature/deck-wizard` (2026-09-24): `README.md`, `PRIVACY_POLICY.md`,
  `AndroidManifest.xml`, `app/build.gradle.kts`, `shared/core-model/.../core/FeatureFlags.kt`,
  navigation routes (`Screen.kt`, `AppNavGraph.kt`), every feature `CLAUDE.md`, `strings.xml`
  (share texts, review-prompt copy), the Play Store icon (`app/src/main/ic_launcher-playstore.png`)
  and `cloudflare/assetlinks.json`.
- **Store presence:** the public listing as indexed by search engines, plus competitor listings,
  third-party store-analytics snippets and 2025–2026 ASO literature (sources in Appendix D).

### 1.2 Limitation: the live listing could not be opened directly

The analysis environment's network egress policy blocks `play.google.com` and store mirrors (AppBrain,
APKCombo, …). The current listing was therefore **reconstructed from search-engine index fragments**:

- **Title:** `ManaHub` (confirmed by every indexed result: *"ManaHub – Apps on Google Play"*).
- **Description fragments indexed:** *"all-in-one companion for Magic: The Gathering — built local-first,
  so the core works fully offline and without an account"*; *"track life for 2–6 players at once"*;
  *"a customizable home with a widget board you arrange yourself — game stats, collection stats, card
  discovery, news, and more"*; *"MTG News — the latest articles and videos from Magic sources, all in one
  feed"*; *"add friends by link, QR, or Game Tag and browse their collection"*; *"deck builder with
  mainboard and sideboard, mana-curve chart and basic-land calculator, and format validation for
  Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Commander, and Casual"*; *"create a free account
  (email or Google) to back up and sync…"*; *"privacy-first: card scanning runs on-device… no ads, and
  no data selling"*; the Wizards "unofficial fan project" disclaimer.
- **Could not be verified:** the short description, number and design of screenshots, feature graphic,
  promo video, category and tags, rating and number of ratings, install bucket, countries/regions,
  Data safety summary, developer name and contact details.

Everything below that depends on those unknowns is marked **(verify)**. Competitor install and rating
figures come from third-party estimates found in search results. Treat them as order-of-magnitude
numbers, not exact counts.

### 1.3 Baseline to capture before changing anything (owner: Play Console)

Record these values in a dated spreadsheet (template in Appendix B). Without a baseline, no later
change can be evaluated. Play Console menu names shift often, so the paths below are approximate.

| # | Data point | Where |
|---|------------|-------|
| B1 | Current title, short description, full description (copy verbatim) | Grow users → Store presence → Main store listing |
| B2 | Number, order and content of phone/tablet screenshots; feature graphic; video URL | same |
| B3 | App category, tags, contact email, website, privacy-policy URL | Store settings |
| B4 | **Release track** (production vs open testing) and **countries/regions** where the app is available | Release → Production → Countries/regions |
| B5 | Store listing visitors, acquisitions, conversion rate, and the peer-median conversion rate Play Console shows | Store performance → Store analysis / listing acquisition |
| B6 | **Traffic sources** split (Google Play search / explore / third-party referrals) | same |
| B7 | **Search terms** that brought visitors and installers (the only first-party keyword data available) | same → Search terms |
| B8 | Installs by country, device type and Android version | Statistics |
| B9 | Average rating, rating count, recent reviews | Ratings and reviews |
| B10 | User-perceived crash rate and ANR rate, overall and per device | Android vitals → Overview |
| B11 | Day-1 / Day-7 / Day-30 retention for new users | Firebase Analytics → Retention |
| B12 | Manual rank check for the 20 priority keywords (§6.1) in US, GB, ES, DE | Play Store app, signed-out or fresh profile |

> **Why B4 matters so much:** an app in **open testing** shows an "early access" / unreleased state and
> ranks poorly, and an app released to a subset of countries simply does not exist anywhere else. If
> either applies, fixing it beats every other item in this plan. The main MTG markets to cover are
> US, CA, GB, IE, AU, NZ, DE, AT, CH, FR, BE, NL, ES, IT, PT, PL, SE, NO, DK, FI, CZ, BR, MX, AR, CL, JP.

---

## 2. How Google Play discovery works (the model this plan is built on)

1. **Text relevance (indexing).** The title is the strongest text signal. The short description (80
   chars) and full description (4,000 chars) are also indexed. The developer name has a small effect.
   In 2026 relevance is judged on how closely the wording matches search *intent*, not on keyword
   density, and over-optimised copy hurts conversion.
2. **Install velocity.** How quickly installs grow, especially installs that come from a search term.
   A new app with no velocity is outranked by established apps on every head term.
3. **Store listing conversion rate.** Visitors → installers. It is a ranking input, and it is the one
   the listing assets control directly.
4. **Retention and engagement after install.** Since 2026, day-7 retention and engagement weigh more
   than raw install counts. Installs that uninstall immediately hurt ranking.
5. **Technical quality (Android vitals).** A user-perceived crash rate above **1.09 %** or an ANR rate
   above **0.47 %** (overall), or above **8 %** on a specific device model, can exclude the app from
   discovery surfaces and add a warning to the listing on affected devices.
6. **Ratings and reviews.** They mostly affect conversion. A low average also reduces how often Google
   recommends the app in explore surfaces.
7. **Freshness.** Regular updates with real improvements. They help mostly through the other signals
   (vitals, retention), not directly.

**Implication:** metadata changes (§7) get ManaHub *indexed* for the right terms. Assets (§8) and
ratings (§11) turn impressions into installs. Off-store traffic (§14) creates the initial velocity the
algorithm needs before it will rank a new app. Retention and vitals (§12) keep those rankings. Every
lever is needed; metadata alone will not fix visibility.

---

## 3. Current-state audit

### 3.1 Listing scorecard

| Element | Current (observed / inferred) | Problem | Score 0–5 | Target |
|---------|-------------------------------|---------|-----------|--------|
| Title | `ManaHub` (7/30) | No generic keywords; brand name collides with unrelated projects | **0** | Brand + "MTG" + core job (§7.1) |
| Short description | (verify) | — | ? | 80/80, core promise + 3 jobs (§7.2) |
| Full description | README-style feature inventory | Opens with jargon ("local-first"); widgets/news before search-driving features; v1 deck builder described, **Deck Wizard absent**; no audience framing | **2** | Benefit-first, intent-clustered (§7.3) |
| Screenshots | (verify); README says *"Screenshots: Coming soon"* | Probably raw or undesigned captures | ? | 8 designed, captioned, story-ordered (§8.2) |
| Feature graphic | (verify) | — | ? | 1024×500 brand + promise (§8.3) |
| Promo video | (verify), probably none | — | ? | 30 s demo (§8.4) |
| Icon | Illustrated WUBRG yin-yang hexagon on purple | Fine detail lost at 48 dp; five glyphs close to Wizards' mana symbols (IP exposure + looks like every other WUBRG icon) | **3** | Test a simplified, ownable variant (§8.1) |
| Category / tags | (verify) | — | ? | Entertainment + 5 relevant tags (§7.6) |
| Ratings | (verify), probably very few | Review flow issues (§3.4) | ? | ≥ 4.5 with steady volume |
| Localization | English only | Invisible to non-English queries | **1** | Phase-6 locales (§13) |
| Web presence | GitHub repo only; stale README | No landing page, no store badge, no UTM | **1** | Landing site + fixed README (§14) |

### 3.2 Product inventory: what can and cannot be promoted

Checked against `FeatureFlags.kt` and the navigation graph on 2026-09-24. **Only promote features that
are live in the production build.** Promising a hidden feature is a metadata-policy risk and causes
1-star "where is X?" reviews.

**Live, and a strong fit for the listing**

| Feature | Search demand | Differentiation | Listing role |
|---------|---------------|-----------------|--------------|
| **Deck Wizard**: builds Commander and 60-card decks (Commander, Commander Casual, Casual, Standard, Pioneer, Modern, Legacy, Vintage, Pauper) **from the user's own collection**, never more copies than owned, transparent gap report | Medium ("deck builder") + long-tail ("build deck from my collection") | **Very high**: no Android competitor leads with it | **Hero** |
| **Deck analysis** (score, pillars, synergy "engines" explained in plain English, category browsing) | Medium ("deck analyzer") | High | Supporting |
| **Collection tracker** with USD/EUR prices, filters, wishlist, for-trade list, automatic tags | Very high | Medium (tags are rare) | Primary |
| **Import/export**: Moxfield/Arena text, Moxfield/ManaBox CSV, with a review queue | Medium (switchers) | High for switchers | Supporting ("bring your collection") |
| **Card scanner** (on-device ML Kit OCR, queue, foil/condition/language) | Very high | Low–medium (crowded) | Primary, but compete on long-tail |
| **Life counter** 2–6 players: commander damage, poison, energy, experience, custom counters, dice/coin, phase + turn tracker, offline voice commands (end turn / land drop; EN/ES/DE models) | Very high | Medium (voice is unusual) | Primary |
| **Stats**: per-deck win rate, average game length, streaks, local-seat accurate | Low–medium | High combined with the life counter | Supporting |
| **Friends + Trades**: negotiation, counter-offers, shared wishlists via link | Low–medium | High (rare on Android) | Supporting |
| **Local tournaments**: Swiss / Round Robin / Single Elimination | Low–medium | Medium | Supporting (LGS/playgroup angle) |
| **Goldfish playtest** (hand + battlefield) | Low–medium | Medium | Supporting |
| **Community decks** (Archidekt-sourced, "decks like yours") | Medium | Medium | Supporting |
| **News & videos**, **Competitive hub** (curated deep links) | Medium | Low | Minor |
| **12 visual themes**, no ads, no account, core features offline | — | Trust / conversion | Trust block |

**Hidden or not ready: do NOT mention in the listing yet** (these become "What's new" and LiveOps
moments when they ship): online multiplayer (`ONLINE_SESSIONS_ENABLED = false`), gamification
(`Gamification.ENABLED = false`), Daily Puzzle (`PUZZLE_ENABLED = false`), draft simulator
(`SIMULATOR_ENABLED = false`; the draft area is flagged "not live, rebuild in progress"), tablet/foldable
layouts (planned). (Update: Deck Studio's **Browse inspirations**, `DISCOVERIES_V2_ENABLED` +
`DISCOVERY_BUILD_HANDOFF_ENABLED`, went live on 2026-09-24. It can be mentioned as "find deck ideas
in your own collection" once it is in a production release.)

**Claims to word carefully**

- The scanner's **recognition** is on-device, but the card **lookup** calls Scryfall. Say "images never
  leave your phone", not "the scanner works offline".
- "Works offline" applies to **core features** (collection, decks, life counter, stats). Card search
  and prices need a connection.
- Voice commands cover **end turn** and **land drop**, not life changes, and use one language per
  session with a downloadable model.

### 3.3 Off-store presence audit

- **GitHub repository** (it ranks for "ManaHub MTG" on web search): no Play badge. *"Screenshots: Coming
  soon"*. Roadmap lists *"Play Store release"* as **planned**, which tells a visitor the app is not out.
  The "Room … DB v40" details are stale.
- **No website or landing page.** App Links and share pages use `https://miguelmglez.github.io/list/…`
  and `/invite/…`, a personal host with no brand and no store badge (verify what the fallback page shows
  to someone who does not have the app).
- **Name collisions:** `manahub.org` (NZ community platform), `manahub.fr`, Instagram `@mana.hub`,
  Facebook `mana.hub0`, X `@mana_hub`. A search for "ManaHub" is not uniquely yours, which is another
  reason the title needs descriptive keywords.
- **Social handles:** none found for the app.
- **Directories:** not listed on AlternativeTo (which has a "ManaBox alternatives" page that ranks on
  Google).

### 3.4 In-app growth-mechanics audit

| Finding | Where | Why it matters |
|---------|-------|----------------|
| The in-app review API is launched **from buttons** (Profile `RateAppRow`, Home first-step `HomeAction.RateApp`), with a store fallback only when `requestReviewFlow` *fails* | `ProfileScreen.kt:380`, `AppNavGraph.kt:439` | Google limits how often the dialog shows. When the quota suppresses it, the call still "succeeds" and the user sees **nothing** after tapping. Google's guidance: buttons should open the store listing; the API is for contextual prompts. |
| The review card title is **"Enjoying ManaHub?"** | `strings.xml` `first_step_rate_app_title` | Google's In-App Review guidelines say not to ask opinion questions ("Do you like the app?") before or while presenting the review flow. Use neutral copy. |
| No **contextual** review prompt (after a win, a finished Deck Wizard deck, a large import, a completed trade) | — | The best moments to get 5-star ratings are never used. |
| Share texts (`Join me on ManaHub! %1$s`, `Add me on ManaHub! My game tag is %1$s`) carry no store link or campaign referrer | `strings.xml` | Every invite is a free acquisition channel that cannot be measured. A recipient without the app gets a `github.io` page (verify it has a Play badge). |
| Deck export is plain text only | `DeckStudioScreen.kt` | Keeping imports clean is correct, but there is no branded "share as image" option (decklists and tournament standings are posted in WhatsApp and Discord groups all the time). |

---

## 4. Competitive landscape

### 4.1 Android competitors

| Play Store title | Package | Title pattern | Core promise | Approx. installs · rating (3rd-party estimates) | Model |
|------------------|---------|---------------|--------------|-------------------------------------------------|-------|
| **ManaBox MTG** | `skilldevs.com.manabox` | Brand + MTG | Scanner, binders/lists, deck builder, prices, deck simulator | ~1.1 M · ~4.35 ★ (~9.4 K ratings) | Free |
| **MTG Scanner - Dragon Shield** | `pt.tscg.mtgmanager` | MTG + job + brand | Any-language scanner, TCGplayer/Cardmarket prices, 30-day price history, folders, decks, friends | ~750 K · ~4.46 ★ (~13 K) | Free (sleeve-brand backed) |
| **MTG Card Scanner Delver Lens** | `delverlab.delverlens` | MTG + job + brand | Fast scanner (tokens/emblems), prices, Card Kingdom buylist | 100 K+ | Free |
| **TopDecked MTG** | `com.maritlabs.topdecked.mtg` | Brand + MTG | Deck builder + simulator, life counter, collection, cross-device sync, deck recommendations | 100 K+ · ~4.7 ★ (~7.5 K) | Freemium subscription |
| **Mythic Tools MTG** (previously "MTG Life Counter: Mythic Tools") | `com.studiolaganne.lengendarylens` | Brand + MTG (was MTG + job + brand) | Life counter up to 8 players, scanner, deck builder, playgroups, stats, drafts/sealed events | (n/a) | Free account |
| **MTG Life Counter App: Lotus** | `com.vanilla.mtgcounter` | MTG + job + brand | Up to 10 players, Planechase/Archenemy, card search | (n/a) | Free, no ads, no IAP |
| **Lifetap Life Counter for MTG** | `com.lifetap` | Brand + job + "for MTG" | Life counter | ~620 K · ~4.84 ★ | — |
| **Magic: The Gathering Companion** (official, Wizards) | `com.wizards.winter_orb` | Official brand | In-store events, life counter, home tournaments up to 8 | (n/a) | Free |
| MTG Tracker & Life Counter · MTG Life Counter: Get a Life! · MTG Life Counter: LifeElk | various | Generic "MTG Life Counter" | Life counters | (n/a) | — |
| **Decked Builder** | `com.deckedbuilder.deckedbuilder` | Brand | Offline deck builder with a local database | (n/a) | Premium |

**Web-first mindshare with no native Android app:** Moxfield (the default Commander deck builder) and
Archidekt (recommends installing its site as a PWA). **An emerging wave of "AI deck builders"**
(ManaForge MTG, Sensei's EDH Brewer on iOS, EDHDeck on the web) shows growing demand for automated deck
building, and none of them builds from the user's own collection on Android.

### 4.2 Title patterns

- **Every competitor has "MTG" in the title. ManaHub is the only exception.**
- Two patterns dominate: **"MTG + job + Brand"** (Delver Lens, Lotus, Dragon Shield), which carries the
  most keywords, and **"Brand + MTG"** (ManaBox, TopDecked, Mythic Tools), which established brands can
  afford because they already have install velocity.
- Mythic Tools moved from "MTG Life Counter: Mythic Tools" to "Mythic Tools MTG" as it grew beyond a
  life counter. This is a useful precedent: **keyword-first while small, brand-first once known.**

### 4.3 White space

1. **"Build decks from the cards you own."** High intent, very low competition, and ManaHub's strongest
   engineering asset. Nobody on Android leads with it.
2. **A toolkit for Commander night:** life counter, local tournament, per-deck win rates and friends
   in one place. Only Mythic Tools comes close.
3. **Trust:** no ads, no account required, on-device recognition, export at any time. ManaBox users who
   are unhappy with ads or paywalls and TopDecked users who dislike subscriptions are natural
   switchers, and the import flow (ManaBox/Moxfield CSV) removes the switching cost.
4. **Trading with friends,** with full negotiation (proposal → counter → complete). Only Dragon Shield
   offers social lists.

**Do not fight head-on for "mtg scanner" or "mtg collection" at first.** Those terms are held by apps
with 100 K–1 M installs. Use the scanner and collection as **conversion proof** (screenshots, bullets)
and rank on long-tail and combination queries first (§6).

---

## 5. Positioning and messaging

### 5.1 Value proposition

> **Build MTG decks from the cards you already own.** ManaHub turns your collection into playable
> decks, then helps you track it, value it, play with it and trade it — no ads, no account needed.

### 5.2 Messaging hierarchy

1. **Hero promise:** decks from your collection (the Deck Wizard).
2. **Everyday jobs:** scan cards → know what the collection is worth → count life at the table →
   see win rates → trade with friends.
3. **Trust:** no ads · core features work offline · no account needed · on-device recognition ·
   export at any time.

### 5.3 Personas (use them to write captions, CSLs and posts)

| Persona | Trigger query (examples) | What convinces them | First screenshot for them |
|---------|--------------------------|---------------------|---------------------------|
| **Commander brewer on a budget** | "commander deck builder", "build deck from collection" | Deck Wizard + "never more copies than you own" | Deck Wizard |
| **Collector / value tracker** | "mtg collection tracker", "mtg card prices", "mtg scanner" | Scanner + collection value + import from other apps | Collection value |
| **Playgroup / LGS regular** | "commander life counter", "mtg life counter 4 players" | Life counter + stats + local tournament | Life counter |
| **Returning player** | "what can I build with my cards mtg", "mtg collection app" | Import + wizard + analysis | Deck Wizard |
| **Trader** | "mtg trade app", "mtg wishlist" | Trade negotiation + shared lists | Trades |

---

## 6. Keyword strategy

### 6.1 Keyword universe

Competition is a qualitative estimate from the competitor set (VH/H/M/L). Validate with the §6.3 process.
Placement key: **T** = title, **S** = short description, **F** = full description, **CSL** = keyword-targeted
custom store listing.

| Cluster | Keyword (EN) | Intent | Competition | Relevance | Placement | Priority |
|---------|--------------|--------|-------------|-----------|-----------|----------|
| Category | mtg | navigational | VH | High | T, S, F | P1 |
| Category | mtg app · mtg companion | generic | H | High | F | P2 |
| Category | magic the gathering | generic (trademark) | VH | High | F (nominative, 1–2×) | P2 |
| Deck | mtg deck builder | transactional | H | Very high | T (deck) + S + F | **P1** |
| Deck | commander deck builder · edh deck builder | transactional | M | Very high | S (Commander) + F + CSL | **P1** |
| Deck | build deck from collection · deck from my cards | transactional | **L** | **Very high** | S + F + CSL | **P1** |
| Deck | mtg deck analyzer · deck checker · deck score | informational | L–M | High | F | P2 |
| Deck | mana curve · land count | informational | L | Medium | F | P3 |
| Deck | mtg playtest · goldfish | transactional | L | Medium | F | P3 |
| Deck | decklist import / export | utility | L | Medium | F | P3 |
| Collection | mtg collection | transactional | H | Very high | T + F | **P1** |
| Collection | mtg collection tracker · collection manager | transactional | H | Very high | F + CSL | P1 |
| Collection | mtg card prices · card value · collection value | informational | H | High | S + F | P1 |
| Collection | mtg binder · trade binder | utility | M | High | F | P2 |
| Collection | mtg wishlist | utility | L | Medium | F | P3 |
| Scanner | mtg scanner · mtg card scanner | transactional | VH | High | S + F + CSL | P1 |
| Scanner | magic card scanner · scan magic cards | transactional | H | High | F | P2 |
| Life | mtg life counter | transactional | VH | High | S + F + CSL | P1 |
| Life | commander life counter · edh life counter | transactional | H | Very high | S + F + CSL | **P1** |
| Life | commander damage tracker · poison counter | utility | L–M | High | F | P2 |
| Life | life counter 4 players · 6 players | utility | L | High | F | P3 |
| Life | voice life counter | utility | L | Medium | F | P3 |
| Social | mtg trade · trading app | transactional | L–M | High | F | P2 |
| Social | mtg tournament · swiss pairings · tournament organizer | utility | L–M | Medium | F | P2 |
| Social | mtg stats · win rate tracker | utility | L | High | F | P2 |
| Content | mtg news | informational | M | Low–medium | F | P3 |
| Content | mtg metagame · decklists · limited ratings | informational | M | Low (links only) | F | P3 |

### 6.2 Placement map

| Field | Keywords it carries |
|-------|---------------------|
| **Title** (30) | ManaHub · **MTG** · **Deck** · **Collection** |
| **Short description** (80) | **decks** · **cards you own** · **card scanner** · **prices** · **Commander** · **life counter** |
| **Full description** (4,000) | Every P1/P2 term, 2–5 natural mentions each, grouped under intent headers (deck builder, scanner, collection, life counter, group play) |
| **CSLs** | One per high-intent cluster that Play Console lists as bringing traffic: life counter, scanner, deck builder (§10) |
| **Developer name** | Consider a brand developer name ("ManaHub") for consistency (§7.5) |

### 6.3 Validation process (no paid tools needed)

1. **Play Console search terms (B7).** This is the ground truth. After the metadata change, review
   weekly for 6 weeks: which terms bring visitors, and at what conversion?
2. **Play Store autocomplete.** Type the seed terms ("mtg ", "commander ", "life counter ",
   "deck builder ", "card scanner ") on a signed-out device in US/GB/ES/DE and record the suggestions.
   Autocomplete reflects real query volume.
3. **Google Trends** (web search, which correlates with store intent). Compare "mtg life counter" vs
   "mtg scanner" vs "mtg deck builder" vs "commander deck builder" over 5 years, by country, to size
   the clusters and find seasonality around set releases.
4. **Google Ads Keyword Planner** (free with an Ads account). Gives volume bands for web queries,
   useful for landing-page SEO (§14.1) and as a proxy for store demand.
5. **Optional:** a 1–2 week trial of an ASO tool (AppTweak, AppFollow, MobileAction, Sensor Tower) to
   get keyword difficulty and competitor keyword maps in the target countries.
6. **Rank tracking:** every Monday, record the position of the 20 P1/P2 keywords in US, GB, ES and DE
   (Appendix B). Keep the method identical each time (same device, signed-out, same country).

### 6.4 Trademark guidance (owner decision)

- "Magic: The Gathering" and "MTG" are Wizards of the Coast trademarks. The whole category uses "MTG"
  in titles descriptively (ManaBox MTG, TopDecked MTG, Mythic Tools MTG, Lifetap … for MTG, Lotus,
  Delver Lens, Dragon Shield). This is common practice, but not risk-free. **Recommendation:** adopt
  "MTG" in the title like the rest of the category. Keep the Wizards Fan Content Policy disclaimer at
  the end of the full description. Never use Wizards logos or set symbols in listing assets, and never
  imply endorsement (no "official").
- Do **not** use competitor brand names as keywords. The one exception is a single factual
  compatibility statement ("Import from … CSV"). Google's metadata policy forbids comparisons with other
  apps and irrelevant keyword references. If in doubt, use the neutral variant in §7.3.
- Avoid "free", "no ads", "best", "#1", "top" and emojis in the **title, icon and developer name**
  (Google metadata policy). Performance claims ("#1", "best") are also out of place in the short
  description. "Free to use, with no ads" belongs in the full description.

---

## 7. Metadata rewrite (ready to paste)

### 7.1 Title (30 characters max)

| Option | Length | Keywords | Notes |
|--------|--------|----------|-------|
| **A (recommended): `ManaHub: MTG Deck & Collection`** | 30 | mtg, deck, collection | Brand kept first; the two highest-intent jobs; covers "mtg deck builder" (with "builder" in S/F) and "mtg collection" |
| B: `ManaHub: Deck Builder for MTG` | 29 | deck builder, mtg | Strongest for the hero feature, loses "collection" |
| C: `ManaHub: MTG Commander Decks` | 28 | mtg, commander, decks | Commander-first niche play; narrower |
| D: `ManaHub for MTG: Decks & Cards` | 30 | mtg, decks, cards | Softest trademark phrasing ("for MTG"); weaker keywords |

- The title **cannot** be A/B tested in store listing experiments. Change it once, then evaluate before
  and after with B5–B7 and B12 over **4–6 weeks** before considering another change. Frequent title
  churn slows down re-indexing.
- If the app reaches solid brand demand (say ≥ 30 % of installs from brand search), revisit option B
  or C to target a different cluster.

### 7.2 Short description (80 characters max)

| Variant | Length | Copy |
|---------|--------|------|
| **Control (recommended)** | 80 | `Build MTG decks from cards you own. Card scanner, prices, Commander life counter` |
| B: keyword-dense | 75 | `MTG deck builder, card scanner, collection tracker & Commander life counter` |
| C: life-counter-led (for the table-top persona / CSL) | 77 | `Commander life counter for 2-6 players, plus decks, collection & card scanner` |

Test control vs B vs C in the first store listing experiment that has enough traffic (§9).

### 7.3 Full description (3,303 / 4,000 characters)

Rules followed: benefit first, one intent cluster per block, plain words (no "local-first"), each P1
term 2–5 times, only live features, Fan Content Policy disclaimer at the end. `•` bullets render well on
Google Play. Leave the remaining ~700 characters free for localization growth and future features
(online play, gamification) when they ship.

```text
Build Magic: The Gathering decks from the cards you already own.

ManaHub is an all-in-one MTG companion: a deck builder that works from your own collection, a card scanner, a collection tracker with card prices, and a Commander life counter. One app, no ads, no account required.

DECK BUILDER THAT USES YOUR COLLECTION
• Deck Wizard: pick a commander, your colors, your favorite cards or a strategy, and ManaHub builds a complete deck from the cards you own
• Commander, Standard, Pioneer, Modern, Legacy, Vintage, Pauper and Casual
• Never adds more copies than you own, and shows exactly which roles still have gaps
• Deck analysis: deck score, mana curve, land count and synergies explained in plain English
• Goldfish playtest: draw opening hands, mulligan and try your deck on a virtual battlefield
• Import and export decklists as text, and browse community decks for ideas

MTG CARD SCANNER
• Point your camera at a card to add it to your collection
• Card recognition runs on your device, so images never leave your phone
• Set foil, condition, language and quantity before adding
• Scan a whole stack into a queue, then add every card at once

COLLECTION TRACKER AND CARD PRICES
• Track every card you own with prices in USD or EUR and see your collection value
• Filter by color, rarity, mana value, format legality, price and oracle text
• Automatic card tags (ramp, removal, card draw, tribal and more) keep your binder organized
• Wishlist and trade binder in the same place
• Import your collection from CSV or text lists (Moxfield, ManaBox and MTG Arena formats)
• Export to text or CSV at any time: your data stays yours

COMMANDER LIFE COUNTER
• 2 to 6 players, built for Commander / EDH and 1v1
• Commander damage, poison, energy, experience and custom counters
• Dice roller, coin flip, turn counter and phase tracker
• Optional hands-free voice commands to end your turn or mark your land drop
• Every game feeds your stats: win rate per deck, average game length and streaks

PLAY WITH YOUR GROUP
• Friends: add players by link, QR code or Game Tag and browse their collections
• Trades: propose, counter and complete trades, and share your wishlist with a link
• Local tournaments for your playgroup or game store: Swiss, Round Robin or Single Elimination

STAY UP TO DATE
• MTG news and videos from the sources you choose, in one feed
• Competitive hub with quick links to decklists, metagame data and Limited ratings

WHO IT'S FOR
• Commander players who want new decks without buying new cards
• Collectors who want to know what their cards are worth
• Playgroups and game stores that need a life counter, pairings and stats in one place
• Returning players who want to see what they can build from an old collection

MADE FOR PLAYERS
• Free to use, with no ads
• Core features work offline and without an account
• Optional free account to back up and sync your collection, decks and stats across devices
• 12 visual themes, from dark to light

Questions or ideas? Use the feedback option in the app. We read every message.

ManaHub is unofficial Fan Content permitted under the Fan Content Policy. Not approved/endorsed by Wizards. Portions of the materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC. Card data and images are provided by Scryfall.
```

**Neutral variant of the import line** (use it if the compatibility mention is ever flagged):
`• Import your collection from CSV or text decklists exported by other popular apps`

**Pre-publish checklist:** every claim still true in the build being published · no hidden feature
mentioned · character count ≤ 4,000 · the disclaimer present · the copy reads naturally aloud.

### 7.4 "What's new" template (≤ 500 characters)

Not indexed, but it affects conversion for returning visitors and it is what update pages and LiveOps
link to. Lead with the user-visible benefit:

```text
NEW: The Deck Wizard now builds 60-card decks (Standard, Pioneer, Modern, Legacy, Vintage, Pauper) from your collection.
IMPROVED: Faster Home screen and smoother widgets.
FIXED: [one line per notable fix]
Enjoying an update? A rating on Google Play helps other players find ManaHub.
```

(The last line is a store-side note, not an in-app prompt, so it does not conflict with §11.)

### 7.5 Developer identity and contact details

- **Developer name:** consider "ManaHub" (or "ManaHub Labs") instead of a personal name. It is
  consistent with the brand, looks more trustworthy, and keeps the owner's name off every listing.
  It must not contain "free", "best", "#1" or similar.
- **Contact email:** a dedicated support address (for example on the branded domain from §14.1), not a
  personal mailbox. It is public on the listing.
- **Website:** the landing page (§14.1). Until it exists, the GitHub repository with an updated README.
- **Privacy policy URL:** a stable public page (the landing site), consistent with `PRIVACY_POLICY.md`.
- **Account deletion URL:** required by Play for apps with accounts. Keep it in sync with
  `docs/account-deletion/`.

### 7.6 Category and tags

- **Category (verify current):** recommended **Entertainment**. That is where hobby companions such
  as Delver Lens sit, and ManaHub is broader than a single-purpose "Tools" utility (ManaBox is listed
  under Tools). Category has little effect on search, but it shapes the "Similar apps" association and
  which chart the app competes in. Before changing it, check which category most competitors in the
  listing's "Similar apps" row use, and follow the cluster that has the strongest competitors.
- **Tags:** pick up to 5 from Play Console's tag list (Store settings → Tags) that describe
  collection, card/trading-card, hobby and game-companion use. Only choose tags that are literally
  accurate.
- Keep "App" (not "Game"). Some life counters list themselves as games, but ManaHub's core value
  (collection, decks, trades) is an app.

---

## 8. Visual assets

### 8.1 Icon

**Audit of the current icon** (`ic_launcher-playstore.png`, 512×512): an attractive five-color
yin-yang hexagon with sun, water drop, skull, flame and tree glyphs on a purple field.

- ✅ Instantly reads as "Magic", colorful, stands out on light and dark backgrounds.
- ⚠️ **Fine detail disappears at 48 dp** (the search-results size). The five glyphs turn into a
  multicolor blob.
- ⚠️ **Not ownable:** WUBRG circles and hexagons are the most common motif in MTG app icons, so it
  does not build brand memory.
- ⚠️ **IP exposure:** the five glyphs are close to Wizards' mana symbols, and using them as the brand
  mark is the most exposed kind of use. Using mana symbols *inside* the app for card costs is normal;
  using them as the product's logo is different.

**Plan:**

1. Design 2 variants that keep the five-color palette (the recognisable "Magic" cue) but use **abstract
   shapes**: (a) a hexagonal hub with five colored segments meeting at a bright center, with no glyphs;
   (b) a bold monogram (a stylised "M" or hub node) over the five-color gradient.
2. Specs: 512×512, 32-bit PNG, ≤ 1 MB, full-bleed square (Google applies the mask and shadow), no text,
   no badges, no ranking claims. Check legibility at 48 dp and 96 dp and in grayscale.
3. Run an **icon store listing experiment** (§9, experiment 3). Ship the winner in the launcher icon in
   the next release so the store and the device match.

### 8.2 Screenshots (phone)

**Specs:** 8 screenshots (Play allows 2–8 per device type). **1080×1920 portrait** PNG or JPEG
(aspect ratio within 2:1; ≥ 1080 px also keeps the app eligible for promotional placements), each
≤ 8 MB. The first 3 matter most: they are what most visitors see without scrolling.

**Design rules**

- Each screenshot = **a caption of 3–6 words stating a benefit** + one real app screen (optionally in a
  flat device frame). Captions in large, high-contrast type, readable at thumbnail size.
- Use the **default theme (NeonVoid)** for screens 1–7, so the listing matches what a new user sees.
  Screen 8 shows theme variety, including the light theme (HallowedPrint).
- Realistic but **synthetic demo data**. Build a demo collection with recognisable staple cards and a
  plausible value. No real users' names, avatars or Game Tags on the friends/trades screens.
- Clean status bar (Android System UI demo mode: full battery, fixed time, no notifications).
- One visual system across all 8 (same background treatment, caption position and font) so they
  read as a story when swiped.
- Do not show hidden features, Wizards logos or set-symbol-heavy compositions as the main visual.

**Storyboard (default listing)**

| # | Caption | Screen to capture | Persona / keyword cluster |
|---|---------|-------------------|---------------------------|
| 1 | **Build decks from cards you own** | Deck Wizard result (full deck + "from your collection" count) or the commander pick step | Brewer · deck builder |
| 2 | **Scan cards in seconds** | Scanner with a detected card and the confirm sheet (foil/condition/qty) | Collector · scanner |
| 3 | **Know what your collection is worth** | Collection with prices + value stat (USD or EUR) | Collector · collection, prices |
| 4 | **Commander life counter, 2–6 players** | 4-player Commander game with commander damage visible | Playgroup · life counter |
| 5 | **See what your deck needs** | Deck analysis: score ring, pillars, a synergy engine explained | Brewer · deck analyzer |
| 6 | **Trade with friends** | Trade negotiation thread or shared wishlist | Trader · trade |
| 7 | **Track wins for every deck** | Stats: per-deck win rate, streak, average game length | Playgroup · stats |
| 8 | **12 themes · No ads · Works offline** | Collage of 3–4 themes (include HallowedPrint) | Trust |

**Alternative first screenshot for experiments:** #4 (life counter) first, which may convert better
for broad "mtg" traffic because it is the most universally needed tool.

**Production workflow (solo-developer friendly)**

1. Build a release-like variant pointed at a demo account with seeded demo data.
2. Pixel-class emulator at 1080×2400 → `adb shell screencap` each screen. Turn on System UI demo mode
   for a clean status bar.
3. Compose captions and frames in one Figma/Canva template (a 1080×1920 frame with the phone capture
   scaled inside). Export PNGs named `phone_01.png` … `phone_08.png`.
4. Keep the template and the source captures in a design folder outside the repo, or in a dedicated
   assets repository. Re-shoot after any visible UI change to a featured screen.
5. **Tablet screenshots:** skip until tablet layouts ship (planned). Uploading stretched phone UI as
   "tablet" screenshots hurts conversion on large screens.

### 8.3 Feature graphic (1024×500, JPEG or 24-bit PNG, no alpha)

- **Content:** logo + wordmark on the left, the headline **"Build decks from the cards you own"**, and
  an angled phone showing the Deck Wizard on the right. Background is the purple/five-color brand
  gradient.
- Keep text and key elements in the central safe area, because the edges can be cropped and a play
  button is overlaid when a promo video exists. Use few words (≤ 7), no small print, no ratings or
  "free/#1" claims.
- It doubles as the video thumbnail and appears in some featured placements, so it must work without
  the icon next to it.

### 8.4 Promo video (optional but valuable)

- YouTube URL, public or unlisted, **monetization off**, not age-restricted. Landscape 16:9 is the
  safe default. Duration 30–45 s. It autoplays muted in some placements, so on-screen text must carry
  the message on its own.
- **Storyboard (30 s):**
  - 0–3 s: hook, "Your bulk → a Commander deck". A stack of cards, then the collection screen.
  - 3–10 s: Deck Wizard picks a commander, generates the deck, deck score appears.
  - 10–15 s: scanning a card (real camera footage cut with screen capture).
  - 15–20 s: collection value.
  - 20–26 s: 4-player Commander game on the life counter at a real table.
  - 26–30 s: end card with logo, "No ads · Works offline" and the Google Play badge.
- The same footage cut vertically (9:16) serves TikTok, Reels and Shorts (§14.5).

---

## 9. Conversion experiments (store listing experiments)

**Precondition:** experiments need traffic. Before starting, use the Play Console experiment setup to
estimate the duration for the chosen minimum detectable effect. **If the estimate is longer than 4
weeks, do not run the experiment yet.** Grow traffic first (§14), and apply best-practice changes
directly with a before/after comparison.

**Setup:** default listing, 50/50 split (or 33/33/33 for 3 arms), metric **retained first-time
installers** (it also protects retention), 90 % confidence, minimum 7 days so every weekday is covered.
Change **one variable per experiment**.

| # | Hypothesis | Variants | Success = |
|---|-----------|----------|-----------|
| 1 | Leading with the unique feature converts better than leading with the universal tool | Screenshot 1 = Deck Wizard (control) vs Life counter | Retained installers +≥ 5 % |
| 2 | A clear promise beats a keyword list in the short description | §7.2 Control vs B vs C | same |
| 3 | A simpler, ownable icon lifts click-through and conversion | Current vs variant (a) vs (b) | same; also check search CTR if available |
| 4 | Benefit captions beat feature captions | "Know what your collection is worth" vs "Collection tracker with prices" | same |
| 5 | A video increases conversion | No video vs 30 s video | same |
| 6 | Localized screenshots convert better (after §13) | EN captions vs localized captions, per locale | same |

Log every experiment (dates, variants, traffic, result, decision) in the Appendix B sheet. Apply
winners to the default listing, then re-baseline.

---

## 10. Custom store listings (CSLs)

Google Play allows up to **50 CSLs**, targeted by **country**, **search keyword**, **URL (a `listing`
parameter)** or Google Ads campaign. Keyword targeting only offers keywords that already bring the app
traffic, so it becomes available **after** §7 and §14 start producing search visits.

| CSL | Targeting | Differences from default |
|-----|-----------|--------------------------|
| **Life counter** | Keywords like "mtg life counter", "commander life counter" (when offered) | Screenshot 1 = life counter; short description variant C; video opens on the table scene |
| **Scanner / collection** | "mtg scanner", "mtg card scanner", "mtg collection" | Screenshots 2–3 moved to the front; short description focused on scan + value |
| **Deck builder** | "mtg deck builder", "commander deck builder" | Default order (Deck Wizard first); short description stresses "from cards you own" |
| **LGS / tournament** | **URL listing** used in QR flyers for game stores | Life counter + local tournament + stats first |
| **Creators** | **URL listing** per creator campaign | Deck Wizard first, with a caption matching the creator's video hook |
| **Country (ES, DE, …)** | Country, after §13 | Localized text + captions |

---

## 11. Ratings and reviews program

### 11.1 Fix the current flow (engineering task E1, §20)

- **Buttons** ("Rate ManaHub" in Profile, the Home first-step card) → open the Play listing directly
  (`market://details?id=com.mmg.manahub`, with the `https://play.google.com/store/apps/details?id=…`
  fallback). Never call the In-App Review API from a button.
- **Copy:** replace "Enjoying ManaHub?" with neutral wording, for example title **"Rate ManaHub on
  Google Play"** and subtitle "Reviews help other players find the app." No opinion questions before
  the review flow.

### 11.2 Contextual prompts with the In-App Review API (engineering task E2)

**Eligibility gate (all must hold):** install age ≥ 3 days · ≥ 3 sessions on distinct days · no crash
or error toast in the current session · not prompted in the last 60 days (Google also applies its own
quota) · never during a game in progress or the first session.

**Triggers (the first one that fires, after the success moment has *finished* on screen):**

1. The Deck Wizard finished and the user opened the resulting deck in Deck Studio.
2. A game ended and the **local seat won** (the life-counter result screen).
3. A collection import finished with ≥ 50 cards added.
4. A trade was marked complete.
5. The 25th card was scanned (cumulative).

**Never:** gate the prompt on sentiment (asking "do you like it?" and sending only happy users to Play
is prohibited review gating), offer anything in exchange for a review, or show custom UI that predicts
the star rating.

**Telemetry (CLAUDE.md rule):** add `review_prompt_requested_<trigger>` and
`review_prompt_store_opened_<source>` events, and have `crashlytics-ux-auditor` review them before
shipping.

### 11.3 Review operations

- Reply to **every** review within 48 h, including 5-star ones (short and personal). For bug reports:
  thank the user, give a workaround if there is one, and **reply again when the fix ships**. Users
  often update their rating.
- Tag reviews weekly (bug · feature request · praise · confusion) and feed recurring confusion into
  the listing copy and onboarding.
- Route qualitative feedback to the existing in-app **Feedback sheet**, offered to all users equally
  and never as a filter in front of the review prompt.
- Target ≥ 4.5 ★. Because recent ratings weigh most, a steady monthly flow matters more than one burst.

---

## 12. Technical quality (ranking gates)

| Item | Target | Action |
|------|--------|--------|
| User-perceived crash rate | < 1.09 % overall, < 8 % on every device model | Weekly Android vitals + Crashlytics review; fix the top clusters first |
| User-perceived ANR rate | < 0.47 % overall, < 8 % per device | Check startup and heavy Room/sync work on the main thread |
| Cold start | As fast as possible (baseline profile already exists) | Keep the `:baseline-profile` module current with each release |
| Download size | Monitor in Play Console; smaller = higher conversion on slow networks | R8 + resource shrinking already on; audit large assets |
| Data safety form | Exactly matches reality (Firebase Analytics/Crashlytics diagnostics, FCM token, account email, synced data) | Re-check whenever an SDK or synced data type changes; listing claims ("no data selling") must match |
| Target API | Keep up with the yearly requirement (currently targetSdk 36) | — |
| Large screens | Tablet/foldable layouts are planned | When they ship: tablet screenshots + large-screen quality checklist → eligibility for tablet recommendations |
| Retention | Improve D1/D7 (a first-class ranking signal) | Onboarding should reach a "first win" in < 2 min: import or scan → Deck Wizard, or start a game |

---

## 13. Localization strategy (owner decision required)

**Context:** the app is **English-only** by project rule (CLAUDE.md language rules). That rule covers
in-app UI and string resources. A **localized store listing** is a separate decision: it only
translates the Play Store page, not the app. It is the main way to appear for non-English searches
("contador de vidas mtg", "mtg lebenszähler", "scanner cartes magic").

**Trade-off:** localized listings open new search surfaces with far less competition. But a user who
does not read English may uninstall, and uninstalls hurt retention signals. Mitigation: state clearly
in every localized description, near the top, that **"The app is currently available in English."**

**Recommendation:** approve localization in phases, measured per locale.

| Phase | Locales | Rationale |
|-------|---------|-----------|
| 6a | **es-ES, es-419** | Owner's home market and a large Latin American audience; voice commands already support Spanish |
| 6b | **de-DE** | One of Europe's largest MTG markets; voice commands support German; high English proficiency |
| 6c | fr-FR, pt-BR, it-IT | Large MTG scenes |
| 6d | ja-JP, pl-PL | Big MTG scenes, lower English proficiency. Only after an in-app localization decision |

**Rules:** human-reviewed translations (Play Console's machine translation is fine for a first draft
only) · research keywords per locale (Appendix C gives seeds) · localize screenshot captions, which
matter as much as the text · keep "MTG" and card names in English (players search that way).
English variants (en-GB, en-AU) can reuse the US copy.

---

## 14. Off-store acquisition and web SEO

### 14.1 Landing site on a branded domain

- **Domain:** register a short branded domain (check availability, e.g. `manahub.app` or
  `getmanahub.com`) and use it for the website, support email, privacy policy and, later, App Links.
- **Static HTML pages** (not the Compose/wasm web app: a canvas-rendered wasm app is not crawlable, so
  it will never rank on web search). One page per intent, each with the Google Play badge
  (UTM-tagged, §14.6), screenshots, a short FAQ and the fan-content disclaimer:
  - `/` home: the §5.1 promise.
  - `/commander-deck-builder` — "Build a Commander deck from your collection".
  - `/mtg-life-counter` — "Free Commander life counter for 2–6 players".
  - `/mtg-card-scanner` — "Scan MTG cards on your phone, privately".
  - `/mtg-collection-tracker` — "Track your MTG collection value".
  - `/trade` — trading with friends.
  - Spanish mirrors (`/es/…`) if §13 is approved.
- **Technical SEO:** unique `<title>` and meta description per page, Open Graph/Twitter cards (invite
  links get shared on WhatsApp and Discord), `SoftwareApplication` structured data (**never** invent
  ratings), a sitemap, fast static hosting (Cloudflare Pages is already in the stack).
- **Content that earns links** (1–2 posts a month, EN and later ES): "How to build a Commander deck from
  your collection", "Commander life counter: what to track", "How to value your MTG collection",
  set-release tier or checklist posts timed with each release.
- **App Links migration (engineering task E4):** add the new host next to `miguelmglez.github.io`
  (keep the old host so existing invite and trade links keep working) and publish `assetlinks.json` on
  the new domain.
- The invite and trade-list **web fallback pages** (currently on `github.io`) must show a prominent
  Play badge with a referrer (`utm_source=invite` / `utm_source=trade_list`).

### 14.2 GitHub repository (quick win)

- README: add the **"Get it on Google Play"** badge and 3–4 screenshots at the top, replace
  *"Screenshots: Coming soon"*, and move "Play Store release" from *Planned* to done. Fix stale facts
  (DB version). Add the §5.1 one-line value proposition as the first line.
- Repository topics: `mtg`, `magic-the-gathering`, `life-counter`, `deck-builder`,
  `collection-manager`, `card-scanner`, `android`, `jetpack-compose`, `kotlin-multiplatform`.
- Developer-community angle (backlinks, not players): Compose/KMP showcase lists and the Kotlin
  community. The app is a real-world KMP migration case study.

### 14.3 Communities (players)

- **Reddit** (r/magicTCG, r/EDH, r/mtg): read each subreddit's self-promotion rules first. Most allow a developer post that offers value and asks for
  feedback. Pitch the hook, not the app: *"I built a free Android app that builds a Commander deck
  from the cards you already own — looking for brutal feedback."* Answer every comment. Never use alt
  accounts or ask for upvotes. **One strong launch post > many weak ones.**
- **Discord:** Commander and local community servers. Share only where allowed, ideally in response to
  "what app do you use for X?" threads.
- **Spanish scene:** the owner's home market is a natural beachhead. Spanish MTG groups (Telegram,
  WhatsApp, Discord), Spanish Commander content creators, and local game stores (§14.4).

### 14.4 Local game stores (LGS)

- An A6 flyer or table tent with a **QR code to the LGS custom store listing URL** (§10) and a UTM tag
  per store: *"Commander night? Free life counter + tournament pairings + deck stats. No ads."*
- Offer store organisers the local tournament feature (Swiss / Round Robin / Single Elimination) for
  casual Commander or draft nights. Organisers bring whole tables of players.
- Start with 3–5 stores the owner visits, measure installs per store with the UTM, then expand.

### 14.5 Content creators

- Micro-creators (1 K–50 K subscribers) on YouTube, TikTok and Instagram in Commander and budget
  brewing, in English and Spanish.
- A natural video format: **"I let an app build a Commander deck from my bulk — then played it."**
  It shows off the Deck Wizard and needs nothing beyond the app.
- Give each creator a URL-targeted CSL (§10) and a UTM link; measure installs per creator.
- Disclosure: creators must follow their platform's rules for sponsored content, if compensated.

### 14.6 Measurement plumbing: UTM and referrer

- Play Store link format for every external placement:
  `https://play.google.com/store/apps/details?id=com.mmg.manahub&referrer=utm_source%3D<source>%26utm_medium%3D<medium>%26utm_campaign%3D<campaign>`
  These appear in Play Console acquisition reports as third-party referrals. **Verify** that Firebase
  Analytics attributes `first_open` campaigns through the Play Install Referrer (engineering task E6).
- One naming convention, for example: `utm_source` = reddit | discord | lgs_<store> | creator_<name> |
  website | github | invite | trade_list; `utm_medium` = post | qr | video | link | share;
  `utm_campaign` = launch_2026q4 | set_<code> | …

### 14.7 Directories

- **AlternativeTo:** add ManaHub as an alternative to ManaBox, Delver Lens, Moxfield, TopDecked, Lotus
  and Dragon Shield (factual feature tags: free, no ads, offline, Android).
- Other free listings: MTG community tool lists, wikis and "best MTG apps" roundups. Contact the
  authors of the roundups that currently rank (Draftsim, GrimDeck, Inked Gaming, etc.) with a short,
  honest pitch and a press kit (icon, screenshots, 50/100/250-word descriptions).

### 14.8 Small paid test (optional, after §7–8 are live)

- **Google Ads App campaign** (it serves on Play search, YouTube and Discover). Small daily budget for
  2–4 weeks in 2–3 countries, optimized for an **in-app action** (for example `deck_created` or
  `game_finished` via Firebase), not raw installs. Cheap, low-quality installs hurt retention signals.
- Send traffic to a CSL. Evaluate by cost per *retained* user. Stop if D7 retention of paid users is
  well below organic.

### 14.9 Google Play featuring

- Once the new listing and assets are live, and before a notable release (for example the Deck Wizard
  60-card wave, or online play when it ships), submit a **featuring nomination** in Play Console well
  ahead of the release date.
- **Promotional content / LiveOps** cards (set releases, major updates) require meeting Google's
  eligibility criteria. Check eligibility in Play Console periodically.

---

## 15. In-app growth loops

| Loop | Current | Improvement |
|------|---------|-------------|
| Friend invite | Link to `github.io/invite/{code}` + share text | Fallback page with a Play badge + `utm_source=invite`; later the branded domain (E3, E4) |
| Shared trade list | `github.io/list/{shareId}` | Same as above (`utm_source=trade_list`) |
| Deck sharing | Plain-text export (keep it clean for imports) | Add **"Share as image"**: the decklist rendered as an image with a small "Built with ManaHub" footer (E5) |
| Tournament standings | In-app only | **"Share standings/pairings as image"** for WhatsApp and Discord groups; every LGS night becomes an ad (E5) |
| Game result | In-app | Optional "share result" card (winner, commander, turns) |

---

## 16. Seasonal calendar and release cadence

- **MTG set releases** (prerelease weekend → release) create search spikes for "deck builder", "card
  scanner" and "prices". Plan a ManaHub update, a "What's new" entry, a short post and (if eligible) a
  LiveOps card for each Standard set release and for major Commander product releases.
- **November–December:** gifting season. New players get Commander precons and starter kits, and life
  counter and deck-builder searches rise. Have the best assets and CSLs live by early November.
- **Cadence:** ship a user-visible improvement every 2–4 weeks. Keep "What's new" benefit-led.
- Hidden features shipping later (online play, gamification, Daily Puzzle, draft simulator) are
  **marketing events**: update screenshots/CSLs, "What's new", featuring nomination, community posts.

---

## 17. Measurement

### 17.1 KPIs

| KPI | Source | Cadence | 90-day target |
|-----|--------|---------|---------------|
| Store listing visitors from Google Play search | Play Console B5/B6 | Weekly | 3× baseline |
| Visitor → installer conversion | Play Console B5 | Weekly | +30 % relative; ≥ category peer median |
| Top search terms (visitors, installs) | Play Console B7 | Weekly | ≥ 10 non-brand terms bringing installs |
| Keyword ranks (20 terms × 4 countries) | Manual (B12) | Weekly | ≥ 10 terms in the top 10 of at least one country |
| Rating average / new ratings per month | Ratings & reviews | Weekly | ≥ 4.5 ★ / ≥ 15 per month |
| D1 / D7 retention of new users | Firebase | Monthly | + meaningful increase over baseline |
| Crash / ANR rate | Android vitals | Weekly | < 1.09 % / < 0.47 % (gates) |
| Installs by UTM source | Play Console + Firebase | Per campaign | Identify the 2 best channels |

### 17.2 Rituals

- **Weekly (30 min):** fill in the Appendix B sheet, read new reviews and reply, check vitals.
- **Monthly (1 h):** review experiments and CSL performance, keyword ranks trend, decide the next
  experiment and the next content piece.
- **Per release:** "What's new", screenshots still accurate, Data safety still accurate, disclaimer
  present.

---

## 18. Roadmap

| Week | Workstream | Deliverables | Owner |
|------|-----------|--------------|-------|
| 0 | Baseline | B1–B12 captured; availability/track verified and fixed; rank-tracking sheet started | Owner |
| 0–1 | Metadata | Title A, short description (control), full description (§7.3), category/tags, developer name, support email | Owner |
| 1 | Off-store quick wins | README + topics, AlternativeTo, UTM convention, social handles reserved (Instagram, TikTok, X, Bluesky, YouTube, Reddit) | Owner |
| 1–2 | Ratings fix | E1 (buttons → store; neutral copy) + E2 (contextual prompts) + telemetry review | `android-kotlin-architect` + `crashlytics-ux-auditor` |
| 1–3 | Creative | Demo data, 8 screenshots, feature graphic, 2 icon variants, 30 s video | Owner / designer |
| 2–4 | Web | Domain, landing site (EN), fallback pages with Play badge, E3 | Owner (static site) + architect (E3/E4) |
| 3–4 | Launch push | Reddit/Discord posts, 3–5 LGS flyers, first 3–5 creators | Owner |
| 4+ | Experiments | Experiments 1–3 (when traffic allows), first keyword CSLs | Owner |
| 6 | Checkpoint | Before/after review of the title change (B5–B7, B12); decide on §13 | Owner |
| 6–10 | Localization (if approved) | es-ES/es-419 listing + captions + ES landing pages; then de-DE | Owner + native reviewers |
| 8–12 | Scale | Paid test (§14.8), share-as-image loops (E5), featuring nomination, set-release calendar | Owner + architect |

---

## 19. Risks and compliance

| Risk | Mitigation |
|------|------------|
| Trademark complaint about "MTG" in the title or about the icon's mana-like glyphs | Category-standard descriptive use, clear fan-content disclaimer, no logos or set symbols, icon variant without glyphs (§8.1). Keep option D ("for MTG") as a fallback title |
| Metadata policy violation (promotional words, comparisons, keyword stuffing) | Rules in §6.4; checklist in §7.3; natural phrasing |
| Promising features that are hidden or break | §3.2 list; re-check `FeatureFlags.kt` before every listing update |
| Review-policy violations (gating, incentives) | §11 rules; neutral copy; no rewards for reviews |
| Localized listing for an English-only app causes uninstalls | "App is in English" line near the top; phase in and measure per locale |
| Paid installs dilute retention signals | Optimize for in-app actions; cap budget; stop on bad D7 |
| Self-promotion backlash in communities | Follow each community's rules; genuine, feedback-seeking posts; the developer answers personally |
| Experiments that never reach significance | Duration estimate first; grow traffic before testing |
| Data safety mismatch after SDK changes | Re-check at every release (§17.2) |

---

## 20. Engineering backlog

Per CLAUDE.md, all `.kt` changes are delegated to **`android-kotlin-architect`**. Every item that adds
or changes analytics goes through **`crashlytics-ux-auditor`**. User-facing strings stay English-only.

| ID | Task | Files (starting points) | Notes |
|----|------|-------------------------|-------|
| **E1** | Rate buttons open the Play listing directly (`market://` → https fallback), with neutral copy ("Rate ManaHub on Google Play") | `feature/profile/presentation/ProfileScreen.kt` (`RateAppRow`, ~l.380), `app/navigation/AppNavGraph.kt` (`HomeAction.RateApp`, ~l.439), `strings.xml` `first_step_rate_app_*` | Removes silent no-op taps; complies with review guidelines |
| **E2** | Contextual In-App Review prompts with the §11.2 eligibility gate and triggers | New small `ReviewPromptCoordinator` (KMP-friendly interface in shared code, Play implementation in `androidMain`/`app`) + call sites: Deck Wizard result → Deck Studio, game result (local win), import complete, trade complete, scan counter | Needs a persisted "last prompted" timestamp + session counters; telemetry events per §11.2 |
| **E3** | Referrer-tagged store links in share texts and on the web fallback pages | `strings.xml` share texts; `FriendRepositoryImpl.kt` (invite URL); the separate GitHub Pages site | Web pages live outside this repo |
| **E4** | Branded-domain App Links (new host + existing `miguelmglez.github.io`) | `AndroidManifest.xml` intent filters, `AppNavGraph.kt` deep-link patterns, `cloudflare/assetlinks.json` on the new domain | Keep old links working |
| **E5** | "Share as image" for decks and tournament standings, with a subtle brand footer | `DeckStudioScreen.kt`, tournament detail screen | Keep the plain-text export unchanged for imports; branding goes outside the card images (Scryfall forbids watermarks/logos on card images) |
| **E6** | Verify install-referrer campaign attribution in Firebase Analytics | Gradle dependencies, Firebase setup | Add the Play Install Referrer library if it is not present transitively |
| **E7** | Demo-data seed for store screenshots (debug-only) | Debug source set only | Must never ship in release |
| **E8** | Tablet/foldable layouts (already planned) → tablet screenshots | — | Unlocks large-screen discovery |

---

## Appendix A — Character counts (verified)

| Field | Text | Chars | Limit |
|-------|------|-------|-------|
| Title A | `ManaHub: MTG Deck & Collection` | 30 | 30 |
| Title B | `ManaHub: Deck Builder for MTG` | 29 | 30 |
| Title C | `ManaHub: MTG Commander Decks` | 28 | 30 |
| Title D | `ManaHub for MTG: Decks & Cards` | 30 | 30 |
| Short control | `Build MTG decks from cards you own. Card scanner, prices, Commander life counter` | 80 | 80 |
| Short B | `MTG deck builder, card scanner, collection tracker & Commander life counter` | 75 | 80 |
| Short C | `Commander life counter for 2-6 players, plus decks, collection & card scanner` | 77 | 80 |
| Full description (§7.3) | — | 3,303 | 4,000 |

Rejected for length: `ManaHub: MTG Collection Tracker` (31), `ManaHub: MTG Deck & Life Counter` (32),
`ManaHub – MTG Collection & Decks` (32), `ManaHub: Commander Deck Builder` (31).

## Appendix B — Tracking sheet template

**Tab 1: Weekly KPIs** — columns: week · visitors (search / explore / referral) · installers ·
conversion % · peer median % · ratings (new, avg) · crash % · ANR % · notes/changes shipped.

**Tab 2: Keyword ranks** — rows: the 20 P1/P2 keywords of §6.1; columns: week × country (US, GB, ES,
DE) → position (or ">50").

**Tab 3: Listing change log** — date · field changed · old → new · reason · linked experiment.

**Tab 4: Experiments** — id · hypothesis · variants · start/end · traffic per arm · result ·
confidence · decision.

**Tab 5: Campaigns (UTM)** — source · medium · campaign · date · link · store visitors · installs ·
D7 retained.

## Appendix C — Localized keyword seeds (validate with native speakers and §6.3)

| Locale | Seeds |
|--------|-------|
| es-ES / es-419 | contador de vidas mtg · contador de vida magic · commander contador de vidas · escáner de cartas magic · colección de cartas magic · precios cartas magic · constructor de mazos commander · crear mazo commander · mazos con mis cartas |
| de-DE | mtg lebenszähler · commander lebenspunkte · magic karten scanner · mtg sammlung verwalten · kartenpreise magic · commander deck bauen · deckbuilder mtg |
| fr-FR | compteur de vie mtg · compteur de points de vie commander · scanner cartes magic · gérer collection magic · prix cartes magic · créer deck commander |
| pt-BR | contador de vida mtg · contador de vida commander · scanner de cartas magic · coleção magic · preço cartas magic · montar deck commander |
| it-IT | contatore vita mtg · contapunti commander · scanner carte magic · collezione magic · prezzi carte magic · costruire mazzo commander |

In every locale, many players also search in English ("mtg life counter"), so keep "MTG", "Commander"
and card names in English inside the localized copy.

## Appendix D — Sources (retrieved 2026-09-24)

- ManaHub listing (search-index fragments): https://play.google.com/store/apps/details?id=com.mmg.manahub&hl=en_AU
- ManaHub repository: https://github.com/Miguelmglez/ManaHub
- ManaBox MTG: https://play.google.com/store/apps/details?id=skilldevs.com.manabox · https://www.appbrain.com/app/manabox-mtg/skilldevs.com.manabox · https://manabox.app/
- MTG Scanner - Dragon Shield: https://play.google.com/store/apps/details?id=pt.tscg.mtgmanager · https://www.similarweb.com/app/google-play/pt.tscg.mtgmanager/statistics/ · https://mtg.dragonshield.com/download-app
- MTG Card Scanner Delver Lens: https://play.google.com/store/apps/details?id=delverlab.delverlens · https://www.delverlab.com/
- TopDecked MTG: https://play.google.com/store/apps/details?id=com.maritlabs.topdecked.mtg · https://www.topdecked.com/ · https://www.similarweb.com/app/google-play/com.maritlabs.topdecked.mtg/statistics/
- Mythic Tools MTG: https://play.google.com/store/apps/details?id=com.studiolaganne.lengendarylens · https://www.appbrain.com/app/mtg-life-counter-mythic-tools/com.studiolaganne.lengendarylens
- MTG Life Counter App: Lotus: https://play.google.com/store/apps/details?id=com.vanilla.mtgcounter · https://www.vanilla.nl/products/lotus/
- Lifetap Life Counter for MTG: https://play.google.com/store/apps/details?id=com.lifetap · https://www.appbrain.com/app/lifetap-life-counter-for-mtg/com.lifetap
- Magic: The Gathering Companion: https://play.google.com/store/apps/details?id=com.wizards.winter_orb · https://magic.wizards.com/en/products/companion-app
- Other life counters: https://play.google.com/store/apps/details?id=com.getalife.tracker · https://play.google.com/store/apps/details?id=com.bartollesina.lifetap · https://play.google.com/store/apps/details?id=net.shalafi.android.mtgpro
- Decked Builder: https://play.google.com/store/apps/details?id=com.deckedbuilder.deckedbuilder
- Category roundups: https://grimdeck.com/blog/best-mtg-collection-tracker-scanner · https://www.cardsaiapp.com/blog/best-magic-the-gathering-apps · https://www.inkedgaming.com/blogs/news/8-great-apps-for-mtg-players · https://draftsim.com/best-mtg-life-counter-app/ · https://alternativeto.net/software/manabox
- Archidekt native app status: https://archidekt.com/forum/thread/11043292 · AI builders: https://manaforge.tools/en/blog/manaforge-vs-moxfield-vs-archidekt
- Google Play metadata policy & listing best practices: https://support.google.com/googleplay/android-developer/answer/9898842 · https://support.google.com/googleplay/android-developer/answer/13393723 · https://www.apptweak.com/en/aso-blog/how-to-prepare-for-new-google-metadata-policy-changes
- Ranking factors 2026: https://www.apptweak.com/en/aso-blog/google-play-ranking-factors · https://asomobile.net/en/blog/app-listings-in-google-play-2026/ · https://www.pressplay.run/blog/google-play-retention-shift-aso
- Android vitals thresholds: https://developer.android.com/topic/performance/vitals · https://android-developers.googleblog.com/2022/10/raising-bar-on-technical-quality-on-google-play.html
- Store listing experiments, CSLs, promotional content: https://play.google.com/console/about/store-listing-experiments/ · https://play.google.com/console/about/customstorelistings/ · https://support.google.com/googleplay/android-developer/answer/9867158 · https://play.google.com/console/about/programs/promotionalcontent/
- Wizards Fan Content Policy: https://company.wizards.com/en/legal/fancontentpolicy
