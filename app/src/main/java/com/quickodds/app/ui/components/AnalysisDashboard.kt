package com.quickodds.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickodds.app.ai.model.*
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ui.theme.OrangeWarning
import kotlinx.coroutines.delay

/**
 * Main Analysis Dashboard composable.
 * Displays Edge Gauge, Momentum Graph, and Kelly Slip.
 */
@Composable
fun AnalysisDashboard(
    analysisResult: BetAnalysisResult,
    walletBalance: Double,
    modifier: Modifier = Modifier
) {
    val aiAnalysis = analysisResult.aiAnalysis

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "AI Analysis Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Ensemble Consensus Badge
        aiAnalysis.ensembleAnalysis?.let { ensemble ->
            EnsembleConsensusBadge(ensemble)
        }

        // Edge Gauge and Confidence Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Edge Gauge
            EdgeGauge(
                edgePercentage = aiAnalysis.edgePercentage ?: 0.0,
                isValueBet = aiAnalysis.isValueBet,
                modifier = Modifier.weight(1f)
            )

            // Confidence Meter
            ConfidenceMeter(
                confidenceScore = aiAnalysis.confidenceScore,
                modifier = Modifier.weight(1f)
            )
        }

        // Momentum Graph
        aiAnalysis.momentumDivergence?.let { momentum ->
            MomentumGraph(
                homeTeam = analysisResult.matchData.homeTeam,
                awayTeam = analysisResult.matchData.awayTeam,
                momentum = momentum
            )
        }

        // Kelly Slip Card
        KellySlipCard(
            aiAnalysis = aiAnalysis,
            walletBalance = walletBalance,
            matchData = analysisResult.matchData
        )

        // Agent Breakdown (if ensemble available)
        aiAnalysis.ensembleAnalysis?.let { ensemble ->
            AgentBreakdownCard(ensemble)
        }
    }
}

/**
 * Ensemble Consensus Badge showing agreement status.
 */
@Composable
fun EnsembleConsensusBadge(ensemble: EnsembleAnalysis) {
    val consensusColor = if (ensemble.consensusReached) {
        GreenValue // Green
    } else {
        OrangeWarning // Orange
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = consensusColor.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (ensemble.consensusReached) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = consensusColor
                )
                Column {
                    Text(
                        text = if (ensemble.consensusReached) "CONSENSUS REACHED" else "NO CONSENSUS",
                        fontWeight = FontWeight.Bold,
                        color = consensusColor
                    )
                    Text(
                        text = "${ensemble.consensusCount}/3 agents agree on value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Agent icons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AgentIcon("Stats", ensemble.statisticalModeler.findsValue)
                AgentIcon("Scout", ensemble.proScout.findsValue)
                AgentIcon("Market", ensemble.marketSharp.findsValue)
            }
        }
    }
}

@Composable
private fun AgentIcon(name: String, findsValue: Boolean) {
    val icon = when (name) {
        "Stats" -> Icons.Default.Analytics
        "Scout" -> Icons.Default.Search
        else -> Icons.Default.TrendingUp
    }
    val color = if (findsValue) GreenValue else Color(0xFFE57373)

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Circular Edge Gauge showing +EV/-EV status.
 */
@Composable
fun EdgeGauge(
    edgePercentage: Double,
    isValueBet: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedEdge by animateFloatAsState(
        targetValue = edgePercentage.toFloat().coerceIn(-20f, 20f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "edge"
    )

    val gaugeColor = if (edgePercentage > 0) {
        GreenValue // Green for +EV
    } else {
        Color(0xFFE57373) // Red for -EV
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EDGE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background arc
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2

                    // Background track
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.3f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(radius * 2, radius * 2)
                    )

                    // Value arc (normalized to -20% to +20% range)
                    val normalizedValue = (animatedEdge + 20f) / 40f // 0 to 1
                    val sweepAngle = normalizedValue * 270f

                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFE57373),
                                Color(0xFFFFB74D),
                                GreenValue
                            )
                        ),
                        startAngle = 135f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(radius * 2, radius * 2)
                    )

                    // Needle indicator
                    val needleAngle = 135f + sweepAngle
                    rotate(needleAngle, pivot = center) {
                        drawLine(
                            color = gaugeColor,
                            start = center,
                            end = Offset(center.x, strokeWidth + 10.dp.toPx()),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${if (edgePercentage > 0) "+" else ""}${String.format("%.1f", edgePercentage)}%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = gaugeColor
                    )
                    Text(
                        text = if (isValueBet) "+EV" else "-EV",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = gaugeColor
                    )
                }
            }
        }
    }
}

