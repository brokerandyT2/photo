//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/repositories/ISubscriptionRepository.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription

/**
 * Repository interface for subscription management
 */
interface ISubscriptionRepository {

    /**
     * Creates a new subscription record
     */
    suspend fun createAsync(subscription: Subscription): Result<Subscription>

    /**
     * Gets the current active subscription for a user
     */
    suspend fun getActiveSubscriptionAsync(userId: String): Result<Subscription?>

    /**
     * Gets subscription by transaction ID
     */
    suspend fun getByTransactionIdAsync(transactionId: String): Result<Subscription?>

    /**
     * Updates an existing subscription
     */
    suspend fun updateAsync(subscription: Subscription): Result<Subscription>

    /**
     * Gets subscription by purchase token
     */
    suspend fun getByPurchaseTokenAsync(purchaseToken: String): Result<Subscription?>

    /**
     * Gets all subscriptions for a user
     */
    suspend fun getSubscriptionsByUserIdAsync(userId: String): Result<List<Subscription>>

    /**
     * Gets subscription by ID
     */
    suspend fun getByIdAsync(id: Int): Result<Subscription?>

    /**
     * Deletes a subscription
     */
    suspend fun deleteAsync(subscription: Subscription): Result<Unit>

    /**
     * Gets expired subscriptions that need cleanup
     */
    suspend fun getExpiredSubscriptionsAsync(): Result<List<Subscription>>

    /**
     * Gets subscriptions that need verification
     */
    suspend fun getSubscriptionsNeedingVerificationAsync(): Result<List<Subscription>>
}