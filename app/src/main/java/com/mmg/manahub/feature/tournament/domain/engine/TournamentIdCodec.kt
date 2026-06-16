package com.mmg.manahub.feature.tournament.domain.engine

/**
 * Single source of truth for the tiny hand-rolled "JSON" encoding used by tournament match rows.
 *
 * Match `playerIds` is stored as `"[id1,id2]"` (or `"[id]"` for a bye) and `finalLifeTotals` as
 * `"{id:life,...}"`. Before this codec the `json.trim('[',']').split(",")` idiom was duplicated across
 * 7+ call sites (engines, use-cases, repository, ViewModel, screen), each subtly different and each
 * silently dropping malformed entries via `toLongOrNull`. Centralising here gives ONE tested
 * implementation (audit M2).
 *
 * Encoding notes / invariants:
 * - Values are Longs (ids) / Ints (life) only — no escaping is needed and none is performed.
 * - `trim('[',']')` strips ALL leading/trailing brackets, so `"[[1,2]]"` decodes the same as `"[1,2]"`;
 *   this is intentional defensiveness against historic double-encoding, not a correctness requirement.
 * - Empty / blank input decodes to an empty collection (never throws): `""` → `[]`, `"[]"` → `[]`.
 * - Whitespace around entries is tolerated.
 */
object TournamentIdCodec {

    /** Encodes a player-id list as `"[a,b,...]"` (matches the legacy on-disk format exactly). */
    fun encodeIds(ids: List<Long>): String = ids.joinToString(",", "[", "]")

    /**
     * Decodes a `"[a,b,...]"` player-id list. Empty/blank input and malformed entries yield an empty
     * or partial list rather than throwing — the caller treats a 1-element list as a bye and a
     * 2-element list as a pairing, so a silently-dropped garbage entry degrades gracefully.
     */
    fun decodeIds(json: String): List<Long> {
        if (json.isBlank()) return emptyList()
        return json.trim('[', ']')
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
    }

    /** Encodes a per-player life map as `"{id:life,...}"` (matches the legacy on-disk format exactly). */
    fun encodeLifeTotals(lifeTotals: Map<Long, Int>): String =
        lifeTotals.entries.joinToString(",", "{", "}") { "${it.key}:${it.value}" }

    /**
     * Decodes a `"{id:life,...}"` life map. Blank input → empty map. Entries without a `:` or with a
     * non-numeric id/life are skipped (never throws). Negative life is preserved.
     */
    fun decodeLifeTotals(json: String): Map<Long, Int> {
        if (json.isBlank()) return emptyMap()
        return json.trim('{', '}')
            .split(",")
            .mapNotNull { entry ->
                val colonIdx = entry.indexOf(':')
                if (colonIdx == -1) return@mapNotNull null
                val id   = entry.substring(0, colonIdx).trim().toLongOrNull()
                val life = entry.substring(colonIdx + 1).trim().toIntOrNull()
                if (id != null && life != null) id to life else null
            }
            .toMap()
    }
}
