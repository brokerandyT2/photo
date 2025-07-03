//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/SubscriptionQueryHandlers.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SubscriptionDomainException

/**
 * Query to get the active subscription for a user
 */
data class GetActiveSubscriptionQuery(
    val userId: String
) : IRequest<Subscription?>

/**
 * Query to get subscription by transaction ID
 */
data class GetSubscriptionByTransactionIdQuery(
    val transactionId: String
) : IRequest<Subscription?>

/**
 * Query to get subscription by purchase token
 */
data class GetSubscriptionByPurchaseTokenQuery(
    val purchaseToken: String
) : IRequest<Subscription?>

/**
 * Query to get subscription by ID
 */
data class GetSubscriptionByIdQuery(
    val subscriptionId: Int
) : IRequest<Subscription?>

/**
 * Query to get all subscriptions for a user
 */
data class GetSubscriptionsByUserIdQuery(
    val userId: String,
    val includeInactive: Boolean = false
) : IRequest<List<Subscription>>

/**
 * Query to get expired subscriptions
 */
data class GetExpiredSubscriptionsQuery(
    val includeGracePeriod: Boolean = false
) : IRequest<List<Subscription>>

/**
 * Query to check if user has any active subscription
 */
data class HasActiveSubscriptionQuery(
    val userId: String
) : IRequest<Boolean>

/**
 * Handler for GetActiveSubscriptionQuery
 */
class GetActiveSubscriptionQueryHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<GetActiveSubscriptionQuery, Subscription?> {

