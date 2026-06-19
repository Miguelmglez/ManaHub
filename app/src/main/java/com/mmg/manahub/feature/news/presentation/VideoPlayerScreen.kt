package com.mmg.manahub.feature.news.presentation

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mmg.manahub.app.MainActivity
import com.mmg.manahub.core.ui.theme.magicTypography
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun VideoPlayerScreen(
    videoId: String,
    title: String,
    isInPiP: Boolean = false,
    onBack: () -> Unit,
) {
    val mt = MaterialTheme.magicTypography
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onBack)

    DisposableEffect(Unit) {
        activity?.setPlayerActive(true)
        // Allow the user to rotate the device to watch the video in landscape
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.setPlayerActive(false)
            // Restore orientation to portrait when leaving the player
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Top Bar
        if (!isInPiP) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Text(
                    text = title,
                    style = mt.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 48.dp, end = 16.dp)
                        .fillMaxWidth(),
                )
            }
        }

        // Player
        AndroidView(
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    // Force the view to fill the screen instead of defaulting to 16:9
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    lifecycleOwner.lifecycle.addObserver(this)
                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            youTubePlayer.loadVideo(videoId, 0f)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            onRelease = { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
        )
    }
}
