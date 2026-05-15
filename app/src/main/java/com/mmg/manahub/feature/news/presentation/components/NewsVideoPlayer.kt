package com.mmg.manahub.feature.news.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun NewsVideoPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onPlayerReady: (YouTubePlayer) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val playerView = rememberYouTubePlayerView(videoId, onPlayerReady)

    AndroidView(
        factory = { playerView },
        modifier = modifier
    )

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
        }
    }
}

@Composable
private fun rememberYouTubePlayerView(
    videoId: String,
    onPlayerReady: (YouTubePlayer) -> Unit
): YouTubePlayerView {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        YouTubePlayerView(context).apply {
            addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    youTubePlayer.cueVideo(videoId, 0f)
                    onPlayerReady(youTubePlayer)
                }
            })
        }
    }
}
