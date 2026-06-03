ManaHub
The ultimate Magic: The Gathering companion app for Android.
ManaHub brings your collection, decks, games, and community into one place — a life counter, collection manager, deck builder with a synergy engine, OCR card scanner, draft guide, news, analytics, tournament organiser, friends, trades, and real-time online multiplayer. Built local-first: every core feature works offline and without an account.

Highlights

Local-first. Collection, decks, life counter, statistics, tournaments and the OCR scanner all work fully offline with no account required.
Optional account (Supabase) unlocks social and cloud features: friends, trades, online multiplayer and cross-device sync.
Multilingual throughout: English, Spanish, and German (app UI, card text, and news).
Privacy-respecting. OCR runs on-device, no location is collected, no ads, no data selling.


Features
⚔ Life Counter
Track life totals for 2 to 6 players simultaneously. Built for Commander and Standard, with full support for poison, experience, energy counters, and commander damage.

Commander damage panel with automatic elimination at 21
Poison (≥10) and life (≤0) auto-elimination with confirmation dialog
Custom counters (name + icon) per player
Dice roller (d20) and coin flip with animation
Phase tracker with configurable phase stops per player
Land-played tracker per turn
Drag-and-drop layout editor — reorder and swap player positions mid-game
Turn counter that increments on full-round completion
Per-player colour themes
Tournament-aware: links a game result to an open tournament match automatically

🌐 Online Multiplayer
Play synchronised games across devices in real time, powered by Supabase Realtime.

Host / join lobby with a shareable code
Deep-link join (open an invite link straight into the lobby)
Live sync of life totals, counters, commander damage, phases, turns, and lands played
Defeat confirmation / revocation flow
Leave or abandon a session cleanly

📡 In-Person Multiplayer (Nearby)
Sync game state device-to-device with no server and no internet, using Google Nearby Connections (Bluetooth / Wi-Fi).
📷 Card Scanner
Identify cards by pointing the camera at the card name. Uses on-device ML Kit OCR — no image ever leaves the device.

Auto-permission: the system dialog is presented immediately on first launch
OCR toggle: pause and resume recognition with one tap to reposition freely
Tap-to-focus: tap the preview to focus and toggle OCR simultaneously
Auto-pause after add: prevents double-scanning the same card
Flash toggle (state preserved across the confirm sheet)
Debounced search (200 ms) with a two-stage lookup: full-text → exact-name fallback
Confirm sheet with foil, condition, language, and quantity

📁 Collection Manager
Search and manage your entire collection with real-time Scryfall data.

Search by card name in English, Spanish, and German
Filter by colour (W/U/B/R/G + Multicolour + Colourless) with official mana SVG icons
Filter by rarity, mana value, format legality, price, oracle text, and trade status
Grid and list views
Card detail with double-faced card support and art crop
Edit quantity, condition, language, foil, and alternative-art flag per copy
Wishlist mode: a card can live in both your collection and your wishlist
Batch price refresh via the Scryfall collection endpoint
Stale-cache indicator when Scryfall data could not be refreshed

🏷 Automatic Card Tagging
Cards are tagged automatically as they enter your collection, via oracle-text analysis.

30+ built-in tags across Keyword, Strategy, Role, Archetype, and Tribal categories
Confidence scores: auto-confirmed above threshold, suggested below
User-overridable tag dictionary: edit labels, patterns, and categories
Add brand-new tags with custom labels and detection patterns
Configurable auto-confirm and suggest thresholds
Tags feed the deck builder's synergy scoring engine

🃏 Deck Builder
Build decks with an intelligent suggestion engine that analyses synergies between your cards.

Three-step flow: Setup → Suggestions → Review
Seed strategies (Tokens, +1/+1 Counters, Ramp, Control, Combo, Graveyard, Burn, Tribal)
Suggestions scored by tag overlap, mana-curve fit, colour identity, and collection ownership
Mainboard and sideboard tabs
Sideboard recommendations based on post-game survey patterns
Mana-curve chart and basic-land auto-calculator
Import / export in Moxfield / MTGO text format
Format validation (Standard, Pioneer, Modern, Commander, Casual)
Deck Improvement screen for upgrading an existing list
Standalone Synergy Explorer to inspect card interactions

👥 Friends

Send and receive friend requests (incoming / outgoing)
Invite by shareable link or QR
Search players by Game Tag
Browse a friend's collection
Head-to-head stats and match history per friend

🔄 Trades

