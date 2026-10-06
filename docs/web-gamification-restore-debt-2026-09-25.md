# Web debt from the gamification restoration

**Recorded:** 2026-09-25
**Status:** Deferred until the user explicitly resumes web implementation.
**Scope:** Web implications discovered while restoring Gamification, Profile, Friends, and Stats on Android. This document records work; it authorizes no web implementation or web build.

## Native rules reference (2026-10-03)

Android adds shared rules models, parser, immutable local search and a focused `:shared:feature-rules`
presentation module. Web still needs baseline resource packaging, official-source fetching with
equivalent host/redirect/size validation, persistent immutable editions and atomic activation, Koin
registration, browser navigation/restoration and Home/Profile entry points. Preserve edition-bound
references, last-valid-snapshot recovery and explicit update checks. Android's Context, OkHttp and
AtomicFile adapters cannot be ported into commonMain. No web source or builds are authorized by this
record; see `docs/adr/ADR-011-native-rules-editions.md`.

## Native rules reference (2026-10-03)

Android adds shared rules models, parser, immutable local search and a focused `:shared:feature-rules`
presentation module. Web still needs baseline resource packaging, official-source fetching with
equivalent host/redirect/size validation, persistent immutable editions and atomic activation, Koin
registration, browser navigation/restoration and Home/Profile entry points. Preserve edition-bound
references, last-valid-snapshot recovery and explicit update checks. Android's Context, OkHttp and
AtomicFile adapters cannot be ported into commonMain. No web source or builds are authorized by this
record; see `docs/adr/ADR-011-native-rules-editions.md`.

## Resume order

1. **Restore the web build baseline.** The previous `wasmJsBrowserDistribution` attempt was blocked by a `yarn.lock` drift and a non-exhaustive `webApp` `AuthViewModel` branch for `PasswordUpdatedSessionRevoked` (noted in the restore plan at commit `984bc927`). Recheck these against the current HEAD when web work resumes; do not assume the old failure still reproduces.
2. **Bring Friends onto the current search contract (F-20).** `WebFriendRepository` still uses `get_friend_collection`. That RPC filters against an empty `public.cards` table, uses OFFSET with a non-unique order, and can silently truncate its unbounded `all` result at PostgREST's row cap. Port web to the stable, paged `search_friend_cards` contract and preserve account/session cancellation and typed errors. After every supported client has moved, the backend owner may retire `get_friend_collection`; do not drop it while a shipped web client still calls it.
3. **Align web Friends behavior with the Android fixes.** A request that disappeared during accept/delete should refresh the list rather than leave a generic row error. Recheck F-24's shared-layer debt: the empty-id `Friend` search sentinel, referral-code errors collapsed to `null`, hard-coded invite host, UI types in ViewModels, and localized strings passed into VM methods. Some of these may already have changed since the 2026-09-24 audit; verify before porting.
4. **Choose a web gamification authority and storage model (G-21).** Shared use cases currently emit events on web into a bus with no engine subscriber. Web repositories deliberately skip some emitters, so web activity does not earn XP even when the same account syncs with Android. Decide whether the web engine is server-authoritative with a remote-first cache or has an IndexedDB-backed local store, and define ledger idempotency, account scoping, offline behavior, and sync conflict rules before adding an engine. Keep `FeatureFlags.Gamification.ENABLED` off during this work.
5. **Port presentation only after the engine contract is settled.** Profile gamification tabs, achievement/quest/reward UI, celebrations, and cosmetics remain Android presentation. Move eligible Compose code to CMP resources (`Res` instead of `R.string`), use multiplatform ViewModel/back handling, and isolate Android AGSL `RuntimeShader` behind a platform brush abstraction with a web fallback. Preserve the 12 MagicTheme palettes and accessible loading/error/empty states.
6. **Port Stats deliberately.** The P8 Android corrections for local-seat totals, decisive win rate, draws, bounded subscriptions, chart contrast, account-scoped trade stats, and immediate A→B UI reset are the behavioral reference. A retained screen must not replay A's state to B on navigation return. Web should use its own data source and lifecycle rather than copying Android Room/WorkManager code. Recheck the remaining P8 UI debt (S-22 bounded single items and partial LOW findings) when designing the web screen.

