//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/SubscriptionQueries.kt
package com.x3squaredcircles.pixmap.shared.application.queries

/**
 * Query to get the active subscription for a user
 */
data class GetActiveSubscriptionQuery(
    val userId: String
)

/**
 * Query to get subscription by transaction ID
 */
data class GetSubscriptionByTransactionIdQuery(
    val transactionId: String
)

/**
 * Query to get subscription by purchase token
 */
data class GetSubscriptionByPurchaseTokenQuery(
    val purchaseToken: String
)

/**
 * Query to get subscription by ID
 */
data class GetSubscriptionByIdQuery(
    val subscriptionId: Int
)

/**
 * Query to get all subscriptions for a user
 */
data class GetSubscriptionsByUserIdQuery(
    val userId: String,
    val includeInactive: Boolean = false
)

/**
 * Query to get expired subscriptions
 */
data class GetExpiredSubscriptionsQuery(
    val includeGracePeriod: Boolean = false
)

/**
 * Query to get subscriptions that need verification
 */
data class GetSubscriptionsNeedingVerificationQuery(
    val maxHoursSinceLastVerification: Int = 24
)

/**
 * Query to check if user has any active subscription
 */
data class HasActiveSubscriptionQuery(
    val userId: String
)

/**
 * Query to get subscription statistics
 */
data class GetSubscriptionStatsQuery(
    val userId: String? = null,
    val startDate: kotlinx.datetime.Instant? = null,
    val endDate: kotlinx.datetime.Instant? = null
)

/**
 * Query to get subscriptions by status
 */
data class GetSubscriptionsByStatusQuery(
    val status: com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus,
    val userId: String? = null
)

/**
 * Query to get all subscription products
 */
data class GetSubscriptionProductsQuery(
    val activeOnly: Boolean = true
)