Trade proposals with full negotiation: counter-offer, edit, accept, decline, cancel, revoke acceptance, mark completed
Wishlist and open-for-trade lists (local, with remote sync)
Shared lists published via a public link
Server-generated trade suggestions
Migration of local lists to your account when you sign in

📰 MTG News

Aggregates articles and videos from configurable RSS / Atom sources
Separate tabs for articles and YouTube videos
Filter by language (EN/ES)
Articles open in Chrome Custom Tabs
Add, enable, or disable individual sources

📺 Draft Guide
Set-by-set draft guidance with tier lists, strategy breakdowns, and curated videos.

Browse draftable sets with release dates and card counts
Per-set tier lists with card-by-card ratings
Strategy guide with archetype breakdowns and key cards
Integrated YouTube video content
Offline-capable with local asset caching
Content served from a Cloudflare Worker backed by R2 storage

📊 Statistics

Collection value in USD or EUR with automatic Scryfall price refresh
Mana-curve and colour-distribution charts
Win rate, average life on win/loss, average game duration, average win turn
Per-deck win rate
Favourite game mode and most frequent elimination reason
Current win streak
Survey-derived insights: mana health, opening-hand ratings, favourite win style

👤 Profile

Player name and avatar
Play style auto-detected from history (Aggro, Midrange, Control, Balanced)
15 unlockable achievements
Collection insights (favourite colour, most valuable colour identity)
Recent game history with W/L badges
Best deck highlighted by win rate
Mana-issue rate and hand quality from survey data
Send Feedback — compose a message with an optional image, sent to the developer via your device's email app. No data is transmitted by ManaHub itself.

🏆 Tournaments

Round Robin, Swiss, and Single Elimination formats
Configurable matches per pairing (best of 1/2/3)
Random or manual pairings
Standings with a points system (Win = 3 pts) and life-total tiebreaker
Matches launch directly into the life counter, recording results automatically
Manual result entry supported

📋 Post-Game Survey
An optional ~2-minute review after each game that feeds your analytics.

Contextual questions based on what happened (commander damage, sideboard, loss reason)
Opening-hand quality rating
Mana-health assessment (smooth, flooded, screwed, inconsistent)
Result-feel rating (dominant, close, lucky, skillful)
Per-card impact assessment
Insights surfaced in the Profile screen and used to refine deck suggestions

🔐 Account & Sync

