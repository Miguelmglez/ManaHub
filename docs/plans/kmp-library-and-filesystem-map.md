# KMP Library & File-System Map (Android + Web)

Companion to `kmp-migration-plan.md`. This doc is the **concrete mapping**: every current library →
its KMP fate and source set, and the **full target module/file tree** with the Android↔Web split.

**Targets:** `android` + `wasmJs` (Compose Multiplatform for Web). iOS/Desktop reserved, not built.
**Rule of thumb:** if it touches an Android `Context`, the JVM/Android-only toolchain, or a native lib
→ it lives in `androidMain` (or behind `expect`/`actual`). Pure Kotlin + multiplatform libs → `commonMain`.

---

## 1. Library mapping (per source set)

Legend: **C** = commonMain · **A** = androidMain · **W** = wasmJsMain · **T** = commonTest · ❌ = removed.

### Networking & serialization
| Current | Fate | Set | Notes |
|---|---|---|---|
| `retrofit`, `converter-gson`, `converter-kotlinx-serialization` | ❌ → Ktor | — | replace all 4 API surfaces |
| `ktor-client-android`, `ktor-client-cio` | keep + split | A | Android engine (OkHttp/CIO) |
| `ktor-client-core`, `-content-negotiation`, `-serialization-kotlinx-json` (NEW) | add | C | shared client |
| `ktor-client-js` / wasm engine (NEW) | add | W | web engine |
| `okhttp`, `okhttp-logging` | keep | A | Ktor-OkHttp engine + debug logging only |
| `gson` | ❌ → kotlinx-serialization | — | drop entirely |
| `kotlinx-serialization-json` | keep | C | already present |

### Dependency Injection
| Current | Fate | Set | Notes |
|---|---|---|---|
| `hilt-android`, `hilt-android-compiler`, `hilt-navigation-compose`, `hilt-work`, `hilt-work-compiler` | ❌ → Koin | — | ~300 files |
| `koin-core` (NEW) | add | C | shared modules |
| `koin-android` (NEW) | add | A | `androidContext`, worker, etc. |
| `koin-compose`, `koin-compose-viewmodel` (NEW) | add | C | `koinViewModel()` in CMP |

### Persistence
| Current | Fate | Set | Notes |
|---|---|---|---|
| `room-runtime`, `room-ktx`, `room-compiler`, `room-paging`, `room-testing` | keep | **A** | Room has **no wasm target**; DAOs/entities/migrations stay Android |
| (Room KMP `BundledSQLiteDriver`) | optional later | A | only if a JVM/iOS target is ever added |
| `datastore-preferences` | keep + abstract | A | behind `expect KeyValueStore` |
| web key-value (`localStorage`) | NEW actual | W | `actual KeyValueStore` |
| web cache (IndexedDB or in-memory) | NEW | W | the web data source behind repo interfaces |

