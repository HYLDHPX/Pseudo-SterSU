package me.weishu.kernelsu.ui.component.rain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

internal data class RainLightningGeometry(val trunk: List<Offset>, val branches: List<List<Offset>>)

/** Three cached, screen-sized strikes alternate; their shape never changes during a flash. */
internal fun rainLightningGeometry(size: Size, strike: Int): RainLightningGeometry? {
    if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) return null
    val variant = Math.floorMod(strike, 3)
    val random = Random(0x71A9 + variant)
    val origin = listOf(0.72f, 0.32f, 0.58f)[variant]
    val direction = if (variant == 1) 1f else -1f
    val trunk = List(10) { index ->
        val progress = index / 9f
        val x = origin + direction * progress * 0.14f + (random.nextFloat() - 0.5f) * 0.10f
        Offset(x.coerceIn(0.04f, 0.96f) * size.width, (0.02f + progress * 0.62f) * size.height)
    }
    val branches = listOf(3, 5).mapIndexed { branch, start ->
        val root = trunk[start]
        val side = if (branch == 0) -direction else direction
        listOf(root) + List(4) { index ->
            val progress = (index + 1) / 4f
            Offset(
                (root.x + (side * progress * 0.18f + (random.nextFloat() - 0.5f) * 0.035f) * size.width)
                    .coerceIn(size.width * 0.03f, size.width * 0.97f),
                root.y + progress * size.height * 0.18f,
            )
        }
    }
    return RainLightningGeometry(trunk, branches)
}

internal data class RainThunderPaint(
    val bolts: List<Pair<Path, Path>>,
    val cloudLight: Brush,
    val reflection: Brush,
    val core: Color,
    val glow: Color,
)

internal fun rainThunderPaint(size: Size, palette: RainPalette): RainThunderPaint {
    fun List<Offset>.path() = Path().apply {
        forEachIndexed { index, point ->
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }
    val bolts = List(3) { strike ->
        val geometry = rainLightningGeometry(size, strike)
        (geometry?.trunk.orEmpty().path()) to Path().apply {
            geometry?.branches?.forEach { addPath(it.path()) }
        }
    }
    val glow = lerp(Color(0xFFBCD9FF), palette.rain, 0.2f)
    return RainThunderPaint(
        bolts,
        Brush.radialGradient(
            listOf(glow.copy(alpha = 0.38f), glow.copy(alpha = 0.15f), Color.Transparent),
            Offset(size.width * 0.5f, size.height * 0.15f),
            maxOf(size.width * 0.85f, size.height * 0.65f, 1f),
        ),
        Brush.verticalGradient(listOf(glow.copy(alpha = 0.10f), Color.Transparent)),
        lerp(palette.rain, palette.highlight, 0.75f),
        glow,
    )
}

internal fun DrawScope.drawRainThunder(paint: RainThunderPaint, strength: Float, strike: Int) {
    if (strength <= 0f) return
    drawRect(paint.cloudLight, alpha = strength)
    val (trunk, branches) = paint.bolts[Math.floorMod(strike, paint.bolts.size)]
    drawPath(trunk, paint.glow.copy(alpha = 0.16f * strength),
        style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(trunk, paint.glow.copy(alpha = 0.40f * strength),
        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(branches, paint.glow.copy(alpha = 0.55f * strength),
        style = Stroke(0.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(trunk, paint.core.copy(alpha = 0.92f * strength),
        style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
