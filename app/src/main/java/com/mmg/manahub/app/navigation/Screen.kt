package com.mmg.manahub.app.navigation

import android.net.Uri

sealed class Screen(val route: String) {

    // ── Root ─────────────────────────────────────────────────────────────────
    object Splash : Screen("splash")

    /** Free-first dashboard — the app start destination and left bottom-bar tab. */
    object Home : Screen("home")

    // ── Collection (bottom tab 1) ────────────────────────────────────────────
    /** Bottom-tab root — also hosts the Cards/Decks/Trades sub-tab row. */
    object Collection : Screen("collection?tab={tab}") {
        /** Base route for the bottom-tab destination. */
        const val baseRoute = "collection"

        /** Builds a route that opens the collection with a specific tab selected. */
        fun routeWithTab(tab: String) = "collection?tab=$tab"
    }
    object CollectionAddCard  : Screen("collection/add")
    object CollectionMassiveAddCard  : Screen("collection/massiveAddCard")

    object CollectionScanner  : Screen("collection/scanner")
    object CollectionCardDetail : Screen("collection/detail/{scryfallId}?sharedTransitionKey={sharedTransitionKey}") {
        fun createRoute(scryfallId: String, sharedTransitionKey: String? = null) =
            "collection/detail/${Uri.encode(scryfallId)}" + (sharedTransitionKey?.let { "?sharedTransitionKey=${Uri.encode(it)}" } ?: "")
    }

    // ── Decks (sub-section of Collection) ────────────────────────────────────
    // The legacy `DeckDetail` route (`DeckMagicDetailScreen`) was RETIRED in the Deck Wizard &
    // Engine Rework plan, Workstream 7.1 (2026-07-28) — Deck Studio (below) is the single
    // create+edit surface. Do not re-add this route.

    /**
     * Unified hybrid deck builder ("Deck Studio") — combines manual editing,
     * card suggestions, and seed-based auto-build in a single destination.
     * The optional [deckId] query parameter opens an existing deck; when absent a
     * fresh draft is created.
     *
     * @property route also carries an optional `fromDraft` flag (Phase E, E.10a) — true ONLY when
     *   this destination is reached from the Draft Simulator's "deck saved" hand-off. It exists
     *   purely to special-case `onBack`'s target in `AppNavGraph` (the draft flow that would
     *   otherwise be the natural "previous" back-stack entry lands on `Screen.Draft`, the set
     *   browser — not a place a user managing a freshly-created deck expects to end up — so a
     *   `fromDraft` open instead routes back to `Screen.Collection`'s Decks tab). Every other entry
     *   point (Collection/Stats/Home/CardDetail/CommunityDeck import/Wizard hand-off) omits it and
     *   keeps the plain `popBackStack()` behavior unchanged.
     */
    object DeckStudio : Screen("deck/studio?deckId={deckId}&fromDraft={fromDraft}") {
        /** Base route for the destination when no deck id is supplied (creates a fresh draft). */
        const val baseRoute = "deck/studio"

        /**
         * Builds the route. A non-empty [deckId] opens an existing deck in the studio;
         * null/empty creates a new draft. [fromDraft] defaults to false — pass true ONLY from the
         * Draft Simulator's deck-saved hand-off (see the class KDoc).
         */
        fun createRoute(deckId: String? = null, fromDraft: Boolean = false): String {
            val params = mutableListOf<String>()
            if (!deckId.isNullOrEmpty()) params += "deckId=$deckId"
            if (fromDraft) params += "fromDraft=true"
            return if (params.isEmpty()) baseRoute else "$baseRoute?${params.joinToString("&")}"
        }
    }

    object DeckAddCards : Screen("collection/decks/{deckId}/add") {
        fun createRoute(deckId: String) = "collection/decks/$deckId/add"
    }

