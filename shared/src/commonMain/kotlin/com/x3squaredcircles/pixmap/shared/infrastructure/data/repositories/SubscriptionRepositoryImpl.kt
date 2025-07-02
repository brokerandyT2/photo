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
        return executeWithExceptionMapping("CreateSubscription") {
            logger.logInfo("Creating subscription for user: ${subscription.userId}")

            val entity = subscription.toEntity()
            entity.timestamp = Clock.System.now().epochSeconds

            val connection = context.getConnection()
            val insertedId = connection.insertOrThrow(
                "INSERT INTO Subscription (userId, productId, transactionId, purchaseToken, status, startDate, expirationDate, autoRenewing, lastVerified, cancelledAt, renewalCount, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                entity.timestamp
            )

            val createdEntity = entity.copy(id = insertedId.toInt())
            val result = createdEntity.toDomain()

            logger.logInfo("Successfully created subscription with ID: ${result.id}")
            result
        }
    }

    override suspend fun getActiveSubscriptionAsync(userId: String): Result<Subscription?> {
        return executeWithExceptionMapping("GetActiveSubscription") {
            logger.logInfo("Getting active subscription for user: $userId")

            val connection = context.getConnection()
            val entities = connection.queryList(
                "SELECT * FROM Subscription WHERE userId = ? AND status = ? AND expirationDate > ? ORDER BY expirationDate DESC LIMIT 1",
                SubscriptionEntity::class,
                userId,
                SubscriptionStatus.ACTIVE.name,
                Clock.System.now().epochSeconds
            )

            val result = entities.firstOrNull()?.toDomain()
            logger.logInfo("Found active subscription: ${result?.id ?: "none"} for user: $userId")
            result
        }
    }

    override suspend fun getByTransactionIdAsync(transactionId: String): Result<Subscription?> {
        return executeWithExceptionMapping("GetByTransactionId") {
            logger.logInfo("Getting subscription by transaction ID: $transactionId")

            val connection = context.getConnection()
            val entities = connection.queryList(
                "SELECT * FROM Subscription WHERE transactionId = ? LIMIT 1",
                SubscriptionEntity::class,
                transactionId
            )

            val result = entities.firstOrNull()?.toDomain()
            logger.logInfo("Found subscription: ${result?.id ?: "none"} for transaction: $transactionId")
            result
        }
    }

    override suspend fun updateAsync(subscription: Subscription): Result<Subscription> {
        return executeWithExceptionMapping("UpdateSubscription") {
            logger.logInfo("Updating subscription: ${subscription.id}")

            val entity = subscription.toEntity()
            entity.timestamp = Clock.System.now().epochSeconds

            val connection = context.getConnection()
            val rowsAffected = connection.executeUpdate(
                "UPDATE Subscription SET userId = ?, productId = ?, transactionId = ?, purchaseToken = ?, status = ?, startDate = ?, expirationDate = ?, autoRenewing = ?, lastVerified = ?, cancelledAt = ?, renewalCount = ?, timestamp = ? WHERE id = ?",
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
            subscription
        }
    }

    override suspend fun getByPurchaseTokenAsync(purchaseToken: String): Result<Subscription?> {
        return executeWithExceptionMapping("GetByPurchaseToken") {
            logger.logInfo("Getting subscription by purchase token")

            val connection = context.getConnection()
            val entities = connection.queryList(
                "SELECT * FROM Subscription WHERE purchaseToken = ? LIMIT 1",
                SubscriptionEntity::class,
                purchaseToken
            )

            val result = entities.firstOrNull()?.toDomain()
            logger.logInfo("Found subscription: ${result?.id ?: "none"} for purchase token")
            result
        }
    }

    override suspend fun getSubscriptionsByUserIdAsync(userId: String): Result<List<Subscription>> {
        return executeWithExceptionMapping("GetSubscriptionsByUserId") {
            logger.logInfo("Getting subscriptions for user: $userId")

            val connection = context.getConnection()
            val entities = connection.queryList(
                "SELECT * FROM Subscription WHERE userId = ? ORDER BY startDate DESC",
                SubscriptionEntity::class,
                userId
            )

            val result = entities.map { it.toDomain() }
            logger.logInfo("Found ${result.size} subscriptions for user: $userId")
            result
        }
    }

    override suspend fun getByIdAsync(id: Int): Result<Subscription?> {
        return executeWithExceptionMapping("GetById") {
            logger.logInfo("Getting subscription by ID: $id")

            val connection = context.getConnection()
            val entities = connection.queryList(
                "SELECT * FROM Subscription WHERE id = ? LIMIT 1",
                SubscriptionEntity::class,
                id
            )

            val result = entities.firstOrNull()?.toDomain()
            logger.logInfo("Found subscription: ${result?.id ?: "none"}")
            result
        }
    }

    override suspend fun deleteAsync(subscription: Subscription): Result<Unit> {
        return executeWithExceptionMapping("DeleteSubscription") {
            logger.logInfo("Deleting subscription: ${subscription.id}")

            val connection = context.getConnection()
            val rowsAffected = connection.executeUpdate(
                "DELETE FROM Subscription WHERE id = ?",
                subscription.id
            )

            if (rowsAffected == 0) {
                throw SubscriptionDomainException.subscriptionNotFound(subscription.id)
            }

            logger.logInfo("Successfully deleted subscription: ${subscription.id}")
        }
    }

    override suspend fun getExpiredSubscriptionsAsync(): Result<List<Subscription>> {
        return executeWithExceptionMapping("GetExpiredSubscriptions") {
            logger.logInfo("Getting expired subscriptions")

            val connection = context.getConnection()
            val entities = connection.queryList(
                "SELECT * FROM Subscription WHERE expirationDate <= ? AND status IN (?, ?) ORDER BY expirationDate ASC",
                SubscriptionEntity::class,
                Clock.System.now().epochSeconds,
                SubscriptionStatus.ACTIVE.name,
                SubscriptionStatus.GRACE_PERIOD.name
            )

            val result = entities.map { it.toDomain() }
            logger.logInfo("Found ${result.size} expired subscriptions")
            result
        }
    }

    override suspend fun getSubscriptionsNeedingVerificationAsync(): Result<List<Subscription>> {
        return executeWithExceptionMapping("GetSubscriptionsNeedingVerification") {
            logger.logInfo("Getting subscriptions needing verification")

            val twentyFourHoursAgo = Clock.System.now().epochSeconds - 86400L

            val connection = context.getConnection()
            val entities = connection.queryList(
                "SELECT * FROM Subscription WHERE status = ? AND (lastVerified IS NULL OR lastVerified < ?) ORDER BY lastVerified ASC",
                SubscriptionEntity::class,
                SubscriptionStatus.ACTIVE.name,
                twentyFourHoursAgo
            )

            val result = entities.map { it.toDomain() }
            logger.logInfo("Found ${result.size} subscriptions needing verification")
            result
        }
    }

    private suspend fun <T> executeWithExceptionMapping(
        operation: String,
        block: suspend () -> T
    ): Result<T> {
        return try {
            val result = block()
            Result.success(result)
        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error in $operation", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Infrastructure error in $operation", ex)
            val mappedException = exceptionMapper.mapToSubscriptionDomainException(ex, operation)
            Result.failure(mappedException.getUserFriendlyMessage())
        }
    }
}

