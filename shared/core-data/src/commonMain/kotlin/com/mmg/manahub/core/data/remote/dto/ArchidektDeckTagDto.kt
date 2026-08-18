package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * One entry from Archidekt's closed deck-tag catalog, `GET /api/decks/tags/v2/` (no auth, no
 * query params — verified live 2026-08-18: 434 entries, ~49 KB, alphabetically ordered). Used to
 * back the Advanced Search sheet's "Deck tag" picker, which filters this list client-side rather
 * than querying Archidekt per keystroke.
 *
 * [createdAt] is intentionally not modeled — unused by this app.
 */
@Serializable
data class ArchidektDeckTagDto(
    val id: Int = 0,
    val name: String = "",
    val aliases: String = "",
    val description: String = "",
)
