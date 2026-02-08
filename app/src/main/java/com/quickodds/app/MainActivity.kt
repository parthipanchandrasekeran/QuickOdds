package com.quickodds.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quickodds.app.ui.screens.bets.BetsScreen
import com.quickodds.app.ui.screens.market.MarketScreen
import com.quickodds.app.ui.screens.market.MarketDetailScreen
import com.quickodds.app.ui.screens.market.MarketDetailViewModel
import com.quickodds.app.ui.screens.market.MarketViewModel
import com.quickodds.app.ui.theme.QuickOddsTheme
import com.quickodds.app.ui.viewmodel.BetViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity for Quick Odds app.
 *
 * Annotated with @AndroidEntryPoint to enable Hilt injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            QuickOddsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QuickOddsNavHost()
                }
            }
        }
    }
}

/**
 * Navigation destinations for the app.
 */
sealed class AppScreen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Markets : AppScreen(
        route = "markets",
        title = "Markets",
        selectedIcon = Icons.Filled.TrendingUp,
        unselectedIcon = Icons.Outlined.TrendingUp
    )

    data object Bets : AppScreen(
        route = "bets",
        title = "My Bets",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    )

    data object MarketDetail : AppScreen(
        route = "market/{matchId}",
        title = "Match Details",
        selectedIcon = Icons.Filled.TrendingUp,
        unselectedIcon = Icons.Outlined.TrendingUp
    ) {
        fun createRoute(matchId: String) = "market/$matchId"
    }

    companion object {
        val bottomNavItems = listOf(Markets, Bets)
    }
}

/**
 * Main NavHost composable that sets up navigation between screens.
 */
@Composable
fun QuickOddsNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine if we should show bottom navigation bar
    val showBottomBar = AppScreen.bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    AppScreen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppScreen.Markets.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Markets Screen - Browse live matches
            composable(AppScreen.Markets.route) {
                val viewModel: MarketViewModel = hiltViewModel()

                MarketScreen(
                    viewModel = viewModel,
                    onMatchClick = { matchId ->
                        navController.navigate(AppScreen.MarketDetail.createRoute(matchId))
                    }
                )
            }

            // Market Detail Screen - View match details and place bets
            composable(
                route = AppScreen.MarketDetail.route,
                arguments = listOf(
                    navArgument("matchId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
                val viewModel: MarketDetailViewModel = hiltViewModel()

                // Initialize with match ID
                LaunchedEffect(matchId) {
                    viewModel.loadMatch(matchId)
                }

                val uiState by viewModel.uiState.collectAsState()

                // LOCAL STATE for bet confirmation (bypasses StateFlow collection issues)
                var showConfirmation by remember { mutableStateOf(false) }
                var confirmationData by remember { mutableStateOf<com.quickodds.app.ui.screens.market.BetConfirmationData?>(null) }

                // Navigate back after bet is placed
                LaunchedEffect(uiState.betPlaced) {
                    if (uiState.betPlaced) {
                        navController.popBackStack()
                    }
                }

                // Show confirmation dialog
                if (showConfirmation && confirmationData != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {
                            showConfirmation = false
                            confirmationData = null
                        },
                        title = { Text("Confirm Bet") },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                Text("Match: ${confirmationData!!.matchName}")
                                Text("Selection: ${confirmationData!!.selection}")
                                Text("Odds: ${confirmationData!!.odds}")
                                Text("Stake: $${confirmationData!!.stake}")
                                Text("Potential Return: $${String.format("%.2f", confirmationData!!.potentialReturn)}")
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.confirmBet(confirmationData)
                                    showConfirmation = false
                                    confirmationData = null
                                }
                            ) {
                                Text("Confirm")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showConfirmation = false
                                confirmationData = null
                            }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                MarketDetailScreen(
                    match = uiState.match,
                    wallet = uiState.wallet,
                    analysisResult = uiState.analysisResult,
                    isAnalyzing = uiState.isAnalyzing,
                    onAnalyze = { viewModel.analyzeMatch() },
                    onPlaceBet = { selection, stake ->
                        // Build confirmation data locally
                        val match = uiState.match
                        val wallet = uiState.wallet
                        if (match != null && wallet != null) {
                            val bookmaker = match.bookmakers.firstOrNull()
                            val h2h = bookmaker?.markets?.find { it.key == "h2h" }
                            val odds = h2h?.outcomes?.find {
                                it.name.equals(selection, ignoreCase = true)
                            }?.price ?: 0.0

                            if (odds > 0 && stake > 0 && stake <= wallet.balance) {
                                confirmationData = com.quickodds.app.ui.screens.market.BetConfirmationData(
                                    eventId = match.id,
                                    sportKey = match.sportKey,
                                    matchName = "${match.homeTeam} vs ${match.awayTeam}",
                                    homeTeam = match.homeTeam,
                                    awayTeam = match.awayTeam,
                                    selection = selection,
                                    odds = odds,
                                    stake = stake,
                                    potentialReturn = stake * odds,
                                    commenceTime = try { java.time.Instant.parse(match.commenceTime).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
                                )
                                showConfirmation = true
                            }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Bets Screen - View bet history
            composable(AppScreen.Bets.route) {
                val viewModel: BetViewModel = hiltViewModel()

                BetsScreen(viewModel = viewModel)
            }
        }
    }
}
