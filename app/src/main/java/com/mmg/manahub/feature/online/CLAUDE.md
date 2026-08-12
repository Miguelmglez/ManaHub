### Online sessions
**Hidden for release (2026-07-14) via new `feature/online/presentation/OnlineFeatureFlags
.ONLINE_SESSIONS_ENABLED = false`** — code intact, only the online-specific UI (GameSetup's "Play
with friends", TournamentsSheet's online rows, `LobbyHost`/`LobbyJoin`/join-deep-link redirects) is
gated; local/offline same-device play and local tournaments are untouched. Flip back to `true` to
re-enable. **HTTP polling (3 s) is the primary mechanism; Supabase Realtime CDC is an optional fast-path** — never
a correctness dependency. Must-know:
- `startLobbyPolling()` runs unconditionally after create/join/resume; `connectAndObserve()` failure is
  silent (log only). **Never replace the participant list with a raw snapshot — always MERGE by id.**
- In-game actions are **broadcast-first** (Realtime) with DB persist as fallback. Guard every per-player
  broadcast handler against self-echo (`event.slotIndex != mySlotIndex`). Call `checkWinner()` after any
  handler that sets `defeated = true`.
- Session codes are **6-digit numeric** (`^[0-9]{6}$`). `disconnect()` must NOT `removeChannel()`.
  `FINISHED` must NOT set `isOnlineSessionAbandoned` (only `ABANDONED` does). Lobby skips disconnect when
  `gameLaunched`; `GameViewModel` owns the final disconnect. Local player always in BOTTOM slot.
- Connect order in game: disconnect stale → connect → snapshot → collect.
- **Guest access**: `LobbyHostViewModel` and `LobbyJoinViewModel` auto-sign-in anonymously (`authRepository.signInAnonymously()`) if `sessionState.value is Unauthenticated` before the first RPC call. Anonymous users have `AuthUser.isAnonymous = true`. GoTrue puts `is_anonymous` as a TOP-LEVEL claim on the session's JWT access token — **never** inside `app_metadata`/`user_metadata`, and supabase-kt's `UserInfo` DTO does not expose it at all — so it is decoded from the access token via the shared `decodeIsAnonymousClaim()` helper (`shared/core-common/.../core/common/SupabaseJwt.kt`, pure commonMain), applied in `AuthRepositoryImpl.toSessionState()` (the sole path feeding `sessionState`). A prior version of this code read `userInfo.appMetadata?.get("is_anonymous")`, which always evaluated false — see `feedback_auth_isanonymous_jwt_toplevel_claim` memory. `isAuthenticatedFlow` in `HomeViewModel` excludes anonymous users — they never see account-gated features. All 15 session RPCs are GRANT'd to both `authenticated` and `anon` roles. Never call `upsertUserProfile` for anonymous users — they have no `user_profiles` row. **This is now enforced at the DB level** (`public.handle_new_user()` guards `is_anonymous` and skips the INSERT entirely, since 2026-08-03 — before that fix it was only true by accident of Android client code never asking; a web client that did ask got a real row back). No `AFTER UPDATE` trigger exists or is needed: no code path in this app flips an existing anonymous `auth.users` row to permanent in place (`signInWithGoogle`/`linkGoogleIdentity`/`signUpWithGoogle` all mint a fresh identity via `supabaseAuth.signInWith`, never an in-place link from an anonymous session) — gamification's `reconcileOnSignIn` merges guest progress into the new account at the application layer instead. → memory: `feedback_handle_new_user_anonymous_guard`
- → memory: `project_online_sessions`, `feedback_online_lobby_snapshot`, `feedback_online_game_sync`,
  `feedback_online_lobby_bugs_2026-05-28`, `feedback_online_ingame_sync_bugs_2026-05-28`,
  `project_gamesetup_hub_refactor`, `project_online_guest_support`

