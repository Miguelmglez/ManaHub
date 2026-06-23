package com.mmg.manahub.core.domain.usecase.card

/**
 * KMP migration compatibility re-exports.
 *
 * [SuggestTagsUseCase] and [AutoTagCardUseCase] moved to `:shared:core-data` `commonMain`
 * during the KMP migration. These typealiases keep existing `:app` import paths compiling.
 */
typealias SuggestTagsUseCase = com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
typealias AutoTagCardUseCase = com.mmg.manahub.core.data.usecase.card.AutoTagCardUseCase
