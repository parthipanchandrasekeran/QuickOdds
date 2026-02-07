package com.quickodds.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.quickodds.app.ai.model.AIAnalysisResponse
import com.quickodds.app.data.remote.dto.OddsEvent
import com.quickodds.app.ui.theme.GreenValue

/**
 * State for a match card with analysis.
 */
data class MatchCardState(
    val isAnalyzing: Boolean = false,
    val isAnalyzed: Boolean = false,
    val analysisResult: AIAnalysisResponse? = null,
    val error: String? = null
)

/**
 * Match card component displaying match info with AI analysis capability.
 */
@Composable
fun MatchCard(
    homeTeam: String,
    awayTeam: String,
    league: String,
    commenceTime: String,
    homeOdds: Double,
    drawOdds: Double?,
    awayOdds: Double,
    state: MatchCardState,
    onAnalyzeClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: League + Time + Brain Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = league,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatCommenceTime(commenceTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // AI Brain Button with states
                AIBrainButton(
                    state = state,
                    onClick = onAnalyzeClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Team Names
            Text(
                text = homeTeam,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "vs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = awayTeam,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Odds Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OddsChip(
                    label = "1",
                    odds = homeOdds,
                    isHighlighted = state.analysisResult?.recommendation == "HOME",
                    modifier = Modifier.weight(1f)
                )
                if (drawOdds != null) {
                    OddsChip(
                        label = "X",
                        odds = drawOdds,
                        isHighlighted = state.analysisResult?.recommendation == "DRAW",
                        modifier = Modifier.weight(1f)
                    )
                }
                OddsChip(
                    label = "2",
                    odds = awayOdds,
                    isHighlighted = state.analysisResult?.recommendation == "AWAY",
                    modifier = Modifier.weight(1f)
                )
            }

            // Value Bet Badge (animated appearance)
            AnimatedVisibility(
                visible = state.analysisResult?.isValueBet == true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                ValueBetBanner(analysisResult = state.analysisResult!!)
            }

            // Error message
            AnimatedVisibility(visible = state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * AI Brain button with loading and analyzed states.
 */
@Composable
private fun AIBrainButton(
    state: MatchCardState,
    onClick: () -> Unit
) {
    val scale = if (state.isAnalyzing) pulsingScale() else 1f

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                when {
                    state.analysisResult?.isValueBet == true -> GreenValue
                    state.isAnalyzed -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(enabled = !state.isAnalyzing, onClick = onClick)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isAnalyzing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            state.analysisResult?.isValueBet == true -> {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = "Value Found",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            state.isAnalyzed -> {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = "Analyzed",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = "Analyze with AI",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * Odds chip component.
 */
@Composable
private fun OddsChip(
    label: String,
    odds: Double,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) {
            GreenValue.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isHighlighted) {
            androidx.compose.foundation.BorderStroke(2.dp, GreenValue)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format("%.2f", odds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) GreenValue
                       else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Value bet banner shown when AI finds value.
 */
@Composable
private fun ValueBetBanner(
    analysisResult: AIAnalysisResponse
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GreenValue.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star badge
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
                        text = "VALUE FOUND!",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bet: ${analysisResult.recommendation}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "${(analysisResult.confidenceScore * 100).toInt()}% confident • ${analysisResult.suggestedStake ?: 1} units",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF388E3C)
                )
            }
        }
    }
}

private fun formatCommenceTime(isoTime: String): String {
    return try {
        val instant = java.time.Instant.parse(isoTime)
        val formatter = java.time.format.DateTimeFormatter
            .ofPattern("EEE, MMM d • HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoTime
    }
}

@Preview
@Composable
private fun MatchCardPreview() {
    MaterialTheme {
        MatchCard(
            homeTeam = "Manchester United",
            awayTeam = "Liverpool",
            league = "Premier League",
            commenceTime = "2024-01-15T15:00:00Z",
            homeOdds = 2.45,
            drawOdds = 3.40,
            awayOdds = 2.90,
            state = MatchCardState(
                isAnalyzed = true,
                analysisResult = AIAnalysisResponse(
                    recommendation = "HOME",
                    confidenceScore = 0.72,
                    isValueBet = true,
                    rationale = "Strong home form suggests value",
                    suggestedStake = 2
                )
            ),
            onAnalyzeClick = {},
            onCardClick = {}
        )
    }
}
