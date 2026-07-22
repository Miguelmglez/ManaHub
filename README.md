# ManaHub

The ultimate Magic: The Gathering companion app for Android.

ManaHub brings your collection, decks, and games into one place — a life counter, a collection manager, an OCR card scanner, a deck builder, automatic card tagging, news, analytics, friends, and trades. Built local-first: every core feature works offline and without an account.

## Highlights

- **Local-first.** Collection, decks, life counter, statistics, and the OCR scanner all work fully offline with no account required.
- **Optional account (Supabase)** unlocks social and cloud features: friends, trades, push notifications, and cross-device sync.
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
- Tags help you organise, search, and filter your collection

### 🃏 Deck Builder
Build and manage decks backed by your collection and real-time Scryfall data.

- Mainboard/sideboard tabs; mana-curve chart and basic-land auto-calculator
- Import / export in Moxfield / MTGO text format
- Format validation (Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Commander, Casual)
- Card detail with double-faced support; quick add/remove from search or your collection
- **Deck creation wizard**: guided multi-step builder (Commander & Casual) that matches your
  collection against community deck templates (EDHREC/Archidekt data), with staged generation
  progress, per-category fill report, and community picks grouped by role (Removal, Ramp, …)
  clearly separated from cards you own
- **Collection discoveries**: synergy clusters detected in your own collection (strategies and
  tribes), each with a one-tap "Build this" handoff into the wizard

### 👥 Friends
Friend requests (incoming/outgoing), invite by link or QR, search by Game Tag, browse a friend's collection, head-to-head stats and match history.

### 🔄 Trades
Full negotiation (counter-offer, edit, accept, decline, cancel, revoke, mark complete), wishlist and open-for-trade lists (local + remote sync), shared lists via public link, server-generated suggestions, and migration of local lists on sign-in.

### 📰 MTG News
Aggregates articles and YouTube videos from configurable RSS / Atom sources, with separate tabs, in-app browsing via Chrome Custom Tabs, and per-source enable/disable.

### 📊 Statistics
Collection value (USD/EUR), mana-curve and colour charts, win rate, average life on win/loss, average game duration, average win turn, per-deck win rate, favourite game mode, most frequent elimination reason, and current streak. Win/loss is keyed on the local seat, not a name match.

### 👤 Profile
Player name and avatar, auto-detected play style, collection insights, recent game history with W/L badges, and best deck by win rate.

