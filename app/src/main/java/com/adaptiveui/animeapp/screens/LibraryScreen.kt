package com.adaptiveui.animeapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adaptiveui.animeapp.design.Aspect
import com.adaptiveui.animeapp.design.Chip
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.RemoteImage
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText
import com.adaptiveui.animeapp.domain.model.Category
import com.adaptiveui.animeapp.domain.model.LibraryEntry
import com.adaptiveui.animeapp.interpreter.ColorExtractor
import com.adaptiveui.animeapp.interpreter.ScreenData
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import com.adaptiveui.animeapp.interpreter.SpecCallbacks
import com.adaptiveui.animeapp.interpreter.UiSpecInterpreter

/**
 * Library screen — the user's saved anime, organized by category.
 *
 * Spec path: if a [ScreenSpec] is saved for "library", the interpreter renders it.
 * Built-in: category chip row + 3-column poster grid. Long-press on an entry opens a remove
 * dialog; long-press on a non-default category opens a rename/delete dialog.
 */
@Composable
fun LibraryScreen(
    onNavigateToDetails: (Int) -> Unit,
    colorExtractor: ColorExtractor,
    modifier: Modifier = Modifier
) {
    val vm: LibraryViewModel = hiltViewModel()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selectedId by vm.selectedCategoryId.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val spec by vm.spec.collectAsStateWithLifecycle()

    if (spec != null) {
        val data = ScreenData.fromLibrary(
            entries = entries,
            categories = categories,
            callbacks = SpecCallbacks(
                onAnimeClick = onNavigateToDetails,
                onCategorySelect = { id -> vm.selectCategory(id) }
            )
        )
        Box(modifier = modifier.fillMaxSize()) {
            UiSpecInterpreter(spec = spec!!, data = data, colorExtractor = colorExtractor)
        }
        return
    }

    var removeEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var categoryAction by remember { mutableStateOf<Category?>(null) }

    Box(modifier = modifier.fillMaxSize().background(LocalColors.current.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DesignText(
                    text = "Library",
                    style = LocalTypography.current.title1,
                    color = LocalColors.current.text,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* show inline add category dialog */ categoryAction = Category(-1, "", false, 0L, 0) }) {
                    Icons.Plus(size = 22.dp)
                }
            }

            // Category chips
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        Chip(
                            text = "All",
                            selected = selectedId == null,
                            onClick = { vm.selectCategory(null) }
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        CategoryChip(
                            category = cat,
                            selected = selectedId == cat.id,
                            onClick = { vm.selectCategory(cat.id) },
                            onLongPress = { if (!cat.isDefault) categoryAction = cat }
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            // Grid or empty state
            if (entries.isEmpty()) {
                LibraryEmptyState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    items(entries, key = { it.animeId }) { entry ->
                        LibraryPosterCard(
                            entry = entry,
                            onClick = { onNavigateToDetails(entry.animeId) },
                            onLongPress = { removeEntry = entry }
                        )
                    }
                }
            }
        }

        // Remove-entry dialog
        removeEntry?.let { entry ->
            ConfirmDialog(
                title = "Remove \"${entry.title}\"?",
                message = "This anime will be removed from your library.",
                confirmLabel = "Remove",
                danger = true,
                onConfirm = {
                    vm.removeEntry(entry.animeId)
                    removeEntry = null
                },
                onDismiss = { removeEntry = null }
            )
        }

        // Category action dialog (rename/delete)
        categoryAction?.let { cat ->
            if (cat.id == -1L) {
                TextEntryDialog(
                    title = "New category",
                    placeholder = "Category name",
                    confirmLabel = "Add",
                    onConfirm = { name -> vm.addCategory(name) },
                    onDismiss = { categoryAction = null }
                )
            } else {
                CategoryActionDialog(
                    category = cat,
                    onRename = { name -> vm.renameCategory(cat.id, name); categoryAction = null },
                    onDelete = { vm.deleteCategory(cat.id); categoryAction = null },
                    onDismiss = { categoryAction = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Chip(text = category.name, selected = selected)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPosterCard(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        RemoteImage(
            url = entry.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Aspect.PORTRAIT),
            shape = RoundedCornerShape(Radius.md)
        )
        Spacer(Modifier.height(Spacing.xs))
        DesignText(
            text = entry.title,
            style = LocalTypography.current.caption,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryEmptyState() {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        DesignText(
            text = "Your library is empty",
            style = LocalTypography.current.title2,
            color = c.text
        )
        DesignText(
            text = "Save anime from the details screen to see them here.",
            style = LocalTypography.current.body,
            color = c.textMuted
        )
    }
}

// ─── Reusable dialogs (custom — NO Material3) ────────────────────────────────

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    Scrim(onDismiss = onDismiss)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            DesignText(text = title, style = LocalTypography.current.title2, color = c.text)
            DesignText(text = message, style = LocalTypography.current.body, color = c.textMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    DesignText(text = "Cancel", style = LocalTypography.current.bodyEmphasis, color = c.textMuted)
                }
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(if (danger) c.danger else c.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onConfirm
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    DesignText(
                        text = confirmLabel,
                        style = LocalTypography.current.bodyEmphasis,
                        color = c.accentText
                    )
                }
            }
        }
    }
}

@Composable
internal fun TextEntryDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    var text by remember { mutableStateOf(initial) }
    Scrim(onDismiss = onDismiss)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            DesignText(text = title, style = LocalTypography.current.title2, color = c.text)
            SimpleTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    DesignText(text = "Cancel", style = LocalTypography.current.bodyEmphasis, color = c.textMuted)
                }
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(c.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (text.isNotBlank()) onConfirm(text) }
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    DesignText(
                        text = confirmLabel,
                        style = LocalTypography.current.bodyEmphasis,
                        color = c.accentText
                    )
                }
            }
        }
    }
}

@Composable
internal fun CategoryActionDialog(
    category: Category,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    var mode by remember { mutableStateOf("menu") } // "menu" | "rename"
    if (mode == "menu") {
        Scrim(onDismiss = onDismiss)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.xl)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(c.surface)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                DesignText(text = category.name, style = LocalTypography.current.title2, color = c.text)
                Spacer(Modifier.height(Spacing.xs))
                DialogActionRow(label = "Rename", onClick = { mode = "rename" })
                DialogActionRow(label = "Delete", danger = true, onClick = onDelete)
                Spacer(Modifier.height(Spacing.xs))
                DialogActionRow(label = "Cancel", muted = true, onClick = onDismiss)
            }
        }
    } else {
        TextEntryDialog(
            title = "Rename category",
            placeholder = category.name,
            confirmLabel = "Save",
            initial = category.name,
            onConfirm = onRename,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun DialogActionRow(
    label: String,
    danger: Boolean = false,
    muted: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalColors.current
    val color = when {
        danger -> c.danger
        muted -> c.textMuted
        else -> c.text
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = Spacing.md)
    ) {
        DesignText(text = label, style = LocalTypography.current.body, color = color)
    }
}

@Composable
internal fun Scrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalColors.current.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    )
}
