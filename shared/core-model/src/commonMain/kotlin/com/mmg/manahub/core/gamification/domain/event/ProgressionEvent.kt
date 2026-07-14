package com.mmg.manahub.core.gamification.domain.event

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A domain event emitted on the canonical write path of a user action that may grant
 * progression (XP / achievements / quests / streaks).
 *
 * Events are PII-free and minimal: they carry only what the engine needs to compute
 * rewards. Features emit these on the [com.mmg.manahub.core.gamification.domain.ProgressionEventBus]
 * after a successful commit — they never call the engine directly (see ADR-002 §1).
 *
 * ### Idempotency
 * Every event exposes an [idempotencyKey]. The XP ledger has a UNIQUE index on this key,
 * so re-processing the same event (crash recovery, sync replay) is a no-op. Keys must be
 * deterministic for naturally-idempotent actions (a game result, a survey) and unique-per-
 * occurrence for actions that are NOT naturally idempotent (collection adds — guarded by a
 * daily cap rather than dedup). See ADR-002 §3.
 */
sealed interface ProgressionEvent {

    /** When the underlying action committed. */
    val occurredAt: Instant

    /**
     * Stable, unique key for this event used to deduplicate XP grants in the ledger.
     * MUST be deterministic for naturally-idempotent events and unique-per-occurrence
     * otherwise.
     */
    val idempotencyKey: String

    /**
     * Whether [idempotencyKey] is derived from a LOCAL identifier (a local Room autoincrement id, a
     * locally-generated UUID, or a local timestamp) and is therefore NOT globally unique across two
     * different devices.
     *
     * Such keys MUST be prefixed with a per-device id before they reach the ledger (see
     * [com.mmg.manahub.core.gamification.engine.IdempotencyKeyScoper]) so that two guest devices do not
     * collide on the server's `ON CONFLICT (user_id, idempotency_key) DO NOTHING` — which would silently
     * drop the second device's XP (ADR-002 §L3).
     *
     * Keys that are GLOBALLY STABLE per user — a server-side id (`trade`/`friend`) or a per-user value
     * like the calendar date (`app_open`) — are `false` so that two devices intentionally dedupe to a
     * SINGLE grant after a sign-in merge. (Catalog-derived keys for achievements/quests are produced
     * outside this hierarchy and are likewise never device-scoped.)
     */
    val isDeviceScoped: Boolean

    /**
     * A finished game.
     *
     * @param isLocalWin whether the local seat won. CRITICAL: this is supplied by the
     *   caller from `player_sessions.is_local = 1` seat semantics — never name matching
     *   (see ADR-002 §7 and memory `feedback_survey_winloss_isLocal`). The engine does
     *   NOT recompute it.
     */
    data class GameFinished(
        val sessionId: Long,
        val isLocalWin: Boolean,
        val mode: String,
        val playerCount: Int,
        val durationMs: Long,
        val winTurn: Int?,
        val localFinalLife: Int?,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "game:$sessionId:result"
        // Local Room session id — collides across devices; must be device-scoped.
        override val isDeviceScoped: Boolean get() = true
    }

    /** A post-game survey was completed. */
    data class SurveyCompleted(
        val surveyId: Long,
        val sessionId: Long,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "survey:$surveyId"
        // Local Room survey id — collides across devices; must be device-scoped.
        override val isDeviceScoped: Boolean get() = true
    }

    /**
     * Cards were added to the collection.
     *
     * Collection adds are NOT naturally idempotent (a user can add the same card twice on
     * purpose), so the key embeds the emission timestamp to keep every add distinct. The
     * daily collection XP cap — not dedup — protects against over-granting.
     */
    data class CardsAdded(
        val addedCopies: Int,
        val addedUnique: Int,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String
            get() = "cards_added:${occurredAt.toEpochMilliseconds()}:$addedUnique:$addedCopies"
        // Local emission timestamp — device-local origin; device-scope it for cross-device safety.
        override val isDeviceScoped: Boolean get() = true
    }

    /** A batch of cards was recognised by the scanner. */
    data class CardScanned(
        val scanBatchId: String,
        val count: Int,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "scan:$scanBatchId"
        // Locally-generated scan batch id — device-local origin; device-scope it.
        override val isDeviceScoped: Boolean get() = true
    }

    /** A new deck was created. */
    data class DeckCreated(
        val deckId: String,
        val format: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "deck_created:$deckId"
        // Locally-created deck id — device-local origin; device-scope it.
        override val isDeviceScoped: Boolean get() = true
    }

    /**
     * An existing deck was saved/edited.
     *
     * Grants no XP in v1 (the XP table has no "deck saved" line — [XpGranter] maps it to 0),
     * but the event is retained for Phase 1/2 quest progress.
     */
    data class DeckSaved(
        val deckId: String,
        val cardCount: Int,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "deck_saved:$deckId"
        // Never reaches the ledger (maps to 0 XP) — scoping is irrelevant; false by convention.
        override val isDeviceScoped: Boolean get() = false
    }

    /** A tournament finished. */
    data class TournamentCompleted(
        val tournamentId: Long,
        val type: String,
        val isLocalWinner: Boolean,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "tournament:$tournamentId"
        // Local Room tournament id — collides across devices; must be device-scoped.
        override val isDeviceScoped: Boolean get() = true
    }

    /** A trade was completed (accepted by both parties). */
    data class TradeCompleted(
        val tradeId: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "trade:$tradeId"
        // Server-side trade id — globally stable per user; NOT device-scoped (devices dedupe to one).
        override val isDeviceScoped: Boolean get() = false
    }

    /** A friend request was accepted. */
    data class FriendAdded(
        val friendId: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "friend:$friendId"
        // Server-side friend/user id — globally stable per user; NOT device-scoped.
        override val isDeviceScoped: Boolean get() = false
    }

    /**
     * The app was opened for the first time on a given local day.
     *
     * @param localDate the local calendar day, formatted `yyyy-MM-dd`, so the daily-open
     *   grant is idempotent per day across process restarts.
     */
    data class AppOpenedToday(
        val localDate: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "app_open:$localDate"
        // Calendar date — globally stable per user; NOT device-scoped so the daily check-in pays once
        // per user per day even across two devices (they intentionally dedupe after a sign-in merge).
        override val isDeviceScoped: Boolean get() = false
    }

    /**
     * A feature was explored/used (Phase 2 — drives EXPLORATION quests only).
     *
     * Grants ZERO XP: the ledger is intentionally NOT written for this event (see
     * [com.mmg.manahub.core.gamification.engine.XpGranter] — it maps to `null`). Its only effect is to
     * advance exploration quests. The [idempotencyKey] is per-local-day so re-exploring the same feature
     * multiple times in one day advances the quest at most once (the explore quest target is 1). The
     * key embeds the system-zone local date of [occurredAt]; PII-free ([featureKey] is a stable
     * code-side slug like `"deck_doctor"`, never user content).
     *
     * @param featureKey stable, code-side feature slug (e.g. `"deck_doctor"`).
     */
    data class FeatureExplored(
        val featureKey: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String
            get() = "explore:$featureKey:${occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date}"
        // Never reaches the ledger (0 XP, advances quests only) — scoping is irrelevant; false.
        override val isDeviceScoped: Boolean get() = false
    }
}
