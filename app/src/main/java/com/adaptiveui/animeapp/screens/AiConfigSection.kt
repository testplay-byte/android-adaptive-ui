package com.adaptiveui.animeapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.adaptiveui.animeapp.ai.FreeModelPresets
import com.adaptiveui.animeapp.ai.SystemPrompt
import com.adaptiveui.animeapp.ai.TestResult
import com.adaptiveui.animeapp.design.Chip
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText
import com.adaptiveui.animeapp.domain.model.AiSettings
import com.adaptiveui.animeapp.domain.model.FreeModelPreset

/**
 * AI configuration UI embedded inside the Settings screen.
 *
 * All settings live in [AiSettings] (persisted via [com.adaptiveui.animeapp.core.datastore.SettingsDataStore]).
 * Local UI state (password mask toggle, custom-model text field, model-picker dialog visibility) is
 * kept here; commits flow up through [onSave] each time a field changes.
 *
 * Note on the custom-model field: we keep a local `customModelDraft` so the user can type freely
 * without each keystroke round-tripping through DataStore. The draft is committed to [onSave] on
 * focus loss (when the field loses focus) — preventing the cursor-jump problem that comes from
 * re-rendering on every keystroke.
 */
@Composable
fun AiConfigSection(
    settings: AiSettings,
    testResult: TestResult?,
    onTestConnection: () -> Unit,
    onClearTestResult: () -> Unit,
    onSave: (AiSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    var showApiKey by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showPromptPreview by remember { mutableStateOf(false) }
    var customModelDraft by remember(settings.customModelId) { mutableStateOf(settings.customModelId) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        // Enable AI
        ToggleRow(
            title = "Enable AI",
            subtitle = "Master switch for all AI features.",
            checked = settings.enabled,
            onCheckChange = { onSave(settings.copy(enabled = it)) }
        )

        // Quick-edit bubble
        ToggleRow(
            title = "Quick-Edit bubble",
            subtitle = "Show the floating AI bubble on every screen.",
            checked = settings.quickEditEnabled,
            onCheckChange = { onSave(settings.copy(quickEditEnabled = it)) }
        )

        // Provider picker
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Provider")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FreeModelPresets.providers.forEach { provider ->
                    val preset = FreeModelPresets.defaultFor(provider)
                    Chip(
                        text = provider,
                        selected = settings.provider.equals(provider, ignoreCase = true),
                        onClick = {
                            onSave(
                                settings.copy(
                                    provider = provider,
                                    baseUrl = preset.baseUrl,
                                    modelId = preset.modelId
                                )
                            )
                        }
                    )
                }
            }
        }

        // Model picker
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Model")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(c.surfaceHi)
                    .border(0.5.dp, c.outline, RoundedCornerShape(Radius.md))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showModelPicker = true }
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DesignText(
                        text = if (settings.useCustomModel) settings.customModelId.ifBlank { "(custom)" } else settings.modelId,
                        style = LocalTypography.current.body,
                        color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    Icons.ChevronDown(size = 18.dp)
                }
            }
        }

        // Custom model toggle + field
        ToggleRow(
            title = "Custom model ID",
            subtitle = "Enter a model ID not in the preset list.",
            checked = settings.useCustomModel,
            onCheckChange = {
                // Commit any pending draft before toggling.
                if (customModelDraft != settings.customModelId) {
                    onSave(settings.copy(useCustomModel = it, customModelId = customModelDraft))
                } else {
                    onSave(settings.copy(useCustomModel = it))
                }
            }
        )
        if (settings.useCustomModel) {
            SimpleTextField(
                value = customModelDraft,
                onValueChange = { customModelDraft = it },
                placeholder = "e.g. openai/gpt-oss-20b",
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { fs ->
                        // Commit the draft to settings when the field loses focus.
                        if (!fs.isFocused && customModelDraft != settings.customModelId) {
                            onSave(settings.copy(customModelId = customModelDraft))
                        }
                    }
            )
        }

        // API key
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("API key")
            SimpleTextField(
                value = settings.apiKey,
                onValueChange = { newKey ->
                    onSave(settings.copy(apiKey = newKey))
                    onClearTestResult()
                },
                placeholder = "Paste your API key",
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showApiKey = !showApiKey }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showApiKey) Icons.EyeOff(size = 18.dp) else Icons.Eye(size = 18.dp)
                    }
                }
            )
            DesignText(
                text = "Stored locally on this device. Never sent anywhere except the chosen provider.",
                style = LocalTypography.current.caption,
                color = c.textMuted
            )
        }

        // Temperature stepper
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Temperature: ${"%.1f".format(settings.temperature)}")
            Stepper(
                value = settings.temperature,
                range = 0.0..1.5,
                step = 0.1,
                onValueChange = { onSave(settings.copy(temperature = it)) }
            )
        }

        // Test connection
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                text = "Test connection",
                onClick = onTestConnection,
                modifier = Modifier.fillMaxWidth()
            )
            testResult?.let { res ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(if (res.success) c.success.copy(alpha = 0.12f) else c.danger.copy(alpha = 0.12f))
                        .border(0.5.dp, if (res.success) c.success else c.danger, RoundedCornerShape(Radius.md))
                        .padding(Spacing.md)
                ) {
                    DesignText(
                        text = res.message,
                        style = LocalTypography.current.caption,
                        color = if (res.success) c.success else c.danger
                    )
                }
            }
        }

        // System prompt
        SecondaryButton(
            text = "Copy system prompt",
            onClick = { showPromptPreview = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showModelPicker) {
        ModelPickerDialog(
            presets = FreeModelPresets.forProvider(settings.provider),
            selectedModelId = settings.modelId,
            onPick = { preset ->
                onSave(settings.copy(modelId = preset.modelId, useCustomModel = false))
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
    if (showPromptPreview) {
        val clipboard = LocalClipboardManager.current
        val previewText = remember {
            SystemPrompt.buildForSpec("home", "Sample data keys: trending, seasonal, upcoming, topRated, allTimePopular")
        }
        PromptPreviewDialog(
            text = previewText,
            onCopy = {
                clipboard.setText(AnnotatedString(previewText))
                showPromptPreview = false
            },
            onDismiss = { showPromptPreview = false }
        )
    }
}

// ─── Small UI primitives ─────────────────────────────────────────────────────

@Composable
internal fun Label(text: String) {
    DesignText(
        text = text,
        style = LocalTypography.current.caption,
        color = LocalColors.current.textMuted
    )
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckChange: (Boolean) -> Unit
) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            DesignText(text = title, style = LocalTypography.current.bodyEmphasis, color = c.text)
            DesignText(text = subtitle, style = LocalTypography.current.caption, color = c.textMuted)
        }
        Spacer(Modifier.width(Spacing.md))
        CustomSwitch(checked = checked, onCheckChange = onCheckChange)
    }
}

