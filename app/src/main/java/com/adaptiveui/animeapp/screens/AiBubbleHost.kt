package com.adaptiveui.animeapp.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adaptiveui.animeapp.ai.AiState
import com.adaptiveui.animeapp.ai.AiViewModel
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText

/**
 * Floating AI Quick-Edit bubble + bottom panel.
 *
 * The bubble shows when:
 *  - `aiSettings.enabled` is true
 *  - `quickEditEnabled` is true
 *  - Either `isConfigured` (Built-in mode) OR `isExternalMode` (External mode — no API key needed)
 *
 * External mode flow:
 *  1. User types instruction
 *  2. "Copy Prompt" button → copies system prompt + instruction to clipboard
 *  3. Panel switches to "Paste Response" mode → user pastes JSON from their external AI
 *  4. "Apply" parses the JSON as ScreenSpec → applies live
 */
@Composable
fun AiBubbleHost(
    screenName: String,
    aiVm: AiViewModel,
    modifier: Modifier = Modifier
) {
    val aiSettings by aiVm.aiSettings.collectAsStateWithLifecycle()
    val state by aiVm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current

    // Show bubble whenever AI is enabled + quick-edit is on.
    // Does NOT require isConfigured — the panel handles the "not configured" case gracefully.
    val visible = aiSettings.enabled && aiSettings.quickEditEnabled

    var panelOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(end = Spacing.lg, bottom = 96.dp)
                    .size(52.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(
                        Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.75f)))
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { panelOpen = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icons.Sparkle(size = 24.dp, tint = c.accentText)
            }
        }

        AnimatedVisibility(
            visible = panelOpen && visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            AiEditPanel(
                screenName = screenName,
                state = state,
                isExternalMode = aiSettings.isExternalMode,
                isConfigured = aiSettings.isConfigured,
                aiVm = aiVm,
                onDismiss = {
                    if (state !is AiState.Generating) {
                        aiVm.dismiss()
                        panelOpen = false
                    }
                }
            )
        }
    }
}

