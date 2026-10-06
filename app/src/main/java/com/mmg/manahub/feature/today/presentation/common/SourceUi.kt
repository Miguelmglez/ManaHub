package com.mmg.manahub.feature.today.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.SourceType

@StringRes
internal fun languageLabelRes(code: String): Int = when (code) {
    "es" -> R.string.today_language_es
    "de" -> R.string.today_language_de
    else -> R.string.today_language_en
}

@StringRes
internal fun sourceKindRes(type: SourceType): Int = when (type) {
    SourceType.ARTICLE -> R.string.today_kind_articles
    SourceType.VIDEO -> R.string.today_kind_youtube
}

/** "Articles · English" / "YouTube channel · Spanish". */
@Composable
internal fun sourceKindAndLanguage(source: ContentSource): String =
    stringResource(
        R.string.today_source_subtitle,
        stringResource(sourceKindRes(source.type)),
        stringResource(languageLabelRes(source.language)),
    )

/** "Open channel" for YouTube sources, "Open site" otherwise. */
@StringRes
internal fun openSiteLabelRes(type: SourceType): Int = when (type) {
    SourceType.VIDEO -> R.string.today_source_open_channel
    SourceType.ARTICLE -> R.string.today_source_open_site
}

/** One or two letters for the avatar fallback: initials of the first two words, else the first two letters. */
internal fun sourceInitials(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.firstOrNull()?.isLetterOrDigit() == true }
    val initials = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> name.take(1)
    }
    return initials.uppercase()
}
