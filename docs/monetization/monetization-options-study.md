# ManaHub — Monetization Options Study

**Status date:** 2026-09-24 · **Companion to:** `docs/seo/google-play-aso-improvement-plan.md`
**Question answered:** within the rules of Wizards of the Coast and of every third-party service
ManaHub depends on, how can the app make money, both with today's features and with new features
that competitors already have?

> **Not legal advice.** This is a product and policy study based on the public terms available on
> 2026-09-24. Before charging users, have the Wizards Fan Content Policy position (§2.1) and the
> Archidekt/EDHREC dependencies (§2.3–2.4) confirmed in writing or reviewed by a lawyer.

---

## 0. Executive summary

**The binding constraint is not Google Play. It is the IP and data layer.**

1. **Wizards of the Coast Fan Content Policy (FCP).** The app's own disclaimer calls it *"unofficial
   Fan Content permitted under the Fan Content Policy"*. The FCP requires Fan Content to be **free to
   access**: no payments, subscriptions or registration walls, and no selling or licensing. It **does
   allow** subsidising the content with **ads, sponsorships and donations**, as long as they do not
   interfere with access.
2. **Scryfall** (card data, images, prices) forbids **paywalling Scryfall data**. Users must be able
   to access card data anonymously or with a free account. Scryfall also forbids repackaging its data
   without adding value, and adding watermarks or logos to card images.
3. **Archidekt**'s terms prohibit commercially exploiting the site and accessing it to build a
   competing product. ManaHub, a deck builder, uses Archidekt's undocumented API for Community Decks
   and samples its categories in `tools/tag-pipeline`. **EDHREC**'s JSON endpoints are unofficial
   (terms could not be verified). Charging money increases exposure on both.

**Consequence:** anything that shows cards, prices or deck data (scanner, collection, deck builder,
Deck Wizard, analysis, life counter with card lookups) **must stay free**. Money can come from the
things that do **not** gate access:

| Stream | Fits the rules? | Revenue potential | Recommendation |
|--------|-----------------|-------------------|----------------|
| **Affiliate commerce**: "buy the missing cards" from the Deck Wizard gap report, wishlist and card detail | ✅ (physical goods; retailer programs) | Medium, grows with users | **Do first** |
| **Supporter tips + "Supporter" subscription** with non-gating perks (cosmetics, icons, badge, voting) | ✅ donations explicitly allowed; perks must not gate card content | Low–medium | **Do second** |
| **Sponsorships** (accessory brands, local game stores, sponsored cosmetic themes) | ✅ explicitly allowed | Low–medium, lumpy | Opportunistic |
| **B2B organizer tools for game stores** (event pages, registration, big-screen pairings) | ✅ mostly (tournament ops are not WotC IP) | Medium, the most defensible | **Medium-term bet** |
| **Compute-heavy AI deck coach** with a free quota and paid credits | ⚠️ grey zone (gates a feature, not the data) | Medium | Only after permission or with a generous free tier |
| **Ads (AdMob)** | ✅ allowed by the FCP; ❌ breaks the "no ads" brand promise; YouTube/news limits | Low at the current scale | **Not now** |
| **Paid app / paywalled features / capped free tier** (what Dragon Shield and TopDecked do) | ❌ conflicts with the FCP and Scryfall as ManaHub is built today | — | **Do not do** |

**Revenue will be small until the app grows.** At illustrative rates (§7) the recommended mix yields
roughly **€40–70 per 1,000 monthly active users per month**. The first realistic milestone is covering
infrastructure (Supabase, Cloudflare, Play fees), not income. The SEO/ASO plan is the prerequisite for
any meaningful number.

**Before any money flows (compliance items, §8):** a Data sources & credits screen; written
permission, or removal of paid-tier exposure, for Archidekt/EDHREC/deckstats; the EU DSA *trader*
declaration on Google Play (it publishes your contact address); privacy-policy and Data safety updates;
tax setup.

---

## 1. Method and limitations

