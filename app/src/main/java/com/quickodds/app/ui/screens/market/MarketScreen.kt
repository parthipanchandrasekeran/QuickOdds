package com.quickodds.app.ui.screens.market

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickodds.app.ui.components.*
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ui.theme.OrangeWarning

/**
 * Main Market Screen displaying live matches and AI analysis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    viewModel: MarketViewModel,
    onMatchClick: (String) -> Unit,
    onCheckForUpdate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Quick Odds",
                            fontWeight = FontWeight.Bold
                        )
                        // Live data indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(8.dp),
                                tint = if (uiState.isUsingLiveData) GreenValue else OrangeWarning
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.isUsingLiveData) "Live Odds" else "Demo Data",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Compact wallet display
                    uiState.wallet?.let { wallet ->
                        CompactWalletHeader(
                            balance = wallet.balance,
                            currency = wallet.currency,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    // Analyze All Today button
                    IconButton(
                        onClick = { viewModel.analyzeAllToday() },
                        enabled = !uiState.isAnalyzingAll && !uiState.isLoading
                    ) {
                        if (uiState.isAnalyzingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Analyze all today's matches"
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.loadMatches() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    // Overflow menu
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Check for update") },
                                onClick = {
                                    showMenu = false
                                    onCheckForUpdate?.invoke()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null
                                    )
                                }
                            )
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
            // Wallet Header
            uiState.wallet?.let { wallet ->
                WalletHeader(
                    wallet = wallet,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Sport Filter Chips
            SportFilterRow(
                sports = uiState.availableSports,
                selectedSport = uiState.selectedSport,
                onSportSelected = { viewModel.selectSport(it) }
            )

            // Scan All Header
            ScanAllHeader(
                matchCount = uiState.matches.size,
                isScanning = uiState.isScanningAll,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                lastScanTime = uiState.lastScanCompleted,
                valueBetsFound = uiState.valueBetsFound,
                onScanAll = { viewModel.scanAllMatches() },
                formatTime = { viewModel.formatLastUpdated(it) }
            )

            // Content
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.matches.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    MatchesList(
                        matches = uiState.matches,
                        matchStates = uiState.matchStates,
                        isLiveData = uiState.isUsingLiveData,
                        onAnalyzeClick = { viewModel.analyzeMatch(it) },
                        onMatchClick = onMatchClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportFilterRow(
    sports: List<SportOption>,
    selectedSport: String,
    onSportSelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(sports) { sport ->
            FilterChip(
                selected = sport.key == selectedSport,
                onClick = { onSportSelected(sport.key) },
                label = { Text(sport.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun ScanAllHeader(
    matchCount: Int,
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
                        text = "$matchCount matches available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onScanAll,
                    enabled = !isScanning && matchCount > 0,
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

@Composable
private fun LoadingContent() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(4) {
            MatchCardShimmer()
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No matches available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Check back later for upcoming events",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MatchesList(
    matches: List<com.quickodds.app.data.remote.dto.OddsEvent>,
    matchStates: Map<String, MatchCardState>,
    isLiveData: Boolean,
    onAnalyzeClick: (String) -> Unit,
    onMatchClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live data badge
        if (isLiveData) {
            item {
                Row(
                    modifier = Modifier
                        .background(
                            color = GreenValue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = GreenValue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live odds from The-Odds-API",
                        style = MaterialTheme.typography.labelMedium,
                        color = GreenValue
                    )
                }
            }
        }

        // Value bets section (if any)
        val valueBets = matches.filter { match ->
            matchStates[match.id]?.analysisResult?.isValueBet == true
        }

        if (valueBets.isNotEmpty()) {
            item {
                Text(
                    text = "Value Bets Found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GreenValue,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(valueBets, key = { "value_${it.id}" }) { match ->
                val bookmaker = match.bookmakers.firstOrNull()
                val h2h = bookmaker?.markets?.find { it.key == "h2h" }

                MatchCard(
                    homeTeam = match.homeTeam,
                    awayTeam = match.awayTeam,
                    league = match.sportTitle,
                    commenceTime = match.commenceTime,
                    homeOdds = h2h?.outcomes?.find { it.name == match.homeTeam }?.price ?: 0.0,
                    drawOdds = h2h?.outcomes?.find { it.name.lowercase() == "draw" }?.price,
                    awayOdds = h2h?.outcomes?.find { it.name == match.awayTeam }?.price ?: 0.0,
                    state = matchStates[match.id] ?: MatchCardState(),
                    onAnalyzeClick = { onAnalyzeClick(match.id) },
                    onCardClick = { onMatchClick(match.id) }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }
        }

        // All matches section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upcoming Matches",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${matches.size} matches",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(
            items = matches.filter { matchStates[it.id]?.analysisResult?.isValueBet != true },
            key = { it.id }
        ) { match ->
            val bookmaker = match.bookmakers.firstOrNull()
            val h2h = bookmaker?.markets?.find { it.key == "h2h" }

            MatchCard(
                homeTeam = match.homeTeam,
                awayTeam = match.awayTeam,
                league = match.sportTitle,
                commenceTime = match.commenceTime,
                homeOdds = h2h?.outcomes?.find { it.name == match.homeTeam }?.price ?: 0.0,
                drawOdds = h2h?.outcomes?.find { it.name.lowercase() == "draw" }?.price,
                awayOdds = h2h?.outcomes?.find { it.name == match.awayTeam }?.price ?: 0.0,
                state = matchStates[match.id] ?: MatchCardState(),
                onAnalyzeClick = { onAnalyzeClick(match.id) },
                onCardClick = { onMatchClick(match.id) }
            )
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
