package com.example.kotobadrop.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Matcha
import com.example.kotobadrop.core.ui.theme.Sumi

/**
 * Shared building blocks for the 2026-07-20 redesign pass: quiet all-caps section labels,
 * white/kinari section cards, a tinted 2x2 stat grid, and the Campaign padlock icon — used
 * across Home, Endless Setup, Results, Level Result, Settings, Stats, Credits, Campaign.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = KotobaTheme.palette.ink.copy(alpha = 0.4f)) {
    Text(
        text = text,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em),
        color = color,
        modifier = modifier,
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KotobaTheme.palette.cardBackground, RoundedCornerShape(14.dp)),
    ) {
        content()
    }
}

data class StatCellData(val value: String, val label: String, val tint: Color)

/** A 2-column grid of tinted stat cells, per Results/Level Result/Stats. */
@Composable
fun StatGrid(cells: List<StatCellData>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cell ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(cell.tint, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(cell.value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KotobaTheme.palette.ink)
                        Text(cell.label, fontSize = 12.sp, color = KotobaTheme.palette.ink.copy(alpha = 0.6f))
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A compact selectable chip — a deliberately tighter-padded, smaller-text replacement for
 * Material3's [androidx.compose.material3.FilterChip]. FilterChip's fixed internal padding
 * is generous enough that a 4-option row (e.g. Speed: Easy/Normal/Hard/Expert) wraps to two
 * rows once translated to Japanese, where every label is both longer in character count and
 * wider per character (full-width glyphs) than its English counterpart — English fits one
 * row, Japanese didn't. This chip's padding/font are sized to keep the same option rows on
 * one line in both languages.
 */
@Composable
fun SelectableChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 11.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 9.dp,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        fontSize = 13.sp,
        maxLines = 1,
        color = if (selected) Sumi else KotobaTheme.palette.ink,
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Matcha, shape)
                } else {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    )
}

/**
 * A simple stroke padlock (rect body + arc shackle), matching the design handoff's SVG
 * exactly — drawn rather than an emoji/bitmap so its stroke weight can match other in-app
 * iconography, per the redesign handoff's explicit "not an emoji" requirement.
 */
@Composable
fun PadlockIcon(modifier: Modifier = Modifier, tint: Color = KotobaTheme.palette.ink.copy(alpha = 0.3f)) {
    Canvas(modifier = modifier.size(16.dp)) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val strokeWidth = 2.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(5f * scaleX, 11f * scaleY),
            size = Size(14f * scaleX, 10f * scaleY),
            cornerRadius = CornerRadius(2f * scaleX, 2f * scaleY),
            style = Stroke(width = strokeWidth),
        )
        val shackle = Path().apply {
            moveTo(8f * scaleX, 11f * scaleY)
            lineTo(8f * scaleX, 7f * scaleY)
            arcTo(
                rect = Rect(offset = Offset(8f * scaleX, 3f * scaleY), size = Size(8f * scaleX, 8f * scaleY)),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(16f * scaleX, 11f * scaleY)
        }
        drawPath(shackle, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}