- **Dependencies inventoried from the code** (hosts referenced in `app/`, `shared/`, `cloudflare/`,
  `supabase/`, `tools/`). Runtime calls: Scryfall (API, images, SVGs), Archidekt, EDHREC
  (`json.edhrec.com`), Commander Spellbook, deckstats.net (import by URL), YouTube (RSS + embedded
  player), third-party RSS/Atom news feeds, Supabase, Firebase, Cloudflare Workers/R2.
  Worker/pipeline-side: TopDeck.gg and Spicerack (competitive Worker, not yet deployed), 17Lands and
  MTGJSON (draft content pipeline), EDHREC + Archidekt (`tools/tag-pipeline`). Deep links only:
  MTGGoldfish, MTGTop8, magic.gg, the Wizards store locator, etc.
- **Terms reviewed** through web search results. Direct fetches of `company.wizards.com`,
  `scryfall.com`, `archidekt.com` and the Play Store are blocked by this environment's network policy,
  so the quotes below come from search-engine extracts of those pages. They are cited in Appendix A
  and should be re-read at the source before acting.
- **Competitor pricing** comes from public pages and third-party roundups (2025–2026).

---

## 2. Constraint map: what each dependency allows

### 2.1 Wizards of the Coast — Fan Content Policy

**What the policy says (extracts):**
- You may use Wizards' IP to make Fan Content that you **share with the community for free**. You
  **can't require payments, surveys, downloads, subscriptions or email registration** to access it,
  and you **can't sell or license** it. It must be free for others to view, access, share and use.
- You **may subsidise** Fan Content with **sponsorships, ad revenue and donations** (Patreon, Twitch,
  YouTube) **as long as they don't interfere with the community's access**.
- You must make clear it is **unofficial** (not approved or endorsed by Wizards). You can't use Wizards
  **logos or trademarks** as your own branding without written permission.

**Open ambiguity: are apps covered at all?** Some secondary sources say Wizards' policies do not let
you publish or sell *applications* for its brands. That wording comes from the older D&D *Fan Site
Policy*. The current FCP text could not be fetched here. Two readings:
- **(a) Apps are Fan Content.** Then the free-access rule applies to the whole app, and ads,
  sponsorships and donations are the sanctioned revenue.
- **(b) Apps are outside the FCP.** Then the app does not rely on a written permission at all, only
  on Wizards' long-standing tolerance of companion apps (every competitor exists this way), and any
  monetization depends on that tolerance.

Either way, **the safest monetization is the one the FCP explicitly blesses (ads, sponsorships,
donations) plus revenue that does not come from Wizards' IP** (affiliate commerce on physical goods,
B2B services, original cosmetics).

**Market reality (a tolerance signal, not a permission):** ManaBox Pro ($2.49/mo, $22.99/yr, includes
cloud sync), Dragon Shield Premium ($2.99/mo, $29.99/yr; the free tier caps scanning and tracking),
TopDecked Spark/Powered/Elite (the free tier caps the collection at 375 cards, Powered raises it to
6,000), Moxfield (Patreon from $1/mo), Archidekt (Patreon $2/mo removes ads) and EDHREC (Patreon) all
charge in some form. No public enforcement against them is known. Some of them may have private
agreements or their own data (Dragon Shield is a major accessories brand), so **do not copy their
paywalls**.

**Rules for ManaHub:**
- W1. Never charge for access to anything that displays Wizards IP (card names, images, rules text,
  mana or set symbols).
- W2. Paid perks must be **original** (procedural cosmetics, app icons, badges) or **services**
  (compute, B2B tooling), never "more cards" or "more decks".
- W3. Keep the unofficial disclaimer everywhere money is involved (store listing, supporter screen,
  website).
- W4. Optional but recommended once revenue exists: email Wizards' licensing/legal contact describing
  the model and ask for comment. Keep the answer on file.

### 2.2 Scryfall (card data, images, prices, SVG symbols)

**Guidelines (extracts):**
- Data is provided free *"for the primary purpose of creating additional Magic software, performing
  research, or creating community content"*.
- **You may not paywall access to Scryfall data.** You may not require payments, surveys,
  subscriptions, ratings, chat-server joins or follows in exchange for it. With an account system,
  users must be able to access card data **anonymously or with free accounts**.
- **You may not simply repackage, republish or proxy** the data. The software must add value.
- **Images:** don't cover, crop or clip the copyright or artist line. With `art_crop`, show the
  artist and copyright elsewhere in the same interface. **Don't add your own watermarks, stamps or
  logos to card images.**
