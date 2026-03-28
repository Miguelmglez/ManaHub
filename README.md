# ManaHub

**The ultimate Magic: The Gathering companion app for Android.**

ManaHub combines a life counter, collection manager, deck builder, and game analytics into one unified experience — designed for players who take their game seriously.

---

## Features

### ⚔ Life Counter
Track life totals for 2 to 10 players simultaneously. Built for Commander and Standard, with full support for poison counters, experience counters, energy counters, and commander damage tracking. Each player gets a personalised colour, an editable name, and a configurable position on the screen.

- Long-press acceleration on +/- buttons (±1 → ±10)
- Dice roller (d20) and coin flip per player
- Commander damage panel with automatic elimination at 21
- Automatic elimination by poison (≥10) and life (≤0)
- Layout editor — reorder players during the game
- Reset or exit with a single tap, with confirmation dialogs
- Game state persists if you switch tabs mid-game

### 📁 Collection Manager
Add cards by text search or barcode scanner. ManaHub connects to the Scryfall API to retrieve card data, images, prices, and set symbols in real time.

- Search in English, Spanish, and German
- Automatic fallback cascade: exact name → multilingual → fuzzy
- Filter by colour (W/U/B/R/G + Multicolour) with official mana SVG icons
- Grid and list view with toggle
- Card detail with double-faced card support
- Edit quantity, condition, and language per copy
- Automatic card tagging by oracle text (Ramp, Removal, Draw Engine, Tribal, and 20+ more)
- Batch price refresh via Scryfall collection endpoint

### 🃏 Deck Builder
Build decks with an intelligent suggestion engine that analyses synergies between cards.

- Three-step flow: Setup → Suggestions → Review
- Choose a seed strategy (Tokens, +1/+1 Counters, Ramp, Control, Combo, Graveyard, Burn, Tribal)
- Card suggestions scored by tag overlap, mana curve fit, colour identity, and collection ownership
- Mainboard and sideboard tabs with drag-to-move
- Sideboard recommendations after each game based on survey responses
- Filters by acquisition status, rarity, and price

### 📊 Statistics
Deep analytics on your collection and game history.

- Collection value with USD/EUR toggle and Scryfall price refresh
- Mana curve and colour distribution across your collection
- Win rate, average life on win/loss, average game duration
- Per-deck win rate tracking
- Favourite format and play style detection

### 👤 Profile
A personalised dashboard that grows with your play history.

- Play style calculated automatically (Aggro, Control, Midrange, Commander)
- 15 unlockable achievements (First Blood, Dominant, Poison Master, High Roller, and more)
- Game insights derived from post-game survey responses
- Recent game history with W/L badges and duration
- Best deck highlighted by win rate

### 🏆 Tournaments
Organise local tournaments with automatic bracket generation and live standings.

- Round Robin, Swiss, and Single Elimination formats
- Configurable matches per pairing (best of 1/2/3)
- Random or manual pairings
- Standings with points system (Win = 3pts, Draw = 1pt)
- Tiebreaker by accumulated life total (goal average)
- Live results linked to in-app game sessions

### 📋 Post-Game Survey
An optional 2-minute review after each game that feeds your analytics.

- Contextual questions based on what happened (commander damage, sideboard, loss reason)
- Opening hand quality rating
- Mana health assessment
- Responses stored and surfaced as insights in your profile
- Sideboard change recommendations based on patterns over time

---

## Screenshots

*Coming soon*

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material3
- **Architecture:** MVVM + Clean Architecture
- **Database:** Room (with full migration history)
- **Dependency Injection:** Hilt
- **Networking:** Retrofit + OkHttp
- **Image Loading:** Coil with SVG decoder
- **Card Data:** Scryfall API
- **Async:** Kotlin Coroutines + Flow
- **Storage:** DataStore Preferences
- **Camera:** CameraX + ML Kit (barcode scanning)
- **Navigation:** Navigation Compose
- **Build:** KSP, Gradle Kotlin DSL
- **Min SDK:** 29 (Android 10)

---

## Architecture

```
app/
├── core/
│   ├── data/
│   │   ├── local/          — Room entities, DAOs, migrations
│   │   ├── remote/         — Scryfall API, DTOs, mappers
│   │   └── repository/     — Repository implementations
│   ├── domain/
│   │   ├── model/          — Domain models
│   │   ├── repository/     — Repository interfaces
│   │   └── usecase/        — Business logic use cases
│   ├── network/            — OkHttp client, rate limiting queue
│   └── ui/
│       ├── components/     — Shared composables
│       └── theme/          — NeonVoid theme, tokens, typography
└── feature/
    ├── collection/         — Collection screen
    ├── addcard/            — Add card (search + scanner)
    ├── carddetail/         — Card detail
    ├── decks/              — Deck list, detail, builder
    ├── play/               — Game setup, game screen, result
    ├── survey/             — Post-game survey
    ├── tournament/         — Tournament setup and standings
    ├── stats/              — Collection statistics
    ├── profile/            — Player profile and achievements
    └── settings/           — App preferences
```

---

## API

ManaHub uses the [Scryfall API](https://scryfall.com/docs/api) for all card data.

All requests include the required `User-Agent` and `Accept` headers as per Scryfall's guidelines. A request queue enforces a minimum 100ms delay between requests (≤10 req/s). Card SVG assets are loaded directly from `svgs.scryfall.io`.

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### Clone and build

```bash
git clone https://github.com/Miguelmglez/Magic_Folder.git
cd Magic_Folder
./gradlew assembleDebug
```

### Run on device or emulator

Open the project in Android Studio and run the `app` configuration on a device or emulator running Android 10 (API 29) or higher.

---

## Roadmap

### v1 — Current
- Life counter with full Commander support
- Collection manager with Scryfall integration
- Deck builder with synergy engine
- Post-game survey and analytics
- Tournaments
- Player profile with achievements

### v2 — Planned
- Online multiplayer (Firebase Realtime Database)
- Draft mode with pick advisor and set-specific guidance
- Trading system (local and online)
- Daily puzzle (Wordle-style MTG scenarios generated by AI)
- Multiple visual themes (Medieval Grimoire, Arcane Cosmos, Phyrexian Oil)
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
