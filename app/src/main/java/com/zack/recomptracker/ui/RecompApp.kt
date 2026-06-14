package com.zack.recomptracker.ui

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.liquidglass.LiquidBottomTab
import com.zack.recomptracker.ui.liquidglass.LiquidBottomTabs
import com.zack.recomptracker.ui.component.GlassOrbBackground
import com.zack.recomptracker.ui.navigation.AppNavGraph
import com.zack.recomptracker.ui.navigation.Routes
import com.zack.recomptracker.ui.navigation.TopLevelDestination
import com.zack.recomptracker.ui.theme.AccentTheme
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.theme.RecompTrackerTheme
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastController
import com.zack.recomptracker.ui.toast.ToastOverlay

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

// Bottom padding screens add so content isn't hidden under the floating nav.
val FloatingNavHeight: Dp = 80.dp

private val topLevelRoutes = setOf(
    TopLevelDestination.Home.route,
    TopLevelDestination.Body.route,
    TopLevelDestination.Coach.route,
    TopLevelDestination.More.route,
    Routes.Food,
)

// Maps a route string to a 0-based tab index.
private fun routeToTabIndex(route: String?): Int = when (route) {
    TopLevelDestination.Home.route -> 0
    TopLevelDestination.Body.route -> 1
    Routes.Food -> 2
    TopLevelDestination.Coach.route -> 3
    TopLevelDestination.More.route -> 4
    else -> 0
}

private val tabRoutes = listOf(
    TopLevelDestination.Home.route,
    TopLevelDestination.Body.route,
    Routes.Food,
    TopLevelDestination.Coach.route,
    TopLevelDestination.More.route,
)

@Composable
fun RecompApp(container: AppContainer, darkMode: Boolean) {
    val accentTheme by container.uiPreferences.accentTheme
        .collectAsStateWithLifecycle(initialValue = AccentTheme.VIOLET)
    RecompTrackerTheme(accentTheme = accentTheme, darkMode = darkMode) {
        val appColors = LocalAppColors.current
        val navController = rememberNavController()
        val toastController = remember { ToastController() }
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

        // Two separate backdrops to avoid a circular GraphicsLayer read:
        //   contentBackdrop — records the gradient only; provided via LocalBackdrop so
        //     glass buttons inside screens sample the gradient without reading a layer
        //     that is currently recording them (which caused crashes on enter/exit).
        //   navBackdrop — records gradient + full app content; used by the nav bar so
        //     it can blur the live content scrolling behind it, restoring the glass effect.
        val contentBackdrop = rememberLayerBackdrop()
        val navBackdrop     = rememberLayerBackdrop()

        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalToastController provides toastController,
            LocalBackdrop provides contentBackdrop,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Layer 1+2 — navBackdrop records both the gradient and the app content.
                // Inside it, contentBackdrop records the gradient only.
                // Glass buttons in screens read contentBackdrop (gradient, no circular read).
                // The nav bar (outside this box) reads navBackdrop (gradient + content).
                Box(
                    modifier = Modifier
                        .layerBackdrop(navBackdrop)
                        .fillMaxSize(),
                ) {
                    // Background captured into contentBackdrop.
                    // Glass composables inside AppNavGraph read this backdrop and blur over it.
                    Box(
                        modifier = Modifier
                            .layerBackdrop(contentBackdrop)
                            .fillMaxSize(),
                    ) {
                        GlassOrbBackground(accentTheme = accentTheme, darkMode = darkMode)
                    }

                    // Previous gradient + aurora background (kept for reference):
                    // Box(
                    //     modifier = Modifier
                    //         .layerBackdrop(contentBackdrop)
                    //         .fillMaxSize()
                    //         .background(
                    //             Brush.verticalGradient(
                    //                 colorStops = arrayOf(
                    //                     0f    to BgDeep,
                    //                     0.45f to BgMid,
                    //                     1f    to BgDark,
                    //                 ),
                    //             ),
                    //         ),
                    // ) {
                    //     AuroraBackground()
                    // }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                    ) { innerPadding ->
                        AppNavGraph(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }

                // Layer 3 — liquid glass nav bar (outside navBackdrop, reads from it).
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
                            backdrop = navBackdrop,
                            tabsCount = 5,
                            accentColor = LocalAppAccent.current.inkLighter,
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
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = appColors.textPrimary)
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
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("Body", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = appColors.textPrimary)
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
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text("Log", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = appColors.textPrimary)
                            }

                            // Coach
                            LiquidBottomTab(
                                onClick = {
                                    navController.navigate(TopLevelDestination.Coach.route) {
                                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Coach",
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("Coach", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = appColors.textPrimary)
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
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text("More", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = appColors.textPrimary)
                            }
                        }
                    }
                }

                // Toast overlay — always above nav
                ToastOverlay()
            }
        }
    }
}
