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
 * Handler for CreateSubscriptionCommand
 */
class CreateSubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<CreateSubscriptionCommand, Subscription> {

    override suspend fun handle(request: CreateSubscriptionCommand): Subscription {
        try {
            logger.logInfo("Creating subscription for user: ${request.userId}, product: ${request.productId}")

            // Check if subscription already exists
            val existingResult = subscriptionRepository.getActiveSubscriptionAsync(request.userId)
            if (existingResult.isSuccess && existingResult.getOrNull() != null) {
                throw SubscriptionDomainException.subscriptionAlreadyExists(request.userId, request.productId)
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
                throw SubscriptionDomainException.databaseError("Failed to create subscription", result.exceptionOrNull())
            }

            logger.logInfo("Successfully created subscription: ${subscription.id}")
            return result.getOrThrow()

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during creation", ex)
            throw ex
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription creation", ex)
            throw SubscriptionDomainException.infrastructureError("CreateSubscription", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for UpdateSubscriptionStatusCommand
 */
class UpdateSubscriptionStatusCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<UpdateSubscriptionStatusCommand, Subscription> {

    override suspend fun handle(request: UpdateSubscriptionStatusCommand): Subscription {
        try {
            logger.logInfo("Updating subscription status: ${request.subscriptionId} to ${request.status}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(request.subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()
            subscription.updateStatus(request.status)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to update subscription status", updateResult.exceptionOrNull())
            }

            logger.logInfo("Successfully updated subscription status: ${subscription.id}")
            return updateResult.getOrThrow()

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during status update", ex)
            throw ex
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription status update", ex)
            throw SubscriptionDomainException.infrastructureError("UpdateSubscriptionStatus", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for RenewSubscriptionCommand
 */
class RenewSubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<RenewSubscriptionCommand, Subscription> {

    override suspend fun handle(request: RenewSubscriptionCommand): Subscription {
        try {
            logger.logInfo("Renewing subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(request.subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()
            subscription.renew(request.newExpirationDate, request.newTransactionId)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.renewalFailed(request.subscriptionId, updateResult.exceptionOrNull())
            }

            logger.logInfo("Successfully renewed subscription: ${subscription.id}")
            return updateResult.getOrThrow()

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during renewal", ex)
            throw ex
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription renewal", ex)
            throw SubscriptionDomainException.renewalFailed(request.subscriptionId, ex)
        }
    }
}

/**
 * Handler for CancelSubscriptionCommand
 */
class CancelSubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<CancelSubscriptionCommand, Subscription> {

    override suspend fun handle(request: CancelSubscriptionCommand): Subscription {
        try {
            logger.logInfo("Cancelling subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(request.subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()

            if (subscription.status == SubscriptionStatus.CANCELLED) {
                logger.logWarning("Subscription ${request.subscriptionId} is already cancelled")
                return subscription
            }

            subscription.cancel()

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to cancel subscription", updateResult.exceptionOrNull())
            }

            logger.logInfo("Successfully cancelled subscription: ${subscription.id}")
            return updateResult.getOrThrow()

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during cancellation", ex)
            throw ex
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription cancellation", ex)
            throw SubscriptionDomainException.infrastructureError("CancelSubscription", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for UpdatePurchaseTokenCommand
 */
class UpdatePurchaseTokenCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<UpdatePurchaseTokenCommand, Subscription> {

    override suspend fun handle(request: UpdatePurchaseTokenCommand): Subscription {
        try {
            logger.logInfo("Updating purchase token for subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(request.subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()
            subscription.updatePurchaseToken(request.newPurchaseToken)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to update purchase token", updateResult.exceptionOrNull())
            }

            logger.logInfo("Successfully updated purchase token for subscription: ${subscription.id}")
            return updateResult.getOrThrow()

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during purchase token update", ex)
            throw ex
        } catch (ex: Exception) {
            logger.logError("Unexpected error during purchase token update", ex)
            throw SubscriptionDomainException.infrastructureError("UpdatePurchaseToken", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for VerifySubscriptionCommand
 */
class VerifySubscriptionCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<VerifySubscriptionCommand, Boolean> {

    override suspend fun handle(request: VerifySubscriptionCommand): Boolean {
        try {
            logger.logInfo("Verifying subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(request.subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()

            // Check if verification is needed
            if (!request.forceRefresh && !subscription.needsVerification()) {
                logger.logInfo("Subscription ${request.subscriptionId} does not need verification")
                return true
            }

            // Mark as verified (in a real implementation, this would verify with the store)
            subscription.markAsVerified()

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to update verification status", updateResult.exceptionOrNull())
            }

            logger.logInfo("Successfully verified subscription: ${subscription.id}")
            return true

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during verification", ex)
            throw ex
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription verification", ex)
            throw SubscriptionDomainException.infrastructureError("VerifySubscription", ex.message ?: "Unknown error", ex)
        }
    }
}

/**
 * Handler for UpdateSubscriptionExpirationCommand
 */
class UpdateSubscriptionExpirationCommandHandler(
    private val subscriptionRepository: ISubscriptionRepository,
    private val logger: ILoggingService
) : IRequestHandler<UpdateSubscriptionExpirationCommand, Subscription> {

    override suspend fun handle(request: UpdateSubscriptionExpirationCommand): Subscription {
        try {
            logger.logInfo("Updating expiration for subscription: ${request.subscriptionId}")

            val subscriptionResult = subscriptionRepository.getByIdAsync(request.subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(request.subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()
            subscription.updateExpirationDate(request.newExpirationDate)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to update expiration date", updateResult.exceptionOrNull())
            }

            logger.logInfo("Successfully updated expiration for subscription: ${subscription.id}")
            return updateResult.getOrThrow()

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during expiration update", ex)
            throw ex
        } catch (ex: Exception) {
            logger.logError("Unexpected error during expiration update", ex)
            throw SubscriptionDomainException.infrastructureError("UpdateSubscriptionExpiration", ex.message ?: "Unknown error", ex)
        }
    }
}