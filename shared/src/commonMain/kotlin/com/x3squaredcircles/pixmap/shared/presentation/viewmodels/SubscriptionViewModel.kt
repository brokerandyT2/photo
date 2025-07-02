//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/SubscriptionViewModel.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

import com.x3squaredcircles.pixmap.shared.application.dto.*
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionService
import com.x3squaredcircles.pixmap.shared.domain.entities.BillingPeriod
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * View model for subscription management and billing
 */
class SubscriptionViewModel(
    private val subscriptionService: ISubscriptionService,
    private val logger: ILoggingService,
    private val userId: String
) : BaseViewModel() {

    // State flows for UI binding
    private val _currentSubscription = MutableStateFlow<SubscriptionDto?>(null)
    val currentSubscription: StateFlow<SubscriptionDto?> = _currentSubscription.asStateFlow()

    private val _availableProducts = MutableStateFlow<List<SubscriptionProductDto>>(emptyList())
    val availableProducts: StateFlow<List<SubscriptionProductDto>> = _availableProducts.asStateFlow()

    private val _subscriptionHistory = MutableStateFlow<List<SubscriptionDto>>(emptyList())
    val subscriptionHistory: StateFlow<List<SubscriptionDto>> = _subscriptionHistory.asStateFlow()

    private val _billingHistory = MutableStateFlow<List<BillingEventDto>>(emptyList())
    val billingHistory: StateFlow<List<BillingEventDto>> = _billingHistory.asStateFlow()

    private val _entitlements = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val entitlements: StateFlow<Map<String, Boolean>> = _entitlements.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _purchaseStatus = MutableStateFlow<PurchaseStatus?>(null)
    val purchaseStatus: StateFlow<PurchaseStatus?> = _purchaseStatus.asStateFlow()

    // Computed properties
    val hasActiveSubscription: StateFlow<Boolean> = currentSubscription.map { subscription ->
        subscription?.isActive == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val subscriptionDisplayName: StateFlow<String> = currentSubscription.map { subscription ->
        subscription?.let { getDisplayName(it.productId) } ?: "No active subscription"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "No active subscription")

    val subscriptionStatus: StateFlow<String> = currentSubscription.map { subscription ->
        subscription?.let { getStatusDisplay(it.status) } ?: "Inactive"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Inactive")

    val daysUntilExpiration: StateFlow<Int> = currentSubscription.map { subscription ->
        subscription?.daysUntilExpiration ?: 0
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val expirationWarning: StateFlow<String?> = currentSubscription.map { subscription ->
        subscription?.let {
            when {
                it.daysUntilExpiration <= 0 -> "Subscription has expired"
                it.daysUntilExpiration <= 3 -> "Subscription expires in ${it.daysUntilExpiration} days"
                it.daysUntilExpiration <= 7 -> "Subscription expires in ${it.daysUntilExpiration} days"
                else -> null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val canUpgrade: StateFlow<Boolean> = combine(
        currentSubscription,
        availableProducts
    ) { subscription, products ->
        subscription?.let { current ->
            products.any { product ->
                isUpgrade(current.productId, product.productId)
            }
        } ?: true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val recommendedProduct: StateFlow<SubscriptionProductDto?> = combine(
        currentSubscription,
        availableProducts
    ) { subscription, products ->
        if (subscription == null) {
            // Recommend yearly plan for new users
            products.find { it.billingPeriod == BillingPeriod.YEARLY }
        } else {
            // Recommend upgrade if available
            products.find { isUpgrade(subscription.productId, it.productId) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        loadInitialData()
    }

    /**
     * Loads initial subscription and product data
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            executeSafely {
                // Load in parallel
                launch { loadCurrentSubscription() }
                launch { loadAvailableProducts() }
                launch { loadEntitlements() }
            }
        }
    }

    /**
     * Loads the current active subscription
     */
    suspend fun loadCurrentSubscription() = executeSafely {
        logger.logInfo("Loading current subscription for user: $userId")

        val result = subscriptionService.getActiveSubscriptionAsync(userId)

        if (result.isSuccess) {
            _currentSubscription.value = result.getOrNull()

            // Load subscription history if we have an active subscription
            result.getOrNull()?.let {
                loadSubscriptionHistory()
            }
        } else {
            logger.logWarning("No active subscription found for user: $userId")
        }
    }

    /**
     * Loads available subscription products
     */
    suspend fun loadAvailableProducts() = executeSafely {
        logger.logInfo("Loading available subscription products")

        val result = subscriptionService.getAvailableProductsAsync()

        if (result.isSuccess) {
            val products = result.getOrThrow().sortedWith(
                compareBy<SubscriptionProductDto> { it.sortOrder }
                    .thenBy { it.billingPeriod.ordinal }
            )
            _availableProducts.value = products
        } else {
            onSystemError("Failed to load subscription products")
        }
    }

    /**
     * Loads subscription history for the user
     */
    suspend fun loadSubscriptionHistory() = executeSafely {
        logger.logInfo("Loading subscription history for user: $userId")

        val result = subscriptionService.getUserSubscriptionsAsync(userId, includeInactive = true)

        if (result.isSuccess) {
            _subscriptionHistory.value = result.getOrThrow()
        }
    }

    /**
     * Loads billing history for the current subscription
     */
    suspend fun loadBillingHistory() = executeSafely {
        val currentSub = _currentSubscription.value
        if (currentSub != null) {
            logger.logInfo("Loading billing history for subscription: ${currentSub.id}")

            val result = subscriptionService.getBillingHistoryAsync(currentSub.id)

            if (result.isSuccess) {
                _billingHistory.value = result.getOrThrow().billingEvents
            }
        }
    }

    /**
     * Loads user entitlements
     */
    suspend fun loadEntitlements() = executeSafely {
        logger.logInfo("Loading entitlements for user: $userId")

        val result = subscriptionService.checkEntitlementsAsync(userId)

        if (result.isSuccess) {
            _entitlements.value = result.getOrThrow()
        }
    }

    /**
     * Initiates a subscription purchase
     */
    suspend fun purchaseSubscription(
        productId: String,
        purchaseToken: String,
        transactionId: String
    ) = executeSafely {
        if (_isPurchasing.value) {
            logger.logWarning("Purchase already in progress")
            return@executeSafely
        }

        _isPurchasing.value = true
        _purchaseStatus.value = PurchaseStatus.Processing

        try {
            logger.logInfo("Starting subscription purchase for product: $productId")

            val result = subscriptionService.purchaseSubscriptionAsync(
                userId = userId,
                productId = productId,
                purchaseToken = purchaseToken,
                transactionId = transactionId
            )

            if (result.isSuccess) {
                val response = result.getOrThrow()
                _currentSubscription.value = response.subscription
                _purchaseStatus.value = PurchaseStatus.Success("Subscription activated successfully!")

                // Reload related data
                launch { loadEntitlements() }
                launch { loadSubscriptionHistory() }

                logger.logInfo("Subscription purchase completed successfully")
            } else {
                _purchaseStatus.value = PurchaseStatus.Error(
                    result.exceptionOrNull()?.message ?: "Purchase failed"
                )
                onSystemError("Purchase failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (ex: Exception) {
            _purchaseStatus.value = PurchaseStatus.Error("Purchase failed: ${ex.message}")
            logger.logError("Subscription purchase failed", ex)
        } finally {
            _isPurchasing.value = false
        }
    }

    /**
     * Restores previous purchases
     */
    suspend fun restorePurchases() = executeSafely {
        if (_isRestoring.value) {
            logger.logWarning("Restore already in progress")
            return@executeSafely
        }

        _isRestoring.value = true

        try {
            logger.logInfo("Restoring purchases for user: $userId")

            val result = subscriptionService.restoreSubscriptionsAsync(userId)

            if (result.isSuccess) {
                val restoreResult = result.getOrThrow()

                if (restoreResult.totalRestored > 0) {
                    _purchaseStatus.value = PurchaseStatus.Success(
                        "Restored ${restoreResult.totalRestored} subscription(s)"
                    )

                    // Reload subscription data
                    loadCurrentSubscription()
                } else {
                    _purchaseStatus.value = PurchaseStatus.Info("No purchases found to restore")
                }

                logger.logInfo("Purchase restore completed: ${restoreResult.totalRestored} restored")
            } else {
                _purchaseStatus.value = PurchaseStatus.Error(
                    result.exceptionOrNull()?.message ?: "Restore failed"
                )
                onSystemError("Restore failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (ex: Exception) {
            _purchaseStatus.value = PurchaseStatus.Error("Restore failed: ${ex.message}")
            logger.logError("Purchase restore failed", ex)
        } finally {
            _isRestoring.value = false
        }
    }

    /**
     * Cancels the current subscription
     */
    suspend fun cancelSubscription(reason: String? = null) = executeSafely {
        val currentSub = _currentSubscription.value
        if (currentSub == null) {
            onSystemError("No active subscription to cancel")
            return@executeSafely
        }

        logger.logInfo("Cancelling subscription: ${currentSub.id}")

        val result = subscriptionService.cancelSubscriptionAsync(currentSub.id, reason)

        if (result.isSuccess) {
            _purchaseStatus.value = PurchaseStatus.Success("Subscription cancelled successfully")
            loadCurrentSubscription() // Reload to get updated status
            logger.logInfo("Subscription cancellation completed")
        } else {
            onSystemError("Cancellation failed: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Verifies the current subscription with the store
     */
    suspend fun verifySubscription(forceRefresh: Boolean = false) = executeSafely {
        val currentSub = _currentSubscription.value
        if (currentSub == null) {
            logger.logWarning("No subscription to verify")
            return@executeSafely
        }

        logger.logInfo("Verifying subscription: ${currentSub.id}")

        val result = subscriptionService.verifySubscriptionAsync(currentSub.id, forceRefresh)

        if (result.isSuccess) {
            val verification = result.getOrThrow()
            if (verification.isValid) {
                _purchaseStatus.value = PurchaseStatus.Success("Subscription verified successfully")
                loadCurrentSubscription() // Reload to get any updates
            } else {
                _purchaseStatus.value = PurchaseStatus.Error(
                    verification.errorMessage ?: "Subscription verification failed"
                )
            }
        } else {
            onSystemError("Verification failed: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Clears the current purchase status
     */
    fun clearPurchaseStatus() {
        _purchaseStatus.value = null
    }

    /**
     * Refreshes all subscription data
     */
    suspend fun refreshData() = executeSafely {
        logger.logInfo("Refreshing subscription data")

        launch { loadCurrentSubscription() }
        launch { loadAvailableProducts() }
        launch { loadEntitlements() }

        _currentSubscription.value?.let {
            launch { loadBillingHistory() }
        }
    }

    /**
     * Gets the product for upgrade from current subscription
     */
    fun getUpgradeProduct(): SubscriptionProductDto? {
        val current = _currentSubscription.value
        return if (current != null) {
            _availableProducts.value.find { product ->
                isUpgrade(current.productId, product.productId)
            }
        } else {
            null
        }
    }

    /**
     * Checks if user has a specific entitlement
     */
    fun hasEntitlement(entitlementKey: String): Boolean {
        return _entitlements.value[entitlementKey] == true
    }

    /**
     * Gets savings amount for yearly vs monthly plans
     */
    fun getYearlySavings(yearlyProductId: String, monthlyProductId: String): String? {
        val products = _availableProducts.value
        val yearly = products.find { it.productId == yearlyProductId }
        val monthly = products.find { it.productId == monthlyProductId }

        return if (yearly != null && monthly != null) {
            // This would need actual price parsing - simplified for example
            val yearlyPrice = parsePrice(yearly.price)
            val monthlyPrice = parsePrice(monthly.price) * 12

            if (yearlyPrice != null && monthlyPrice != null && monthlyPrice > yearlyPrice) {
                val savings = monthlyPrice - yearlyPrice
                val percentage = ((savings / monthlyPrice) * 100).toInt()
                "Save $percentage% with yearly plan"
            } else {
                null
            }
        } else {
            null
        }
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

    private fun getStatusDisplay(status: SubscriptionStatus): String {
        return when (status) {
            SubscriptionStatus.ACTIVE -> "Active"
            SubscriptionStatus.EXPIRED -> "Expired"
            SubscriptionStatus.CANCELLED -> "Cancelled"
            SubscriptionStatus.PENDING -> "Pending"
            SubscriptionStatus.GRACE_PERIOD -> "Grace Period"
            SubscriptionStatus.ON_HOLD -> "On Hold"
            SubscriptionStatus.PAUSED -> "Paused"
        }
    }

    private fun isUpgrade(currentProductId: String, targetProductId: String): Boolean {
        // Simplified upgrade logic - Pro is upgrade from Premium, Yearly is upgrade from Monthly
        val currentTier = when {
            currentProductId.contains("pro", ignoreCase = true) -> 2
            currentProductId.contains("premium", ignoreCase = true) -> 1
            else -> 0
        }

        val targetTier = when {
            targetProductId.contains("pro", ignoreCase = true) -> 2
            targetProductId.contains("premium", ignoreCase = true) -> 1
            else -> 0
        }

        val currentPeriod = if (currentProductId.contains("yearly", ignoreCase = true)) 2 else 1
        val targetPeriod = if (targetProductId.contains("yearly", ignoreCase = true)) 2 else 1

        return targetTier > currentTier || (targetTier == currentTier && targetPeriod > currentPeriod)
    }

    private fun parsePrice(priceString: String): Double? {
        return try {
            priceString.replace("[^0-9.]".toRegex(), "").toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Purchase status sealed class for UI state management
 */
sealed class PurchaseStatus {
    object Processing : PurchaseStatus()
    data class Success(val message: String) : PurchaseStatus()
    data class Error(val message: String) : PurchaseStatus()
    data class Info(val message: String) : PurchaseStatus()
}