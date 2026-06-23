package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.coroutines.launch

private val PULL_TRIGGER_HEIGHT = 56.dp
private val PULL_MAX_DRAG_HEIGHT = 96.dp

data class PullRefreshState(
    val nestedScrollConnection: NestedScrollConnection,
    val headerHeightDp: Dp,
    val dragFraction: Float,
)

@Composable
fun rememberPullRefreshState(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
): PullRefreshState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val triggerPx = with(density) { PULL_TRIGGER_HEIGHT.toPx() }
    val maxDragPx = with(density) { PULL_MAX_DRAG_HEIGHT.toPx() }

    val headerHeight = remember { Animatable(0f) }
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && headerHeight.value > 0f) {
            headerHeight.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentIsRefreshing) return Offset.Zero
                if (available.y < 0f && headerHeight.value > 0f) {
                    val toConsume = (-available.y).coerceAtMost(headerHeight.value)
                    scope.launch { headerHeight.snapTo(headerHeight.value - toConsume) }
                    return Offset(0f, -toConsume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (currentIsRefreshing) return Offset.Zero
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    val resistance = if (headerHeight.value > triggerPx) 0.35f else 0.65f
                    val newH = (headerHeight.value + available.y * resistance).coerceIn(0f, maxDragPx)
                    scope.launch { headerHeight.snapTo(newH) }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!currentIsRefreshing) {
                    if (headerHeight.value >= triggerPx) {
                        currentOnRefresh()
                        headerHeight.animateTo(triggerPx, spring(stiffness = Spring.StiffnessMedium))
                    } else {
                        headerHeight.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }
                return super.onPreFling(available)
            }
        }
    }

    val headerHeightDp: Dp = with(density) { headerHeight.value.toDp() }
    val dragFraction = (headerHeight.value / triggerPx).coerceIn(0f, 1f)

    return PullRefreshState(
        nestedScrollConnection = nestedScrollConnection,
        headerHeightDp = headerHeightDp,
        dragFraction = dragFraction,
    )
}

/**
 * A header composable that shows a pull-to-refresh indicator.
 *
 * @param height The current height of the header based on drag distance.
 * @param isRefreshing Whether a refresh is currently in progress.
 * @param dragFraction The fraction of the drag threshold reached (0..1).
 * @param refreshingText The text to display while refreshing (e.g. "Updating...").
 * @param pullIcon The icon to display as a pull-down hint (e.g. Icons.Default.KeyboardArrowDown).
 * @param pullHintDescription The content description for the pull-down arrow icon.
 */
@Composable
fun PullRefreshHeader(
    height: Dp,
    isRefreshing: Boolean,
    dragFraction: Float,
    refreshingText: String,
    pullIcon: ImageVector,
    pullHintDescription: String,
) {
    val mc = MaterialTheme.magicColors
    val showSpinner = isRefreshing || dragFraction >= 1f

    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (showSpinner) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color       = mc.primaryAccent,
                )
                Text(
                    text  = refreshingText,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }
        } else {
            Icon(
                imageVector        = pullIcon,
                contentDescription = pullHintDescription,
                tint               = mc.textSecondary.copy(alpha = dragFraction.coerceIn(0f, 1f)),
                modifier           = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = (1f - dragFraction) * -30f },
            )
        }
    }
}
