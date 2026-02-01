package com.quickodds.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shimmer loading effect for content placeholders.
 */
@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color.LightGray.copy(alpha = 0.6f),
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.6f)
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_translate"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

/**
 * Shimmer placeholder for match cards.
 */
@Composable
fun MatchCardShimmer(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        // League
        Spacer(
            modifier = Modifier
                .height(12.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Team names
        Spacer(
            modifier = Modifier
                .height(20.dp)
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(
            modifier = Modifier
                .height(20.dp)
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Odds row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                Spacer(
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
            }
        }
    }
}

/**
 * Shimmer placeholder for analysis results.
 */
@Composable
fun AnalysisShimmer(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // AI Analysis label
            Spacer(
                modifier = Modifier
                    .height(16.dp)
                    .width(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            // Badge placeholder
            Spacer(
                modifier = Modifier
                    .height(24.dp)
                    .width(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Brain icon shimmer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text lines
        repeat(3) {
            Spacer(
                modifier = Modifier
                    .height(14.dp)
                    .fillMaxWidth(if (it == 2) 0.5f else 0.9f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Pulsing animation for the brain icon during analysis.
 */
@Composable
fun pulsingScale(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    return scale.value
}
