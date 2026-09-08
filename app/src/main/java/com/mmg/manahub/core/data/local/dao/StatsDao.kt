package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.projection.ArtistCountProjection
import com.mmg.manahub.core.data.local.entity.projection.CardValueProjection
import com.mmg.manahub.core.data.local.entity.projection.CmcCountProjection
import com.mmg.manahub.core.data.local.entity.projection.ColorCountProjection
import com.mmg.manahub.core.data.local.entity.projection.DecadeCountProjection
import com.mmg.manahub.core.data.local.entity.projection.DuplicateCardProjection
import com.mmg.manahub.core.data.local.entity.projection.FormatCoverageProjection
import com.mmg.manahub.core.data.local.entity.projection.KeywordsProjection
import com.mmg.manahub.core.data.local.entity.projection.RarityCountProjection
import com.mmg.manahub.core.data.local.entity.projection.SetCountProjection
import com.mmg.manahub.core.data.local.entity.projection.SetValueProjection
import com.mmg.manahub.core.data.local.entity.projection.TagProjection
import com.mmg.manahub.core.data.local.entity.projection.TotalsProjection
import com.mmg.manahub.core.data.local.entity.projection.TypeCountProjection
import com.mmg.manahub.core.data.local.entity.projection.UniqueCardPriceProjection
import com.mmg.manahub.core.data.local.entity.projection.VariantCardProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Query("""
        SELECT COALESCE(SUM(uc.quantity), 0) AS totalCards, COUNT(DISTINCT uc.scryfall_id) AS uniqueCards
        FROM user_card_collection uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeTotals(colorFilter: String?, setFilter: String?, userId: String?): Flow<TotalsProjection>

    @Query("""
        SELECT COALESCE(SUM(uc.quantity * CASE
            WHEN uc.is_foil = 1 AND c.price_usd_foil IS NOT NULL THEN c.price_usd_foil
            ELSE COALESCE(c.price_usd, 0) END), 0)
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeTotalValueUsd(colorFilter: String?, setFilter: String?, userId: String?): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(uc.quantity * CASE
            WHEN uc.is_foil = 1 AND c.price_eur_foil IS NOT NULL THEN c.price_eur_foil
            ELSE COALESCE(c.price_eur, 0) END), 0)
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeTotalValueEur(colorFilter: String?, setFilter: String?, userId: String?): Flow<Double>

    @Query("""
        SELECT c.scryfall_id AS scryfallId, c.name AS name,
               c.image_art_crop AS imageArtCrop, c.image_normal AS imageNormal, uc.is_foil AS isFoil,
               c.color_identity AS colorIdentity, c.set_code AS setCode,
               c.set_name AS setName, c.rarity AS rarity,
               CASE WHEN uc.is_foil = 1 AND c.price_usd_foil IS NOT NULL
                    THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END AS priceUsd,
               CASE WHEN uc.is_foil = 1 AND c.price_eur_foil IS NOT NULL
                    THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END AS priceEur
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        ORDER BY
            CASE WHEN :useEur = 1 THEN priceEur ELSE priceUsd END DESC
        LIMIT :limit
    """)
    fun observeMostValuableCards(limit: Int, useEur: Boolean, colorFilter: String?, setFilter: String?, userId: String?): Flow<List<CardValueProjection>>

    @Query("""
        SELECT c.color_identity AS colorIdentity, SUM(uc.quantity) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.color_identity
    """)
    fun observeCountByColorIdentity(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<ColorCountProjection>>

    @Query("""
        SELECT c.rarity AS rarity, SUM(uc.quantity) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.rarity
    """)
    fun observeCountByRarity(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<RarityCountProjection>>

    @Query("""
        SELECT c.type_line AS typeLine, SUM(uc.quantity) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.type_line
    """)
    fun observeCountByTypeLine(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<TypeCountProjection>>

    @Query("""
        SELECT CASE WHEN CAST(c.cmc AS INTEGER) > 7 THEN 7 ELSE CAST(c.cmc AS INTEGER) END AS cmc,
               SUM(uc.quantity) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND c.type_line NOT LIKE '%Land%'
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY CASE WHEN CAST(c.cmc AS INTEGER) > 7 THEN 7 ELSE CAST(c.cmc AS INTEGER) END
        ORDER BY cmc ASC
    """)
    fun observeManaCurve(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<CmcCountProjection>>

    @Query("""
        SELECT c.set_code AS setCode, SUM(uc.quantity) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.set_code ORDER BY count DESC
    """)
    fun observeCountBySet(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<SetCountProjection>>

    // --- Innovative Stats ---

    @Query("""
        SELECT COALESCE(SUM(uc.quantity), 0)
        FROM user_card_collection uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND uc.is_foil = 1
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeTotalFoil(colorFilter: String?, setFilter: String?, userId: String?): Flow<Int>

    @Query("""
        SELECT COALESCE(SUM(uc.quantity), 0)
        FROM user_card_collection uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (c.frame_effects LIKE '%fullart%' OR c.frame_effects LIKE '%borderless%' OR c.promo_types LIKE '%boosterfun%')
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeTotalFullArt(colorFilter: String?, setFilter: String?, userId: String?): Flow<Int>

    @Query("""
        SELECT artist, SUM(uc.quantity) as count FROM cards c
        INNER JOIN user_card_collection uc ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY artist ORDER BY count DESC LIMIT 1
    """)
    fun observeTopArtist(colorFilter: String?, setFilter: String?, userId: String?): Flow<ArtistCountProjection?>

    @Query("""
        SELECT AVG(c.cmc) FROM cards c
        INNER JOIN user_card_collection uc ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND c.type_line NOT LIKE '%Land%'
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeAvgManaValue(colorFilter: String?, setFilter: String?, userId: String?): Flow<Double?>

    @Query("""
        SELECT AVG(CAST(c.power AS REAL)) FROM cards c
        INNER JOIN user_card_collection uc ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND c.power IS NOT NULL AND c.power NOT LIKE '%*%' AND c.power NOT LIKE '%+%'
          AND c.power NOT IN ('X', '∞')
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeAvgPower(colorFilter: String?, setFilter: String?, userId: String?): Flow<Double?>

    @Query("""
        SELECT AVG(CAST(c.toughness AS REAL)) FROM cards c
        INNER JOIN user_card_collection uc ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND c.toughness IS NOT NULL AND c.toughness NOT LIKE '%*%' AND c.toughness NOT LIKE '%+%'
          AND c.toughness NOT IN ('X', '∞')
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeAvgToughness(colorFilter: String?, setFilter: String?, userId: String?): Flow<Double?>

    @Query("""
        SELECT c.scryfall_id AS scryfallId, c.name AS name,
               c.image_art_crop AS imageArtCrop, uc.is_foil AS isFoil,
               c.color_identity AS colorIdentity, c.set_code AS setCode,
               c.set_name AS setName, c.rarity AS rarity,
               CASE WHEN uc.is_foil = 1 AND c.price_usd_foil IS NOT NULL
                    THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END AS priceUsd,
               CASE WHEN uc.is_foil = 1 AND c.price_eur_foil IS NOT NULL
                    THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END AS priceEur
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        ORDER BY c.released_at ASC LIMIT 1
    """)
    fun observeOldestCard(colorFilter: String?, setFilter: String?, userId: String?): Flow<CardValueProjection?>

    @Query("""
        SELECT c.scryfall_id AS scryfallId, c.name AS name,
               c.image_art_crop AS imageArtCrop, uc.is_foil AS isFoil,
               c.color_identity AS colorIdentity, c.set_code AS setCode,
               c.set_name AS setName, c.rarity AS rarity,
               CASE WHEN uc.is_foil = 1 AND c.price_usd_foil IS NOT NULL
                    THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END AS priceUsd,
               CASE WHEN uc.is_foil = 1 AND c.price_eur_foil IS NOT NULL
                    THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END AS priceEur
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        ORDER BY c.released_at DESC LIMIT 1
    """)
    fun observeNewestCard(colorFilter: String?, setFilter: String?, userId: String?): Flow<CardValueProjection?>

    @Query("""
        SELECT c.set_code AS setCode, SUM(uc.quantity) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.set_code ORDER BY count DESC LIMIT 1
    """)
    fun observeTopSetByCount(colorFilter: String?, setFilter: String?, userId: String?): Flow<SetCountProjection?>

    @Query("""
        SELECT c.set_code AS setCode, SUM(uc.quantity * CASE
            WHEN uc.is_foil = 1 AND :useEur = 1 AND c.price_eur_foil IS NOT NULL THEN c.price_eur_foil
            WHEN uc.is_foil = 1 AND :useEur = 0 AND c.price_usd_foil IS NOT NULL THEN c.price_usd_foil
            ELSE (CASE WHEN :useEur = 1 THEN COALESCE(c.price_eur, 0) ELSE COALESCE(c.price_usd, 0) END)
        END) AS totalValue
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.set_code ORDER BY totalValue DESC LIMIT 1
    """)
    fun observeTopSetByValue(colorFilter: String?, setFilter: String?, useEur: Boolean, userId: String?): Flow<SetValueProjection?>

    @Query("""
        SELECT c.tags FROM cards c
        INNER JOIN user_card_collection uc ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.scryfall_id
    """)
    fun observeAllCollectionTags(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<TagProjection>>

    @Query("""
        SELECT DISTINCT c.set_code
        FROM user_card_collection uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
    """)
    fun observeCollectionSetCodes(userId: String?): Flow<List<String>>

    // --- Phase 2 (2026-07 stats expansion) ---

    /**
     * One row per distinct owned card (by scryfall_id) with its base (non-foil) price in both
     * currencies. Used to compute average/median card value — deliberately uses the card's
     * canonical price rather than per-copy foil pricing to keep "unique card value" well-defined
     * when a scryfall_id has both foil and non-foil collection rows.
     */
    @Query("""
        SELECT DISTINCT c.scryfall_id AS scryfallId,
               COALESCE(c.price_usd, 0) AS priceUsd,
               COALESCE(c.price_eur, 0) AS priceEur
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeUniqueCardPrices(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<UniqueCardPriceProjection>>

    @Query("""
        SELECT COALESCE(SUM(uc.quantity * CASE
            WHEN c.price_usd_foil IS NOT NULL THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END), 0)
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND uc.is_foil = 1
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeTotalFoilValueUsd(colorFilter: String?, setFilter: String?, userId: String?): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(uc.quantity * CASE
            WHEN c.price_eur_foil IS NOT NULL THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END), 0)
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND uc.is_foil = 1
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeTotalFoilValueEur(colorFilter: String?, setFilter: String?, userId: String?): Flow<Double>

    @Query("""
        SELECT c.scryfall_id AS scryfallId, c.name AS name, c.image_art_crop AS imageArtCrop,
               MAX(uc.is_foil) AS isFoil, c.color_identity AS colorIdentity, c.set_code AS setCode,
               c.set_name AS setName, c.rarity AS rarity,
               CASE WHEN MAX(uc.is_foil) = 1 AND c.price_usd_foil IS NOT NULL
                    THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END AS priceUsd,
               CASE WHEN MAX(uc.is_foil) = 1 AND c.price_eur_foil IS NOT NULL
                    THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END AS priceEur,
               SUM(uc.quantity) AS totalQuantity
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.scryfall_id
        ORDER BY totalQuantity DESC
        LIMIT 1
    """)
    fun observeMostDuplicatedCard(colorFilter: String?, setFilter: String?, userId: String?): Flow<DuplicateCardProjection?>

    @Query("""
        SELECT
            COUNT(DISTINCT CASE WHEN c.legality_commander = 'legal' THEN c.scryfall_id END) AS commanderCount,
            COUNT(DISTINCT CASE WHEN c.legality_modern = 'legal' THEN c.scryfall_id END) AS modernCount,
            COUNT(DISTINCT CASE WHEN c.legality_standard = 'legal' THEN c.scryfall_id END) AS standardCount
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
    """)
    fun observeFormatCoverage(colorFilter: String?, setFilter: String?, userId: String?): Flow<FormatCoverageProjection>

    @Query("""
        SELECT c.keywords FROM cards c
        INNER JOIN user_card_collection uc ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.scryfall_id
    """)
    fun observeAllCollectionKeywords(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<KeywordsProjection>>

    /**
     * Distinct owned card count per set, GLOBAL (no color/set filter — set completion is
     * inherently per-set and independent of the active Stats filters).
     */
    @Query("""
        SELECT c.set_code AS setCode, COUNT(DISTINCT uc.scryfall_id) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
        GROUP BY c.set_code
    """)
    fun observeDistinctOwnedCountBySet(userId: String?): Flow<List<SetCountProjection>>

    // --- Hall of Fame enrichment (2026-07 stats expansion) ---

    /**
     * The card (by `oracle_id`) owned in the most distinct (set_code, lang) printings — see
     * [VariantCardProjection] for the determinism technique. `oracle_id != ''` guards against
     * blank-oracle-id rows (backfill-pending, per `project_card_versions_languages`) collapsing
     * into one bogus group.
     */
    @Query("""
        SELECT c.scryfall_id AS scryfallId, c.name AS name, c.image_art_crop AS imageArtCrop,
               uc.is_foil AS isFoil, c.color_identity AS colorIdentity, c.set_code AS setCode,
               c.set_name AS setName, c.rarity AS rarity,
               CASE WHEN uc.is_foil = 1 AND c.price_usd_foil IS NOT NULL
                    THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END AS priceUsd,
               CASE WHEN uc.is_foil = 1 AND c.price_eur_foil IS NOT NULL
                    THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END AS priceEur,
               COUNT(DISTINCT c.set_code || '|' || c.lang) AS variantCount,
               MAX(c.released_at) AS releasedAt
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND c.oracle_id != ''
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.oracle_id
        ORDER BY variantCount DESC
        LIMIT 1
    """)
    fun observeMostVariantsCard(colorFilter: String?, setFilter: String?, userId: String?): Flow<VariantCardProjection?>

    /**
     * Up to [limit] distinct owned cards illustrated by [artist], for the Top Artist gallery row.
     * Grouped by `scryfall_id` (same `MAX(uc.is_foil)` representative-row technique as
     * [observeMostDuplicatedCard]) so a card owned as both foil and non-foil renders once.
     */
    @Query("""
        SELECT c.scryfall_id AS scryfallId, c.name AS name, c.image_art_crop AS imageArtCrop,
               c.image_normal AS imageNormal, MAX(uc.is_foil) AS isFoil,
               c.color_identity AS colorIdentity, c.set_code AS setCode,
               c.set_name AS setName, c.rarity AS rarity,
               CASE WHEN MAX(uc.is_foil) = 1 AND c.price_usd_foil IS NOT NULL
                    THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END AS priceUsd,
               CASE WHEN MAX(uc.is_foil) = 1 AND c.price_eur_foil IS NOT NULL
                    THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END AS priceEur
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND c.artist = :artist
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY c.scryfall_id
        ORDER BY c.name ASC
        LIMIT :limit
    """)
    fun observeCardsByArtist(artist: String?, colorFilter: String?, setFilter: String?, userId: String?, limit: Int): Flow<List<CardValueProjection>>

    /**
     * Owned card quantity grouped by release decade (e.g. `decade` = 1990 for the 1990s), derived
     * from `released_at` ("YYYY-MM-DD"). Replaces the retired monthly-additions collection-growth
     * chart (2026-07 stats expansion).
     */
    @Query("""
        SELECT (CAST(SUBSTR(c.released_at, 1, 4) AS INTEGER) / 10) * 10 AS decade,
               COALESCE(SUM(uc.quantity), 0) AS count
        FROM user_card_collection uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE uc.is_deleted = 0
          AND (c.stale_reason IS NULL OR c.stale_reason != 'pending_hydration')
          AND (:userId IS NULL OR uc.user_id = :userId OR uc.user_id IS NULL)
          AND (:colorFilter IS NULL
               OR (:colorFilter = '[]' AND c.color_identity = '[]')
               OR (:colorFilter != '[]' AND c.color_identity LIKE '%' || :colorFilter || '%'))
          AND (:setFilter IS NULL OR c.set_code = :setFilter)
        GROUP BY decade
        ORDER BY decade ASC
    """)
    fun observeCountByDecade(colorFilter: String?, setFilter: String?, userId: String?): Flow<List<DecadeCountProjection>>
}