    override suspend fun handle(request: GetActiveSubscriptionQuery): Subscription? {
        try {
            logger.logInfo("Getting active subscription for user: ${request.userId}")

            val result = subscriptionRepository.getActiveSubscriptionAsync(request.userId)
            if (!result.isSuccess) {
                logger.logWarning("Failed to get active subscription for user: ${request.userId}")
                return null
            }

            val subscription = result.data
            logger.logInfo("Found active subscription: ${subscription?.id ?: "none"} for user: ${request.userId}")
            return subscription

        } catch (ex: Exception) {
            logger.logError("Error getting active subscription for user: ${request.userId}", ex)
            throw SubscriptionDomainException.infrastructureError("GetActiveSubscription", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for GetSubscriptionByTransactionIdQuery
 */
class GetSubscriptionByTransactionIdQueryHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<GetSubscriptionByTransactionIdQuery, Subscription?> {

    override suspend fun handle(request: GetSubscriptionByTransactionIdQuery): Subscription? {
        try {
            logger.logInfo("Getting subscription by transaction ID: ${request.transactionId}")

            val result = subscriptionRepository.getByTransactionIdAsync(request.transactionId)
            if (!result.isSuccess) {
                logger.logWarning("Failed to get subscription by transaction ID: ${request.transactionId}")
                return null
            }

            val subscription = result.data
            logger.logInfo("Found subscription: ${subscription?.id ?: "none"} for transaction: ${request.transactionId}")
            return subscription

        } catch (ex: Exception) {
            logger.logError("Error getting subscription by transaction ID: ${request.transactionId}", ex)
            throw SubscriptionDomainException.infrastructureError("GetSubscriptionByTransactionId", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for GetSubscriptionByPurchaseTokenQuery
 */
class GetSubscriptionByPurchaseTokenQueryHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<GetSubscriptionByPurchaseTokenQuery, Subscription?> {

    override suspend fun handle(request: GetSubscriptionByPurchaseTokenQuery): Subscription? {
        try {
            logger.logInfo("Getting subscription by purchase token")

            val result = subscriptionRepository.getByPurchaseTokenAsync(request.purchaseToken)
            if (!result.isSuccess) {
                logger.logWarning("Failed to get subscription by purchase token")
                return null
            }

            val subscription = result.data
            logger.logInfo("Found subscription: ${subscription?.id ?: "none"} for purchase token")
            return subscription

        } catch (ex: Exception) {
            logger.logError("Error getting subscription by purchase token", ex)
            throw SubscriptionDomainException.infrastructureError("GetSubscriptionByPurchaseToken", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for GetSubscriptionByIdQuery
 */
class GetSubscriptionByIdQueryHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<GetSubscriptionByIdQuery, Subscription?> {

    override suspend fun handle(request: GetSubscriptionByIdQuery): Subscription? {
        try {
            logger.logInfo("Getting subscription by ID: ${request.subscriptionId}")

            val result = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!result.isSuccess) {
                logger.logWarning("Failed to get subscription by ID: ${request.subscriptionId}")
                return null
            }

            val subscription = result.data
            logger.logInfo("Found subscription: ${subscription?.id ?: "none"}")
            return subscription

        } catch (ex: Exception) {
            logger.logError("Error getting subscription by ID: ${request.subscriptionId}", ex)
            throw SubscriptionDomainException.infrastructureError("GetSubscriptionById", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for GetSubscriptionsByUserIdQuery
 */
class GetSubscriptionsByUserIdQueryHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<GetSubscriptionsByUserIdQuery, List<Subscription>> {

    override suspend fun handle(request: GetSubscriptionsByUserIdQuery): List<Subscription> {
        try {
            logger.logInfo("Getting subscriptions for user: ${request.userId}")

            val result = subscriptionRepository.getSubscriptionsByUserIdAsync(request.userId)
            if (!result.isSuccess) {
                logger.logWarning("Failed to get subscriptions for user: ${request.userId}")
                return emptyList()
            }

            val subscriptions = result.data ?: emptyList()

            val filteredSubscriptions = if (request.includeInactive) {
                subscriptions
            } else {
                subscriptions.filter { it.isActive() }
            }

            logger.logInfo("Found ${filteredSubscriptions.size} subscriptions for user: ${request.userId}")
            return filteredSubscriptions

        } catch (ex: Exception) {
            logger.logError("Error getting subscriptions for user: ${request.userId}", ex)
            throw SubscriptionDomainException.infrastructureError("GetSubscriptionsByUserId", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for GetExpiredSubscriptionsQuery
 */
class GetExpiredSubscriptionsQueryHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<GetExpiredSubscriptionsQuery, List<Subscription>> {

    override suspend fun handle(request: GetExpiredSubscriptionsQuery): List<Subscription> {
        try {
            logger.logInfo("Getting expired subscriptions")

            val result = subscriptionRepository.getExpiredSubscriptionsAsync()
            if (!result.isSuccess) {
                logger.logWarning("Failed to get expired subscriptions")
                return emptyList()
            }

            val subscriptions = result.data ?: emptyList()
            logger.logInfo("Found ${subscriptions.size} expired subscriptions")
            return subscriptions

        } catch (ex: Exception) {
            logger.logError("Error getting expired subscriptions", ex)
            throw SubscriptionDomainException.infrastructureError("GetExpiredSubscriptions", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for HasActiveSubscriptionQuery
 */
class HasActiveSubscriptionQueryHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<HasActiveSubscriptionQuery, Boolean> {

    override suspend fun handle(request: HasActiveSubscriptionQuery): Boolean {
        try {
            logger.logInfo("Checking if user has active subscription: ${request.userId}")

            val result = subscriptionRepository.getActiveSubscriptionAsync(request.userId)
            if (!result.isSuccess) {
                logger.logWarning("Failed to check active subscription for user: ${request.userId}")
                return false
            }

            val hasActiveSubscription = result.data != null
            logger.logInfo("User ${request.userId} has active subscription: $hasActiveSubscription")
            return hasActiveSubscription

        } catch (ex: Exception) {
            logger.logError("Error checking active subscription for user: ${request.userId}", ex)
            throw SubscriptionDomainException.infrastructureError("HasActiveSubscription", ex.message ?: "Unknown error", ex)
        }
    }
}