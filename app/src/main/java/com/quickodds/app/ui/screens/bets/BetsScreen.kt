package com.quickodds.app.ui.screens.bets

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickodds.app.data.local.entity.BetStatus
import com.quickodds.app.data.local.entity.VirtualBet
import com.quickodds.app.ui.viewmodel.BetViewModel
import com.quickodds.app.ui.viewmodel.SettlementResult
import java.text.SimpleDateFormat
import java.util.*

/**
 * Filter options for the bets list.
 */
enum class BetFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    SETTLED("Settled"),
    WON("Won"),
    LOST("Lost")
}

/**
 * Bets Screen - Shows bet history, statistics, and settlement controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetsScreen(
    viewModel: BetViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf(BetFilter.ALL) }

    // Filter bets based on selected filter
    val filteredBets = when (selectedFilter) {
        BetFilter.ALL -> uiState.allBets
        BetFilter.PENDING -> uiState.pendingBets
        BetFilter.SETTLED -> uiState.allBets.filter { it.status != BetStatus.PENDING }
        BetFilter.WON -> uiState.allBets.filter { it.status == BetStatus.WON }
        BetFilter.LOST -> uiState.allBets.filter { it.status == BetStatus.LOST }
    }

    // Handle settlement results
    LaunchedEffect(uiState.lastSettlementResult) {
        when (val result = uiState.lastSettlementResult) {
            is SettlementResult.Won -> {
                snackbarHostState.showSnackbar(
                    "Bet Won! +$${String.format("%.2f", result.winnings)}"
                )
                viewModel.clearLastSettlementResult()
            }
            is SettlementResult.Lost -> {
                snackbarHostState.showSnackbar("Bet Lost")
                viewModel.clearLastSettlementResult()
            }
            is SettlementResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearLastSettlementResult()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Bets",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Demo: Settle all pending bets randomly
                    if (uiState.pendingBets.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.mockSettleAllPending() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Settle All")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BetFilter.entries) { filter ->
                    FilterChip(
                        selected = filter == selectedFilter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }

            if (uiState.allBets.isEmpty()) {
                EmptyBetsContent(
                    modifier = Modifier.fillMaxSize()
                )
            } else if (filteredBets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ${selectedFilter.label.lowercase()} bets",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Statistics Card (only show for ALL filter)
                    if (selectedFilter == BetFilter.ALL) {
                        item {
                            BetStatsCard(viewModel = viewModel)
                        }
                    }

                    // Bets list
                    items(
                        items = filteredBets,
                        key = { it.id }
                    ) { bet ->
                        BetCard(
                            bet = bet,
                            onSettleClick = { viewModel.mockSettleBet(bet.id) }
                        )
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBetsContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Receipt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No bets yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Place your first virtual bet from the Markets tab",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun BetStatsCard(viewModel: BetViewModel) {
    val stats = viewModel.getBetStats()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Betting Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Total",
                    value = stats.totalBets.toString(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatItem(
                    label = "Won",
                    value = stats.wonBets.toString(),
                    color = Color(0xFF4CAF50)
                )
                StatItem(
                    label = "Lost",
                    value = stats.lostBets.toString(),
                    color = Color(0xFFF44336)
                )
                StatItem(
                    label = "Win Rate",
                    value = "${(stats.winRate * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Net Profit/Loss",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${if (stats.netProfit >= 0) "+" else ""}$${String.format("%.2f", stats.netProfit)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (stats.netProfit >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Staked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$${String.format("%.2f", stats.totalStaked)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun BetCard(
    bet: VirtualBet,
    onSettleClick: (() -> Unit)? = null
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    val statusColor = when (bet.status) {
        BetStatus.PENDING -> Color(0xFFFF9800)
        BetStatus.WON -> Color(0xFF4CAF50)
        BetStatus.LOST -> Color(0xFFF44336)
    }

    val statusIcon = when (bet.status) {
        BetStatus.PENDING -> Icons.Outlined.Schedule
        BetStatus.WON -> Icons.Filled.CheckCircle
        BetStatus.LOST -> Icons.Filled.Cancel
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bet.matchName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = bet.status.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Selection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = bet.selectedTeam,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Odds",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = String.format("%.2f", bet.odds),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Stake",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$${String.format("%.2f", bet.stakeAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (bet.status == BetStatus.WON) "Won" else "Potential Win",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$${String.format("%.2f", bet.stakeAmount * bet.odds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (bet.status == BetStatus.WON) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(bet.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                // Demo settle button for pending bets
                if (bet.status == BetStatus.PENDING && onSettleClick != null) {
                    TextButton(
                        onClick = onSettleClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Settle",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
