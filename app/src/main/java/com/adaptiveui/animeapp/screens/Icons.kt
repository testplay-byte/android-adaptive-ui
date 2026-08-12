package com.adaptiveui.animeapp.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adaptiveui.animeapp.design.LocalColors

/**
 * Minimal Canvas-drawn icons. No Material Icons dependency — every glyph is drawn from paths
 * on a transparent [Canvas] sized `size × size` dp. All icons use the design system's text
 * color by default; pass `tint` to override.
 *
 * Stroke-based, rounded caps, 1.8dp stroke width — matches the minimal aesthetic.
 */
object Icons {

    @Composable
    fun Home(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawHome(s, w, c) }

    @Composable
    fun Library(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawLibrary(s, w, c) }

    @Composable
    fun Search(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawSearch(s, w, c) }

    @Composable
    fun Settings(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawSettings(s, w, c) }

    @Composable
    fun Back(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawBack(s, w, c) }

    @Composable
    fun Save(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawSave(s, w, c) }

    @Composable
    fun SaveFilled(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawSaveFilled(s, w, c) }

    @Composable
    fun Refresh(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawRefresh(s, w, c) }

    @Composable
    fun Plus(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawPlus(s, w, c) }

    @Composable
    fun Close(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawClose(s, w, c) }

    @Composable
    fun Star(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawStar(s, w, c) }

    @Composable
    fun Play(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawPlay(s, w, c) }

    @Composable
    fun Send(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawSend(s, w, c) }

    @Composable
    fun Sparkle(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawSparkle(s, w, c) }

    @Composable
    fun Eye(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawEye(s, w, c) }

    @Composable
    fun EyeOff(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawEyeOff(s, w, c) }

    @Composable
    fun Lock(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawLock(s, w, c) }

    @Composable
    fun ChevronDown(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawChevronDown(s, w, c) }

    @Composable
    fun ChevronRight(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawChevronRight(s, w, c) }

    @Composable
    fun Upload(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawUpload(s, w, c) }

    @Composable
    fun Trash(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawTrash(s, w, c) }

    @Composable
    fun Filter(tint: Color = LocalColors.current.text, size: Dp = 24.dp) =
        IconCanvas(size, tint) { s, w, c -> drawFilter(s, w, c) }
}

/** Canvas wrapper that hands the draw scope a normalized size + the requested tint. */
@Composable
private fun IconCanvas(
    size: Dp,
    tint: Color,
    draw: DrawScope.(Float, Float, Color) -> Unit
) {
    val px = size.value
    Canvas(modifier = Modifier.size(size)) {
        draw(px, 1.8f, tint)
    }
}

// ─── Path helpers ────────────────────────────────────────────────────────────

private fun DrawScope.drawHome(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Roof + body
    val path = Path().apply {
        moveTo(0.2f * s, 0.5f * s)
        lineTo(0.5f * s, 0.2f * s)
        lineTo(0.8f * s, 0.5f * s)
        lineTo(0.8f * s, 0.8f * s)
        lineTo(0.2f * s, 0.8f * s)
        close()
    }
    drawPath(path, color = c, style = stroke)
    // Door
    val door = Path().apply {
        moveTo(0.42f * s, 0.8f * s)
        lineTo(0.42f * s, 0.6f * s)
        lineTo(0.58f * s, 0.6f * s)
        lineTo(0.58f * s, 0.8f * s)
    }
    drawPath(door, color = c, style = stroke)
}

private fun DrawScope.drawLibrary(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Three vertical book spines
    val spine1 = Path().apply {
        moveTo(0.25f * s, 0.2f * s); lineTo(0.4f * s, 0.22f * s); lineTo(0.38f * s, 0.8f * s); lineTo(0.23f * s, 0.78f * s); close()
    }
    val spine2 = Path().apply {
        moveTo(0.45f * s, 0.21f * s); lineTo(0.6f * s, 0.2f * s); lineTo(0.6f * s, 0.8f * s); lineTo(0.45f * s, 0.81f * s); close()
    }
    val spine3 = Path().apply {
        moveTo(0.66f * s, 0.24f * s); lineTo(0.8f * s, 0.18f * s); lineTo(0.82f * s, 0.76f * s); lineTo(0.68f * s, 0.82f * s); close()
    }
    drawPath(spine1, color = c, style = stroke)
    drawPath(spine2, color = c, style = stroke)
    drawPath(spine3, color = c, style = stroke)
}

