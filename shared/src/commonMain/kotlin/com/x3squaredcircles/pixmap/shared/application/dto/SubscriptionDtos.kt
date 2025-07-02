//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/dto/SubscriptionDtos.kt
package com.x3squaredcircles.pixmap.shared.application.dto

import com.x3squaredcircles.pixmap.shared.domain.entities.BillingPeriod
import com.x3squaredcircles.pixmap.shared.domain.entities.SubscriptionStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Data transfer object for subscription
 */
@Serializable
data class SubscriptionDto(
    val id: Int,
    val userId: String,
    val productId: String,
    val transactionId: String,
    val purchaseToken: String,
    val status: SubscriptionStatus,
    val startDate: Instant,
    val expirationDate: Instant,
    val autoRenewing: Boolean,
    val lastVerified: Instant? = null,
    val cancelledAt: Instant? = null,
    val renewalCount: Int = 0,
    val isActive: Boolean = false,
    val isExpired: Boolean = false,
    val isInGracePeriod: Boolean = false,
    val needsVerification: Boolean = false,
    val daysUntilExpiration: Int = 0
)

/**
 * Data transfer object for subscription product
 */
@Serializable
data class SubscriptionProductDto(
    val productId: String,
    val name: String,
    val description: String,
    val price: String,
    val currency: String,
    val billingPeriod: BillingPeriod,
    val features: List<String> = emptyList(),
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)

/**
 * Data transfer object for subscription summary
 */
@Serializable
data class SubscriptionSummaryDto(
    val userId: String,
    val hasActiveSubscription: Boolean,
    val activeSubscription: SubscriptionDto? = null,
    val totalSubscriptions: Int = 0,
    val totalRenewals: Int = 0,
    val firstSubscriptionDate: Instant? = null,
    val lastRenewalDate: Instant? = null
)

/**
 * Data transfer object for subscription statistics
 */
@Serializable
data class SubscriptionStatsDto(
    val totalActiveSubscriptions: Int = 0,
    val totalExpiredSubscriptions: Int = 0,
    val totalCancelledSubscriptions: Int = 0,
    val totalRenewals: Int = 0,
    val subscriptionsByProduct: Map<String, Int> = emptyMap(),
    val subscriptionsByStatus: Map<SubscriptionStatus, Int> = emptyMap(),
    val averageSubscriptionDuration: Double = 0.0,
    val churnRate: Double = 0.0,
    val renewalRate: Double = 0.0
)

/**
 * Data transfer object for subscription billing history
 */
@Serializable
data class SubscriptionBillingHistoryDto(
    val subscriptionId: Int,
    val userId: String,
    val productId: String,
    val billingEvents: List<BillingEventDto> = emptyList()
)

/**
 * Data transfer object for billing event
 */
@Serializable
data class BillingEventDto(
    val id: Int,
    val subscriptionId: Int,
    val eventType: BillingEventType,
    val transactionId: String,
    val amount: String,
    val currency: String,
    val eventDate: Instant,
    val description: String,
    val isSuccessful: Boolean = true
)

/**
 * Billing event types
 */
@Serializable
enum class BillingEventType {
    PURCHASE,
    RENEWAL,
    CANCELLATION,
    REFUND,
    UPGRADE,
    DOWNGRADE,
    GRACE_PERIOD_START,
    GRACE_PERIOD_END,
    VERIFICATION,
    RESTORE
}

/**
 * Data transfer object for subscription verification result
 */
@Serializable
data class SubscriptionVerificationDto(
    val subscriptionId: Int,
    val isValid: Boolean,
    val verificationDate: Instant,
    val originalPurchaseDate: Instant? = null,
    val latestReceiptInfo: String? = null,
    val expirationDate: Instant? = null,
    val autoRenewStatus: Boolean? = null,
    val errorMessage: String? = null
)

/**
 * Data transfer object for subscription restore result
 */
@Serializable
data class SubscriptionRestoreDto(
    val userId: String,
    val restoredSubscriptions: List<SubscriptionDto> = emptyList(),
    val newSubscriptions: List<SubscriptionDto> = emptyList(),
    val updatedSubscriptions: List<SubscriptionDto> = emptyList(),
    val totalRestored: Int = 0,
    val restoreDate: Instant,
    val isSuccessful: Boolean = true,
    val errorMessage: String? = null
)

/**
 * Data transfer object for subscription purchase request
 */
@Serializable
data class SubscriptionPurchaseRequestDto(
    val userId: String,
    val productId: String,
    val purchaseToken: String,
    val transactionId: String,
    val purchaseDate: Instant,
    val autoRenewing: Boolean = true
)

/**
 * Data transfer object for subscription purchase response
 */
@Serializable
data class SubscriptionPurchaseResponseDto(
    val subscription: SubscriptionDto,
    val isNewSubscription: Boolean,
    val previousSubscription: SubscriptionDto? = null,
    val purchaseDate: Instant,
    val isSuccessful: Boolean = true,
    val errorMessage: String? = null
)