Sign up / sign in with email + password or Google
Auto-generated Game Tag (e.g. #A3KX9Z) as your unique identifier
Link a Google identity, reset password, change nickname, delete account
Encrypted session stored on disk
Background cloud sync of collection, decks, and stats via WorkManager

⚙ Settings

App language (English, Spanish, German)
Card-text language preference
News language filter
Preferred currency (USD / EUR)
Auto-refresh prices on launch toggle
Visual theme (Neon Void, Medieval Grimoire, Arcane Cosmos)


Screenshots
Coming soon

Tech Stack
LayerTechnologyVersionLanguageKotlin2.3.20BuildAGP / Gradle Kotlin DSL / KSPAGP 9.1.0 · KSP 2.3.6UIJetpack Compose (BOM) + Material 3BOM 2026.03.01 · M3 1.4.0ArchitectureMVVM + Clean Architecture (feature modules)—Dependency InjectionHilt2.59.2DatabaseRoom (exported schemas, DB v35)2.8.4NetworkingRetrofit + OkHttp3.0.0 / 5.3.2Backend HTTP clientKtor (used by Supabase)3.1.3Image loadingCoil + SVG decoder2.7.0CameraCameraX1.6.0OCRML Kit Text Recognition (on-device, + JP/KR)16.0.1AsyncCoroutines + Flow1.10.2PreferencesJetpack DataStore1.2.1NavigationNavigation Compose2.9.7PagingPaging 3 + room-paging3.3.6Background workWorkManager + Hilt Work2.10.1Backend (BaaS)Supabase (auth + postgrest + realtime)BOM 3.1.4Google Sign-InCredentials API + googleid1.5.0 / 1.1.1TelemetryFirebase Analytics + CrashlyticsBOM 34.12.0Securityandroidx.security-crypto (encrypted session)1.1.0-alpha07VideoYouTube Player + YouTube Data API v313.0.0In-person multiplayerGoogle Nearby Connections19.3.0Card dataScryfall API—
SDK: minSdk = 29 (Android 10) · targetSdk = 35 (Android 15) · compileSdk = 36. JDK 17.
Release builds use R8 (minification + resource shrinking) with a custom proguard-rules.pro. Sensitive values (YOUTUBE_API_KEY, SUPABASE_URL, SUPABASE_ANON_KEY, GOOGLE_CLIENT_ID, CLOUDFLARE_WORKER_URL) are injected via BuildConfig from local.properties (git-ignored) or CI environment variables.

Architecture
Clean Architecture by layer (data / domain / presentation) within each module. Root package: com.mmg.manahub.
app/
├── app/
│   ├── ManaHubApp.kt        — @HiltAndroidApp; schedules workers, inits Crashlytics, syncs mana symbols
│   ├── MainActivity.kt      — singleTask, deep links, App Links
│   └── navigation/          — AppNavGraph.kt, Screen.kt (sealed routes)
├── core/
│   ├── auth/                — EncryptedSessionManager (encrypted on-disk session)
│   ├── data/
│   │   ├── local/           — Room: MtgDatabase (v35), DAOs, entities, converters, paging, mappers
│   │   ├── remote/          — Scryfall API, DTOs, mappers
│   │   └── repository/      — repository implementations + cache policy
│   ├── di/                  — Hilt modules (Analytics, Crashlytics, Coroutines, Dispatchers, Database, Repository, Supabase, Sync)
│   ├── domain/              — shared models, repository interfaces, use cases
│   ├── nearby/              — Nearby Connections (P2P) for in-person games
│   ├── network/             — OkHttp client, rate-limiting request queue (≤10 req/s for Scryfall)
│   ├── online/              — online sessions (Supabase Realtime): repos, use cases, models
│   ├── sync/                — WorkManager workers: CollectionSyncWorker, CollectionStatsSyncWorker, PriceRefreshWorker, SyncManager
│   ├── tagging/             — TagDictionary, TagAnalyzers, override repository
│   ├── ui/                  — shared composables + themes
│   └── util/                — utilities
└── feature/
├── addcard/             — card search + advanced search + set picker
├── auth/                — sign-in/up, account management
├── carddetail/          — card detail + tag management
├── collection/          — collection browse + filters
├── decks/               — deck list, detail, builder, import/export, improvement
├── draft/               — draft guide, tier lists, set videos
├── friends/             — friend list, invites, detail (folder/history/stats)
├── game/                — game setup, life counter, phase tracker, result screen
├── news/                — MTG news articles + YouTube videos
├── online/              — host/join lobby, deep-link join
├── profile/             — player profile, achievements, insights
├── scanner/             — OCR camera scanner
├── settings/            — app preferences
├── splash/              — startup screen
├── stats/               — collection & game statistics
├── survey/              — post-game survey engine
├── synergy/             — card synergy explorer
├── tagdictionary/       — tag dictionary editor
├── tournament/          — tournament setup and live standings
└── trades/              — proposals, negotiation, wishlist, open-for-trade, shared lists
The initial navigation destination is the Collection screen.

Backend & External Services
Supabase (primary BaaS)

Auth: email/password + Google. Passwords are stored only as a bcrypt hash and are never readable by the developer.
Postgres tables: profiles, friendships, friend_requests, trades, trade_proposals, wishlists, open_for_trade, shared_lists, online_sessions, session_participants, player_states.
RPC functions: batch_upsert_collection, batch_upsert_decks, upsert_deck_cards, get_deck_cards_for_deck, get_collection_changes_since, get_deck_changes_since, create_proposal, counter_proposal, edit_proposal, resolve_shared_list, get_suggestions_for_card, get_trade_suggestions, refresh_trade_suggestions, get_my_active_session(s).
Realtime: online game session state.
Email templates: in supabase/email-templates/ (sign-up confirmation, password reset, email/password change, user invite).

Cloudflare Worker — manahub-draft-api
Serves Draft Guide data from an R2 bucket (manahub-assets).

Routes: /draft/sets-index.json, /draft/{setCode}/guide.json, /draft/{setCode}/tier-list.json
ETag-based caching (304 Not Modified) with Cache-Control: max-age=300, stale-while-revalidate=60
GET/HEAD only, open CORS
Default URL: https://manahub-draft-api.miguel-mglez.workers.dev/

Scryfall API
Card data, prices, images, and set information.

All requests include the required User-Agent: ManaHub/1.0 Android and Accept headers
A rate-limiting queue enforces ≤10 req/s
Card and set data is cached in Room; prices refresh on demand or on launch (if enabled)
Batch price updates use the /cards/collection endpoint in chunks of 75 (capped at 1,000 IDs per call)

YouTube Data API v3
Set-specific videos for the Draft Guide and News. The API key is injected via BuildConfig; if absent, the feature is gracefully disabled (no crash).
ML Kit (Google)
Text recognition runs entirely on-device. No camera frames or OCR results are transmitted externally.
RSS / Atom Feeds
The News feature fetches content from publicly available feeds. Feed URLs are stored locally and no user data is attached.
App Links (GitHub Pages)

https://miguelmglez.github.io/list/{shareId} → shared trade lists
https://miguelmglez.github.io/invite/{code} → friend invites
Custom-scheme fallbacks: manahub://invite/{code} and manahub://auth (Supabase email verification)
assetlinks.json provided under .well-known/ for App Link verification


Data & Privacy
ManaHub is local-first. The following lives only on your device and is never transmitted unless explicitly noted:

Collection, decks, game history, surveys, tournaments, Scryfall metadata cache (Room) · name, avatar, currency, languages, tag dictionary, settings (DataStore).

If you create an account, only the following is stored on Supabase: email, nickname, Game Tag, and auth provider. Passwords are bcrypt-hashed. No location is collected. No ads. No data selling. See PRIVACY_POLICY.md for the full policy.

Security

Traffic restricted to HTTPS via network_security_config.xml (cleartextTrafficPermitted="false")
HttpLoggingInterceptor is BODY level in debug only; NONE in release
Room and DataStore excluded from Google Drive auto-backup (backup_rules.xml, data_extraction_rules.xml)
Release builds use R8 minification and resource shrinking with a custom proguard-rules.pro
YouTube API key injected via an OkHttp interceptor — not visible in Retrofit signatures or Logcat
Scryfall search queries sanitised with an allowlist before being appended to API URLs
Camera frames never leave the device (on-device ML Kit)
User session encrypted on disk (EncryptedSessionManager + security-crypto)


Getting Started
Prerequisites

Android Studio Meerkat or later
JDK 17 (bundled with Android Studio)
Android SDK 35

Clone and build
bashgit clone https://github.com/Miguelmglez/ManaHub.git
cd ManaHub
./gradlew assembleDebug
Configure local.properties
Required keys are marked with *:
propertiesSUPABASE_URL=...            # *
SUPABASE_ANON_KEY=...       # *
GOOGLE_CLIENT_ID=...        # *
YOUTUBE_API_KEY=...         # optional — Draft Guide videos disabled if absent
CLOUDFLARE_WORKER_URL=...   # optional — has a default

# Release signing (optional)
KEY_STORE_PATH=
KEY_STORE_PASSWORD=
KEY_ALIAS=
KEY_PASSWORD=
Open the project in Android Studio and run the app configuration on a device or emulator running Android 10 (API 29) or higher. Camera features require a physical device.

Testing
The project includes ~970 unit-test methods (47 files) plus instrumented Room tests.
bash# Unit tests
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
Coverage spans repositories (Card, UserCard, Deck, GameSession, Tournament…), use cases, and ViewModels (Game, Tournament, Collection, AddCard, CardDetail, Scanner, Survey, Settings, Profile, TagDictionary…). A key instrumented regression test ensures the card upsert (CASCADE) never deletes the user's cards.
Test stack: JUnit 4, MockK, Turbine, kotlinx-coroutines-test, arch-core-testing, Espresso, room-testing.

Roadmap
Implemented
Life counter with full Commander support · collection manager with Scryfall integration and automatic tagging · OCR card scanner · deck builder with synergy engine and import/export · draft guide with tier lists and videos · MTG news aggregator · post-game survey and analytics · tournaments (Round Robin, Swiss, Single Elimination) · player profile with achievements · tag dictionary editor · accounts (Supabase + Google) · friends · trades with shared lists · online multiplayer (Realtime) · in-person multiplayer (Nearby) · cloud sync.
Planned (v2)

Daily puzzle (Wordle-style MTG scenarios) — route stub already in place
Additional visual themes
Tablet and foldable layout support
Play Store release


Contributing
This project is currently in active development. Issues and pull requests are welcome once v1 is stable.

Legal
ManaHub is an unofficial fan project and is not affiliated with, endorsed by, or sponsored by Wizards of the Coast.
Magic: The Gathering and all associated card names, imagery, and lore are intellectual property of Wizards of the Coast LLC.
Card data and images are provided by Scryfall under their terms of service.
Set symbols and mana symbols are copyright Wizards of the Coast and are used in accordance with their fan content policy.

License
This project is licensed under the MIT License — see the LICENSE file for details.

Built with ☕ and too many Commander games.