/**
 * Confidence Meter visualization.
 */
@Composable
fun ConfidenceMeter(
    confidenceScore: Double,
    modifier: Modifier = Modifier
) {
    val animatedConfidence by animateFloatAsState(
        targetValue = confidenceScore.toFloat(),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "confidence"
    )

    val confidenceLevel = when {
        confidenceScore >= 0.7 -> "HIGH"
        confidenceScore >= 0.5 -> "MEDIUM"
        else -> "LOW"
    }

    val confidenceColor = when {
        confidenceScore >= 0.7 -> GreenValue
        confidenceScore >= 0.5 -> Color(0xFFFFB74D)
        else -> Color(0xFFE57373)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CONFIDENCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedConfidence },
                    modifier = Modifier.fillMaxSize(),
                    color = confidenceColor,
                    strokeWidth = 12.dp,
                    trackColor = Color.Gray.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(confidenceScore * 100).toInt()}%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = confidenceColor
                    )
                    Text(
                        text = confidenceLevel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = confidenceColor
                    )
                }
            }
        }
    }
}

/**
 * Momentum Graph showing L3 vs L10 comparison.
 */
@Composable
fun MomentumGraph(
    homeTeam: String,
    awayTeam: String,
    momentum: MomentumDivergence
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "MOMENTUM TRACKER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Home Team Momentum
            MomentumBar(
                teamName = homeTeam,
                momentum = momentum.homeMomentum ?: 0.0,
                isHome = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Away Team Momentum
            MomentumBar(
                teamName = awayTeam,
                momentum = momentum.awayMomentum ?: 0.0,
                isHome = false
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Momentum advantage indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val advantageIcon = when (momentum.momentumAdvantage) {
                    "HOME" -> Icons.Default.Home
                    "AWAY" -> Icons.Default.FlightTakeoff
                    else -> Icons.Default.Balance
                }
                val advantageText = when (momentum.momentumAdvantage) {
                    "HOME" -> "Home has momentum"
                    "AWAY" -> "Away has momentum"
                    else -> "Momentum neutral"
                }

                Icon(
                    imageVector = advantageIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = advantageText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun MomentumBar(
    teamName: String,
    momentum: Double,
    isHome: Boolean
) {
    val animatedMomentum by animateFloatAsState(
        targetValue = momentum.toFloat(),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "momentum"
    )

    val isHot = momentum > 0.05
    val isCold = momentum < -0.05
    val statusText = when {
        isHot -> "HEATING UP"
        isCold -> "COOLING DOWN"
        else -> "STABLE"
    }
    val statusColor = when {
        isHot -> Color(0xFFFF5722) // Fire orange
        isCold -> Color(0xFF2196F3) // Ice blue
        else -> Color.Gray
    }
    val statusEmoji = when {
        isHot -> "🔥"
        isCold -> "❄️"
        else -> "➡️"
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isHome) Icons.Default.Home else Icons.Default.FlightTakeoff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = teamName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "$statusEmoji $statusText",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Sparkline bar using Row for centering
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = 0.2f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side (negative momentum)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (animatedMomentum < 0) {
                    val barWidth = (kotlin.math.abs(animatedMomentum) * 5).coerceAtMost(1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barWidth)
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .background(Color(0xFF2196F3)) // Ice blue
                    )
                }
            }

            // Center line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )

            // Right side (positive momentum)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (animatedMomentum > 0) {
                    val barWidth = (kotlin.math.abs(animatedMomentum) * 5).coerceAtMost(1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barWidth)
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(Color(0xFFFF5722)) // Fire orange
                    )
                }
            }
        }

        // L3 vs L10 labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "L10",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                text = "${if (momentum > 0) "+" else ""}${String.format("%.1f", momentum * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Text(
                text = "L3",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * Kelly Slip Card with stake recommendation and typewriter animation.
 */