- Repeated misuse → API access restricted or blocked.

**Implications:**
- S1. Prices, card search, card images and anything derived from them (price history built from
  Scryfall snapshots, collection-value reports) **stay free**.
- S2. `Card.purchaseUris` (Scryfall's `purchase_uris`), already shown in `CardDetailScreen`, **carry
  Scryfall's own affiliate tags**. Keep them as they are: that revenue funds the data source. Add
  ManaHub's own affiliate links **only for retailers Scryfall does not link** (Card Kingdom), or ask
  Scryfall first before adding a second TCGplayer/Cardmarket link.
- S3. The "share deck/standings as image" idea in the SEO plan (E5) must keep any ManaHub branding
  **outside** the card images.
- S4. A single dependency with an enforcement switch. Monetization must never create an incentive to
  over-call the API (the rate-limit queues stay authoritative).

### 2.3 Archidekt (Community Decks, "decks like yours", tag-pipeline sampling)

- The terms of service prohibit users from **commercially exploiting the Site**, **copying or
  distributing content without permission**, and **accessing the Site to build a similar or
  competitive** product. There is no official, documented public API. The team has said it cannot
  maintain API docs.
- ManaHub is a deck builder (a competitor) that calls Archidekt at runtime (≤ 5 req/s via
  `ArchidektRequestQueue`) and samples Archidekt categories offline in `tools/tag-pipeline`.
- **Risk today:** low but real. **Risk once monetized:** materially higher.
- **Options:** (a) ask Archidekt for written permission or a partnership. Precedent: Card Kingdom
  already runs a `partner=archidekt` referral with them, so they understand partner economics.
  (b) Keep Community Decks strictly out of any paid tier and away from ad slots. (c) Replace or
  supplement with sources whose terms allow reuse.

### 2.4 EDHREC (`json.edhrec.com` theme pages; runtime tag enrichment + offline pipeline)

- The JSON endpoints are open (no key) but **undocumented and unofficial**. EDHREC's terms could not
  be verified from this environment. The `edhrec_rank` field comes via Scryfall (fine).
- **Action:** read EDHREC's terms at the source and ask for permission before monetizing. Until then,
  keep EDHREC-derived signals free and uncached beyond what the feature needs.

### 2.5 Commander Spellbook (combos)

- Backend and site are **MIT-licensed open source**. The API is for sparse, unauthenticated calls
  (~80 req/min is safe). Name your service in the `User-Agent`, handle 429s, and **credit and link back
  to commanderspellbook.com**.
- **Action:** add visible credit + link wherever combo data appears (see §8, credits screen). No
  monetization restriction found.

### 2.6 deckstats.net (import deck by URL)

- No public API or terms found. The endpoint appears non-public. User-initiated, low-volume import is
  low risk. **Keep it free.** Consider asking deckstats, or dropping it if it ever becomes a
  monetized flow.

### 2.7 TopDeck.gg and Spicerack (competitive Worker, not yet deployed)

- TopDeck.gg requires a **visible credit and link back** on any project using its API, and has its own
  terms. Spicerack's developer terms could not be verified. **Action:** confirm both before the Worker
  ships. TopDeck.gg and Spicerack sell tournament software to stores, so a ManaHub B2B organizer
  product (§5.8) would compete with them. Read their terms with that in mind.

### 2.8 Draft content pipeline sources

- **17Lands public datasets:** **CC BY 4.0**. Commercial use is allowed with attribution and a note
  of changes. Show "Data: 17Lands (CC BY 4.0)" wherever tier data appears.
- **MTGJSON:** **MIT**. Commercial use is allowed. Attribution is appreciated.
- **Expert articles used as research input:** copyrighted. Facts and ratings can inform original
  content, but article text must never be copied. Keep draft guides free.

### 2.9 YouTube and news feeds

- Videos are listed from public channel RSS and played in the **embedded YouTube player**. YouTube API
  policies prohibit selling ads or sponsorships **on, around or within the player**, or monetizing
  primarily-YouTube screens. → **No ads or sponsor slots on News/video screens.**
- RSS articles open in Custom Tabs (headline + link). Fine. Never republish full text next to monetized
  placements.

### 2.10 Google Play

- **Digital goods and features** sold in-app (subscriptions, cosmetics, tips to the developer) **must
  use Google Play Billing**. Service fee: 15 % on subscriptions and on the first $1 M/yr of one-time
  purchases. The US allows alternative billing and external links, with fees from 2026-10-01.
- **Physical goods** (cards, sealed product, accessories) must **not** use Play Billing, so affiliate
  links to retailers are the normal model.
- **Donations:** only tax-exempt charity donations are exempt from Play Billing. A "support the
  developer" tip is a digital purchase and goes through Play Billing. Patreon/Ko-fi can be promoted
  **outside** the app (website, README, social), not linked as an in-app payment path.
- **EU Digital Services Act:** a developer who monetizes must declare **trader** status, and Google
  then shows the trader's **address, email and phone** on the listing. Plan a business address before
  turning on any revenue.
- **Ads** (if ever): Play ads policy, GDPR consent via Google's UMP SDK in the EEA/UK, Data safety
  update ("advertising ID", "shared for advertising"). This also contradicts `PRIVACY_POLICY.md` ("we
  do not … use it for advertising") and the "no ads" listing claims.

### 2.11 Other components (no monetization blockers)

ML Kit (free, on-device), Vosk (Apache-2.0 models), Firebase, Supabase, Cloudflare. Their only role is
**cost**: revenue should first cover the Supabase plan, Worker/R2 usage and the Play developer account.

### 2.12 The golden rules (summary)

1. **Card data is always free** (Wizards FCP + Scryfall).
2. **Earn from commerce, services, originals and goodwill**, not from access.
3. **Keep Scryfall's affiliate links intact.** Add ManaHub's own only where Scryfall has none, or with
   Scryfall's consent.
4. **No ads near YouTube content.** Ideally no ads at all while "no ads" is a positioning pillar.
5. **Get permission from Archidekt and EDHREC, or isolate them from paid surfaces.**
6. **Credit every source visibly** (Scryfall, Commander Spellbook, 17Lands, TopDeck.gg, MTGJSON,
   Archidekt, EDHREC).
7. **Every in-app digital purchase goes through Play Billing.** Physical goods go through affiliate
   links.

---

## 3. How competitors monetize

| App | Model | What is paid | Lesson for ManaHub |
|-----|-------|--------------|--------------------|
| ManaBox | Freemium subscription (Pro $2.49/mo, $22.99/yr) | Cloud sync + Pro features | Users accept ~$2–3/mo; **do not** paywall sync (ManaHub already gives it free) |
| Dragon Shield | Freemium ($2.99/mo, $29.99/yr) + a sleeves brand | Scan/track caps, weekly value emails, price tracking | Capping card tracking is exactly what the FCP and Scryfall rules forbid for ManaHub |
| TopDecked | Tiered subscriptions (Spark/Powered/Elite) | Collection size (375 → 6,000), price trends, movers & shakers, unlimited deck simulation, tags/archiving | Rich perk menu, but built on caps. Copy the *ideas*, not the gating |
| Delver Lens | Free core + marketplace integrations | Buy/sell with Card Kingdom and TCGplayer | **Commerce integration is the scanner app's business model** |
| Moxfield | Patreon ($1+/mo) | Supporter perks, core free | Community-funded model works at scale |
| Archidekt | Ads + Patreon ($2/mo removes ads) | Ad-free, perks | Ads + "pay to remove ads" is the classic FCP-compatible model |
| EDHREC | Ads + Patreon + affiliate | Ad-free, perks | Content + affiliate + supporters |
| Lotus (life counter) | Free, no ads, no IAP | — | Pure goodwill product |
| TopDeck.gg / Spicerack | B2B subscriptions for organizers and stores | Event management | **Organizer tooling is a real paid market** |

---

## 4. Options with today's features

### 4.1 Affiliate commerce (recommended first)

**Why:** it monetizes physical goods (outside Play Billing and outside the Wizards IP question),
matches the user's intent at the moment of need, and costs the user nothing extra.

| Placement (existing feature) | Trigger | Retailers |
|------------------------------|---------|-----------|
| **Deck Wizard gap report**: "your deck is missing 7 cards (~€12)" | After generation; also in Deck Studio for any deck with unowned cards | Card Kingdom (partner/affiliate links; builder/cart URLs exist — see the Archidekt precedent), TCGplayer (Impact program ~3.5 %, first-click, 48 h window; supports mass entry), Cardmarket (**partner apps & services** program via API partnership — its private referral scheme is capped at €10/month and useless for an app) |
| **Wishlist**: "buy all / buy this" | Wishlist screen and price-drop moments | Same |
| **Card detail**: purchase links | Already present via Scryfall `purchase_uris` (keep Scryfall's tags). Add **Card Kingdom** as an extra link | Card Kingdom |
| **Community / "decks like yours"**: "buy what you're missing" | Deck detail | Only after the Archidekt question (§2.3) is settled |
| **Accessories**: sleeves, deck boxes, playmats, dice | Contextual: after creating a deck, in the life counter settings | Amazon Associates (per country), retailer programs |
| **MTGO/Arena** digital cards | Only if MTGO support is ever added | Cardhoarder affiliate program |

**Implementation notes:** region-aware retailer choice (EUR users → Cardmarket/Card Kingdom EU
shipping; USD → TCGplayer/Card Kingdom). A clear **"affiliate link" disclosure** (FTC, EU consumer
law), shown once per surface. Links built server-side (a Cloudflare Worker) so affiliate IDs can
rotate without an app update. Telemetry `affiliate_link_opened_<surface>_<retailer>` (no PII) reviewed
by `crashlytics-ux-auditor`.

### 4.2 Supporter tips and a "ManaHub Supporter" subscription (recommended second)

- **Tip jar** (Play Billing consumables, e.g. €1.99 / €4.99 / €9.99) on an "About / Support ManaHub"
  screen. Thanks are cosmetic only.
- **Supporter subscription** (e.g. €2.49/mo or €19.99/yr, in line with ManaBox and Dragon Shield) whose
  perks **never gate card content**:
  - Supporter badge and profile flair (visible to friends and in trades).
  - **Cosmetic packs**: new procedural themes and effects beyond the 12 free ones (per ADR-002 the 12
    stay free forever), life-counter backgrounds and animations made from **original, non-Wizards
    art**, alternative app icons.
  - Feature voting and a supporters' roadmap channel.
  - Early access to beta features through the Play testing track (open to supporters, and free to
    anyone who asks, so it does not gate content).
- **Patreon/Ko-fi** on the website and README only (not in-app, per Play's payments policy).
- **Interaction with gamification (ADR-002):** its cosmetics are designed to be **earned**. Decide
  explicitly whether paid cosmetics are a *separate* catalog (recommended) or also purchasable. Never
  sell XP, levels or achievements.

### 4.3 Sponsorships

- **Accessory brands** (sleeves, playmats, deck boxes): a sponsored **cosmetic theme or life-counter
  playmat skin**, or a "presented by" slot on the tournament screen. Allowed by the FCP. Clearly
  labelled.
- **Local game stores:** paid listing in a future "stores near you / events" surface, or a sponsored
  standings share template for the store's events. This connects to B2B (§5.8).
- **Content creators:** revenue share on creator-branded themes (their brand, original art).
- Keep sponsorships off News/YouTube screens (§2.9) and away from card images (§2.2).

### 4.4 Ads — evaluated, not recommended now

- Allowed by the FCP. But: (1) "no ads" is a **positioning and conversion pillar** in the ASO plan,
  and the privacy policy says data isn't used for advertising; (2) low yield at small scale (§7);
  (3) GDPR consent flow, Data safety changes, SDK weight; (4) excluded from News/video screens anyway.
- **If ever needed:** a single non-intrusive native unit on low-intent screens, plus a paid
  "remove ads" option (the Archidekt/EDHREC model). Update the privacy policy, Data safety and listing
  copy on the same day.

### 4.5 Features that must remain free (explicitly)

Card search, card detail, prices and collection value, scanner, collection tracker, import/export,
Deck Wizard, deck analysis, playtest, life counter, stats, friends, trades, local tournaments, news,
competitive hub, cloud sync, the 12 themes. **Removing or capping any of these to create a paid tier
would breach the rules above and trigger a review backlash** (the free sync is a differentiator
against ManaBox Pro).

---

## 5. New features (seen at competitors) and how they could earn

| # | Feature | Who has it | Monetization path | Compliance | Priority |
|---|---------|-----------|-------------------|------------|----------|
| 5.1 | **"Buy missing cards" cart**: one tap from a gap report or wishlist to a filled cart | Archidekt, Moxfield, Delver Lens (buy/sell) | Affiliate | ✅ | **P1** |
| 5.2 | **Price alerts + collection value history** (daily snapshots, "movers & shakers") | TopDecked (paid), Dragon Shield (paid) | Free feature that drives **affiliate** clicks (alert → buy/sell link). Sponsorship slot on the weekly digest | ⚠️ built from Scryfall prices → **must be free**. A paid version would need licensed price data (TCGplayer/Cardmarket partner APIs) | P1 |
| 5.3 | **Sell / buylist prices + "sell to store"** | Delver Lens (Card Kingdom, TCGplayer) | Affiliate / partner revenue on buylist submissions | ✅ (retailer partnership needed) | P2 |
| 5.4 | **Trade evaluator** (fair-value check for a proposed trade) | TopDecked, ManaBox | Free. Increases trade usage → social stickiness | ✅ free | P2 |
| 5.5 | **Weekly collection value email/push digest** | Dragon Shield (paid) | Free + affiliate links + a sponsor line | ⚠️ free (Scryfall prices) | P2 |
| 5.6 | **Binders/folders/locations** (physical storage mapping) | ManaBox, Dragon Shield | Free (core collection feature) | ✅ free | P2 |
| 5.7 | **AI deck coach**: "explain my deck", "upgrade under €20" (LLM) | ManaForge, Sensei's EDH Brewer, TopDecked recommendations | Free monthly quota + paid credits or supporter perk, justified by **compute cost**. The "under €20" upgrades end in **affiliate** carts | ⚠️ grey: gates a service, not the data. Keep a meaningful free quota and ask Wizards (W4) before charging | P3 |
| 5.8 | **Organizer / store tools**: event pages, player self-registration via QR, big-screen pairings and timer (web), standings share, store branding, season leaderboards | TopDeck.gg, Spicerack, Mythic Tools events, Wizards EventLink (official, sanctioned events) | **B2B subscription for stores** (monthly per store), sponsorship of store events | ✅ mostly: tournament operations are not Wizards IP. Stay out of sanctioned-event territory (EventLink) and position for casual/Commander nights | **P2 bet** |
| 5.9 | **Web app** (KMP wasm, already planned) | TopDecked, Moxfield, Archidekt | Enables Stripe for supporters/B2B without Play's fee (web purchases are outside Play), and big-screen tournament mode | Same FCP/Scryfall rules | P2 (roadmap) |
| 5.10 | **Printable/shareable decklists and standings** (images/PDF, branding outside card images) | Various | Growth loop + sponsor footer | ✅ (§2.2 S3) | P2 |
| 5.11 | **Unlimited playtest analytics** (thousands of simulated hands, mulligan and curve stats) | TopDecked (paid) | Supporter perk *only if* the basic playtest stays free and the perk is compute-heavy | ⚠️ grey | P3 |
| 5.12 | **Proxy printing** | Some web tools | — | ❌ **Do not build.** Reproducing card images for play is outside every policy above | — |
| 5.13 | **Peer-to-peer marketplace** between friends (payments) | — | Transaction fees | ⚠️ heavy regulatory load (payments, consumer law, fraud). Out of scope | — |

---

## 6. Recommended model: "Free core, fair supporters, commerce on intent"

**Phase 0 — Compliance and groundwork (before any revenue)**
- Add a **Data sources & credits** screen (Settings → About): Scryfall, Commander Spellbook (with
  link), 17Lands (CC BY 4.0), MTGJSON, Archidekt, EDHREC, TopDeck.gg (when the Worker ships), plus the
  Wizards FCP disclaimer.
- Written permission requests: **Archidekt** (partnership pitch: ManaHub can send deck traffic and
  Card Kingdom referrals), **EDHREC**, **deckstats**, and **Scryfall** (on adding ManaHub affiliate
  links next to theirs).
- Google Play: payments profile, **DSA trader declaration** (with a business address), tax setup (in
  Spain: consult a *gestor* about self-employment/activity registration once income is regular).
- Update `PRIVACY_POLICY.md` and Data safety for purchases and affiliate click tracking.

**Phase 1 — Affiliate commerce (weeks 1–6 after Phase 0)**
- 5.1 "Buy missing cards" from the Deck Wizard gap report + wishlist, region-aware. Card Kingdom link
  in card detail. Disclosure copy. Links served by a Worker.
- KPI: affiliate click-through per gap report, orders per 1,000 MAU.

**Phase 2 — Supporters (weeks 6–12)**
- Tip jar + Supporter subscription (Play Billing via a `commonMain` `EntitlementRepository`
  interface with a Play implementation in `androidMain`, per the KMP directive). First cosmetic pack
  and supporter badge. Patreon/Ko-fi on the website.
- KPI: supporters as % of MAU (target 1–3 %), churn.

**Phase 3 — Bigger bets (quarter 2+)**
- 5.2/5.5 price alerts and digest (free, affiliate-driven).
- 5.8 organizer/store tools prototype with 3–5 stores (ties into the ASO plan's LGS channel), then a
  B2B subscription, with web big-screen mode once the web target exists.
- 5.7 AI coach with a free quota, after the Wizards check (W4).
- Sponsorship pilots (one accessory brand theme, one store sponsorship).

**Explicitly rejected:** paid app, paywalled or capped card features, ads while "no ads" is a
positioning pillar, ads or sponsors near YouTube content, proxies, P2P payments.

---

## 7. Illustrative revenue model (per 1,000 monthly active users, per month)

Assumptions are deliberately conservative and **must be replaced with measured values**.

| Stream | Assumption | € / 1k MAU / month |
|--------|-----------|--------------------|
| Supporter subscription | 2 % of MAU × €2.49 × 85 % net (15 % Play fee) | ~€42 |
| Tips | 0.3 % of MAU × €4 × 85 % | ~€10 |
| Affiliate | 3 % of MAU click a buy link → 20 % order → €25 basket × 4 % commission | ~€6 |
| **Recommended mix** | | **~€58** (range ~€40–70) |
| Ads (for comparison) | 20 % DAU/MAU × 3 impressions/day × €1.5 eCPM | ~€27, at the cost of the "no ads" pillar |
| B2B stores | €15–30 per store per month, independent of MAU | 10 stores ≈ €150–300 |

At 10,000 MAU the recommended mix gives roughly €400–700/month. At 50,000 MAU, €2,000–3,500/month.
First target: cover infrastructure costs.

---

## 8. Implementation and compliance checklist

| # | Item | Owner |
|---|------|-------|
| C1 | Data sources & credits screen (all sources in §2) | `android-kotlin-architect` |
| C2 | Permission emails: Archidekt, EDHREC, deckstats, Scryfall (affiliate), optional Wizards (W4) | Owner |
| C3 | DSA trader declaration, payments profile, business address, tax advice | Owner |
| C4 | Privacy policy + Data safety: purchases, affiliate click tracking | Owner |
| C5 | `EntitlementRepository` interface (`commonMain`) + Play Billing implementation (`androidMain`); server-side purchase verification (Supabase Edge Function) | `android-kotlin-architect` + `backend-supabase-expert` |
| C6 | Affiliate link Worker (region-aware retailer, rotating IDs, disclosure flag) | Owner (Worker JS) |
| C7 | Telemetry for purchase funnel and affiliate clicks (no PII) | `crashlytics-ux-auditor` → architect |
| C8 | Store listing: in-app purchases label appears automatically. Keep "no ads" accurate; describe the Supporter perks honestly | Owner |
| C9 | Supabase security invariants (CLAUDE.md) for any entitlement tables/RPCs | `backend-supabase-expert` |
| C10 | Re-check `FeatureFlags.kt`: no paid perk may depend on a hidden feature (e.g. gamification cosmetics while `Gamification.ENABLED = false`) | Owner |

---

## 9. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Wizards objects to monetization of a Fan Content app | Low–medium | High | Rules W1–W4; FCP-sanctioned streams first; written inquiry |
| Scryfall blocks API access for paywalling or image misuse | Low if rules followed | Critical (the app depends on it) | Rules S1–S4; everything card-related free |
| Archidekt/EDHREC object to commercial use of their data | Medium once monetized | Medium (features lost) | Ask first; isolate from paid surfaces; fallback sources |
| Backlash for "enshittification" (users joined for free/no ads) | Medium | Medium | Never remove free features; supporter framing; transparency post |
| Low conversion makes the effort not worth it | High at small scale | Low | Sequence behind ASO growth; cheapest streams first (affiliate) |
| DSA trader disclosure exposes a personal address | Certain once monetized | Medium (privacy) | Business address / virtual office before enabling revenue |
| Affiliate program changes (rates, attribution) | Medium | Low | Server-side link building; several retailers |

---

## 10. Decisions needed from the owner

1. Accept the **"card data is always free"** principle as a written product rule (candidate ADR).
2. Approve **Phase 0** permission requests (Archidekt, EDHREC, deckstats, Scryfall; Wizards optional).
3. Approve **affiliate commerce** as the first stream, and the retailer list per region.
4. Decide whether **paid cosmetics** are a separate catalog from ADR-002's earned cosmetics.
5. Decide whether to explore **B2B store tools** (it changes the roadmap and competes with TopDeck.gg
   and Spicerack).
6. Confirm **no ads** for now (keeps the ASO positioning intact).

---

## Appendix A — Sources (retrieved 2026-09-24, via search extracts)

- Wizards of the Coast Fan Content Policy: https://company.wizards.com/en/legal/fancontentpolicy ·
  discussion: https://www.enworld.org/threads/the-other-snake-in-the-grass-the-wizards-fan-content-policy.694906/ ·
  older Fan Site Policy wording: https://ddowiki.com/page/Wizards_of_the_Coast_fansite_tool_kit ·
  https://www.greyhawkonline.com/greyhawkwiki/index.php?title=Fan_Site_Policy
- Scryfall API guidelines and terms: https://scryfall.com/docs/api · https://scryfall.com/docs/terms ·
  https://scryfall.com/docs/api/images · affiliate-link history: https://scryfall.com/blog/deprecation-notice-multiple-api-affiliate-links-201
- Archidekt terms: https://archidekt.com/terms · API status: https://archidekt.com/forum/thread/16962481 ·
  https://archidekt.com/forum/thread/2832338 · Card Kingdom partner precedent: https://www.cardkingdom.com/catalog/search?search=header&filter%5Bname%5D=Last+One+Standing&partner=archidekt&partner_args=single
- EDHREC JSON endpoints (unofficial): https://github.com/sigiltenebrae/edhrec_scraper/issues/1
- Commander Spellbook API guidelines & license: https://spacecowmedia.github.io/commander-spellbook-backend/api.html ·
  https://commanderspellbook.com/about/
- deckstats API status: https://deckstats.net/forum/index.php?topic=41323.0
- TopDeck.gg API and terms: https://topdeck.gg/docs/tournaments-v2 · https://topdeck.gg/terms-and-conditions ·
  Spicerack docs: https://docs.spicerack.gg/troubleshooting/faqs
- 17Lands public datasets (CC BY 4.0): https://www.17lands.com/public_datasets
- MTGJSON license (MIT): https://mtgjson.com/license/
- Google Play payments policy: https://support.google.com/googleplay/android-developer/answer/10281818 ·
  https://support.google.com/googleplay/android-developer/answer/9858738 · US changes:
  https://support.google.com/googleplay/android-developer/answer/15582165
- Affiliate programs: TCGplayer https://docs.tcgplayer.com/docs/tcgplayer-affiliate-program ·
  https://getlasso.co/affiliate/tcgplayer/ · Card Kingdom affiliates https://blog.cardkingdom.com/card-kingdom-affiliates-tolarian-community-college/ ·
  Cardmarket partner apps https://help.cardmarket.com/en/api-partnerships · Cardmarket referral limits
  https://refer.guide/r/cardmarket · Cardhoarder https://help.cardhoarder.com/en/articles/8684184-affiliate-program
- Competitor pricing: ManaBox https://manabox.app/guides/general/subscriptions/ · TopDecked
  https://www.topdecked.com/articles/powered-features/ · Dragon Shield / roundups
  https://grimdeck.com/blog/best-mtg-collection-tracker-scanner · https://manaforge.tools/en/blog/manaforge-vs-moxfield-vs-archidekt
