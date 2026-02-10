package com.quickodds.app.ui.screens.market

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.quickodds.app.ui.screens.paywall.PaywallDialog
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ui.theme.OrangeWarning
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickodds.app.domain.model.Market
import com.quickodds.app.domain.model.Sport
import com.quickodds.app.ui.components.MarketCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    onNavigateToMarket: (String) -> Unit,
    viewModel: MarketsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Paywall dialog
    if (uiState.showPaywall) {
        PaywallDialog(
            billingRepository = viewModel.billingRepository,
            onDismiss = { viewModel.dismissPaywall() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Markets",
                            fontWeight = FontWeight.Bold
                        )
                        // Last Updated timestamp
                        Text(
                            text = "Updated ${viewModel.formatLastUpdated(uiState.lastUpdated)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Refresh odds button with label
                    TextButton(
                        onClick = { viewModel.refreshMarkets() },
                        enabled = !uiState.isRefreshing
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Updating...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh odds",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sports Filter
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.sports) { sport ->
                    SportChip(
                        sport = sport,
                        isSelected = sport == uiState.selectedSport,
                        onClick = { viewModel.selectSport(sport) }
                    )
                }
            }

            // Scan All Header with Analysis Status
            ScanAllHeader(
                marketCount = uiState.markets.size,
                isScanning = uiState.isScanningAll,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                lastScanTime = uiState.lastScanCompleted,
                valueBetsFound = uiState.valueBetsFound,
                onScanAll = { viewModel.scanAllMatches() },
                formatTime = { viewModel.formatLastUpdated(it) }
            )

            // Usage limits info (free tier only)
            if (uiState.remainingScans < Int.MAX_VALUE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "Scans: ${uiState.remainingScans} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Analyzes: ${uiState.remainingAnalyzes} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.markets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No matches today",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "There are no scheduled matches for today in this league",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.markets) { market ->
                        val analysisState = uiState.bulkAnalysisResults[market.id]
                        MarketCardWithAnalysis(
                            market = market,
                            analysisState = analysisState,
                            onAnalyze = { viewModel.analyzeMatch(market) },
                            onClick = { onNavigateToMarket(market.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scan All Header with bulk analysis controls.
 */
@Composable
private fun ScanAllHeader(
    marketCount: Int,
    isScanning: Boolean,
    scanProgress: Int,
    scanTotal: Int,
    lastScanTime: Long?,
    valueBetsFound: Int,
    onScanAll: () -> Unit,
    formatTime: (Long?) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Column {
                    Text(
                        text = "AI Slate Analysis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$marketCount matches available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onScanAll,
                    enabled = !isScanning && marketCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan All")
                    }
                }
            }

            // Progress bar when scanning
            AnimatedVisibility(visible = isScanning) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (scanTotal > 0) scanProgress.toFloat() / scanTotal else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Analyzing $scanProgress of $scanTotal matches...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Last scan results
            AnimatedVisibility(visible = lastScanTime != null && !isScanning) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Last scan: ${formatTime(lastScanTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (valueBetsFound > 0) {
                            Surface(
                                color = GreenValue,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$valueBetsFound Value Bets",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Market card with individual analyze button and analysis badge.
 */
@Composable
private fun MarketCardWithAnalysis(
    market: Market,
    analysisState: MarketAnalysisState?,
    onAnalyze: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Match info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${market.homeTeam} vs ${market.awayTeam}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = market.league,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Analysis badge or button
                when {
                    analysisState?.isAnalyzing == true -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    analysisState?.result != null -> {
                        // Show analysis result badge
                        Column(horizontalAlignment = Alignment.End) {
                            AnalysisBadge(
                                isValueBet = analysisState.result.aiAnalysis.isValueBet,
                                recommendation = analysisState.result.aiAnalysis.recommendation,
                                confidence = analysisState.result.aiAnalysis.confidenceScore,
                                hasSharpAlert = analysisState.result.hasSharpAlert
                            )
                            // Show sharp alert indicator if triggered
                            if (analysisState.result.hasSharpAlert) {
                                Spacer(modifier = Modifier.height(4.dp))
                                SharpAlertChip(
                                    alertType = analysisState.result.sharpAlertType,
                                    sharpSide = analysisState.result.sharpMoneySide
                                )
                            }
                        }
                    }
                    else -> {
                        // Show analyze button
                        OutlinedButton(
                            onClick = onAnalyze,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Analyze",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Odds row with sharp money indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val sharpSide = analysisState?.result?.sharpMoneySide
                OddsChip(
                    label = "Home",
                    odds = market.odds.home,
                    isRecommended = analysisState?.result?.aiAnalysis?.recommendation == "HOME",
                    isSharpSide = sharpSide == "HOME"
                )
                if (market.odds.draw > 0) {
                    OddsChip(
                        label = "Draw",
                        odds = market.odds.draw,
                        isRecommended = analysisState?.result?.aiAnalysis?.recommendation == "DRAW",
                        isSharpSide = false
                    )
                }
                OddsChip(
                    label = "Away",
                    odds = market.odds.away,
                    isRecommended = analysisState?.result?.aiAnalysis?.recommendation == "AWAY",
                    isSharpSide = sharpSide == "AWAY"
                )
            }

            // Show brief rationale if analyzed
            analysisState?.result?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.aiAnalysis.rationale.take(120) + if (result.aiAnalysis.rationale.length > 120) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Sharp Alert chip indicator.
 */
@Composable
private fun SharpAlertChip(
    alertType: String?,
    sharpSide: String?
) {
    Surface(
        color = OrangeWarning,  // Orange for sharp alerts
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = when (alertType) {
                    "RLM" -> "RLM"
                    "SHARP_MONEY" -> "Sharp"
                    "BOTH" -> "Sharp+"
                    else -> "Alert"
                } + (sharpSide?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AnalysisBadge(
    isValueBet: Boolean,
    recommendation: String,
    confidence: Double,
    hasSharpAlert: Boolean = false
) {
    // Use different colors based on value bet and sharp alert status
    val backgroundColor = when {
        isValueBet && hasSharpAlert -> Color(0xFF2E7D32)  // Dark green for value + sharp
        isValueBet -> GreenValue                   // Green for value bet
        hasSharpAlert -> OrangeWarning                // Orange for sharp alert only
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isValueBet || hasSharpAlert) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isValueBet) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = if (recommendation == "NO_BET") "Skip" else recommendation,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun OddsChip(
    label: String,
    odds: Double,
    isRecommended: Boolean,
    isSharpSide: Boolean = false
) {
    // Determine colors based on recommendation and sharp status
    val backgroundColor = when {
        isRecommended -> MaterialTheme.colorScheme.primaryContainer
        isSharpSide -> Color(0xFFFFF3E0)  // Light orange for sharp side
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRecommended) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Sharp money indicator
                if (isSharpSide && !isRecommended) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Sharp Money",
                        modifier = Modifier.size(10.dp),
                        tint = OrangeWarning
                    )
                }
            }
            Text(
                text = String.format("%.2f", odds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isRecommended) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            when {
                isRecommended -> {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Recommended",
                        modifier = Modifier.size(12.dp),
                        tint = GreenValue
                    )
                }
                isSharpSide -> {
                    Text(
                        text = "Sharp",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8,
                        color = OrangeWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportChip(
    sport: Sport,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(text = "${sport.name} (${sport.marketsCount})")
        }
    )
}
