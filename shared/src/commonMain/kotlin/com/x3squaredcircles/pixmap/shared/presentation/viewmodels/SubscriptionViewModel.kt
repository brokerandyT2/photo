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
            executeSafely(
                operation = {
                    // Load in parallel
                    launch { loadCurrentSubscription() }
                    launch { loadAvailableProducts() }
                    launch { loadEntitlements() }
                }
            )
        }
    }

    /**
     * Loads the current active subscription
     */
    suspend fun loadCurrentSubscription() = executeSafely(
        operation = {
            logger.logInfo("Loading current subscription for user: $userId")

            val result = subscriptionService.getActiveSubscriptionAsync(userId)

            if (result.isSuccess) {
                _currentSubscription.value = result.data

                // Load subscription history if we have an active subscription
                result.data?.let {
                    viewModelScope.launch { loadSubscriptionHistory() }
                }
            } else {
                logger.logWarning("No active subscription found for user: $userId")
            }
        }
    )

    /**
     * Loads available subscription products
     */
    suspend fun loadAvailableProducts() = executeSafely(
        operation = {
            logger.logInfo("Loading available subscription products")

            val result = subscriptionService.getAvailableProductsAsync()

            if (result.isSuccess) {
                val products = result.data?.sortedWith(
                    compareBy<SubscriptionProductDto> { it.sortOrder }
                        .thenBy { it.billingPeriod.ordinal }
                ) ?: emptyList()
                _availableProducts.value = products
            } else {
                onSystemError("Failed to load subscription products")
            }
        }
    )

    /**
     * Loads subscription history for the user
     */
    suspend fun loadSubscriptionHistory() = executeSafely(
        operation = {
            logger.logInfo("Loading subscription history for user: $userId")

            val result = subscriptionService.getUserSubscriptionsAsync(userId, includeInactive = true)

            if (result.isSuccess) {
                _subscriptionHistory.value = result.data ?: emptyList()
            }
        }
    )

    /**
     * Loads billing history for the current subscription
     */
    suspend fun loadBillingHistory() = executeSafely(
        operation = {
            val currentSub = _currentSubscription.value
            if (currentSub != null) {
                logger.logInfo("Loading billing history for subscription: ${currentSub.id}")

                val result = subscriptionService.getBillingHistoryAsync(currentSub.id)

                if (result.isSuccess) {
                    _billingHistory.value = result.data?.billingEvents ?: emptyList()
                }
            }
        }
    )

    /**
     * Loads user entitlements
     */
    suspend fun loadEntitlements() = executeSafely(
        operation = {
            logger.logInfo("Loading entitlements for user: $userId")

            val result = subscriptionService.checkEntitlementsAsync(userId)

            if (result.isSuccess) {
                _entitlements.value = result.data ?: emptyMap()
            }
        }
    )

    /**
     * Initiates a subscription purchase
     */
    suspend fun purchaseSubscription(
        productId: String,
        purchaseToken: String,
        transactionId: String
    ) = executeSafely(
        operation = {
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
                    val response = result.data
                    if (response != null) {
                        _currentSubscription.value = response.subscription
                        _purchaseStatus.value = PurchaseStatus.Success("Subscription activated successfully!")

                        // Reload related data
                        viewModelScope.launch { loadEntitlements() }
                        viewModelScope.launch { loadSubscriptionHistory() }

                        logger.logInfo("Subscription purchase completed successfully")
                    } else {
                        _purchaseStatus.value = PurchaseStatus.Error("Purchase failed: No response data")
                        onSystemError("Purchase failed: No response data")
                    }
                } else {
                    _purchaseStatus.value = PurchaseStatus.Error(
                        result.errorMessage ?: "Purchase failed"
                    )
                    onSystemError("Purchase failed: ${result.errorMessage}")
                }
            } catch (ex: Exception) {
                _purchaseStatus.value = PurchaseStatus.Error("Purchase failed: ${ex.message}")
                logger.logError("Purchase failed", ex)
            } finally {
                _isPurchasing.value = false
            }
        }
    )

    /**
     * Restores previous purchases
     */
    suspend fun restorePurchases() = executeSafely(
        operation = {
            if (_isRestoring.value) {
                logger.logWarning("Restore already in progress")
                return@executeSafely
            }

            _isRestoring.value = true

            try {
                logger.logInfo("Restoring purchases for user: $userId")

                val result = subscriptionService.restoreSubscriptionsAsync(userId)

                if (result.isSuccess) {
                    val response = result.data
                    if (response != null) {
                        _purchaseStatus.value = PurchaseStatus.Success("Purchases restored successfully!")

                        // Reload related data
                        viewModelScope.launch { loadCurrentSubscription() }
                        viewModelScope.launch { loadEntitlements() }
                        viewModelScope.launch { loadSubscriptionHistory() }

                        logger.logInfo("Purchase restoration completed successfully")
                    } else {
                        _purchaseStatus.value = PurchaseStatus.Error("Restore failed: No response data")
                        onSystemError("Restore failed: No response data")
                    }
                } else {
                    _purchaseStatus.value = PurchaseStatus.Error(
                        result.errorMessage ?: "Restore failed"
                    )
                    onSystemError("Restore failed: ${result.errorMessage}")
                }
            } catch (ex: Exception) {
                _purchaseStatus.value = PurchaseStatus.Error("Restore failed: ${ex.message}")
                logger.logError("Purchase restore failed", ex)
            } finally {
                _isRestoring.value = false
            }
        }
    )

    /**
     * Cancels the current subscription
     */
    suspend fun cancelSubscription(reason: String? = null) = executeSafely(
        operation = {
            val currentSub = _currentSubscription.value
            if (currentSub == null) {
                onSystemError("No active subscription to cancel")
                return@executeSafely
            }

            logger.logInfo("Cancelling subscription: ${currentSub.id}")

            val result = subscriptionService.cancelSubscriptionAsync(currentSub.id, reason)

            if (result.isSuccess) {
                _purchaseStatus.value = PurchaseStatus.Success("Subscription cancelled successfully")
                viewModelScope.launch { loadCurrentSubscription() } // Reload to get updated status
                logger.logInfo("Subscription cancellation completed")
            } else {
                onSystemError("Cancellation failed: ${result.errorMessage}")
            }
        }
    )

    /**
     * Verifies the current subscription with the store
     */
    suspend fun verifySubscription(forceRefresh: Boolean = false) = executeSafely(
        operation = {
            val currentSub = _currentSubscription.value
            if (currentSub == null) {
                logger.logWarning("No subscription to verify")
                return@executeSafely
            }

            logger.logInfo("Verifying subscription: ${currentSub.id}")

            val result = subscriptionService.verifySubscriptionAsync(currentSub.id, forceRefresh)

            if (result.isSuccess) {
                val verification = result.data
                if (verification != null) {
                    if (verification.isValid) {
                        _purchaseStatus.value = PurchaseStatus.Success("Subscription verified successfully")
                        viewModelScope.launch { loadCurrentSubscription() } // Reload to get any updates
                    } else {
                        _purchaseStatus.value = PurchaseStatus.Error(
                            verification.errorMessage ?: "Verification failed"
                        )
                        onSystemError("Verification failed: ${verification.errorMessage}")
                    }
                } else {
                    _purchaseStatus.value = PurchaseStatus.Error("Verification failed: No response data")
                    onSystemError("Verification failed: No response data")
                }
            } else {
                onSystemError("Verification failed: ${result.errorMessage}")
            }
        }
    )

    /**
     * Checks if one product is an upgrade from another
     */
    private fun isUpgrade(currentProductId: String, newProductId: String): Boolean {
        // Implementation would depend on your product hierarchy
        // For example, yearly might be an upgrade from monthly
        return when {
            currentProductId.contains("monthly") && newProductId.contains("yearly") -> true
            currentProductId.contains("basic") && newProductId.contains("premium") -> true
            else -> false
        }
    }

    /**
     * Gets display name for a product ID
     */
    private fun getDisplayName(productId: String): String {
        return when {
            productId.contains("monthly") -> "Monthly Subscription"
            productId.contains("yearly") -> "Yearly Subscription"
            productId.contains("premium") -> "Premium Subscription"
            else -> "Subscription"
        }
    }

    /**
     * Gets display string for subscription status
     */
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

}

/**
 * Represents the status of a purchase operation
 */
sealed class PurchaseStatus {
    object Processing : PurchaseStatus()
    data class Success(val message: String) : PurchaseStatus()
    data class Error(val message: String) : PurchaseStatus()
}