7. **Align advanced card color search when web work resumes.** Treat `M` as an additional two-or-more-distinct-colors condition on the selected card-color or color-identity field. Keep W/U/B/R/G comparison semantics independent of `M`, preserve panel expansion and immediate Clear behavior, and distinguish a confirmed empty Scryfall response from syntax or network errors. Web Friends still needs the paged `search_friend_cards` contract from step 2; its `M` behavior must agree with Android and the RPC. Archidekt remains exact-set W/U/B/R/G/C without `M`. Check double-faced cards explicitly: Scryfall color predicates can match different faces in one query, so `c>=2 c=w` is not logically empty even though front-face local colors are monowhite.

## Verification when web work resumes

- Wishlist transfer delivery uses an independent owner/version ledger, explicit user_id absolute
  payloads and conditional acknowledgement, with no collection markers or XP. Preserve retained
  review through explicit follow-up without copying source quantities. Android legacy editor/delete
  concurrency and timeout tombstones still block automatic delivery; web needs its own equivalent
  protocol rather than assuming local dirty=false or a remote postcommit marker is atomic authority.

- Android collection transfer application now has a serialized pure session gate, checked quantity
  arithmetic and Room-local subset markers/history/events. A future web store must provide its own
  atomic protocol and direct auth-state guards; it cannot reuse Room idempotency as cross-device
  authority. Room62 guest row provenance intentionally leaves legacy NULL-owned rows unclaimed.
  Preserve separate wishlist outcomes and zero-XP, replayable CollectionChanged reconciliation.
  Android production workers/intake/host and web execution remain pending independently.

- Collection large transfers added pure owner/session, repository/file-store/coordinator contracts
  and a block-streaming parser on 2026-09-30. They do not require new web implementations until web
  transfer work resumes. The future adapter needs strict UTF-8 decoding, durable per-source ordinal
  staging, keyset review, explicit guest provenance and atomic local apply markers; Android Room and
  WorkManager implementations must not be copied into wasm. Parser completion alone is not a ready
  review or confirmation. No web target was edited or built for this Android substep.
  Android Room v59 now stores neutral source staging, provenance and frozen snapshots. Future web
  adapters must preserve the user's explicit per-entry collection/wishlist/undecided intention;
  file formats, folder names and omission consent never imply a collection action. Android v59
  neutral acknowledgement cannot authorize an action. Android v60 adds destination-specific action
  snapshots and per-entry versions/locks; future web persistence needs the same subset semantics,
  leaving unrelated pending entries editable. Collection/wishlist execution and web adapters remain
  pending; no web source or build was changed.
  Android private receipt copies now use strict UTF-8, actual-byte budgets, SHA-256, UUID final paths,
  bounded sequential provider I/O and hash-verified rename-gap recovery. A future web source store
  needs equivalent durable receipt accounting/replay without Android ContentResolver/filesystem code;
  do not infer guest ownership during auth loading. Runtime worker/session binding remains pending.
  A pure durable resolver now consumes a small gateway/store contract: exact-printing priority,
  75-identifier requests, pending-preserving network waits, five real failures, and persisted name
  claims. Web adapters remain deferred. The Android gateway reuses its existing limiter and cache;
  no wasm implementation/build was added. Android Room61 now separates immutable source-group keys
  from edited review payloads and archives decisions before reconstruction. Future web storage must
  retain destinations and edits across unchanged contributions, and expose durable decisions when
  selected contributions change/disappear; excluded-source copies must not survive implicitly.
  Review group pages are200, visible pages50, and provenance is at most10 files. Sorted content-hash
  fingerprints retain multiplicity and owner-bound history; hash matching never replaces apply
  markers. Android functional validation is separate from web and device heap/load acceptance.

- Build `wasmJsBrowserDistribution` from a clean, current dependency lockfile and run affected common/web tests.
- Confirm no Android/Java imports leak into `commonMain`; Android Room, DataStore, WorkManager, and AGSL stay behind interfaces or actuals.
- Exercise one account on Android and web: XP idempotency, account switch, sign-out, guest isolation, friend search pagination, expired friend requests, and trade stats refresh.
- Inspect NeonVoid and HallowedPrint, keyboard navigation, labels, touch/click targets, and loading/error/empty states.

## Source pointers

