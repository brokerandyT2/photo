// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/SubscriptionServiceImpl.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.dto.*
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISubscriptionRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionStoreService
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toDto
import com.x3squaredcircles.pixmap.shared.application.mappers.SubscriptionMapper.toSummaryDto
import com.x3squaredcircles.pixmap.shared.domain.entities.Subscription
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SubscriptionDomainException
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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

            // Check for existing subscription
            val existingSubscriptionResult = subscriptionRepository.getByTransactionIdAsync(transactionId)
            if (existingSubscriptionResult.isSuccess && existingSubscriptionResult.data != null) {
                logger.logWarning("Subscription already exists for transaction: $transactionId")
                return Result.success(
                    SubscriptionPurchaseResponseDto(
                        subscription = existingSubscriptionResult.data!!.toDto(),
                        isNewSubscription = false,
                        purchaseDate = existingSubscriptionResult.data!!.startDate
                    )
                )
            }

            // Create new subscription
            val subscription = Subscription(
                userId = userId,
                productId = productId,
                transactionId = transactionId,
                purchaseToken = purchaseToken,
                status = SubscriptionStatus.ACTIVE,
                startDate = Clock.System.now(),
                expirationDate = verification.expirationDate ?: Clock.System.now().plus(30.days),
                autoRenewing = verification.autoRenewStatus ?: true
            )

            val createResult = subscriptionRepository.createAsync(subscription)
            if (!createResult.isSuccess || createResult.data == null) {
                return Result.failure("Failed to create subscription: ${createResult.errorMessage}")
            }

            logger.logInfo("Successfully created subscription: ${createResult.data!!.id}")
            Result.success(
                SubscriptionPurchaseResponseDto(
                    subscription = createResult.data!!.toDto(),
                    isNewSubscription = true,
                    purchaseDate = createResult.data!!.startDate
                )
            )

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
                    verificationDate = Clock.System.now(),
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

    override suspend fun cancelSubscriptionAsync(
        subscriptionId: Int,
        reason: String?
    ): Result<Boolean> {
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

            subscription.updateStatus(SubscriptionStatus.CANCELLED)

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

    override suspend fun restoreSubscriptionsAsync(userId: String): Result<SubscriptionRestoreDto> {
        return try {
            logger.logInfo("Restoring subscriptions for user: $userId")

            // Get existing subscriptions
            val existingResult = subscriptionRepository.getSubscriptionsByUserIdAsync(userId)
            val existingSubscriptions = if (existingResult.isSuccess) {
                existingResult.data ?: emptyList()
            } else {
                emptyList()
            }

            // Get purchases from store
            val storePurchases = storeService.restorePurchasesAsync(userId)
            if (!storePurchases.isSuccess) {
                return Result.failure("Failed to get purchases from store: ${storePurchases.errorMessage}")
            }

            val purchases = storePurchases.data ?: emptyList()
            var restoredCount = 0
            val restoredSubscriptions = mutableListOf<Subscription>()

            for (purchase in purchases) {
                val existingSubscription = existingSubscriptions.find {
                    it.transactionId == purchase.transactionId || it.purchaseToken == purchase.purchaseToken
                }

                if (existingSubscription == null) {
                    // Create new subscription from purchase
                    val newSubscription = Subscription(
                        userId = userId,
                        productId = purchase.productId,
                        transactionId = purchase.transactionId,
                        purchaseToken = purchase.purchaseToken,
                        status = SubscriptionStatus.ACTIVE,
                        startDate = purchase.purchaseDate,
                        expirationDate = Clock.System.now().plus(30.days),
                        autoRenewing = purchase.autoRenewing
                    )

                    val createResult = subscriptionRepository.createAsync(newSubscription)
                    if (createResult.isSuccess && createResult.data != null) {
                        restoredSubscriptions.add(createResult.data!!)
                        restoredCount++
                    }
                } else {
                    // Update existing subscription if needed
                    existingSubscription.updateStatus(SubscriptionStatus.ACTIVE)
                    val updateResult = subscriptionRepository.updateAsync(existingSubscription)
                    if (updateResult.isSuccess) {
                        restoredSubscriptions.add(existingSubscription)
                        restoredCount++
                    }
                }
            }

            Result.success(
                SubscriptionRestoreDto(
                    userId = userId,
                    restoredSubscriptions = restoredSubscriptions.map { it.toDto() },
                    newSubscriptions = restoredSubscriptions.filter { it.renewalCount == 0 }.map { it.toDto() },
                    updatedSubscriptions = restoredSubscriptions.filter { it.renewalCount > 0 }.map { it.toDto() },
                    totalRestored = restoredCount,
                    restoreDate = Clock.System.now()
                )
            )

        } catch (ex: Exception) {
            logger.logError("Error restoring subscriptions for user: $userId", ex)
            Result.failure("Failed to restore subscriptions: ${ex.message}")
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
            Result.success(subscriptions.toSummaryDto(userId))

        } catch (ex: Exception) {
            logger.logError("Error getting subscription summary for user: $userId", ex)
            Result.failure("Failed to get subscription summary: ${ex.message}")
        }
    }

    override suspend fun getAvailableProductsAsync(): Result<List<SubscriptionProductDto>> {
        return try {
            val storeProducts = storeService.getProductDetailsAsync(listOf("premium_monthly", "premium_yearly"))
            if (!storeProducts.isSuccess) {
                return Result.failure("Failed to get products from store: ${storeProducts.errorMessage}")
            }

            Result.success(storeProducts.data ?: emptyList())

        } catch (ex: Exception) {
            logger.logError("Error getting available products", ex)
            Result.failure("Failed to get products: ${ex.message}")
        }
    }

    override suspend fun hasActiveSubscriptionAsync(userId: String): Result<Boolean> {
        return try {
            val result = subscriptionRepository.getActiveSubscriptionAsync(userId)
            Result.success(result.isSuccess && result.data != null)

        } catch (ex: Exception) {
            logger.logError("Error checking active subscription for user: $userId", ex)
            Result.failure("Failed to check subscription: ${ex.message}")
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
            logger.logInfo("Processing renewal for user: $userId")

            val activeResult = subscriptionRepository.getActiveSubscriptionAsync(userId)
            if (!activeResult.isSuccess || activeResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(0)
            }

            val subscription = activeResult.data!!
            subscription.renew(expirationDate, transactionId)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess || updateResult.data == null) {
                throw SubscriptionDomainException.databaseError("Failed to update subscription", null)
            }

            Result.success(updateResult.data!!.toDto())

        } catch (ex: Exception) {
            logger.logError("Error processing renewal for user: $userId", ex)
            Result.failure("Failed to process renewal: ${ex.message}")
        }
    }

    override suspend fun updateExpirationDateAsync(
        subscriptionId: Int,
        newExpirationDate: Instant
    ): Result<SubscriptionDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!
            subscription.updateExpirationDate(newExpirationDate)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess || updateResult.data == null) {
                throw SubscriptionDomainException.databaseError("Failed to update expiration date", null)
            }

            Result.success(updateResult.data!!.toDto())

        } catch (ex: Exception) {
            logger.logError("Error updating expiration date for subscription: $subscriptionId", ex)
            Result.failure("Failed to update expiration date: ${ex.message}")
        }
    }

    override suspend fun getBillingHistoryAsync(subscriptionId: Int): Result<SubscriptionBillingHistoryDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!

            // For now, return empty billing history - this would be expanded with actual billing events
            Result.success(
                SubscriptionBillingHistoryDto(
                    subscriptionId = subscriptionId,
                    userId = subscription.userId,
                    productId = subscription.productId,
                    billingEvents = emptyList()
                )
            )

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
            // Implementation would filter by date range and user if provided
            // For now, return basic stats
            Result.success(
                SubscriptionStatsDto(
                    totalActiveSubscriptions = 0,
                    totalExpiredSubscriptions = 0,
                    totalCancelledSubscriptions = 0,
                    totalRenewals = 0,
                    subscriptionsByProduct = emptyMap(),
                    subscriptionsByStatus = emptyMap(),
                    averageSubscriptionDuration = 0.0,
                    churnRate = 0.0,
                    renewalRate = 0.0
                )
            )

        } catch (ex: Exception) {
            logger.logError("Error getting subscription stats", ex)
            Result.failure("Failed to get stats: ${ex.message}")
        }
    }

    override suspend fun validateWithStoreAsync(subscription: Subscription): Result<Boolean> {
        return try {
            val verificationResult = storeService.verifyPurchaseAsync(
                subscription.productId,
                subscription.purchaseToken,
                subscription.transactionId
            )

            if (verificationResult.isSuccess && verificationResult.data != null) {
                val verification = verificationResult.data!!
                if (!verification.isValid) {
                    subscription.updateStatus(SubscriptionStatus.EXPIRED)
                    subscriptionRepository.updateAsync(subscription)
                }
                Result.success(verification.isValid)
            } else {
                Result.success(false)
            }

        } catch (ex: Exception) {
            logger.logError("Error validating subscription with store", ex)
            Result.failure("Validation failed: ${ex.message}")
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
            Result.failure("Failed to get subscriptions: ${ex.message}")
        }
    }

    override suspend fun getExpiredSubscriptionsAsync(includeGracePeriod: Boolean): Result<List<SubscriptionDto>> {
        return try {
            val result = subscriptionRepository.getExpiredSubscriptionsAsync()
            if (!result.isSuccess) {
                return Result.success(emptyList())
            }

            val subscriptions = result.data ?: emptyList()
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
            val expiredResult = getExpiredSubscriptionsAsync(false)
            if (!expiredResult.isSuccess) {
                return Result.failure("Failed to get expired subscriptions: ${expiredResult.errorMessage}")
            }

            val expiredSubscriptions = expiredResult.data ?: emptyList()
            var processedCount = 0

            for (subscriptionDto in expiredSubscriptions) {
                val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionDto.id)
                if (subscriptionResult.isSuccess && subscriptionResult.data != null) {
                    val subscription = subscriptionResult.data!!
                    subscription.updateStatus(SubscriptionStatus.EXPIRED)
                    subscriptionRepository.updateAsync(subscription)
                    processedCount++
                }
            }

            Result.success(processedCount)

        } catch (ex: Exception) {
            logger.logError("Error processing expired subscriptions", ex)
            Result.failure("Failed to process expired subscriptions: ${ex.message}")
        }
    }

    override suspend fun updatePurchaseTokenAsync(
        subscriptionId: Int,
        newPurchaseToken: String
    ): Result<SubscriptionDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!
            subscription.updatePurchaseToken(newPurchaseToken)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess || updateResult.data == null) {
                throw SubscriptionDomainException.databaseError("Failed to update purchase token", null)
            }

            Result.success(updateResult.data!!.toDto())

        } catch (ex: Exception) {
            logger.logError("Error updating purchase token for subscription: $subscriptionId", ex)
            Result.failure("Failed to update purchase token: ${ex.message}")
        }
    }

    override suspend fun checkEntitlementsAsync(userId: String): Result<Map<String, Boolean>> {
        return try {
            val activeResult = getActiveSubscriptionAsync(userId)
            if (!activeResult.isSuccess) {
                return Result.success(emptyMap())
            }

            val hasActiveSubscription = activeResult.data != null
            val entitlements = mapOf(
                "premium_features" to hasActiveSubscription,
                "ad_free" to hasActiveSubscription,
                "unlimited_storage" to hasActiveSubscription
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
            if (!result.isSuccess) {
                return Result.success(null)
            }

            Result.success(result.data?.toDto())

        } catch (ex: Exception) {
            logger.logError("Error getting subscription by transaction ID: $transactionId", ex)
            Result.failure("Failed to get subscription: ${ex.message}")
        }
    }

    override suspend fun getByPurchaseTokenAsync(purchaseToken: String): Result<SubscriptionDto?> {
        return try {
            val result = subscriptionRepository.getByPurchaseTokenAsync(purchaseToken)
            if (!result.isSuccess) {
                return Result.success(null)
            }

            Result.success(result.data?.toDto())

        } catch (ex: Exception) {
            logger.logError("Error getting subscription by purchase token", ex)
            Result.failure("Failed to get subscription: ${ex.message}")
        }
    }

    override suspend fun handleGracePeriodAsync(subscriptionId: Int): Result<SubscriptionDto> {
        return try {
            val subscriptionResult = subscriptionRepository.getByIdAsync(subscriptionId)
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!
            subscription.updateStatus(SubscriptionStatus.GRACE_PERIOD)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess || updateResult.data == null) {
                throw SubscriptionDomainException.databaseError("Failed to enter grace period", null)
            }

            Result.success(updateResult.data!!.toDto())

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
            if (!subscriptionResult.isSuccess || subscriptionResult.data == null) {
                throw SubscriptionDomainException.subscriptionNotFound(subscriptionId)
            }

            val subscription = subscriptionResult.data!!
            val status = SubscriptionStatus.valueOf(newState.uppercase())
            subscription.updateStatus(status)

            val updateResult = subscriptionRepository.updateAsync(subscription)
            if (!updateResult.isSuccess || updateResult.data == null) {
                throw SubscriptionDomainException.databaseError("Failed to process state change", null)
            }

            Result.success(updateResult.data!!.toDto())

        } catch (ex: Exception) {
            logger.logError("Error processing state change for subscription: $subscriptionId", ex)
            Result.failure("Failed to process state change: ${ex.message}")
        }
    }

    override suspend fun synchronizeWithStoreAsync(userId: String): Result<List<SubscriptionDto>> {
        return try {
            logger.logInfo("Synchronizing subscriptions with store for user: $userId")

            val storePurchases = storeService.restorePurchasesAsync(userId)
            if (!storePurchases.isSuccess) {
                return Result.failure("Failed to get purchases from store: ${storePurchases.errorMessage}")
            }

            val purchases = storePurchases.data ?: emptyList()
            val synchronizedSubscriptions = mutableListOf<Subscription>()

            for (purchase in purchases) {
                val existingResult = subscriptionRepository.getByTransactionIdAsync(purchase.transactionId)

                if (existingResult.isSuccess && existingResult.data != null) {
                    // Update existing subscription
                    val existing = existingResult.data!!
                    existing.updateStatus(SubscriptionStatus.ACTIVE)
                    existing.updateExpirationDate(Clock.System.now().plus(30.days))

                    val updateResult = subscriptionRepository.updateAsync(existing)
                    if (updateResult.isSuccess && updateResult.data != null) {
                        synchronizedSubscriptions.add(updateResult.data!!)
                    }
                } else {
                    // Create new subscription
                    // Get the current Instant
                    val now: Instant = Clock.System.now()

// Create a DateTimePeriod representing 30 days

                    val thirtyDaysFromNow: Instant = now.plus(30.days)

                    val newSubscription = Subscription(
                        userId = userId,
                        productId = purchase.productId,
                        transactionId = purchase.transactionId,
                        purchaseToken = purchase.purchaseToken,
                        status = SubscriptionStatus.ACTIVE,
                        startDate = purchase.purchaseDate,
                        expirationDate = thirtyDaysFromNow,
                        autoRenewing = purchase.autoRenewing
                    )

                    val createResult = subscriptionRepository.createAsync(newSubscription)
                    if (createResult.isSuccess && createResult.data != null) {
                        synchronizedSubscriptions.add(createResult.data!!)
                    }
                }
            }

            Result.success(synchronizedSubscriptions.map { it.toDto() })

        } catch (ex: Exception) {
            logger.logError("Error synchronizing subscriptions for user: $userId", ex)
            Result.failure("Failed to synchronize subscriptions: ${ex.message}")
        }
    }

    override suspend fun getProductDetailsFromStoreAsync(productIds: List<String>): Result<List<SubscriptionProductDto>> {
        return try {
            val result = storeService.getProductDetailsAsync(productIds)
            Result.success(result.data ?: emptyList())

        } catch (ex: Exception) {
            logger.logError("Error getting product details from store", ex)
            Result.failure("Failed to get product details: ${ex.message}")
        }
    }
}