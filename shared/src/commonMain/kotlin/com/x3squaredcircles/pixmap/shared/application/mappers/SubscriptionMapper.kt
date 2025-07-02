//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/mappers/SubscriptionMapper.kt
package com.x3squaredcircles.pixmap.shared.application.mappers

import com.x3squaredcircles.pixmap.shared.application.dto.*
import com.x3squaredcircles.pixmap.shared.domain.entities.*
import kotlinx.datetime.Clock

/**
 * Mapper object for subscription entity transformations
 */
object SubscriptionMapper {

    /**
     * Maps Subscription entity to SubscriptionDto
     */
    fun Subscription.toDto(): SubscriptionDto {
        return SubscriptionDto(
            id = this.id,
            userId = this.userId,
            productId = this.productId,
            transactionId = this.transactionId,
            purchaseToken = this.purchaseToken,
            status = this.status,
            startDate = this.startDate,
            expirationDate = this.expirationDate,
            autoRenewing = this.autoRenewing,
            lastVerified = this.lastVerified,
            cancelledAt = this.cancelledAt,
            renewalCount = this.renewalCount,
            isActive = this.isActive(),
            isExpired = this.isExpired(),
            isInGracePeriod = this.isInGracePeriod(),
            needsVerification = this.needsVerification(),
            daysUntilExpiration = this.getDaysUntilExpiration()
        )
    }

    /**
     * Maps SubscriptionProduct entity to SubscriptionProductDto
     */
    fun SubscriptionProduct.toDto(): SubscriptionProductDto {
        return SubscriptionProductDto(
            productId = this.productId,
            name = this.name,
            description = this.description,
            price = this.price,
            currency = this.currency,
            billingPeriod = this.billingPeriod,
            features = this.features,
            isActive = true,
            sortOrder = 0
        )
    }

    /**
     * Maps List of Subscriptions to SubscriptionSummaryDto
     */
    fun List<Subscription>.toSummaryDto(userId: String): SubscriptionSummaryDto {
        val activeSubscription = this.firstOrNull { it.isActive() }
        val totalRenewals = this.sumOf { it.renewalCount }
        val firstSubscription = this.minByOrNull { it.startDate }
        val lastRenewal = this.filter { it.renewalCount > 0 }
            .maxByOrNull { it.startDate }

        return SubscriptionSummaryDto(
            userId = userId,
            hasActiveSubscription = activeSubscription != null,
            activeSubscription = activeSubscription?.toDto(),
            totalSubscriptions = this.size,
            totalRenewals = totalRenewals,
            firstSubscriptionDate = firstSubscription?.startDate,
            lastRenewalDate = lastRenewal?.startDate
        )
    }

    /**
     * Maps collection of subscriptions to SubscriptionStatsDto
     */
    fun List<Subscription>.toStatsDto(): SubscriptionStatsDto {
        val activeCount = this.count { it.isActive() }
        val expiredCount = this.count { it.isExpired() }
        val cancelledCount = this.count { it.status == SubscriptionStatus.CANCELLED }
        val totalRenewals = this.sumOf { it.renewalCount }

        val subscriptionsByProduct = this.groupBy { it.productId }
            .mapValues { it.value.size }

        val subscriptionsByStatus = this.groupBy { it.status }
            .mapValues { it.value.size }

        val averageDuration = if (this.isNotEmpty()) {
            this.map { subscription ->
                val endDate = subscription.cancelledAt ?: subscription.expirationDate
                endDate.epochSeconds - subscription.startDate.epochSeconds
            }.average() / 86400.0 // Convert to days
        } else 0.0

        val churnRate = if (this.isNotEmpty()) {
            cancelledCount.toDouble() / this.size * 100
        } else 0.0

        val renewalRate = if (this.isNotEmpty()) {
            val subscriptionsEligibleForRenewal = this.count {
                it.renewalCount > 0 || it.status == SubscriptionStatus.ACTIVE
            }
            if (subscriptionsEligibleForRenewal > 0) {
                totalRenewals.toDouble() / subscriptionsEligibleForRenewal * 100
            } else 0.0
        } else 0.0

        return SubscriptionStatsDto(
            totalActiveSubscriptions = activeCount,
            totalExpiredSubscriptions = expiredCount,
            totalCancelledSubscriptions = cancelledCount,
            totalRenewals = totalRenewals,
            subscriptionsByProduct = subscriptionsByProduct,
            subscriptionsByStatus = subscriptionsByStatus,
            averageSubscriptionDuration = averageDuration,
            churnRate = churnRate,
            renewalRate = renewalRate
        )
    }

