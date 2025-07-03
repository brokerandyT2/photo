//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/SubscriptionCommandHandlers.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.*
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SubscriptionDomainException
import kotlinx.datetime.Clock

/**
 * Command to create a new subscription
 */
data class CreateSubscriptionCommand(
    val userId: String,
    val productId: String,
    val transactionId: String,
    val purchaseToken: String,
    val expirationDate: kotlinx.datetime.Instant,
    val autoRenewing: Boolean = true
) : com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest<Result<Subscription>>

/**
 * Command to update subscription status
 */
data class UpdateSubscriptionStatusCommand(
    val subscriptionId: Int,
    val status: SubscriptionStatus,
    val reason: String? = null
) : com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest<Result<Subscription>>

/**
 * Command to renew a subscription
 */
data class RenewSubscriptionCommand(
    val subscriptionId: Int,
    val newExpirationDate: kotlinx.datetime.Instant,
    val newTransactionId: String? = null
) : com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest<Result<Subscription>>

/**
 * Command to cancel a subscription
 */
data class CancelSubscriptionCommand(
    val subscriptionId: Int,
    val reason: String? = null
) : com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest<Result<Boolean>>

/**
 * Command to update purchase token
 */
data class UpdatePurchaseTokenCommand(
    val subscriptionId: Int,
    val newPurchaseToken: String
) : com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest<Result<Subscription>>

/**
 * Command to restore subscription from store
 */
data class RestoreSubscriptionCommand(
    val userId: String,
    val productId: String
) : com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest<Result<Subscription>>

/**
 * Command to update subscription expiration date
 */
data class UpdateSubscriptionExpirationCommand(
    val subscriptionId: Int,
    val newExpirationDate: kotlinx.datetime.Instant
) : com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest<Result<Subscription>>

/**
 * Handler for CreateSubscriptionCommand
 */
class CreateSubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<CreateSubscriptionCommand, Result<Subscription>> {

    override suspend fun handle(request: CreateSubscriptionCommand): Result<Subscription> {
        return try {
            logger.logInfo("Creating subscription for user: ${request.userId}, product: ${request.productId}")

            // Check if subscription already exists
            val existingResult = subscriptionRepository.getActiveSubscriptionAsync(request.userId)
            if (existingResult.isSuccess && existingResult.data != null) {
                return Result.failure("Subscription already exists for user")
            }

            // Create new subscription
            val subscription = Subscription.create(
                userId = request.userId,
                productId = request.productId,
                transactionId = request.transactionId,
                purchaseToken = request.purchaseToken,
                expirationDate = request.expirationDate,
                autoRenewing = request.autoRenewing
            )

            val result = subscriptionRepository.createAsync(subscription)
            if (!result.isSuccess) {
                return Result.failure("Failed to create subscription: ${result.errorMessage}")
            }

            logger.logInfo("Successfully created subscription: ${subscription.id}")
            Result.success(result.data!!)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during creation", ex)
            Result.failure("Failed to create subscription: ${ex.message}")
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription creation", ex)
            Result.failure("Failed to create subscription: ${ex.message}")
        }
    }
}

/**
 * Handler for UpdateSubscriptionStatusCommand
 */
class UpdateSubscriptionStatusCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<UpdateSubscriptionStatusCommand, Result<Subscription>> {

    override suspend fun handle(request: UpdateSubscriptionStatusCommand): Result<Subscription> {
        return try {
            logger.logInfo("Updating subscription status: ${request.subscriptionId} to ${request.status}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                return Result.failure("Subscription not found")
            }

            val subscription = subscriptionResult.data!!
            subscription.updateStatus(request.status)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                return Result.failure("Failed to update subscription status: ${updateResult.errorMessage}")
            }

            logger.logInfo("Successfully updated subscription status: ${subscription.id}")
            Result.success(subscription)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during status update", ex)
            Result.failure("Failed to update subscription status: ${ex.message}")
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription status update", ex)
            Result.failure("Failed to update subscription status: ${ex.message}")
        }
    }
}

/**
 * Handler for RenewSubscriptionCommand
 */
class RenewSubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<RenewSubscriptionCommand, Result<Subscription>> {

