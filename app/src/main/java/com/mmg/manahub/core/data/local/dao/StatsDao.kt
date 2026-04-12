package com.mmg.manahub.core.data.local.dao


import androidx.room.*
import com.mmg.manahub.core.data.local.entity.projection.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Query("""
        SELECT SUM(uc.quantity) AS totalCards, COUNT(DISTINCT uc.scryfall_id) AS uniqueCards
        FROM user_cards uc
    """)
    fun observeTotals(): Flow<TotalsProjection>

    @Query("""
        SELECT COALESCE(SUM(uc.quantity * CASE
            WHEN uc.is_foil = 1 AND c.price_usd_foil IS NOT NULL THEN c.price_usd_foil
            ELSE COALESCE(c.price_usd, 0) END), 0)
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
    """)
    fun observeTotalValueUsd(): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(uc.quantity * CASE
            WHEN uc.is_foil = 1 AND c.price_eur_foil IS NOT NULL THEN c.price_eur_foil
            ELSE COALESCE(c.price_eur, 0) END), 0)
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
    """)
    fun observeTotalValueEur(): Flow<Double>

    @Query("""
        SELECT c.scryfall_id AS scryfallId, c.name AS name,
               c.image_art_crop AS imageArtCrop, uc.is_foil AS isFoil,
               c.color_identity AS colorIdentity,
               CASE WHEN uc.is_foil = 1 AND c.price_usd_foil IS NOT NULL
                    THEN c.price_usd_foil ELSE COALESCE(c.price_usd, 0) END AS priceUsd,
               CASE WHEN uc.is_foil = 1 AND c.price_eur_foil IS NOT NULL
                    THEN c.price_eur_foil ELSE COALESCE(c.price_eur, 0) END AS priceEur
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        ORDER BY 
            CASE WHEN :useEur = 1 THEN priceEur ELSE priceUsd END DESC 
        LIMIT :limit
    """)
    fun observeMostValuableCards(limit: Int = 5, useEur: Boolean = false): Flow<List<CardValueProjection>>

    @Query("""
        SELECT c.color_identity AS colorIdentity, SUM(uc.quantity) AS count
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        GROUP BY c.color_identity
    """)
    fun observeCountByColorIdentity(): Flow<List<ColorCountProjection>>

    @Query("""
        SELECT c.rarity AS rarity, SUM(uc.quantity) AS count
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        GROUP BY c.rarity
    """)
    fun observeCountByRarity(): Flow<List<RarityCountProjection>>

    @Query("""
        SELECT c.type_line AS typeLine, SUM(uc.quantity) AS count
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        GROUP BY c.type_line
    """)
    fun observeCountByTypeLine(): Flow<List<TypeCountProjection>>

    @Query("""
        SELECT MIN(CAST(c.cmc AS INTEGER), 7) AS cmc, SUM(uc.quantity) AS count
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE c.type_line NOT LIKE '%Land%'
        GROUP BY MIN(CAST(c.cmc AS INTEGER), 7) ORDER BY cmc ASC
    """)
    fun observeManaCurve(): Flow<List<CmcCountProjection>>

    @Query("""
        SELECT c.set_code AS setCode, SUM(uc.quantity) AS count
        FROM user_cards uc INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        GROUP BY c.set_code ORDER BY count DESC
    """)
    fun observeCountBySet(): Flow<List<SetCountProjection>>
}