### 🏠 Home Dashboard
A customizable widget board (the app's start screen).

- Hub-based widgets (game stats, collection stats, social, trades), Discover cards, news, and more
- Layout persists locally; account-gated widgets prompt to create an account
- Time-of-day greeting and quick-start shortcuts

### 🔔 Push Notifications
Optional, account-gated push (social/trade events) via a Supabase outbox → FCM pipeline. Payloads carry no PII; opt-out preferences supported.

### 🔐 Account & Sync
- Sign up / sign in with email + password or Google
- Auto-generated Game Tag as your unique identifier
- Link Google identity, reset password, change nickname, delete account
- Session stored encrypted on disk (Android Keystore AES-GCM + DataStore)
- Background cloud sync of collection, decks, and stats via WorkManager

### ⚙ Settings
News language filter, preferred currency (USD/EUR), auto-refresh prices toggle, voice-model management, and visual theme selection (12 MagicTheme palettes).

## Screenshots
Coming soon.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin (Multiplatform — Android now, Web/wasmJs next) |
| Build | AGP / Gradle Kotlin DSL / KSP |
| UI | Compose Multiplatform + Material 3 (12 custom MagicTheme palettes) |
| Architecture | MVVM + Clean Architecture, KMP-oriented (`:shared:core-*` commonMain + `:app` Android) |
| Dependency Injection | Koin (new/migrated code) + Hilt (legacy, being phased out) |
| Database | Room (exported schemas, DB v40) — Android-only (no wasm target); repo interfaces are shared |
| Networking | Ktor (commonMain, js/wasm-ready) + kotlinx.serialization; a small Retrofit remnant remains only for `DraftModule` (Cloudflare/YouTube manual JSON) |
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
| Card data | Scryfall API |

SDK: `minSdk = 29` (Android 10) · `targetSdk = 36` (Android 16) · `compileSdk = 37`. JDK 17.

Release builds use R8 (minification + resource shrinking) with a custom `proguard-rules.pro`. Sensitive values (`YOUTUBE_API_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_CLIENT_ID`, `CLOUDFLARE_WORKER_URL`, `COMMUNITY_WORKER_URL`) are injected via `BuildConfig` from `local.properties` (git-ignored) or CI environment variables.

## Architecture

Clean Architecture by layer (data / domain / presentation), **migrating to Kotlin Multiplatform**
(Android + Web). Root package: `com.mmg.manahub`. Sequencing: get the Android app fully working on
KMP first, then build the Web target incrementally — `:webApp` doesn't exist yet.

```
:shared:core-model    — commonMain — pure domain models (no platform deps)
:shared:core-common   — commonMain — DispatcherProvider, KeyValueStore, CrashReporter, Page
:shared:core-domain   — commonMain — repository interfaces, use cases, gamification catalogs, deck/tournament engines
:shared:core-data     — commonMain — Ktor clients, DTOs, rate-limit queues, some repo impls, tagging support
:shared:core-ui       — commonMain — MagicTheme design system, 55+ shared composables, CMP composeResources
:app                  — androidMain (+ legacy single-module layout below) — Room, Hilt (legacy), Android-only UI
:baseline-profile     — Android baseline profile generation
```

`:app`'s internal layout (data / domain / presentation per feature) predates the KMP split and still
holds everything not yet migrated:

```
app/
├── app/
│   ├── ManaHubApp.kt        — @HiltAndroidApp; schedules workers, inits Crashlytics
│   ├── MainActivity.kt      — singleTask, deep links, App Links
│   └── navigation/          — AppNavGraph.kt, Screen.kt (sealed routes); start destination = Home
├── core/
│   ├── auth/                — SecureSessionManager (Keystore AES-GCM + DataStore)
│   ├── data/{local,remote,repository}  — Room (MtgDatabase v40); repo impls mapping to shared domain types
│   ├── di/                  — Hilt modules (legacy) + Koin bridge modules (migrated features)
│   ├── network/             — OkHttp client + Scryfall request queue (≤10 req/s)
│   ├── sync/                — WorkManager workers + SyncManager
│   ├── tagging/             — TagDictionary (Android-only: Locale/DataStore), analyzers (shared), override repository
│   ├── ui/                  — remaining Android-only composables (hard-blocked, e.g. Activity-bound sheets)
│   └── util/                — utilities
└── feature/                 — one package per screen/flow (home, collection, decks, game,
                               scanner, stats, trades, …); `feature/online`, `core/voice`, and
                               `feature/scanner` are explicitly excluded from the KMP migration for now
```

**Room has no wasm target** — DAOs/entities/migrations stay `androidMain`-only; every repository
interface lives in `shared/core-domain` as a pure Kotlin contract, with the Android impl mapping
DAO/entity types to shared domain models at the repository boundary. Web will get its own
Supabase-backed implementation of the same interfaces when that phase starts.

> Note: the repository also contains feature modules that are still in active development and not yet enabled in the shipping build (see Roadmap).

## Backend & External Services

- **Supabase** — auth (email/password + Google), Postgres, Realtime, Edge Functions, and the push outbox. Backend SQL/migrations/RPCs/Edge Functions live under `supabase/`.
- **Scryfall API** — all card data, prices, images, set info. Rate-limited (≤10 req/s) via a request queue; queries are allowlist-sanitised; data cached in Room.
- **YouTube Data API v3** — news videos. The key is injected via BuildConfig; if absent, the feature is gracefully disabled.
- **ML Kit (Google)** — on-device OCR. No camera frames or OCR results leave the device.
- **Firebase** — Analytics + Crashlytics (telemetry) and FCM (push delivery). `google-services.json` is git-ignored and never committed.

## Data & Privacy

ManaHub is local-first. Collection, decks, game history, and the Scryfall metadata cache (Room), plus name, avatar, currency, tag dictionary, and settings (DataStore) live only on your device.

If you create an account, only email, nickname, Game Tag, auth provider, and the data you choose to sync (collection/decks/stats, friends, trades) are stored on Supabase. Passwords are bcrypt-hashed. No location is collected. No ads. No data selling. See `PRIVACY_POLICY.md` for the full policy.

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
YOUTUBE_API_KEY=...         # optional — News videos disabled if absent
CLOUDFLARE_WORKER_URL=...   # optional — has a default
COMMUNITY_WORKER_URL=...    # optional — has a placeholder default, `manahub-community` Worker not yet deployed

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

**Available now:** life counter with full Commander support · collection manager with Scryfall integration and automatic tagging · OCR card scanner · deck builder with import/export and format validation · MTG news aggregator · statistics · player profile · customizable home dashboard · offline voice control · push notifications · accounts (Supabase + Google) · friends · trades · cloud sync.

**In development (not yet enabled in the shipping build):** intelligent deck suggestion engine (Deck Doctor) · deck playtest (goldfish + battlefield) · draft simulator & guide · tournaments (Swiss / Round Robin / Single Elimination) · real-time online multiplayer · in-person (Nearby) multiplayer · gamification (XP, levels, achievements, quests, streaks, cosmetics) · post-game survey & deeper analytics.

**Planned:** additional visual themes · tablet and foldable layout support · Play Store release.

## Contributing
This project is in active development. Issues and pull requests are welcome once v1 is stable.

## Legal
ManaHub is an unofficial fan project and is not affiliated with, endorsed by, or sponsored by Wizards of the Coast. Magic: The Gathering and all associated card names, imagery, and lore are intellectual property of Wizards of the Coast LLC. Card data and images are provided by Scryfall under their terms of service. Set symbols and mana symbols are copyright Wizards of the Coast and are used in accordance with their fan content policy.

## License
Licensed under the MIT License — see the `LICENSE` file for details.

Built with ☕ and too many Commander games.
