package com.mmg.magicfolder.feature.news.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.news.domain.model.NewsItem
import com.mmg.magicfolder.feature.news.presentation.TimeAgoFormatter

@Composable
fun ArticleCard(
    article: NewsItem.Article,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    languageBadge: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        if (article.imageUrl != null) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = article.title,
                    style = mt.bodyLarge,
                    color = mc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (languageBadge != null) {
                    LanguageBadge(languageBadge)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${article.sourceName}  ·  ${TimeAgoFormatter.format(article.publishedAt)}",
                style = mt.labelSmall,
                color = mc.textSecondary,
                maxLines = 1,
            )
            if (article.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = article.description,
                    style = mt.bodySmall,
                    color = mc.textDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun LanguageBadge(code: String) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = mc.primaryAccent.copy(alpha = 0.15f),
    ) {
        Text(
            text = code.uppercase(),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.primaryAccent,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
