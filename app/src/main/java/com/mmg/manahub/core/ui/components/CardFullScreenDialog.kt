package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Full-screen dialog showing a single card at maximum size.
 *
 * Supports double-faced cards (DFCs): if [card.cardFaces] is non-null, a flip FAB
 * animates a rotationY flip and swaps the image to the back face.
 */
@Composable
fun CardFullScreenDialog(
    card: Card,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    var showingBack by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (showingBack) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "cardFlipRotation",
    )

    val displayImage = if (showingBack && card.cardFaces != null) {
        card.imageBackNormal ?: card.cardFaces.getOrNull(1)?.imageNormal
    } else {
        card.imageNormal
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mc.background.copy(alpha = 0.96f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Card image with flip animation.
                AsyncImage(
                    model             = displayImage,
                    contentDescription = card.name,
                    contentScale      = ContentScale.Fit,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .aspectRatio(63f / 88f)
                        .graphicsLayer { rotationY = rotation },
                )

                // Info row: name, type line, mana cost.
                Surface(
                    color  = mc.surface.copy(alpha = 0.9f),
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text     = card.name,
                                style    = ty.titleMedium,
                                color    = mc.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (card.manaCost != null) {
                                Text(
                                    text  = card.manaCost,
                                    style = ty.bodySmall,
                                    color = mc.textSecondary,
                                )
                            }
                        }
                        Text(
                            text  = card.typeLine,
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                    }
                }
            }

            // Close button — top start.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(mc.surface.copy(alpha = 0.85f), CircleShape),
            ) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint               = mc.textPrimary,
                )
            }

            // Flip FAB — bottom end (only for DFCs).
            if (card.cardFaces != null) {
                FloatingActionButton(
                    onClick          = { showingBack = !showingBack },
                    containerColor   = mc.primaryAccent,
                    contentColor     = mc.background,
                    modifier         = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(24.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = stringResource(R.string.playtest_flip),
                            style = ty.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
