# ManaHub

The ultimate Magic: The Gathering companion app for Android.

ManaHub brings your collection, decks, games, and community into one place — a life counter, collection manager, deck builder with an intelligent suggestion engine (Deck Doctor), an OCR card scanner, a draft simulator/guide, news, analytics, tournaments, friends, trades, real-time online multiplayer, and a full gamification layer (XP, levels, achievements, quests, streaks, cosmetics). Built local-first: every core feature works offline and without an account.

## Highlights

- **Local-first.** Collection, decks, life counter, statistics, tournaments, gamification, and the OCR scanner all work fully offline with no account required.
- **Optional account (Supabase)** unlocks social and cloud features: friends, trades, online multiplayer, push notifications, and cross-device sync. Guests can join online games anonymously.
- **English-only.** The app's UI, card text, and news are English. (Card data comes from Scryfall; the OCR scanner can read foreign-printed cards and resolves them to their English entry.)
- **Privacy-respecting.** OCR runs on-device, no location is collected, no ads, no data selling.

## Features

### ⚔ Life Counter
Track life totals for 2 to 6 players simultaneously. Built for Commander and Standard, with full support for poison, experience, energy counters, and commander damage.

- Commander damage panel with automatic elimination at 21
- Poison (≥10) and life (≤0) auto-elimination with confirmation dialog
- Custom counters (name + icon) per player
- Dice roller (d20) and coin flip with animation
- Phase tracker with configurable phase stops per player; land-played tracker per turn
- Drag-and-drop layout editor — reorder and swap player positions mid-game
- Turn counter that increments on full-round completion; per-player colour themes
- Optional offline voice control (one language per session)
- Tournament-aware: links a game result to an open tournament match automatically

### 🌐 Online Multiplayer
Play synchronised games across devices in real time. HTTP polling is the primary sync mechanism, with Supabase Realtime as an optional fast-path.

- Host / join lobby with a shareable 6-digit code; deep-link join
- Guests can join anonymously (no account required)
- Live sync of life totals, counters, commander damage, phases, turns, and lands played
- Defeat confirmation / revocation flow; leave or abandon a session cleanly

### 📡 In-Person Multiplayer (Nearby)
Sync game state device-to-device with no server and no internet, using Google Nearby Connections (Bluetooth / Wi-Fi).

### 📷 Card Scanner
Identify cards by pointing the camera at the card name. Uses on-device ML Kit OCR — no image ever leaves the device.

- Auto-permission on first launch; OCR pause/resume toggle; tap-to-focus
- Auto-pause after add to prevent double-scanning; flash toggle
- Debounced two-stage lookup (full-text → exact-name fallback)
- Confirm sheet with foil, condition, language, and quantity

### 📁 Collection Manager
Search and manage your entire collection with real-time Scryfall data.

- Search by card name; filter by colour (W/U/B/R/G + Multicolour + Colourless) with official mana SVG icons
- Filter by rarity, mana value, format legality, price, oracle text, and trade status
- Grid and list views; card detail with double-faced support and art crop
- Edit quantity, condition, language, and foil per copy
- Wishlist mode (a card can live in both collection and wishlist)
- Batch price refresh via the Scryfall collection endpoint; stale-cache indicator

### 🏷 Automatic Card Tagging
Cards are tagged automatically as they enter your collection, via English oracle-text analysis.

- Built-in tags across Keyword, Strategy, Role, Archetype, and Tribal categories
- Confidence scores: auto-confirmed above threshold, suggested below
- User-overridable tag dictionary (edit labels, patterns, categories; add new tags)
- Tags feed the deck suggestion engine

### 🃏 Deck Builder + Deck Doctor
Build and upgrade decks with an engine that analyses roles, mana curve, colour identity, tribal synergy, and your collection.

