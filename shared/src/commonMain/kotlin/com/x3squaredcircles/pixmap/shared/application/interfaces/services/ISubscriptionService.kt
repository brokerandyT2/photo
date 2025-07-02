//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/ISubscriptionService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.*
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionProduct
import kotlinx.datetime.Instant

/**
 * Service interface for subscription management and billing operations
 */
interface ISubscriptionService {

    /**
     * Purchases a new subscription
     */
    suspend fun purchaseSubscriptionAsync(
        userId: String,
        productId: String,
        purchaseToken: String,
        transactionId: String
    ): Result<SubscriptionPurchaseResponseDto>

    /**
     * Verifies a subscription with the store
     */
    suspend fun verifySubscriptionAsync(
        subscriptionId: Int,
        forceRefresh: Boolean = false
    ): Result<SubscriptionVerificationDto>

    /**
     * Restores previous subscriptions for a user
     */
    suspend fun restoreSubscriptionsAsync(userId: String): Result<SubscriptionRestoreDto>

    /**
     * Cancels an active subscription
     */
    suspend fun cancelSubscriptionAsync(
        subscriptionId: Int,
        reason: String? = null
    ): Result<Boolean>

    /**
     * Gets the current active subscription for a user
     */
    suspend fun getActiveSubscriptionAsync(userId: String): Result<SubscriptionDto?>

    /**
     * Gets all subscriptions for a user
     */
    suspend fun getUserSubscriptionsAsync(
        userId: String,
        includeInactive: Boolean = false
    ): Result<List<SubscriptionDto>>

    /**
     * Gets subscription summary for a user
     */
    suspend fun getSubscriptionSummaryAsync(userId: String): Result<SubscriptionSummaryDto>

    /**
     * Gets available subscription products
     */
    suspend fun getAvailableProductsAsync(): Result<List<SubscriptionProductDto>>

    /**
     * Checks if user has any active subscription
     */
    suspend fun hasActiveSubscriptionAsync(userId: String): Result<Boolean>

    /**
     * Processes a subscription renewal
     */
    suspend fun processRenewalAsync(
        userId: String,
        productId: String,
        transactionId: String,
        purchaseToken: String,
        expirationDate: Instant
    ): Result<SubscriptionDto>

    /**
     * Updates subscription expiration date (for grace periods)
     */
    suspend fun updateExpirationDateAsync(
        subscriptionId: Int,
        newExpirationDate: Instant
    ): Result<SubscriptionDto>

    /**
     * Gets subscription billing history
     */
    suspend fun getBillingHistoryAsync(subscriptionId: Int): Result<SubscriptionBillingHistoryDto>

    /**
     * Gets subscription statistics
     */
    suspend fun getSubscriptionStatsAsync(
        userId: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<SubscriptionStatsDto>

    /**
     * Validates subscription status with store
     */
    suspend fun validateWithStoreAsync(subscription: Subscription): Result<Boolean>

    /**
     * Gets subscriptions that need verification
     */
    suspend fun getSubscriptionsNeedingVerificationAsync(): Result<List<SubscriptionDto>>

    /**
     * Gets expired subscriptions
     */
    suspend fun getExpiredSubscriptionsAsync(includeGracePeriod: Boolean = false): Result<List<SubscriptionDto>>

    /**
     * Processes expired subscriptions cleanup
     */
    suspend fun processExpiredSubscriptionsAsync(): Result<Int>

    /**
     * Updates purchase token for existing subscription
     */
    suspend fun updatePurchaseTokenAsync(
        subscriptionId: Int,
        newPurchaseToken: String
    ): Result<SubscriptionDto>

    /**
     * Checks subscription entitlements
     */
    suspend fun checkEntitlementsAsync(userId: String): Result<Map<String, Boolean>>

    /**
     * Gets subscription by transaction ID
     */
    suspend fun getByTransactionIdAsync(transactionId: String): Result<SubscriptionDto?>

    /**
     * Gets subscription by purchase token
     */
    suspend fun getByPurchaseTokenAsync(purchaseToken: String): Result<SubscriptionDto?>

    /**
     * Handles subscription grace period logic
     */
    suspend fun handleGracePeriodAsync(subscriptionId: Int): Result<SubscriptionDto>

    /**
     * Processes subscription state changes
     */
    suspend fun processStateChangeAsync(
        subscriptionId: Int,
        newState: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<SubscriptionDto>

    /**
     * Synchronizes subscription data with store
     */
    suspend fun synchronizeWithStoreAsync(userId: String): Result<List<SubscriptionDto>>

    /**
     * Gets subscription product details from store
     */
    suspend fun getProductDetailsFromStoreAsync(productIds: List<String>): Result<List<SubscriptionProductDto>>
}

/**
 * Interface for platform-specific subscription store operations
 */
interface ISubscriptionStoreService {

    /**
     * Verifies purchase with platform store (Google Play, App Store)
     */
    suspend fun verifyPurchaseAsync(
        productId: String,
        purchaseToken: String,
        transactionId: String
    ): Result<SubscriptionVerificationDto>

    /**
     * Gets product details from store
     */
    suspend fun getProductDetailsAsync(productIds: List<String>): Result<List<SubscriptionProductDto>>

    /**
     * Restores purchases from store
     */
    suspend fun restorePurchasesAsync(userId: String): Result<List<SubscriptionPurchaseRequestDto>>

    /**
     * Acknowledges purchase with store
     */
    suspend fun acknowledgePurchaseAsync(purchaseToken: String): Result<Boolean>

    /**
     * Consumes purchase (for consumable products)
     */
    suspend fun consumePurchaseAsync(purchaseToken: String): Result<Boolean>

    /**
     * Gets purchase history from store
     */
    suspend fun getPurchaseHistoryAsync(userId: String): Result<List<BillingEventDto>>

    /**
     * Checks store connection status
     */
    suspend fun isStoreAvailableAsync(): Result<Boolean>

    /**
     * Starts purchase flow
     */
    suspend fun startPurchaseFlowAsync(
        productId: String,
        userId: String
    ): Result<String> // Returns purchase token
}