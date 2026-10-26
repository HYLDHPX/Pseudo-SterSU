package me.weishu.kernelsu.ui.component.rain

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.passthrough.MaterialText
import me.weishu.kernelsu.ui.theme.isInDarkTheme

@Composable
fun RainHomeMotto(modifier: Modifier = Modifier) {
    val settings = LocalRainSettings.current
    if (!settings.active) return
    MaterialText(
        text = stringResource(R.string.rain_motto_thunderstorm),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = rainPalette(settings, isInDarkTheme()).content,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
