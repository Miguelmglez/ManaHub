# KMP Migration — Living Progress Tracker

**Single source of truth for "where are we / what's next".** A new session reads this +
`kmp-migration-plan.md` (master plan: decisions §2, rules §3, Android debt §4, web roadmap §5)
and resumes automatically.

Branch: `feature/kmp-migration` (Android-on-KMP phase, merged) → **`kmp-migration-web`** (current, web
phase, since 2026-07-30) · All Android/shared `.kt` → `android-kotlin-architect` ·
all web-target `.kt` → `kmp-web-fullstack-dev` · wasm/library gotchas → memory
`project_kmp_spike_findings`.

> This file was compacted 2026-07-04 (was 2469 lines). The full slice-by-slice history
> (every commit, verification detail, and closure rationale from 2026-06-20 → 2026-07-04) is in
> the git history of this file — do not re-derive it.

---

## How to resume
1. Read STATUS + NEXT STEP below.
2. `git branch --show-current` = `feature/kmp-migration`; check for uncommitted WIP (a dead agent's
   tree may already be green — run the gauntlet on it before discarding anything).
3. Execute NEXT STEP per the plan's §3 operating rules; verify; commit; update this file.

**Verify gauntlet** (after every slice; details in plan §3):
`:app:assembleDebug` green · touched `:shared:*:compileKotlinWasmJs` green ·
`:app:testDebugUnitTest` vs floor **1967 / 118 failed / 2 skipped** (failing-CLASS set must not
grow; 18 pre-existing failing classes are documented: online×2, voice, scanner, push×2,
collection/sync×5, auth, tagging×2, trades×4) · commonMain leak grep
`grep -rnP "import (androidx\.(?!compose)|android\.|java\.)" shared/*/src/commonMain` empty ·
inline-FQN + same-package-implicit grep after any type move · `--rerun-tasks` after cross-module
moves · `:shared:<m>:clean` if the wasmJs incremental cache corrupts after source deletion.

**Hard exclusions (untouched, still Hilt + Android Compose):** `feature/online`,
`core/voice` + in-game voice, `feature/scanner`, `core/nearby`. Never merge/push to `master`
without the user.

---

## STATUS (2026-08-05)

**Android-on-KMP: COMPLETE with a short debt tail. Web: W0 + W1 + W2a + W2b + W3a + W3b + W3c + W3d + W4a + W4b + W4c + W4d + W5b DONE — MVP screen list complete + responsive regression sweep clean. Web scope expansion round 1 (approved 2026-08-04): Settings + Profile + Add Card ALL DONE. Web scope expansion round 2 (approved 2026-08-04, second wave): Friends DONE, Trades DONE (with explicit, flagged follow-ups — proposal creation/counter item-picker, Mark Completed + collection sync, gift-trade dialog, Trade Suggestions UI). Email/password auth (sign-in, sign-up, password reset) DONE — real (non-guest, non-Google) accounts are now genuinely reachable via the web UI itself, not just SQL-seeded test fixtures. Card Detail completion (print/language switching, art variants, read-only tag display) DONE — tag EDITING explicitly deferred; confirmed there is NO rulings gap (Android has no rulings feature at all). Game/online sessions remain explicitly out of scope (`feature/online` hard-excluded from the whole KMP migration).**

