//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/SubscriptionServiceImpl.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.*
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
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SubscriptionDomainException
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
                return Result.failure("Purchase verification failed: ${verificationResult.exceptionOrNull()?.message}")
            }

            val verification = verificationResult.getOrThrow()
            if (!verification.isValid) {
                return Result.failure("Invalid purchase: ${verification.errorMessage}")
            }

            // Check for existing subscription
            val existingResult = subscriptionRepository.getActiveSubscriptionAsync(userId)
            var previousSubscription: Subscription? = null
            var isNewSubscription = true

            if (existingResult.isSuccess && existingResult.getOrNull() != null) {
                previousSubscription = existingResult.getOrThrow()
                previousSubscription.cancel()
                subscriptionRepository.updateAsync(previousSubscription)
                isNewSubscription = false
            }

            // Create new subscription
            val expirationDate = verification.expirationDate ?: calculateExpirationDate(productId)
            val subscription = Subscription.create(
                userId = userId,
                productId = productId,
                transactionId = transactionId,
                purchaseToken = purchaseToken,
                expirationDate = expirationDate,
                autoRenewing = verification.autoRenewStatus ?: true
            )

            val createResult = subscriptionRepository.createAsync(subscription)
            if (!createResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to create subscription", createResult.exceptionOrNull())
            }

            val createdSubscription = createResult.getOrThrow()

            // Acknowledge purchase with store
            storeService.acknowledgePurchaseAsync(purchaseToken)

            logger.logInfo("Successfully processed subscription purchase: ${createdSubscription.id}")

            val response = createdSubscription.toPurchaseResponseDto(isNewSubscription, previousSubscription)
            Result.success(response)

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during purchase", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription purchase", ex)
            Result.failure("Purchase failed: ${ex.message}")
        }
    }

    override suspend fun verifySubscriptionAsync(
        subscriptionId: Int,
        forceRefresh: Boolean
    ): Result<SubscriptionVerificationDto> {
        return try {
            logger.logInfo("Verifying subscription: $subscriptionId")

            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()

            // Check if verification is needed
            if (!forceRefresh && !subscription.needsVerification()) {
                logger.logInfo("Subscription $subscriptionId does not need verification")
                return Result.success(subscription.toVerificationDto(true))
            }

            // Verify with store
            val storeVerificationResult = storeService.verifyPurchaseAsync(
                subscription.productId,
                subscription.purchaseToken,
                subscription.transactionId
            )

            if (!storeVerificationResult.isSuccess) {
                val verification = subscription.toVerificationDto(false, "Store verification failed")
                return Result.success(verification)
            }

            val storeVerification = storeVerificationResult.getOrThrow()

            // Update subscription based on store verification
            if (storeVerification.isValid) {
                subscription.markAsVerified()
                storeVerification.expirationDate?.let { newExpiration ->
                    if (newExpiration != subscription.expirationDate) {
                        subscription.updateExpirationDate(newExpiration)
                    }
                }
                subscriptionRepository.updateAsync(subscription)
            }

            logger.logInfo("Subscription verification completed: ${subscription.id}")
            Result.success(subscription.toVerificationDto(storeVerification.isValid, storeVerification.errorMessage))

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during verification", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during subscription verification", ex)
            Result.failure("Verification failed: ${ex.message}")
        }
    }

    override suspend fun restoreSubscriptionsAsync(userId: String): Result<SubscriptionRestoreDto> {
        return try {
            logger.logInfo("Restoring subscriptions for user: $userId")

            // Get purchases from store
            val storePurchasesResult = storeService.restorePurchasesAsync(userId)
            if (!storePurchasesResult.isSuccess) {
                return Result.failure("Failed to restore from store: ${storePurchasesResult.exceptionOrNull()?.message}")
            }

            val storePurchases = storePurchasesResult.getOrThrow()
            val restoredSubscriptions = mutableListOf<Subscription>()
            var newCount = 0
            var updatedCount = 0

            for (purchase in storePurchases) {
                // Check if subscription already exists
                val existingResult = subscriptionRepository.getByTransactionIdAsync(purchase.transactionId)

                if (existingResult.isSuccess && existingResult.getOrNull() != null) {
                    // Update existing subscription
                    val existing = existingResult.getOrThrow()
                    existing.updatePurchaseToken(purchase.purchaseToken)
                    existing.markAsVerified()
                    subscriptionRepository.updateAsync(existing)
                    restoredSubscriptions.add(existing)
                    updatedCount++
                } else {
                    // Create new subscription
                    val subscription = Subscription.create(
                        userId = purchase.userId,
                        productId = purchase.productId,
                        transactionId = purchase.transactionId,
                        purchaseToken = purchase.purchaseToken,
                        expirationDate = purchase.purchaseDate.plus(calculateSubscriptionDuration(purchase.productId)),
                        autoRenewing = purchase.autoRenewing
                    )

                    val createResult = subscriptionRepository.createAsync(subscription)
                    if (createResult.isSuccess) {
                        restoredSubscriptions.add(createResult.getOrThrow())
                        newCount++
                    }
                }
            }

            logger.logInfo("Restored ${restoredSubscriptions.size} subscriptions for user: $userId")

            val restoreDto = restoredSubscriptions.toRestoreDto(userId, restoredSubscriptions.size, newCount, updatedCount)
            Result.success(restoreDto)

        } catch (ex: Exception) {
            logger.logError("Error during subscription restore", ex)
            Result.failure("Restore failed: ${ex.message}")
        }
    }

    override suspend fun cancelSubscriptionAsync(subscriptionId: Int, reason: String?): Result<Boolean> {
        return try {
            logger.logInfo("Cancelling subscription: $subscriptionId")

            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()

            if (subscription.status == SubscriptionStatus.CANCELLED) {
                logger.logWarning("Subscription $subscriptionId is already cancelled")
                return Result.success(true)
            }

            subscription.cancel()

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to cancel subscription", updateResult.exceptionOrNull())
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

            val subscription = result.getOrNull()
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

            val subscriptions = result.getOrNull() ?: emptyList()
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

            val subscriptions = result.getOrNull() ?: emptyList()
            val summary = subscriptions.toSummaryDto(userId)

            Result.success(summary)

        } catch (ex: Exception) {
            logger.logError("Error getting subscription summary for user: $userId", ex)
            Result.failure("Failed to get subscription summary: ${ex.message}")
        }
    }

    override suspend fun getAvailableProductsAsync(): Result<List<SubscriptionProductDto>> {
        return try {
            // Get product IDs (this would typically come from configuration)
            val productIds = listOf("premium_monthly", "premium_yearly", "pro_monthly", "pro_yearly")

            val result = storeService.getProductDetailsAsync(productIds)
            if (!result.isSuccess) {
                return Result.failure("Failed to get product details: ${result.exceptionOrNull()?.message}")
            }

            Result.success(result.getOrThrow())

        } catch (ex: Exception) {
            logger.logError("Error getting available products", ex)
            Result.failure("Failed to get available products: ${ex.message}")
        }
    }

    override suspend fun hasActiveSubscriptionAsync(userId: String): Result<Boolean> {
        return try {
            val result = subscriptionRepository.getActiveSubscriptionAsync(userId)
            val hasActive = result.isSuccess && result.getOrNull()?.isActive() == true
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
            if (!existingResult.isSuccess || existingResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound("active subscription for user")
            }

            val subscription = existingResult.getOrThrow()
            subscription.renew(expirationDate, transactionId)
            subscription.updatePurchaseToken(purchaseToken)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.renewalFailed(subscription.id, updateResult.exceptionOrNull())
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
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()
            subscription.updateExpirationDate(newExpirationDate)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to update expiration date", updateResult.exceptionOrNull())
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
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()

            // Get billing history from store
            val historyResult = storeService.getPurchaseHistoryAsync(subscription.userId)
            val billingEvents = if (historyResult.isSuccess) {
                historyResult.getOrThrow()
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
                result.getOrNull() ?: emptyList()
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

    // Additional methods implementation continues...
    override suspend fun validateWithStoreAsync(subscription: Subscription): Result<Boolean> {
        return try {
            val result = storeService.verifyPurchaseAsync(
                subscription.productId,
                subscription.purchaseToken,
                subscription.transactionId
            )

            if (result.isSuccess) {
                val verification = result.getOrThrow()
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

            val subscriptions = result.getOrNull() ?: emptyList()
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

            val subscriptions = result.getOrNull() ?: emptyList()
            val filtered = if (includeGracePeriod) {
                subscriptions
            } else {
                subscriptions.filter { !it.isInGracePeriod() }
            }

            Result.success(filtered.map { it.toDto() })

        } catch (ex: Exception) {
            logger.logError("Error getting expired subscriptions", ex)
            Result.failure("Failed to get expired subscriptions: ${ex.message}")
        }
    }

    override suspend fun processExpiredSubscriptionsAsync(): Result<Int> {
        return try {
            logger.logInfo("Processing expired subscriptions")

            val expiredResult = subscriptionRepository.getExpiredSubscriptionsAsync()
            if (!expiredResult.isSuccess) {
                return Result.success(0)
            }

            val expiredSubscriptions = expiredResult.getOrNull() ?: emptyList()
            var processedCount = 0

            for (subscription in expiredSubscriptions) {
                try {
                    if (subscription.status == SubscriptionStatus.ACTIVE) {
                        subscription.updateStatus(SubscriptionStatus.EXPIRED)
                        subscriptionRepository.updateAsync(subscription)
                        processedCount++
                    }
                } catch (ex: Exception) {
                    logger.logError("Failed to process expired subscription: ${subscription.id}", ex)
                }
            }

            logger.logInfo("Processed $processedCount expired subscriptions")
            Result.success(processedCount)

        } catch (ex: Exception) {
            logger.logError("Error processing expired subscriptions", ex)
            Result.failure("Failed to process expired subscriptions: ${ex.message}")
        }
    }

    override suspend fun updatePurchaseTokenAsync(subscriptionId: Int, newPurchaseToken: String): Result<SubscriptionDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()
            subscription.updatePurchaseToken(newPurchaseToken)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess) {
                throw SubscriptionDomainException.databaseError("Failed to update purchase token", updateResult.exceptionOrNull())
            }

            Result.success(subscription.toDto())

        } catch (ex: SubscriptionDomainException) {
            logger.logError("Subscription domain error during purchase token update", ex)
            Result.failure(ex.getUserFriendlyMessage())
        } catch (ex: Exception) {
            logger.logError("Unexpected error during purchase token update", ex)
            Result.failure("Update failed: ${ex.message}")
        }
    }

    override suspend fun checkEntitlementsAsync(userId: String): Result<Map<String, Boolean>> {
        return try {
            val activeResult = subscriptionRepository.getActiveSubscriptionAsync(userId)
            val hasActiveSubscription = activeResult.isSuccess && activeResult.getOrNull()?.isActive() == true

            val entitlements = mapOf(
                "premium_features" to hasActiveSubscription,
                "unlimited_locations" to hasActiveSubscription,
                "weather_data" to hasActiveSubscription,
                "export_data" to hasActiveSubscription,
                "priority_support" to hasActiveSubscription
            )

            Result.success(entitlements)

        } catch (ex: Exception) {
            logger.logError("Error checking entitlements for user: $userId", ex)
            Result.failure("Failed to check entitlements: ${ex.message}")
        }
    }

    override suspend fun getByTransactionIdAsync(transactionId: String): Result<SubscriptionDto?> {
        return try {
            val result = subscriptionRepository.getByTransactionIdAsync(transactionId)
            val subscription = result.getOrNull()
            Result.success(subscription?.toDto())

        } catch (ex: Exception) {
            logger.logError("Error getting subscription by transaction ID: $transactionId", ex)
            Result.failure("Failed to get subscription: ${ex.message}")
        }
    }

    override suspend fun getByPurchaseTokenAsync(purchaseToken: String): Result<SubscriptionDto?> {
        return try {
            val result = subscriptionRepository.getByPurchaseTokenAsync(purchaseToken)
            val subscription = result.getOrNull()
            Result.success(subscription?.toDto())

        } catch (ex: Exception) {
            logger.logError("Error getting subscription by purchase token", ex)
            Result.failure("Failed to get subscription: ${ex.message}")
        }
    }

    override suspend fun handleGracePeriodAsync(subscriptionId: Int): Result<SubscriptionDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()

            if (subscription.isExpired() && subscription.status == SubscriptionStatus.ACTIVE) {
                subscription.updateStatus(SubscriptionStatus.GRACE_PERIOD)
                // Extend expiration by grace period (e.g., 3 days)
                val gracePeriodExtension = kotlinx.datetime.DateTimeUnit.DAY * 3
                val newExpiration = subscription.expirationDate.plus(gracePeriodExtension)
                subscription.updateExpirationDate(newExpiration)

                subscriptionRepository.updateAsync(subscription)
            }

            Result.success(subscription.toDto())

        } catch (ex: Exception) {
            logger.logError("Error handling grace period for subscription: $subscriptionId", ex)
            Result.failure("Failed to handle grace period: ${ex.message}")
        }
    }

    override suspend fun processStateChangeAsync(
        subscriptionId: Int,
        newState: String,
        metadata: Map<String, Any>
    ): Result<SubscriptionDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.getOrNull() == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.getOrThrow()

            // Process state change based on the new state
            when (newState.uppercase()) {
                "CANCELLED" -> subscription.cancel()
                "PAUSED" -> subscription.updateStatus(SubscriptionStatus.PAUSED)
                "ACTIVE" -> subscription.updateStatus(SubscriptionStatus.ACTIVE)
                "ON_HOLD" -> subscription.updateStatus(SubscriptionStatus.ON_HOLD)
            }

            subscriptionRepository.updateAsync(subscription)
            Result.success(subscription.toDto())

        } catch (ex: Exception) {
            logger.logError("Error processing state change for subscription: $subscriptionId", ex)
            Result.failure("Failed to process state change: ${ex.message}")
        }
    }

    override suspend fun synchronizeWithStoreAsync(userId: String): Result<List<SubscriptionDto>> {
        return try {
            logger.logInfo("Synchronizing subscriptions with store for user: $userId")

            // Get current subscriptions from store
            val storePurchasesResult = storeService.restorePurchasesAsync(userId)
            if (!storePurchasesResult.isSuccess) {
                return Result.failure("Failed to get store purchases: ${storePurchasesResult.exceptionOrNull()?.message}")
            }

            val storePurchases = storePurchasesResult.getOrThrow()
            val syncedSubscriptions = mutableListOf<SubscriptionDto>()

            for (purchase in storePurchases) {
                // Verify each purchase
                val verificationResult = storeService.verifyPurchaseAsync(
                    purchase.productId,
                    purchase.purchaseToken,
                    purchase.transactionId
                )

                if (verificationResult.isSuccess) {
                    val verification = verificationResult.getOrThrow()

                    // Update or create subscription based on verification
                    val existingResult = subscriptionRepository.getByTransactionIdAsync(purchase.transactionId)

                    if (existingResult.isSuccess && existingResult.getOrNull() != null) {
                        val existing = existingResult.getOrThrow()
                        existing.markAsVerified()
                        verification.expirationDate?.let { existing.updateExpirationDate(it) }
                        subscriptionRepository.updateAsync(existing)
                        syncedSubscriptions.add(existing.toDto())
                    }
                }

                // Add small delay to avoid overwhelming the store API
                delay(100)
            }

            logger.logInfo("Synchronized ${syncedSubscriptions.size} subscriptions for user: $userId")
            Result.success(syncedSubscriptions)

        } catch (ex: Exception) {
            logger.logError("Error synchronizing with store for user: $userId", ex)
            Result.failure("Synchronization failed: ${ex.message}")
        }
    }

    override suspend fun getProductDetailsFromStoreAsync(productIds: List<String>): Result<List<SubscriptionProductDto>> {
        return storeService.getProductDetailsAsync(productIds)
    }

    /**
     * Helper function to calculate expiration date based on product ID
     */
    private fun calculateExpirationDate(productId: String): Instant {
        val now = Clock.System.now()
        return when {
            productId.contains("monthly", ignoreCase = true) -> now.plus(kotlinx.datetime.DateTimeUnit.MONTH * 1)
            productId.contains("yearly", ignoreCase = true) -> now.plus(kotlinx.datetime.DateTimeUnit.YEAR * 1)
            productId.contains("weekly", ignoreCase = true) -> now.plus(kotlinx.datetime.DateTimeUnit.WEEK * 1)
            else -> now.plus(kotlinx.datetime.DateTimeUnit.MONTH * 1) // Default to monthly
        }
    }

    /**
     * Helper function to calculate subscription duration
     */
    private fun calculateSubscriptionDuration(productId: String): kotlinx.datetime.DateTimeUnit {
        return when {
            productId.contains("monthly", ignoreCase = true) -> kotlinx.datetime.DateTimeUnit.MONTH * 1
            productId.contains("yearly", ignoreCase = true) -> kotlinx.datetime.DateTimeUnit.YEAR * 1
            productId.contains("weekly", ignoreCase = true) -> kotlinx.datetime.DateTimeUnit.WEEK * 1
            else -> kotlinx.datetime.DateTimeUnit.MONTH * 1 // Default to monthly
        }
    }
}