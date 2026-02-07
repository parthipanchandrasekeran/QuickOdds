package com.quickodds.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.quickodds.app.data.local.entity.UserWallet
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ui.theme.RedLoss

/**
 * Wallet header component displaying user's virtual balance.
 */
@Composable
fun WalletHeader(
    wallet: UserWallet?,
    modifier: Modifier = Modifier,
    onAddFundsClick: (() -> Unit)? = null
) {
    val balance = wallet?.balance ?: 0.0
    val currency = wallet?.currency ?: "USD"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6C63FF),
                            Color(0xFF4834DF)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Virtual Wallet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    // Currency badge
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = currency,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Balance
                Text(
                    text = formatCurrency(balance, currency),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Available Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                if (onAddFundsClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onAddFundsClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF4834DF)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add Funds", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Compact wallet header for use in app bars.
 */
@Composable
fun CompactWalletHeader(
    balance: Double,
    currency: String = "USD",
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatCurrency(balance, currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Wallet stats row showing wins/losses.
 */
@Composable
fun WalletStatsRow(
    totalWon: Double,
    totalLost: Double,
    pendingBets: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        WalletStatItem(
            label = "Won",
            value = "+${formatCurrency(totalWon, "USD")}",
            icon = Icons.Filled.TrendingUp,
            color = GreenValue
        )
        WalletStatItem(
            label = "Lost",
            value = "-${formatCurrency(totalLost, "USD")}",
            icon = Icons.Filled.TrendingDown,
            color = RedLoss
        )
        WalletStatItem(
            label = "Pending",
            value = "$pendingBets bets",
            icon = null,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun WalletStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCurrency(amount: Double, currency: String): String {
    return when (currency) {
        "USD" -> "$${String.format("%,.2f", amount)}"
        "EUR" -> "€${String.format("%,.2f", amount)}"
        "GBP" -> "£${String.format("%,.2f", amount)}"
        else -> "${String.format("%,.2f", amount)} $currency"
    }
}

@Preview
@Composable
private fun WalletHeaderPreview() {
    MaterialTheme {
        WalletHeader(
            wallet = UserWallet(balance = 10523.45, currency = "USD"),
            onAddFundsClick = {}
        )
    }
}