    /**
     * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.4) — the 4-step wizard + generation +
     * result flow. A single destination with INTERNAL phase state (mirrors the Playtest
     * mulligan/battle-phase-in-one-screen precedent), never a second nav destination per step.
     * Always creates a FRESH draft (no `deckId` arg, unlike [DeckStudio]) — the wizard owns its own
     * deck-creation lifecycle and hands off to [DeckStudio] only on the Result screen's "Open in
     * Deck Studio" CTA.
     *
     * Optional query args pre-fill the wizard from a Discoveries v2 "Build this" tap (D11). Deck
     * Engine Unification plan (D2): these carry the UNIFIED taxonomy directly — [archetype] is a
     * raw [com.mmg.manahub.feature.decks.domain.engine.ArchetypeId] enum name, [theme] a raw
     * [com.mmg.manahub.feature.decks.domain.engine.ThemeId] enum name, [tribe] a raw
     * `tribe:<subtype>` key, [colors] a concatenated
     * [com.mmg.manahub.feature.decks.domain.engine.ManaColor] symbol string (e.g. "WU"). All blank
     * by default (a plain "Build from seed" entry point passes none).
     */
    object DeckWizard : Screen("deck/wizard?archetype={archetype}&theme={theme}&tribe={tribe}&colors={colors}&seeds={seeds}") {
        const val baseRoute = "deck/wizard"

        /**
         * @param seeds Deck Engine Unification plan D7 (Phase 4.3) — a Commander Spellbook combo's
         *   component card names, joined with `|` (NOT `,` — many real MTG card names contain a
         *   literal comma, e.g. "Urza, Lord High Artificer") then percent-encoded as ONE query
         *   value; see [com.mmg.manahub.feature.decks.presentation.wizard.DeckWizardViewModel]'s
         *   init for the matching `split("|")`. When present, forces the wizard's Flow A
         *   (cards-first) entry, mirroring how [archetype]/[theme]/[tribe]/[colors] force Flow
         *   B/C's picks.
         */
        fun createRoute(
            archetype: String? = null,
            theme: String? = null,
            tribe: String? = null,
            colors: String? = null,
            seeds: List<String>? = null,
        ): String {
            val params = mutableListOf<String>()
            if (!archetype.isNullOrEmpty()) params += "archetype=${Uri.encode(archetype)}"
            if (!theme.isNullOrEmpty()) params += "theme=${Uri.encode(theme)}"
            if (!tribe.isNullOrEmpty()) params += "tribe=${Uri.encode(tribe)}"
            if (!colors.isNullOrEmpty()) params += "colors=${Uri.encode(colors)}"
            if (!seeds.isNullOrEmpty()) params += "seeds=${Uri.encode(seeds.joinToString("|"))}"
            return if (params.isEmpty()) baseRoute else "$baseRoute?${params.joinToString("&")}"
        }
    }
    // Screen.DeckImprovement (the standalone Deck Doctor screen) was RETIRED in Phase 0.5 of
    // docs/claude-code-prompt-deck-doctor-community.md (D10) — Deck Studio's Suggestions tab
    // is now the sole Deck Doctor UI surface.

    // ── Community Decks (Archidekt browse + import) ──────────────────────────
    /** Community Decks landing / browse screen. */
    object CommunityDecks : Screen("community/decks")

    /** Detail view for a single community deck, identified by its Archidekt numeric id. */
    object CommunityDeckDetail : Screen("community/decks/detail/{archidektId}") {
        fun createRoute(archidektId: Int) = "community/decks/detail/$archidektId"
    }

    /** Community decks that contain a specific card (deep-linkable from card detail). */
    object CommunityDecksByCard : Screen("community/decks/bycard/{cardName}") {
        fun createRoute(cardName: String) = "community/decks/bycard/${Uri.encode(cardName)}"
    }

    // ── Stats (bottom tab 2) ─────────────────────────────────────────────────
    object Stats    : Screen("stats")
    object Settings : Screen("settings")
    object TagDictionary : Screen("settings/tag_dictionary")

    // ── Profile (bottom tab 4) ───────────────────────────────────────────────
    /**
     * Profile screen. Optional [tab] query parameter selects the initial tab
     * (`overview` | `achievements` | `quests`) so Home widgets can deep-link to a specific tab (Phase 2).
     * The plain [route] (no query) defaults to the Overview tab.
     */
    object Profile : Screen("profile?tab={tab}") {
        /** Base route for the bottom-tab destination (no tab argument → Overview). */
        const val baseRoute = "profile"

        /** Builds a route that opens [tab] (e.g. "achievements"). */
        fun routeWithTab(tab: String) = "profile?tab=$tab"
    }
    object FriendsList : Screen("profile/friends")

    // ── Account management (Phase 4b) ─────────────────────────────────────────
    /** Account settings hub — reachable from the "Manage my account" CTA on [AccountSection]. */
    object AccountManagement : Screen("auth/manage")