    /**
     * Maps to SubscriptionPurchaseResponseDto
     */
    fun Subscription.toPurchaseResponseDto(
        isNewSubscription: Boolean,
        previousSubscription: Subscription? = null
    ): SubscriptionPurchaseResponseDto {
        return SubscriptionPurchaseResponseDto(
            subscription = this.toDto(),
            isNewSubscription = isNewSubscription,
            previousSubscription = previousSubscription?.toDto(),
            purchaseDate = this.startDate,
            isSuccessful = true,
            errorMessage = null
        )
    }

    /**
     * Maps to SubscriptionVerificationDto
     */
    fun Subscription.toVerificationDto(
        isValid: Boolean,
        errorMessage: String? = null
    ): SubscriptionVerificationDto {
        return SubscriptionVerificationDto(
            subscriptionId = this.id,
            isValid = isValid,
            verificationDate = Clock.System.now(),
            originalPurchaseDate = this.startDate,
            latestReceiptInfo = this.purchaseToken,
            expirationDate = this.expirationDate,
            autoRenewStatus = this.autoRenewing,
            errorMessage = errorMessage
        )
    }

    /**
     * Maps List of Subscriptions to SubscriptionRestoreDto
     */
    fun List<Subscription>.toRestoreDto(
        userId: String,
        restoredCount: Int,
        newCount: Int,
        updatedCount: Int
    ): SubscriptionRestoreDto {
        return SubscriptionRestoreDto(
            userId = userId,
            restoredSubscriptions = this.map { it.toDto() },
            newSubscriptions = this.filter { it.renewalCount == 0 }.map { it.toDto() },
            updatedSubscriptions = this.filter { it.renewalCount > 0 }.map { it.toDto() },
            totalRestored = restoredCount,
            restoreDate = Clock.System.now(),
            isSuccessful = true,
            errorMessage = null
        )
    }

    /**
     * Maps to BillingEventDto
     */
    fun createBillingEvent(
        subscription: Subscription,
        eventType: BillingEventType,
        description: String,
        amount: String = "0.00",
        currency: String = "USD"
    ): BillingEventDto {
        return BillingEventDto(
            id = 0, // Will be set by repository
            subscriptionId = subscription.id,
            eventType = eventType,
            transactionId = subscription.transactionId,
            amount = amount,
            currency = currency,
            eventDate = Clock.System.now(),
            description = description,
            isSuccessful = true
        )
    }

    /**
     * Maps to SubscriptionBillingHistoryDto
     */
    fun List<BillingEventDto>.toBillingHistoryDto(
        subscriptionId: Int,
        userId: String,
        productId: String
    ): SubscriptionBillingHistoryDto {
        return SubscriptionBillingHistoryDto(
            subscriptionId = subscriptionId,
            userId = userId,
            productId = productId,
            billingEvents = this.sortedByDescending { it.eventDate }
        )
    }

    /**
     * Maps SubscriptionDto back to entity (for updates)
     */
    fun SubscriptionDto.toEntity(): Subscription {
        return Subscription(
            userId = this.userId,
            productId = this.productId,
            transactionId = this.transactionId,
            purchaseToken = this.purchaseToken,
            status = this.status,
            startDate = this.startDate,
            expirationDate = this.expirationDate,
            autoRenewing = this.autoRenewing
        ).apply {
            // Set ID and other properties that are managed internally
            if (this@toEntity.id > 0) {
                // Reflection or internal setter would be needed here
                // This is typically handled by the repository layer
            }
        }
    }

    /**
     * Extension function to check if subscription needs attention
     */
    fun Subscription.needsAttention(): Boolean {
        return this.needsVerification() ||
                this.isExpired() ||
                (this.getDaysUntilExpiration() <= 7 && this.isActive())
    }

    /**
     * Extension function to get subscription display status
     */
    fun Subscription.getDisplayStatus(): String {
        return when {
            this.isActive() -> "Active"
            this.isExpired() -> "Expired"
            this.isInGracePeriod() -> "Grace Period"
            this.status == SubscriptionStatus.CANCELLED -> "Cancelled"
            this.status == SubscriptionStatus.PENDING -> "Pending"
            this.status == SubscriptionStatus.ON_HOLD -> "On Hold"
            this.status == SubscriptionStatus.PAUSED -> "Paused"
            else -> "Unknown"
        }
    }

    /**
     * Extension function to get subscription priority for sorting
     */
    fun Subscription.getPriority(): Int {
        return when {
            this.isActive() -> 1
            this.isInGracePeriod() -> 2
            this.status == SubscriptionStatus.PENDING -> 3
            this.isExpired() -> 4
            this.status == SubscriptionStatus.CANCELLED -> 5
            else -> 6
        }
    }
}