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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adaptiveui.animeapp.design.Aspect
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.RemoteImage
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText
import com.adaptiveui.animeapp.interpreter.ColorExtractor
import com.adaptiveui.animeapp.interpreter.ScreenData
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import com.adaptiveui.animeapp.interpreter.SpecCallbacks
import com.adaptiveui.animeapp.interpreter.UiSpecInterpreter

/**
 * Search screen — debounced AniList search with infinite scroll.
 *
 * Spec path: if a [ScreenSpec] is saved for "search", the interpreter renders it. Built-in:
 * top bar with title, search field with leading search icon, 3-column result grid with load-more
 * on scroll near the end.
 */
@Composable
fun SearchScreen(
    onNavigateToDetails: (Int) -> Unit,
    colorExtractor: ColorExtractor,
    modifier: Modifier = Modifier
) {
    val vm: SearchViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val spec by vm.spec.collectAsStateWithLifecycle()

    if (spec != null) {
        val data = ScreenData.fromSearch(
            results = state.results,
            callbacks = SpecCallbacks(onAnimeClick = onNavigateToDetails)
        )
        Box(modifier = modifier.fillMaxSize()) {
            UiSpecInterpreter(spec = spec!!, data = data, colorExtractor = colorExtractor)
        }
        return
    }

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd && !state.isLoading && state.hasNextPage) vm.loadMore()
    }

    Box(modifier = modifier.fillMaxSize().background(LocalColors.current.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DesignText(
                        text = "Search",
                        style = LocalTypography.current.title1,
                        color = LocalColors.current.text,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { /* filters dialog — future */ }) {
                        Icons.Filter(size = 22.dp)
                    }
                }
                SimpleTextField(
                    value = state.query,
                    onValueChange = vm::updateQuery,
                    placeholder = "Search anime…",
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icons.Search(size = 18.dp) }
                )
            }

            // Results
            if (state.isLoading && state.results.isEmpty()) {
                LoadingState()
            } else if (state.error != null && state.results.isEmpty()) {
                ErrorState(message = state.error!!, onRetry = { vm.search() })
            } else if (state.results.isEmpty()) {
                EmptyResults(query = state.query)
            } else {
                LazyVerticalGrid(
                    state = gridState,
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
                    items(state.results, key = { it.id }) { card ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onNavigateToDetails(card.id) }
                                )
                        ) {
                            RemoteImage(
                                url = card.coverUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(Aspect.PORTRAIT),
                                shape = RoundedCornerShape(Radius.md)
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            DesignText(
                                text = card.displayTitle,
                                style = LocalTypography.current.caption,
                                color = LocalColors.current.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                                contentAlignment = Alignment.Center
                            ) {
                                DesignText(
                                    text = "Loading more…",
                                    style = LocalTypography.current.caption,
                                    color = LocalColors.current.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    val c = LocalColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        DesignText(
            text = "Searching…",
            style = LocalTypography.current.body,
            color = c.textMuted
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Spacer(Modifier.height(80.dp))
        DesignText(text = "Search failed", style = LocalTypography.current.title2, color = c.text)
        DesignText(text = message, style = LocalTypography.current.caption, color = c.textMuted)
        RetryPill(onRetry = onRetry)
    }
}

@Composable
private fun EmptyResults(query: String) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Spacer(Modifier.height(80.dp))
        DesignText(
            text = if (query.isBlank()) "No results" else "No results for \"$query\"",
            style = LocalTypography.current.title2,
            color = c.text
        )
        DesignText(
            text = "Try a different search term.",
            style = LocalTypography.current.body,
            color = c.textMuted
        )
    }
}