@Composable
fun KellySlipCard(
    aiAnalysis: AIAnalysisResponse,
    walletBalance: Double,
    matchData: MatchData
) {
    val kellyStake = aiAnalysis.kellyStake
    val suggestedStakePercent = kellyStake?.recommendedStakePercent ?: 0.0
    val suggestedStakeAmount = walletBalance * (suggestedStakePercent / 100)

    val confidenceLevel = when {
        aiAnalysis.confidenceScore >= 0.7 -> "HIGH"
        aiAnalysis.confidenceScore >= 0.5 -> "MEDIUM"
        else -> "LOW"
    }

    val expectedReturn = aiAnalysis.edgePercentage ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "KELLY SLIP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Recommendation badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (aiAnalysis.recommendation) {
                        "HOME" -> GreenValue
                        "AWAY" -> Color(0xFF2196F3)
                        "DRAW" -> Color(0xFFFFB74D)
                        else -> Color.Gray
                    }
                ) {
                    Text(
                        text = aiAnalysis.recommendation,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Suggested Stake
                KellyStatItem(
                    label = "Suggested Stake",
                    value = "$${String.format("%.2f", suggestedStakeAmount)}",
                    subValue = "(${String.format("%.1f", suggestedStakePercent)}% of bankroll)",
                    icon = Icons.Default.AttachMoney
                )

                // Confidence Level
                KellyStatItem(
                    label = "Confidence",
                    value = confidenceLevel,
                    subValue = "${(aiAnalysis.confidenceScore * 100).toInt()}%",
                    icon = Icons.Default.Psychology,
                    valueColor = when (confidenceLevel) {
                        "HIGH" -> GreenValue
                        "MEDIUM" -> Color(0xFFFFB74D)
                        else -> Color(0xFFE57373)
                    }
                )

                // Expected Return
                KellyStatItem(
                    label = "Expected Return",
                    value = "${if (expectedReturn > 0) "+" else ""}${String.format("%.1f", expectedReturn)}%",
                    subValue = "edge",
                    icon = Icons.Default.TrendingUp,
                    valueColor = if (expectedReturn > 0) GreenValue else Color(0xFFE57373)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Divider
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(12.dp))

            // AI Rationale with typewriter effect
            Text(
                text = "AI RATIONALE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            TypewriterText(
                text = aiAnalysis.rationale,
                modifier = Modifier.fillMaxWidth()
            )

            // Units indicator
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    val isFilled = index < (aiAnalysis.suggestedStake ?: 0)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary
                                else Color.Gray.copy(alpha = 0.3f)
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${aiAnalysis.suggestedStake ?: 0}/5 units",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KellyStatItem(
    label: String,
    value: String,
    subValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

/**
 * Typewriter animation for AI rationale text.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    typingSpeed: Long = 20L
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { index, _ ->
            delay(typingSpeed)
            displayedText = text.substring(0, index + 1)
        }
    }

    Text(
        text = displayedText + if (displayedText.length < text.length) "▌" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * Agent Breakdown Card showing individual agent opinions.
 */
@Composable
fun AgentBreakdownCard(ensemble: EnsembleAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "AGENT BREAKDOWN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Statistical Modeler
            AgentRow(
                icon = Icons.Default.Analytics,
                name = "Statistical Modeler",
                emoji = "📊",
                perspective = ensemble.statisticalModeler
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pro Scout
            AgentRow(
                icon = Icons.Default.Search,
                name = "Pro Scout",
                emoji = "🔍",
                perspective = ensemble.proScout
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Market Sharp
            AgentRow(
                icon = Icons.Default.ShowChart,
                name = "Market Sharp",
                emoji = "💹",
                perspective = ensemble.marketSharp
            )

            // Dissenting opinion
            ensemble.dissentingOpinion?.let { opinion ->
                if (opinion.isNotBlank() && opinion != "null") {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dissent: $opinion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    emoji: String,
    perspective: AgentPerspective
) {
    val valueColor = if (perspective.findsValue) GreenValue else Color(0xFFE57373)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (perspective.findsValue) GreenValue.copy(alpha = 0.1f)
                else Color(0xFFE57373).copy(alpha = 0.1f)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 20.sp)

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = valueColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = perspective.recommendation,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = valueColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = perspective.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (perspective.estimatedEdge > 0) "+" else ""}${String.format("%.1f", perspective.estimatedEdge)}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = if (perspective.findsValue) "VALUE" else "NO VALUE",
                style = MaterialTheme.typography.labelSmall,
                color = valueColor
            )
        }
    }
}
