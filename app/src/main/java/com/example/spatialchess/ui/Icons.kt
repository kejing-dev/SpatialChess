package com.example.spatialchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.example.spatialchess.R
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Toolbar icons come straight from the Figma page 04 exports (`res/drawable-nodpi/ic_*.png`, 64 px,
 * dark glyph on transparent, tinted at runtime); the tilt arrows and the tip badge are drawn with Canvas.
 */
enum class ToolIcon { UNDO, REDO, SETTINGS, NEW_BOARD, HELP, TILT_UP, TILT_DOWN, INFO }

private val FIGMA_ICONS = mapOf(
    ToolIcon.UNDO to R.drawable.ic_undo,
    ToolIcon.REDO to R.drawable.ic_redo,
    ToolIcon.SETTINGS to R.drawable.ic_settings,
    ToolIcon.NEW_BOARD to R.drawable.ic_new_board,
    ToolIcon.HELP to R.drawable.ic_help,
)

@Composable
fun ToolIconGlyph(icon: ToolIcon, tint: Color, size: Dp = 22.dp) {
    FIGMA_ICONS[icon]?.let { res ->
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = ColorFilter.tint(tint),
        )
        return
    }
    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = this.size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            ToolIcon.UNDO -> drawUndo(tint, stroke)
            ToolIcon.REDO -> scale(scaleX = -1f, scaleY = 1f) { drawUndo(tint, stroke) }
            ToolIcon.SETTINGS -> drawGear(tint, stroke)
            ToolIcon.NEW_BOARD -> drawBoard(tint, stroke)
            ToolIcon.HELP -> drawHelp(tint, stroke)
            ToolIcon.TILT_UP -> drawArrow(tint, stroke, up = true)
            ToolIcon.TILT_DOWN -> drawArrow(tint, stroke, up = false)
            ToolIcon.INFO -> drawInfo(tint, stroke)
        }
    }
}

/** Three-quarter arc with an arrow head at its upper-left end (↶); mirrored for redo. */
private fun DrawScope.drawUndo(tint: Color, stroke: Stroke) {
    val w = size.width
    val r = w * 0.30f
    val c = Offset(w * 0.52f, w * 0.52f)
    drawArc(
        color = tint, startAngle = 300f, sweepAngle = 270f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = stroke,
    )
    // arrow head at the end of the arc (210°), tangent pointing up-right
    val endA = Math.toRadians(210.0)
    val tip = Offset(c.x + r * cos(endA).toFloat(), c.y + r * sin(endA).toFloat())
    val dir = Offset(-sin(endA).toFloat(), cos(endA).toFloat())      // clockwise tangent
    val perp = Offset(-dir.y, dir.x)
    val len = w * 0.20f
    drawLine(tint, tip, tip - dir * len + perp * len * 0.9f, stroke.width, StrokeCap.Round)
    drawLine(tint, tip, tip - dir * len - perp * len * 0.9f, stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawGear(tint: Color, stroke: Stroke) {
    val w = size.width
    val c = Offset(w / 2f, w / 2f)
    drawCircle(tint, radius = w * 0.24f, center = c, style = stroke)
    drawCircle(tint, radius = w * 0.07f, center = c)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(tint, c + dir * (w * 0.31f), c + dir * (w * 0.45f), stroke.width, StrokeCap.Round)
    }
}

/** New board: a rounded 2 × 2 grid. */
private fun DrawScope.drawBoard(tint: Color, stroke: Stroke) {
    val w = size.width
    val l = w * 0.14f; val t = w * 0.14f; val s = w * 0.72f
    drawRoundRect(tint, topLeft = Offset(l, t), size = Size(s, s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = stroke)
    drawLine(tint, Offset(l + s / 2f, t), Offset(l + s / 2f, t + s), stroke.width * 0.8f, StrokeCap.Round)
    drawLine(tint, Offset(l, t + s / 2f), Offset(l + s, t + s / 2f), stroke.width * 0.8f, StrokeCap.Round)
    drawRect(tint, topLeft = Offset(l, t), size = Size(s / 2f, s / 2f), alpha = 0.35f)
    drawRect(tint, topLeft = Offset(l + s / 2f, t + s / 2f), size = Size(s / 2f, s / 2f), alpha = 0.35f)
}

private fun DrawScope.drawHelp(tint: Color, stroke: Stroke) {
    val w = size.width
    val r = w * 0.17f
    val c = Offset(w * 0.5f, w * 0.36f)
    drawArc(
        color = tint, startAngle = 200f, sweepAngle = 250f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = stroke,
    )
    drawLine(tint, Offset(w * 0.5f, c.y + r), Offset(w * 0.5f, w * 0.66f), stroke.width, StrokeCap.Round)
    drawCircle(tint, radius = w * 0.065f, center = Offset(w * 0.5f, w * 0.84f))
}

/** Straight arrow: tilt up (+20°) points up, tilt down (−20°) points down (UI 1.1 fix for the side arrows). */
private fun DrawScope.drawArrow(tint: Color, stroke: Stroke, up: Boolean) {
    val w = size.width
    val top = Offset(w * 0.5f, w * 0.20f); val bottom = Offset(w * 0.5f, w * 0.80f)
    val tip = if (up) top else bottom
    val tail = if (up) bottom else top
    val headY = if (up) w * 0.44f else w * 0.56f
    drawLine(tint, tail, tip, stroke.width, StrokeCap.Round)
    drawLine(tint, tip, Offset(w * 0.27f, headY), stroke.width, StrokeCap.Round)
    drawLine(tint, tip, Offset(w * 0.73f, headY), stroke.width, StrokeCap.Round)
}

/** Exclamation mark in a circle: the tip can be dismissed. */
private fun DrawScope.drawInfo(tint: Color, stroke: Stroke) {
    val w = size.width
    val c = Offset(w / 2f, w / 2f)
    drawCircle(tint, radius = w * 0.42f, center = c, style = stroke)
    drawLine(tint, Offset(w * 0.5f, w * 0.28f), Offset(w * 0.5f, w * 0.56f), stroke.width, StrokeCap.Round)
    drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.5f, w * 0.71f))
}
