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
    val userId: String,
    val productId: String,
    val transactionId: String,
    val purchaseToken: String,
    val status: SubscriptionStatus,
    val startDate: Instant,
    val expirationDate: Instant,
    val autoRenewing: Boolean = true
) : Entity() {

    var lastVerified: Instant? = null
        private set

    var cancelledAt: Instant? = null
        private set

    var renewalCount: Int = 0
        private set

    private var _status: SubscriptionStatus = status
    private var _transactionId: String = transactionId
    private var _purchaseToken: String = purchaseToken
    private var _expirationDate: Instant = expirationDate
    private var _autoRenewing: Boolean = autoRenewing

    val currentStatus: SubscriptionStatus
        get() = _status

    val currentTransactionId: String
        get() = _transactionId

    val currentPurchaseToken: String
        get() = _purchaseToken

    val currentExpirationDate: Instant
        get() = _expirationDate

    val currentAutoRenewing: Boolean
        get() = _autoRenewing

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
        if (_status != newStatus) {
            _status = newStatus

            if (newStatus == SubscriptionStatus.CANCELLED && cancelledAt == null) {
                cancelledAt = Clock.System.now()
            }
        }
    }

    /**
     * Renews the subscription
     */
    fun renew(newExpirationDate: Instant, newTransactionId: String? = null) {
        require(newExpirationDate > _expirationDate) {
            "New expiration date must be after current expiration date"
        }

        _expirationDate = newExpirationDate
        renewalCount++
        _status = SubscriptionStatus.ACTIVE
        cancelledAt = null
        lastVerified = Clock.System.now()

        newTransactionId?.let {
            _transactionId = it
        }
    }

    /**
     * Cancels the subscription
     */
    fun cancel() {
        _status = SubscriptionStatus.CANCELLED
        cancelledAt = Clock.System.now()
        _autoRenewing = false
    }

    /**
     * Updates the purchase token
     */
    fun updatePurchaseToken(newToken: String) {
        require(newToken.isNotBlank()) { "Purchase token cannot be empty" }
        _purchaseToken = newToken
    }

    /**
     * Marks subscription as verified
     */
    fun markAsVerified() {
        lastVerified = Clock.System.now()
    }

    /**
     * Checks if subscription is currently active
     */
    fun isActive(): Boolean {
        return _status == SubscriptionStatus.ACTIVE &&
                _expirationDate > Clock.System.now()
    }

    /**
     * Checks if subscription is expired
     */
    fun isExpired(): Boolean {
        return _expirationDate <= Clock.System.now()
    }

    /**
     * Checks if subscription is in grace period
     */
    fun isInGracePeriod(): Boolean {
        return _status == SubscriptionStatus.GRACE_PERIOD
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
        val secondsUntilExpiration = _expirationDate.epochSeconds - now.epochSeconds
        return (secondsUntilExpiration / 86400).toInt() // 86400 seconds in a day
    }

    /**
     * Updates expiration date (for grace period extensions)
     */
    fun updateExpirationDate(newExpirationDate: Instant) {
        _expirationDate = newExpirationDate
    }

    /**
     * Sets auto-renewal preference
     */
    fun setAutoRenewing(autoRenew: Boolean) {
        _autoRenewing = autoRenew
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