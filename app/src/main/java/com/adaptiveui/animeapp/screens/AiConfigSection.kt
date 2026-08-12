package com.adaptiveui.animeapp.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
 * KEY FIXES from previous version:
 * 1. All text fields (API key, GitHub token, custom model) use LOCAL state + commit on focus loss.
 *    This eliminates the cursor-jump / 3-char-limit bug caused by async DataStore saves on every keystroke.
 * 2. Model picker uses a proper [Dialog] composable (not a custom overlay inside a Column) — fixes the crash.
 * 3. Added AI mode toggle: Built-in (uses the app's API key) vs External (manual copy/paste flow).
 * 4. Added GitHub token field for the "Build APK" feature.
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
    val context = LocalContext.current
    var showApiKey by remember { mutableStateOf(false) }
    var showGithubToken by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showPromptPreview by remember { mutableStateOf(false) }

    // Local drafts for all text fields — committed to DataStore ONLY on focus loss.
    // This prevents the async-round-trip cursor-jump bug.
    var customModelDraft by remember(settings.customModelId) { mutableStateOf(settings.customModelId) }
    var apiKeyDraft by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var githubTokenDraft by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }

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

        // AI Mode selector
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("AI Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Chip(
                    text = "Built-in",
                    selected = !settings.isExternalMode,
                    onClick = { onSave(settings.copy(aiMode = "BUILT_IN")) }
                )
                Chip(
                    text = "External (manual)",
                    selected = settings.isExternalMode,
                    onClick = { onSave(settings.copy(aiMode = "EXTERNAL")) }
                )
            }
            DesignText(
                text = if (settings.isExternalMode)
                    "External mode: the bubble shows even without an API key. Copy the prompt, paste the response from any AI."
                else
                    "Built-in mode: the app calls the AI provider directly using your API key.",
                style = LocalTypography.current.caption,
                color = c.textMuted
            )
        }

        // ─── Built-in settings (only relevant for BUILT_IN mode) ───
        if (!settings.isExternalMode) {
            // Provider picker
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label("Provider")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FreeModelPresets.providers.forEach { provider ->
                        Chip(
                            text = provider,
                            selected = settings.provider.equals(provider, ignoreCase = true),
                            onClick = {
                                val preset = FreeModelPresets.defaultFor(provider)
                                onSave(settings.copy(provider = provider, baseUrl = preset.baseUrl, modelId = preset.modelId))
                            }
                        )
                    }
                }
            }

            // Model picker (uses proper Dialog — fixes crash)
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
                            text = if (settings.useCustomModel)
                                settings.customModelId.ifBlank { "(custom)" }
                            else settings.modelId,
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
                    if (customModelDraft != settings.customModelId) {
                        onSave(settings.copy(useCustomModel = it, customModelId = customModelDraft))
                    } else {
                        onSave(settings.copy(useCustomModel = it))
                    }
                }
            )
            if (settings.useCustomModel) {
                CommitOnFocusLossTextField(
                    initialValue = customModelDraft,
                    placeholder = "e.g. openai/gpt-oss-20b",
                    onCommit = { newValue ->
                        customModelDraft = newValue
                        if (newValue != settings.customModelId) {
                            onSave(settings.copy(customModelId = newValue))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // API key — uses local draft, commits on focus loss
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label("API key")
                CommitOnFocusLossTextField(
                    initialValue = apiKeyDraft,
                    placeholder = "Paste your API key",
                    singleLine = true,
                    password = !showApiKey,
                    onCommit = { newValue ->
                        apiKeyDraft = newValue
                        if (newValue != settings.apiKey) {
                            onSave(settings.copy(apiKey = newValue))
                            onClearTestResult()
                        }
                    },
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
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // Get free key link
                val signupUrl = FreeModelPresets.defaultFor(settings.provider).keySignupUrl
                DesignText(
                    text = "Get a free key",
                    style = LocalTypography.current.caption,
                    color = c.accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signupUrl)))
                            }.onFailure {
                                Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
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
        }

        // ─── GitHub token (needed for both modes if using "Build APK") ───
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("GitHub token (for Build APK)")
            CommitOnFocusLossTextField(
                initialValue = githubTokenDraft,
                placeholder = "Paste your GitHub PAT",
                singleLine = true,
                password = !showGithubToken,
                onCommit = { newValue ->
                    githubTokenDraft = newValue
                    if (newValue != settings.githubToken) {
                        onSave(settings.copy(githubToken = newValue))
                    }
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showGithubToken = !showGithubToken }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showGithubToken) Icons.EyeOff(size = 18.dp) else Icons.Eye(size = 18.dp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // System prompt preview
        SecondaryButton(
            text = "View system prompt",
            onClick = { showPromptPreview = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Model picker — proper Dialog (fixes the crash)
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
                Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
                showPromptPreview = false
            },
            onDismiss = { showPromptPreview = false }
        )
    }
}

// ─── Commit-on-focus-loss text field (fixes the cursor-jump bug) ───────────

/**
 * Text field that holds its own local state and commits the value ONLY when focus is lost.
 * This prevents the async-DataStore-round-trip cursor reset that made typing impossible.
 */
@Composable
private fun CommitOnFocusLossTextField(
    initialValue: String,
    placeholder: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val c = LocalColors.current
    // Local state — survives recomposition without round-tripping through DataStore.
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surfaceHi)
            .border(0.5.dp, c.outline, RoundedCornerShape(Radius.md))
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                DesignText(
                    text = placeholder,
                    style = LocalTypography.current.body,
                    color = c.textMuted
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                textStyle = LocalTypography.current.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { fs ->
                        if (!fs.isFocused && text != initialValue) {
                            onCommit(text)
                        }
                    }
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailingIcon()
        }
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

// ─── Dialogs (using proper Dialog composable — fixes crash) ───────────────

@Composable
private fun ModelPickerDialog(
    presets: List<FreeModelPreset>,
    selectedModelId: String,
    onPick: (FreeModelPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            DesignText(text = "Select a model", style = LocalTypography.current.title2, color = c.text)
            Spacer(Modifier.height(Spacing.sm))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            DesignText(text = "System prompt", style = LocalTypography.current.title2, color = c.text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 350.dp)
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
