package com.mmg.manahub.feature.news.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.news.domain.model.NewsItem
import com.mmg.manahub.core.util.TimeAgoFormatter

@Composable
fun VideoCard(
    video: NewsItem.Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    languageBadge: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .clickable(onClick = onClick),
    ) {
        // Thumbnail 16:9 with play overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        ) {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Play button overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            // Language badge — top-right corner of thumbnail
            if (languageBadge != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    LanguageBadge(languageBadge)
                }
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = video.title,
                style = mt.bodyLarge,
                color = mc.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${video.channelName}  ·  ${TimeAgoFormatter.format(video.publishedAt)}",
                style = mt.labelSmall,
                color = mc.textSecondary,
                maxLines = 1,
            )
        }
    }
}
