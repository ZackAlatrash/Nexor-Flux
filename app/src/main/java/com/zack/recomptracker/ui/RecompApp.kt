package com.zack.recomptracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zack.recomptracker.core.AppContainer
import com.zack.recomptracker.ui.liquidglass.LiquidBottomTab
import com.zack.recomptracker.ui.liquidglass.LiquidBottomTabs
import com.zack.recomptracker.ui.navigation.AppNavGraph
import com.zack.recomptracker.ui.navigation.Routes
import com.zack.recomptracker.ui.navigation.TopLevelDestination
import com.zack.recomptracker.ui.theme.BgDeep
import com.zack.recomptracker.ui.theme.BgDark
import com.zack.recomptracker.ui.theme.BgMid
import com.zack.recomptracker.ui.theme.RecompTrackerTheme
import com.zack.recomptracker.ui.theme.Violet300

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

// Bottom padding screens add so content isn't hidden under the floating nav.
val FloatingNavHeight: Dp = 80.dp

private val topLevelRoutes = setOf(
    TopLevelDestination.Home.route,
    TopLevelDestination.Body.route,
    TopLevelDestination.Progress.route,
    TopLevelDestination.More.route,
    Routes.Food,
)

// Maps a route string to a 0-based tab index.
private fun routeToTabIndex(route: String?): Int = when (route) {
    TopLevelDestination.Home.route -> 0
    TopLevelDestination.Body.route -> 1
    Routes.Food -> 2
    TopLevelDestination.Progress.route -> 3
    TopLevelDestination.More.route -> 4
    else -> 0
}

private val tabRoutes = listOf(
    TopLevelDestination.Home.route,
    TopLevelDestination.Body.route,
    Routes.Food,
    TopLevelDestination.Progress.route,
    TopLevelDestination.More.route,
)

@Composable
fun RecompApp(container: AppContainer) {
    RecompTrackerTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

        // The LayerBackdrop captures the background for glass sampling.
        val backdrop = rememberLayerBackdrop()

        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalSnackbarHostState provides snackbarHostState,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Layer 1 — background captured into the backdrop GraphicsLayer.
                Box(
                    modifier = Modifier
                        .layerBackdrop(backdrop)
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f    to BgDeep,
                                    0.45f to BgMid,
                                    1f    to BgDark,
                                ),
                            ),
                        ),
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                    ) { innerPadding ->
                        AppNavGraph(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }

                // Layer 2 — liquid glass nav bar (sibling to the backdrop layer).
                if (currentRoute in topLevelRoutes) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        LiquidBottomTabs(
                            selectedTabIndex = { routeToTabIndex(currentRoute) },
                            onTabSelected = { index ->
                                val route = tabRoutes[index]
                                navController.navigate(route) {
                                    popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            backdrop = backdrop,
                            tabsCount = 5,
                            accentColor = Violet300,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        ) {
                            // Home
                            LiquidBottomTab(
                                onClick = {
                                    navController.navigate(TopLevelDestination.Home.route) {
                                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }

                            // Body
                            LiquidBottomTab(
                                onClick = {
                                    navController.navigate(TopLevelDestination.Body.route) {
                                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Body",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("Body", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }

                            // Log
                            LiquidBottomTab(
                                onClick = {
                                    navController.navigate(Routes.Food) {
                                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Log food",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text("Log", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }

                            // Progress
                            LiquidBottomTab(
                                onClick = {
                                    navController.navigate(TopLevelDestination.Progress.route) {
                                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = "Progress",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("Progress", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }

                            // More
                            LiquidBottomTab(
                                onClick = {
                                    navController.navigate(TopLevelDestination.More.route) {
                                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "More",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("More", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
