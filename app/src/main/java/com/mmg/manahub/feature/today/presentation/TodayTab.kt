package com.mmg.manahub.feature.today.presentation

import androidx.annotation.StringRes
import com.mmg.manahub.R

/** MTG Today tabs; [routeId] is the `tab` argument of `Screen.MtgToday`. */
enum class TodayTab(val routeId: String, @StringRes val labelRes: Int) {
    FEED("feed", R.string.today_tab_feed),
    EVENTS("events", R.string.today_tab_events),
    SAVED("saved", R.string.today_tab_saved),
    SOURCES("sources", R.string.today_tab_sources),
    ;

    companion object {
        fun fromRouteId(routeId: String?): TodayTab =
            entries.firstOrNull { it.routeId == routeId?.lowercase() } ?: FEED
    }
}