- ✅ **Web email/password auth (sign-in, sign-up, password reset) — DONE** (2026-08-05, branch
  `kmp-migration-web`, commit `7c7317db`). `:webApp` previously supported ONLY anonymous guest
  sign-in (W2a) — Google OAuth is separately, deliberately deferred (needs external OAuth client
  credentials), but email/password needs no external credentials at all (Supabase's own built-in
  auth) and had simply never been built. User explicitly asked to confirm/add it.
  1. **New `WebAuthRepository`** (`shared/core-data` wasmJsMain) implements the full, already-
     commonMain `AuthRepository` interface (`shared/core-domain`) — the first web slice to route
     auth actions through the shared repository/use-case layer instead of calling
     `SupabaseClient.auth` directly (`AuthViewModel`'s W2a pragmatic shortcut). Real:
     `signInWithEmail`/`signUpWithEmail`/`signOut`/`getCurrentUser`/`resetPassword`/
     `updateNickname`/`updateAvatarUrl`/`signInAnonymously`/`sessionState`. Loud
     `UnsupportedOperationException` stubs (matching the established `WebCardRepository`/
     `WebDeckRepository` pattern): `signInWithGoogle`/`signUpWithGoogle`/`linkGoogleIdentity`
     (Google OAuth, still deferred) and `deleteAccount` (a real but lower-priority, out-of-scope-
     for-this-task Profile-adjacent follow-up). `sessionState` is JWT-metadata-only, NOT
     `user_profiles`-enriched like Android's — `ProfileScreen` remains the sole source of truth for
     the full profile view; see the class KDoc for the full rationale.
  2. **`AuthViewModel` rewired** to take `AuthRepository` + the already-commonMain
     `SignInWithEmailUseCase`/`SignUpWithEmailUseCase` (`shared/core-domain`) instead of a raw
     `SupabaseClient`. `AuthUiState` dropped its `Error` case — every action-specific failure now
     surfaces via a new `AuthFormState` (mode toggle, per-field values/errors, a neutral
     `infoMessage` slot for email-confirmation-pending/password-reset-sent, separate from the red
     `formError` slot) instead of overloading the session-status display.
  3. **`AuthScreen` UI**: a sign-in/sign-up toggle (two `MagicCtaButton`s in a `Row`, each
     `Modifier.weight(1f)` — the mandatory pattern per the W4d `MagicCtaButton`-unweighted-`Row`
     bug), email + password (with a visibility-toggle trailing icon) fields, confirm-password +
     nickname on sign-up, a "Forgot password?" link, and inline validation (empty fields, email
     format, password `>= 8` chars) — all client-side, zero network calls until valid. Typed
     `AuthError -> String` mapping mirrors Android's `feature.auth.presentation.AuthViewModel
     .toUiMessage()` copy (`strings.xml` `auth_error_*` keys) for cross-platform consistency
     without pulling Android string resources into `:webApp`.
  4. **Password reset and "email already registered" are BOTH deliberately enumeration-safe** —
     `resetPassword` always shows the same neutral "If an account exists for that email..."
     message regardless of whether the account exists (confirmed live: real vs. nonexistent email
     both returned HTTP 200 from `/auth/v1/recover`). Signing up with an email that's already
     registered-and-confirmed is NOT a distinct red error either — Supabase GoTrue's own anti-
     enumeration design returns a 200 fake-user response (no session, no error) for this case, so
     it renders as the SAME green "check your inbox" info message a genuine new sign-up gets.
     Confirmed via live testing + a direct SQL row-count check (still exactly 1 `auth.users` row
     for the email, no duplicate/mutation) — this is expected secure behavior, not a bug to "fix"
     by leaking account existence.
  5. **Only a simple "check your inbox" confirmation was built for both flows** — NOT the fuller
     password-recovery-link-landing flow (handling Supabase's recovery redirect URL to show a
     "set new password" form), which is genuinely more complex and explicitly out of scope for
     this slice; noted here as a deferred follow-up if that flow is ever requested.
  6. **A real, unrelated build blocker was hit and NOT fixed by this agent**: `shared/core-common`'s
     `expect fun sha256Hex` (new, uncommitted, from a concurrent Daily Puzzle work stream) had no
     `wasmJs` `actual` — broke the ENTIRE wasmJs compile graph for every agent on this branch. A
     sibling concurrent agent (working on `CollectionScreen`/`CollectionViewModel`) had already
     dropped a documented TEMPORARY LOCAL-ONLY stub (`input.hashCode().toString()`, explicit
     "deleted again once verification is done" comment) to unblock itself — this agent reused that
     existing stub rather than writing a competing real implementation or duplicating effort; it
     was gone again (deleted by its owner) by the time this slice's work was verified. Any future
     session should NOT assume a real wasmJs `sha256Hex` actual exists — check first.
  7. **CRITICAL process finding, not a code bug**: `git add <own files>` followed by a plain
     `git commit -m "..."` swept in FOUR files from a concurrent agent's own staged-but-uncommitted
     work (`CollectionScreen.kt`/`CollectionViewModel.kt`/`SettingsScreen.kt`/
     `SettingsViewModel.kt`) into this agent's commit — multiple agents in this multi-agent session
     share ONE git index/working tree, so staging is a shared, not per-agent, resource. Caught
     immediately via `git show --stat HEAD` after committing (habit worth keeping), fixed with
     `git reset --soft HEAD~1` (undoes the commit, keeps the index) +
     `git restore --staged <the 4 foreign files>` + re-commit with an explicit `git commit --only
     -- <exact file list>` (belt-and-braces: pathspec-scoped even if the index drifts again before
     the commit lands). **Rule for any future multi-agent session on a shared working tree**: verify
     `git status`/`git diff --cached --stat` immediately before EVERY commit, not just right after
     `git add` — another agent's concurrent `git add` can land in the gap between your own `add` and
     `commit`. Recorded in memory as a NEW, distinct hazard from the already-documented Gradle-
     cache-corruption one.
  8. **Verified live end-to-end in Chromium (Playwright) against the REAL Supabase project**
     (`uimogilwuixgkgfcfmyb`): signed up a brand-new test email+password (real disposable address
     pattern) → `/auth/v1/signup` 200, UI showed the green "check your inbox" message (this
     project has email confirmation enabled) → confirmed the account via a direct SQL
     `email_confirmed_at = now()` update (same seed/verify discipline as the Profile/Friends/Trades
     slices) → confirmed `user_profiles` row exists with the EXACT nickname typed in the form →
     signed in with the same email+password → `/auth/v1/token?grant_type=password` 200, session
     state correctly showed "Signed in as account" (not "guest") → opened Profile → showed the
     real nickname + server-generated game tag + Sign Out (proving `ProfileScreen`, built in an
     earlier slice, now genuinely reflects non-guest state through this new flow, not just guest/
     SQL-seeded testing) → signed out (`/auth/v1/logout` 204) → signed back in with the same
     credentials → succeeded → wrong password → clean red "Incorrect email or password." (400,
     no raw exception text) → empty-field / invalid-email-format / too-short-password validation
     all correctly blocked submission client-side with per-field red messages, zero network calls.
     Zero horizontal overflow at 375px/768px/1280px (375px screenshot confirmed the sign-in toggle
     row + bottom nav bar both render correctly, no clipping). `:app:assembleDebug` green;
     `:webApp:wasmJsBrowserDistribution` built successfully (fresh, not cached — a mid-verification
     `--rerun` attempt hit the documented concurrent-daemon `NoSuchFileException` race, recovered by
     retrying without `--rerun`); zero Android/commonMain source touched (100%
     `shared/core-data` wasmJsMain + `webApp` wasmJsMain) — no leak-grep risk this slice. Test
     account (`auth.users` + cascaded `user_profiles`) deleted afterward, confirmed 0 remaining rows.
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/` (this session's findings to be
  recorded there) and memory `project_kmp_spike_findings`.

- ✅ **Web Collection grouping — DONE** (2026-08-05, branch `kmp-migration-web`, commit
  `cc44441a`). Closes the gap flagged since the W3d/Settings-expansion slices (see the
  `CollectionGroupingMode` bullet above, now superseded): the mode was persisted and Settings-
  editable since W3a/the Settings slice but silently ignored by `CollectionScreen`, which always
  rendered a flat grid of raw `UserCardWithCard` entries.
  1. **Zero new domain work** — `List<UserCardWithCard>.groupByCard()` and
     `groupCollection(groups, mode)` (`shared/core-model`'s `CollectionGrouping.kt`/
     `CollectionCardGroup.kt`) were already pure, already commonMain, and already exactly the shape
     Android's own `CollectionViewModel` uses. `CollectionViewModel` now `combine()`s
     `observeCollection()` with `collectionGroupingModeFlow` and runs both functions on every
     emission of either, exposing `CollectionUiState.groups`/`sections`/`groupingMode`.
  2. **Side effect, not scope creep**: switching to `CollectionCardGroup` tiles means the screen now
     collapses same-(identity, set) copies into one tile with a combined quantity — matching
     Android's Collection screen — instead of one raw tile per DB row. List mode also now reuses
     `CardListItem`'s existing dedicated `CollectionCardGroup` overload directly (it already existed
     in `shared/core-ui`, unused until now) instead of the screen's own hand-rolled foil/price
     selection logic.
  3. **UI**: a `ManaHubSelector` "Group by:" row (the same generic dropdown component
     `GroupingFlowSelector`/the Deck Builder's grouping picker wraps) sits below the existing header
     Grid/List toggle. Section headers render label + `"$count · x$totalCopies"`, skipped entirely
     when `groupingMode == NONE` (the shared function still returns one blank-labelToken section for
     `NONE` — the screen renders its items flat with no header rather than branching the whole render
     path). Items are keyed `"${section.labelToken}|${item.groupKey}"` per `CollectionGrouping.kt`'s
     own documented TAG-multi-section contract. `collectionSectionLabel()` is a minimal, non-
     composable token→display-string resolver (no `TagDictionary` lookup for TAG, unlike Android's
     `collectionGroupLabel` — acceptable for an English-only app per the task brief) covering
     COLOR/CMC/SET/RARITY/TAG's raw tokens.
  4. **Settings' "Group collection by" section description updated** (`SettingsScreen.kt`) — no
     longer claims grouping is unapplied on Collection.
  5. **Verified live in Chromium (Playwright, real guest session)**: added 9 distinct cards spanning
     5 types (Creature/Instant/Sorcery/Artifact/Land), 5 colors, 4 CMC buckets, 3 rarities, and 4 sets
     via real search+tap-to-add. Confirmed real per-bucket headers + correct counts cycling through
     Type ("Creatures 2·x2" / "Instants 1·x1" / "Sorceries 3·x3"), Color ("White"/"Blue"/"Red" —
     resolved display names, not raw letters), Mana value ("1 mana"/"3 mana"/"4 mana" — driven by
     real Scryfall CMC data, not assumptions), Set (full set names, alphabetically ordered), and
     Rarity (Mythic > Rare > Uncommon, matching the shared `rarityWeight` order); Tag mode correctly
     falls back to a single "Untagged" bucket with no crash (no web card carries a STRATEGY tag yet).
     Confirmed Grid and List both render sections identically and both toggle cleanly with grouping
     active; confirmed 1280/768/375px are all overflow-free (no clipped headers/counts, bottom nav
     bar unaffected); confirmed switching back to `None` returns to the exact original flat layout.
     Zero console/page errors throughout. `:webApp:compileKotlinWasmJs` verified green in isolation
     (see finding 6 on the auth bullet above re: the concurrent `sha256Hex` blocker and the shared
     temporary stub); change is 100% confined to `webApp` wasmJsMain source files, so `:app` is
     structurally unaffected (not re-run this slice — no androidMain file touched).
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/` (this session's findings to be recorded
  there).

- ✅ **Web Card Detail completion — DONE** (2026-08-05, branch `kmp-migration-web`, commit
  `8f08c44a`). Completes the W4b Card Detail screen (built deliberately minimal/read-only) with
  print/language switching, art variants, and read-only tag display — task explicitly confirmed
  Android has NO rulings feature at all before dispatching, so no rulings gap exists to fill.
  1. **Zero new repository work for prints/languages/variants** — `CardRepository.getCardPrints`/
     `getLanguagePrints`/`getCardArtVariants` were ALL among the 13 real `WebCardRepository` methods
     since W3b. A single new generic `CardVersionPickerDialog` (`MagicAlertDialog` + `LazyColumn`,
     not `ModalBottomSheet` — no web screen had used that yet, so the already-mandatory dialog
     component was reused instead of introducing an unverified one) backs BOTH a prints/languages
     picker (merges `getCardPrints(name)` + `getLanguagePrints(setCode, collectorNumber)`, deduped
     by `scryfallId`) and an art variants picker (`getCardArtVariants(name)`) — one "pick a Card,
     navigate to its detail" component, not three bespoke ones. Selecting a row re-navigates to
     `CardDetailRoute` with the new `scryfallId` (a normal push, not a replace — a fresh
     `CardDetailViewModel` resolves everything for the new printing, tags included).
  2. **Tag DISPLAY required real wiring, not just a UI pass** — verified BEFORE assuming:
     `WebCardRepository.getCardById` does NOT populate `Card.tags`/`userTags` (Scryfall has no tag
     concept; that data comes from the separate Supabase `card_strategy_tags` table via the
     already-commonMain `CardStrategyTagsRepository`/`CardStrategyTagsRepositoryImpl`/
     `CardStrategyTagsRemoteDataSource` stack, previously unused by `:webApp`). Added ONE new file,
     `WebCardStrategyTagsCache` (`shared/core-data` wasmJsMain) — a plain session-scoped in-memory
     `CardStrategyTagsCache` actual (Room has no wasmJs target; mirrors `WebCardRepository`'s own
     `sessionCardCache` pattern) — and wired the existing `CardStrategyTagsRepositoryImpl` into
     `WebAppKoinModule` exactly like Android's `CardStrategyTagsKoinModule`, differing only in the
     cache actual. `CardDetailViewModel` calls `getStrategyTags(oracleId)` directly and renders
     `Found.tags` via the mandatory `CardTagChip` — deliberately does NOT reuse Android's
     `RefreshCardStrategyTagsUseCase` (it persists via `CardRepository.unionCardTags`, a Room-only
     stub on web that would silently no-op) and does NOT run the on-device fallback rule engine on a
     miss — both out of scope for a read-only display slice. Since `CardStrategyTagsRepositoryImpl`
     already resolves raw pipeline tag keys to `CardTag` objects internally (dictionary-miss
     TYPE-category fallback included), no `TagDictionary` handling was needed at the web call site.
  3. **Tag EDITING is explicitly deferred, not half-wired** — `WebCardRepository`'s
     `updateCardTags`/`unionCardTags`/`updateUserTags`/`updateSuggestedTags`/`confirmSuggestedTag`/
     `dismissSuggestedTag` remain untouched, still throwing their documented
     `UnsupportedOperationException`. This is a genuinely separate, deeper feature (an override
     repository + rule-syntax editor + the `custom_` key-prefix CRUD system per CLAUDE.md's tagging
     engine section) — a real follow-up if/when the user asks for it, not started here.
  4. **Verified live in Chromium (Playwright)**: searched "Sol Ring" → opened detail → confirmed 3
     real tag chips render with correct per-category coloring (Artifact/TYPE, Mana rock/ROLE, Ramp/
     ARCHETYPE) resolved from a real `GET .../card_strategy_tags?oracle_id=eq....` 200 response →
     opened the prints/languages picker (real `unique:prints` + `lang:any` Scryfall queries, 6+ real
     rows with thumbnails/set names/collector numbers/language flags, current printing checkmarked)
     → selected a different printing → URL changed to the new `scryfallId`, new art/price rendered,
     tags correctly re-resolved via the SAME `oracle_id` → opened the art variants picker (real
     `(game:paper)` query, distinct Secret Lair/other-set illustrations rendered) → confirmed zero
     horizontal overflow at 375px/768px/1280px including with a picker dialog open at 375px (dialog
     stays legible, text ellipsizes, no clipping) → confirmed COMPACT (stacked) vs. MEDIUM (rail +
     side-by-side) responsive split unaffected by the new header icon buttons. `:app:assembleDebug`
     green (UP-TO-DATE); diff confirmed to touch only `webApp` wasmJsMain (4 files) + one new
     `shared/core-data` wasmJsMain file — zero commonMain/Android leakage.
  5. **Repeated the now-established scratch-shim workaround** for the concurrent Daily Puzzle
     `sha256Hex` wasmJs-actual gap (see the auth bullet's finding 6) — created a temporary, never-
     committed `actual fun sha256Hex` stub to unblock `:webApp`/`:shared:core-data` wasmJs
     compilation and the `wasmJsBrowserDistribution` build, deleted it again immediately after each
     verification pass. Confirmed via `git status` before and after that no trace of it was staged
     or left behind.
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/` (this session's findings recorded
  there).

- ✅ **Web scope expansion round 2, Friends — DONE** (2026-08-05, branch `kmp-migration-web`,
  commits `ff64ade2` + `46b6b181`). First slice of the second scope-expansion wave (approved
  2026-08-04); Trades remains a separate, larger follow-up (its repository-layer situation was
  being investigated independently) and is NOT part of this slice — see the concurrent `web(trades):
  *` commits on this branch from a different work stream. Game/online sessions stay out of scope.
  1. **`WebFriendRepository`** (new file, `shared/core-data` wasmJsMain) implements all 19
     `FriendRepository` methods remote-first against the already-shared `FriendRemoteDataSource`
     (confirmed already commonMain before writing any code — no move needed, unlike prior slices).
     Same in-memory-cache-per-list pattern as `WebDeckRepository`/`WebUserCardRepository`; hydration
     driven by `SupabaseClient.auth.sessionStatus`. **Gamification event emission
     (`ProgressionEventBus.emit(ProgressionEvent.FriendAdded(...))` on accept) is deliberately
     skipped** — out of web v1 scope per the master plan, unlike Android's `FriendRepositoryImpl`.
     `getFriendCollection`/`getFriendStats`/`getFriendMatchHistory` are fully implemented (repository
     contract complete) even though nothing in the UI calls them yet — see the deferred scope below.
  2. **Core UI**: new `webApp/.../friends/{FriendsScreen,FriendsViewModel}.kt` — friends list with a
     Remove action, pending (incoming) requests with Accept/Reject, outgoing (sent) requests with
     Cancel, and a search-by-exact-game-tag add-friend flow. Reachable via a new "Friends" row on
     `AuthScreen` (same account-adjacent placement as Settings/Profile — the nav rail is already at 6
     top-level tabs, a 7th account-adjacent surface would crowd COMPACT's bottom bar further). New
     zero-arg `FriendsRoute` in `WebNavGraph.kt`.
  3. **Deliberately DEFERRED, not half-built**: a friend-detail view (`getFriendCollection`/
     `getFriendStats`/`getFriendMatchHistory` — a genuinely separate, bigger screen viewing another
     user's server-RLS-gated data) and the referral-invite flow (`acceptInvite`/`getMyShareUrl` — no
     natural UI home found this slice; Profile's own game-tag display was the most likely future
     insertion point but wasn't touched). Both documented in `FriendsScreen.kt`'s own KDoc as explicit
     follow-ups.
  4. **Three real UI bugs found and fixed via live two-account testing** (commit `46b6b181`): (a)
     `OutlinedTextField`'s `label` slot renders as a vertical single-letter stack on this CMP/wasmJs
     version — nobody else in `:webApp` had used `label` before; switched to the caption-above-field
     pattern Profile's `NicknameEditor` already proved working. (b) `MagicCtaButton`'s internal
     `fillMaxWidth()` (same class of bug as `project_w4d_home_screen.md`) hogs the whole row when
     placed unweighted next to ANY sibling, not just a weighted one — restructured 3 rows
     (search-result card, outgoing-request row, friend row) from a horizontal `SpaceBetween` Row to
     the vertical stack `PendingRequestRow` already used correctly, and weighted the search
     field/button pair (2f/1f). (c) `Friend.gameTag` already carries its own `#` prefix as stored
     (`#20FERA`) — displaying `"#$gameTag"` doubled it to `##20FERA`; fixed to render verbatim,
     matching Android's `FriendsScreen.kt` convention.
  5. **A real backend/test-fixture gap found, not a web-code bug**: every `friendships` RLS policy
     (insert/select/update/delete) requires `is_profile_complete()` — a throwaway Supabase test
     account seeded directly via SQL (the established `auth.users`/`auth.identities` + `crypt()`
     technique from the Profile-slice memory) has `user_profiles.profile_completed = false` by
     default, since the normal client-side "complete your profile" onboarding step never ran. First
     `sendFriendRequest` attempt failed with a real `403`/`new row violates row-level security policy
     for table "friendships"`, diagnosed via `get_logs` (service: postgres), fixed by setting
     `profile_completed = true` directly on the two test rows (test-fixture correction, not a client
     override of the invariant `feedback_auth_profile_completed_invariant` protects). **Any future
     test account seeded this way for a Friends/social feature must also set
     `profile_completed = true`, or every `friendships` RLS policy will reject it.**
  6. **Also found (live, unrelated) another concurrent-editing hazard, not a code bug**: this branch
     had a SECOND active work stream (Trades) landing commits during this same session
     (`04df0ec9`/`3b7a0dad`/`8302468c`) — two Gradle builds targeting the exact same `:webApp` module
     concurrently corrupted shared incremental-compilation caches (`ArrayIndexOutOfBoundsException`
     in `WasmIrFileMetadata`, `NoSuchFileException` on a klib's `linkdata/module`, "Storage ... is
     already registered"). Recovered each time via `./gradlew --stop` + clearing the AFFECTED
     module's own `build/classes/kotlin/wasmJs`+`build/kotlin/compileKotlinWasmJs` (never the whole
     repo's `build/`) + retry. When the OTHER stream's in-progress file had a genuine (non-
     corruption) compile error mid-edit, temporarily moved its whole package directory aside,
     built, then restored it immediately — same technique the testing conventions doc already
     documents for a broken test file, generalized to a broken concurrent-editing source file.
  7. **Verified live in Chromium (Playwright), TWO real (non-anonymous) Supabase accounts** — seeded
     via the same direct-SQL technique as the Profile slice (`auth.users`/`auth.identities` +
     `crypt()`-hashed password + `/auth/v1/token?grant_type=password`, both accounts' `game_tag`
     auto-generated by `handle_new_user`): account 1 searched account 2's exact game tag (found, real
     `user_profiles` row rendered) → sent a friend request (hit the RLS 403 above, diagnosed + fixed
     live) → retried, succeeded, "Sent requests (1)" appeared on account 1's OWN screen → **switched
     to a SEPARATE browser context for account 2** → "Pending requests (1)" showed account 1's real
     profile independently → clicked Accept → account 2's screen reactively showed "Friends (1)" →
     **brand-new browser context (fresh WASM boot, not a same-session reload) for account 2** →
     still "Friends (1)" (real server state, not optimistic) → **brand-new browser context for
     account 1** → also shows "Friends (1)" (bidirectional, confirmed independently on both sides) →
     account 1 clicked Remove → fresh reload on BOTH accounts confirmed "No friends yet" on both
     sides. Responsive: zero horizontal overflow at 375px/768px/1280px (375px screenshot confirmed
     the COMPACT bottom bar + full-width reflow). Test accounts + their friendship row fully deleted
     afterward (`auth.users` cascade confirmed 0 remaining rows in `auth.users`/`user_profiles`;
     `friendships` confirmed 0 remaining rows for either id). `:app:compileDebugKotlin` UP-TO-DATE
     (zero Android source touched — this slice is 100% `wasmJsMain`).
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/` (Friends slice findings recorded there
  this session) and memory `project_kmp_spike_findings`.

- ✅ **Web scope expansion round 2, Trades — DONE (with explicit, flagged follow-ups)** (2026-08-05,
  branch `kmp-migration-web`, commits `04df0ec9`/`3b7a0dad`/`8302468c`/`c48d7b64`/`39d680b4`/
  `be3598c2`). Second slice of the second scope-expansion wave, built concurrently with the Friends
  slice above (on the SAME branch/working tree, at the same time — see the concurrent-editing
  hazards note below). Verified context going in: `TradesRepository` (unusually, lives in
  `shared/core-data`, not `core-domain` like the other 4 trade repos — a pre-existing
  inconsistency, left as-is) and its full negotiation use-case suite were ALREADY commonMain;
  `TradeSuggestionsRepositoryImpl`/`SharedListsRepositoryImpl` were ALREADY commonMain and
  Room-free too (confirmed, but not bound in Koin this slice — see deferred list below).
  1. **`WebTradesRepository`** (`shared/core-data` wasmJsMain) — a near-direct port of Android's
     `TradesRepositoryImpl`, which was ALREADY Room-free for its proposal cache (a plain in-memory
     `MutableStateFlow<List<TradeProposal>>`); only swapped `CardDao` item-enrichment for
     `CardRepository.getCardsByIds` (same join-through-another-repository pattern as
     `WebUserCardRepository`/`WebFriendRepository`) and dropped the `TradeCompleted` gamification
     event entirely (nothing wired to emit into on web).
  2. **`WebWishlistRepository`/`WebOpenForTradeRepository`** — same in-memory-cache-instead-of-Room
     shape as `WebUserCardRepository`, porting Android's merge/dedup logic
     (`updateEntryWithMerge`'s branches, variant-tuple grouping) but dropping Crashlytics
     instrumentation and the "unsynced, deferred" fallback branch (every web write is remote-first
     with a real userId always available — guest sessions are real anonymous-auth sessions, never a
     true offline mode).
  3. **UI**: new `webApp/.../trades/{TradesScreen,TradesViewModel,TradeThreadScreen,
     TradeThreadViewModel}.kt`. `TradesScreen` is a 4-tab hub (Active/History proposal lists +
     minimal READ-ONLY Wishlist/Open-for-Trade lists) reachable via its OWN bottom-nav-rail tab
     (unlike Settings/Profile/Friends, which hang off the Account row) — Trades is a primary
     feature per the task brief's explicit call. `TradeThreadScreen` is the negotiation detail view:
     every version in a proposal chain, Accept/Decline (receiver)/Cancel-with-confirm-dialog
     (proposer)/Revoke acceptance, gated by `TradeStatus` + proposer-vs-receiver role — all reusing
     the already-shared negotiation use cases directly (`AcceptProposalUseCase` etc.), zero
     reimplemented business logic. Item counts on list rows are hydrated via a bounded fan-out of
     `TradesRepository.refreshItemsForThread` per distinct active root proposal id (same
     metadata-vs-items split CLAUDE.md's Home section documents for `hydrateTradeItemCounts`, more
     generous cap here — 20, not Home's ≤5 — since this is the dedicated screen, not a widget).
  4. **A dead Counter button was caught and removed, not left as a no-op** (commit `c48d7b64`):
     Counter was originally wired end-to-end in the ViewModel down to a `NavigateToCounter` event,
     but the actual navigation destination (the shared item-picker, needed by both Counter and
     brand-new-proposal creation) doesn't exist yet — the nav callback was a silently-inert `{ }`.
     Per CLAUDE.md's no-stub rule (Home widget board precedent), removed the button and the event
     machinery entirely rather than shipping a dead control. Two more live-testing fixes (commits
     `39d680b4`/`be3598c2`): the negotiation detail card had no visible status text for a terminal
     proposal beyond "no buttons shown" (reused `TradesScreen`'s `StatusBadge`, made `internal`) and
     an arrow glyph rendered as a tofu box on this headless-Chromium font config (swapped for a
     plain hyphen); a 375px screenshot also caught the 4-tab `TabRow` mid-word-wrapping "Wishlist"
     (same class of bug W5b's sweep found — DOM `scrollWidth` alone misses internal text-wrap
     issues), fixed with `maxLines=1` + ellipsis.
  5. **Deliberately DEFERRED, not half-built** (explicitly flagged, matching the task brief's own
     "ship what's solid" permission): creating a brand-new proposal from scratch (friend picker +
     own-collection item picker + counterparty's open-for-trade item picker — the single heaviest
     remaining piece); Counter (needs the identical item-picker); Mark Completed + automatic
     collection sync (`UpdateTradeCollectionUseCase` — needs a web analog of Android's
     `TradeCollectionSyncDao`); the "gift trade" (review-collection-only) warning dialog; Trade
     Suggestions UI (repository confirmed ready, just never wired); Wishlist/Open-for-Trade add/edit
     UI (no "add to wishlist" affordance exists anywhere on web yet, Card Detail included).
  6. **Verified live in Chromium (Playwright), TWO real (non-anonymous) Supabase accounts**: seeded
     via the same direct-SQL `auth.users`/`auth.identities` + `crypt()` technique as the Profile/
     Friends slices, plus 3 `trade_proposals`/`trade_items` rows inserted directly (proposal
     CREATION isn't implemented on web yet, so a `create_proposal`-shaped direct insert stood in for
     it — only the respond flow was under test). Bob (receiver on 2 proposals, proposer on the
     third) drove the real UI end-to-end: **Accept → Revoke** on P1
     (PROPOSED→ACCEPTED→REVOKED), **Decline** on P2 (PROPOSED→DECLINED), **Cancel-with-confirm-
     dialog** on P3 (PROPOSED→CANCELLED) — every transition independently re-verified via a direct
     `execute_sql` re-read of `trade_proposals.status`/`updated_at` afterward, never trusting only
     the canvas render (Compose for Web still renders to one opaque `<canvas>` with no DOM/ARIA
     tree — coordinate clicks read off screenshots + a server-truth SQL check remains the only
     verification shape available on this target). Real card names/art resolved correctly in the
     negotiation detail (Lightning Bolt / Llanowar Elves via a live Scryfall fetch). Responsive: zero
     horizontal `scrollWidth` overflow at 375/768/1280px; screenshots confirmed COMPACT bottom bar,
     MEDIUM collapsed rail, LARGE expanded rail all correct. Test accounts + proposals fully deleted
     afterward (`auth.users`/`user_profiles`/`trade_proposals` confirmed 0 remaining rows).
     `:app:compileDebugKotlin` untouched (zero Android source touched — 100% `wasmJsMain` +
     `shared/core-data` wasmJsMain).
  7. **Concurrent-editing hazard (same one Friends' own entry above documents, from this slice's
     side)**: two Gradle builds targeting the same `:webApp` module from two simultaneous agent
     sessions repeatedly corrupted the wasmJs incremental compiler cache
     (`ArrayIndexOutOfBoundsException` in `WasmIrFileMetadata`, `NoSuchFileException` on a
     dependency klib's `linkdata/module`). Recovered every time via `rm -rf
     <module>/build/classes <module>/build/kotlin` (never the whole `build/` — `build/dist` can be
     locked by a lingering static-file server from an earlier Playwright run) + retry with
     `--no-daemon -Dkotlin.compiler.execution.strategy=in-process`. Separately, the entire
     `web/trades/` directory (4 files, already committed) was physically deleted from disk mid-
     session by the Friends stream's own recovery technique (moving a sibling stream's directory
     aside to unblock its build) — recovered instantly via `git checkout HEAD -- <path>` since it
     was already committed. Reinforces `feedback_session_limit_wip_safety` doubly hard in a
     multi-agent shared-working-tree setup: the hazard isn't only an abrupt session cutoff, a
     concurrent sibling agent's own recovery step can collide with your in-progress edit too.
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/project_trades_hub_negotiation.md`.

- ✅ **Web scope expansion, Add Card (spotlight discovery) — DONE** (2026-08-04, branch
  `kmp-migration-web`, commits `6db47c9d` / `a28df9c4`). Third and LAST of the three approved
  slices (Settings → Profile → Add Card).
  1. **Not a new screen — extended the existing `CardSearchScreen`/`CardSearchViewModel` (W3b)**,
     per CLAUDE.md's own Add Card section: Android's `AddCardScreen` IS the search screen, with an
     idle-state (no query) spotlight/discovery grid rather than a separate destination. Building a
     second "Add Card" nav route would have duplicated Search and bloated an already 6-item nav
     rail — confirmed this reading BEFORE writing any code.
  2. **Zero new domain work** — `GetSpotlightFeedUseCase` (`shared/core-domain` commonMain) was
     already fully shared and depends only on `CardRepository` (real on web since W3b:
     `getPlayableSets`/`searchCardsPaginated`). Registered as a Koin `single` in
     `webAppKoinModule` and constructor-injected into `CardSearchViewModel` — the first
     domain-layer use case registered on web (every prior ViewModel called repository methods
     directly).
  3. **State/behavior**: `CardSearchUiState` gained `spotlightCards`/`isSpotlightLoading`.
     `loadSpotlightFeed()` fires once from `init` and again from the grid's footer item (a
     `LaunchedEffect(Unit)` that runs the moment the footer scrolls into composition — the same
     footer-trigger pattern Android's `SpotlightGrid` uses), appending each call's page and
     advancing `currentSpotlightSetIndex` via the use case's `nextSetIndex`. "Idle" is exactly
     `query.isBlank()` (this ViewModel has no separate filter state, unlike Android's
     `AddCardViewModel` which also gates on an advanced-search filter). `onQueryChange` now resets
     search state immediately when the query is cleared back to blank, so the screen falls back to
     the (already-cached) spotlight grid without requiring an explicit re-search.
  4. **UI reuses `CardSearchResultTile` exactly** — a spotlight card supports the identical
     add-to-collection (tap image) / view-details (overlay icon) gestures as a real search result,
     since spotlight cards are real, addable `Card`s. Camera-scanner FAB and language-selector
     sheet were deliberately NOT ported (`feature/scanner` is excluded from the whole KMP
     migration; no web language-switcher exists yet).
  5. **Verified live in Chromium (Playwright, real guest session via a saved `storageState`)**:
     landed on `#search` with an empty query → real shuffled grid of cards from one Scryfall set
     (`set:trc`) rendered with real card art (network-confirmed: `api.scryfall.com/cards/search?
     q=set%3Atrc...` then `cards.scryfall.io/.../*.jpg` 200s) → scrolled → next set (`set:trk`)
     fetched and appended → scrolled further → a THIRD set (`set:mbc`) fetched, confirming
     multi-page accumulation works, not just a one-shot next-page → typed "Lightning Bolt" and hit
     Enter → grid correctly REPLACED with 2 real fuzzy-matched search results (not appended
     alongside spotlight cards) → cleared the query → grid reverted to the SAME cached spotlight
     cards (TRC/TRK) with zero re-fetch (proves the in-memory `spotlightCards` accumulation
     persists across a query round-trip, not just the initial load) → tapped a spotlight tile's
     card art → "Added Kirk, Enterprising Captain to your collection." banner rendered +
     `batch_upsert_collection` RPC returned 204 — same round-trip proof pattern as every prior
     add-to-collection verification (W3d/W4d). Zero horizontal `scrollWidth` overflow at
     375/768/1280px, spot-checked visually at 375px (3-column COMPACT reflow, bottom nav bar,
     no clipping).
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/project_w4f_add_card_spotlight.md`.

- ✅ **Web scope expansion, Profile screen — DONE** (2026-08-04, branch `kmp-migration-web`, commit
  `12e6874b`). Second of the three approved slices (Settings → Profile → Add Card). Deliberately
  MINIMAL per the task brief: nickname edit, avatar DISPLAY only (no upload — needs a Storage
  bucket + file picker, out of scope), read-only game tag, sign out. Explicitly excludes every
  gamification surface (achievements/stats/level/cosmetics/`?tab=` deep link) — that's Android's
  `ProfileScreen.kt`/`ProfileViewModel.kt`'s scope, not web v1's.
  1. **Pure UI slice — zero new repository work**, same pattern as Settings: `UserProfileClient`
     (`shared/core-data`, already registered in `WebAppKoinModule.kt` since W2b) already had
     `fetchProfile`/`updateNickname`. New `webApp/.../profile/{ProfileScreen,ProfileViewModel}.kt`,
     reachable via a new "Profile" row on `AuthScreen` (shown only while `AuthUiState.SignedIn`),
     same "hangs off Account, not a new nav-rail tab" precedent as Settings. New zero-arg
     `ProfileRoute` in `WebNavGraph.kt`.
  2. **Anonymous guest sessions get a distinct `ProfileUiState.Guest`** — per CLAUDE.md's Online
     sessions invariant ("Never call `upsertUserProfile` for anonymous users — they have no
     `user_profiles` row"), the ViewModel decodes the JWT's `is_anonymous` claim (same
     `decodeIsAnonymousClaim` helper `AuthViewModel` uses) and skips the `fetchProfile` network call
     entirely for a guest, showing an explanatory message + Sign Out instead of attempting a fetch
     guaranteed to return nothing.
  3. **Discovered and fixed a genuine backend bug, not a web-code bug**, via live round-trip testing
     against a real (non-anonymous) Supabase account: `update_user_nickname`, `complete_user_profile`,
     and `get_profile_by_user_id` are `SECURITY INVOKER` (correct) but used `RETURNING *`/`SELECT *`
     against `public.user_profiles`, which has COLUMN-LEVEL (not table-level) grants — `email`/
     `referral_code` are deliberately NOT granted to `authenticated`/`anon`. Under INVOKER, `*`
     requires SELECT on every column for the calling role, so the statement failed with
     `42501: permission denied for table user_profiles` for ANY real signed-in user (Android
     included — same shared `UserProfileClient`) — invisible until now because guest-only sessions
     never call these RPCs. Delegated to `backend-supabase-expert`, fixed by narrowing
     `RETURNING */SELECT *` to an explicit column list matching `UserProfileDto`, verified live,
     `get_advisors` clean. See `.claude/agent-memory/backend-supabase-expert/
     feedback_invoker_star_vs_column_grants.md`.
  4. **Verified live in Chromium (Playwright)**: seeded a REAL (non-anonymous) test Supabase Auth
     user directly via SQL (`auth.users`/`auth.identities` + `crypt()`-hashed password, since Google
     OAuth is still deferred and there's no other way to reach a non-guest session), signed in via
     the real `/auth/v1/token?grant_type=password` endpoint to get a genuine GoTrue JWT, injected the
     resulting `UserSession` into a Playwright `storageState` (pre-populated localStorage BEFORE the
     WASM app boots — see the harness gotcha below). Confirmed: Loaded state shows the server-seeded
     nickname/game tag; edited the nickname through the real `OutlinedTextField`, saved, got "Saved."
     with the RPC-returned value; **independently re-read via raw SQL** (`select nickname from
     user_profiles`) to confirm server truth, not optimistic UI; loaded a **brand-new browser context**
     restored from the ORIGINAL (pre-edit) storageState and confirmed the new nickname still loads —
     proves real persistence across a cold app boot, not in-memory state. Verified guest state (no
     fetch, correct message). Verified sign-out: `auth.signOut()` cleared `localStorage`, navigated to
     `AccountRoute`, "Profile" row correctly disappeared. Responsive 375/768/1280px: zero
     `scrollWidth` overflow at any width. Test fixture (`auth.users` row) deleted afterward
     (cascade-confirmed 0 remaining rows in `auth.users`/`user_profiles`). `:app:assembleDebug`
     untouched (zero Android source touched this slice); leak grep on `shared/*/src/commonMain`
     empty (zero shared-module change).
  5. **Harness gotcha (testing-only, not a product bug)**: `page.goto(url + '#hash')` for a
     FRAGMENT-ONLY URL change on an already-loaded page is a same-document navigation in Chromium —
     it does NOT reboot the WASM module, so `page.evaluate(() => localStorage.setItem(...))` done
     AFTER the first page load has no effect on session state (the Supabase client already resolved
     its session before the injection). Always pre-populate `localStorage` via
     `browser.newContext({ storageState })` BEFORE the first `page.goto()` for this app, never
     `evaluate()` + a same-origin hash-only `goto()` after the fact.
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/project_w4e_profile_screen.md`.

- ✅ **Web scope expansion, Settings screen — DONE** (2026-08-04, branch `kmp-migration-web`, commit
  `69b3e31a`). User approved expanding the web MVP beyond the original master-plan screen list to
  add Settings → Profile → Add Card, in that priority order (Trades/Friends/Game/online sessions
  stay explicitly out of scope — `feature/online` isn't even KMP-migrated on Android yet). This
  slice is Settings, the first and smallest of the three.
  1. **Pure UI slice — zero new repository work.** `UserPreferencesRepository`
     (`WebUserPreferencesRepository`, W3a) already implemented every flow/setter the screen needed.
     New `webApp/.../settings/{SettingsScreen,SettingsViewModel}.kt`, reachable via a new "Settings"
     row on `AuthScreen` (not a 7th bottom-nav tab — the rail already carries 6 items and Profile/Add
     Card are still to come; matches Android's own `feature/settings/` UX precedent of hanging off
     the account surface). New zero-arg `SettingsRoute` in `WebNavGraph.kt`.
  2. **Exposes 4 of the interface's 9 setters as `FilterChip`/`FlowRow` pickers**: card search
     language (11 options), preferred currency, collection view mode, collection grouping mode.
     Deliberately did NOT build `setAppLanguage` (the enum has exactly ONE value, `ENGLISH` — the app
     is English-only per CLAUDE.md, and Android's own equivalent row is commented out for the same
     reason) or `setNewsLanguages` (News isn't built on web at all yet, CORS-deferred) — both would
     be dead UI. `saveLastPriceRefresh`/user-defined-tag setters also excluded (not settings-screen
     toggles per CLAUDE.md's backend-call-budget rule / not a web feature yet).
  3. **Two previously-inert preferences made genuinely observable, not just persisted:**
     - `CollectionViewMode` is now fully wired: `CollectionScreen` gained a header Grid/List toggle
       and a real LIST layout (`LazyColumn` of the existing general-purpose `CardListItem`, reused
       from the W4c Deck Editor precedent) — both the header toggle and the new Settings picker write
       through the same `CollectionViewModel.setViewMode` → `UserPreferencesRepository
       .saveCollectionViewMode`, so either surface reflects the other immediately.
     - `PreferredCurrency` was a genuinely DEAD setting on web before this slice —
       `CardListItem`/`CardGridItem` (`shared/core-ui`) already read `LocalPreferredCurrency`, but
       nothing on `:webApp` ever provided a non-default value. `App.kt` now provides it at the root
       from `preferredCurrencyFlow` via `koinInject`, mirroring Android's `MainActivity` pattern.
       Verified live: switching the Settings currency chip flips a real card's displayed price from
       `$1.44` to `1,78 €` instantly, on the already-rendered Collection list.
     - `CollectionGroupingMode` was exposed and persisted correctly (round-trips through a fresh
       browser/context restore) but was DELIBERATELY NOT consumed by `CollectionScreen` yet at this
       point in the slice — grouping needed a `CollectionCardGroup`-shaped collapsing step the web
       repository's raw `List<UserCardWithCard>` didn't have. **Closed 2026-08-05, commit
       `cc44441a` — see the dedicated STATUS entry below.**
  4. **Verified live in Chromium (Playwright)**: guest sign-in → Account → Settings (all 4 sections
     render, 11-language `FlowRow` wraps cleanly) → changed all 4 settings (Español / USD / List /
     Rarity) → `storageState` captured → **fresh browser + fresh context restored from
     `storageState`** deep-linked to `#settings` → all 4 selections pixel-identical (real reload
     persistence, not a same-session illusion) → Search → added a real card → Collection LIST view
     showed it with `$1.44` (USD-first, proving the currency wiring) → toggled header icon to GRID →
     Settings → switched currency to EUR → back to Collection (GRID mode still active, confirming the
     toggle round-trips) → toggled to LIST → price now `1,78 €`. Responsive: zero
     `scrollWidth`/`clientWidth` overflow and clean chip-wrapping (no mid-word/mid-number text
     corruption, the W5b lesson) at 375px/768px/1280px, visually confirmed via screenshot at each
     width. `:app:assembleDebug` UP-TO-DATE (zero Android source touched); leak grep on
     `shared/*/src/commonMain` empty (no shared-module change this slice at all).
  Full detail: `.claude/agent-memory/kmp-web-fullstack-dev/` (this session's findings to be recorded
  there) and memory `project_kmp_spike_findings`.

- ✅ **Web W5b (responsive regression sweep) — DONE** (2026-08-04, branch `kmp-migration-web`,
  commits `b6ba4989` + `a42e12bf`). Master plan §5 W5, reframed by the session's approved plan as a
  safety-net sweep (every screen was already gated at 375/768/1280px during its own W1-W4d slice) —
  swept all 8 real screens (Theme, Home, Account, Card Search, Card Detail, Decks, Deck Editor,
  Collection) at two edge widths never previously tested: 320px (iPhone-SE-class) and 1920px
  (ultra-wide desktop).
  1. **Cross-cutting fix (`b6ba4989`), affecting all 8 screens**: `AdaptiveScaffold`'s
     LARGE-breakpoint 1200.dp content clamp was a structural Compose modifier-ordering no-op
     (`.fillMaxWidth().then(widthIn(max=1200.dp))` — `fillMaxWidth` as the outer modifier locks the
     width before `widthIn` can cap it). Verified via a new DOM-ruler-overlay technique (precise
     pixel measurement, not eyeballing): at 1920px content spanned ~1656.dp instead of the
     documented ~1136.dp. Fixed by reordering (`widthIn` outer, `fillMaxWidth` inner). Also fixed:
     bottom-bar nav labels ("Collection", "Account") wrapped mid-WORD at 320px — added
     `maxLines=1`+ellipsis+center-align.
  2. **Deck Editor fixes (`a42e12bf`)**: the 3-chip Format row overflowed at 320px and
     `FilterChip`'s "Draft" label wrapped one CHARACTER per line; the per-card board row's
     price/quantity text wrapped MID-NUMBER (`"$1.4"`/`"4"`) since `CardListItem(weight(1f))` was
     squeezed against three full-48.dp `IconButton`s. Both fixed with
     `.horizontalScroll(rememberScrollState())` (touch targets kept at their full 48.dp — never
     traded away for layout fit); `weight(1f)` had to become a fixed `220.dp` width since
     `weight()` and `horizontalScroll` are mutually exclusive on the same Row (bounded vs. infinite
     max-width constraint).
  3. **Every other screen was clean at both widths**, verified with REAL populated-state data (a
     created deck, an added card), not just empty states — a full guest-sign-in → search → add →
     create-deck → deck-editor → home → card-detail loop run twice (320px, 1920px), zero
     `scrollWidth` overflow at any step. `:app:assembleDebug` UP-TO-DATE both times (zero Android
     source touched — pure `webApp/`/`shared/core-ui` commonMain layout code).
  4. **Reusable finding for future sweeps**: the page-level `scrollWidth`/`clientWidth` overflow
     check is necessary but NOT sufficient — both real bugs this slice were Compose-INTERNAL text
     corruption inside an already-bounded Row (a squeezed child wrapping text mid-word/mid-number),
     invisible to that DOM-level check. Only caught via visual screenshot inspection of every dense
     multi-child Row.
  Full detail: memory `project_kmp_spike_findings` (W5b addendum) and
  `.claude/agent-memory/kmp-web-fullstack-dev/project_w5b_responsive_regression_sweep.md`.

- ✅ **Web security-pass cleanup — DONE** (2026-08-04, branch `kmp-migration-web`, commits
  `0e5dc3bf`/`18ffd87d`/`2c888077`). Two MEDIUM findings from a full security pass on the web
  target, both in `kmp-web-fullstack-dev`'s domain, plus a related follow-up fix.
  1. **Finding 1 — leftover debug panel** (`0e5dc3bf`): `AuthViewModel`'s `_profileCheck`/
     `runProfileCheck()` (a W2b plumbing-proof smoke check) and its UI block in `AuthScreen.kt`
     rendered raw HTTP status/exception text on the real production Account screen. Removed
     entirely (not hidden) — 7+ real screens since (W3a-W4d) already prove the underlying
     Ktor+auth-header plumbing end-to-end. `AuthViewModel` no longer takes a `UserProfileClient`
     dependency.
  2. **Finding 2 — raw exception text in 9 UI sites** (`18ffd87d`): every web ViewModel did
     `_uiState.update { it.copy(error = e.message ?: "...") }`, leaking Ktor/Supabase SDK
     implementation detail. Added `Throwable.toUserFacingMessage(action, crashReporter)`
     (`:webApp`-local — Android has no established shared convention for this to match) at all 9
     confirmed sites (`AuthViewModel.signInAsGuest`, `CardSearchViewModel.addToCollection`, 6
     `DeckEditorViewModel` mutation methods, `DeckListViewModel.createDeck`). **Also fixed a real
     bug this uncovered**: on wasmJs a failed `fetch()` surfaces through Ktor's `Js` engine as
     `kotlin.Error`, not `kotlin.Exception` — the original `catch (e: Exception)` blocks never
     fired on a genuine network failure, so the UI silently showed nothing. Widened all 9 sites to
     `catch (e: Throwable)`. See memory `feedback_wasmjs_fetch_failure_is_kotlin_error`.
  3. **Follow-up — `isAnonymous` wiring** (`2c888077`): once `android-kotlin-architect` landed
     `decodeIsAnonymousClaim(accessToken)` in `shared/core-common` (commit `cac24add`, the shared
     fix for the `is_anonymous`-is-a-top-level-JWT-claim bug), wired `AuthViewModel.toUiState()` to
     use it instead of the always-empty `session.user.appMetadata` read. No new module dependency
     (`:webApp` already depends on `:shared:core-common`).
  All three verified live in Chromium (Playwright): Account screen clean with guest sign-in still
  working end-to-end (real `/auth/v1/signup` 200); happy paths unaffected (search, add-to-collection,
  deck create/rename/format-change/add-card, all via real Supabase network calls); a real forced
  offline failure (`context.setOffline(true)` + increment quantity) now shows "Couldn't update the
  quantity. Please try again." instead of failing silently; guest sign-in now correctly shows
  "Signed in as guest." instead of "Signed in as account."
  **Not this task** (being fixed in parallel, do not duplicate): the `user_profiles` RLS exposure
  HIGH finding (`backend-supabase-expert`).

- ✅ **Web W4d — DONE** (2026-08-04, branch `kmp-migration-web`, commit `aa2a8e0e`). Fourth and
  LAST W4 slice from the master plan's originally-scoped MVP screen list (Auth → Search/CardDetail
  → Collection → Decks/Deck Studio → News → reduced Home; News stays deferred, CORS blocker) — a
  real Home screen replacing the `ThemeShowcaseScreen` fallthrough the "Home" nav tab had used
  since W4a. Deliberately NOT a port of Android's 17-widget customizable Home board
  (gamification/trades/community-decks/first-steps, its own Room schema + DataStore layout) — per
  the same no-stub principle documented in CLAUDE.md's Home section, this screen ships only
  real, wired content.
  1. **New `webApp/src/wasmJsMain/kotlin/com/mmg/manahub/web/home/` (`HomeScreen.kt` +
     `HomeViewModel.kt`)**: a static greeting header (no MTG-flavored greeting-pool system), three
     quick-link buttons (Search/Decks/Collection), and two "recent" `LazyRow` strips (Recent Decks,
     Recently Added) capped to 5 items each. `HomeViewModel` is nothing more than a client-side
     `sortedByDescending{}.take(5)` over two ALREADY-real repository reads
     (`DeckRepository.observeAllDeckSummaries` since W3c, `UserCardRepository.observeCollection`
     since W3d, combined via `combine{}`) — zero new repository or backend work. `DeckSummary`/
     `UserCard` both already carry a `createdAt: Long` field, so no model change was needed either.
  2. **`WebNavGraph.kt`**: `HomeRoute`'s `composable<HomeRoute>{}` body now renders `HomeScreen`
     (previously `ThemeShowcaseScreen`, matching the pre-W4a fallthrough); `ThemeRoute` keeps its
     own `ThemeShowcaseScreen` untouched (the 12-palette picker stays a genuinely useful dedicated
     tab). Quick-link buttons/recent-item taps reuse the existing `navigateToTopLevel`/
     `navController.navigate(CardDetailRoute/DeckEditorRoute(...))` helpers already established in
     W4a/W4b/W4c — no new navigation mechanism.
  3. **Found + fixed a real `MagicCtaButton` (core-ui, commonMain) bug**, surfaced by this screen's
     first-ever use of 3 `MagicCtaButton`s side-by-side in a `Row`: the component's internal
     `CenteredButtonContent` applies its own `Modifier.fillMaxWidth()` to its content `Row`
     whenever it has text, and since a plain (non-weighted) `Row` gives every child the SAME
     unbounded max-width constraint, all 3 buttons independently tried to claim the full row width
     — only the first was visible, the other two rendered off-screen (verified live via screenshot
     before the fix: one giant "SEARCH CARDS" button, "My Decks"/"My Collection" nowhere on
     screen). Fixed LOCALLY in `HomeScreen.kt` with `Modifier.weight(1f)` per button in the
     MEDIUM+ branch (a `RowScope` extension, so the COMPACT-stack and MEDIUM+-row button lists are
     written out explicitly per branch rather than shared through one cross-scope lambda) — no
     `core-ui` component change, since this only surfaces under the specific "N buttons, unweighted
     Row" usage shape this screen introduced.
  4. **Verified live in Chromium (Playwright)**: fresh guest sign-in → created a deck ("New Deck",
     `batch_upsert_decks` 204) → searched "Lightning Bolt" → tapped to add to collection
     (`batch_upsert_collection` 204) → deep-linked reload at `#home` → both strips render REAL data
     ("New Deck / Casual · 0 cards" in Recent Decks; the Lightning Bolt card art + name in Recently
     Added, confirmed via `cards.scryfall.io` 200 + a full re-screenshot once the image settled) →
     all 3 quick-link buttons navigate correctly (`#search`/`#decks`/`#collection`) → the recent
     deck card navigates to `#deck/<id>` (DeckEditor renders) → the recent card tile navigates to
     `#card/<id>` (CardDetail renders, full mana symbol + prices). Responsive: zero horizontal
     overflow (`scrollWidth == clientWidth`, checked programmatically) at 375px (COMPACT: 3 buttons
     stacked full-width, bottom nav bar), 768px (MEDIUM: buttons evenly split via `weight(1f)`,
     collapsed icon rail), 1280px (LARGE: same split, expanded icon+label rail) — all visually
     confirmed via screenshot too, not just the overflow check. `:app:assembleDebug` green
     (UP-TO-DATE, confirming zero Android source touched); diff confirmed 4 files, all under
     `webApp/src/wasmJsMain/**` — zero Android/commonMain leakage, no repository or shared-module
     change needed this slice at all.
  Full detail: memory `project_kmp_spike_findings` (W4d addendum) and
  `.claude/agent-memory/kmp-web-fullstack-dev/project_w4d_home_screen.md`.

- ✅ **Web W4c — DONE** (2026-08-04, branch `kmp-migration-web`, commit `17ddc8d0`). Third W4
  slice — a deliberately minimal Deck Editor, scoped WAY down from Android's Deck Studio (2320-line
  screen + 1774-line ViewModel): mainboard/sideboard lists with quantities, add a card via a compact
  screen-local search, remove/decrement a card, rename the deck, change its format (curated 3-format
  subset: Commander/Casual/Draft, mirroring Android's `STUDIO_FORMATS`). Explicitly OUT of scope,
  not even a stub: Deck Doctor suggestions/analysis, the wizard/seed-build flow, playtest,
  import/export, warning overlays, land-suggestion auto-fill.
  1. **New `webApp/src/wasmJsMain/kotlin/com/mmg/manahub/web/deckeditor/` (`DeckEditorScreen.kt` +
     `DeckEditorViewModel.kt`)**. No new repository work needed — `DeckRepository`
     (`WebDeckRepository`, real since W3c) and `CardRepository` (`WebCardRepository`, real since
     W3b) already implement every method this screen calls. `DeckEditorViewModel` combines
     `observeDeckWithCards(deckId)` with its own local `Card` cache (warmed via
     `cardRepository.warmCacheForIds` + `getCardsByIds`, the SAME two-cache-join pattern W3d's
     `WebUserCardRepository` established, reused here one layer up at the ViewModel instead of a
     repository). `DeckFormat` (`shared/core-model` commonMain) is directly reusable — no new
     format enum needed. Card rows (both search results and mainboard/sideboard) reuse the
     existing general-purpose `CardListItem` overload (`core-ui/components/CardListItem.kt`) rather
     than a new tile type.
  2. **Second parameterized nav route**, `DeckEditorRoute(deckId: String)` → `#deck/<deckId>`, same
     path-segment encoding + dedicated deep-link prefix-strip branch pattern `CardDetailRoute`
     established in W4b — confirms that recipe generalizes cleanly to a second route with zero new
     surprises. `DeckListScreen`'s previously-dead deck rows now navigate into it via a new
     `onDeckClick` callback.
  3. **Responsive**: mainboard/sideboard stack vertically at COMPACT, render side-by-side at
     MEDIUM/EXPANDED/LARGE — mirrors `CardDetailScreen`'s stacked-vs-side-by-side split.
  4. **A genuinely new Playwright verification-methodology finding (not a code bug)**: synthetic
     `page.keyboard.type()` at default (near-zero-delay) speed immediately followed by a click can
     outrun Compose-for-wasmJs's TextField→state commit by one JS event-loop tick, producing a REAL
     one-character-short write — caught by checking the actual Supabase row via MCP `execute_sql`
     (not trusting a screenshot), root-caused, and confirmed fixed by adding either a typing delay
     or a settle wait before the commit click. No `.kt` change was made — this pattern
     (TextField + separate commit button reading `_uiState.value`) is the same one already used
     unincidentally by every other web screen this session. Full write-up + the DB-verification
     technique: memory `project_kmp_spike_findings` (W4c addendum).
  5. **Verified live in Chromium (Playwright)**: guest sign-in → Decks → "New deck" → tap-to-open
     the editor → format defaults to Casual (chip pre-selected, confirming the
     `DeckFormat.entries.firstOrNull { ignoreCase }` resolution) → searched "Lightning Bolt" → added
     a result to the mainboard (`upsert_deck_cards` + `batch_upsert_decks` both 204) → increment ×2
     / decrement ×1 (net ×2) → renamed (byte-correct, confirmed via direct DB read) → changed format
     to Commander (`batch_upsert_decks` 204) → removed the card entirely (`upsert_deck_cards` 204,
     mainboard back to 0) → captured `storageState` + the `#deck/<id>` URL → fresh browser + fresh
     context deep-linked directly to that URL (never clicked a tab) → real network re-fetch
     (`get_deck_changes_since`/`get_deck_cards_for_deck`, both 200) → identical deck (name,
     Commander format, 1-card mainboard) renders. Zero horizontal overflow confirmed
     programmatically (`scrollWidth == clientWidth`) at 768px and 375px, plus visual confirmation of
     the COMPACT-stacked vs. MEDIUM/LARGE-side-by-side split and correct nav-chrome switching at all
     three breakpoints. `:app:assembleDebug` green; diff confirmed `:webApp`-only — zero
     Android/commonMain leakage, no repository or shared-module change needed this slice at all.
  Full detail: memory `project_kmp_spike_findings` (W4c addendum) and
  `.claude/agent-memory/kmp-web-fullstack-dev/project_w4c_deck_editor.md`.

- ✅ **Web W4b — DONE** (2026-08-04, branch `kmp-migration-web`, commits `bff1b863` + `d93033b8`).
  Second slice of W4 — a deliberately minimal, READ-ONLY Card Detail screen, scoped WAY down from
  Android's 2713-line `CardDetailScreen.kt`: image/name/mana cost/type line/oracle text/prices ONLY.
  No tag editing, no language/set/print switching, no art variants, no rulings, no
  add-to-collection-from-detail (that stays on Search per W3d) — an explicit MVP boundary, not
  incomplete work.
  1. **New `webApp/src/wasmJsMain/kotlin/com/mmg/manahub/web/carddetail/` (`CardDetailScreen.kt` +
     `CardDetailViewModel.kt`)**. `CardDetailViewModel` takes `scryfallId` as a Koin runtime
     parameter (`params.get()`/`parametersOf(scryfallId)`, not a `SavedStateHandle` — `:webApp` has
     no `koin-androidx-compose`) and calls `CardRepository.getCardById` (already real on
     `WebCardRepository` since W3b — zero new repository work). Reused existing `commonMain`
     `core-ui` components: `CardName`, `ManaCostImages`, `OracleText`, `MagicCard`. Responsive:
     stacked column at COMPACT, fixed-width-image + scrollable-text-column side-by-side at
     MEDIUM/EXPANDED/LARGE.
  2. **First PARAMETERIZED nav route in `WebNavGraph.kt`** — every W4a route was zero-arg.
     `CardDetailRoute(val scryfallId: String)` encodes as a URL PATH SEGMENT (`#card/<scryfallId>`,
     confirmed live), not a query string. The W4a `ROUTES_BY_SERIAL_NAME` exact-match deep-link
     parser doesn't generalize to this shape — added a dedicated `"card/"`-prefix branch alongside
     it. `NavBackStackEntry.toRoute<T>()` resolves cleanly (unlike `hasRoute<T>()`, broken per W4a).
  3. **Wired from both existing entry points, preserving prior gestures.** Search result tiles keep
     their W3d tap-to-add-to-collection gesture on the card image unchanged; a NEW small overlay
     "view details" icon button (distinct tap target, Material's default `IconButton` sizing already
     clears the 48dp touch-target rule) opens detail instead. Collection tiles had no competing
     gesture, so the tile image itself now opens detail directly.
  4. **Found + fixed a real Coil3/wasmJs gap surfaced by this screen's mana-cost row**:
     `ManaCostImages`/`OracleText` load Scryfall SVG mana symbols via Coil3, but the SVG bytes
     fetched (200) and never rendered — `:webApp` was missing the `coil-svg` decoder artifact
     (dependency-only fix, delegated to `android-kotlin-architect` per this agent's `.gradle.kts`
     domain boundary, commit `d93033b8`). **No `.kt` wiring needed** — confirmed live that Coil3's
     wasmJs `ServiceLoaderTarget` auto-registers the decoder once the artifact is a direct
     dependency, narrowing the earlier W0 finding that explicit `setSingletonImageLoaderFactory`
     wiring was required.
  5. **Verified live in Chromium (Playwright)**: search "Lightning Bolt" → add-to-collection tap
     unchanged (204) → info-icon tap → `#card/<id>`, full render (name/type/oracle text/`{R}` mana
     symbol/all 4 USD-EUR-foil prices) → back → `#search` → Collection tab → tile tap → same card's
     detail → `storageState` captured → fresh browser + fresh context deep-linked directly to the
     captured `#card/<id>` URL → same card resolves, zero console errors. Zero horizontal overflow
     at 375/768/1280px; visually confirmed the COMPACT-stacked vs. MEDIUM+-side-by-side split.
     `:app:assembleDebug` green; diff confirmed to touch only `webApp/src/wasmJsMain/**` (+ the one
     delegated `.gradle.kts` line) — zero Android/commonMain leakage.
  Full detail: memory `project_kmp_spike_findings` (W4b addendum) and
  `.claude/agent-memory/kmp-web-fullstack-dev/project_w4b_card_detail_screen.md`.

- ✅ **Web W4a — DONE** (2026-08-04, branch `kmp-migration-web`, commit `9c79f7eb`). First slice of
  W4 (navigation + screens), **deliberately scoped down from the master plan's original W4
  description**: this slice gives `:webApp` its OWN real navigation (back stack + browser URL
  routing) — it does NOT touch Android's live production nav graph
  (`app/src/main/java/com/mmg/manahub/app/navigation/Screen.kt`/`AppNavGraph.kt`, still on the
  Android-only `androidx.navigation:navigation-compose`) and does NOT attempt the
  42-route-table-unification originally sketched in the master plan §5 W4 — that remains an
  explicitly DEFERRED, separate, opt-in future task, not started, not scheduled.
  1. **New catalog dependency**: `org.jetbrains.androidx.navigation:navigation-compose:2.9.2`
     (catalog key `navigation-compose-multiplatform`, distinct from the pre-existing Android-only
     `navigation-compose` alias — never wired into any Android-consumed module). Latest stable
     (non-alpha) release at pin time.
  2. **New file `webApp/src/wasmJsMain/kotlin/com/mmg/manahub/web/navigation/WebNavGraph.kt`**:
     6 type-safe `@Serializable`/`@SerialName`-annotated route objects (`home`/`search`/`decks`/
     `collection`/`theme`/`account`) mirroring the existing 6 `AdaptiveScaffold` nav items 1:1 (no
     new/removed destinations — `Home` and `Theme` intentionally still render the same
     `ThemeShowcaseScreen`, matching the pre-W4a fallthrough). `App.kt`'s old
     `selectedNavIndex`-`when` placeholder is gone; nav selection state now derives from
     `NavDestination.route` string-matched against each route's `serializer().descriptor.serialName`
     (the reified `NavDestination.hasRoute<T>()` convenience did NOT resolve on this
     artifact/version — see memory). Browser URL routing via
     `NavController.bindToBrowserNavigation()` (`@ExperimentalBrowserHistoryApi`, stable since CMP
     1.9.0 — supersedes the older deprecated `Window.bindToNavigation()`).
  3. **Two real bugs found and fixed in the same slice** (both documented in memory, not just
     patched silently): (a) the default `bindToBrowserNavigation()` does NOT replay the page's own
     initial URL fragment into a matching destination on load — fixed by parsing
     `window.location.hash` and pre-navigating before binding; (b) that pre-navigate step raced
     `AdaptiveScaffold`'s `movableContentOf`-wrapped `NavHost` region and threw a
     silently-swallowed (console.error only, no `pageerror`) `setGraph()`/`getGraph()`
     `IllegalStateException` that permanently blanked the page on a fresh deep-link load — fixed by
     gating on `currentBackStackEntryFlow.first()` before touching `navigate()`/`.graph`.
  4. **Verified live in Chromium (Playwright)**: tab clicks update the URL fragment
     (`#home`→`#search`→`#decks`→`#collection`); browser back×3/forward×1 walk the stack correctly
     matching the click history; a full page reload at a deep URL (`.../#search`, fresh browser +
     fresh context) renders the correct screen (Card Search, not Home) with zero console errors —
     the actual NEW capability this task adds. Full checkpoint-3 regression: guest sign-in → search
     "Lightning Bolt" → add to collection (`batch_upsert_collection` 204) → appears in Collection →
     Decks tab → "New deck" (`batch_upsert_decks` 204) → **deep-linked reload at `.../#decks` from a
     brand-new browser+context (restored via Playwright `storageState`, not a tab click)** →
     `get_deck_changes_since`/`get_deck_cards_for_deck` re-fire for real → the SAME deck reappears.
     Responsive: zero horizontal overflow (`scrollWidth == clientWidth`) and correct nav-chrome
     switching (bottom bar / collapsed rail / expanded rail) confirmed unaffected at
     375px/768px/1280px. `:app:assembleDebug` BUILD SUCCESSFUL (pre-existing warnings only);
     commonMain leak grep clean (this slice touches no `commonMain`/Android source at all).
  Full detail: memory `project_kmp_spike_findings` (W4a addendum) — includes the klib-inspection
  technique used to confirm the exact `bindToBrowserNavigation` package/signature before writing any
  import, since this is the first use of `navigation-compose-multiplatform` in the project.

- ✅ **Web W3d — DONE** (2026-08-03, branch `kmp-migration-web`, commits `dbfc3b23` + `04a3468c` +
  `56e6228c`). Fourth W3 "web data layer, one repo per slice" task — `UserCardRepository`
  (collection). Same remote-first-no-local-staging shape as W3c (`UserCardRepository`'s own KDoc:
  "local CRUD only, `SyncManager` owns push/pull"), not W3b's online-first-search shape.
  1. **Moved `CollectionRemoteDataSource`/`SupabaseCollectionDataSource`/`UserCardCollectionDto`
     from `:app` to `shared/core-data` commonMain** (commit `dbfc3b23`, same pattern as W3c's
     `DeckRemoteDataSource` move) — Room-entity mapping extensions
     (`toEntity`/`toDto`) stayed in `:app` (renamed `UserCardCollectionMappers.kt`). Also added a
     NEW `mergeEntry(...)` method to the interface, backed by a NEW Supabase RPC,
     `merge_collection_entry(p_entry_id uuid, p_new_scryfall_id text, p_is_foil boolean,
     p_condition text, p_language text, p_quantity integer) RETURNS boolean` (added by
     `backend-supabase-expert` this slice) — the existing `batch_upsert_collection` RPC looked like
     it already did collision-merging but was solving a different problem (sync-push cross-device
     races, not a single-entry re-point) and would have left a stale/duplicate row in two of the
     four required branches; verified by reading its live SQL body before requesting a new RPC.
     `merge_collection_entry` was verified against 7 scenarios inside a ROLLBACK transaction,
     `get_advisors` clean, `GRANT EXECUTE` to `authenticated` only (an anonymous-signed-in guest
     already has Postgrest role `authenticated`, not `anon` — corrected an initial
     over-instruction to also grant `anon`, which would have been harmless but inconsistent with
     the sibling collection RPCs). Verified `:app:assembleDebug` + `SyncManagerTest`/
     `CollectionSyncTest` green (community-decks WIP test files moved aside/restored per
     convention), `:shared:core-data:compileKotlinWasmJs`/`compileAndroidMain` green, commonMain
     leak grep clean.
  2. **`WebUserCardRepository`** (new file, `shared/core-data` wasmJsMain, commit `04a3468c`) — all
     11 `UserCardRepository` methods implemented. Two in-memory caches: `entriesCache` (ALL
     collection rows including soft-deleted — kept, not dropped, so `addOrIncrement`/
     `decrementOrRemove` can revive a soft-deleted row at a target tuple instead of duplicating it)
     and `cardsCache` (resolved `Card` data via the EXISTING `CardRepository` singleton —
     `WebCardRepository` — reusing W3b's Scryfall stack rather than duplicating it). Hydration
     driven by `SupabaseClient.auth.sessionStatus`, per the W3c async-hydration fix.
     `updateEntryWithMerge` calls the new RPC then does a full re-pull rather than re-deriving the
     merge branch client-side. Open-for-Trade re-pointing (part of Android's Room transaction) is a
     documented Web v1 gap — no web Trades feature exists yet. Compiled clean via
     `:shared:core-data:compileKotlinWasmJs` (hit + fixed the documented stale-incremental-klib-cache
     `ArrayIndexOutOfBoundsException` with `--rerun --no-build-cache`).
  3. **`:webApp` wiring + minimal Collection screen** (commit `56e6228c`) — `WebAppKoinModule.kt`
     registers `single<CollectionRemoteDataSource>`/`single<UserCardRepository>` (interface-typed).
     New 4th-of-6 `AdaptiveScaffold` nav item "Collection" → `CollectionScreen`/
     `CollectionViewModel` (read-only, `observeCollection()` rendered via the existing
     `AdaptiveCardGrid`, mirroring `DeckListScreen`'s scope discipline). Card Search's previously
     no-op "tap a result" now calls `UserCardRepository.addOrIncrement(...)` via
     `CardSearchViewModel.addToCollection`, proving the write path too. **Verified live in Chromium
     (Playwright)**: guest sign-in → search "Lightning Bolt" → tap the result → `batch_upsert_collection`
     RPC returns 204 → card appears in Collection ("Lightning Bolt x1") → full page reload (fresh
     Koin graph, fresh `WebUserCardRepository` instance) → `get_collection_changes_since` re-fires →
     the SAME card reappears — proving the write landed in Supabase, not an optimistic local-only
     illusion. Zero horizontal overflow at 375px (COMPACT bottom bar) / 1280px (LARGE expanded
     rail). Re-confirmed the "re-probe nav rail coordinates after adding a new `AdaptiveNavItem`"
     lesson from prior slices.
  Full detail: memory `project_kmp_spike_findings` (W3d addendum) and
  `.claude/agent-memory/kmp-web-fullstack-dev/project_w3d_collection_repository.md`.

- ✅ **Web W3c — DONE** (2026-08-03, branch `kmp-migration-web`, commits `8a91b518` + `f0f6d0ee` +
  `720a9cb0`). Third W3 "web data layer, one repo per slice" task — `DeckRepository`. Different
  architecture note from W3a/W3b: `DeckRepository`'s own KDoc documents it as **local-CRUD-only by
  design on Android** ("Sync is NOT part of this interface — `SyncManager` owns the push/pull
  cycle"). Web has no Room and no separate sync engine, so `WebDeckRepository` had to implement the
  CRUD contract by talking to Supabase directly and immediately, not by porting the Android
  repository's semantics.
  1. **Moved `DeckRemoteDataSource`/`DeckSyncDto`/`DeckCardSyncDto`/`SupabaseDeckDataSource` from
     `:app` to `shared/core-data` commonMain** (commit `8a91b518`) — the prerequisite Android-side
     move, since Android's `SyncManager` and the new web repository needed to share ONE Supabase-
     calling implementation instead of duplicating it. The Room-entity mapping extensions
     (`toEntity`/`toDto`/`toSyncDto`, which import `DeckEntity`/`DeckCardEntity`) stayed in `:app`
     (renamed file `DeckEntityMappers.kt`, Room has no wasmJs target). `SupabaseDeckDataSource` lost
     its Hilt `@Inject`/`@Singleton` (Hilt is androidMain-only) and now takes a commonMain
     `DispatcherProvider` instead of the `@IoDispatcher` qualifier; `RepositoryModule`'s `@Binds` was
     replaced by a manually-constructed `@Provides` in `SharedDomainUseCaseModule`, mirroring the
     pre-existing `provideScryfallRemoteDataSource` pattern. `SyncManager`'s push/pull behavior is
     byte-identical — verified via `:app:assembleDebug` green + `SyncManagerTest`/`CollectionSyncTest`
     green (ran with the two pre-existing, unrelated community-decks WIP test files temporarily
     moved aside, since the test source set compiles as one unit — restored after).
  2. **`WebDeckRepository`** (new file, `shared/core-data` wasmJsMain, commit `f0f6d0ee`) — all 15
     `DeckRepository` methods implemented against the shared `DeckRemoteDataSource`, remote-first
     with NO local staging: every mutation calls a Supabase RPC directly and immediately. Reactivity
     via in-memory `MutableStateFlow` caches (decks + per-deck card slots), but with ONE documented
     divergence from the W3a/W3b caches: hydration is genuinely async (a real network round-trip,
     not `LocalStorageKeyValueStore`'s zero-suspension-point synchronous path), so it's driven by
     `SupabaseClient.auth.sessionStatus` transitioning to `Authenticated` rather than a one-shot
     `init` fetch — avoids a race against the also-async session restore from `localStorage` on a
     fresh page load. Card-slot mutations (add/remove/move/clear/replaceAllCards) all funnel through
     one read-modify-write pair against the RPC's full-replacement semantics (there's no
     single-card-add RPC). `updateDeckAttribution`/`updateArchetypeOverride`/`updateTribeOverride`
     are documented loud stubs (`UnsupportedOperationException`) — NOT a web limitation:
     `DeckSyncDto` carries no synced column for these fields on ANY platform (Android's own
     `SyncManager` never pushes them either), and there's no web consumer yet either.
     `updateStrategyLocked` is the one exception (real synced column, fully implemented).
     `DeckSummary.colorIdentity`/`coverImageUrl` are documented Web v1 gaps (empty set/null — no
     local card cache to join against, unlike Android's Room-backed `DeckSummaryRow`). Compiled
     clean via `:shared:core-data:compileKotlinWasmJs` + `:compileAndroidMain` (hit the documented
     stale-incremental-klib-cache `ArrayIndexOutOfBoundsException` on the first wasmJs attempt after
     adding the file — fixed with `--rerun --no-build-cache`).
  3. **`:webApp` wiring + minimal Deck List screen** (commit `720a9cb0`) — `WebAppKoinModule.kt`
     registers `single<DeckRemoteDataSource> { SupabaseDeckDataSource(...) }` and
     `single<DeckRepository> { WebDeckRepository(...) }` (interface-typed). New 5th `AdaptiveScaffold`
     nav item "Decks" → `DeckListScreen`/`DeckListViewModel` — deliberately NOT Deck Studio (no card
     editing, no format picker, no detail nav), sole purpose is proving the repository works
     end-to-end. **Verified live in Chromium (Playwright)**: guest sign-in → Decks tab → "New deck" →
     `batch_upsert_decks` RPC returns 204 → deck appears immediately ("New Deck / Casual · 0 cards")
     → full page reload (fresh Koin graph, fresh `WebDeckRepository` instance) → `get_deck_changes_since`
     + `get_deck_cards_for_deck` re-fire over the network → the SAME deck reappears — proving the
     write landed in Supabase and the read-back is real, not an optimistic local-only illusion.
     Spot-checked 375px (COMPACT bottom bar) and 1280px (LARGE expanded rail) for the Decks screen —
     zero horizontal overflow at either width, empty state renders cleanly at both extremes.
  Full detail: memory `project_kmp_spike_findings` (W3c addendum, pending) and
  `.claude/agent-memory/kmp-web-fullstack-dev/project_w3_repo_slice_pattern.md`.

- ✅ **Web W3b — DONE** (2026-08-03, branch `kmp-migration-web`, commits `ddcfa689` + `4e23d61a`).
  Second W3 "web data layer, one repo per slice" task, and the FIRST slice that backs a real
  product screen (Card Search), not a showcase. `CardRepository` is a 27-method interface; per
  the scoping decision recorded in the task brief (master plan §2.1: web is online-first, no
  local card database, Collection/Deck Studio/tagging not yet on web), it was split rather than
  faithfully ported.
  1. **`WebCardRepository`** (new file, `shared/core-data` wasmJsMain,
     `repository/WebCardRepository.kt`) implements the 13 real search/lookup methods
     (`searchCardByName`/`searchCards`/`searchCardsPaginated`/`getCardById`/
     `getCardBySetAndNumber`/`getCardPrints`/`getCardArtVariants`/`getLanguagePrints`/
     `getCardByExactName`/`searchWithRawQuery`/`getPlayableSets`/`getCardsByIds`/`observeCard`/
     `warmCacheForIds`) by delegating directly to **`ScryfallRemoteDataSource`** — already
     `commonMain`, already wraps the shared `ScryfallRequestQueue` rate limiter +
     `ScryfallCache` TTL/dedup cache Android uses, so this slice reuses it as-is rather than
     re-implementing rate-limiting/caching against the raw `ScryfallClient` (a simpler shape
     than the task brief anticipated, since it assumed a from-scratch client wrapper). On top,
     a plain in-memory `MutableStateFlow<Map<String, Card>>` session cache backs `observeCard`'s
     `Flow` and `getCardsByIds`'s "local, no network fetch" contract — the web analogue of a
     Room read, cleared on reload, never persisted (a full card cache doesn't belong in
     `localStorage`). The remaining 14 Room-cache-only / tag-write methods
     (`refreshCardById`/`backfillMissingOracleIds`/`backfillMissingStrategyTags`/
     `getCachedEnglishSiblings`/`updatePrices(Batch)`/`evictStaleCache`/`updateCardTags`/
     `unionCardTags`/`updateUserTags`/`updateSuggestedTags`/`confirmSuggestedTag`/
     `dismissSuggestedTag`) throw a loud, documented `UnsupportedOperationException` pointing
     back at the scoping note — never a silent no-op, never faked Room semantics. Compiled clean
     via `:shared:core-data:compileKotlinWasmJs` (hit the documented stale-incremental-klib-cache
     `ArrayIndexOutOfBoundsException` on the first attempt — fixed with `--rerun --no-build-cache`
     per memory, not a real code problem).
  2. **`:webApp` wiring + Card Search MVP screen** — `WebAppKoinModule.kt` assembles the whole
     Scryfall stack fresh for web (Ktor `Js` `HttpClient` + content negotiation, `ScryfallClient`,
     `ScryfallRequestQueue`, `ScryfallCache`, `DispatcherProvider`, `ScryfallRemoteDataSource`),
     mirroring Android's `NetworkModule`/`SharedDomainUseCaseModule` (Hilt) construction shape
     one-for-one, then binds `single<CardRepository> { WebCardRepository(...) }` (interface-typed,
     per the project's Koin gotcha). New `CardSearchScreen`/`CardSearchViewModel` replace the
     "Search" nav placeholder in `App.kt` — the first REAL `:webApp` MVP screen, not a showcase.
     A text field triggers `searchCardsPaginated`; results render in the existing `AdaptiveCardGrid`
     via a new minimal `CardSearchResultTile` (`MagicCard` + `CardName`, NOT
     `CardGridItem` — that component takes a `CollectionCardGroup`, a collection-only shape
     with no fit for a raw search result with no ownership/quantity data; a fresh minimal tile
     was the right call, confirming the plan's contingency). Handles idle/loading/error/content
     states plus a "Load more" pagination footer. **Verified live in Chromium (Playwright)**:
     typing "Lightning Bolt" and pressing Enter resolves two REAL Scryfall results (Emeritus of
     Conflict / SOS, Lightning Bolt / MSC) with real card art rendering (`cards.scryfall.io` image
     requests both 200), confirmed at 375px (COMPACT, bottom bar, 2-col reflow), 768px (MEDIUM,
     collapsed rail, larger tiles/full names), and 1280px (LARGE, expanded rail) — zero horizontal
     overflow at any width (`scrollWidth == clientWidth` at all three). Confirmed no code path in
     the new screen/ViewModel reaches any of the 14 stub methods (only `searchCardsPaginated` is
     called). Android's `CardRepositoryImpl` (`app/src/main/java/.../repository/`) was NOT touched.
     `:app:assembleDebug` stays green. **Note**: the paired A3 move (Android's `CardRepositoryImpl`
     into `shared/core-data/src/androidMain`) was intentionally NOT done in this slice per its own
     brief (explicitly out of scope, Android-side work belongs to `android-kotlin-architect`) —
     still outstanding, same as it was before this slice.
  Full detail + the reusable "delegate to the existing commonMain remote data source instead of
  rebuilding rate-limiting/caching" finding: memory `project_kmp_spike_findings` (W3b addendum,
  pending) and `.claude/agent-memory/kmp-web-fullstack-dev/project_w3_repo_slice_pattern.md`.

- ✅ **Web W3a — DONE** (2026-08-03, branch `kmp-migration-web`, commits `b9aba6ad` + `49772075`).
  First W3 "web data layer, one repo per slice" task — deliberately the narrowest repo in the W3
  order (`UserPreferencesRepository`: 6 flows + 9 setters, 37 lines), chosen to validate the
  "wasmJs impl behind the shared `commonMain` interface" pattern before the bigger repos
  (`CardRepository`, `DeckRepository`, etc. — still NOT started).
  1. **`WebUserPreferencesRepository`** (new file, `shared/core-data` wasmJsMain,
     `repository/WebUserPreferencesRepository.kt`) — a FRESH implementation, deliberately NOT a
     port of Android's 1008-line `UserPreferencesDataStore` god-object (which serves dozens of
     unrelated preference flows well beyond this interface) — that file was correctly left
     completely untouched. Backed by the existing wasmJs `KeyValueStore` actual
     (`LocalStorageKeyValueStore`, real `window.localStorage`, built in W1). **Reactivity
     pattern** (reusable for future repo slices): `KeyValueStore` has no native reactive-flow
     support (no Room/DataStore `Flow`, and `localStorage`'s own `storage` DOM event only fires in
     OTHER tabs) — every exposed `Flow` is backed by an in-memory `MutableStateFlow` cache,
     hydrated once at construction via a `Dispatchers.Unconfined` coroutine (runs to completion
     eagerly/synchronously since `LocalStorageKeyValueStore`'s suspend calls never actually
     suspend) and kept in sync on every write through the same Koin-`single` instance. Encoding
     mirrors Android's conceptual scheme (enum `.code`/`.name`, JSON via `kotlinx.serialization`
     for the `List<UserDefinedTag>` and `Set<NewsLanguage>` collections) without being a
     cross-platform wire-format port — each platform persists to its own local store. Compiled
     clean via `:shared:core-data:compileKotlinWasmJs` with zero `.gradle.kts` changes needed
     (core-data's commonMain already exposed `kotlinx-serialization-json` + `core-domain` as `api`
     dependencies, both inherited by wasmJsMain automatically).
  2. **`:webApp` wiring + live proof** — `WebAppKoinModule.kt` gains
     `single<UserPreferencesRepository> { WebUserPreferencesRepository(...) }` (bound against the
     interface, not the bare concrete class, per the project's documented Koin gotcha).
     `ThemeShowcaseViewModel`/`ThemeShowcaseScreen` extended with a minimal real exercise: a
     `collectionViewModeFlow`/`saveCollectionViewMode` GRID↔LIST toggle button, deliberately
     distinct from W1's raw `KeyValueStore` toggle (this proves the REPOSITORY layer's own
     hydration/serialization logic, not just the underlying KV store). **Verified live in
     Chromium (Playwright)**: clicking Toggle writes `collection_view_mode=LIST` to real
     `window.localStorage` and updates the UI immediately; a full page reload (fresh Koin graph,
     fresh `WebUserPreferencesRepository` instance) still shows `Current: LIST` — proving the
     repository's own read/hydration path works end-to-end, not merely that the raw key
     survived. Zero console errors across both checks. No `.gradle.kts` changes needed here
     either (`:shared:core-domain`'s `UserPreferencesRepository` type was already transitively
     visible to `:webApp` via `core-data`'s `api` dependency on `core-domain`).
  Both checkpoints passed the `android-security-auditor` pre-push gate clean (no secrets/PII —
  only localStorage key names and non-sensitive user preference data). Full detail: memory
  `project_kmp_spike_findings` (W3a addendum).

- ✅ **Web W2b — DONE** (2026-08-03, branch `kmp-migration-web`, commits `f159d441` + `4629a484`).
  Master plan §2.2 Action 2 (Ktor-level auth-header injection in commonMain), in two checkpoints.
  **Google OAuth is explicitly OUT of scope / deliberately deferred** — the user chose to defer
  rather than pick an implementation approach on the spot (memory
  `project_kmp_web_google_oauth_deferred`); do not treat it as blocking.
  1. **Shared Ktor auth-header plugin** — new `installSupabaseAuthHeaders(supabaseClient, anonKey)`
     (`shared/core-data` commonMain, `remote/SupabaseAuthHeaderPlugin.kt`): a plain
     `HttpClientConfig<*>` extension using `createClientPlugin` (same idiom already proven by
     `SupabaseClientFactory.kt`'s call-counter plugin) that injects `apikey`/
     `Authorization: Bearer <token or anonKey>`/`Content-Type`/`Accept` on every request (token read
     fresh per-request via `Auth.currentSessionOrNull()`), plus the shared `ContentNegotiation`
     (kotlinx-json, `ignoreUnknownKeys=true`, `encodeDefaults=true`) and `expectSuccess=true` — one
     mechanism, installed identically on Android (`OkHttp` engine) and Web (`Js` engine).
     `AuthKoinModule.kt`'s `"supabaseKtor"` single now builds on a bare (logging-only) OkHttp
     engine + this plugin. **Important nuance found mid-task, not anticipated by the original
     brief**: the separate `@Named("supabase")` OkHttpClient single was intentionally left
     UNTOUCHED (still has its own interceptor) because `AuthRepositoryImpl` makes two RAW
     (non-Ktor) OkHttp calls to Edge Functions (`delete-current-user`,
     `set-google-account-password`) that bypass Ktor entirely and rely on that interceptor for
     header injection — removing it would have silently broken both. Verified:
     `:app:assembleDebug` green; `AuthRepositoryImplTest`'s one pre-existing failure (session
     mapping, unrelated) reproduces identically on baseline via git-stash A/B, confirming no
     regression from this change.
  2. **Web wiring** — `WebAppKoinModule.kt` gains a `@Named("supabaseKtor")` `HttpClient(Js)` using
     the same plugin, plus `UserProfileClient`/`FriendshipClient` construction (both already
     commonMain, just needed a web construction site). `AuthViewModel` gained a one-time, clearly-
     marked plumbing smoke check: after guest sign-in resolves, it calls
     `UserProfileClient.fetchProfile()` and shows the outcome in `AuthScreen` — not a real feature,
     exists only to prove the Bearer token is read correctly at wasmJs runtime. **Verified live in
     Chromium (Playwright)**: guest sign-in → `POST .../auth/v1/signup` (200) → `GET
     .../rest/v1/user_profiles?id=eq.<that session's own uid>&select=...` (200, 1 row) — getting
     back exactly the caller's own row is strong proof the correct per-session token was injected
     (a wrong/stale token 401/403s; a missing one falls back to the anon key and would RLS-deny).
     **Finding for follow-up, NOT fixed here (out of scope)**: this result contradicts CLAUDE.md's
     documented "guests have no user_profiles row" invariant — confirmed via Supabase MCP that
     `public.handle_new_user()` (the `on_auth_user_created` trigger) inserts a `user_profiles` row
     for EVERY `auth.users` insert unconditionally, anonymous sign-ups included. Needs a dedicated
     `backend-supabase-expert` look. Both checkpoints passed the `android-security-auditor`
     pre-push gate clean. Full detail: memory `project_kmp_spike_findings` (W2b addendum).

- ✅ **Web W2a — DONE** (2026-07-30, branch `kmp-migration-web`, commits `6481a7f6` + `930d56c3`).
  Guest-only auth on web, in two checkpoints:
  1. **Shared `SupabaseClient` factory** — extracted `createManaHubSupabaseClient(...)` to
     `shared/core-data` commonMain (new file `remote/SupabaseClientFactory.kt`), parameterized by
     `sessionManager`/`httpEngine`/`oauthScheme`/`crashReporter`. Installs Auth
     (`alwaysAutoRefresh`/`autoLoadFromStorage`/the passed `SessionManager`, `scheme`/`host` only
     when `oauthScheme != null`), Postgrest, Realtime, and the WS7 call-counter plugin — byte-
     identical to what `SupabaseModule.kt` used to build inline. `app/.../SupabaseModule.kt`
     rewritten to delegate to it (Android behavior unchanged: same `SecureSessionManager`, same
     `Android.create()` engine, same `"manahub"` scheme). Added `libs.supabase.realtime` to
     `shared/core-data/build.gradle.kts` commonMain (was missing). Verified:
     `:app:assembleDebug` BUILD SUCCESSFUL; `AuthRepositoryImplTest` isolated run green (worked
     around the same pre-existing community-decks WIP test-compile break as W0/W1, per project
     convention — moved the two broken files aside, ran, restored).
  2. **Web auth screen** — new `WebSessionManager` (wasmJsMain) implementing supabase-kt's own
     `SessionManager` contract (NOT a hand-rolled token store, per the W0 audit follow-up),
     persisting `UserSession` JSON to `window.localStorage` under `manahub_web_session`, no
     encryption layer (documented: no browser equivalent of Android Keystore — same trust
     boundary as a cookie-based session). New `AuthViewModel`/`AuthScreen` (guest sign-in only —
     `Auth.signInAnonymously()`; Google OAuth deliberately deferred to a follow-up). `AuthUiState`
     exposes only resolved fields (`userId`, `isAnonymous`) — never the raw `SessionStatus`/
     `UserSession`, per the W0 spike's JWT-leak finding. Wired into `App.kt` as a 4th "Account"
     nav tab inside the existing `AdaptiveScaffold`. `:webApp` gained a `:shared:core-data`
     dependency (was missing). **Verified live in Chromium (Playwright)**: guest sign-in resolved
     a real anonymous session against the project's Supabase instance (real JWT, real user id);
     `localStorage` payload was byte-identical before and after a full page reload (proves the new
     `SessionManager` persists, not just the generic KeyValueStore toggle W1 already proved); zero
     horizontal overflow at 375px/768px/1280px, screen renders cleanly inside the reused shell at
     all three. **Notable finding (not fixed, out of scope):** the real anonymous JWT's
     `app_metadata` is empty — `is_anonymous: true` is a top-level JWT/user-object claim, not
     nested under `app_metadata` — so Android's existing `userInfo.appMetadata?.get("is_anonymous")`
     convention (documented in this file's CLAUDE.md-adjacent notes) likely mis-resolves too for a
     freshly-minted anonymous session; the web `AuthViewModel` mirrors that same convention for
     parity rather than silently diverging. Worth a follow-up audit on the Android side — see
     memory `project_kmp_spike_findings` addendum.
  Security gate: both checkpoints passed `android-security-auditor` review (checkpoint 2 had one
  non-blocking LOW finding — raw Supabase error message surfaced to `AuthUiState.Error`, not
  sanitized — deferred, not urgent).

- ✅ **Web W1 — DONE** (2026-07-30, branch `kmp-migration-web`). Replaced W0's throwaway `main()`
  with the real entry point: `startKoin` (no `androidContext()`/`androidLogger()`) + `webAppKoinModule`
  + `App.kt` (`MagicTheme { AdaptiveScaffold { ThemeShowcaseScreen() } }`). New shared
  `shared/core-ui/.../layout/` package (commonMain, Android+wasmJs green): `ManaWindowSizeClass`
  (COMPACT/MEDIUM/EXPANDED/LARGE, hand-rolled — CMP has no `androidx.window` equivalent),
  `AdaptiveScaffold` (bottom bar / collapsed rail / expanded rail per breakpoint, built fresh rather
  than adapting `MagicBottomBar`; 1200.dp content clamp+center at LARGE; breakpoint-scaled screen
  padding), `AdaptiveCardGrid` (breakpoint-scaled `GridCells.Adaptive`). `LocalStorageKeyValueStore`
  (core-common wasmJs) now backed by REAL `window.localStorage` (kotlinx-browser), replacing the
  in-memory stub. Added `koin-compose-viewmodel` (4.2.2) for the pure-CMP `koinViewModel()`.
  **Validated live in a real browser** (Playwright/Chromium) at 375px (COMPACT, bottom bar), 768px
  (MEDIUM, collapsed icon-only rail), 1280px (LARGE, expanded icon+label rail) — zero horizontal
  overflow at any width; the 1200dp clamp+gutters confirmed at 1600px (not visible at 1280px, where
  the 200dp rail leaves only ~1080dp of content width — under the clamp threshold, expected). Live
  theme switching confirmed across multiple palettes incl. `HallowedPrint`. Persistence confirmed:
  `localStorage` value `null` → `"true"` after toggling → still `"true"` after a full page reload.
  One notable testing gotcha (memory `project_kmp_spike_findings`): Compose Multiplatform for wasmJs
  renders to a single `<canvas>` with no exposed DOM/ARIA tree, so Playwright's `getByRole()`/
  `getByText()` find nothing — verification needs raw `page.mouse.click(x, y)` coordinates read off
  a screenshot. Android verify: `:app:assembleDebug` BUILD SUCCESSFUL (this slice touches no
  `androidMain`/`:app` source at all). `:app:testDebugUnitTest` run once (no retry loop) — failed at
  the COMPILE step, but the errors are 100% inside two pre-existing, uncommitted community-decks test
  files already on this branch before this session (unrelated in-progress work, left untouched per
  instruction) — identical errors to the W0 run, confirming a standing unrelated blocker, not a
  regression.

- ✅ **Web W0 — DONE** (2026-07-30, branch `kmp-migration-web`). `:webApp` module created for real
  (wasmJs-only target, kept permanently — only the throwaway `main()` body gets replaced in W1).
  All 4 target libraries CONFIRMED WORKING at wasmJs RUNTIME in an actual browser (Chromium via
  Playwright, not just compile): `kotlinx.browser.localStorage` (real round-trip, survives reload),
  standalone Ktor `Js`-engine `HttpClient` (Scryfall fetch+parse), Coil3 via `coil-network-ktor3` +
  explicit `KtorNetworkFetcherFactory` wiring (rendered a real card image), and supabase-kt
  (`auth-kt`+`postgrest-kt`, own from-scratch client) — `signInAnonymously()` resolved against the
  real project Supabase instance, session persisted across reload. supabase-kt fallback (hand-rolled
  Ktor client) was **NOT** triggered. One real runtime bug found+fixed: supabase-kt 3.1.4's wasmJs
  klib needs kotlinx-datetime 0.6.x (forced via `resolutionStrategy` in `webApp/build.gradle.kts` —
  Gradle's default resolution otherwise bumps it to 0.7.x via CMP/Coil3, causing an `IrLinkageError`
  at runtime only). Getting `wasmJsBrowserDistribution` to build at all on this Windows dev box also
  required disabling the Kotlin/Wasm toolchain's own Node.js/Yarn/Binaryen repo auto-registration
  (conflicts with `FAIL_ON_PROJECT_REPOS`) in favor of system-installed tools, a KT-58759 DSL fix,
  and a real npm/cli#9133 workaround (kept Yarn as the wasm package manager). Full findings + every
  toolchain gotcha: memory `project_kmp_spike_findings`. Android verify: `:app:assembleDebug` BUILD
  SUCCESSFUL (no cross-module graph breakage); `:app:testDebugUnitTest` could **not** be verified
  cleanly on this machine (Gradle Test Executor crashed after a cascade of OOMs in unrelated test
  classes, following a "1768 tests completed, 67 failed, 3 skipped" summary line — consistent with
  resource contention from the heavy build/browser work in this session, not a regression from this
  change, since it touches no `androidMain`/`:app` source at all). **Re-run
  `:app:testDebugUnitTest` on an idle machine before relying on this branch further** to reconfirm
  the 1967/118/2 floor.

- ✅ **Phase 0 / 0.5 / 1 / 2 / 3 / 4 / 5-Slice-1 — DONE** (2026-06-20 → 2026-07-01). Highlights:
  5 `:shared:core-*` modules, **357 commonMain files**; Retrofit+Gson fully removed (6 Ktor
  clients); all repo interfaces + models + movable use cases + design system + ~54 composables
  shared; `java.time`/`@StringRes`/`R.string` eliminated from shared; regression audit root-caused
  every failing test (floor 1967/118/2); signed release + R8 verified green post-split.
- ✅ **Hilt→Koin cutover Batches 1–6 — DONE** (2026-07-03/04). Koin owns ALL non-excluded feature
  DI (~40 VMs, 20 islands) + the 7 non-excluded workers (`koin-androidx-workmanager`,
  `DelegatingWorkerFactory(Koin + Hilt)` for the excluded scanner worker). 10 feature Hilt modules +
  the `SharedDomainUseCaseModule` hub deleted. Remaining Hilt = 12 infra/bridge modules + 4 excluded
  modules + `KoinToHiltBridgeModule` (reverse bridge for what workers/excluded still consume).
  Batch 7 (infra→Koin) deliberately DEFERRED to the excluded-trio final wave (plan §4 A5).
- ⚠️ **Batch 6 caveat (open):** the Koin WorkerFactory ordering was verified against library source
  but **never on-device** — validate all workers execute next time an emulator is available (A1).
- ✅ **2026-07-01 audit remediation — executed**: P0.1 dead use cases deleted, P0.3 grep fixed,
  P1.1 CrashReporter for the 7 migration-candidate classes, P1.3 orphan VMs → Koin, P1.4 first
  26 commonTest tests, P2.1 `DeckBuilderState` package fix, `GetAccountNudgeUseCase` +
  `ImportCommunityDeckUseCase` unblocked + moved. P1.2 (Room impls move) RESERVED → paired with
  web W3. P0.2 (web KV stub) → web W1. The audit doc is deleted; its verdict + web critical path
  live in the master plan §1/§5.
- 📋 **User decisions on record:** full-Koin end-state (Hilt dies with the excluded wave) ·
  Room stays Android, web is Supabase-remote-first/online-first · `:shared:feature-*` modules
  dropped for now · CI deferred until web exists · `DeckMagicDetailScreen` kept · Android-first
  sequencing satisfied — web phase unblocked.

## NEXT STEP

1. **Web scope expansion round 1 (Settings, Profile, Add Card) is COMPLETE** (see STATUS above,
   commits `69b3e31a` / `12e6874b` / `6db47c9d` + `a28df9c4`), closing out the user's first
   approved 3-slice priority order (2026-08-04). **Web scope expansion round 2 (approved
   2026-08-04, second wave) has Friends DONE** (commits `ff64ade2` + `46b6b181`, see STATUS above)
   — friends list + pending/outgoing requests + search-by-game-tag add-friend, verified live with
   two real Supabase accounts. **Trades is a SEPARATE, LARGER follow-up** — its repository-layer
   situation was flagged for independent investigation and is being built out concurrently on this
   same branch by a different work stream (`web(trades): *` commits) — do not duplicate that work;
   check its own commits/state before touching anything under `webApp/.../web/trades/` or
   `WebTradesRepository`/`WebWishlistRepository`/`WebOpenForTradeRepository`. **Game/online sessions
   remain explicitly OUT of scope** — `feature/online` is not yet KMP-migrated on Android (still
   Hilt + Android-only), so building it for web first would be architecturally backwards; do not
   touch anything online-session/game/life-counter-related without the user first asking for it.
   **Deferred within Friends itself** (documented in `FriendsScreen.kt`'s KDoc, not started): a
   friend-detail view (`getFriendCollection`/`getFriendStats`/`getFriendMatchHistory` — all three
   repository methods are fully implemented, just no UI consumer yet) and the referral-invite flow
   (`acceptInvite`/`getMyShareUrl` — Profile's own game-tag display is the most likely future home
   for a "share my invite" control, not a new Friends-screen control). No further web scope
   expansion beyond what's already approved — raise it with the user before starting anything new.
2. **Web W4 + W5b are COMPLETE (see STATUS above).** W4d (Home screen) closed out the master
   plan's originally-scoped MVP screen list; W5b (2026-08-04, commits `b6ba4989`/`a42e12bf`) swept
   every screen at 320px/1920px+ and fixed the two real bugs it found (cross-cutting
   `AdaptiveScaffold` clamp + Deck Editor text-wrap). **What remains open under the master plan's
   W5 umbrella, and what's still deferred elsewhere:**
   - **The telemetry framework choice is a REAL, OPEN USER DECISION — not something to pick
     autonomously.** The master plan's W5 scope includes telemetry/analytics for the web target,
     but Android's Firebase Crashlytics/Analytics stack has no ready wasmJs equivalent (Firebase
     JS SDK vs. a hand-rolled web analytics client vs. deferring entirely are all live options with
     real tradeoffs — cost, privacy/consent implications for an unauthenticated guest-first web
     app, and effort). Do not implement any telemetry plumbing for `:webApp` until the user has
     chosen a direction.
   - **W3's two remaining data-layer slices stay deferred** — `NewsRepository` (needs a Cloudflare
     Worker CORS proxy first, memory `project_kmp_web_news_cors_deferred`) and `CommunityDecks`
     (blocked on the user's own active uncommitted WIP on this branch, memory
     `project_kmp_web_communitydecks_deferred`). Re-check both before resuming either.
   - **The Android-nav-artifact-unification question (androidx → JetBrains CMP navigation-compose
     on Android too, sharing one `Screen.kt`) remains explicitly UNDECIDED and NOT started** — W4a
     deliberately scoped around it (see STATUS). Raise it with the user before ever touching
     `app/src/main/java/com/mmg/manahub/app/navigation/`.
   - **A3 debt item for `CardRepositoryImpl` is now INVESTIGATED and BLOCKED, not just outstanding**
     (2026-08-04, `android-kotlin-architect`, no code changed). It is NOT a mechanical single-file
     relocation like W3c/W3d's remote-data-source moves: `CardRepositoryImpl` calls `CardDao`
     directly in nearly every method, and `CardDao`/`CardEntity`/`CardEntityMapper.kt` all still live
     in `:app`, which `shared/core-data` cannot depend on (one-directional Gradle module deps). The
     move requires FIRST promoting the `CardDao`/`CardEntity`/mapper trio into
     `shared/core-data/src/androidMain` (its own checkpoint, real Room-schema-adjacent work, not a
     pure relocation) plus swapping the Hilt-qualified dispatcher/scope constructor params for the
     KMP `DispatcherProvider` abstraction and rewiring the (still-Hilt, not Koin) `RepositoryModule`
     binding to a `@Provides`. **This generalizes to essentially every one of A3's own "~15 impls"**
     — spot-checked `DeckRepositoryImpl`, same unmoved Room-DAO-heavy shape (W3c only moved
     `DeckRemoteDataSource`, a different, Room-free class). Needs a real sub-plan / explicit user
     decision on sequencing before the next attempt, not "pick up whenever convenient." Full
     investigation: memory `project_kmp_spike_findings` §"A3 debt item — CardRepositoryImpl move
     BLOCKED, investigated not executed (2026-08-04)".
   - **Email/password auth (sign-in/sign-up/password reset) is COMPLETE** (2026-08-05, commit
     `7c7317db`, see STATUS above). **Google OAuth remains explicitly deferred** (user decision,
     memory `project_kmp_web_google_oauth_deferred`) — not a blocking prerequisite to anything
     above. Note this changes prior sessions' verification methodology: earlier slices (e.g.
     Profile) seeded real non-guest test accounts directly via SQL because "Google OAuth is still
     deferred and there's no other way to reach a non-guest session" — that caveat is now stale.
     A real account can be created and signed into through the web UI itself; SQL-seeding remains
     useful for BYPASSING email confirmation (`email_confirmed_at`) and for MULTI-ACCOUNT scenarios
     needing two accounts set up in one shot, but is no longer the only path to a non-guest session.
   - **Card Detail completion (print/language switching, art variants, read-only tag display) is
     COMPLETE** (2026-08-05, commit `8f08c44a`, see STATUS above). **Tag EDITING remains explicitly
     deferred** (confirming/dismissing suggested tags, custom user tags, the `custom_` key-prefix
     CRUD system) — a genuinely separate, deeper feature; `WebCardRepository`'s tag-mutation methods
     stay as their documented `UnsupportedOperationException` stubs. There is **no rulings gap** to
     revisit — Android's `CardDetailViewModel` has zero ruling-related code, confirmed before this
     slice was dispatched.
   - **Deck Studio / Home richer-porting remain optional, additive future passes**, not required to
     call the MVP "done" — the repository layer needs zero further work for either
     (`WebDeckRepository`/`WebCardRepository`/`DeckRepository`/`UserCardRepository` already cover
     every call a richer UI would make).
   - **The `PlatformCapabilities`-gated "not available on web" placeholder remains NOT
     recommended right now**, same reasoning as before (no-stub-rule precedent): the web nav still
     only exposes real, working destinations (Home/Search/CardDetail/Decks/DeckEditor/Collection/
     Theme/Account) — do this once a screen needs to link toward a genuinely non-MVP feature
     (game/life-counter, online, voice, scanner, playtest, push, gamification UI) and show a
     graceful message instead of a dead link.
   - **W6 (release: Cloudflare Pages deploy + CI + a real mobile-browser device check) is the
     natural next phase once the telemetry decision lands**, per the master plan roadmap — not
     started.
3. ✅ **RESOLVED (2026-08-03)** — the `handle_new_user()` anonymous-user finding from W2b. User
   approved a DB-level fix: `public.handle_new_user()` now guards `is_anonymous` (migration
   `fix_handle_new_user_skip_anonymous_users`) and the 10 pre-existing spurious `user_profiles`
   rows were deleted. No `AFTER UPDATE` trigger needed (no code path converts an anonymous session
   to permanent in place). CLAUDE.md's "Online sessions" invariant updated to reflect DB-level
   enforcement. Memory: `feedback_handle_new_user_anonymous_guard`.
4. **Fix (or hand off) the pre-existing community-decks test compile break** — two uncommitted test
   files (`CommunityDecksRepositoryImplTest.kt`, `CommunityDecksSearchViewModelTest.kt`) reference
   `deckFormatId`/`format`/`ALL`/`featuredFormatDecks` symbols that don't exist on the currently
   uncommitted community-decks production WIP on this branch. This is the user's own in-progress
   work (not touched by the web agent, per explicit instruction) and has now blocked
   `:app:testDebugUnitTest` from producing a clean pass/fail/skip count across every web session
   so far (W0 through W3c). Needs resolving (by whoever owns that WIP) before the 1967/118/2 floor
   can be reconfirmed on this branch.
5. **A2 — `android-edge-case-tester` pass** on Tournament finish-and-advance, GameSession/Stats,
   Deck Doctor (last open Android hardening item; fixes → architect).
6. **A1 — on-device WorkerFactory validation** (first time a device/emulator is available).

## LOG (compact; full detail in git history of this file)

- 2026-06-20: Phase 0 spikes A/D/E; Phase 0.5 blockers; Phase 1 foundation repaired.
- 2026-06-21/22: 20 Koin islands; `:shared:core-{model,common,domain,data}`; Retrofit→Ktor ×6.
- 2026-06-23/24: Phase 2 data sharing substantially complete; `:shared:core-ui` + CMP 1.11 + Coil 3.
- 2026-06-25 → 07-01: Phase 4 (55+ use cases, gamification/game/tournament domain, catalogs,
  composable sweep, kotlinx-datetime, CMP Res POC); Phase 5 Slice 1 regression audit; release/R8 +
  security + baseline-profile audits clean; README refresh.
- 2026-07-01: external audit → remediation P0.1/P0.3/P1.1/P1.3/P1.4 + quick wins; floor 1967/118/2.
- 2026-07-03/04: Hilt→Koin cutover batches 1–6 (features, use-case hub, workers); reverse bridge
  `KoinToHiltBridgeModule`; minor debt closed (P2.1, NudgeTrigger/GetAccountNudge).
- 2026-07-04: docs consolidated — master plan rewritten, audit/map/next-session-prompt deleted,
  this tracker compacted.
- 2026-07-30: branch `kmp-migration-web` created for the web phase. **Web W0 done**: `:webApp`
  module created for real; supabase-kt/Ktor-Js/Coil3/kotlinx-browser localStorage all confirmed
  working at wasmJs runtime in an actual browser; kotlinx-datetime forced to 0.6.2 (supabase-kt klib
  compat); wasmJs toolchain (Node/Yarn/Binaryen repo conflicts with FAIL_ON_PROJECT_REPOS) fixed for
  this dev machine. Full findings: memory `project_kmp_spike_findings`.
- 2026-07-30: **Web W1 done** (same day): real Koin-started `main()`/`App.kt`/`ThemeShowcaseScreen`
  replacing W0's throwaway entry point; new `shared/core-ui/.../layout/` responsive package
  (`ManaWindowSizeClass`/`AdaptiveScaffold`/`AdaptiveCardGrid`); real `localStorage`-backed
  `LocalStorageKeyValueStore`. Validated live at 375/768/1280(+1600 for the clamp)px — zero
  horizontal overflow, correct nav-chrome switching, persisted toggle survives reload. Found: CMP
  wasmJs has no DOM/ARIA tree (Playwright locators need raw coordinate clicks). Discovered (not
  caused): the pre-existing uncommitted community-decks test files are still compile-broken,
  blocking a clean `testDebugUnitTest` floor reading on this branch (now NEXT STEP #1).
- 2026-07-30: **Web W2a done** (same day, commits `6481a7f6` + `930d56c3`): shared
  `createManaHubSupabaseClient` factory (`shared/core-data` commonMain) consumed by both
  `SupabaseModule.kt` (Android, behavior unchanged) and a new `:webApp` guest-only auth screen
  (`WebSessionManager` implementing supabase-kt's real `SessionManager` contract over
  `localStorage`, `AuthViewModel`/`AuthScreen`, wired as a 4th "Account" tab in `App.kt`). Verified
  live in Chromium: real anonymous session against the Supabase project, session survives a full
  page reload (byte-identical stored payload), zero overflow at 375/768/1280px. Found (not fixed):
  the real anonymous JWT's `is_anonymous` claim lives at the top level, not under `app_metadata` —
  Android's existing `isAnonymous` derivation likely has the same latent miss; flagged for a
  follow-up audit, not touched in this slice.
- 2026-08-03: **Web W2b done** (commits `f159d441` + `4629a484`): shared
  `installSupabaseAuthHeaders` Ktor plugin (`shared/core-data` commonMain) replacing the
  Android-only OkHttp interceptor for `UserProfileClient`/`FriendshipClient` traffic — installed
  identically on Android (`OkHttp`) and Web (`Js`). Kept `AuthRepositoryImpl`'s separate raw-OkHttp
  Edge Function calls (`delete-current-user`/`set-google-account-password`) working unmodified via
  their own untouched interceptor-bearing client. Wired the shared client +
  `UserProfileClient`/`FriendshipClient` into `:webApp`'s `WebAppKoinModule.kt`. Verified live in
  Chromium: guest sign-in → real authenticated `GET user_profiles` returned exactly the caller's
  own row (200, 1 row) — strong proof of correct per-session Bearer-token injection on wasmJs.
  Found (not fixed, routed to `backend-supabase-expert`): `handle_new_user()` creates a
  `user_profiles` row for anonymous sign-ups too, contradicting CLAUDE.md's "guests have no
  user_profiles row" line. Google OAuth remains explicitly deferred per user decision (memory
  `project_kmp_web_google_oauth_deferred`) — W3 (web data layer) is the actual next step, not
  blocked on OAuth.
- 2026-08-03: **Web W3a done** (commits `b9aba6ad` + `49772075`): first W3 data-layer slice,
  `WebUserPreferencesRepository` (`shared/core-data` wasmJsMain) — fresh implementation, NOT a
  port of Android's 1008-line `UserPreferencesDataStore` (left untouched). Reactivity via an
  in-memory `MutableStateFlow` cache hydrated from the wasmJs `KeyValueStore` (real localStorage)
  at construction, kept in sync on every write — a pattern future repo slices should reuse. Wired
  into `:webApp`'s Koin module (bound against the interface type) and exercised via a
  `ThemeShowcaseScreen` GRID/LIST toggle. Verified live in Chromium: toggle click writes to real
  `localStorage` and updates the UI; a full page reload (fresh repository instance) still shows
  the persisted value, proving the repository's own hydration path, not just the raw KV store.
  Zero `.gradle.kts` changes needed (transitive `api` exposure already covered both
  `kotlinx-serialization` and `core-domain`). Next slice: `CardRepository`.
- 2026-08-03: **Web W3d done** (commits `dbfc3b23` + `04a3468c` + `56e6228c`): fourth W3 data-layer
  slice, `WebUserCardRepository` (`shared/core-data` wasmJsMain) — collection. Same remote-first
  shape as W3c (not W3b). Two caches: `entriesCache` (all rows incl. soft-deleted, so a revive
  branch can find them) and `cardsCache` (joined `Card` data resolved via the existing
  `CardRepository` singleton). Added a new `merge_collection_entry` Supabase RPC for
  `updateEntryWithMerge` after confirming the existing `batch_upsert_collection` RPC — despite
  looking similar — would leave a stale/duplicate row in two of the four required branches; caught
  and corrected an over-broad `anon`-role grant instruction along the way (anonymous-signed-in
  guests are Postgrest role `authenticated`, not `anon`). Wired into `:webApp`'s Koin module + a new
  read-only Collection screen; Card Search's tap-a-result now writes through
  `addOrIncrement`. Verified live in Chromium: a searched card, added, appears in Collection, and
  survives a full page reload. Next slice: `NewsRepository`.
- 2026-08-03: **Web W3b done** (commits `ddcfa689` + `4e23d61a`): second W3 data-layer slice,
  `WebCardRepository` (`shared/core-data` wasmJsMain) — the first slice backing a REAL screen
  (Card Search), not a showcase. Split the 27-method `CardRepository` interface: 13 real
  search/lookup methods delegate to the already-`commonMain` `ScryfallRemoteDataSource` (shared
  rate limiter + TTL cache, reused as-is rather than rebuilt), backed by an in-memory
  `MutableStateFlow<Map<String, Card>>` session cache for `observeCard`/`getCardsByIds`; the
  remaining 14 Room-cache-only/tag-write methods throw a documented `UnsupportedOperationException`
  instead of a silent no-op or faked Room read. Wired into `:webApp`'s Koin module (full Scryfall
  stack assembled fresh, mirroring Android's Hilt shape) and a new `CardSearchScreen`/
  `CardSearchViewModel` — the first real `:webApp` MVP screen. Verified live in Chromium: "Lightning
  Bolt" resolves two real Scryfall results with real card art, reflowing correctly at
  375/768/1280px with zero overflow. `CardGridItem` was NOT reusable (it takes a collection-only
  `CollectionCardGroup`) — built a minimal `CardSearchResultTile` from `MagicCard`+`CardName`
  instead, confirming the plan's documented contingency. Android's `CardRepositoryImpl` untouched;
  `:app:assembleDebug` green. Next slice: `DeckRepository` (Auth/Profile already covered by
  W2a/W2b). Outstanding: the paired A3 Android-impl move to `androidMain` was not part of this
  slice's brief and remains open.
- 2026-08-04: **Web W4b done** (commits `bff1b863` + `d93033b8`): second W4 slice, a deliberately
  minimal read-only Card Detail screen (`webApp/.../carddetail/`) — image/name/mana cost/type
  line/oracle text/prices only, explicitly excluding tag editing, print/language switching, and
  add-to-collection-from-detail. First PARAMETERIZED CMP nav route (`#card/<scryfallId>`, a path
  segment, confirmed live) — the W4a zero-arg deep-link lookup table needed a dedicated prefix-strip
  branch alongside it. Wired from Search (new distinct overlay icon, preserving the W3d
  tap-to-add gesture unchanged) and Collection (tile image now opens detail directly, no competing
  gesture there). Found + fixed (via a delegated one-line `.gradle.kts` dependency, no `.kt` needed)
  a real gap: `:webApp` was missing `coil-svg`, so Scryfall mana-symbol SVGs fetched but never
  rendered — Coil3's wasmJs `ServiceLoaderTarget` auto-registered the decoder the moment the
  dependency was added, narrowing the earlier W0 finding that explicit `ImageLoader` wiring was
  required. Verified live in Chromium: full search→detail→back→collection→detail→deep-link-reload
  loop, zero console errors, zero overflow at 375/768/1280px. `:app:assembleDebug` green; diff
  confirmed `:webApp`-only.
- 2026-08-04: **Web W4c done** (commit `17ddc8d0`): third W4 slice, a deliberately minimal Deck
  Editor (`webApp/.../deckeditor/`) — mainboard/sideboard with quantities, compact screen-local
  add-card search, remove/decrement, rename, curated 3-format picker. Explicitly excludes Deck
  Doctor suggestions, wizard/seed-build, playtest, import/export, and warning overlays. No new
  repository work needed (`WebDeckRepository`/`WebCardRepository` already covered every call).
  Second parameterized route (`#deck/<deckId>`, same recipe as W4b's `#card/<id>`). `DeckListScreen`
  rows now navigate into it. Found a genuinely new Playwright-methodology gotcha (not a code bug):
  synthetic fast-typing immediately followed by a click can outrun Compose-wasmJs's TextField→state
  commit by one JS tick, producing a real 1-char-short write — caught via a direct Supabase
  `execute_sql` check (not just a screenshot), confirmed fixed with a typing delay/settle wait, no
  `.kt` change needed (same TextField+button pattern used unincidentally elsewhere in the app).
  Verified live in Chromium: full add→increment→decrement→rename→format-change→remove loop, then a
  fresh-browser deep-link reload from captured `storageState` reproducing the exact deck state.
  Zero overflow at 768/375px (programmatic check), correct COMPACT-stacked vs. MEDIUM/LARGE-
  side-by-side responsive split. `:app:assembleDebug` green; diff confirmed `:webApp`-only. Next:
  Deck Studio porting (bigger, own slice, now de-risked) or the W4-placeholder/nav-unification
  options, per NEXT STEP.
- 2026-08-04: **Web W4d done** (commit `aa2a8e0e`): fourth and LAST W4 slice — a real Home screen
  (`webApp/.../home/`) replacing the `ThemeShowcaseScreen` fallthrough the "Home" tab used since
  W4a. Static greeting + 3 quick-link buttons (Search/Decks/Collection) + two capped-to-5 `LazyRow`
  strips (Recent Decks, Recently Added), backed by `HomeViewModel` — a pure client-side sort+cap
  over `DeckRepository.observeAllDeckSummaries`/`UserCardRepository.observeCollection` (both real
  since W3c/W3d). Zero new repository/backend work. Found + fixed a real `MagicCtaButton` bug
  (core-ui): its internal content `Row` applies its own `fillMaxWidth()`, so 3 buttons side-by-side
  in a plain `Row` each claimed the full width and only the first rendered — fixed locally with
  `Modifier.weight(1f)` per button in `HomeScreen.kt` (no core-ui change; RowScope-only fix).
  Verified live in Chromium: guest sign-in → created a deck → searched+added a card → deep-linked
  `#home` reload shows both real ("New Deck"/"Lightning Bolt" with real art) → all 3 quick-links +
  both recent-item taps navigate correctly (`#search`/`#decks`/`#collection`/`#deck/<id>`/
  `#card/<id>`) → zero overflow at 375/768/1280px (programmatic + visual). `:app:assembleDebug`
  green (UP-TO-DATE); diff confirmed 4 files, all `:webApp`-only. This completes the master plan's
  originally-scoped MVP screen list — NEXT STEP flags that W5 should get a fresh scope pass rather
  than blindly starting, since every W4 screen was scoped WAY down from the plan's assumptions.
- 2026-08-04: **Web W5b done** (commits `b6ba4989` + `a42e12bf`): responsive regression sweep of
  all 8 real screens at 320px/1920px+ (edge widths never covered by the standard 375/768/1280px
  per-screen gate). Found + fixed a cross-cutting `AdaptiveScaffold` bug (the LARGE-breakpoint
  1200.dp clamp was a `fillMaxWidth()`-before-`widthIn(max=)` Compose modifier-ordering no-op,
  confirmed via a new DOM-ruler-overlay measurement technique — content spanned ~1656.dp instead of
  the documented ~1136.dp at 1920px; fixed by reordering) plus a bottom-bar nav-label mid-word-wrap
  bug (added maxLines=1+ellipsis). Found + fixed two Deck-Editor-only bugs at 320px (the 3-chip
  Format row overflowed and wrapped "Draft" one character per line; the board-row price/quantity
  text wrapped mid-number since `CardListItem(weight(1f))` was squeezed against three 48.dp
  `IconButton`s) — both fixed with `horizontalScroll` (touch targets never shrunk below 48.dp).
  Every other screen (Theme, Home, Account, Card Search, Card Detail, Decks, Collection) was clean
  at both widths, verified with real populated-state data via a full guest-sign-in → search → add →
  create-deck → deck-editor → home → card-detail loop run at each width. `:app:assembleDebug`
  UP-TO-DATE both times (zero Android source touched). This completes the master plan's W5
  responsive-safety-net scope; the telemetry-framework choice remains a real, open user decision
  (not implemented), and W3's News/CommunityDecks slices + the Android-nav-unification question stay
  deferred/undecided per NEXT STEP.
