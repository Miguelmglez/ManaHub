package com.mmg.manahub.feature.news.domain.source

/** Whether a newly seeded default source starts followed: English, plus the device's own language. */
object DefaultFollowPolicy {

    fun isFollowedByDefault(language: String, deviceLanguage: String): Boolean =
        language == "en" || language == deviceLanguage.lowercase()
}