    /**
     * Final step of the "Change email" flow — reached DIRECTLY from [AccountManagement]. "Change
     * email" skips any reauthentication gate — Supabase's "Secure email change" project setting
     * already double-confirms an email change server-side (see the KDoc on
     * [com.mmg.manahub.core.domain.auth.AuthRepository.confirmEmailUpdate]).
     */
    object UpdateEmail : Screen("auth/update_email")

    /**
     * "Change password" / "Set a password" — reached DIRECTLY from [AccountManagement] (no
     * intermediate reauthentication-code screen: Supabase's "Require current password when
     * updating" project setting protects this server-side instead — see the KDoc on
     * [com.mmg.manahub.core.domain.auth.AuthRepository.updatePassword]).
     */
    object UpdatePassword : Screen("auth/update_password?requireCurrentPassword={requireCurrentPassword}") {
        /**
         * Builds the route for [requireCurrentPassword]: true for "Change password" (account
         * already has an email/password identity — GoTrue requires the current password), false
         * for "Set a password" (Google-only account with none yet — GoTrue skips the check).
         * A plain boolean nav argument is safe here — unlike the retired reauthentication code,
         * it carries no sensitive data.
         */
        fun routeWithRequireCurrentPassword(requireCurrentPassword: Boolean) =
            "auth/update_password?requireCurrentPassword=$requireCurrentPassword"
    }

    /**
     * "Forgot password" recovery-link completion. Reached via a plain `navigate(route)` call from
     * `AppNavGraph.kt`'s reactive `LaunchedEffect(recoverySessionState)` (hardened 2026-08-18 —
     * see that effect's KDoc), which fires whenever `AuthRepository.sessionState` reflects a
     * recovery-authenticated session, regardless of how that session came to exist. The
     * `manahub://auth/recovery` deep link pattern is still declared on the destination but is now
     * an inert, unused secondary entry point — no code path enqueues it.
     */
    object ResetPasswordConfirm : Screen("auth/reset_password_confirm")

    /**
     * Phantom screen that processes an incoming friend invite link.
     * Deep link patterns: `https://manahub.app/invite/{code}` and `manahub://invite/{code}`.
     */
    object FriendsInvite : Screen("friends/invite/{code}") {
        fun createRoute(code: String) = "friends/invite/$code"
    }

    /** Full-screen detail view for a specific friend, identified by their auth UUID. */
    object FriendDetail : Screen("friends/detail/{userId}") {
        fun createRoute(userId: String) = "friends/detail/$userId"
    }

    // ── Game flow (central FAB) ───────────────────────────────────────────────

    /**
     * Game setup screen. Supports an optional [joinCode] query parameter so that
     * deep-link join flows can pre-open the join sheet without a separate lobby screen.
     */
    object GameSetup : Screen("game/setup?joinCode={joinCode}") {
        /** Base route used for navigation when no join code is needed. */
        const val baseRoute = "game/setup"

        /** Builds a route that pre-fills the join sheet with [code]. */
        fun routeWithJoinCode(code: String) = "game/setup?joinCode=$code"
    }
    object GamePlay   : Screen("game/play/{mode}/{playerCount}") {
        fun createRoute(mode: String, playerCount: Int) = "game/play/$mode/$playerCount"
    }
    object GameResult : Screen("game/result")
    object GameSurvey : Screen("game/survey/{sessionId}?mode={mode}") {
        fun createRoute(sessionId: Long, mode: String = "COMPLETE") = "game/survey/$sessionId?mode=$mode"
    }

    // ── Tournament flow ───────────────────────────────────────────────────────
    object TournamentList   : Screen("tournament/list")
    object TournamentSetup  : Screen("tournament/setup")
    object TournamentDetail : Screen("tournament/{tournamentId}") {
        fun route(id: Long) = "tournament/$id"
    }

    // ── News ──────────────────────────────────────────────────────────────────
    object News : Screen("news")
    object NewsSourcesSettings : Screen("news_sources_settings")
    object NewsVideoPlayer : Screen("news/video/{videoId}?title={title}") {
        fun createRoute(videoId: String, title: String) =
            "news/video/${Uri.encode(videoId)}?title=${Uri.encode(title)}"
    }

    // ── Competitive ──────────────────────────────────────────────────────────────────

    object Competitive : Screen("competitive")