### UI — Compose → Compose Multiplatform
| Current | Fate | Set | Notes |
|---|---|---|---|
| `compose-bom` + `compose-ui`/`material3`/`foundation`/`material3`(dup)/`material-icons-extended`/`ui-tooling(-preview)`/`ui-text-google-fonts` | → **CMP** | C | `org.jetbrains.compose` plugin; icons via CMP; tooling android-only |
| `navigation-compose` | → CMP nav | C | decision from Spike E (CMP-Nav / Voyager / Decompose) |
| `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | → `org.jetbrains.androidx.lifecycle` | C | KMP lifecycle/ViewModel |
| `coil-compose`, `coil-svg` (Coil 2) | → **Coil 3** (`coil3:coil-compose` + `coil-network-ktor`) | C | wasm-capable |
| `accompanist-permissions` | keep | A | web has no equivalent |
| `core-splashscreen`, `browser`, `emoji2`, `tv-material` | keep | A | Android-only |

### Platform features (Android-only → expect/actual)
| Current | Fate | Set | Web actual |
|---|---|---|---|
| `camera-camera2/-lifecycle/-view` (scanner) | keep | A | `getUserMedia` or "unavailable" |
| `vosk-android` (voice) | keep | A | WebSpeech or "unavailable" |
| `youtube-player` | keep | A | web `<iframe>` or hidden |
| `play-app-update`, `play-review` | keep | A | no-op on web |
| `credentials`, `credentials-play-services-auth`, `googleid` (Google sign-in) | keep | A | Supabase web OAuth |
| `work-runtime`, `hilt-work` | keep | A | web: skip / service-worker |
| `paging-runtime`, `paging-compose` | keep | A | common pagination model; web manual paging |

### Firebase
| Current | Fate | Set | Web actual |
|---|---|---|---|
| `firebase-bom`, `-analytics`, `-messaging`, `-crashlytics-ktx` | keep | A | Crashlytics → **no-op**; Analytics → GA4/no-op; FCM → out of scope v1 |
| `CrashReporter` interface (NEW) | add | C | `expect`/interface; Android actual wraps Crashlytics |

### Backend
| Current | Fate | Set | Notes |
|---|---|---|---|
| `supabase-bom`, `auth-kt`, `postgrest-kt`, `realtime-kt` | keep | C | jan-tennert KMP; **verify wasmJs in Spike B** (fallback: thin Ktor REST/Realtime in common) |

### Utility
| Current | Fate | Set | Notes |
|---|---|---|---|
| `kotlinx-collections-immutable` | keep | C | KMP |
| `guava` | keep | A | JVM-only; isolate any usage to Android |

### Testing
| Current | Fate | Set | Notes |
|---|---|---|---|
| `mockk` | keep | androidUnitTest | **JVM-only — cannot run in commonTest/wasm** |
| `turbine`, `kotlinx-coroutines-test` | keep | C/T | KMP |
| `kotlin-test` (NEW) | add | T | commonTest assertions + **fakes instead of mockk** |
| `junit`, `arch-core-testing`, `room-testing`, `mockwebserver`, `espresso-core`, `uiautomator`, `androidx-test-ext-junit` | keep | A | Android instrumented/unit only |

---

## 2. Target module & file-system tree

```
ManaHub/
├── settings.gradle.kts                 includes all :shared:* + :androidApp + :webApp
├── gradle/libs.versions.toml           KMP-capable aliases (Koin, Ktor, CMP, Coil3, kotlinx)
│
├── shared/
│   ├── core-model/                     ← pure Kotlin, ZERO platform deps (extract FIRST)
│   │   └── src/commonMain/kotlin/…/core/model/      Card, Deck, ManaColor, DeckFormat, GameFormat…
│   │
│   ├── core-common/
│   │   └── src/
│   │       ├── commonMain/…            Result/Resource, DispatcherProvider (expect),
│   │       │                           KeyValueStore (expect), CrashReporter (interface),
│   │       │                           pagination model (replaces PagingData)
│   │       ├── androidMain/…           actual dispatchers, DataStore KeyValueStore, Crashlytics reporter
│   │       └── wasmJsMain/…            actual dispatchers, localStorage KeyValueStore, no-op reporter
│   │
│   ├── core-domain/
│   │   └── src/commonMain/…            UseCases + repository INTERFACES (no platform imports)
│   │
│   ├── core-data/
│   │   └── src/
│   │       ├── commonMain/…            RepositoryImpl orchestration, Ktor API clients,
│   │       │                           Supabase client, CachePolicy, rate-limit queues (Mutex)
│   │       ├── androidMain/…           Room: MtgDatabase, DAOs, entities, mappers, migrations;
│   │       │                           Android LocalDataSource actuals
│   │       └── wasmJsMain/…            web LocalDataSource (IndexedDB/localStorage + remote-first)
│   │
│   ├── core-ui/
│   │   └── src/commonMain/…            MagicTheme (12 palettes), tokens, typography, spacing,
│   │                                   shapes, shared components (EmptyState, MagicToast, CardGridItem…)
│   │
│   └── feature-<name>/                 one module per feature (collection, decks, game, …)
│       └── src/
│           ├── commonMain/…            domain + data + ViewModel + CMP UI (Screen, sub-composables)
│           ├── androidMain/…           ONLY platform-bound features: scanner camera preview,
│           │                           voice mic, YouTube — actuals + Android-only UI
│           └── wasmJsMain/…            web fallbacks for those platform-bound features
│
├── androidApp/                         ← Android entrypoint (was :app)
│   └── src/main/…                      MainActivity, ManaHubApp, Koin Android startup,
│                                       Firebase/Camera/Vosk/Work/Play/credentials wiring,
│                                       AndroidManifest, res/, google-services.json
│
├── webApp/                             ← NEW web entrypoint
│   └── src/wasmJsMain/…                main(), CanvasBasedWindow/ComposeViewport, Koin web startup,
│       └── resources/                  index.html, web manifest, web-only assets
│
└── baseline-profile/                   unchanged (Android only)
```

### Android vs Web split — decision rules
- **Shared (commonMain):** all domain, all use cases, repository interfaces + orchestration, Ktor API
  clients, Supabase, ViewModels, MagicTheme + CMP UI for non-platform screens.
- **Android-only (androidMain):** Room, DataStore, Firebase, WorkManager, CameraX, Vosk, YouTube,
  Play services, Google credential sign-in, splashscreen/browser/emoji2/tv-material, paging.
- **Web-only (wasmJsMain):** localStorage KV, IndexedDB cache, web Supabase session/OAuth, browser
  fallbacks (getUserMedia / WebSpeech / iframe) or graceful "unavailable on web" states.
- **expect/actual contracts to define in core-common:** `DispatcherProvider`, `KeyValueStore`,
  `CrashReporter`, `PlatformCapabilities` (camera/voice/push availability flags), `BackgroundScheduler`.

### Migration order of the tree (executable sequence)
1. `shared/core-model` (Spike A skeleton → full extraction after blockers).
2. `shared/core-common` (the expect/actual contracts — unblocks everything else).
3. `shared/core-domain` → `shared/core-data` (Room stays androidMain; Ktor replaces Retrofit).
4. `shared/core-ui` (CMP theme + components).
5. `shared/feature-*` leaf-first (settings, stats) → heavy-last (game, decks, online, scanner).
6. `webApp` entrypoint stands up once core-ui + first feature compile on wasmJs.
7. Rename `:app` → `:androidApp` last (mechanical), keep it shippable throughout.

---

## 3. Version-catalog additions (no removals until a surface is fully migrated)
Add aliases for: `koin-core`, `koin-android`, `koin-compose`, `koin-compose-viewmodel`;
`ktor-client-core`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`,
`ktor-client-js`; `coil3-compose`, `coil3-network-ktor`; `compose-multiplatform` plugin +
`jetbrains-lifecycle` + `jetbrains-navigation`; `kotlin-test`. Keep every existing Android alias —
remove a library ONLY when its last usage is migrated (per-feature), so Android never breaks.
