package com.mmg.manahub.feature.trades.domain.repository

/**
 * KMP migration compatibility re-export.
 *
 * [TradesRepository] moved to `:shared:core-data` `commonMain` during the KMP migration.
 * This typealias keeps existing `:app` import paths compiling.
 */
typealias TradesRepository = com.mmg.manahub.core.data.repository.TradesRepository
