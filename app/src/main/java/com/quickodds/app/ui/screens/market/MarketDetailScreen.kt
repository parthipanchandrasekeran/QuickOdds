package com.quickodds.app.ui.screens.market

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quickodds.app.ai.model.AIAnalysisResponse
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ai.model.BetAnalysisResult
import com.quickodds.app.ai.model.MatchData
import com.quickodds.app.ai.model.ImpliedProbabilities
import com.quickodds.app.data.local.entity.BetStatus
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.data.remote.dto.OddsEvent
import com.quickodds.app.ui.components.AnalysisDashboard
import com.quickodds.app.ui.components.AnalysisShimmer
import com.quickodds.app.ui.components.pulsingScale

/**
 * Market Detail Screen for viewing match details and placing bets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketDetailScreen(
    match: OddsEvent?,
    wallet: UserWallet?,
    analysisResult: AIAnalysisResponse?,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
    onPlaceBet: (selection: String, stake: Double) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Bet confirmation state
    showBetConfirmation: Boolean = false,
    betConfirmationData: BetConfirmationData? = null,
    isPlacingBet: Boolean = false,
    onConfirmBet: () -> Unit = {},
    onDismissConfirmation: () -> Unit = {},
    // Error handling
    error: String? = null,
    onClearError: () -> Unit = {}
) {
    var selectedOutcome by remember { mutableStateOf<String?>(null) }
    var stakeAmount by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            onClearError()
        }
    }

    // Bet Confirmation Bottom Sheet
    if (showBetConfirmation && betConfirmationData != null) {
        BetConfirmationBottomSheet(
            data = betConfirmationData,
            isLoading = isPlacingBet,
            onConfirm = onConfirmBet,
            onDismiss = onDismissConfirmation,
            sheetState = sheetState
        )
    }

    if (match == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val bookmaker = match.bookmakers.firstOrNull()
    val h2hMarket = bookmaker?.markets?.find { it.key == "h2h" }
    val outcomes = h2hMarket?.outcomes ?: emptyList()

    val homeOdds = outcomes.find { it.name == match.homeTeam }?.price ?: 0.0
    val drawOdds = outcomes.find { it.name.lowercase() == "draw" }?.price
    val awayOdds = outcomes.find { it.name == match.awayTeam }?.price ?: 0.0

    val selectedOdds = when (selectedOutcome) {
        match.homeTeam -> homeOdds
        "Draw" -> drawOdds ?: 0.0
        match.awayTeam -> awayOdds
        else -> 0.0
    }

    val stake = stakeAmount.toDoubleOrNull() ?: 0.0
    val potentialWin = stake * selectedOdds

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Match Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
        ) {
            // Match Header
            MatchHeader(
                homeTeam = match.homeTeam,
                awayTeam = match.awayTeam,
                league = match.sportTitle,
                commenceTime = match.commenceTime
            )

            Spacer(modifier = Modifier.height(20.dp))

            // AI Analysis Section with Full Dashboard
            AIAnalysisSection(
                analysisResult = analysisResult,
                isAnalyzing = isAnalyzing,
                onAnalyze = onAnalyze,
                homeTeam = match.homeTeam,
                awayTeam = match.awayTeam,
                homeOdds = homeOdds,
                drawOdds = drawOdds,
                awayOdds = awayOdds,
                walletBalance = wallet?.balance ?: 0.0,
                modifier = Modifier.padding(horizontal = 0.dp),
                showFullDashboard = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Betting Section
            Text(
                text = "Place Your Bet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Outcome Selection
            OutcomeSelectionRow(
                homeTeam = match.homeTeam,
                awayTeam = match.awayTeam,
                homeOdds = homeOdds,
                drawOdds = drawOdds,
                awayOdds = awayOdds,
                selectedOutcome = selectedOutcome,
                recommendedOutcome = analysisResult?.recommendation,
                onSelect = { selectedOutcome = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stake Input
            StakeInputSection(
                stakeAmount = stakeAmount,
                onStakeChange = { stakeAmount = it },
                balance = wallet?.balance ?: 0.0,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bet Summary
            AnimatedVisibility(
                visible = selectedOutcome != null && stake > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                BetSummaryCard(
                    selection = selectedOutcome ?: "",
                    odds = selectedOdds,
                    stake = stake,
                    potentialWin = potentialWin,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val buttonEnabled = selectedOutcome != null && stake > 0 && stake <= (wallet?.balance ?: 0.0)

            // Place Bet Button
            Button(
                onClick = {
                    if (selectedOutcome != null && stake > 0) {
                        onPlaceBet(selectedOutcome!!, stake)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                enabled = buttonEnabled,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Place Bet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MatchHeader(
    homeTeam: String,
    awayTeam: String,
    league: String,
    commenceTime: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // League badge
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = league,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Teams
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home team
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = homeTeam.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = homeTeam,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // VS
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Away team
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = awayTeam.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = awayTeam,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Away",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time
            Text(
                text = formatCommenceTime(commenceTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AIAnalysisSection(
    analysisResult: AIAnalysisResponse?,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
    homeTeam: String,
    awayTeam: String,
    homeOdds: Double = 0.0,
    drawOdds: Double? = null,
    awayOdds: Double = 0.0,
    walletBalance: Double = 0.0,
    modifier: Modifier = Modifier,
    showFullDashboard: Boolean = true
) {
    // Create a BetAnalysisResult for the dashboard if we have analysis
    val betAnalysisResult = analysisResult?.let {
        BetAnalysisResult(
            matchData = MatchData(
                eventId = "",
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeOdds = homeOdds,
                drawOdds = drawOdds,
                awayOdds = awayOdds,
                league = "",
                commenceTime = ""
            ),
            impliedProbabilities = ImpliedProbabilities(
                home = if (homeOdds > 0) 1.0 / homeOdds else 0.0,
                draw = drawOdds?.let { 1.0 / it },
                away = if (awayOdds > 0) 1.0 / awayOdds else 0.0,
                bookmakerMargin = 0.0
            ),
            aiAnalysis = it
        )
    }

    when {
        isAnalyzing -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    AnalysisShimmer()
                }
            }
        }
        betAnalysisResult != null && showFullDashboard -> {
            // Show the full Analysis Dashboard
            AnalysisDashboard(
                analysisResult = betAnalysisResult,
                walletBalance = walletBalance,
                modifier = modifier
            )
        }
        analysisResult != null -> {
            // Fallback to simple view
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Analysis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (analysisResult.isValueBet) {
                            Surface(
                                color = GreenValue,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "VALUE!",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    AnalysisResultContent(
                        result = analysisResult,
                        homeTeam = homeTeam,
                        awayTeam = awayTeam
                    )
                }
            }
        }
        else -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    AnalyzePrompt(onAnalyze = onAnalyze)
                }
            }
        }
    }
}

@Composable
private fun AnalyzePrompt(onAnalyze: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Get AI-powered analysis",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onAnalyze) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Analyze Match")
        }
    }
}

@Composable
private fun AnalysisResultContent(
    result: AIAnalysisResponse,
    homeTeam: String,
    awayTeam: String
) {
    Column {
        // Recommendation
        val recommendationText = when (result.recommendation) {
            "HOME" -> homeTeam
            "AWAY" -> awayTeam
            "DRAW" -> "Draw"
            else -> "No Bet"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Recommendation",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = recommendationText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.isValueBet) GreenValue
                           else MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Confidence",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(result.confidenceScore * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rationale
        Text(
            text = result.rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (result.suggestedStake != null && result.suggestedStake > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Suggested: ${result.suggestedStake} units",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun OutcomeSelectionRow(
    homeTeam: String,
    awayTeam: String,
    homeOdds: Double,
    drawOdds: Double?,
    awayOdds: Double,
    selectedOutcome: String?,
    recommendedOutcome: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutcomeCard(
            label = homeTeam,
            odds = homeOdds,
            isSelected = selectedOutcome == homeTeam,
            isRecommended = recommendedOutcome == "HOME",
            onClick = { onSelect(homeTeam) },
            modifier = Modifier.weight(1f)
        )

        if (drawOdds != null) {
            OutcomeCard(
                label = "Draw",
                odds = drawOdds,
                isSelected = selectedOutcome == "Draw",
                isRecommended = recommendedOutcome == "DRAW",
                onClick = { onSelect("Draw") },
                modifier = Modifier.weight(1f)
            )
        }

        OutcomeCard(
            label = awayTeam,
            odds = awayOdds,
            isSelected = selectedOutcome == awayTeam,
            isRecommended = recommendedOutcome == "AWAY",
            onClick = { onSelect(awayTeam) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OutcomeCard(
    label: String,
    odds: Double,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isRecommended -> GreenValue
        else -> Color.Transparent
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecommended) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Recommended",
                    tint = GreenValue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = String.format("%.2f", odds),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StakeInputSection(
    stakeAmount: String,
    onStakeChange: (String) -> Unit,
    balance: Double,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = stakeAmount,
            onValueChange = { value ->
                if (value.isEmpty() || value.toDoubleOrNull() != null) {
                    onStakeChange(value)
                }
            },
            label = { Text("Stake Amount") },
            placeholder = { Text("Enter amount") },
            leadingIcon = { Text("$", style = MaterialTheme.typography.titleMedium) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Balance: $${String.format("%.2f", balance)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quick stake buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 25, 50, 100).forEach { amount ->
                    SuggestionChip(
                        onClick = { onStakeChange(amount.toString()) },
                        label = { Text("$$amount") }
                    )
                }
            }
        }
    }
}

@Composable
private fun BetSummaryCard(
    selection: String,
    odds: Double,
    stake: Double,
    potentialWin: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Bet Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Selection:", style = MaterialTheme.typography.bodyMedium)
                Text(selection, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Odds:", style = MaterialTheme.typography.bodyMedium)
                Text(String.format("%.2f", odds), fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Stake:", style = MaterialTheme.typography.bodyMedium)
                Text("$${String.format("%.2f", stake)}", fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Potential Win:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$${String.format("%.2f", potentialWin)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GreenValue
                )
            }
        }
    }
}

private fun formatCommenceTime(isoTime: String): String {
    return try {
        val instant = java.time.Instant.parse(isoTime)
        val formatter = java.time.format.DateTimeFormatter
            .ofPattern("EEEE, MMMM d 'at' HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoTime
    }
}

/**
 * Bet Confirmation Bottom Sheet.
 * Shows bet details and asks for confirmation before placing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BetConfirmationBottomSheet(
    data: BetConfirmationData,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Confirm Your Bet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Match info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = data.matchName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Your Pick: ${data.selection}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bet details
            BetDetailRow(label = "Odds", value = String.format("%.2f", data.odds))
            BetDetailRow(label = "Stake", value = "$${String.format("%.2f", data.stake)}")

            // Recommended stake badge (if available)
            if (data.recommendedStake != null && data.recommendedStake > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "AI Recommended: ${data.recommendedStake} units",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Potential return - highlighted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Potential Return",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$${String.format("%.2f", data.potentialReturn)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = GreenValue
                )
            }

            // Profit calculation
            val profit = data.potentialReturn - data.stake
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "(Profit: +$${String.format("%.2f", profit)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = GreenValue
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenValue
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm Bet", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BetDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
