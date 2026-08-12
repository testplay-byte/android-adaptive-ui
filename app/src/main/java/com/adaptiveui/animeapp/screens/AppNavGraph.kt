package com.adaptiveui.animeapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adaptiveui.animeapp.ai.AiViewModel
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText

// ─── Routes ──────────────────────────────────────────────────────────────────

private object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{animeId}"
    fun details(id: Int) = "details/$id"
}

@Composable
fun AppNavGraph(
    aiVm: AiViewModel,
    colorExtractor: com.adaptiveui.animeapp.interpreter.ColorExtractor
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    Box(modifier = Modifier.fillMaxSize().background(LocalColors.current.bg)) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToDetails = { id -> navController.navigate(Routes.details(id)) },
                    onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                    colorExtractor = colorExtractor
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onNavigateToDetails = { id -> navController.navigate(Routes.details(id)) },
                    colorExtractor = colorExtractor
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onNavigateToDetails = { id -> navController.navigate(Routes.details(id)) },
                    colorExtractor = colorExtractor
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument("animeId") { type = NavType.IntType })
            ) {
                // animeId is read by DetailsViewModel via SavedStateHandle (Hilt auto-injects nav args).
                DetailsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDetails = { id -> navController.navigate(Routes.details(id)) },
                    colorExtractor = colorExtractor
                )
            }
        }

        // Floating bottom nav (hidden on details).
        if (!currentRoute.startsWith("details")) {
            BottomNav(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            // Pop back to start destination and avoid stacking.
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // AI bubble overlay (current route is the "screen name").
        val screenName = when {
            currentRoute.startsWith("details") -> "details"
            else -> currentRoute
        }
        AiBubbleHost(
            screenName = screenName,
            aiVm = aiVm
        )
    }
}

// ─── Floating bottom nav ─────────────────────────────────────────────────────

@Composable
private fun BottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val items = listOf(
        NavItem("home", "Home") { Icons.Home(it) },
        NavItem("library", "Library") { Icons.Library(it) },
        NavItem("search", "Search") { Icons.Search(it) },
        NavItem("settings", "Settings") { Icons.Settings(it) }
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(c.surface)
                .padding(Spacing.xs)
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavPill(
                    item = item,
                    selected = selected,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable (Color) -> Unit
)

@Composable
private fun NavPill(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val bg = if (selected) c.accent else Color.Transparent
    val fg = if (selected) c.accentText else c.textMuted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        item.icon(fg)
        if (selected) {
            DesignText(text = item.label, style = LocalTypography.current.caption, color = fg)
        }
    }
}
