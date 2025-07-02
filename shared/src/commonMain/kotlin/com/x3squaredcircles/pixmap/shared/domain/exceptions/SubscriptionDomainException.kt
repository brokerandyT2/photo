//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/SubscriptionDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when subscription domain business rules are violated
 */
class SubscriptionDomainException(
    code: String,
    message: String,
    cause: Throwable? = null
) : DomainException(code, message, cause) {

    override fun getUserFriendlyMessage(): String {
        return when (code) {
            "INVALID_USER_ID" -> "User ID cannot be empty"
            "INVALID_PRODUCT_ID" -> "Product ID cannot be empty"
            "INVALID_TRANSACTION_ID" -> "Transaction ID cannot be empty"
            "INVALID_PURCHASE_TOKEN" -> "Purchase token cannot be empty"
            "INVALID_DATE_RANGE" -> "Start date must be before expiration date"
            "SUBSCRIPTION_NOT_FOUND" -> "Subscription not found"
            "SUBSCRIPTION_ALREADY_EXISTS" -> "A subscription already exists for this user and product"
            "SUBSCRIPTION_EXPIRED" -> "Subscription has expired"
            "SUBSCRIPTION_CANCELLED" -> "Subscription has been cancelled"
            "SUBSCRIPTION_NOT_ACTIVE" -> "Subscription is not active"
            "INVALID_RENEWAL_DATE" -> "New expiration date must be after current expiration date"
            "PURCHASE_VERIFICATION_FAILED" -> "Unable to verify purchase with store"
            "SUBSCRIPTION_LIMIT_EXCEEDED" -> "Maximum number of subscriptions exceeded"
            "INVALID_SUBSCRIPTION_STATUS" -> "Invalid subscription status"
            "BILLING_ERROR" -> "Billing system error occurred"
            "REFUND_NOT_ALLOWED" -> "Refund is not allowed for this subscription"
            "RENEWAL_FAILED" -> "Subscription renewal failed"
            "DOWNGRADE_NOT_ALLOWED" -> "Subscription downgrade is not permitted"
            "UPGRADE_FAILED" -> "Subscription upgrade failed"
            "PAYMENT_METHOD_INVALID" -> "Payment method is invalid or expired"
            "INSUFFICIENT_FUNDS" -> "Insufficient funds for subscription payment"
            "STORE_CONNECTION_ERROR" -> "Unable to connect to app store"
            "SUBSCRIPTION_RESTORE_FAILED" -> "Failed to restore previous subscription"
            "GRACE_PERIOD_EXPIRED" -> "Subscription grace period has expired"
            "FAMILY_SHARING_ERROR" -> "Family sharing configuration error"
            "REGIONAL_RESTRICTION" -> "Subscription not available in your region"
            "CONCURRENT_MODIFICATION" -> "Subscription was modified by another process"
            "DATABASE_ERROR" -> "Database operation failed"
            "NETWORK_ERROR" -> "Network connection error"
            "INFRASTRUCTURE_ERROR" -> "System error occurred"
            else ->  message ?: "Unknown subscription error"
        }
    }

    companion object {
        // Validation errors
        fun invalidUserId(userId: String?, cause: Throwable? = null) =
            SubscriptionDomainException("INVALID_USER_ID", "User ID '${userId ?: "null"}' is invalid", cause)

        fun invalidProductId(productId: String?, cause: Throwable? = null) =
            SubscriptionDomainException("INVALID_PRODUCT_ID", "Product ID '${productId ?: "null"}' is invalid", cause)

        fun invalidTransactionId(transactionId: String?, cause: Throwable? = null) =
            SubscriptionDomainException("INVALID_TRANSACTION_ID", "Transaction ID '${transactionId ?: "null"}' is invalid", cause)

        fun invalidPurchaseToken(cause: Throwable? = null) =
            SubscriptionDomainException("INVALID_PURCHASE_TOKEN", "Purchase token is invalid", cause)

        fun invalidDateRange(cause: Throwable? = null) =
            SubscriptionDomainException("INVALID_DATE_RANGE", "Start date must be before expiration date", cause)

        // Business rule violations
        fun subscriptionNotFound(id: Any?, cause: Throwable? = null) =
            SubscriptionDomainException("SUBSCRIPTION_NOT_FOUND", "Subscription with ID '${id ?: "null"}' not found", cause)

        fun subscriptionAlreadyExists(userId: String?, productId: String?, cause: Throwable? = null) =
            SubscriptionDomainException("SUBSCRIPTION_ALREADY_EXISTS", "Subscription already exists for user '${userId ?: "null"}' and product '${productId ?: "null"}'", cause)

        fun subscriptionExpired(subscriptionId: Any?, cause: Throwable? = null) =
            SubscriptionDomainException("SUBSCRIPTION_EXPIRED", "Subscription '${subscriptionId ?: "null"}' has expired", cause)

        fun subscriptionCancelled(subscriptionId: Any?, cause: Throwable? = null) =
            SubscriptionDomainException("SUBSCRIPTION_CANCELLED", "Subscription '${subscriptionId ?: "null"}' has been cancelled", cause)

        fun subscriptionNotActive(subscriptionId: Any?, cause: Throwable? = null) =
            SubscriptionDomainException("SUBSCRIPTION_NOT_ACTIVE", "Subscription '${subscriptionId ?: "null"}' is not active", cause)

        // Purchase and billing errors
        fun purchaseVerificationFailed(transactionId: String?, cause: Throwable? = null) =
            SubscriptionDomainException("PURCHASE_VERIFICATION_FAILED", "Failed to verify purchase '${transactionId ?: "unknown"}'", cause)

        fun billingError(message: String?, cause: Throwable? = null) =
            SubscriptionDomainException("BILLING_ERROR", "Billing error: ${message ?: "Unknown error"}", cause)

        fun renewalFailed(subscriptionId: Any?, cause: Throwable? = null) =
            SubscriptionDomainException("RENEWAL_FAILED", "Failed to renew subscription '${subscriptionId ?: "null"}'", cause)

        fun paymentMethodInvalid(cause: Throwable? = null) =
            SubscriptionDomainException("PAYMENT_METHOD_INVALID", "Payment method is invalid or expired", cause)

        fun insufficientFunds(cause: Throwable? = null) =
            SubscriptionDomainException("INSUFFICIENT_FUNDS", "Insufficient funds for subscription payment", cause)

        // Store and platform errors
        fun storeConnectionError(cause: Throwable? = null) =
            SubscriptionDomainException("STORE_CONNECTION_ERROR", "Unable to connect to app store", cause)

        fun subscriptionRestoreFailed(cause: Throwable? = null) =
            SubscriptionDomainException("SUBSCRIPTION_RESTORE_FAILED", "Failed to restore previous subscription", cause)

        fun regionalRestriction(region: String?, cause: Throwable? = null) =
            SubscriptionDomainException("REGIONAL_RESTRICTION", "Subscription not available in region '${region ?: "unknown"}'", cause)

        // System errors
        fun databaseError(message: String?, cause: Throwable? = null) =
            SubscriptionDomainException("DATABASE_ERROR", "Database error: ${message ?: "Unknown error"}", cause)

        fun networkError(message: String?, cause: Throwable? = null) =
            SubscriptionDomainException("NETWORK_ERROR", "Network error: ${message ?: "Unknown error"}", cause)

        fun infrastructureError(operation: String?, message: String?, cause: Throwable? = null) =
            SubscriptionDomainException("INFRASTRUCTURE_ERROR", "Infrastructure error in ${operation ?: "unknown operation"}: ${message ?: "Unknown error"}", cause)

        fun concurrentModification(subscriptionId: Any?, cause: Throwable? = null) =
            SubscriptionDomainException("CONCURRENT_MODIFICATION", "Subscription '${subscriptionId ?: "null"}' was modified by another process", cause)
    }
}