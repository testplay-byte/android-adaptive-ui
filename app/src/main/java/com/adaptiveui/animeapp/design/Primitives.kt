package com.adaptiveui.animeapp.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography

/** Minimal card — surface bg, subtle outline, rounded. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.lg),
    background: Color = LocalColors.current.surface,
    borderColor: Color = LocalColors.current.outline,
    borderWidth: Dp = 0.5.dp,
    onClick: (() -> Unit)? = null,
    padding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = Modifier
        .then(modifier)
        .clip(shape)
        .background(background)
        .border(borderWidth, borderColor, shape)
    Box(
        modifier = if (onClick != null) base.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = onClick
        ) else base
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

/** Text wrapper that reads theme colors by default. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTypography.current.body,
    color: Color = LocalColors.current.text,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow
    )
}

/** Remote image with placeholder + error fallback. */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape? = null
) {
    val m = if (shape != null) modifier.clip(shape) else modifier
    if (url.isNullOrBlank()) {
        Box(modifier = m.background(LocalColors.current.surfaceHi))
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = m
        )
    }
}

/** Pill button. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true
) {
    val c = LocalColors.current
    val bg = when {
        !enabled -> c.surfaceHi
        primary -> c.accent
        else -> c.surfaceHi
    }
    val fg = when {
        !enabled -> c.textMuted
        primary -> c.accentText
        else -> c.text
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = LocalTypography.current.bodyEmphasis, color = fg)
    }
}

/** Small rounded chip. */
@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val bg = if (selected) c.accent else c.surfaceHi
    val fg = if (selected) c.accentText else c.textMuted
    val m = if (onClick != null) modifier.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick
    ) else modifier
    Box(
        modifier = m
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
    ) {
        Text(text, style = LocalTypography.current.caption, color = fg)
    }
}
