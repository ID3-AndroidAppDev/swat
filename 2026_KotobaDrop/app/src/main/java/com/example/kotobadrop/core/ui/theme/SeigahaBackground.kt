package com.example.kotobadrop.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A large-scale seigaiha (青海波, "blue ocean wave") pattern — rows of overlapping concentric
 * semicircles — drawn directly rather than as a bitmap, per CLAUDE.md §10. Deliberately faint:
 * a quiet background texture, not a decoration competing with the sakura-petal burst (the
 * app's actual signature element, step 9). The default alpha is theme-dependent (see
 * [KotobaPalette.seigahaAlpha]) — @Composable so the default can read the palette; every
 * call site builds its modifier chain in composition anyway.
 */
@Composable
fun Modifier.seigahaPattern(
    color: Color = Sora,
    alpha: Float = KotobaTheme.palette.seigahaAlpha,
    waveRadius: Dp = 28.dp,
    ringCount: Int = 3,
): Modifier = this.drawBehind {
    val radius = waveRadius.toPx()
    val ringGap = radius / ringCount
    val strokeColor = color.copy(alpha = alpha)
    val strokeWidth = 1.dp.toPx()
    val rowHeight = radius
    val colWidth = radius * 2f

    var row = 0
    var y = 0f
    while (y < size.height + radius) {
        val xOffset = if (row % 2 == 0) 0f else radius
        var x = -radius + xOffset
        while (x < size.width + radius) {
            for (ring in 0 until ringCount) {
                val r = radius - ring * ringGap
                if (r > 0f) {
                    drawArc(
                        color = strokeColor,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(x - r, y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
            x += colWidth
        }
        y += rowHeight
        row++
    }
}
