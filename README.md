# ManaHub

**The ultimate Magic: The Gathering companion app for Android.**

ManaHub combines a life counter, collection manager, deck builder, draft guide, game analytics, and tournament organiser into one unified experience — designed for players who take their game seriously.

---

## Features

### ⚔ Life Counter
Track life totals for 2 to 6 players simultaneously. Built for Commander and Standard, with full support for poison counters, experience counters, energy counters, energy counters, and commander damage tracking. Each player gets a personalised colour theme, an editable name, and a configurable position on screen.

- Commander damage panel with automatic elimination at 21
- Poison (≥10) and life (≤0) auto-elimination with confirmation dialog
- Custom counters (name + icon) per player
- Dice roller (d20) and coin flip with animation
- Phase tracker with configurable phase stops per player
- Land-played tracker per turn
- Layout editor — drag players to reorder and swap positions mid-game
- Turn counter increments on full-round completion
- Grid slot reassignment by drag-and-drop
- Player theme customisation (colour gradient presets)
- Tournament-aware: links game result to an open tournament match automatically

### 📷 Card Scanner
Identify cards by pointing the camera at the card name. Uses on-device ML Kit OCR — no image is sent to any server.

- **Auto-permission**: the system dialog is presented immediately on first launch
- **OCR toggle button**: pause and resume text recognition with one tap, so you can reposition the camera freely before scanning
- **Tap-to-focus**: tap anywhere on the preview to focus the camera at that point and toggle OCR on/off simultaneously
- **Auto-pause after add**: OCR pauses automatically after a card is added, preventing double-scanning the same card
- Flash on/off toggle — flash state is preserved when the confirm sheet closes
- Debounced search (200ms) + two-stage lookup: full-text search → exact name fallback
- Confirm sheet with foil, condition, language and quantity options

### 📁 Collection Manager
Search and manage your entire card collection with real-time Scryfall data.

- Search by card name in English, Spanish, and German
- Filter by colour (W/U/B/R/G + Multicolour + Colourless) with official mana SVG icons
- Filter by rarity, mana value, format legality, price, oracle text, and trade status
- Grid and list view with toggle
- Card detail with double-faced card support and art crop
- Edit quantity, condition, language, foil, and alternative-art flag per copy
- Wishlist mode: the same card can exist in your collection and your wishlist simultaneously
- Batch price refresh via Scryfall collection endpoint
- Stale-cache indicator when Scryfall data could not be refreshed

### 🏷 Automatic Card Tagging
Cards are tagged automatically as they enter your collection using oracle text analysis.

- 30+ built-in tags across categories: Keyword, Strategy, Role, Archetype, Tribal
- Tags with confidence scores: auto-confirmed above threshold, suggested below
- Multilingual pattern matching (EN/ES/DE)
- User-overridable tag dictionary: edit labels, patterns, and categories per tag
- Add completely new tags with custom labels and detection patterns
- Configurable auto-confirm and suggest thresholds via sliders
- Tags feed the deck builder's synergy scoring engine

### 🃏 Deck Builder
Build decks with an intelligent suggestion engine that analyses synergies between your cards.

- Three-step flow: Setup → Suggestions → Review
- Choose a seed strategy (Tokens, +1/+1 Counters, Ramp, Control, Combo, Graveyard, Burn, Tribal)
- Card suggestions scored by tag overlap, mana curve fit, colour identity, and collection ownership
- Mainboard and sideboard tabs
- Sideboard change recommendations based on post-game survey patterns
- Mana curve chart
- Import deck from text (Moxfield/MTGO format)
- Export deck to clipboard
- Basic land auto-calculator
- Format validation (Standard, Pioneer, Modern, Commander, Casual)

### 📰 MTG News
Stay up to date with the Magic community directly from the app.

- Aggregates articles and videos from configurable RSS/Atom sources
- Separate tabs for articles and YouTube videos
- Filter by language (EN/ES/DE)
- Articles open in Chrome Custom Tabs
- Videos link directly to YouTube
- Add, enable, or disable individual news sources

### 📺 Draft Guide
Set-by-set draft guidance with tier lists, strategy breakdowns, and curated video content.

- Browse draftable sets with release dates and card counts
- Per-set tier lists with card-by-card ratings
- Strategy guide with archetype breakdown and key cards
- YouTube video integration — watch draft guides without leaving the app
- Offline-capable with local asset caching

### 📊 Statistics
Deep analytics on your collection and game history.

