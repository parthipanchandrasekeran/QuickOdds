package com.quickodds.app.ui.screens.accuracy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickodds.app.data.local.dao.SportAccuracy
import com.quickodds.app.data.local.dao.TierAccuracy
import com.quickodds.app.data.local.entity.PredictionRecord
import com.quickodds.app.ui.theme.BlueInfo
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ui.theme.OrangeWarning
import com.quickodds.app.ui.theme.RedLoss
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuracyScreen(
    viewModel: AccuracyViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prediction Accuracy") }
            )
        },
        modifier = modifier
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.settledPredictions == 0) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No settled predictions yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Run AI analysis on a match, place a bet,\nand wait for it to settle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    if (uiState.totalPredictions > 0) {
                        Text(
                            "${uiState.totalPredictions} prediction(s) awaiting settlement",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrangeWarning
                        )
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Accuracy Card
            item {
                OverallAccuracyCard(
                    accuracy = uiState.overallAccuracy,
                    correct = uiState.correctPredictions,
                    total = uiState.settledPredictions,
                    pending = uiState.totalPredictions - uiState.settledPredictions
                )
            }

            // Per-Agent Section
            if (uiState.agentAccuracies.isNotEmpty()) {
                item {
                    Text(
                        "Agent Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.agentAccuracies.forEach { agent ->
                            AgentAccuracyCard(
                                agent = agent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Confidence Calibration
            if (uiState.confidenceTierAccuracies.isNotEmpty()) {
                item {
                    Text(
                        "Confidence Calibration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    ConfidenceCalibrationCard(tiers = uiState.confidenceTierAccuracies)
                }
            }

            // Per-Sport Accuracy
            if (uiState.sportAccuracies.isNotEmpty()) {
                item {
                    Text(
                        "Accuracy by Sport",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    SportAccuracyCard(sports = uiState.sportAccuracies)
                }
            }

            // Recent Predictions
            if (uiState.recentPredictions.isNotEmpty()) {
                item {
                    Text(
                        "Recent Predictions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.recentPredictions) { prediction ->
                    RecentPredictionRow(prediction = prediction)
                }
            }
        }
    }
}

@Composable
private fun OverallAccuracyCard(
    accuracy: Double,
    correct: Int,
    total: Int,
    pending: Int
) {
    val accuracyColor = when {
        accuracy >= 0.60 -> GreenValue
        accuracy >= 0.45 -> OrangeWarning
        else -> RedLoss
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Overall Accuracy",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${(accuracy * 100).toInt()}%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = accuracyColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$correct correct out of $total settled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pending > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$pending pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrangeWarning
                )
            }
        }
    }
}

@Composable
private fun AgentAccuracyCard(
    agent: AgentAccuracy,
    modifier: Modifier = Modifier
) {
    val icon = when (agent.agentName) {
        "Stat Modeler" -> Icons.Default.BarChart
        "Pro Scout" -> Icons.Default.Search
        "Market Sharp" -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Default.SmartToy
    }

    val accuracyColor = when {
        agent.total == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        agent.accuracy >= 0.60 -> GreenValue
        agent.accuracy >= 0.45 -> OrangeWarning
        else -> RedLoss
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = BlueInfo
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                agent.agentName,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (agent.total > 0) {
                Text(
                    "${(agent.accuracy * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accuracyColor
                )
                Text(
                    "${agent.correct}/${agent.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "--",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ConfidenceCalibrationCard(tiers: List<TierAccuracy>) {
    // Sort tiers in logical order
    val tierOrder = listOf("LOW", "MEDIUM", "HIGH", "VERY_HIGH")
    val sorted = tiers.sortedBy { tierOrder.indexOf(it.tier) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Tier",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Predicted",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Actual",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "N",
                    modifier = Modifier.weight(0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            sorted.forEach { tier ->
                val tierLabel = when (tier.tier) {
                    "VERY_HIGH" -> "Very High"
                    "HIGH" -> "High"
                    "MEDIUM" -> "Medium"
                    "LOW" -> "Low"
                    else -> tier.tier
                }

                val calibrationDiff = tier.accuracy - tier.avgConfidence
                val calibrationColor = when {
                    kotlin.math.abs(calibrationDiff) <= 0.10 -> GreenValue
                    kotlin.math.abs(calibrationDiff) <= 0.20 -> OrangeWarning
                    else -> RedLoss
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tierLabel,
                        modifier = Modifier.weight(1.2f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${(tier.avgConfidence * 100).toInt()}%",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${(tier.accuracy * 100).toInt()}%",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = calibrationColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${tier.total}",
                        modifier = Modifier.weight(0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SportAccuracyCard(sports: List<SportAccuracy>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            sports.forEach { sport ->
                val displayName = sport.sportKey
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }

                val accuracyColor = when {
                    sport.accuracy >= 0.60 -> GreenValue
                    sport.accuracy >= 0.45 -> OrangeWarning
                    else -> RedLoss
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${(sport.accuracy * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = accuracyColor
                    )
                    Text(
                        " (${sport.correct}/${sport.total})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentPredictionRow(prediction: PredictionRecord) {
    val isCorrect = prediction.actualOutcome == "WON"
    val outcomeColor = if (isCorrect) GreenValue else RedLoss
    val outcomeIcon = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel

    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val settledDate = prediction.settledAt?.let { dateFormat.format(Date(it)) } ?: ""

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = outcomeIcon,
                contentDescription = prediction.actualOutcome,
                tint = outcomeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    prediction.matchName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "Picked: ${prediction.selectedTeam} | Conf: ${(prediction.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    prediction.actualOutcome ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = outcomeColor
                )
                Text(
                    settledDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
