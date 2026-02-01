package com.quickodds.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.quickodds.app.ui.screens.bets.BetsScreen
import com.quickodds.app.ui.screens.dashboard.DashboardScreen
import com.quickodds.app.ui.screens.market.MarketDetailScreen
import com.quickodds.app.ui.screens.market.MarketDetailViewModel
import com.quickodds.app.ui.screens.market.MarketsScreen
import com.quickodds.app.ui.screens.wallet.WalletScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Dashboard.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                wallet = null,
                pendingBetsCount = 0,
                valueBetsCount = 0,
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
                    // TODO: Add analysis screen
                }
            )
        }

        composable(Screen.Markets.route) {
            MarketsScreen(
                onNavigateToMarket = { marketId ->
                    navController.navigate(Screen.MarketDetail.createRoute(marketId))
                }
            )
        }

        composable(Screen.Wallet.route) {
            WalletScreen()
        }

        composable(Screen.Bets.route) {
            BetsScreen()
        }

        composable(
            route = Screen.MarketDetail.route,
            arguments = listOf(
                navArgument("marketId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val marketId = backStackEntry.arguments?.getString("marketId") ?: ""
            val viewModel: MarketDetailViewModel = hiltViewModel()

            LaunchedEffect(marketId) {
                viewModel.loadMatch(marketId)
            }

            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.betPlaced) {
                if (uiState.betPlaced) {
                    navController.popBackStack()
                }
            }

            MarketDetailScreen(
                match = uiState.match,
                wallet = uiState.wallet,
                analysisResult = uiState.analysisResult,
                isAnalyzing = uiState.isAnalyzing,
                onAnalyze = { viewModel.analyzeMatch() },
                onPlaceBet = { selection, stake ->
                    viewModel.placeBet(selection, stake)
                },
                onNavigateBack = { navController.popBackStack() },
                // Bet confirmation bottom sheet
                showBetConfirmation = uiState.showBetConfirmation,
                betConfirmationData = uiState.betConfirmationData,
                isPlacingBet = uiState.isPlacingBet,
                onConfirmBet = { viewModel.confirmBet() },
                onDismissConfirmation = { viewModel.dismissBetConfirmation() }
            )
        }
    }
}
