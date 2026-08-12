package com.adaptiveui.animeapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Minimal text field built on [BasicTextField] — no Material3 dependency. Supports a placeholder
 * (shown when the field is empty) and an optional leading icon slot. Used across all screens for
 * search input, AI instructions, API key entry, etc.
 *
 * NOTE: callers that need password masking should pass [visualTransformation] = PasswordVisualTransformation.
 */
@Composable
internal fun SimpleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = LocalTypography.current.body
) {
    val c = LocalColors.current
    val showPlaceholder = value.isEmpty()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surfaceHi)
            .border(0.5.dp, c.outline, RoundedCornerShape(Radius.md))
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(Spacing.sm))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (showPlaceholder) {
                DesignText(
                    text = placeholder,
                    style = textStyle,
                    color = c.textMuted
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = visualTransformation,
                interactionSource = remember { MutableInteractionSource() }
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailingIcon()
        }
    }
}
