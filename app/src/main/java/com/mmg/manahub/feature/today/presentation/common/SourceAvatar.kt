package com.mmg.manahub.feature.today.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.news.domain.source.SourceIconExtractor

@Composable
internal fun SourceAvatar(source: ContentSource) {
    val mc = MaterialTheme.magicColors
    val imageModel = remember(source.iconUrl, source.siteUrl, source.type) {
        source.iconUrl ?: if (source.type == SourceType.ARTICLE) SourceIconExtractor.favicon(source.siteUrl) else null
    }
    var imageFailed by remember(imageModel) { mutableStateOf(imageModel == null) }

    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(mc.surfaceVariant)
            .border(1.dp, mc.primaryAccent.copy(alpha = 0.24f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageFailed && imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true },
            )
        } else {
            Text(
                text = sourceInitials(source.name),
                color = mc.primaryAccent,
                style = MaterialTheme.magicTypography.titleMedium,
            )
        }
    }
}
