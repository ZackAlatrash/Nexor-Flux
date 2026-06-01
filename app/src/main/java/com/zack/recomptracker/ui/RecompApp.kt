package com.zack.recomptracker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zack.recomptracker.core.AppContainer
import com.zack.recomptracker.ui.navigation.AppNavGraph
import com.zack.recomptracker.ui.navigation.TopLevelDestination
import com.zack.recomptracker.ui.theme.RecompTrackerTheme

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

private val NavBlue = Color(0xFF3b82f6)
private val NavPillBg = Color(0xFF1e3a5f)
private val NavInactive = Color(0xFF444444)
private val NavBarBg = Color(0xFF111111)

@Composable
fun RecompApp(container: AppContainer) {
    RecompTrackerTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalSnackbarHostState provides snackbarHostState,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = NavBarBg,
                        tonalElevation = 0.dp,
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(TopLevelDestination.Home.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = NavPillBg,
                                    selectedIconColor = NavBlue,
                                    selectedTextColor = NavBlue,
                                    unselectedIconColor = NavInactive,
                                    unselectedTextColor = NavInactive,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                AppNavGraph(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