@Composable
internal fun CustomSwitch(checked: Boolean, onCheckChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    val thumbColor = if (checked) c.accentText else c.textMuted
    val trackColor = if (checked) c.accent else c.surfaceHi
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckChange(!checked) }
            )
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(thumbColor)
        )
    }
}

@Composable
internal fun Stepper(
    value: Double,
    range: ClosedRange<Double>,
    step: Double,
    onValueChange: (Double) -> Unit
) {
    val c = LocalColors.current
    val rounded = (Math.round(value / step) * step)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surfaceHi)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val next = (rounded - step).coerceIn(range.start, range.endInclusive)
                        onValueChange(next)
                    }
                ),
            contentAlignment = Alignment.Center
        ) { DesignText("−", style = LocalTypography.current.title2, color = c.text) }
        DesignText(
            text = "%.1f".format(rounded),
            style = LocalTypography.current.bodyEmphasis,
            color = c.text,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val next = (rounded + step).coerceIn(range.start, range.endInclusive)
                        onValueChange(next)
                    }
                ),
            contentAlignment = Alignment.Center
        ) { DesignText("+", style = LocalTypography.current.title2, color = c.text) }
    }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(c.surfaceHi)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        DesignText(text = text, style = LocalTypography.current.bodyEmphasis, color = c.text)
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun ModelPickerDialog(
    presets: List<FreeModelPreset>,
    selectedModelId: String,
    onPick: (FreeModelPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    Scrim(onDismiss = onDismiss)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            DesignText(text = "Select a model", style = LocalTypography.current.title2, color = c.text)
            Spacer(Modifier.height(Spacing.sm))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                presets.forEach { preset ->
                    val selected = preset.modelId == selectedModelId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPick(preset) }
                            )
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            DesignText(
                                text = preset.label,
                                style = LocalTypography.current.bodyEmphasis,
                                color = if (selected) c.accent else c.text
                            )
                            DesignText(
                                text = preset.notes,
                                style = LocalTypography.current.caption,
                                color = c.textMuted
                            )
                        }
                        if (selected) Icons.Star(size = 16.dp, tint = c.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptPreviewDialog(text: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    Scrim(onDismiss = onDismiss)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                .height(400.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            DesignText(text = "System prompt (spec mode)", style = LocalTypography.current.title2, color = c.text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(c.bg)
                    .padding(Spacing.md)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DesignText(
                        text = text,
                        style = LocalTypography.current.caption,
                        color = c.textMuted
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) { DesignText("Close", style = LocalTypography.current.bodyEmphasis, color = c.textMuted) }
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(c.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCopy
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) { DesignText("Copy", style = LocalTypography.current.bodyEmphasis, color = c.accentText) }
            }
        }
    }
}
