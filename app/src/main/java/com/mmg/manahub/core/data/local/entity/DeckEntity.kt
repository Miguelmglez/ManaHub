package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,               // UUID, client-generated
    @ColumnInfo(name = "user_id") val userId: String?,     // null = guest session
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "format") val format: String = "casual",
    @ColumnInfo(name = "cover_card_id") val coverCardId: String? = null,
    @ColumnInfo(name = "commander_card_id") val commanderCardId: String? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    // ── Community Decks attribution (v41) ──────────────────────────────────
    // Populated when a deck is imported from an external community source
    // (e.g. Archidekt). All nullable so locally-created decks remain unaffected.
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    @ColumnInfo(name = "source_author") val sourceAuthor: String? = null,
    @ColumnInfo(name = "source_service") val sourceService: String? = null,
    @ColumnInfo(name = "imported_at") val importedAt: Long? = null,
    // ── Archetype-aware Deck Doctor (v43) ──────────────────────────────────
    // Nullable: null = the engine infers the macro archetype every analysis. A raw enum-name
    // string (ArchetypeId.name), never persisted as anything richer — parsed defensively via
    // `entries.firstOrNull { ... }` at every read site (CLAUDE.md: never `.valueOf()`).
    @ColumnInfo(name = "archetype_override") val archetypeOverride: String? = null,
    // JSON array of theme enum-name strings (ThemeId.name), e.g. `["TRIBAL","ARISTOCRATS"]`.
    // Null/blank = no theme pin. At most 2 entries (enforced at the UI/use-case layer, not here).
    @ColumnInfo(name = "themes_override") val themesOverride: String? = null,
    // ── Deck Engine Unification (v47) ────────────────────────────────────────
    // Raw `tribe:<subtype>` key, nullable (no default pin). A SEPARATE column from
    // archetype_override/themes_override -- see Deck.tribeOverride's KDoc for why.
    @ColumnInfo(name = "tribe_override") val tribeOverride: String? = null,
    // True for a wizard-built deck (D4 hard no-cut guarantee). Additive, defaults false so every
    // pre-migration/manually-created deck is unaffected.
    @ColumnInfo(name = "strategy_locked") val strategyLocked: Boolean = false,
)

/** Cross-reference: which cards belong to which deck (mainboard + sideboard). */
@Entity(
    tableName = "deck_cards",
    primaryKeys = ["deck_id", "scryfall_id", "is_sideboard"],
    foreignKeys = [ForeignKey(
        entity = DeckEntity::class,
        parentColumns = ["id"],
        childColumns = ["deck_id"],
        // CASCADE: deleting a deck removes all its card rows automatically,
        // which is safe because deck_cards has no further dependents.
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deck_id")]
)
data class DeckCardEntity(
    @ColumnInfo(name = "deck_id") val deckId: String,
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "quantity") val quantity: Int = 1,
    @ColumnInfo(name = "is_sideboard") val isSideboard: Boolean = false,
    // Deck Engine Unification (v47, D4): raw DeckCardSource.name, default "USER" so every existing
    // 3/4-arg construction (tests, sync pull, manual DAO calls not yet source-aware) keeps
    // compiling and behaves exactly as before this column existed.
    @ColumnInfo(name = "source") val source: String = "USER",
)
