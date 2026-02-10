package com.quickodds.app.ui.screens.paywall

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.quickodds.app.AppConfig
import com.quickodds.app.billing.BillingRepository

@Composable
fun PaywallDialog(
    billingRepository: BillingRepository,
    onDismiss: () -> Unit
) {
    val products by billingRepository.products.collectAsState()
    val activity = LocalContext.current as? Activity

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Daily Limit Reached",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "You've used your free analyses for today. " +
                        "Upgrade to Pro for unlimited daily analyses.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                // Pricing cards
                if (products.isNotEmpty()) {
                    products.forEach { product ->
                        PricingCard(
                            product = product,
                            onSubscribe = {
                                activity?.let { billingRepository.launchPurchaseFlow(it, product) }
                            }
                        )
                    }
                } else {
                    // Fallback pricing when Play Console products not yet available
                    val context = LocalContext.current
                    FallbackPricingCard(
                        label = "Monthly",
                        price = "\$2.99/month",
                        onClick = {
                            billingRepository.startConnection()
                            Toast.makeText(context, "Connecting to Google Play...", Toast.LENGTH_SHORT).show()
                        }
                    )
                    FallbackPricingCard(
                        label = "Yearly",
                        price = "\$6.99/year",
                        isBestValue = true,
                        onClick = {
                            billingRepository.startConnection()
                            Toast.makeText(context, "Connecting to Google Play...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}

@Composable
private fun FallbackPricingCard(
    label: String,
    price: String,
    isBestValue: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isBestValue) {
                    Text(
                        text = "Best value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Button(onClick = onClick) {
                Text(price)
            }
        }
    }
}

@Composable
private fun PricingCard(
    product: ProductDetails,
    onSubscribe: () -> Unit
) {
    val offer = product.subscriptionOfferDetails?.firstOrNull()
    val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: ""
    val isMonthly = product.productId == AppConfig.SUBSCRIPTION_MONTHLY_ID
    val period = if (isMonthly) "/month" else "/year"

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMonthly) "Monthly" else "Yearly",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!isMonthly) {
                    Text(
                        text = "Best value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Button(onClick = onSubscribe) {
                Text("$price$period")
            }
        }
    }
}
