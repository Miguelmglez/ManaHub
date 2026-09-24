package com.mmg.manahub.feature.news.domain.source

import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException

/** What a pasted "add source" input points at, after normalization to HTTPS. */
sealed class SourceInput {
    data class YouTubeFeed(val channelId: String) : SourceInput()
    data class YouTubeChannelId(val channelId: String) : SourceInput()

    /** An `@handle`, `/c/…` or `/user/…` channel page that must be fetched once to find the channel id. */
    data class YouTubeHandle(val pageUrl: String) : SourceInput()
    data class Web(val url: String) : SourceInput()
}

object SourceInputClassifier {

    private val HANDLE = Regex("^@[\\w.-]+$")
    private val CHANNEL_PATH = Regex("^/channel/(UC[\\w-]{22})(?![\\w-])")
    private val HANDLE_PATH = Regex("^/(@[^/?#]+|c/[^/?#]+|user/[^/?#]+)")

    /** Fails with [SourceResolveError.INVALID_INPUT] or [SourceResolveError.NOT_HTTPS]; `http://` is upgraded. */
    fun classify(raw: String): Result<SourceInput> {
        val input = raw.trim()
        if (input.isEmpty()) return failure(SourceResolveError.INVALID_INPUT)
        if (HANDLE.matches(input)) return Result.success(SourceInput.YouTubeHandle("https://www.youtube.com/$input"))
        if (YouTubeUrls.isChannelId(input)) return Result.success(SourceInput.YouTubeChannelId(input))

        val parsed = SourceUrl.parse(if (SourceUrl.hasScheme(input)) input else "https://$input")
            ?: return failure(SourceResolveError.INVALID_INPUT)
        val url = when (parsed.scheme) {
            "https" -> parsed
            "http" -> parsed.copy(scheme = "https")
            else -> return failure(SourceResolveError.NOT_HTTPS)
        }

        if (url.host == "youtu.be") return failure(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND)
        if (!YouTubeUrls.isYouTubeHost(url.host)) return Result.success(SourceInput.Web(url.toString()))

        if (url.path == "/feeds/videos.xml") {
            val channelId = url.queryParameter("channel_id")?.takeIf(YouTubeUrls::isChannelId)
            return Result.success(
                if (channelId != null) SourceInput.YouTubeFeed(channelId)
                else SourceInput.Web(url.copy(host = "www.youtube.com", port = null).toString()),
            )
        }
        CHANNEL_PATH.find(url.path)?.let { return Result.success(SourceInput.YouTubeChannelId(it.groupValues[1])) }
        HANDLE_PATH.find(url.path)?.let {
            return Result.success(SourceInput.YouTubeHandle("https://www.youtube.com/${it.groupValues[1]}"))
        }
        // Videos, playlists and the home page carry other channels' ids; only channel links are safe.
        return failure(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND)
    }

    private fun failure(error: SourceResolveError): Result<SourceInput> =
        Result.failure(SourceResolveException(error))
}
