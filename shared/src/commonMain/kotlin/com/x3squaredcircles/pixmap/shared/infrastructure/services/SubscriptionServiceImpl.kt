// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/SubscriptionServiceImpl.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.dto.*
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionStoreService
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toDto
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toPurchaseResponseDto
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toRestoreDto
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toStatsDto
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toSummaryDto
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toVerificationDto
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toBillingHistoryDto
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SubscriptionDomainException
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Duration.Companion.days

/**
 * Implementation of subscription service with business logic and orchestration
 */
class SubscriptionServiceImpl(
    private val subscriptionRepository: ISubscriptionRepository,
    private val storeService: ISubscriptionStoreService,
    private val logger: ILoggingService
) : ISubscriptionService {

    override suspend fun purchaseSubscriptionAsync(
        userId: String,
        productId: String,
        purchaseToken: String,
        transactionId: String
    ): Result<SubscriptionPurchaseResponseDto> {
        return try {
            logger.logInfo("Processing subscription purchase for user: $userId, product: $productId")

            // Verify purchase with store
            val verificationResult = storeService.verifyPurchaseAsync(productId, purchaseToken, transactionId)
            if (!verificationResult.isSuccess) {
                return Result.failure("Purchase verification failed: ${verificationResult.errorMessage}")
            }

            val verification = verificationResult.data!!
            if (!verification.isValid) {
                return Result.failure("Invalid purchase: ${verification.errorMessage}")
            }

            // Check for existing subscription
            val existingResult = subscriptionRepository.getActiveSubscriptionAsync(userId)
            var previousSubscription: Subscription? = null

            if (existingResult.isSuccess && existingResult.data != null) {
                previousSubscription = existingResult.data
                previousSubscription!!.cancel()
                subscriptionRepository.updateAsync(previousSubscription)
            }

            // Create new subscription
            val subscription = Subscription.createFromPurchase(
                userId = userId,
                productId = productId,
                transactionId = transactionId,
                purchaseToken = purchaseToken,
                expirationDate = verification.expirationDate
            )

            val saveResult = subscriptionRepository.createAsync(subscription)
            if (!saveResult.isSuccess) {
                return Result.failure("Failed to save subscription: ${saveResult.errorMessage}")
            }

            logger.logInfo("Successfully processed subscription purchase for user: $userId")
            val response = SubscriptionPurchaseResponseDto(
                subscriptionId = saveResult.data!!.id,
                isSuccess = true,
                previousSubscriptionId = previousSubscription?.id,
                message = "Subscription purchased successfully"
            )
            Result.success(response)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during purchase", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription purchase", ex)
            Result.failure("Purchase failed: ${ex.message}")
        }
    }

    override suspend fun verifySubscriptionAsync(subscriptionId: Int): Result<SubscriptionVerificationDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!

            // Verify with store
            val storeVerificationResult = storeService.verifyPurchaseAsync(
                subscription.productId,
                subscription.purchaseToken,
                subscription.transactionId
            )

            val verification = if (storeVerificationResult.isSuccess && storeVerificationResult.data != null) {
                storeVerificationResult.data!!
            } else {
                // Create failed verification response
                SubscriptionVerificationDto(
                    subscriptionId = subscriptionId,
                    isValid = false,
                    errorMessage = storeVerificationResult.errorMessage ?: "Store verification failed"
                )
            }

            // Update subscription status based on verification
            if (verification.isValid) {
                subscription.markAsVerified()
                subscriptionRepository.updateAsync(subscription)
            }

            Result.success(verification)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during verification", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription verification", ex)
            Result.failure("Verification failed: ${ex.message}")
        }
    }

    override suspend fun cancelSubscriptionAsync(subscriptionId: Int): Result<Boolean> {
        return try {
            logger.logInfo("Cancelling subscription: $subscriptionId")

            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!

            if (subscription.status == SubscriptionStatus.CANCELLED) {
                logger.logWarning("Subscription $subscriptionId is already cancelled")
                return Result.success(true)
            }

            subscription.cancel()

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to cancel subscription", null)
            }

            logger.logInfo("Successfully cancelled subscription: $subscriptionId")
            Result.success(true)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during cancellation", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription cancellation", ex)
            Result.failure("Cancellation failed: ${ex.message}")
        }
    }

    override suspend fun getActiveSubscriptionAsync(userId: String): Result<SubscriptionDto?> {
        return try {
            val result = subscriptionRepository.getActiveSubscriptionAsync(userId)
            if (!result.isSuccess) {
                return Result.success(null)
            }

            val subscription = result.data
            Result.success(subscription?.toDto())

        } catch (ex: Exception) {
            logger.logError("Error getting active subscription for user: $userId", ex)
            Result.failure("Failed to get active subscription: ${ex.message}")
        }
    }

    override suspend fun getUserSubscriptionsAsync(userId: String, includeInactive: Boolean): Result<List<SubscriptionDto>> {
        return try {
            val result = subscriptionRepository.getSubscriptionsByUserIdAsync(userId)
            if (!result.isSuccess) {
                return Result.success(emptyList())
            }

            val subscriptions = result.data ?: emptyList()
            val filteredSubscriptions = if (includeInactive) {
                subscriptions
            } else {
                subscriptions.filter { it.isActive() }
            }

            Result.success(filteredSubscriptions.map { it.toDto() })

        } catch (ex: Exception) {
            logger.logError("Error getting subscriptions for user: $userId", ex)
            Result.failure("Failed to get subscriptions: ${ex.message}")
        }
    }

    override suspend fun getSubscriptionSummaryAsync(userId: String): Result<SubscriptionSummaryDto> {
        return try {
            val result = subscriptionRepository.getSubscriptionsByUserIdAsync(userId)
            if (!result.isSuccess) {
                return Result.success(SubscriptionSummaryDto(userId, false))
            }

            val subscriptions = result.data ?: emptyList()
            val summary = subscriptions.toSummaryDto(userId)

            Result.success(summary)

        } catch (ex: Exception) {
            logger.logError("Error getting subscription summary for user: $userId", ex)
            Result.failure("Failed to get subscription summary: ${ex.message}")
        }
    }

    override suspend fun restoreSubscriptionsAsync(userId: String): Result<SubscriptionRestoreDto> {
        return try {
            logger.logInfo("Restoring subscriptions for user: $userId")

            // Get purchases from store
            val storePurchasesResult = storeService.restorePurchasesAsync(userId)
            if (!storePurchasesResult.isSuccess) {
                return Result.failure("Failed to restore from store: ${storePurchasesResult.errorMessage}")
            }

            val storePurchases = storePurchasesResult.data!!
            val restoredSubscriptions = mutableListOf<Subscription>()
            var newCount = 0
            var updatedCount = 0

            for (purchase in storePurchases) {
                // Check if subscription already exists
                val existingResult = subscriptionRepository.getByTransactionIdAsync(purchase.transactionId)

                if (existingResult.isSuccess && existingResult.data != null) {
                    // Update existing subscription
                    val existing = existingResult.data!!
                    existing.updatePurchaseToken(purchase.purchaseToken)
                    subscriptionRepository.updateAsync(existing)
                    restoredSubscriptions.add(existing)
                    updatedCount++
                } else {
                    // Create new subscription
                    val newSubscription = Subscription.createFromRestore(
                        userId = userId,
                        productId = purchase.productId,
                        transactionId = purchase.transactionId,
                        purchaseToken = purchase.purchaseToken,
                        expirationDate = Clock.System.now().plus(30.days) // Default 30 days if no expiration
                    )

                    val saveResult = subscriptionRepository.createAsync(newSubscription)
                    if (saveResult.isSuccess) {
                        restoredSubscriptions.add(saveResult.data!!)
                        newCount++
                    }
                }
            }

            val restoreDto = SubscriptionRestoreDto(
                userId = userId,
                restoredCount = restoredSubscriptions.size,
                newSubscriptions = newCount,
                updatedSubscriptions = updatedCount,
                subscriptions = restoredSubscriptions.map { it.toDto() }
            )

            Result.success(restoreDto)

        } catch (ex: Exception) {
            logger.logError("Error restoring subscriptions for user: $userId", ex)
            Result.failure("Failed to restore subscriptions: ${ex.message}")
        }
    }

    override suspend fun getAvailableProductsAsync(): Result<List<SubscriptionProductDto>> {
        return try {
            // Get product IDs (this would typically come from configuration)
            val productIds = listOf("premium_monthly", "premium_yearly", "pro_monthly", "pro_yearly")

            val result = storeService.getProductDetailsAsync(productIds)
            if (!result.isSuccess) {
                return Result.failure("Failed to get product details: ${result.errorMessage}")
            }

            Result.success(result.data!!)

        } catch (ex: Exception) {
            logger.logError("Error getting available products", ex)
            Result.failure("Failed to get available products: ${ex.message}")
        }
    }

    override suspend fun hasActiveSubscriptionAsync(userId: String): Result<Boolean> {
        return try {
            val result = subscriptionRepository.getActiveSubscriptionAsync(userId)
            val hasActive = result.isSuccess && result.data?.isActive() == true
            Result.success(hasActive)

        } catch (ex: Exception) {
            logger.logError("Error checking active subscription for user: $userId", ex)
            Result.success(false)
        }
    }

    override suspend fun processRenewalAsync(
        userId: String,
        productId: String,
        transactionId: String,
        purchaseToken: String,
        expirationDate: Instant
    ): Result<SubscriptionDto> {
        return try {
            logger.logInfo("Processing subscription renewal for user: $userId")

            // Find existing subscription
            val existingResult = subscriptionRepository.getActiveSubscriptionAsync(userId)
            if (!existingResult.isSuccess || existingResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound("active subscription for user")
            }

            val subscription = existingResult.data!!
            subscription.renew(expirationDate, transactionId)
            subscription.updatePurchaseToken(purchaseToken)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.renewalFailed(subscription.id, null)
            }

            logger.logInfo("Successfully renewed subscription: ${subscription.id}")
            Result.success(subscription.toDto())

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during renewal", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription renewal", ex)
            Result.failure("Renewal failed: ${ex.message}")
        }
    }

    override suspend fun updateExpirationDateAsync(subscriptionId: Int, newExpirationDate: Instant): Result<SubscriptionDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!
            subscription.updateExpirationDate(newExpirationDate)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to update expiration date", null)
            }

            Result.success(subscription.toDto())

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during expiration update", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during expiration update", ex)
            Result.failure("Update failed: ${ex.message}")
        }
    }

    override suspend fun getBillingHistoryAsync(subscriptionId: Int): Result<SubscriptionBillingHistoryDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!

            // Get billing history from store
            val historyResult = storeService.getPurchaseHistoryAsync(subscription.userId)
            val billingEvents = if (historyResult.isSuccess && historyResult.data != null) {
                historyResult.data!!
            } else {
                emptyList()
            }

            val billingHistory = billingEvents.toBillingHistoryDto(
                subscriptionId,
                subscription.userId,
                subscription.productId
            )

            Result.success(billingHistory)

        } catch (ex: Exception) {
            logger.logError("Error getting billing history for subscription: $subscriptionId", ex)
            Result.failure("Failed to get billing history: ${ex.message}")
        }
    }

    override suspend fun getSubscriptionStatsAsync(
        userId: String?,
        startDate: Instant?,
        endDate: Instant?
    ): Result<SubscriptionStatsDto> {
        return try {
            // For now, get all subscriptions and filter (in production, this would be done at DB level)
            val allSubscriptions = if (userId != null) {
                val result = subscriptionRepository.getSubscriptionsByUserIdAsync(userId)
                result.data ?: emptyList()
            } else {
                // Would need a method to get all subscriptions in the repository
                emptyList<Subscription>()
            }

            val stats = allSubscriptions.toStatsDto()
            Result.success(stats)

        } catch (ex: Exception) {
            logger.logError("Error getting subscription stats", ex)
            Result.failure("Failed to get subscription stats: ${ex.message}")
        }
    }

    override suspend fun validateWithStoreAsync(subscription: Subscription): Result<Boolean> {
        return try {
            val result = storeService.verifyPurchaseAsync(
                subscription.productId,
                subscription.purchaseToken,
                subscription.transactionId
            )

            if (result.isSuccess && result.data != null) {
                val verification = result.data!!
                Result.success(verification.isValid)
            } else {
                Result.success(false)
            }

        } catch (ex: Exception) {
            logger.logError("Error validating subscription with store", ex)
            Result.success(false)
        }
    }

    override suspend fun getSubscriptionsNeedingVerificationAsync(): Result<List<SubscriptionDto>> {
        return try {
            val result = subscriptionRepository.getSubscriptionsNeedingVerificationAsync()
            if (!result.isSuccess) {
                return Result.success(emptyList())
            }

            val subscriptions = result.data ?: emptyList()
            Result.success(subscriptions.map { it.toDto() })

        } catch (ex: Exception) {
            logger.logError("Error getting subscriptions needing verification", ex)
            Result.failure("Failed to get subscriptions needing verification: ${ex.message}")
        }
    }

    override suspend fun getExpiredSubscriptionsAsync(includeGracePeriod: Boolean): Result<List<SubscriptionDto>> {
        return try {
            val result = subscriptionRepository.getExpiredSubscriptionsAsync()
            if (!result.isSuccess) {
                return Result.success(emptyList())
            }

            val subscriptions = result.data ?: emptyList()
            val filteredSubscriptions = if (includeGracePeriod) {
                subscriptions.filter { subscription ->
                    // Include subscriptions within grace period (7 days after expiration)
                    val gracePeriodEnd = subscription.expirationDate.plus(7.days)
                    Clock.System.now() <= gracePeriodEnd
                }
            } else {
                subscriptions
            }

            Result.success(filteredSubscriptions.map { it.toDto() })

        } catch (ex: Exception) {
            logger.logError("Error getting expired subscriptions", ex)
            Result.failure("Failed to get expired subscriptions: ${ex.message}")
        }
    }

    override suspend fun syncSubscriptionsAsync(userId: String): Result<List<SubscriptionDto>> {
        return try {
            logger.logInfo("Syncing subscriptions for user: $userId")

            // Get current subscriptions from database
            val currentSubscriptionsResult = subscriptionRepository.getSubscriptionsByUserIdAsync(userId)
            val currentSubscriptions = currentSubscriptionsResult.data ?: emptyList()

            // Get active purchases from store
            val storePurchasesResult = storeService.getActivePurchasesAsync(userId)
            if (!storePurchasesResult.isSuccess) {
                return Result.failure("Failed to get store purchases: ${storePurchasesResult.errorMessage}")
            }

            val storePurchases = storePurchasesResult.data!!
            val syncedSubscriptions = mutableListOf<Subscription>()

            // Process each store purchase
            for (purchase in storePurchases) {
                val existingSubscription = currentSubscriptions.find {
                    it.transactionId == purchase.transactionId
                }

                if (existingSubscription != null) {
                    // Update existing subscription
                    existingSubscription.updatePurchaseToken(purchase.purchaseToken)
                    subscriptionRepository.updateAsync(existingSubscription)
                    syncedSubscriptions.add(existingSubscription)
                } else {
                    // Create new subscription from store purchase
                    val newSubscription = Subscription.createFromPurchase(
                        userId = userId,
                        productId = purchase.productId,
                        transactionId = purchase.transactionId,
                        purchaseToken = purchase.purchaseToken,
                        expirationDate = purchase.expirationDate
                    )

                    val saveResult = subscriptionRepository.createAsync(newSubscription)
                    if (saveResult.isSuccess) {
                        syncedSubscriptions.add(saveResult.data!!)
                    }
                }
            }

            Result.success(syncedSubscriptions.map { it.toDto() })

        } catch (ex: Exception) {
            logger.logError("Error syncing subscriptions for user: $userId", ex)
            Result.failure("Failed to sync subscriptions: ${ex.message}")
        }
    }

    override suspend fun validateSubscriptionStatusAsync(subscriptionId: Int): Result<Boolean> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                return Result.success(false)
            }

            val subscription = subscriptionResult.data!!

            // Check if subscription is expired
            val isExpired = subscription.expirationDate < Clock.System.now()
            if (isExpired && subscription.status == SubscriptionStatus.ACTIVE) {
                // Mark as expired
                subscription.expire()
                subscriptionRepository.updateAsync(subscription)
                return Result.success(false)
            }

            Result.success(subscription.isActive())

        } catch (ex: Exception) {
            logger.logError("Error validating subscription status: $subscriptionId", ex)
            Result.success(false)
        }
    }
}