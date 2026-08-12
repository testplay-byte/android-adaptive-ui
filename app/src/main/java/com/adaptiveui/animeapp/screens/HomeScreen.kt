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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.adaptiveui.animeapp.domain.model.AnimeCard
import com.adaptiveui.animeapp.interpreter.ColorExtractor
import com.adaptiveui.animeapp.interpreter.ScreenData
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import com.adaptiveui.animeapp.interpreter.SpecCallbacks
import com.adaptiveui.animeapp.interpreter.UiSpecInterpreter

/**
 * Home screen — AniList trending/seasonal/upcoming/top-rated/all-time-popular sections.
 *
 * If the user has applied an AI-generated [ScreenSpec] for "home" (via the AI Quick-Edit bubble),
 * the spec is rendered via [UiSpecInterpreter] against the live [ScreenData]. Otherwise the
 * built-in minimal layout is rendered: transparent top bar with title + search + refresh, then
 * five horizontal poster rails.
 */
@Composable
fun HomeScreen(
    onNavigateToDetails: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    colorExtractor: ColorExtractor,
    modifier: Modifier = Modifier
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val spec by vm.spec.collectAsStateWithLifecycle()

    // AI spec path — render the spec tree against the live ScreenData.
    if (spec != null && state is HomeState.Ready) {
        val home = (state as HomeState.Ready).data
        val data = ScreenData.fromHome(
            trending = home.trending,
            seasonal = home.seasonal,
            upcoming = home.upcoming,
            topRated = home.topRated,
            allTimePopular = home.allTimePopular,
            callbacks = SpecCallbacks(
                onAnimeClick = onNavigateToDetails,
                onSearch = onNavigateToSearch,
                onRefresh = { vm.load(forceRefresh = true) }
            )
        )
        Box(modifier = modifier.fillMaxSize()) {
            UiSpecInterpreter(spec = spec!!, data = data, colorExtractor = colorExtractor)
        }
        return
    }

    // Built-in fallback layout.
    Box(modifier = modifier.fillMaxSize().background(LocalColors.current.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item { HomeTopBar(onSearch = onNavigateToSearch, onRefresh = { vm.load(forceRefresh = true) }) }
            when (val s = state) {
                is HomeState.Loading -> item { HomeShimmer() }
                is HomeState.Error -> item {
                    HomeError(message = s.message, onRetry = { vm.retry() })
                }
                is HomeState.Ready -> {
                    item { Spacer(Modifier.height(Spacing.lg)) }
                    homeSection("Trending", s.data.trending, onNavigateToDetails)
                    homeSection("Popular This Season", s.data.seasonal, onNavigateToDetails)
                    homeSection("Upcoming", s.data.upcoming, onNavigateToDetails)
                    homeSection("Top Rated", s.data.topRated, onNavigateToDetails)
                    homeSection("All Time Popular", s.data.allTimePopular, onNavigateToDetails)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onSearch: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DesignText(
            text = "Aniverse",
            style = LocalTypography.current.title1,
            color = LocalColors.current.text,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSearch) { Icons.Search(size = 22.dp) }
        Spacer(Modifier.width(Spacing.sm))
        IconButton(onClick = onRefresh) { Icons.Refresh(size = 22.dp) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeSection(
    title: String,
    items: List<AnimeCard>,
    onClick: (Int) -> Unit
) {
    if (items.isEmpty()) return
    item {
        DesignText(
            text = title,
            style = LocalTypography.current.title3,
            color = LocalColors.current.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )
    }
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            items(items, key = { it.id }) { card ->
                PosterCard(card = card, onClick = { onClick(card.id) })
            }
        }
    }
    item { Spacer(Modifier.height(Spacing.xl)) }
}

@Composable
private fun PosterCard(card: AnimeCard, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box {
            RemoteImage(
                url = card.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(Aspect.PORTRAIT),
                shape = RoundedCornerShape(Radius.md)
            )
            card.scoreLabel?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(c.accent)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    DesignText(
                        text = score,
                        style = LocalTypography.current.micro,
                        color = c.accentText
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        DesignText(
            text = card.displayTitle,
            style = LocalTypography.current.caption,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeShimmer() {
    val c = LocalColors.current
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)) {
        repeat(3) { sectionIdx ->
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 18.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(c.surfaceHi)
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) {
                    Column(modifier = Modifier.width(140.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(Aspect.PORTRAIT)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(c.surfaceHi)
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(c.surfaceHi)
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp, horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        DesignText(
            text = "Couldn't load home",
            style = LocalTypography.current.title2,
            color = c.text
        )
        DesignText(
            text = message,
            style = LocalTypography.current.caption,
            color = c.textMuted
        )
        RetryPill(onRetry = onRetry)
    }
}

// ─── Shared bits used across screens ─────────────────────────────────────────

@Composable
internal fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
internal fun RetryPill(onRetry: () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(c.accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRetry
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        DesignText(
            text = "Retry",
            style = LocalTypography.current.bodyEmphasis,
            color = c.accentText
        )
    }
}

@Composable
internal fun ShimmerBox(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(c.surfaceHi)
    )
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    DesignText(
        text = text,
        style = LocalTypography.current.title3,
        color = LocalColors.current.textMuted,
        modifier = modifier
    )
}
