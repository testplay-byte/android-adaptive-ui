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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.adaptiveui.animeapp.design.Aspect
import com.adaptiveui.animeapp.design.Chip
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.RemoteImage
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText
import com.adaptiveui.animeapp.domain.model.AnimeDetail
import com.adaptiveui.animeapp.domain.model.Category
import com.adaptiveui.animeapp.domain.model.Episode
import com.adaptiveui.animeapp.interpreter.ColorExtractor
import com.adaptiveui.animeapp.interpreter.ScreenData
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import com.adaptiveui.animeapp.interpreter.SpecCallbacks
import com.adaptiveui.animeapp.interpreter.UiSpecInterpreter

/**
 * Details screen — full AniList payload + merged episode metadata.
 *
 * Spec path: if a [ScreenSpec] is saved for "details", the interpreter renders it. Built-in:
 * banner + overlapping cover + title + score + synopsis + info grid + genres + episodes + characters
 * + recommendations. Save button: tap = toggle Default save; long-press = category picker.
 */
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onNavigateToDetails: (Int) -> Unit,
    colorExtractor: ColorExtractor,
    modifier: Modifier = Modifier
) {
    val vm: DetailsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val isSaved by vm.isSaved.collectAsStateWithLifecycle()
    val savedCategoryIds by vm.savedCategoryIds.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val spec by vm.spec.collectAsStateWithLifecycle()

    if (spec != null && state.detail != null) {
        val data = ScreenData.fromDetail(
            detail = state.detail!!,
            episodes = state.episodes,
            callbacks = SpecCallbacks(
                onBack = onBack,
                onRefresh = { vm.refresh() },
                onSave = { if (isSaved) vm.unsave() else vm.saveToDefault() },
                onAnimeClick = onNavigateToDetails
            )
        )
        Box(modifier = modifier.fillMaxSize()) {
            UiSpecInterpreter(spec = spec!!, data = data, colorExtractor = colorExtractor)
        }
        return
    }

    var showCategoryPicker by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }

    val c = LocalColors.current
    val detail = state.detail

    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        if (state.isLoading || detail == null) {
            if (state.error != null) {
                DetailsError(message = state.error!!, onBack = onBack, onRetry = { vm.refresh() })
            } else {
                DetailsShimmer()
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Banner with gradient scrim + top bar overlay
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                if (!detail.bannerImage.isNullOrBlank()) {
                    AsyncImage(
                        model = detail.bannerImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(c.surfaceHi))
                }
                // Gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to c.bg
                            )
                        )
                )
                // Top bar overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icons.Back(size = 22.dp) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.refresh() }) { Icons.Refresh(size = 22.dp) }
                    Spacer(Modifier.width(Spacing.sm))
                    SaveButton(
                        isSaved = isSaved,
                        onTap = { if (isSaved) vm.unsave() else vm.saveToDefault() },
                        onLongPress = { showCategoryPicker = true }
                    )
                }
            }

            // Cover (overlap banner by 60dp) + title block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-60).dp)
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.Bottom
            ) {
                RemoteImage(
                    url = detail.coverUrl,
                    modifier = Modifier
                        .size(width = 120.dp, height = 180.dp),
                    shape = RoundedCornerShape(Radius.md)
                )
                Spacer(Modifier.width(Spacing.lg))
                Column(
                    modifier = Modifier.padding(top = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    DesignText(
                        text = detail.displayTitle,
                        style = LocalTypography.current.title1,
                        color = c.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaLine = listOfNotNull(
                        detail.format?.lowercase()?.replaceFirstChar { it.uppercase() },
                        detail.seasonYear?.toString(),
                        detail.episodes?.let { "$it ep" }
                    ).joinToString(" • ")
                    if (metaLine.isNotBlank()) {
                        DesignText(
                            text = metaLine,
                            style = LocalTypography.current.caption,
                            color = c.textMuted
                        )
                    }
                    detail.scoreLabel?.let { score ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(c.accent)
                                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icons.Star(size = 12.dp, tint = c.accentText)
                                    Spacer(Modifier.width(Spacing.xs))
                                    DesignText(
                                        text = "$score / 10",
                                        style = LocalTypography.current.caption,
                                        color = c.accentText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // Synopsis
            if (!detail.description.isNullOrBlank()) {
                val plain = detail.description!!.replace(Regex("<[^>]+>"), "")
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    SectionHeader("Synopsis")
                    Spacer(Modifier.height(Spacing.sm))
                    DesignText(
                        text = if (synopsisExpanded) plain else plain.take(280) + if (plain.length > 280) "…" else "",
                        style = LocalTypography.current.body,
                        color = c.text
                    )
                    if (plain.length > 280) {
                        Spacer(Modifier.height(Spacing.xs))
                        Box(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { synopsisExpanded = !synopsisExpanded }
                            )
                        ) {
                            DesignText(
                                text = if (synopsisExpanded) "Read less" else "Read more",
                                style = LocalTypography.current.bodyEmphasis,
                                color = c.accent
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }

            // Info grid
            InfoSection(detail = detail)
            Spacer(Modifier.height(Spacing.xl))

            // Genres
            if (detail.genres.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    SectionHeader("Genres")
                    Spacer(Modifier.height(Spacing.sm))
                    // Use FlowRow from foundation
                    GenreFlowRow(genres = detail.genres)
                }
                Spacer(Modifier.height(Spacing.xl))
            }

            // Episodes
            EpisodesSection(
                episodes = state.episodes,
                isLoading = state.isRefreshing
            )

            // Characters
            if (detail.characters.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xl))
                HorizontalSection(title = "Characters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        items(detail.characters, key = { it.characterId }) { ch ->
                            CharacterCard(name = ch.characterName ?: "—", imageUrl = ch.characterImage, role = ch.role)
                        }
                    }
                }
            }

            // Recommendations
            if (detail.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xl))
                HorizontalSection(title = "Recommendations") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        items(detail.recommendations, key = { it.mediaId }) { rec ->
                            RecommendationCard(
                                title = rec.title ?: "Untitled",
                                cover = rec.cover,
                                onClick = { onNavigateToDetails(rec.mediaId) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }

        if (showCategoryPicker) {
            CategoryPickerDialog(
                categories = categories,
                selectedIds = savedCategoryIds.toSet(),
                isSaved = isSaved,
                onAddCategory = { vm.addCategory(it) },
                onApply = { ids ->
                    if (ids.isEmpty()) vm.unsave() else vm.saveToCategories(ids)
                    showCategoryPicker = false
                },
                onRemove = {
                    vm.unsave()
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SaveButton(isSaved: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSaved) Icons.SaveFilled(size = 22.dp, tint = LocalColors.current.accent)
        else Icons.Save(size = 22.dp)
    }
}

@Composable
private fun InfoSection(detail: AnimeDetail) {
    val c = LocalColors.current
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        SectionHeader("Information")
        Spacer(Modifier.height(Spacing.sm))
        InfoRow("Format", detail.format?.lowercase()?.replaceFirstChar { it.uppercase() })
        InfoRow("Episodes", detail.episodes?.toString())
        InfoRow("Duration", detail.duration?.let { "$it min" })
        InfoRow("Status", detail.status?.lowercase()?.replaceFirstChar { it.uppercase() })
        InfoRow("Season", detail.seasonLabel)
        InfoRow("Studios", detail.studios.filter { it.isAnimationStudio }.joinToString(", ") { it.name })
        if (detail.source != null) InfoRow("Source", detail.source)
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val c = LocalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DesignText(
            text = label,
            style = LocalTypography.current.body,
            color = c.textMuted
        )
        DesignText(
            text = value,
            style = LocalTypography.current.bodyEmphasis,
            color = c.text
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GenreFlowRow(genres: List<String>) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        genres.forEach { g ->
            Chip(text = g, selected = false)
        }
    }
}

@Composable
private fun EpisodesSection(episodes: List<Episode>, isLoading: Boolean) {
    val c = LocalColors.current
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        SectionHeader("Episodes")
        Spacer(Modifier.height(Spacing.md))
        when {
            isLoading && episodes.isEmpty() -> {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(vertical = Spacing.xs)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(c.surfaceHi)
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }
            episodes.isEmpty() -> {
                DesignText(
                    text = "No episode info available.",
                    style = LocalTypography.current.body,
                    color = c.textMuted
                )
            }
            else -> {
                episodes.forEach { ep -> EpisodeRow(ep) }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode) {
    val c = LocalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        RemoteImage(
            url = episode.thumbnail,
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp),
            shape = RoundedCornerShape(Radius.sm)
        )
        Spacer(Modifier.width(Spacing.md))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesignText(
                    text = "EP ${episode.number}",
                    style = LocalTypography.current.bodyEmphasis,
                    color = c.accent
                )
                if (episode.filler) {
                    Spacer(Modifier.width(Spacing.sm))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(c.surfaceHi)
                            .padding(horizontal = Spacing.xs, vertical = 2.dp)
                    ) {
                        DesignText(text = "FILLER", style = LocalTypography.current.micro, color = c.textMuted)
                    }
                }
            }
            DesignText(
                text = episode.displayTitle,
                style = LocalTypography.current.body,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            episode.airDate?.let { airDate ->
                DesignText(
                    text = airDate,
                    style = LocalTypography.current.caption,
                    color = c.textMuted
                )
            }
        }
    }
}

@Composable
private fun HorizontalSection(title: String, content: @Composable () -> Unit) {
    Column {
        SectionHeader(
            text = title,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Spacer(Modifier.height(Spacing.md))
        content()
    }
}

@Composable
private fun CharacterCard(name: String, imageUrl: String?, role: String) {
    val c = LocalColors.current
    Column(
        modifier = Modifier.width(100.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        RemoteImage(
            url = imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(Radius.md)
        )
        DesignText(
            text = name,
            style = LocalTypography.current.caption,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        DesignText(
            text = role,
            style = LocalTypography.current.micro,
            color = c.textMuted
        )
    }
}

@Composable
private fun RecommendationCard(title: String, cover: String?, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        RemoteImage(
            url = cover,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Aspect.PORTRAIT),
            shape = RoundedCornerShape(Radius.md)
        )
        DesignText(
            text = title,
            style = LocalTypography.current.caption,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailsShimmer() {
    val c = LocalColors.current
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surfaceHi)
        )
        Spacer(Modifier.height(Spacing.lg))
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(c.surfaceHi)
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun DetailsError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icons.Back(size = 22.dp) }
            DesignText(text = "Error", style = LocalTypography.current.title1, color = c.text, modifier = Modifier.weight(1f))
        }
        DesignText(text = message, style = LocalTypography.current.body, color = c.textMuted)
        RetryPill(onRetry = onRetry)
    }
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedIds: Set<Long>,
    isSaved: Boolean,
    onAddCategory: (String) -> Unit,
    onApply: (List<Long>) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    val mutable = remember(selectedIds) { mutableStateOf(selectedIds.toMutableSet()) }
    var showAdd by remember { mutableStateOf(false) }
    Scrim(onDismiss = onDismiss)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesignText(text = "Categories", style = LocalTypography.current.title2, color = c.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAdd = true }) { Icons.Plus(size = 20.dp) }
            }
            categories.forEach { cat ->
                val checked = cat.id in mutable.value
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val s = mutable.value.toMutableSet()
                                if (checked) s.remove(cat.id) else s.add(cat.id)
                                mutable.value = s
                            }
                        )
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (checked) c.accent else Color.Transparent)
                            .padding(2.dp)
                    )
                    Spacer(Modifier.width(Spacing.md))
                    DesignText(text = cat.name, style = LocalTypography.current.body, color = c.text)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isSaved) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRemove
                            )
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    ) {
                        DesignText(text = "Remove", style = LocalTypography.current.bodyEmphasis, color = c.danger)
                    }
                    Spacer(Modifier.width(Spacing.sm))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(c.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onApply(mutable.value.toList()) }
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    DesignText(text = "Apply", style = LocalTypography.current.bodyEmphasis, color = c.accentText)
                }
            }
        }
    }
    if (showAdd) {
        TextEntryDialog(
            title = "New category",
            placeholder = "Category name",
            confirmLabel = "Add",
            onConfirm = { name -> onAddCategory(name); showAdd = false },
            onDismiss = { showAdd = false }
        )
    }
}
