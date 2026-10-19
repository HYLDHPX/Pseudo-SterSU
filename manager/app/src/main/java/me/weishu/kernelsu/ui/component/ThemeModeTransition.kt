package me.weishu.kernelsu.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val LightTransitionColor = Color(0xFFF5F7FA)
private val DarkTransitionColor = Color(0xFF10131A)
private const val TRANSITION_DURATION_MS = 420
private const val TRANSITION_MAX_ALPHA = 0.38f

@Composable
fun ThemeModeTransitionOverlay(
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val targetColor = if (darkMode) DarkTransitionColor else LightTransitionColor
    val progress = remember { Animatable(1f) }
    var fromColor by remember { mutableStateOf(targetColor) }
    var toColor by remember { mutableStateOf(targetColor) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(darkMode) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }

        // The theme colors are committed immediately by the root theme. Cover the
        // first frame with the outgoing neutral tone, then dissolve into the new
        // tone so custom backgrounds and Miuix surfaces do not flash white/black.
        fromColor = if (darkMode) LightTransitionColor else DarkTransitionColor
        toColor = targetColor
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = TRANSITION_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    val transitionProgress = progress.value.coerceIn(0f, 1f)
    val alpha = ((1f - transitionProgress) * TRANSITION_MAX_ALPHA).coerceIn(0f, 1f)
    if (initialized && alpha > 0.001f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    lerp(fromColor, toColor, transitionProgress).copy(alpha = alpha),
                ),
        )
    }
}
