# Web debt from the gamification restoration

**Recorded:** 2026-09-25
**Status:** Deferred until the user explicitly resumes web implementation.
**Scope:** Web implications discovered while restoring Gamification, Profile, Friends, and Stats on Android. This document records work; it authorizes no web implementation or web build.

## Resume order

1. **Restore the web build baseline.** The previous `wasmJsBrowserDistribution` attempt was blocked by a `yarn.lock` drift and a non-exhaustive `webApp` `AuthViewModel` branch for `PasswordUpdatedSessionRevoked` (noted in the restore plan at commit `984bc927`). Recheck these against the current HEAD when web work resumes; do not assume the old failure still reproduces.
2. **Bring Friends onto the current search contract (F-20).** `WebFriendRepository` still uses `get_friend_collection`. That RPC filters against an empty `public.cards` table, uses OFFSET with a non-unique order, and can silently truncate its unbounded `all` result at PostgREST's row cap. Port web to the stable, paged `search_friend_cards` contract and preserve account/session cancellation and typed errors. After every supported client has moved, the backend owner may retire `get_friend_collection`; do not drop it while a shipped web client still calls it.
3. **Align web Friends behavior with the Android fixes.** A request that disappeared during accept/delete should refresh the list rather than leave a generic row error. Recheck F-24's shared-layer debt: the empty-id `Friend` search sentinel, referral-code errors collapsed to `null`, hard-coded invite host, UI types in ViewModels, and localized strings passed into VM methods. Some of these may already have changed since the 2026-09-24 audit; verify before porting.
4. **Choose a web gamification authority and storage model (G-21).** Shared use cases currently emit events on web into a bus with no engine subscriber. Web repositories deliberately skip some emitters, so web activity does not earn XP even when the same account syncs with Android. Decide whether the web engine is server-authoritative with a remote-first cache or has an IndexedDB-backed local store, and define ledger idempotency, account scoping, offline behavior, and sync conflict rules before adding an engine. Keep `FeatureFlags.Gamification.ENABLED` off during this work.
5. **Port presentation only after the engine contract is settled.** Profile gamification tabs, achievement/quest/reward UI, celebrations, and cosmetics remain Android presentation. Move eligible Compose code to CMP resources (`Res` instead of `R.string`), use multiplatform ViewModel/back handling, and isolate Android AGSL `RuntimeShader` behind a platform brush abstraction with a web fallback. Preserve the 12 MagicTheme palettes and accessible loading/error/empty states.
6. **Port Stats deliberately.** The P8 Android corrections for local-seat totals, decisive win rate, draws, bounded subscriptions, chart contrast, account-scoped trade stats, and immediate A→B UI reset are the behavioral reference. A retained screen must not replay A's state to B on navigation return. Web should use its own data source and lifecycle rather than copying Android Room/WorkManager code. Recheck the remaining P8 UI debt (S-22 bounded single items and partial LOW findings) when designing the web screen.

7. **Align advanced card color search when web work resumes.** Treat `M` as an additional two-or-more-distinct-colors condition on the selected card-color or color-identity field. Keep W/U/B/R/G comparison semantics independent of `M`, preserve panel expansion and immediate Clear behavior, and distinguish a confirmed empty Scryfall response from syntax or network errors. Web Friends still needs the paged `search_friend_cards` contract from step 2; its `M` behavior must agree with Android and the RPC. Archidekt remains exact-set W/U/B/R/G/C without `M`. Check double-faced cards explicitly: Scryfall color predicates can match different faces in one query, so `c>=2 c=w` is not logically empty even though front-face local colors are monowhite.

## Verification when web work resumes

- Build `wasmJsBrowserDistribution` from a clean, current dependency lockfile and run affected common/web tests.
- Confirm no Android/Java imports leak into `commonMain`; Android Room, DataStore, WorkManager, and AGSL stay behind interfaces or actuals.
- Exercise one account on Android and web: XP idempotency, account switch, sign-out, guest isolation, friend search pagination, expired friend requests, and trade stats refresh.
- Inspect NeonVoid and HallowedPrint, keyboard navigation, labels, touch/click targets, and loading/error/empty states.

## Source pointers

- `docs/plans/gamification-restore-plan.md` (P2, P5, P7, D14 and progress log)
- `docs/plans/audits-2026-09-24/friends-audit.md` (F-20, F-24)
- `docs/plans/audits-2026-09-24/gamification-drift-audit.md` (G-21 and KMP classification)
- `docs/plans/kmp-migration-plan.md` and `docs/plans/kmp-migration-progress.md`
