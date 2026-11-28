package com.code7.techupdates.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.code7.techupdates.R
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.safeUrl
import com.code7.techupdates.ui.common.PlaceholderScreen
import com.code7.techupdates.ui.feed.FeedRoute
import com.code7.techupdates.ui.library.LibraryRoute

private enum class TopLevel(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    NEWS(Routes.NEWS, R.string.nav_news, R.drawable.ic_news),
    LIBRARY(Routes.LIBRARY, R.string.nav_library, R.drawable.ic_library),
}

/**
 * App shell: bottom navigation (News, Library) plus full-screen reader and comments destinations.
 * [storyLookup] resolves stories already seen in a feed (the reader's in-memory hand-off).
 */
@Composable
fun AppRoot(storyLookup: (String) -> Story?) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination == null || TopLevel.entries.any { it.route == destination.route }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavHost(navController, storyLookup, Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    storyLookup: (String) -> Story?,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.NEWS,
        modifier = modifier,
    ) {
        composable(Routes.NEWS) {
            FeedRoute(
                onOpenStory = { navController.navigate(Routes.story(it)) },
                onOpenComments = { navController.navigate(Routes.comments(it)) },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryRoute(
                onOpenStory = { navController.navigate(Routes.story(it)) },
                onOpenComments = { navController.navigate(Routes.comments(it)) },
            )
        }
        composable(
            route = Routes.STORY,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = Routes.STORY_DEEP_LINK }),
        ) { entry ->
            val story = entry.arguments?.getString(Routes.ARG_ID)?.let(storyLookup)
            val uriHandler = LocalUriHandler.current
            val url = safeUrl(story?.url)
            PlaceholderScreen(
                title = story?.title ?: stringResource(R.string.app_name),
                message = stringResource(R.string.reader_coming_soon),
                onBack = { navController.navigateUpOrNews() },
                actionLabel = url?.let { stringResource(R.string.reader_open_original) },
                onAction = { url?.let(uriHandler::openUri) },
            )
        }
        composable(
            route = Routes.COMMENTS,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
        ) { entry ->
            val story = entry.arguments?.getString(Routes.ARG_ID)?.let(storyLookup)
            PlaceholderScreen(
                title = story?.title ?: stringResource(R.string.app_name),
                message = stringResource(R.string.comments_coming_soon),
                onBack = { navController.navigateUpOrNews() },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) =
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

/** Deep-linked screens may have nothing below them: fall back to News. */
private fun NavHostController.navigateUpOrNews() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Routes.NEWS) { popUpTo(graph.id) { inclusive = true } }
    }
}
