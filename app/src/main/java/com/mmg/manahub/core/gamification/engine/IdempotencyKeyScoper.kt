package com.mmg.manahub.core.gamification.engine

/**
 * Single source of truth for prefixing LOCAL-ID-DERIVED XP-ledger idempotency keys with a stable
 * per-device id (ADR-002 §L3).
 *
 * ### Why
 * The server ledger PK is `(user_id, idempotency_key)` with `ON CONFLICT DO NOTHING`. A key built from
 * a LOCAL identifier (a local Room autoincrement id like `game:42:result`, a locally-generated UUID, or
 * a local timestamp) is NOT globally unique across devices: two different guest devices can both mint
 * `game:42:result` for two genuinely different games, and after they sync to the same account the
 * server drops the second device's XP as a false duplicate.
 *
 * Prefixing such keys with the per-device id (`dev:{deviceId}:game:42:result`) makes them collision-free
 * across devices while remaining stable PER device, so crash recovery / sync replay on the SAME device
 * still dedupes correctly.
 *
 * ### What must NOT be scoped
 * Keys that are GLOBALLY STABLE per user — server-side ids (`trade`/`friend`), per-user calendar values
 * (`app_open`), and catalog-derived keys produced elsewhere (`achievement:{id}:tier:{n}`,
 * `quest_claim:{instanceId}`) — must stay un-prefixed so two devices intentionally dedupe to a SINGLE
 * grant after a sign-in merge. Prefixing those would pay the same achievement/quest/trade out once per
 * device. This helper is therefore only ever applied to keys flagged
 * [com.mmg.manahub.core.gamification.domain.event.ProgressionEvent.isDeviceScoped].
 */
object IdempotencyKeyScoper {

    /** Prefix marker for device-scoped keys (kept in ONE place so it is consistent and testable). */
    private const val DEVICE_PREFIX = "dev"

    /**
     * Returns [rawKey] prefixed with [deviceId] when [deviceScoped] is true, otherwise [rawKey]
     * unchanged.
     *
     * The produced form is `dev:{deviceId}:{rawKey}`.
     */
    fun scope(rawKey: String, deviceId: String, deviceScoped: Boolean): String =
        if (deviceScoped) "$DEVICE_PREFIX:$deviceId:$rawKey" else rawKey
}