private fun DrawScope.drawSearch(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawCircle(color = c, radius = 0.3f * s, center = Offset(0.42f * s, 0.42f * s), style = stroke)
    drawLine(c, start = Offset(0.65f * s, 0.65f * s), end = Offset(0.82f * s, 0.82f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawSettings(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val cx = 0.5f * s
    val cy = 0.5f * s
    drawCircle(color = c, radius = 0.13f * s, center = Offset(cx, cy), style = stroke)
    // 8 teeth as small lines around the cog
    val teeth = 8
    val inner = 0.22f * s
    val outer = 0.32f * s
    for (i in 0 until teeth) {
        val angle = (i * (2.0 * Math.PI / teeth)).toFloat()
        val cosA = kotlin.math.cos(angle); val sinA = kotlin.math.sin(angle)
        drawLine(
            color = c,
            start = Offset(cx + inner * cosA, cy + inner * sinA),
            end = Offset(cx + outer * cosA, cy + outer * sinA),
            strokeWidth = w,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBack(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(c, start = Offset(0.55f * s, 0.2f * s), end = Offset(0.25f * s, 0.5f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.25f * s, 0.5f * s), end = Offset(0.55f * s, 0.8f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.25f * s, 0.5f * s), end = Offset(0.8f * s, 0.5f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawSave(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Bookmark outline
    val path = Path().apply {
        moveTo(0.3f * s, 0.2f * s)
        lineTo(0.7f * s, 0.2f * s)
        lineTo(0.7f * s, 0.8f * s)
        lineTo(0.5f * s, 0.62f * s)
        lineTo(0.3f * s, 0.8f * s)
        close()
    }
    drawPath(path, color = c, style = stroke)
}

private fun DrawScope.drawSaveFilled(s: Float, w: Float, c: Color) {
    val path = Path().apply {
        moveTo(0.3f * s, 0.2f * s)
        lineTo(0.7f * s, 0.2f * s)
        lineTo(0.7f * s, 0.8f * s)
        lineTo(0.5f * s, 0.62f * s)
        lineTo(0.3f * s, 0.8f * s)
        close()
    }
    drawPath(path, color = c)
}

private fun DrawScope.drawRefresh(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val cx = 0.5f * s; val cy = 0.5f * s; val r = 0.28f * s
    // Arc from 30deg to 290deg
    val startAngle = 30f
    val sweepAngle = 260f
    drawArc(
        color = c,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = stroke
    )
    // Arrowhead at start
    val angleRad = Math.toRadians(startAngle.toDouble())
    val ax = cx + r * kotlin.math.cos(angleRad).toFloat()
    val ay = cy + r * kotlin.math.sin(angleRad).toFloat()
    drawLine(c, start = Offset(ax, ay), end = Offset(ax - 0.12f * s, ay - 0.02f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(ax, ay), end = Offset(ax + 0.02f * s, ay - 0.12f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawPlus(s: Float, w: Float, c: Color) {
    drawLine(c, start = Offset(0.5f * s, 0.2f * s), end = Offset(0.5f * s, 0.8f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.2f * s, 0.5f * s), end = Offset(0.8f * s, 0.5f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawClose(s: Float, w: Float, c: Color) {
    drawLine(c, start = Offset(0.25f * s, 0.25f * s), end = Offset(0.75f * s, 0.75f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.75f * s, 0.25f * s), end = Offset(0.25f * s, 0.75f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawStar(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val cx = 0.5f * s; val cy = 0.5f * s
    val outerR = 0.32f * s; val innerR = 0.14f * s
    val path = Path()
    for (i in 0 until 10) {
        val angle = (Math.PI / 2 + i * Math.PI / 5).toFloat()
        val r = if (i % 2 == 0) outerR else innerR
        val x = cx + r * kotlin.math.cos(angle)
        val y = cy - r * kotlin.math.sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = c, style = stroke)
}

private fun DrawScope.drawPlay(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(0.35f * s, 0.25f * s)
        lineTo(0.75f * s, 0.5f * s)
        lineTo(0.35f * s, 0.75f * s)
        close()
    }
    drawPath(path, color = c, style = stroke)
}

private fun DrawScope.drawSend(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(0.2f * s, 0.5f * s)
        lineTo(0.8f * s, 0.2f * s)
        lineTo(0.55f * s, 0.8f * s)
        lineTo(0.45f * s, 0.55f * s)
        close()
    }
    drawPath(path, color = c, style = stroke)
    drawLine(c, start = Offset(0.45f * s, 0.55f * s), end = Offset(0.8f * s, 0.2f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawSparkle(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val cx = 0.5f * s; val cy = 0.5f * s
    // 4-pointed sparkle: two crossed diamond shapes
    val path = Path().apply {
        moveTo(cx, 0.15f * s)
        lineTo(0.58f * s, cy)
        lineTo(cx, 0.85f * s)
        lineTo(0.42f * s, cy)
        close()
    }
    drawPath(path, color = c, style = stroke)
    // Small side sparkles
    val small = Path().apply {
        moveTo(0.8f * s, 0.25f * s)
        lineTo(0.85f * s, 0.35f * s)
        lineTo(0.95f * s, 0.4f * s)
        lineTo(0.85f * s, 0.45f * s)
        lineTo(0.8f * s, 0.55f * s)
        lineTo(0.75f * s, 0.45f * s)
        lineTo(0.65f * s, 0.4f * s)
        lineTo(0.75f * s, 0.35f * s)
        close()
    }
    drawPath(small, color = c, style = stroke)
}

private fun DrawScope.drawEye(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Eye outline
    val path = Path().apply {
        moveTo(0.15f * s, 0.5f * s)
        cubicTo(0.3f * s, 0.25f * s, 0.7f * s, 0.25f * s, 0.85f * s, 0.5f * s)
        cubicTo(0.7f * s, 0.75f * s, 0.3f * s, 0.75f * s, 0.15f * s, 0.5f * s)
        close()
    }
    drawPath(path, color = c, style = stroke)
    drawCircle(color = c, radius = 0.1f * s, center = Offset(0.5f * s, 0.5f * s), style = stroke)
}

private fun DrawScope.drawEyeOff(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(0.15f * s, 0.5f * s)
        cubicTo(0.3f * s, 0.25f * s, 0.7f * s, 0.25f * s, 0.85f * s, 0.5f * s)
        cubicTo(0.7f * s, 0.75f * s, 0.3f * s, 0.75f * s, 0.15f * s, 0.5f * s)
        close()
    }
    drawPath(path, color = c, style = stroke)
    // Diagonal strike-through
    drawLine(c, start = Offset(0.2f * s, 0.2f * s), end = Offset(0.8f * s, 0.8f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawLock(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Shackle (arc)
    drawArc(
        color = c,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(0.32f * s, 0.15f * s),
        size = Size(0.36f * s, 0.36f * s),
        style = stroke
    )
    // Body
    val body = Path().apply {
        addRect(Rect(left = 0.25f * s, top = 0.45f * s, right = 0.75f * s, bottom = 0.85f * s))
    }
    drawPath(body, color = c, style = stroke)
}

private fun DrawScope.drawChevronDown(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(c, start = Offset(0.25f * s, 0.4f * s), end = Offset(0.5f * s, 0.65f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.5f * s, 0.65f * s), end = Offset(0.75f * s, 0.4f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawChevronRight(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(c, start = Offset(0.4f * s, 0.25f * s), end = Offset(0.65f * s, 0.5f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.65f * s, 0.5f * s), end = Offset(0.4f * s, 0.75f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawUpload(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Arrow up
    drawLine(c, start = Offset(0.5f * s, 0.2f * s), end = Offset(0.5f * s, 0.65f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.5f * s, 0.2f * s), end = Offset(0.32f * s, 0.38f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.5f * s, 0.2f * s), end = Offset(0.68f * s, 0.38f * s), strokeWidth = w, cap = StrokeCap.Round)
    // Base line
    drawLine(c, start = Offset(0.25f * s, 0.8f * s), end = Offset(0.75f * s, 0.8f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawTrash(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Lid
    drawLine(c, start = Offset(0.25f * s, 0.3f * s), end = Offset(0.75f * s, 0.3f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.4f * s, 0.3f * s), end = Offset(0.4f * s, 0.22f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.6f * s, 0.3f * s), end = Offset(0.6f * s, 0.22f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.4f * s, 0.22f * s), end = Offset(0.6f * s, 0.22f * s), strokeWidth = w, cap = StrokeCap.Round)
    // Body
    val body = Path().apply {
        moveTo(0.32f * s, 0.3f * s)
        lineTo(0.36f * s, 0.8f * s)
        lineTo(0.64f * s, 0.8f * s)
        lineTo(0.68f * s, 0.3f * s)
    }
    drawPath(body, color = c, style = stroke)
    // Inner lines
    drawLine(c, start = Offset(0.45f * s, 0.4f * s), end = Offset(0.47f * s, 0.7f * s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(c, start = Offset(0.55f * s, 0.4f * s), end = Offset(0.53f * s, 0.7f * s), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawFilter(s: Float, w: Float, c: Color) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(0.2f * s, 0.3f * s)
        lineTo(0.8f * s, 0.3f * s)
        lineTo(0.58f * s, 0.55f * s)
        lineTo(0.58f * s, 0.78f * s)
        lineTo(0.42f * s, 0.7f * s)
        lineTo(0.42f * s, 0.55f * s)
        close()
    }
    drawPath(path, color = c, style = stroke)
}
