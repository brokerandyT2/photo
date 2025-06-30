// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Subscription.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Subscription entity representing user subscription information
 */
@Serializable
class Subscription(
    userId: String,
    productId: String,
    transactionId: String,
    purchaseToken: String,
    status: SubscriptionStatus,
    startDate: Instant,
    expirationDate: Instant,
    autoRenewing: Boolean = true
) : Entity() {

    var userId: String = userId
        private set

    var productId: String = productId
        private set

    var transactionId: String = transactionId
        private set

    var purchaseToken: String = purchaseToken
        private set

    var status: SubscriptionStatus = status
        private set

    var startDate: Instant = startDate
        private set

    var expirationDate: Instant = expirationDate
        private set

    var autoRenewing: Boolean = autoRenewing
        private set

    var lastVerified: Instant? = null
        private set

    var cancelledAt: Instant? = null
        private set

    var renewalCount: Int = 0
        private set

    init {
        require(userId.isNotBlank()) { "User ID cannot be empty" }
        require(productId.isNotBlank()) { "Product ID cannot be empty" }
        require(transactionId.isNotBlank()) { "Transaction ID cannot be empty" }
        require(purchaseToken.isNotBlank()) { "Purchase token cannot be empty" }
        require(startDate <= expirationDate) { "Start date must be before or equal to expiration date" }
    }

    /**
     * Updates the subscription status
     */
    fun updateStatus(newStatus: SubscriptionStatus) {
        if (status != newStatus) {
            status = newStatus

            if (newStatus == SubscriptionStatus.CANCELLED && cancelledAt == null) {
                cancelledAt = Clock.System.now()
            }

            markAsModified()
        }
    }

    /**
     * Renews the subscription
     */
    fun renew(newExpirationDate: Instant, newTransactionId: String? = null) {
        require(newExpirationDate > expirationDate) {
            "New expiration date must be after current expiration date"
        }

        expirationDate = newExpirationDate
        renewalCount++
        status = SubscriptionStatus.ACTIVE
        cancelledAt = null
        lastVerified = Clock.System.now()

        newTransactionId?.let {
            transactionId = it
        }

        markAsModified()
    }

    /**
     * Cancels the subscription
     */
    fun cancel() {
        status = SubscriptionStatus.CANCELLED
        cancelledAt = Clock.System.now()
        autoRenewing = false
        markAsModified()
    }

    /**
     * Updates the purchase token
     */
    fun updatePurchaseToken(newToken: String) {
        require(newToken.isNotBlank()) { "Purchase token cannot be empty" }
        purchaseToken = newToken
        markAsModified()
    }

    /**
     * Marks subscription as verified
     */
    fun markAsVerified() {
        lastVerified = Clock.System.now()
        markAsModified()
    }

    /**
     * Checks if subscription is currently active
     */
    fun isActive(): Boolean {
        return status == SubscriptionStatus.ACTIVE &&
                expirationDate > Clock.System.now()
    }

    /**
     * Checks if subscription is expired
     */
    fun isExpired(): Boolean {
        return expirationDate <= Clock.System.now()
    }

    /**
     * Checks if subscription is in grace period
     */
    fun isInGracePeriod(): Boolean {
        return status == SubscriptionStatus.GRACE_PERIOD
    }

    /**
     * Checks if subscription needs verification
     */
    fun needsVerification(): Boolean {
        val now = Clock.System.now()
        return lastVerified == null ||
                (now.epochSeconds - lastVerified!!.epochSeconds) > VERIFICATION_INTERVAL_SECONDS
    }

    /**
     * Gets days until expiration
     */
    fun getDaysUntilExpiration(): Int {
        val now = Clock.System.now()
        val secondsUntilExpiration = expirationDate.epochSeconds - now.epochSeconds
        return (secondsUntilExpiration / 86400).toInt() // 86400 seconds in a day
    }

    /**
     * Updates expiration date (for grace period extensions)
     */
    fun updateExpirationDate(newExpirationDate: Instant) {
        expirationDate = newExpirationDate
        markAsModified()
    }

    /**
     * Sets auto-renewal preference
     */
    fun setAutoRenewing(autoRenew: Boolean) {
        autoRenewing = autoRenew
        markAsModified()
    }

    companion object {
        private const val VERIFICATION_INTERVAL_SECONDS = 86400L // 24 hours

        /**
         * Creates a new subscription
         */
        fun create(
            userId: String,
            productId: String,
            transactionId: String,
            purchaseToken: String,
            expirationDate: Instant,
            autoRenewing: Boolean = true
        ): Subscription {
            return Subscription(
                userId = userId,
                productId = productId,
                transactionId = transactionId,
                purchaseToken = purchaseToken,
                status = SubscriptionStatus.ACTIVE,
                startDate = Clock.System.now(),
                expirationDate = expirationDate,
                autoRenewing = autoRenewing
            )
        }
    }
}

/**
 * Subscription status enumeration
 */
@Serializable
enum class SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    PENDING,
    GRACE_PERIOD,
    ON_HOLD,
    PAUSED
}

/**
 * Subscription product information
 */
@Serializable
data class SubscriptionProduct(
    val productId: String,
    val name: String,
    val description: String,
    val price: String,
    val currency: String,
    val billingPeriod: BillingPeriod,
    val features: List<String> = emptyList()
)

/**
 * Billing period enumeration
 */
@Serializable
enum class BillingPeriod {
    MONTHLY,
    YEARLY,
    WEEKLY,
    LIFETIME
}