- The 2026-10-06 Import UX adds shared `TransferReviewScope`, `TransferPageDirection` and
  `TransferReviewCursor` contracts for bounded pending/history pages. Android performs scope
  filtering in Room before hydrating at most 50 entries and validates job/generation/scope cursor
  identity. A future web implementation needs equivalent owner-checked forward/backward paging,
  pending-only ordinary bulk actions and an explicit retained-Wishlist follow-up. Completed
  Collection/Wishlist pages use immutable action-entry snapshots keyed by action/entry, with
  `sourceEntryId` identifying the original aggregate; web must preserve those outcomes after
  later edits or follow-up. Confirmation requests also carry an optional expected payload version
  that Android validates transactionally; retained-Wishlist and invalid-pending counts are distinct
  from immutable history totals. Web needs equivalent authoritative consent/capability checks.
  The shared API default is not a working web implementation.
  Android picker launch proof, lifecycle and modal
  host remain platform adapters. Web implementation/builds remain paused by user direction.

- Legacy import preference quarantine now has a pure verification/removal decision contract and an
  Android opaque private recovery adapter. Web restoration must also treat unowned old queues as
  unknown previous application and cannot automatically claim/import them. Do not port Java framed
  I/O, Context or SharedPreferences into commonMain; web storage/backup controls remain deferred.

- Wishlist mutation delivery now has Android Room64 retained tombstones and a versioned absolute
  target ledger shared by manual editors and transfers. Web must implement the same local deletion
  protection, dormant ghost repair, conditional acknowledgement and captured-owner commands behind
  shared TransferWishlistSync. Server fencing is absent: do not promise remote/cross-device exactly-once
  or port Android Room classes. Android functional tests do not validate web storage or concurrency.

- `docs/plans/gamification-restore-plan.md` (P2, P5, P7, D14 and progress log)
- Shared CollectionTransferRepository now includes bounded inventories/provenance/error previews,
  membership decisions, explicit destination commands, independent wishlist outcomes and versioned
  pending-only discard. Android implements them with Room/session serialization; web implementations
  remain deferred. Do not infer pending quantities from accepted minus collection-applied, or lose
  manual pause checkpoints on auth switching. Export remains a separate Phase 3 contract.
- Android transfer orchestration now uses WorkManager KEEP, UUID-only job requests, independent
  wishlist delivery and a common eight-minute application-first runner. Web scheduling remains
  deferred and must implement durable completion reconciliation for late commands, distinguish
  user/owner pauses and preserve resolver cooldown/failures across account return. Common models
  contain no WorkManager/Room imports. Android startup fallback is platform-specific, not a web
  implementation. CollectionChanged is zero-XP and its existing derived enable catch-up remains
  necessary when the gamification backend flag drops bus events.
- `docs/plans/audits-2026-09-24/friends-audit.md` (F-20, F-24)
- `docs/plans/audits-2026-09-24/gamification-drift-audit.md` (G-21 and KMP classification)
- `docs/plans/kmp-migration-plan.md` and `docs/plans/kmp-migration-progress.md`

- Collection transfer Phase2 now shares immutable selection/query contracts and source/destination
  review APIs; Android provides Room65 snapshots and Android Paging through owner-scoped adapters.
  Web snapshot storage, stable ordering, provenance-aware guest sources, intake/recovery/report
  adapters and routed review remain paused. Export must read all matched individual snapshot rows,
  never a visible page or group representatives. New shared defaults reject unavailable mutations;
  their existence does not implement a web destination. Android phase2 functional checks do not
  certify wasm storage or cross-platform behavior.
- Today now uses Feed/Events/Trends on Android, with source management and live/saved filters in
  separate sheets. Web navigation, saved-feed integration and the initial AddCard set argument
  remain deferred. Shared news cards require caller-provided image fallbacks and text-bound
  contrast scrims; adaptive compact set cards preserve readable names at larger font scales.
  Common source-icon extraction does not implement web HTTP discovery or icon persistence;
  Android's bounded HTTPS client and targeted Room updates remain platform adapters.
- Import card/page refinement adds QUEUE presentation scope, global reverse paging and explicit per-entry retained Wishlist follow-up. Future web adapters must preserve owner/version guards, readonly completion and bounded pages; Android direct-add controls do not implement wasm storage or sync.
- Canonical Import Duplicate now requires independently editable durable rows with original-source lineage and an identity discriminator. Future web persistence must implement equivalent clone/rebuild/application semantics; shared repository fallback rejects unsupported duplication. Completion toast and retained inversion presentation are Android integration only during the pause.
