package com.quickodds.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quickodds.app.data.local.AppDatabase
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.ui.screens.bets.BetsScreen
import com.quickodds.app.ui.screens.dashboard.DashboardScreen
import com.quickodds.app.ui.screens.market.MarketScreen
import com.quickodds.app.ui.screens.market.MarketDetailScreen
import com.quickodds.app.ui.screens.market.MarketViewModel
import com.quickodds.app.ui.screens.market.MarketDetailViewModel
import com.quickodds.app.ui.theme.QuickOddsTheme
import com.quickodds.app.ui.viewmodel.BetViewModel

/**
 * Navigation destinations.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Markets : Screen("markets")
    object MarketDetail : Screen("market/{matchId}") {
        fun createRoute(matchId: String) = "market/$matchId"
    }
    object Wallet : Screen("wallet")
    object Bets : Screen("bets")
    object Analysis : Screen("analysis")
}

/**
 * Bottom navigation items.
 */
data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Markets, "Markets", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp),
    BottomNavItem(Screen.Bets, "Bets", Icons.Filled.Receipt, Icons.Outlined.Receipt),
    BottomNavItem(Screen.Wallet, "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet)
)

/**
 * Main App composable with navigation.
 */
@Composable
fun QuickOddsApp(database: AppDatabase) {
    QuickOddsTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        // Check if we should show bottom bar
        val showBottomBar = bottomNavItems.any { item ->
            currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
        }

        // Observe wallet for dashboard
        val wallet by database.userWalletDao().observeWallet()
            .collectAsState(initial = null)

        // Observe pending bets count
        val pendingBetsCount by database.virtualBetDao().observePendingBetsCount()
            .collectAsState(initial = 0)

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true

                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        wallet = wallet,
                        pendingBetsCount = pendingBetsCount,
                        valueBetsCount = 0, // Would come from analysis state
                        onNavigateToMarkets = {
                            navController.navigate(Screen.Markets.route)
                        },
                        onNavigateToWallet = {
                            navController.navigate(Screen.Wallet.route)
                        },
                        onNavigateToBets = {
                            navController.navigate(Screen.Bets.route)
                        },
                        onNavigateToAnalysis = {
                            navController.navigate(Screen.Analysis.route)
                        }
                    )
                }

                composable(Screen.Markets.route) {
                    val viewModel: MarketViewModel = hiltViewModel()

                    MarketScreen(
                        viewModel = viewModel,
                        onMatchClick = { matchId ->
                            navController.navigate(Screen.MarketDetail.createRoute(matchId))
                        }
                    )
                }

                composable(
                    route = Screen.MarketDetail.route,
                    arguments = listOf(navArgument("matchId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
                    val viewModel: MarketDetailViewModel = hiltViewModel()

                    LaunchedEffect(matchId) {
                        viewModel.loadMatch(matchId)
                    }

                    val uiState by viewModel.uiState.collectAsState()

                    // USE LOCAL STATE - bypass StateFlow collection issues
                    var localShowConfirmation by remember { mutableStateOf(false) }
                    var localConfirmationData by remember { mutableStateOf<com.quickodds.app.ui.screens.market.BetConfirmationData?>(null) }

                    LaunchedEffect(uiState.betPlaced) {
                        if (uiState.betPlaced) {
                            navController.popBackStack()
                        }
                    }

                    // Show bet confirmation dialog
                    if (localShowConfirmation && localConfirmationData != null) {
                        AlertDialog(
                            onDismissRequest = {
                                localShowConfirmation = false
                                localConfirmationData = null
                            },
                            title = { Text("Confirm Bet") },
                            text = {
                                Column {
                                    Text("Match: ${localConfirmationData!!.matchName}")
                                    Text("Selection: ${localConfirmationData!!.selection}")
                                    Text("Odds: ${localConfirmationData!!.odds}")
                                    Text("Stake: $${localConfirmationData!!.stake}")
                                    Text("Potential Return: $${String.format("%.2f", localConfirmationData!!.potentialReturn)}")
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.confirmBet()
                                        localShowConfirmation = false
                                        localConfirmationData = null
                                    }
                                ) {
                                    Text("Confirm")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    localShowConfirmation = false
                                    localConfirmationData = null
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
                            val match = uiState.match ?: return@MarketDetailScreen
                            val wallet = uiState.wallet ?: return@MarketDetailScreen
                            val bookmaker = match.bookmakers.firstOrNull() ?: return@MarketDetailScreen
                            val h2h = bookmaker.markets.find { it.key == "h2h" } ?: return@MarketDetailScreen

                            val odds = h2h.outcomes.find {
                                it.name.equals(selection, ignoreCase = true)
                            }?.price ?: 0.0

                            if (odds > 0 && stake > 0 && stake <= wallet.balance) {
                                localConfirmationData = com.quickodds.app.ui.screens.market.BetConfirmationData(
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
                                localShowConfirmation = true
                            }
                        },
                        onNavigateBack = { navController.popBackStack() },
                        // These are no longer used but kept for compatibility
                        showBetConfirmation = false,
                        betConfirmationData = null,
                        isPlacingBet = uiState.isPlacingBet,
                        onConfirmBet = { },
                        onDismissConfirmation = { },
                        // Error handling
                        error = uiState.error,
                        onClearError = { viewModel.clearError() }
                    )
                }

                composable(Screen.Wallet.route) {
                    // Wallet screen placeholder
                    WalletScreenPlaceholder(wallet = wallet)
                }

                composable(Screen.Bets.route) {
                    val betViewModel: BetViewModel = hiltViewModel()
                    BetsScreen(viewModel = betViewModel)
                }

                composable(Screen.Analysis.route) {
                    // Custom analysis screen placeholder
                    Text("Custom Analysis Screen - Coming Soon")
                }
            }
        }
    }
}

@Composable
private fun WalletScreenPlaceholder(wallet: UserWallet?) {
    com.quickodds.app.ui.components.WalletHeader(
        wallet = wallet,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