- Collection value in USD or EUR with automatic Scryfall price refresh
- Mana curve and colour distribution charts
- Win rate, average life on win/loss, average game duration, average win turn
- Per-deck win rate with dedicated stats table
- Favourite game mode and most frequent elimination reason
- Current win streak
- Survey-derived insights: mana health, opening hand ratings, favourite win style

### 👤 Profile
A personalised dashboard that grows with your play history.

- Player name and avatar customisation
- Play style auto-detected from your history (Aggro, Midrange, Control, Balanced)
- 15 unlockable achievements (First Blood, Dominant, Poison Master, High Roller, and more)
- Collection insights: favourite colour, most valuable colour identity
- Recent game history with W/L badges and duration
- Best deck highlighted by win rate
- Mana issue rate and hand quality from survey data
- **Send Feedback** — compose a message and optionally attach an image from your photo library; sent to the developer via your device's email app. No data is transmitted by ManaHub itself.

### 🏆 Tournaments
Organise local tournaments with automatic bracket generation and live standings.

- Round Robin, Swiss, and Single Elimination formats
- Configurable matches per pairing (best of 1/2/3)
- Random or manual pairings
- Standings with points system (Win = 3 pts) and life-total tiebreaker
- Matches launch directly into the life counter — results recorded automatically
- Manual result entry when playing without the app

### 📋 Post-Game Survey
An optional 2-minute review after each game that feeds your analytics.

- Contextual questions based on what happened (commander damage, sideboard, loss reason)
- Opening hand quality rating
- Mana health assessment (smooth, flooded, screwed, inconsistent)
- Result-feel rating (dominant, close, lucky, skillful)
- Card impact assessment for cards in played decks
- Responses surfaced as insights in the Profile screen
- Data feeds deck synergy scoring for future suggestions

### ⚙ Settings
- App language (English, Spanish, German)
- Card language preference for oracle text and printed text
- News language filter
- Preferred currency (USD / EUR)
- Auto-refresh prices on app launch toggle
- Visual theme (Neon Void, Medieval Grimoire, Arcane Cosmos)

---

## Screenshots

*Coming soon*

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture (feature modules) |
| Database | Room 2.8 with full migration history (v1→21) |
| Dependency Injection | Hilt |
| Networking | Retrofit 3 + OkHttp 5 |
| Image Loading | Coil 2 with SVG decoder |
| Camera | CameraX |
| OCR | ML Kit Text Recognition (on-device) |
| Card Data | Scryfall API |
| Video | YouTube Data API v3 |
| Async | Kotlin Coroutines + Flow |
| Preferences | Jetpack DataStore |
| Navigation | Navigation Compose |
| Build | KSP, Gradle Kotlin DSL |
| Min SDK | 29 (Android 10) |
| Target SDK | 35 (Android 15) |

---

## Architecture

```
app/
├── core/
│   ├── data/
│   │   ├── local/          — Room entities, DAOs, migrations (v1→21)
│   │   ├── remote/         — Scryfall API, DTOs, mappers
│   │   └── repository/     — Repository implementations + CachePolicy
│   ├── domain/
│   │   ├── model/          — Domain models
│   │   ├── repository/     — Repository interfaces
│   │   └── usecase/        — Business logic use cases
│   ├── network/            — OkHttp client, rate-limiting request queue
│   ├── tagging/            — Tag dictionary, analyzers, override repository
│   └── ui/
│       ├── components/     — Shared composables (MagicToast, AddToCollectionSheet, …)
│       └── theme/          — NeonVoid / Medieval Grimoire / Arcane Cosmos themes
└── feature/
    ├── addcard/            — Card search + advanced search + set picker
    ├── carddetail/         — Card detail + tag management
    ├── collection/         — Collection browse + filters
    ├── decks/              — Deck list, detail, builder, import/export
    ├── draft/              — Draft guide, tier lists, set videos
    ├── game/               — Game setup, life counter, phase tracker, result screen
    ├── news/               — MTG news articles + YouTube videos
    ├── profile/            — Player profile, achievements, insights
    ├── scanner/            — OCR camera scanner
    ├── settings/           — App preferences
    ├── stats/              — Collection statistics
    ├── survey/             — Post-game survey engine
    ├── synergy/            — Card synergy explorer
    ├── tagdictionary/      — Tag dictionary editor
    └── tournament/         — Tournament setup and live standings
```

---

## API & External Services

