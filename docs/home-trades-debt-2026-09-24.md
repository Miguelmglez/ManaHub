# Home + Trades audit — open debt (2026-09-24)

Work merged from `claude/home-trades-audit-optimize-9a498e`: full edge-case audits of `feature/home` and
`feature/trades`, all client-side CRITICAL/HIGH/MEDIUM/LOW fixes, the Home loading/motion overhaul,
Community Decks format selector, Rules Tip roll button, widget gallery fix, double-back-to-exit, and the
shared UI component inventory in CLAUDE.md. Everything below was deliberately left open.

## 1. Release gates not run (do before any push/PR)
- [ ] **Pre-push security gate** (`pre-push-security-gate` skill → `android-security-auditor`) over the
      merged diff (`git diff ee7634be..<merge>`). Only a local regex secret-grep was run (no hits).
- [ ] **Telemetry review** (`crashlytics-ux-auditor`) of the new events/keys: `trade_ref_mismatch`,
      `home_community_decks_format_selected`, `home_rules_tip_rolled`, `home_layout_write_failed`,
      `home_pref_write_*`, `home_flow_<source>`, `app_exit_back_prompt_shown`, `home_widget_added/removed`;
      plus stale instrumentation from deleted code (Save draft, `WidgetLoading`, Rules Tip pager,
      `FriendAvatarRow`, `onStart(null)` flows).
- [ ] **On-device verification**: nothing was checked visually (no emulator). Check the Home staggered
      reveal, fixed slot heights (NeonVoid + HallowedPrint), Rules Tip roll, Card Detail return
      transition, widget gallery add/remove/move, double-back toast, trades screens.
- [ ] **`Migration54To55Test`** compiles but was never executed (instrumented). Regenerate the Room
      schema JSON locally.

## 2. Web parity
- [ ] `WebAuthRepository` still maps `SessionStatus.RefreshFailure` → `Unauthenticated` (Android fixed in
      92631436). Owner: `kmp-web-fullstack-dev`. An unverified partial implementation was discarded; its
      approach: keep `lastAuthenticated` (set/cleared in `.onEach` before `stateIn`); on `RefreshFailure`
      return `lastAuthenticated ?: auth.sessionManager.loadSession()?.toAuthenticatedState() ?:
      Unauthenticated`; never `currentSessionOrNull()` (null during RefreshFailure in supabase-kt 3.5);
      `catch (Throwable)` with `CancellationException` rethrown (wasm fetch failures are `kotlin.Error`).
      Verify with `:shared:core-data:compileKotlinWasmJs` + web distribution build.

## 3. Backend (Supabase) — needs `backend-supabase-expert` + user approval
- [ ] Trades H2(e): per-participant `collection_applied_at` on `trade_participants` so the "already
      applied" gate works across devices/reinstalls (today it is local Room only).
- [ ] Trades H4: reject `revoke_acceptance` once either party has marked completed.
- [ ] Trades H5: RLS on trade proposals lets the receiver read DRAFT rows (client "Save draft" was
      removed; legacy drafts render without actions).
- [ ] Trades M13: `create_proposal`/`counter_proposal` idempotency key (`p_client_request_id`),
      `SELECT … FOR UPDATE` in state RPCs, `IF NOT FOUND` on unknown ids.
- [ ] Trades M14: server-side validation that a referenced collection row belongs to the sender and
      matches the card.
- [ ] `resolve_shared_list` returns wishlist items without `quantity` (always ×1) and its "private"
      status only checks the owner still exists (no real sharing flag).
- [ ] The five gamification `*_changes_since` RPCs are still unpaginated (pre-existing, ADR-008).

## 4. Trades client leftovers
- [ ] L6: a composed proposal is lost on process death (needs a serializable `SavedStateHandle` model).
- [ ] H4 auto-apply of a pending collection change only runs while the trade screen is open; otherwise
      the "Update collection" button remains the fallback.
- [ ] Wishlist decrement after a trade runs after the Room transaction (remote-first order); a failure
      is logged and can leave one wishlist row stale.
- [ ] `FloatingActionButton` in `TradesScreen` has no shared equivalent in the component inventory.

## 5. Home leftovers
- [ ] L5: Trending shows commander deck counts with the cards icon — needs a `DeckItem` change.
- [ ] Dead/undecided code: `triggerActionRequiredNudge` (no callers → ACTION_REQUIRED nudge unreachable),
      `ResetLayout` (no UI trigger), `HomeAction.MoveWidget` (VM handler kept, UI no longer uses it).
- [ ] Widget gallery: if a layout write never echoes back (write failure), the sheet ignores external
      layout changes until reopened.
- [ ] Predictive back at the root loses the "go home" animation while the exit guard is armed (inherent).
- [ ] First-run hero reserves its slot while `firstStepsCompletionSeen == false`; one-time jump if it
      resolves to no steps. Completion card collapses via `animateItem` after 2 s (intended).
- [ ] Card Detail shared transition still uses `sharedBounds` (four other entries share the key).
- [ ] No unit tests for `UserPreferencesDataStore` Home keys (L8 cooldown expiry) — no test infra.
- [ ] Community Decks header "ALL FORMATS · POPULAR" can ellipsize the category on narrow phones.

## 6. Shared UI component gaps (app-wide; not changed to avoid regressions)
- [ ] `MagicSegmentedControl`: 44 dp (< 48 dp target), no tab/selected semantics, unselected text uses
      `textDisabled`.
- [ ] `EmptyState` subtitle uses `textDisabled` (low contrast).
- [ ] `SectionHeader` defaults the title to gold (callers must pass a color).
- [ ] `InlineErrorState` retry text target < 48 dp tall (button semantics added only).
- [ ] `CopyBadge` background `surfaceVariant` is ~1.1:1 on HallowedPrint.

## 7. Repository hygiene
- [ ] Committed code on `feature/deck-wizard` references files that exist only as **untracked** files in
      the main checkout (`app/.../app/update/`, `app/.../core/config/`,
      `core/online/.../StatusParsing.kt`, `RealtimeConnectException.kt`, `GameResultOrdering.kt`,
      `shared/core-domain/.../config/`, `.../update/`, `shared/core-ui/.../ForceUpdateScreen.kt`). A fresh
      clone/worktree does not compile until they are committed.
- [ ] Learnings not yet written to memory: supabase-kt `RefreshFailure` ≠ sign-out and
      `currentSessionOrNull()` is null during it; supabase-kt 3.5 `filter {}` with two top-level `or {}`
      blocks silently overwrite each other (nest under one `and {}`); `deleteSyncedNotIn(ids)` crashes
      past 999 SQLite bind args on minSdk 29 (batch 500); trade apply must decrement by copies, never
      `deleteCard(ref)`.
- [ ] `android-kotlin-architect` agent-memory `MEMORY.md` index is ~21 KB — compact it.
