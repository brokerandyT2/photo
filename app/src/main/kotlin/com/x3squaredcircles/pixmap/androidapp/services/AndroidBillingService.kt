//app/src/main/kotlin/com/x3squaredcircles/pixmap/androidapp/services/AndroidBillingService.kt
package com.x3squaredcircles.pixmap.androidapp.services

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.*
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ISubscriptionStoreService
import com.x3squaredcircles.pixmap.shared.domain.entities.BillingPeriod
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.resume

/**
 * Android-specific implementation of subscription store service using Google Play Billing
 */
class AndroidBillingService(
    private val context: Context
) : ISubscriptionStoreService, PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private var isServiceConnected = false
    private var connectionCallback: ((Boolean) -> Unit)? = null

    companion object {
        private const val SKUS_FETCH_TIMEOUT = 5000L
    }

    init {
        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
    }

    override suspend fun verifyPurchaseAsync(
        productId: String,
        purchaseToken: String,
        transactionId: String
    ): Result<SubscriptionVerificationDto> {
        return try {
            if (!ensureConnection()) {
                return Result.failure("Billing service not available")
            }

            val purchases = queryPurchases()
            val purchase = purchases.find { it.purchaseToken == purchaseToken }

            if (purchase == null) {
                return Result.success(
                    SubscriptionVerificationDto(
                        subscriptionId = 0,
                        isValid = false,
                        verificationDate = Clock.System.now(),
                        errorMessage = "Purchase not found"
                    )
                )
            }

            val isValid = purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.isAcknowledged

            Result.success(
                SubscriptionVerificationDto(
                    subscriptionId = 0,
                    isValid = isValid,
                    verificationDate = Clock.System.now(),
                    originalPurchaseDate = Instant.fromEpochMilliseconds(purchase.purchaseTime),
                    latestReceiptInfo = purchase.originalJson,
                    expirationDate = null, // Would need to be determined from product details
                    autoRenewStatus = purchase.isAutoRenewing
                )
            )
        } catch (e: Exception) {
            Result.failure("Purchase verification failed: ${e.message}")
        }
    }

    override suspend fun getProductDetailsAsync(productIds: List<String>): Result<List<SubscriptionProductDto>> {
        return try {
            if (!ensureConnection()) {
                return Result.failure("Billing service not available")
            }

            val productList = productIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val productDetails = suspendCancellableCoroutine { continuation ->
                billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        continuation.resume(productDetailsList ?: emptyList())
                    } else {
                        continuation.resume(emptyList())
                    }
                }
            }

            val subscriptionProducts = productDetails.map { details ->
                val subscriptionOfferDetails = details.subscriptionOfferDetails?.firstOrNull()
                val pricingPhase = subscriptionOfferDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()

                SubscriptionProductDto(
                    productId = details.productId,
                    name = details.name,
                    description = details.description,
                    price = pricingPhase?.formattedPrice ?: "Unknown",
                    currency = pricingPhase?.priceCurrencyCode ?: "USD",
                    billingPeriod = mapBillingPeriod(pricingPhase?.billingPeriod),
                    features = emptyList(), // Would need to be stored elsewhere
                    isActive = true
                )
            }

            Result.success(subscriptionProducts)
        } catch (e: Exception) {
            Result.failure("Failed to get product details: ${e.message}")
        }
    }

    override suspend fun restorePurchasesAsync(userId: String): Result<List<SubscriptionPurchaseRequestDto>> {
        return try {
            if (!ensureConnection()) {
                return Result.failure("Billing service not available")
            }

            val purchases = queryPurchases()
            val subscriptionPurchases = purchases.map { purchase ->
                SubscriptionPurchaseRequestDto(
                    userId = userId,
                    productId = purchase.products.firstOrNull() ?: "",
                    purchaseToken = purchase.purchaseToken,
                    transactionId = purchase.orderId ?: "",
                    purchaseDate = Instant.fromEpochMilliseconds(purchase.purchaseTime),
                    autoRenewing = purchase.isAutoRenewing
                )
            }

            Result.success(subscriptionPurchases)
        } catch (e: Exception) {
            Result.failure("Failed to restore purchases: ${e.message}")
        }
    }

    override suspend fun acknowledgePurchaseAsync(purchaseToken: String): Result<Boolean> {
        return try {
            if (!ensureConnection()) {
                return Result.failure("Billing service not available")
            }

            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()

            val result = suspendCancellableCoroutine { continuation ->
                billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
                    continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure("Failed to acknowledge purchase: ${e.message}")
        }
    }

    override suspend fun consumePurchaseAsync(purchaseToken: String): Result<Boolean> {
        return try {
            if (!ensureConnection()) {
                return Result.failure("Billing service not available")
            }

            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()

            val result = suspendCancellableCoroutine { continuation ->
                billingClient?.consumeAsync(consumeParams) { billingResult, _ ->
                    continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure("Failed to consume purchase: ${e.message}")
        }
    }

    override suspend fun getPurchaseHistoryAsync(userId: String): Result<List<BillingEventDto>> {
        return try {
            if (!ensureConnection()) {
                return Result.failure("Billing service not available")
            }

            val params = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val purchaseHistory = suspendCancellableCoroutine { continuation ->
                billingClient?.queryPurchaseHistoryAsync(params) { billingResult, purchaseHistoryList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        continuation.resume(purchaseHistoryList ?: emptyList())
                    } else {
                        continuation.resume(emptyList())
                    }
                }
            }

            val billingEvents = purchaseHistory.mapIndexed { index, historyRecord ->
                BillingEventDto(
                    id = index,
                    subscriptionId = 0, // Would need to be resolved
                    eventType = BillingEventType.PURCHASE,
                    transactionId = historyRecord.purchaseToken,
                    amount = "0.00", // Not available in history
                    currency = "USD",
                    eventDate = Instant.fromEpochMilliseconds(historyRecord.purchaseTime),
                    description = "Purchase: ${historyRecord.products.joinToString(", ")}",
                    isSuccessful = true
                )
            }

            Result.success(billingEvents)
        } catch (e: Exception) {
            Result.failure("Failed to get purchase history: ${e.message}")
        }
    }

    override suspend fun isStoreAvailableAsync(): Result<Boolean> {
        return try {
            val isAvailable = ensureConnection()
            Result.success(isAvailable)
        } catch (e: Exception) {
            Result.success(false)
        }
    }

    override suspend fun startPurchaseFlowAsync(productId: String, userId: String): Result<String> {
        return try {
            if (!ensureConnection()) {
                return Result.failure("Billing service not available")
            }

            // This would typically be called from an Activity context
            // For now, we'll return a placeholder indicating the flow should be started
            Result.success("PURCHASE_FLOW_READY")
        } catch (e: Exception) {
            Result.failure("Failed to start purchase flow: ${e.message}")
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String): Boolean {
        if (!ensureConnection()) return false

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                productDetailsList?.isNotEmpty() == true) {

                val productDetails = productDetailsList.first()
                val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                if (offerToken != null) {
                    val productDetailsParamsList = listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )

                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build()

                    billingClient?.launchBillingFlow(activity, billingFlowParams)
                }
            }
        }

        return true
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle user cancellation
        } else {
            // Handle other error cases
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
                    // Handle acknowledgment result
                }
            }
        }
    }

    private suspend fun ensureConnection(): Boolean {
        if (isServiceConnected) return true

        return suspendCancellableCoroutine { continuation ->
            connectionCallback = { connected ->
                continuation.resume(connected)
                connectionCallback = null
            }

            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    isServiceConnected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    connectionCallback?.invoke(isServiceConnected)
                }

                override fun onBillingServiceDisconnected() {
                    isServiceConnected = false
                    connectionCallback?.invoke(false)
                }
            })
        }
    }

    private suspend fun queryPurchases(): List<Purchase> {
        if (!ensureConnection()) return emptyList()

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchasesList ?: emptyList())
                } else {
                    continuation.resume(emptyList())
                }
            }
        }
    }

    private fun mapBillingPeriod(billingPeriod: String?): BillingPeriod {
        return when (billingPeriod) {
            "P1M" -> BillingPeriod.MONTHLY
            "P1Y" -> BillingPeriod.YEARLY
            "P1W" -> BillingPeriod.WEEKLY
            else -> BillingPeriod.MONTHLY
        }
    }

    fun disconnect() {
        billingClient?.endConnection()
        isServiceConnected = false
    }
}