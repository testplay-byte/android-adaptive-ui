package com.adaptiveui.animeapp.screens

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
import androidx.compose.ui.graphics.Color
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
 * Shown when:
 *  - `aiSettings.enabled` is true (the master switch)
 *  - `aiSettings.isConfigured` is true (API key + base URL set)
 *  - `quickEditEnabled` is true (the bubble toggle)
 *
 * The bubble is a circular accent-colored circle with a Sparkle icon. Tapping it opens a custom
 * bottom panel (not Material3 BottomSheet) where the user types a natural-language instruction,
 * generates a [com.adaptiveui.animeapp.interpreter.ScreenSpec] preview, and either applies it
 * (which persists to DataStore → the current screen re-renders via the interpreter) or discards.
 *
 * The panel also exposes a "Build real APK" path: the AI generates raw Compose source → pushed
 * to GitHub → workflow dispatched → returns the Actions run URL.
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

    val visible = aiSettings.enabled && aiSettings.isConfigured
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
                onGenerateSpec = { instruction ->
                    aiVm.generateSpec(instruction, screenName, availableDataFor(screenName))
                },
                onGenerateCompose = { instruction ->
                    aiVm.generateCompose(instruction, screenName, availableDataFor(screenName))
                },
                onApply = {
                    val s = state
                    if (s is AiState.PreviewReady) aiVm.applyPreview(s.spec)
                },
                onTriggerBuild = {
                    val s = state
                    if (s is AiState.ComposeReady) aiVm.triggerBuild(s.source, s.screenName)
                },
                onDiscard = { aiVm.discardPreview() },
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
    onGenerateSpec: (String) -> Unit,
    onGenerateCompose: (String) -> Unit,
    onApply: () -> Unit,
    onTriggerBuild: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    var instruction by remember { mutableStateOf("") }
    val isGenerating = state is AiState.Generating

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim (not dismissable while generating)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // Panel
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
                        text = "Screen: ${screenName.replaceFirstChar { it.uppercase() }}",
                        style = LocalTypography.current.caption,
                        color = c.textMuted
                    )
                }
                IconButton(onClick = onDismiss) { Icons.Close(size = 20.dp) }
            }

            // Instruction text field
            SimpleTextField(
                value = instruction,
                onValueChange = { instruction = it },
                placeholder = "Describe the layout you want… (e.g. 'Show trending in a 2-column grid with big posters')",
                singleLine = false,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            // State-driven content
            when (state) {
                is AiState.Idle -> {
                    PrimaryButton(
                        text = "Generate Preview",
                        onClick = { onGenerateSpec(instruction) },
                        enabled = instruction.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(
                        text = "Build real APK",
                        onClick = { onGenerateCompose(instruction) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                    onClick = onDiscard
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
                                    onClick = onApply
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
                        onClick = onTriggerBuild,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(
                        text = "Discard",
                        onClick = onDiscard,
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
                    PrimaryButton(
                        text = "Retry",
                        onClick = { onGenerateSpec(instruction) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
            // Three orbiting dots — simple, clean processing animation
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