    override suspend fun handle(request: RenewSubscriptionCommand): Result<Subscription> {
        return try {
            logger.logInfo("Renewing subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                return Result.failure("Subscription not found")
            }

            val subscription = subscriptionResult.data!!
            subscription.renew(request.newExpirationDate, request.newTransactionId)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                return Result.failure("Failed to renew subscription: ${updateResult.errorMessage}")
            }

            logger.logInfo("Successfully renewed subscription: ${subscription.id}")
            Result.success(subscription)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during renewal", ex)
            Result.failure("Failed to renew subscription: ${ex.message}")
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription renewal", ex)
            Result.failure("Failed to renew subscription: ${ex.message}")
        }
    }
}

/**
 * Handler for UpdateSubscriptionExpirationCommand
 */
class UpdateSubscriptionExpirationCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<UpdateSubscriptionExpirationCommand, Result<Subscription>> {

    override suspend fun handle(request: UpdateSubscriptionExpirationCommand): Result<Subscription> {
        return try {
            logger.logInfo("Updating expiration for subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                return Result.failure("Subscription not found")
            }

            val subscription = subscriptionResult.data!!
            subscription.updateExpirationDate(request.newExpirationDate)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                return Result.failure("Failed to update expiration date: ${updateResult.errorMessage}")
            }

            logger.logInfo("Successfully updated expiration for subscription: ${subscription.id}")
            Result.success(subscription)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during expiration update", ex)
            Result.failure("Failed to update expiration date: ${ex.message}")
        } catch (ex: Exception) {
            logger.logError("Unexpected error during expiration update", ex)
            Result.failure("Failed to update expiration date: ${ex.message}")
        }
    }
}

/**
 * Handler for UpdatePurchaseTokenCommand
 */
class UpdatePurchaseTokenCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<UpdatePurchaseTokenCommand, Result<Subscription>> {

    override suspend fun handle(request: UpdatePurchaseTokenCommand): Result<Subscription> {
        return try {
            logger.logInfo("Updating purchase token for subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                return Result.failure("Subscription not found")
            }

            val subscription = subscriptionResult.data!!
            subscription.updatePurchaseToken(request.newPurchaseToken)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                return Result.failure("Failed to update purchase token: ${updateResult.errorMessage}")
            }

            logger.logInfo("Successfully updated purchase token for subscription: ${subscription.id}")
            Result.success(subscription)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during purchase token update", ex)
            Result.failure("Failed to update purchase token: ${ex.message}")
        } catch (ex: Exception) {
            logger.logError("Unexpected error during purchase token update", ex)
            Result.failure("Failed to update purchase token: ${ex.message}")
        }
    }
}

/**
 * Handler for CancelSubscriptionCommand
 */
class CancelSubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<CancelSubscriptionCommand, Result<Boolean>> {

    override suspend fun handle(request: CancelSubscriptionCommand): Result<Boolean> {
        return try {
            logger.logInfo("Cancelling subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                return Result.failure("Subscription not found")
            }

            val subscription = subscriptionResult.data!!

            if (subscription.status == SubscriptionStatus.CANCELLED) {
                logger.logWarning("Subscription ${request.subscriptionId} is already cancelled")
                return Result.success(true)
            }

            subscription.cancel()

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                return Result.failure("Failed to cancel subscription: ${updateResult.errorMessage}")
            }

            logger.logInfo("Successfully cancelled subscription: ${subscription.id}")
            Result.success(true)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during cancellation", ex)
            Result.failure("Failed to cancel subscription: ${ex.message}")
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription cancellation", ex)
            Result.failure("Failed to cancel subscription: ${ex.message}")
        }
    }
}

/**
 * Handler for RestoreSubscriptionCommand
 */
class RestoreSubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<RestoreSubscriptionCommand, Result<Subscription>> {

    override suspend fun handle(request: RestoreSubscriptionCommand): Result<Subscription> {
        return try {
            logger.logInfo("Restoring subscription for user: ${request.userId}, product: ${request.productId}")

            val subscriptionResult = subscriptionRepository.getActiveSubscriptionAsync(request.userId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                return Result.failure("No active subscription found to restore")
            }

            val subscription = subscriptionResult.data!!
            subscription.markAsVerified()

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                return Result.failure("Failed to restore subscription: ${updateResult.errorMessage}")
            }

            logger.logInfo("Successfully restored subscription: ${subscription.id}")
            Result.success(subscription)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during restore", ex)
            Result.failure("Failed to restore subscription: ${ex.message}")
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription restore", ex)
            Result.failure("Failed to restore subscription: ${ex.message}")
        }
    }
}