- Seed strategies (Tokens, +1/+1 Counters, Ramp, Control, Combo, Graveyard, Burn, Tribal)
- Role classification, mana-base analysis (colour-source shortages, splash health), and format-aware construction validation
- Budget-aware suggestions that can pull from your collection, wishlist, or an external Scryfall pool
- Structured, human-readable reasons for every suggestion and warning
- Mainboard/sideboard tabs; mana-curve chart and basic-land auto-calculator
- Import / export in Moxfield / MTGO text format
- Format validation (Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Commander, Casual)
- **Deck Playtest:** goldfish a deck with mulligan/redraw and a drag-and-drop battlefield (fully ephemeral — nothing is saved unless you choose to)

### 🎲 Draft Simulator & Guide
Set-by-set draft simulation and guidance, with content served from a Cloudflare Worker backed by R2 storage.

- Simulate a draft with archetype-aware bots (data-driven per-set engine; supports 3+ colour sets)
- Configurable seat count (2–10); Sealed mode
- Per-set tier lists, strategy/archetype breakdowns, and curated videos
- Offline-capable with local asset caching

### 🎮 Gamification
A cross-cutting, local-first progression layer (works 100% offline; an account only adds cloud sync).

- XP and levels earned from real activity (idempotent XP ledger)
- ~40 achievements (including secret ones) with unlock celebrations
- Daily/weekly quests (deterministically generated) and activity streaks with freeze tokens
- 21 fully procedural cosmetics (titles, badges, avatar frames, level-ring styles) — no image assets
- All progression syncs to your account monotonically (never last-write-wins)

