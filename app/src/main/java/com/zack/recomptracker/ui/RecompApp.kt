package com.zack.recomptracker.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zack.recomptracker.core.AppContainer
import com.zack.recomptracker.ui.navigation.AppNavGraph
import com.zack.recomptracker.ui.navigation.Routes
import com.zack.recomptracker.ui.navigation.TopLevelDestination
import com.zack.recomptracker.ui.theme.BgDeep
import com.zack.recomptracker.ui.theme.BgDark
import com.zack.recomptracker.ui.theme.BgMid
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.NavLogEnd
import com.zack.recomptracker.ui.theme.NavLogStart
import com.zack.recomptracker.ui.theme.RecompTrackerTheme
import com.zack.recomptracker.ui.theme.Violet300
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

// Bottom padding screens add so content isn't hidden under the floating nav.
// Pill height (~50dp) + bottom gap (16dp) + a little buffer = 78dp.
val FloatingNavHeight: Dp = 78.dp

private val topLevelRoutes = setOf(
    TopLevelDestination.Home.route,
    TopLevelDestination.Body.route,
    TopLevelDestination.Progress.route,
    TopLevelDestination.More.route,
    Routes.Food,
)

@Composable
fun RecompApp(container: AppContainer) {
    RecompTrackerTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val liquidState = rememberLiquidState()

        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalSnackbarHostState provides snackbarHostState,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f    to BgDeep,
                                0.45f to BgMid,
                                1f    to BgDark,
                            ),
                        ),
                    )
                    .liquefiable(liquidState),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent, // load-bearing: Liquid Glass samples through this
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                if (currentRoute in topLevelRoutes) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        CompactPillNav(
                            currentRoute = currentRoute,
                            liquidState  = liquidState,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onLogClick = {
                                navController.navigate(Routes.Food) {
                                    popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compact Pill Navigation — Liquid Glass aesthetic
//
// Design principles:
//  • Full semicircle ends (CornerPill = 100.dp) — true capsule shape
//  • Liquid Glass effect via FletchMcKee/liquid library
//  • liquefiable() on the root background Box; liquid() on the pill Row
//  • Icons 20dp / labels 10sp / 2dp gap — compact, Apple-tight
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactPillNav(
    currentRoute: String?,
    liquidState: LiquidState,
    onNavigate: (String) -> Unit,
    onLogClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 16dp side margins, 16dp bottom lift
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .liquid(liquidState) {
                shape = RoundedCornerShape(CornerPill)
                frost = 12.dp
                edge  = 0.6f
            }
            // Inner horizontal padding; vertical settled by item padding below
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(
            icon     = Icons.Default.Home,
            label    = "Home",
            isActive = currentRoute == TopLevelDestination.Home.route,
            onClick  = { onNavigate(TopLevelDestination.Home.route) },
            modifier = Modifier.weight(1f),
        )
        TabItem(
            icon     = Icons.Default.Person,
            label    = "Body",
            isActive = currentRoute == TopLevelDestination.Body.route,
            onClick  = { onNavigate(TopLevelDestination.Body.route) },
            modifier = Modifier.weight(1f),
        )
        LogTab(
            isActive = currentRoute == Routes.Food,
            onClick  = onLogClick,
            modifier = Modifier.weight(1f),
        )
        TabItem(
            icon     = Icons.AutoMirrored.Filled.TrendingUp,
            label    = "Progress",
            isActive = currentRoute == TopLevelDestination.Progress.route,
            onClick  = { onNavigate(TopLevelDestination.Progress.route) },
            modifier = Modifier.weight(1f),
        )
        TabItem(
            icon     = Icons.Default.MoreHoriz,
            label    = "More",
            isActive = currentRoute == TopLevelDestination.More.route,
            onClick  = { onNavigate(TopLevelDestination.More.route) },
            modifier = Modifier.weight(1f),
        )
    }
}

// Standard tab item — matches iOS tab bar item layout exactly:
// icon (20dp) + 2dp gap + label (10sp), total ~48dp vertical with padding.
@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication        = ripple(bounded = false, radius = 28.dp),
                onClick           = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = if (isActive) Violet300 else Color.White.copy(alpha = 0.28f),
            modifier           = Modifier.size(20.dp),
        )
        Text(
            text       = label,
            fontSize   = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (isActive) Violet300 else Color.White.copy(alpha = 0.28f),
        )
    }
}

// Log tab — same height as TabItem, just with a compact circle badge instead
// of a bare icon. The circle differentiates the action without dominating.
@Composable
private fun LogTab(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication        = ripple(bounded = false, radius = 28.dp),
                onClick           = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r  = size.minDimension / 2f

                    // ── Soft glow ──────────────────────────────────────────────
                    drawIntoCanvas { canvas ->
                        val glowPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color       = android.graphics.Color.argb(
                                if (isActive) 90 else 55, 139, 92, 246,
                            )
                            maskFilter = BlurMaskFilter(10.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawCircle(cx, cy, r + 2.dp.toPx(), glowPaint)
                    }

                    // ── Gradient fill ──────────────────────────────────────────
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(NavLogStart, NavLogEnd),
                        ),
                    )

                    // ── Inner sheen ────────────────────────────────────────────
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x22FFFFFF), Color(0x00FFFFFF)),
                            endY   = size.height * 0.55f,
                        ),
                    )

                    // ── Border ─────────────────────────────────────────────────
                    drawCircle(
                        color = Color(0x60C4B5FD),
                        style = Stroke(0.75.dp.toPx()),
                    )
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "Log food",
                tint               = Color.White,
                modifier           = Modifier.size(16.dp),
            )
        }
        Text(
            text       = "Log",
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (isActive) Violet300 else Color.White.copy(alpha = 0.55f),
        )
    }
}
