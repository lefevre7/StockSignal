package com.example.stocksignal.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import kotlinx.coroutines.flow.first
import com.example.stocksignal.ui.onboarding.OnboardingRoute
import com.example.stocksignal.ui.marketmovers.MarketMoversRoute
import com.example.stocksignal.ui.search.SearchRoute
import com.example.stocksignal.ui.signals.SignalsFeedRoute
import com.example.stocksignal.ui.notes.NotesRoute
import com.example.stocksignal.ui.settings.SettingsRoute
import com.example.stocksignal.ui.stockdetail.StockDetailRoute

sealed class BottomNavScreen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Watchlist : BottomNavScreen("watchlist", "Watchlist", Icons.AutoMirrored.Filled.ListAlt)
    data object MarketMovers : BottomNavScreen("movers", "Movers", Icons.AutoMirrored.Filled.ShowChart)
    data object Signals : BottomNavScreen("signals", "Signals", Icons.Filled.Assessment)
    data object Notes : BottomNavScreen("notes", "Notes", Icons.AutoMirrored.Filled.Notes)
    data object Settings : BottomNavScreen("settings", "Settings", Icons.Filled.Settings)
}

private val bottomNavItems = listOf(
    BottomNavScreen.Watchlist,
    BottomNavScreen.MarketMovers,
    BottomNavScreen.Signals,
    BottomNavScreen.Notes,
    BottomNavScreen.Settings
)

private const val ONBOARDING_ROUTE = "onboarding"
private const val SEARCH_ROUTE = "search"
private const val STOCK_DETAIL_ROUTE = "stock/{ticker}?eventId={eventId}"

private fun stockDetailRoute(ticker: String, eventId: String? = null): String {
    return if (eventId.isNullOrBlank()) {
        "stock/${Uri.encode(ticker)}"
    } else {
        "stock/${Uri.encode(ticker)}?eventId=${Uri.encode(eventId)}"
    }
}

@Composable
fun StockSignalApp(launchIntent: Intent? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appStateViewModel: AppStateViewModel = hiltViewModel()
    val onboardingCompleted by appStateViewModel.onboardingCompleted.collectAsStateWithLifecycle(initialValue = false)

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val currentBaseRoute = currentRoute?.substringBefore("?")
                val bottomRoutes = remember { bottomNavItems.map { it.route }.toSet() }
                if (currentBaseRoute in bottomRoutes) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentBaseRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                                label = { Text(text = item.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (onboardingCompleted) BottomNavScreen.Watchlist.route else ONBOARDING_ROUTE,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(ONBOARDING_ROUTE) {
                    OnboardingRoute(
                        onFinished = {
                            navController.navigate(BottomNavScreen.Watchlist.route) {
                                popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                            }
                        }
                    )
                }
                composable(BottomNavScreen.Watchlist.route) {
                    com.example.stocksignal.ui.watchlist.WatchlistRoute(
                        onSearchClick = { navController.navigate(SEARCH_ROUTE) },
                        onOpenDetail = { ticker, eventId ->
                            navController.navigate(stockDetailRoute(ticker, eventId))
                        }
                    )
                }
                composable(BottomNavScreen.MarketMovers.route) {
                    MarketMoversRoute(
                        onOpenDetail = { navController.navigate(stockDetailRoute(it)) }
                    )
                }
                composable(BottomNavScreen.Signals.route) {
                    SignalsFeedRoute(
                        onOpenDetail = { ticker, eventId ->
                            navController.navigate(stockDetailRoute(ticker, eventId))
                        }
                    )
                }
                composable(
                    route = "${BottomNavScreen.Notes.route}?symbol={symbol}",
                    arguments = listOf(
                        navArgument("symbol") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    val symbol = entry.arguments?.getString("symbol")
                    NotesRoute(initialSymbol = symbol)
                }
                composable(BottomNavScreen.Settings.route) {
                    SettingsRoute()
                }
                composable(SEARCH_ROUTE) {
                    SearchRoute(
                        onBack = { navController.popBackStack() },
                        onOpenMovers = { navController.navigate(BottomNavScreen.MarketMovers.route) },
                        onOpenDetail = { navController.navigate(stockDetailRoute(it)) }
                    )
                }
                composable(
                    route = STOCK_DETAIL_ROUTE,
                    arguments = listOf(
                        navArgument("ticker") { type = NavType.StringType },
                        navArgument("eventId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "stocksignal://stock/{ticker}" },
                        navDeepLink { uriPattern = "stocksignal://stock/{ticker}?eventId={eventId}" }
                    )
                ) {
                    StockDetailRoute(
                        onBack = { navController.popBackStack() },
                        onAddNote = { ticker ->
                            navController.navigate("${BottomNavScreen.Notes.route}?symbol=${Uri.encode(ticker)}")
                        },
                        onShare = { ticker, eventId ->
                            val link = if (eventId.isNullOrBlank()) {
                                "stocksignal://stock/${Uri.encode(ticker)}"
                            } else {
                                "stocksignal://stock/${Uri.encode(ticker)}?eventId=${Uri.encode(eventId)}"
                            }
                            val text = "$ticker — StockSignal\n$link"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        }
                    )
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(onboardingCompleted, currentRoute) {
        if (onboardingCompleted && currentRoute == ONBOARDING_ROUTE) {
            navController.navigate(BottomNavScreen.Watchlist.route) {
                popUpTo(ONBOARDING_ROUTE) { inclusive = true }
            }
        }
    }

    LaunchedEffect(launchIntent) {
        if (launchIntent?.action == Intent.ACTION_VIEW && launchIntent.data != null) {
            navController.currentBackStackEntryFlow.first()
            navController.handleDeepLink(launchIntent)
        }
    }
}

@Composable
private fun ScreenPlaceholder(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