### 🏠 Home Dashboard
A customizable widget board (the app's start screen).

- Hub-based widgets (game stats, collection stats, social, trades), Discover cards, news, and more
- Layout persists locally; account-gated widgets prompt to create an account
- Time-of-day greeting and quick-start shortcuts

### 👥 Friends
Friend requests (incoming/outgoing), invite by link or QR, search by Game Tag, browse a friend's collection, head-to-head stats and match history.

### 🔄 Trades
Full negotiation (counter-offer, edit, accept, decline, cancel, revoke, mark complete), wishlist and open-for-trade lists (local + remote sync), shared lists via public link, server-generated suggestions, and migration of local lists on sign-in.

### 📰 MTG News
Aggregates articles and YouTube videos from configurable RSS / Atom sources, with separate tabs, in-app browsing via Chrome Custom Tabs, and per-source enable/disable.

### 📊 Statistics
Collection value (USD/EUR), mana-curve and colour charts, win rate, average life on win/loss, average game duration, average win turn, per-deck win rate, favourite game mode, most frequent elimination reason, current streak, and survey-derived insights. Win/loss is keyed on the local seat, not a name match.

### 👤 Profile
Player name and avatar, auto-detected play style, achievements and equipped cosmetics, collection insights, recent game history with W/L badges, best deck by win rate, and survey-derived mana/hand quality.

### 🏆 Tournaments
Round Robin, Swiss, and Single Elimination; configurable matches per pairing (best of 1/2/3); random or manual pairings; standings with a points system and OMW%/GW% tiebreakers; matches launch into the life counter and record results automatically; manual result entry supported.

### 📋 Post-Game Survey
An optional ~2-minute review after each game that feeds analytics: contextual questions, opening-hand quality, mana-health assessment, result-feel rating, and per-card impact.

### 🔔 Push Notifications
Optional, account-gated push (social/trade events) via a Supabase outbox → FCM pipeline. Payloads carry no PII; opt-out preferences supported.

### 🔐 Account & Sync
- Sign up / sign in with email + password or Google; anonymous guest sign-in for online play
- Auto-generated Game Tag as your unique identifier
- Link Google identity, reset password, change nickname, delete account
- Session stored encrypted on disk (Android Keystore AES-GCM + DataStore)
- Background cloud sync of collection, decks, stats, and gamification via WorkManager

### ⚙ Settings
News language filter, preferred currency (USD/EUR), auto-refresh prices toggle, voice-model management, and visual theme selection (12 MagicTheme palettes).

## Screenshots
Coming soon.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| Build | AGP / Gradle Kotlin DSL / KSP |
| UI | Jetpack Compose + Material 3 (12 custom MagicTheme palettes) |
| Architecture | MVVM + Clean Architecture (single `:app` module) |
| Dependency Injection | Hilt |
| Database | Room (exported schemas, DB v40) |
| Networking | Retrofit + OkHttp; kotlinx.serialization |
| Backend (BaaS) | Supabase (auth + postgrest + realtime + edge functions) via Ktor client |
| Image loading | Coil + SVG decoder |
| Camera / OCR | CameraX + ML Kit Text Recognition (on-device) |
| Async | Coroutines + Flow |
| Preferences | Jetpack DataStore |
| Navigation | Navigation Compose |
| Paging | Paging 3 + room-paging |
| Background work | WorkManager + Hilt Work |
| Google Sign-In | Credentials API + googleid |
| Telemetry | Firebase Analytics + Crashlytics |
| Push | Firebase Cloud Messaging (FCM HTTP v1) |
| Voice | Vosk (offline, on-device, grammar-restricted) |
| In-person multiplayer | Google Nearby Connections |
| Card data | Scryfall API |

SDK: `minSdk = 29` (Android 10) · `targetSdk = 35` · `compileSdk = 36`. JDK 17.

Release builds use R8 (minification + resource shrinking) with a custom `proguard-rules.pro`. Sensitive values (`YOUTUBE_API_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_CLIENT_ID`, `CLOUDFLARE_WORKER_URL`) are injected via `BuildConfig` from `local.properties` (git-ignored) or CI environment variables.

## Architecture

Clean Architecture by layer (data / domain / presentation). Root package: `com.mmg.manahub`. Single Gradle module (`:app`).

```
app/
├── app/
│   ├── ManaHubApp.kt        — @HiltAndroidApp; schedules workers, inits Crashlytics, runs the gamification engine
│   ├── MainActivity.kt      — singleTask, deep links, App Links, global celebration host
│   └── navigation/          — AppNavGraph.kt, Screen.kt (sealed routes); start destination = Home
├── core/
│   ├── auth/                — SecureSessionManager (Keystore AES-GCM + DataStore)
│   ├── data/{local,remote,repository}  — Room (MtgDatabase v40); Scryfall API; repo impls + cache policy
│   ├── di/                  — Hilt modules
│   ├── domain/              — shared models, repository interfaces, use cases
│   ├── gamification/        — XP/levels/achievements/quests/streaks/cosmetics engine
│   ├── nearby/              — Nearby Connections (P2P)
│   ├── network/             — OkHttp client + Scryfall request queue (≤10 req/s)
│   ├── online/              — online sessions (polling-first; Realtime fast-path)
│   ├── sync/                — WorkManager workers + SyncManager
│   ├── tagging/             — TagDictionary, analyzers, override repository
│   ├── ui/                  — shared composables + 12 MagicTheme palettes
│   └── util/                — utilities
└── feature/                 — one package per screen/flow (home, collection, decks, draft, game,
                               online, scanner, stats, survey, tournament, trades, playtest, …)
```

## Backend & External Services

- **Supabase** — auth (email/password + Google + anonymous), Postgres, Realtime, Edge Functions, and the push outbox. Backend SQL/migrations/RPCs/Edge Functions live under `supabase/`.
- **Cloudflare Worker `manahub-draft-api`** — serves Draft content from the R2 bucket `manahub-assets`. Routes: `/draft/sets-index.json`, `/draft/{setCode}/{guide,tier-list,booster,engine}.json`. ETag/304 caching, GET/HEAD only, open CORS. Worker source under `cloudflare/manahub-draft-api/`; content is generated offline by the Python pipeline in `scripts/draftsim_py/`.
- **Scryfall API** — all card data, prices, images, set info. Rate-limited (≤10 req/s) via a request queue; queries are allowlist-sanitised; data cached in Room.
- **YouTube Data API v3** — set/news videos. The key is injected via BuildConfig; if absent, the feature is gracefully disabled.
- **ML Kit (Google)** — on-device OCR. No camera frames or OCR results leave the device.
- **Firebase** — Analytics + Crashlytics (telemetry) and FCM (push delivery). `google-services.json` is git-ignored and never committed.

## Data & Privacy

ManaHub is local-first. Collection, decks, game history, surveys, tournaments, gamification state, and the Scryfall metadata cache (Room), plus name, avatar, currency, tag dictionary, and settings (DataStore) live only on your device.

If you create an account, only email, nickname, Game Tag, auth provider, and the data you choose to sync (collection/decks/stats/gamification, friends, trades) are stored on Supabase. Passwords are bcrypt-hashed. No location is collected. No ads. No data selling. See `PRIVACY_POLICY.md` for the full policy.

## Security

- HTTPS only via `network_security_config.xml` (`cleartextTrafficPermitted="false"`)
- `HttpLoggingInterceptor` is BODY level in debug only; NONE in release
- Room and DataStore excluded from Google Drive auto-backup (`backup_rules.xml`, `data_extraction_rules.xml`)
- Release builds use R8 minification + resource shrinking with a custom `proguard-rules.pro`
- YouTube API key injected via an OkHttp interceptor — not visible in Retrofit signatures or Logcat
- Scryfall queries sanitised with an allowlist before being appended to API URLs
- Camera frames never leave the device (on-device ML Kit)
- User session encrypted on disk (Android Keystore AES-GCM via `SecureSessionManager`)
- A mandatory pre-push secret-scan gate guards against leaking keys/tokens into the repo

## Getting Started

### Prerequisites
- Android Studio (latest stable)
- JDK 17 (bundled with Android Studio)
- Android SDK 36

### Clone and build
```bash
git clone https://github.com/Miguelmglez/ManaHub.git
cd ManaHub
./gradlew assembleDebug
```

### Configure `local.properties`
Required keys are marked with `*`:
```properties
SUPABASE_URL=...            # *
SUPABASE_ANON_KEY=...       # *
GOOGLE_CLIENT_ID=...        # *
YOUTUBE_API_KEY=...         # optional — Draft Guide videos disabled if absent
CLOUDFLARE_WORKER_URL=...   # optional — has a default

# Release signing (optional)
KEY_STORE_PATH=
KEY_STORE_PASSWORD=
KEY_ALIAS=
KEY_PASSWORD=
```

Open the project in Android Studio and run on a device or emulator running Android 10 (API 29) or higher. Camera features require a physical device.

## Testing

```bash
# Unit tests
./gradlew test

# Single class
./gradlew test --tests "com.mmg.manahub.feature.game.GameViewModelTest"

# Instrumented Room tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

Coverage spans repositories, use cases, and ViewModels across the app, plus instrumented Room tests. A key instrumented regression test ensures the card upsert (CASCADE) never deletes the user's cards. Test stack: JUnit 4, MockK, Turbine, kotlinx-coroutines-test, arch-core-testing, room-testing.

> Note: `./gradlew test --tests "<pattern>"` compiles the entire `src/test` source set first — a compile error in any test file fails the whole run.

## Roadmap

**Implemented:** life counter with full Commander support · collection manager with Scryfall integration and automatic tagging · OCR card scanner · deck builder with the Deck Doctor engine, playtest, and import/export · draft simulator & guide · MTG news aggregator · post-game survey and analytics · tournaments · player profile · gamification (XP/levels/achievements/quests/streaks/cosmetics) · customizable home dashboard · offline voice control · push notifications · accounts (Supabase + Google, anonymous guests) · friends · trades · online multiplayer · in-person multiplayer · cloud sync.

**Planned:** additional visual themes · tablet and foldable layout support · Play Store release.

## Contributing
This project is in active development. Issues and pull requests are welcome once v1 is stable.

## Legal
ManaHub is an unofficial fan project and is not affiliated with, endorsed by, or sponsored by Wizards of the Coast. Magic: The Gathering and all associated card names, imagery, and lore are intellectual property of Wizards of the Coast LLC. Card data and images are provided by Scryfall under their terms of service. Set symbols and mana symbols are copyright Wizards of the Coast and are used in accordance with their fan content policy.

## License
Licensed under the MIT License — see the `LICENSE` file for details.

Built with ☕ and too many Commander games.