/**
 * Extension functions for entity/domain mapping
 */
private fun Subscription.toEntity(): SubscriptionEntity {
    return SubscriptionEntity(
        id = this.id,
        userId = this.userId,
        productId = this.productId,
        transactionId = this.transactionId,
        purchaseToken = this.purchaseToken,
        status = this.status.name,
        startDate = this.startDate.epochSeconds,
        expirationDate = this.expirationDate.epochSeconds,
        autoRenewing = this.autoRenewing,
        lastVerified = this.lastVerified?.epochSeconds,
        cancelledAt = this.cancelledAt?.epochSeconds,
        renewalCount = this.renewalCount,
        timestamp = Clock.System.now().epochSeconds
    )
}

private fun SubscriptionEntity.toDomain(): Subscription {
    return Subscription(
        userId = this.userId,
        productId = this.productId,
        transactionId = this.transactionId,
        purchaseToken = this.purchaseToken,
        status = SubscriptionStatus.valueOf(this.status),
        startDate = Instant.fromEpochSeconds(this.startDate),
        expirationDate = Instant.fromEpochSeconds(this.expirationDate),
        autoRenewing = this.autoRenewing
    ).apply {
        // Set internal properties that are managed by the entity
        // This would typically require reflection or internal setters
        // For now, we'll note that the repository layer handles this mapping
    }
}

/**
 * Subscription entity for database storage
 */
data class SubscriptionEntity(
    val id: Int = 0,
    val userId: String,
    val productId: String,
    val transactionId: String,
    val purchaseToken: String,
    val status: String,
    val startDate: Long,
    val expirationDate: Long,
    val autoRenewing: Boolean,
    val lastVerified: Long? = null,
    val cancelledAt: Long? = null,
    val renewalCount: Int = 0,
    var timestamp: Long = 0
)