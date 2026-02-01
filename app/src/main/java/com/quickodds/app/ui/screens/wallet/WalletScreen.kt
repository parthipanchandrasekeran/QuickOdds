package com.quickodds.app.ui.screens.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quickodds.app.domain.model.Transaction
import com.quickodds.app.domain.model.TransactionType
import com.quickodds.app.ui.components.WalletSummaryCard
import com.quickodds.app.ui.theme.GreenValue
import com.quickodds.app.ui.theme.RedLoss
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Wallet",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showDepositDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Deposit")
            }
        }
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    uiState.wallet?.let { wallet ->
                        WalletSummaryCard(wallet = wallet)
                    }
                }

                item {
                    Text(
                        text = "Transaction History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.transactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.transactions) { transaction ->
                        TransactionItem(transaction = transaction)
                    }
                }
            }
        }
    }

    // Deposit Dialog
    if (uiState.showDepositDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDepositDialog() },
            title = { Text("Add Virtual Funds") },
            text = {
                Column {
                    Text(
                        text = "Enter the amount to add to your virtual wallet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.depositAmount,
                        onValueChange = { viewModel.updateDepositAmount(it) },
                        label = { Text("Amount") },
                        leadingIcon = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deposit() },
                    enabled = uiState.depositAmount.toDoubleOrNull()?.let { it > 0 } == true
                ) {
                    Text("Deposit")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDepositDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getTransactionIcon(transaction.type),
                    contentDescription = null,
                    tint = getTransactionColor(transaction.type),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = getTransactionTitle(transaction.type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(transaction.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = formatAmount(transaction.amount, transaction.type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = getTransactionColor(transaction.type)
            )
        }
    }
}

private fun getTransactionIcon(type: TransactionType) = when (type) {
    TransactionType.DEPOSIT -> Icons.Default.Add
    TransactionType.WITHDRAWAL -> Icons.Default.Remove
    TransactionType.BET_PLACED -> Icons.Default.Casino
    TransactionType.BET_WON -> Icons.Default.EmojiEvents
    TransactionType.BET_LOST -> Icons.Default.Close
    TransactionType.BET_VOID -> Icons.Default.Block
}

private fun getTransactionColor(type: TransactionType) = when (type) {
    TransactionType.DEPOSIT, TransactionType.BET_WON -> GreenValue
    TransactionType.WITHDRAWAL, TransactionType.BET_PLACED, TransactionType.BET_LOST -> RedLoss
    TransactionType.BET_VOID -> androidx.compose.ui.graphics.Color.Gray
}

private fun getTransactionTitle(type: TransactionType) = when (type) {
    TransactionType.DEPOSIT -> "Deposit"
    TransactionType.WITHDRAWAL -> "Withdrawal"
    TransactionType.BET_PLACED -> "Bet Placed"
    TransactionType.BET_WON -> "Bet Won"
    TransactionType.BET_LOST -> "Bet Lost"
    TransactionType.BET_VOID -> "Bet Void"
}

private fun formatAmount(amount: Double, type: TransactionType): String {
    val prefix = when (type) {
        TransactionType.DEPOSIT, TransactionType.BET_WON -> "+"
        TransactionType.WITHDRAWAL, TransactionType.BET_PLACED -> "-"
        else -> ""
    }
    val absAmount = kotlin.math.abs(amount)
    return "$prefix$${String.format("%.2f", absAmount)}"
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
