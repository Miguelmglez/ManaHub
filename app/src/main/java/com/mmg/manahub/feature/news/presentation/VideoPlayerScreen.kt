package com.mmg.manahub.feature.news.presentation

import android.os.Build
import android.util.Rational
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@Composable
fun VideoPlayerScreen(
    videoId: String,
    title: String,
    onBack: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    BackHandler(onBack = onBack)

    // Enter PiP automatically when the user leaves the screen (home button, recent apps, etc.)
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity?.enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                    .padding(start = 56.dp, end = 16.dp)
                    .fillMaxWidth(),
            )
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl("https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0")
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