@Composable
private fun AiEditPanel(
    screenName: String,
    state: AiState,
    isExternalMode: Boolean,
    isConfigured: Boolean,
    aiVm: AiViewModel,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var instruction by remember { mutableStateOf("") }
    var pastedResponse by remember { mutableStateOf("") }
    var externalStep by remember { mutableStateOf(0) } // 0 = type instruction, 1 = paste response
    val isGenerating = state is AiState.Generating
    val availableData = availableDataFor(screenName)

    // The entire panel area gets imePadding so the keyboard pushes it up — this prevents
    // the text field from being hidden behind the keyboard (which caused the scrim to receive
    // the touch instead of the text field, dismissing the panel).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Scrim — dismisses when tapped. NOT dismissable while generating.
        // The scrim is BEHIND the panel (first child of Box). The panel (second child) is on top
        // and consumes touches in its area, so tapping the panel does NOT trigger the scrim.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isGenerating,
                    onClick = onDismiss
                )
        )

        // Panel — sits on top of the scrim. Touches here are consumed by the panel, not the scrim.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl))
                .background(c.surface)
                .border(0.5.dp, c.outline, RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl))
                .navigationBarsPadding()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icons.Sparkle(size = 20.dp, tint = c.accent)
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    DesignText(text = "AI Quick Edit", style = LocalTypography.current.title2, color = c.text)
                    DesignText(
                        text = "${screenName.replaceFirstChar { it.uppercase() }} · ${if (isExternalMode) "External" else "Built-in"}",
                        style = LocalTypography.current.caption,
                        color = c.textMuted
                    )
                }
                IconButton(onClick = onDismiss) { Icons.Close(size = 20.dp) }
            }

            when (state) {
                is AiState.Idle -> {
                    if (isExternalMode) {
                        // ─── External mode flow ───
                        when (externalStep) {
                            0 -> {
                                // Step 1: Type instruction
                                SimpleTextField(
                                    value = instruction,
                                    onValueChange = { instruction = it },
                                    placeholder = "Describe the layout you want…",
                                    singleLine = false,
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth().height(120.dp)
                                )
                                PrimaryButton(
                                    text = "Copy prompt to clipboard",
                                    onClick = {
                                        val fullPrompt = aiVm.buildPrompt(screenName, availableData) +
                                            "\n\n--- USER REQUEST ---\n$instruction"
                                        clipboard.setText(AnnotatedString(fullPrompt))
                                        Toast.makeText(context, "Prompt copied! Paste it into your AI.", Toast.LENGTH_LONG).show()
                                        externalStep = 1
                                    },
                                    enabled = instruction.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                SecondaryButton(
                                    text = "Back",
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            1 -> {
                                // Step 2: Paste response
                                DesignText(
                                    text = "Paste the JSON response from your AI below:",
                                    style = LocalTypography.current.bodyEmphasis,
                                    color = c.text
                                )
                                SimpleTextField(
                                    value = pastedResponse,
                                    onValueChange = { pastedResponse = it },
                                    placeholder = "Paste the ScreenSpec JSON here…",
                                    singleLine = false,
                                    maxLines = 12,
                                    modifier = Modifier.fillMaxWidth().height(200.dp)
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(Radius.pill))
                                            .background(c.surfaceHi)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { externalStep = 0 }
                                            )
                                            .padding(vertical = Spacing.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DesignText("Back", style = LocalTypography.current.bodyEmphasis, color = c.text)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(Radius.pill))
                                            .background(c.accent)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { aiVm.applyExternalResponse(pastedResponse, screenName) }
                                            )
                                            .padding(vertical = Spacing.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DesignText("Apply", style = LocalTypography.current.bodyEmphasis, color = c.accentText)
                                    }
                                }
                            }
                        }
                    } else {
                        // ─── Built-in mode flow ───
                        if (!isConfigured) {
                            // Not configured yet — show a message instead of the generate button.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.md))
                                    .background(c.danger.copy(alpha = 0.12f))
                                    .padding(Spacing.md)
                            ) {
                                DesignText(
                                    text = "Add your API key and select a model in Settings → AI to use built-in generation. Or switch to External mode to use any AI manually.",
                                    style = LocalTypography.current.caption,
                                    color = c.danger
                                )
                            }
                            SecondaryButton(
                                text = "Close",
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            SimpleTextField(
                                value = instruction,
                                onValueChange = { instruction = it },
                                placeholder = "Describe the layout you want… (e.g. 'Show trending in a 2-column grid with big posters')",
                                singleLine = false,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            )
                            PrimaryButton(
                                text = "Generate Preview",
                                onClick = { aiVm.generateSpec(instruction, screenName, availableData) },
                                enabled = instruction.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SecondaryButton(
                                text = "Build real APK",
                                onClick = { aiVm.generateCompose(instruction, screenName, availableData) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is AiState.Generating -> {
                    GeneratingIndicator()
                }

                is AiState.PreviewReady -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(c.success.copy(alpha = 0.12f))
                            .padding(Spacing.md)
                    ) {
                        DesignText(
                            text = "Preview ready. Apply to render this screen with the AI-generated spec — Discard to keep the current layout.",
                            style = LocalTypography.current.caption,
                            color = c.success
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(c.surfaceHi)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { aiVm.discardPreview() }
                                )
                                .padding(vertical = Spacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            DesignText(text = "Discard", style = LocalTypography.current.bodyEmphasis, color = c.text)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(c.accent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { aiVm.applyPreview(state.spec) }
                                )
                                .padding(vertical = Spacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            DesignText(text = "Apply", style = LocalTypography.current.bodyEmphasis, color = c.accentText)
                        }
                    }
                }

                is AiState.ComposeReady -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(c.surfaceHi)
                            .padding(Spacing.md)
                    ) {
                        Column {
                            DesignText(
                                text = "Compose source generated.",
                                style = LocalTypography.current.bodyEmphasis,
                                color = c.text
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            DesignText(
                                text = "${state.source.take(180)}…",
                                style = LocalTypography.current.caption,
                                color = c.textMuted,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    PrimaryButton(
                        text = "Push & Build APK",
                        onClick = { aiVm.triggerBuild(state.source, state.screenName) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(
                        text = "Discard",
                        onClick = { aiVm.discardPreview() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is AiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(c.danger.copy(alpha = 0.12f))
                            .padding(Spacing.md)
                    ) {
                        DesignText(
                            text = state.message,
                            style = LocalTypography.current.caption,
                            color = c.danger
                        )
                    }
                    if (isExternalMode) {
                        // For external mode errors, go back to paste step
                        PrimaryButton(
                            text = "Back to paste",
                            onClick = {
                                aiVm.dismiss()
                                externalStep = 1
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        PrimaryButton(
                            text = "Retry",
                            onClick = { aiVm.generateSpec(instruction, screenName, availableData) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is AiState.BuildTriggered -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(c.success.copy(alpha = 0.12f))
                            .padding(Spacing.md)
                    ) {
                        Column {
                            DesignText(
                                text = "Build dispatched. APK will be available when the GitHub Actions run completes.",
                                style = LocalTypography.current.caption,
                                color = c.success
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            DesignText(
                                text = state.runUrl,
                                style = LocalTypography.current.caption,
                                color = c.accent,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    SecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratingIndicator() {
    val c = LocalColors.current
    val transition = rememberInfiniteTransition(label = "orbit")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(c.accent)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(c.accent.copy(alpha = 0.6f))
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(c.accent.copy(alpha = 0.3f))
            )
        }
        Spacer(Modifier.width(Spacing.md))
        DesignText(
            text = "Generating…",
            style = LocalTypography.current.body,
            color = c.textMuted
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = LocalColors.current
    val bg = if (enabled) c.accent else c.surfaceHi
    val fg = if (enabled) c.accentText else c.textMuted
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        DesignText(text = text, style = LocalTypography.current.bodyEmphasis, color = fg)
    }
}

/** Mapping from route → human-readable summary of the data the AI can reference. */
private fun availableDataFor(screenName: String): String = when (screenName.lowercase()) {
    "home" -> "Lists: trending, seasonal, upcoming, topRated, allTimePopular. Each item has id, title, coverUrl, bannerImage, score, scoreLabel, year, format, episodes, genres."
    "library" -> "Lists: entries (saved anime), categories. Entry fields: id, title, coverUrl, bannerImage, score, episodes, format, year. Category fields: id, name, isDefault."
    "search" -> "List: results (AnimeCard). Each item: id, title, coverUrl, bannerImage, score, scoreLabel, year, format, episodes, genres."
    "details" -> "Single: detail (AnimeDetail). Fields: id, title, coverUrl, bannerImage, description, score, scoreLabel, popularity, favourites, format, episodes, duration, status, season, seasonYear, genres, studios. List: episodes (number, title, description, thumbnail, airDate, runtime, filler, score, hasMetadata)."
    "settings" -> "No screen-data — Settings is mostly static."
    else -> "No data binding available for this screen."
}
