//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/SubscriptionRepositoryImpl.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SubscriptionDomainException
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.SubscriptionEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import com.x3squaredcircles.pixmap.shared.infrastructure.services.mapToSubscriptionDomainException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Repository implementation for subscription management
 */
class SubscriptionRepositoryImpl(
    private val context: IDatabaseContext,
    private val logger: ILoggingService,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) : ISubscriptionRepository {

    override suspend fun createAsync(subscription: Subscription): Result<Subscription> {
        return try {
            logger.logInfo("Creating subscription for user: ${subscription.userId}")

            val entity = mapDomainToEntity(subscription)
            val id = context.insertAsync(entity)

            val createdSubscription = createSubscriptionWithId(subscription, id.toInt())
            logger.logInfo("Successfully created subscription with ID: $id")
            Result.success(createdSubscription)
        } catch (ex: Exception) {
            logger.logError("Failed to create subscription", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun getActiveSubscriptionAsync(userId: String): Result<Subscription?> {
        return try {
            logger.logInfo("Getting active subscription for user: $userId")

            val entities = context.queryAsync<SubscriptionEntity>(
                """SELECT * FROM Subscription 
                   WHERE userId = ? AND status = ? AND expirationDate > ? 
                   ORDER BY expirationDate DESC LIMIT 1""",
                ::mapCursorToSubscriptionEntity,
                userId,
                SubscriptionStatus.ACTIVE.name,
                Clock.System.now().epochSeconds
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found active subscription: ${result?.id ?: "none"} for user: $userId")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get active subscription for user: $userId", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun getByTransactionIdAsync(transactionId: String): Result<Subscription?> {
        return try {
            logger.logInfo("Getting subscription by transaction ID: $transactionId")

            val entities = context.queryAsync<SubscriptionEntity>(
                "SELECT * FROM Subscription WHERE transactionId = ? LIMIT 1",
                ::mapCursorToSubscriptionEntity,
                transactionId
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found subscription: ${result?.id ?: "none"} for transaction: $transactionId")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get subscription by transaction ID: $transactionId", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun updateAsync(subscription: Subscription): Result<Subscription> {
        return try {
            logger.logInfo("Updating subscription: ${subscription.id}")

            val entity = mapDomainToEntity(subscription)
            val rowsAffected = context.executeAsync(
                """UPDATE Subscription 
                   SET userId = ?, productId = ?, transactionId = ?, purchaseToken = ?, 
                       status = ?, startDate = ?, expirationDate = ?, autoRenewing = ?, 
                       lastVerified = ?, cancelledAt = ?, renewalCount = ?, timestamp = ?
                   WHERE id = ?""",
                entity.userId,
                entity.productId,
                entity.transactionId,
                entity.purchaseToken,
                entity.status,
                entity.startDate,
                entity.expirationDate,
                if (entity.autoRenewing) 1 else 0,
                entity.lastVerified,
                entity.cancelledAt,
                entity.renewalCount,
                entity.timestamp,
                entity.id
            )

            if (rowsAffected == 0) {
                throw SubscriptionDomainException.subscriptionNotFound(subscription.id)
            }

            logger.logInfo("Successfully updated subscription: ${subscription.id}")
            Result.success(subscription)
        } catch (ex: Exception) {
            logger.logError("Failed to update subscription: ${subscription.id}", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun getByPurchaseTokenAsync(purchaseToken: String): Result<Subscription?> {
        return try {
            logger.logInfo("Getting subscription by purchase token")

            val entities = context.queryAsync<SubscriptionEntity>(
                "SELECT * FROM Subscription WHERE purchaseToken = ? LIMIT 1",
                ::mapCursorToSubscriptionEntity,
                purchaseToken
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found subscription: ${result?.id ?: "none"} for purchase token")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get subscription by purchase token", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun getSubscriptionsByUserIdAsync(userId: String): Result<List<Subscription>> {
        return try {
            logger.logInfo("Getting subscriptions for user: $userId")

            val entities = context.queryAsync<SubscriptionEntity>(
                "SELECT * FROM Subscription WHERE userId = ? ORDER BY startDate DESC",
                ::mapCursorToSubscriptionEntity,
                userId
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} subscriptions for user: $userId")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get subscriptions for user: $userId", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun getByIdAsync(id: Int): Result<Subscription?> {
        return try {
            logger.logInfo("Getting subscription by ID: $id")

            val entities = context.queryAsync<SubscriptionEntity>(
                "SELECT * FROM Subscription WHERE id = ? LIMIT 1",
                ::mapCursorToSubscriptionEntity,
                id
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found subscription: ${result?.id ?: "none"}")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get subscription by ID: $id", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun deleteAsync(subscription: Subscription): Result<Unit> {
        return try {
            logger.logInfo("Deleting subscription: ${subscription.id}")

            val rowsAffected = context.executeAsync(
                "DELETE FROM Subscription WHERE id = ?",
                subscription.id
            )

            if (rowsAffected == 0) {
                throw SubscriptionDomainException.subscriptionNotFound(subscription.id)
            }

            logger.logInfo("Successfully deleted subscription: ${subscription.id}")
            Result.success(Unit)
        } catch (ex: Exception) {
            logger.logError("Failed to delete subscription: ${subscription.id}", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun getExpiredSubscriptionsAsync(): Result<List<Subscription>> {
        return try {
            logger.logInfo("Getting expired subscriptions")

            val entities = context.queryAsync<SubscriptionEntity>(
                """SELECT * FROM Subscription 
                   WHERE expirationDate <= ? AND status IN (?, ?) 
                   ORDER BY expirationDate ASC""",
                ::mapCursorToSubscriptionEntity,
                Clock.System.now().epochSeconds,
                SubscriptionStatus.ACTIVE.name,
                SubscriptionStatus.GRACE_PERIOD.name
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} expired subscriptions")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get expired subscriptions", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    override suspend fun getSubscriptionsNeedingVerificationAsync(): Result<List<Subscription>> {
        return try {
            logger.logInfo("Getting subscriptions needing verification")

            val twentyFourHoursAgo = Clock.System.now().epochSeconds - 86400L

            val entities = context.queryAsync<SubscriptionEntity>(
                """SELECT * FROM Subscription 
                   WHERE status = ? AND (lastVerified IS NULL OR lastVerified < ?)
                   ORDER BY lastVerified ASC""",
                ::mapCursorToSubscriptionEntity,
                SubscriptionStatus.ACTIVE.name,
                twentyFourHoursAgo
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} subscriptions needing verification")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get subscriptions needing verification", ex)
            Result.failure(exceptionMapper.mapToSubscriptionDomainException(ex, "CreateSubscription").message ?: "Subscription operation failed")
        }
    }

    private fun createSubscriptionWithId(originalSubscription: Subscription, id: Int): Subscription {
        val newSubscription = Subscription(
            userId = originalSubscription.userId,
            productId = originalSubscription.productId,
            transactionId = originalSubscription.transactionId,
            purchaseToken = originalSubscription.purchaseToken,
            status = originalSubscription.status,
            startDate = originalSubscription.startDate,
            expirationDate = originalSubscription.expirationDate,
            autoRenewing = originalSubscription.autoRenewing
        )

        setIdUsingReflection(newSubscription, id)

        originalSubscription.lastVerified?.let {
            newSubscription.markAsVerified()
        }

        if (originalSubscription.status == SubscriptionStatus.CANCELLED) {
            newSubscription.cancel()
        }

        repeat(originalSubscription.renewalCount) {
            incrementRenewalCountUsingReflection(newSubscription)
        }

        return newSubscription
    }

    private fun mapEntityToDomain(entity: SubscriptionEntity): Subscription {
        val subscription = Subscription(
            userId = entity.userId,
            productId = entity.productId,
            transactionId = entity.transactionId,
            purchaseToken = entity.purchaseToken,
            status = SubscriptionStatus.valueOf(entity.status),
            startDate = Instant.fromEpochSeconds(entity.startDate),
            expirationDate = Instant.fromEpochSeconds(entity.expirationDate),
            autoRenewing = entity.autoRenewing
        )

        setIdUsingReflection(subscription, entity.id)

        entity.lastVerified?.let {
            subscription.markAsVerified()
        }

        if (entity.cancelledAt != null && subscription.status == SubscriptionStatus.CANCELLED) {
            subscription.cancel()
        }

        repeat(entity.renewalCount) {
            incrementRenewalCountUsingReflection(subscription)
        }

        return subscription
    }

    private fun mapDomainToEntity(subscription: Subscription): SubscriptionEntity {
        return SubscriptionEntity(
            id = subscription.id,
            userId = subscription.userId,
            productId = subscription.productId,
            transactionId = subscription.transactionId,
            purchaseToken = subscription.purchaseToken,
            status = subscription.status.name,
            startDate = subscription.startDate.epochSeconds,
            expirationDate = subscription.expirationDate.epochSeconds,
            autoRenewing = subscription.autoRenewing,
            lastVerified = subscription.lastVerified?.epochSeconds,
            cancelledAt = subscription.cancelledAt?.epochSeconds,
            renewalCount = subscription.renewalCount,
            timestamp = Clock.System.now().epochSeconds
        )
    }

    private fun mapCursorToSubscriptionEntity(cursor: app.cash.sqldelight.db.SqlCursor): SubscriptionEntity {
        return SubscriptionEntity(
            id = cursor.getLong(0)?.toInt() ?: 0,
            userId = cursor.getString(1) ?: "",
            productId = cursor.getString(2) ?: "",
            transactionId = cursor.getString(3) ?: "",
            purchaseToken = cursor.getString(4) ?: "",
            status = cursor.getString(5) ?: SubscriptionStatus.EXPIRED.name,
            startDate = cursor.getLong(6) ?: 0L,
            expirationDate = cursor.getLong(7) ?: 0L,
            autoRenewing = cursor.getLong(8) == 1L,
            lastVerified = cursor.getLong(9),
            cancelledAt = cursor.getLong(10),
            renewalCount = cursor.getLong(11)?.toInt() ?: 0,
            timestamp = cursor.getLong(12) ?: 0L
        )
    }

    private fun setIdUsingReflection(subscription: Subscription, id: Int) {
        try {
            val idField = subscription::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.setInt(subscription, id)
        } catch (e: Exception) {
            logger.logWarning("Could not set ID via reflection: ${e.message}")
        }
    }

    private fun incrementRenewalCountUsingReflection(subscription: Subscription) {
        try {
            val renewalCountField = subscription::class.java.getDeclaredField("renewalCount")
            renewalCountField.isAccessible = true
            val currentCount = renewalCountField.getInt(subscription)
            renewalCountField.setInt(subscription, currentCount + 1)
        } catch (e: Exception) {
            logger.logWarning("Could not increment renewal count via reflection: ${e.message}")
        }
    }
}