    // ── Draft ─────────────────────────────────────────────────────────────────
    object Draft : Screen("draft")
    object DraftSetDetail : Screen("draft/{setCode}?setName={setName}&setIconUri={setIconUri}&setReleasedAt={setReleasedAt}") {
        fun createRoute(
            setCode: String,
            setName: String,
            setIconUri: String,
            setReleasedAt: String,
        ) = "draft/$setCode?setName=${Uri.encode(setName)}&setIconUri=${Uri.encode(setIconUri)}&setReleasedAt=${Uri.encode(setReleasedAt)}"
    }

    // ── Draft Simulator ──────────────────────────────────────────────────────

    /** Setup screen: choose draft mode and optional timer. Entry from DraftSetDetail. */
    object DraftSimSetup : Screen("draft/sim/setup/{setCode}") {
        fun createRoute(setCode: String) = "draft/sim/setup/$setCode"
    }

    /**
     * Merged draft-flow screen: active drafting (Picks tab) and deck preview/save (Deck tab) in one
     * destination (Phase C collapse — previously a separate `DraftSimResult` destination existed;
     * that nav hop is gone, both panes now live under this single route and switch via an in-screen
     * TabRow instead of `navController.navigate`).
     */
    object DraftSimDrafting : Screen("draft/sim/drafting/{sessionId}") {
        fun createRoute(sessionId: String) = "draft/sim/drafting/$sessionId"
    }

    // ── Online multiplayer lobby ──────────────────────────────────────────────

    /** Host lobby — configure and create a new online session. */
    object LobbyHost : Screen("online/lobby/host?mode={mode}&playerCount={playerCount}") {
        fun route(mode: String = "", playerCount: Int = 0) =
            "online/lobby/host?mode=$mode&playerCount=$playerCount"
    }

    /**
     * Join lobby — enter a 6-character code and wait for the session to start.
     * The [prefilledCode] parameter is optional (used by deep links and invite flows).
     */
    object LobbyJoin : Screen("online/lobby/join?code={code}") {
        /** Builds the route with an optional pre-filled join code. */
        fun route(code: String = "") = "online/lobby/join?code=$code"
    }

    // ── Deck Playtest ─────────────────────────────────────────────────────────

    /** Setup screen: choose draw count and on-the-play/draw before the first hand. */
    object PlaytestSetup : Screen("playtest/setup/{deckId}") {
        fun createRoute(deckId: String) = "playtest/setup/$deckId"
    }

    /**
     * Hand screen: draw, redraw, mulligan, keep, and save the test.
     * The [PlaytestSetup] object is passed in-memory from the setup screen —
     * no nav args needed for the full setup payload.
     */
    object PlaytestHand : Screen("playtest/hand/{deckId}") {
        fun createRoute(deckId: String) = "playtest/hand/$deckId"
    }

    // ── Daily Puzzle (ADR-006) ──────────────────────────────────────────────
    // Repurposes the pre-existing "puzzle" route stub (formerly `object Puzzle`, unreferenced by
    // any composable) rather than minting a second, parallel route for the same feature.
    object DailyPuzzle : Screen("puzzle")

    // ── Trades (sub-section of Collection, also handles deep links) ───────────
    /**
     * Deep link target for shared wishlist / open-for-trade lists.
     * App Link pattern: https://miguelmglez.github.io/list/{shareId}
     */
    object TradesSharedList : Screen("trades/shared/{shareId}") {
        fun createRoute(shareId: String) = "trades/shared/$shareId"
    }

    // ── Trade proposal flow ───────────────────────────────────────────────────
    object CreateTradeProposal : Screen(
        "trades/proposal/create/{receiverId}?parentProposalId={parentProposalId}&editingProposalId={editingProposalId}&rootProposalId={rootProposalId}"
    ) {
        fun createRoute(receiverId: String) = "trades/proposal/create/$receiverId"
        fun createCounterRoute(receiverId: String, parentProposalId: String, rootProposalId: String) =
            "trades/proposal/create/${Uri.encode(receiverId)}?parentProposalId=${Uri.encode(parentProposalId)}&rootProposalId=${Uri.encode(rootProposalId)}"
        fun createEditRoute(receiverId: String, editingProposalId: String, rootProposalId: String) =
            "trades/proposal/create/${Uri.encode(receiverId)}?editingProposalId=${Uri.encode(editingProposalId)}&rootProposalId=${Uri.encode(rootProposalId)}"
    }

    object TradeNegotiationDetail : Screen("trades/proposal/{proposalId}/thread/{rootProposalId}") {
        fun createRoute(proposalId: String, rootProposalId: String) =
            "trades/proposal/$proposalId/thread/$rootProposalId"
    }
}

