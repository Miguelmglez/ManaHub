package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.news.ReleaseCalendar
import com.mmg.manahub.feature.news.domain.events.ReleaseCalendarBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Events data from the cached, rate-limited Scryfall set list (`CardRepository.getPlayableSets`); never a new request path. */
class GetUpcomingReleasesUseCase(
    private val cardRepository: CardRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    suspend operator fun invoke(): Result<ReleaseCalendar> =
        when (val sets = cardRepository.getPlayableSets()) {
            is DataResult.Success -> Result.success(ReleaseCalendarBuilder.build(sets.data, today()))
            is DataResult.Error -> Result.failure(IllegalStateException(sets.message))
        }
}