### Scryfall API
ManaHub uses the [Scryfall API](https://scryfall.com/docs/api) for card data, prices, images, and set information.

- All requests include the required `User-Agent: ManaHub/1.0 Android` and `Accept` headers per Scryfall's guidelines
- A rate-limiting queue enforces ≤10 req/s
- Card and set data is cached in Room; prices refresh on demand or on launch if auto-refresh is enabled
- Batch price updates use the `/cards/collection` endpoint in chunks of 75 (capped at 1,000 IDs per call)

### YouTube Data API v3
The Draft Guide fetches set-specific video content from YouTube. The API key is stored in `local.properties` and injected via `BuildConfig` — it is never hardcoded in source or visible in network logs.

### ML Kit (Google)
Text recognition runs entirely **on-device**. No camera frames or OCR results are transmitted externally.

### RSS / Atom Feeds
The News feature fetches article content from publicly available RSS feeds. Feed URLs are stored locally and no user data is attached to these requests.

---

## Security

- Network traffic restricted to HTTPS via `network_security_config.xml` (`cleartextTrafficPermitted="false"`)
- HTTP logging (`HttpLoggingInterceptor`) is `BODY` level in debug builds only; `NONE` in release
- Room database and DataStore excluded from Google Drive auto-backup via `backup_rules.xml` and `data_extraction_rules.xml`
- Release builds use R8 minification and resource shrinking with a custom `proguard-rules.pro`
- YouTube API key injected via OkHttp interceptor — not visible in Retrofit signatures or Logcat
- Scryfall search queries sanitised with an allowlist before being appended to API URLs
- Camera frames never leave the device (ML Kit on-device processing)

---

## Getting Started

### Prerequisites

- Android Studio Meerkat or later
- JDK 17 (bundled with Android Studio)
- Android SDK 35

### Clone and build

```bash
git clone https://github.com/Miguelmglez/Magic_Folder.git
cd Magic_Folder
./gradlew assembleDebug
```

### YouTube API key (optional)

The Draft Guide video feature requires a YouTube Data API v3 key. Without it the feature is gracefully disabled (no crash). To enable it:

1. Create a key at [Google Cloud Console](https://console.cloud.google.com/)
2. Add it to `local.properties` (this file is git-ignored):
```properties
YOUTUBE_API_KEY=your_key_here
```

### Run on device or emulator

Open the project in Android Studio and run the `app` configuration on a device or emulator running Android 10 (API 29) or higher. Camera features require a physical device.

---

## Testing

The project includes **270+ unit tests** and **10 instrumented Room tests**.

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Test coverage

| Area | Type |
|---|---|
| CardRepository, UserCardRepository | Unit + Room instrumented |
| DeckRepository, GameSessionRepository, TournamentRepository | Unit |
| AddCardToCollectionUseCase, CollectionUseCases | Unit |
| GameViewModel, TournamentViewModel, CollectionViewModel | Unit |
| AddCardViewModel, CardDetailViewModel, ScannerViewModel | Unit |
| SurveyViewModel, SettingsViewModel, ProfileViewModel | Unit |
| TagDictionaryRepository, TagDictionaryViewModel | Unit |
| CardDao CASCADE regression (upsert must not delete user cards) | Room in-memory |

---

## Roadmap

### v1 — Current
- Life counter with full Commander support
- Collection manager with Scryfall integration and automatic tagging
- OCR card scanner with tap-to-focus
- Deck builder with synergy engine and import/export
- Draft guide with tier lists and YouTube videos
- MTG news aggregator
- Post-game survey and analytics
- Tournaments (Round Robin, Swiss, Single Elimination)
- Player profile with achievements
- Tag Dictionary editor

### v2 — Planned
- Online multiplayer (Firebase Realtime Database)
- Trading system (local and online)
- Daily puzzle (Wordle-style MTG scenarios)
- Multiple visual themes (additional presets)
- Tablet and foldable layout support
- Play Store release

---

## Contributing

This project is currently in active development. Issues and pull requests are welcome once v1 is stable.

---

## Legal

ManaHub is an unofficial fan project and is not affiliated with, endorsed by, or sponsored by Wizards of the Coast.

Magic: The Gathering and all associated card names, imagery, and lore are intellectual property of Wizards of the Coast LLC.

Card data and images are provided by [Scryfall](https://scryfall.com) under their [terms of service](https://scryfall.com/docs/terms).

Set symbols and mana symbols are copyright Wizards of the Coast and are used in accordance with their [fan content policy](https://company.wizards.com/en/legal/fancontentpolicy).

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

*Built with ☕ and too many Commander games.*
