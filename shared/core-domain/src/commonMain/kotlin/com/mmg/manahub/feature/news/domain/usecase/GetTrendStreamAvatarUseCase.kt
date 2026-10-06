package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository

/** Loads a profile image from one of the curated Trends Twitch pages. */
class GetTrendStreamAvatarUseCase(
    private val repository: NewsRepository,
) {
    /** Returns the Open Graph image URL when the page exposes one. */
    suspend operator fun invoke(channelId: String): String? = repository.getTrendStreamAvatar(channelId)
}
