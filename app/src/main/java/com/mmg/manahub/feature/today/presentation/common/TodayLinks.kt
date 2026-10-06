package com.mmg.manahub.feature.today.presentation.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.mmg.manahub.feature.news.domain.source.YouTubeUrls

/** Opens [url] in a Custom Tab (HTTPS only; `http://` is upgraded). Returns false when nothing could open it. */
internal fun openInBrowser(context: Context, url: String, toolbarColor: Int): Boolean {
    val uri = httpsUri(url) ?: return false
    return try {
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
        true
    } catch (_: Exception) {
        openWithAnyApp(context, uri)
    }
}

/** Opens a source's site; YouTube pages go through ACTION_VIEW so the YouTube app handles them when installed. */
internal fun openSourceSite(context: Context, url: String, toolbarColor: Int): Boolean {
    val uri = httpsUri(url) ?: return false
    val isYouTube = uri.host?.lowercase()?.let(YouTubeUrls::isYouTubeHost) == true
    return if (isYouTube) openWithAnyApp(context, uri) || openInBrowser(context, url, toolbarColor)
    else openInBrowser(context, url, toolbarColor)
}

/** Android share sheet with the item's title and link. */
internal fun shareLink(context: Context, title: String, url: String, chooserTitle: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    }
    context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

private fun openWithAnyApp(context: Context, uri: Uri): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

private fun httpsUri(url: String): Uri? {
    val uri = Uri.parse(url.trim())
    return when (uri.scheme?.lowercase()) {
        "https" -> uri
        "http" -> uri.buildUpon().scheme("https").build()
        else -> null
    }
}
