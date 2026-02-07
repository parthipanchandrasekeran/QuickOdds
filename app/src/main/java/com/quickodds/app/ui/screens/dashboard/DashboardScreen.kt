package com.quickodds.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.ui.components.*
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ui.theme.OrangeWarning

/**
 * Dashboard Screen - Main entry point of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    wallet: UserWallet?,
    pendingBetsCount: Int,
    valueBetsCount: Int,
    onNavigateToMarkets: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToBets: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Quick Odds",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI-Powered Betting Analysis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Notification badge for value bets
                    if (valueBetsCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text("$valueBetsCount") }
                            }
                        ) {
                            IconButton(onClick = onNavigateToMarkets) {
                                Icon(
                                    imageVector = Icons.Filled.Psychology,
                                    contentDescription = "Value Bets"
                                )
                            }
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Wallet Card
            item {
                WalletHeader(
                    wallet = wallet,
                    onAddFundsClick = onNavigateToWallet
                )
            }

            // Quick Stats Row
            item {
                QuickStatsRow(
                    pendingBets = pendingBetsCount,
                    valueBetsFound = valueBetsCount
                )
            }

            // Quick Actions
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                QuickActionsGrid(
                    onMarketsClick = onNavigateToMarkets,
                    onAnalysisClick = onNavigateToAnalysis,
                    onBetsClick = onNavigateToBets,
                    onWalletClick = onNavigateToWallet
                )
            }

            // How It Works Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "How It Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                HowItWorksCard()
            }

            // Disclaimer
            item {
                DisclaimerCard()
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun QuickStatsRow(
    pendingBets: Int,
    valueBetsFound: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Pending Bets",
            value = pendingBets.toString(),
            icon = Icons.Outlined.Receipt,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Value Bets",
            value = valueBetsFound.toString(),
            icon = Icons.Filled.Star,
            color = GreenValue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onMarketsClick: () -> Unit,
    onAnalysisClick: () -> Unit,
    onBetsClick: () -> Unit,
    onWalletClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                title = "Markets",
                subtitle = "Browse matches",
                icon = Icons.Filled.TrendingUp,
                color = MaterialTheme.colorScheme.primary,
                onClick = onMarketsClick,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "AI Analysis",
                subtitle = "Custom analysis",
                icon = Icons.Filled.Psychology,
                color = Color(0xFF9C27B0),
                onClick = onAnalysisClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                title = "My Bets",
                subtitle = "View history",
                icon = Icons.Filled.Receipt,
                color = OrangeWarning,
                onClick = onBetsClick,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "Wallet",
                subtitle = "Manage funds",
                icon = Icons.Filled.AccountBalanceWallet,
                color = GreenValue,
                onClick = onWalletClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HowItWorksStep(
                number = "1",
                title = "Browse Markets",
                description = "View upcoming matches with real-time odds"
            )
            HowItWorksStep(
                number = "2",
                title = "AI Analysis",
                description = "Tap the brain icon to get AI-powered insights"
            )
            HowItWorksStep(
                number = "3",
                title = "Find Value",
                description = "Look for 'Value Found!' badges indicating positive edge"
            )
            HowItWorksStep(
                number = "4",
                title = "Place Virtual Bets",
                description = "Practice betting strategies with virtual money"
            )
        }
    }
}

@Composable
private fun HowItWorksStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Virtual Money Only",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = "This app uses virtual currency for educational purposes. No real money is involved. AI analysis is for entertainment only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBF360C)
                )
            }
        }
    }
}
