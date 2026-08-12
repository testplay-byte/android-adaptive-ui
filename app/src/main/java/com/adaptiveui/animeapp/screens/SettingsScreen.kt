package com.adaptiveui.animeapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adaptiveui.animeapp.ai.AiViewModel
import com.adaptiveui.animeapp.ai.TestResult
import com.adaptiveui.animeapp.design.Chip
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText
import com.adaptiveui.animeapp.domain.model.AiSettings

/**
 * Settings screen — appearance, AI config, custom screen specs, and about.
 *
 * Card-section layout: each section is a [com.adaptiveui.animeapp.design.Card] with title + body.
 * Spacing: [Spacing.lg] inside cards, [Spacing.md] between cards, [Spacing.lg] horizontal margin.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = hiltViewModel()
    val aiVm: AiViewModel = hiltViewModel()
    val aiSettings by vm.aiSettings.collectAsStateWithLifecycle()
    val quickEdit by vm.quickEditEnabled.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val specs by vm.screenSpecs.collectAsStateWithLifecycle()
    val testResult by aiVm.testResult.collectAsStateWithLifecycle()

    val c = LocalColors.current

    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DesignText(
                        text = "Settings",
                        style = LocalTypography.current.title1,
                        color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // APPEARANCE
            item {
                SettingsCard(title = "Appearance") {
                    Label("Theme")
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf("AUTO" to "Auto", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                            Chip(
                                text = label,
                                selected = themeMode == mode,
                                onClick = { vm.setThemeMode(mode) }
                            )
                        }
                    }
                }
            }

            // AI
            item {
                SettingsCard(title = "AI") {
                    AiConfigSection(
                        settings = aiSettings,
                        testResult = testResult,
                        onTestConnection = { aiVm.testConnection() },
                        onClearTestResult = { aiVm.clearTestResult() },
                        onSave = { vm.saveAiSettings(it) }
                    )
                }
            }

            // CUSTOM SCREENS
            item {
                SettingsCard(title = "Custom screens") {
                    if (specs.isEmpty()) {
                        DesignText(
                            text = "No AI-generated screen specs applied. Use the Quick-Edit bubble on any screen to generate one.",
                            style = LocalTypography.current.body,
                            color = c.textMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            specs.keys.sorted().forEach { screenName ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 8.dp, height = 8.dp)
                                            .clip(RoundedCornerShape(Radius.pill))
                                            .background(c.success)
                                    )
                                    Spacer(Modifier.width(Spacing.md))
                                    DesignText(
                                        text = screenName.replaceFirstChar { it.uppercase() },
                                        style = LocalTypography.current.body,
                                        color = c.text,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(Radius.pill))
                                            .background(c.surfaceHi)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { vm.clearSpec(screenName) }
                                            )
                                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                                    ) {
                                        DesignText(
                                            text = "Clear",
                                            style = LocalTypography.current.caption,
                                            color = c.textMuted
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        DangerTextButton(
                            text = "Clear all",
                            onClick = { vm.clearAllSpecs() }
                        )
                    }
                }
            }

            // ABOUT
            item {
                SettingsCard(title = "About") {
                    AboutRow(label = "App", value = "Aniverse")
                    AboutRow(label = "Version", value = "1.0.0")
                    Spacer(Modifier.height(Spacing.sm))
                    DesignText(
                        text = "An anime browsing demo app with an AI-driven adaptive UI. Browse, search, and save anime; the UI itself can be redesigned live from natural language.",
                        style = LocalTypography.current.body,
                        color = c.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.surface)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        DesignText(
            text = title.uppercase(),
            style = LocalTypography.current.micro,
            color = c.textMuted
        )
        content()
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DesignText(text = label, style = LocalTypography.current.body, color = c.textMuted)
        DesignText(
            text = value,
            style = LocalTypography.current.bodyEmphasis,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DangerTextButton(text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(c.danger.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        DesignText(
            text = text,
            style = LocalTypography.current.bodyEmphasis,
            color = c.danger
        )
    }
}
