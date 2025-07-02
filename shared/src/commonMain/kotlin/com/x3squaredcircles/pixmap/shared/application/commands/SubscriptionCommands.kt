//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/SubscriptionCommands.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import kotlinx.datetime.Instant

/**
 * Command to create a new subscription
 */
data class CreateSubscriptionCommand(
    val userId: String,
    val productId: String,
    val transactionId: String,
    val purchaseToken: String,
    val expirationDate: Instant,
    val autoRenewing: Boolean = true
)

/**
 * Command to update subscription status
 */
data class UpdateSubscriptionStatusCommand(
    val subscriptionId: Int,
    val status: SubscriptionStatus,
    val reason: String? = null
)

/**
 * Command to renew a subscription
 */
data class RenewSubscriptionCommand(
    val subscriptionId: Int,
    val newExpirationDate: Instant,
    val newTransactionId: String? = null
)

/**
 * Command to cancel a subscription
 */
data class CancelSubscriptionCommand(
    val subscriptionId: Int,
    val reason: String? = null
)

/**
 * Command to update purchase token
 */
data class UpdatePurchaseTokenCommand(
    val subscriptionId: Int,
    val newPurchaseToken: String
)

/**
 * Command to verify subscription with store
 */
data class VerifySubscriptionCommand(
    val subscriptionId: Int,
    val forceRefresh: Boolean = false
)

/**
 * Command to restore subscription from store
 */
data class RestoreSubscriptionCommand(
    val userId: String,
    val productId: String
)

/**
 * Command to update subscription expiration date
 */
data class UpdateSubscriptionExpirationCommand(
    val subscriptionId: Int,
    val newExpirationDate: Instant
)

/**
 * Command to process subscription renewal
 */
data class ProcessSubscriptionRenewalCommand(
    val userId: String,
    val productId: String,
    val transactionId: String,
    val purchaseToken: String,
    val expirationDate: Instant
)