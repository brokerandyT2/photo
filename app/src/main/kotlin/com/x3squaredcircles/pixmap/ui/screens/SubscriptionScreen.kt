//app/src/main/kotlin/com/x3squaredcircles/pixmap/ui/screens/SubscriptionScreen.kt
package com.x3squaredcircles.pixmap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.x3squaredcircles.pixmap.shared.application.dto.SubscriptionDto
import com.x3squaredcircles.pixmap.shared.application.dto.SubscriptionProductDto
import com.x3squaredcircles.pixmap.shared.domain.entities.BillingPeriod
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import com.x3squaredcircles.pixmap.shared.presentation.viewmodels.PurchaseStatus
import com.x3squaredcircles.pixmap.shared.presentation.viewmodels.SubscriptionViewModel
import kotlinx.coroutines.launch

/**
 * Main subscription management screen
 */
class SubscriptionScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<SubscriptionViewModel>()

        val currentSubscription by viewModel.currentSubscription.collectAsState()
        val availableProducts by viewModel.availableProducts.collectAsState()
        val hasActiveSubscription by viewModel.hasActiveSubscription.collectAsState()
        val isPurchasing by viewModel.isPurchasing.collectAsState()
        val isRestoring by viewModel.isRestoring.collectAsState()
        val purchaseStatus by viewModel.purchaseStatus.collectAsState()
        val expirationWarning by viewModel.expirationWarning.collectAsState()
        val entitlements by viewModel.entitlements.collectAsState()

        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        // Handle purchase status messages
        LaunchedEffect(purchaseStatus) {
            purchaseStatus?.let { status ->
                when (status) {
                    is PurchaseStatus.Success -> {
                        snackbarHostState.showSnackbar(status.message)
                        viewModel.clearPurchaseStatus()
                    }
                    is PurchaseStatus.Error -> {
                        snackbarHostState.showSnackbar(
                            message = status.message,
                            actionLabel = "Dismiss"
                        )
                        viewModel.clearPurchaseStatus()
                    }
                    is PurchaseStatus.Info -> {
                        snackbarHostState.showSnackbar(status.message)
                        viewModel.clearPurchaseStatus()
                    }
                    else -> { /* Processing state handled by UI indicators */ }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Subscription") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (hasActiveSubscription) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.verifySubscription(forceRefresh = true)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Verify")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current subscription status
                if (hasActiveSubscription && currentSubscription != null) {
                    item {
                        CurrentSubscriptionCard(
                            subscription = currentSubscription!!,
                            expirationWarning = expirationWarning,
                            onCancelClick = {
                                scope.launch {
                                    viewModel.cancelSubscription()
                                }
                            }
                        )
                    }
                }

                // Expiration warning
                expirationWarning?.let { warning ->
                    item {
                        ExpirationWarningCard(warning = warning)
                    }
                }

                // Features/Entitlements
                if (entitlements.isNotEmpty()) {
                    item {
                        EntitlementsCard(entitlements = entitlements)
                    }
                }

                // Available products
                if (availableProducts.isNotEmpty()) {
                    item {
                        Text(
                            text = if (hasActiveSubscription) "Upgrade Options" else "Choose Your Plan",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(availableProducts) { product ->
                        SubscriptionProductCard(
                            product = product,
                            isCurrentPlan = currentSubscription?.productId == product.productId,
                            onPurchaseClick = {
                                // This would typically open the purchase flow
                                // For now, we'll show a placeholder
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Purchase flow would open for ${product.name}"
                                    )
                                }
                            }
                        )
                    }
                }

                // Action buttons
                item {
                    ActionButtonsSection(
                        hasActiveSubscription = hasActiveSubscription,
                        isPurchasing = isPurchasing,
                        isRestoring = isRestoring,
                        onRestorePurchases = {
                            scope.launch {
                                viewModel.restorePurchases()
                            }
                        },
                        onManageSubscription = {
                            // Navigate to billing history or settings
                            navigator.push(BillingHistoryScreen())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentSubscriptionCard(
    subscription: SubscriptionDto,
    expirationWarning: String?,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusBadge(status = subscription.status)
            }

            Text(
                text = getDisplayName(subscription.productId),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (subscription.isActive) {
                Text(
                    text = "Expires in ${subscription.daysUntilExpiration} days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (subscription.autoRenewing && subscription.isActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.AutoRenew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Auto-renewal enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (subscription.status == SubscriptionStatus.ACTIVE) {
                TextButton(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel Subscription")
                }
            }
        }
    }
}

@Composable
private fun ExpirationWarningCard(warning: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EntitlementsCard(entitlements: Map<String, Boolean>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Your Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            entitlements.forEach { (feature, hasAccess) ->
                EntitlementItem(
                    feature = formatFeatureName(feature),
                    hasAccess = hasAccess
                )
            }
        }
    }
}

@Composable
private fun EntitlementItem(
    feature: String,
    hasAccess: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = feature,
            style = MaterialTheme.typography.bodyMedium
        )

        Icon(
            imageVector = if (hasAccess) Icons.Default.Check else Icons.Default.Lock,
            contentDescription = if (hasAccess) "Available" else "Locked",
            tint = if (hasAccess) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SubscriptionProductCard(
    product: SubscriptionProductDto,
    isCurrentPlan: Boolean,
    onPurchaseClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (product.billingPeriod == BillingPeriod.YEARLY) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (product.billingPeriod == BillingPeriod.YEARLY) {
                        Text(
                            text = "Best Value",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = product.price,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/${product.billingPeriod.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = product.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (product.features.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    product.features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (isCurrentPlan) {
                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                ) {
                    Text("Current Plan")
                }
            } else {
                Button(
                    onClick = onPurchaseClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose Plan")
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    hasActiveSubscription: Boolean,
    isPurchasing: Boolean,
    isRestoring: Boolean,
    onRestorePurchases: () -> Unit,
    onManageSubscription: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onRestorePurchases,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isPurchasing && !isRestoring
        ) {
            if (isRestoring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Restore Purchases")
        }

        if (hasActiveSubscription) {
            TextButton(
                onClick = onManageSubscription,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Billing History")
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SubscriptionStatus) {
    val (backgroundColor, contentColor, text) = when (status) {
        SubscriptionStatus.ACTIVE -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            "Active"
        )
        SubscriptionStatus.EXPIRED -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            "Expired"
        )
        SubscriptionStatus.CANCELLED -> Triple(
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurface,
            "Cancelled"
        )
        SubscriptionStatus.GRACE_PERIOD -> Triple(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary,
            "Grace Period"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            status.name
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// Helper functions
private fun getDisplayName(productId: String): String {
    return when {
        productId.contains("premium", ignoreCase = true) && productId.contains("yearly", ignoreCase = true) ->
            "Premium Yearly"
        productId.contains("premium", ignoreCase = true) && productId.contains("monthly", ignoreCase = true) ->
            "Premium Monthly"
        productId.contains("pro", ignoreCase = true) && productId.contains("yearly", ignoreCase = true) ->
            "Pro Yearly"
        productId.contains("pro", ignoreCase = true) && productId.contains("monthly", ignoreCase = true) ->
            "Pro Monthly"
        else -> productId.replaceFirstChar { it.uppercase() }
    }
}

private fun formatFeatureName(featureKey: String): String {
    return featureKey.replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}

// Placeholder screen for billing history
class BillingHistoryScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Billing History") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Billing History Screen\n(Implementation pending)",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}