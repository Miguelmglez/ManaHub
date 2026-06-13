package com.mmg.manahub.core.gamification.domain.event

import java.time.Instant
import java.time.ZoneId

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
    }

    /** A post-game survey was completed. */
    data class SurveyCompleted(
        val surveyId: Long,
        val sessionId: Long,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "survey:$surveyId"
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
            get() = "cards_added:${occurredAt.toEpochMilli()}:$addedUnique:$addedCopies"
    }

    /** A batch of cards was recognised by the scanner. */
    data class CardScanned(
        val scanBatchId: String,
        val count: Int,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "scan:$scanBatchId"
    }

    /** A new deck was created. */
    data class DeckCreated(
        val deckId: String,
        val format: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "deck_created:$deckId"
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
    }

    /** A tournament finished. */
    data class TournamentCompleted(
        val tournamentId: Long,
        val type: String,
        val isLocalWinner: Boolean,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "tournament:$tournamentId"
    }

    /** A trade was completed (accepted by both parties). */
    data class TradeCompleted(
        val tradeId: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "trade:$tradeId"
    }

    /** A friend request was accepted. */
    data class FriendAdded(
        val friendId: String,
        override val occurredAt: Instant,
    ) : ProgressionEvent {
        override val idempotencyKey: String get() = "friend:$friendId"
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
            get() = "explore:$featureKey:${occurredAt.atZone(ZoneId.systemDefault()).toLocalDate()}"
    }
}
