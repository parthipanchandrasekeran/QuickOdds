package com.quickodds.app.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.quickodds.app.AppConfig
import com.quickodds.app.data.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onCheckForUpdate: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val products by viewModel.billingRepository.products.collectAsState()
    val isConnected by viewModel.billingRepository.isConnected.collectAsState()
    val activity = LocalContext.current as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme Section
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size
                        )
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Subscription Section
            Text(
                text = "Subscription",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isSubscribed)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isSubscribed) Icons.Default.CheckCircle else Icons.Default.Star,
                            contentDescription = null,
                            tint = if (uiState.isSubscribed)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (uiState.isSubscribed) "QuickOdds Pro" else "Free Plan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.isSubscribed) "Unlimited analyses"
                                else "Limited daily analyses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!uiState.isSubscribed) {
                if (products.isNotEmpty()) {
                    // Real products from Play Console
                    products.forEach { product ->
                        val offer = product.subscriptionOfferDetails?.firstOrNull()
                        val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: ""
                        val period = if (product.productId == AppConfig.SUBSCRIPTION_MONTHLY_ID) "/month" else "/year"

                        OutlinedButton(
                            onClick = { activity?.let { viewModel.billingRepository.launchPurchaseFlow(it, product) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upgrade to Pro - $price$period")
                        }
                    }
                } else {
                    // Fallback pricing cards (products not yet loaded from Play Console)
                    OutlinedButton(
                        onClick = {
                            viewModel.billingRepository.startConnection()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (!isConnected) "Could not connect to Google Play. Please try again later."
                                    else "Subscription products are being set up. Please try again soon."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upgrade to Pro - \$2.99/month")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.billingRepository.startConnection()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (!isConnected) "Could not connect to Google Play. Please try again later."
                                    else "Subscription products are being set up. Please try again soon."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upgrade to Pro - \$6.99/year (Best value)")
                    }
                }
            }

            HorizontalDivider()

            // Usage Section
            Text(
                text = "Daily Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (uiState.isSubscribed) {
                Text(
                    text = "Unlimited - Pro subscriber",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Scan All", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${uiState.remainingScans} of ${AppConfig.FREE_SCAN_ALL_LIMIT_PER_DAY} remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Analyze", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${uiState.remainingAnalyzes} of ${AppConfig.FREE_ANALYZE_LIMIT_PER_DAY} remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // Check for update
            OutlinedButton(
                onClick = { onCheckForUpdate?.invoke() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for Update")
            }

            Spacer(modifier = Modifier.weight(1f))

            // App version
            Text(
                text = "QuickOdds v${com.quickodds.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
