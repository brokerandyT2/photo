//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/domain/entities/PhotographyEntities.kt
package com.x3squaredcircles.pixmap.photography.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.math.sqrt
import kotlin.math.atan
import kotlin.math.PI

/**
 * Photography domain entities for KMM
 * Converted from C# entities in Photography.Domain
 */

@Serializable
enum class MountType {
    CanonEF,
    CanonEFM,
    CanonRF,
    NikonF,
    NikonZ,
    SonyE,
    SonyFE,
    PentaxK,
    MicroFourThirds,
    FujifilmX,
    Other
}

@Serializable
enum class LensType {
    PRIME,
    ZOOM,
    MACRO,
    FISHEYE,
    TILT_SHIFT
}

@Serializable
data class CameraBody(
    val name: String,
    val sensorType: String, // "Full Frame", "Crop", "Micro Four Thirds", "Medium Format"
    val sensorWidth: Double,
    val sensorHeight: Double,
    val mountType: MountType,
    val isUserCreated: Boolean = false,
    val manufacturer: String = "",
    val model: String = "",
    val cropFactor: Double = 1.0
) : Entity() {

    init {
        require(name.isNotBlank()) { "Camera name cannot be blank" }
        require(sensorWidth > 0) { "Sensor width must be positive" }
        require(sensorHeight > 0) { "Sensor height must be positive" }
    }

    companion object {
        fun create(
            name: String,
            sensorType: String,
            sensorWidth: Double,
            sensorHeight: Double,
            mountType: MountType,
            isUserCreated: Boolean = false
        ): CameraBody {
            val cropFactor = when (sensorType.lowercase()) {
                "full frame" -> 1.0
                "crop" -> 1.6 // Canon APS-C
                "micro four thirds" -> 2.0
                "medium format" -> 0.79
                else -> 1.0
            }

            return CameraBody(
                name = name,
                sensorType = sensorType,
                sensorWidth = sensorWidth,
                sensorHeight = sensorHeight,
                mountType = mountType,
                isUserCreated = isUserCreated,
                cropFactor = cropFactor
            )
        }
    }

    fun calculateFOV(focalLength: Double): Double {
        val diagonalMM = sqrt(sensorWidth * sensorWidth + sensorHeight * sensorHeight)
        return 2 * atan(diagonalMM / (2 * focalLength)) * 180 / PI
    }

    fun getEffectiveFocalLength(focalLength: Double): Double {
        return focalLength * cropFactor
    }

    fun updateManufacturerInfo(manufacturer: String, model: String): CameraBody {
        return copy(manufacturer = manufacturer, model = model)
    }
}

@Serializable
data class Lens(
    val name: String,
    val manufacturer: String,
    val mountType: MountType,
    val lensType: LensType,
    val minMM: Double,
    val maxMM: Double,
    val maxAperture: String,
    val isUserCreated: Boolean = false,
    val notes: String = ""
) : Entity() {

    init {
        require(name.isNotBlank()) { "Lens name cannot be blank" }
        require(manufacturer.isNotBlank()) { "Manufacturer cannot be blank" }
        require(minMM > 0) { "Minimum focal length must be positive" }
        require(maxMM >= minMM) { "Maximum focal length must be >= minimum focal length" }
        require(maxAperture.isNotBlank()) { "Max aperture cannot be blank" }
    }

    companion object {
        fun createPrimeLens(
            name: String,
            manufacturer: String,
            mountType: MountType,
            focalLength: Double,
            maxAperture: String,
            isUserCreated: Boolean = false
        ): Lens {
            return Lens(
                name = name,
                manufacturer = manufacturer,
                mountType = mountType,
                lensType = LensType.PRIME,
                minMM = focalLength,
                maxMM = focalLength,
                maxAperture = maxAperture,
                isUserCreated = isUserCreated
            )
        }

        fun createZoomLens(
            name: String,
            manufacturer: String,
            mountType: MountType,
            minMM: Double,
            maxMM: Double,
            maxAperture: String,
            isUserCreated: Boolean = false
        ): Lens {
            return Lens(
                name = name,
                manufacturer = manufacturer,
                mountType = mountType,
                lensType = LensType.ZOOM,
                minMM = minMM,
                maxMM = maxMM,
                maxAperture = maxAperture,
                isUserCreated = isUserCreated
            )
        }
    }

    fun isPrimeLens(): Boolean = minMM == maxMM

    fun isZoomLens(): Boolean = minMM != maxMM

    fun getZoomRatio(): Double = if (isZoomLens()) maxMM / minMM else 1.0

    fun updateNotes(notes: String): Lens {
        return copy(notes = notes)
    }
}

@Serializable
data class LensCameraCompatibility(
    val lensId: Int,
    val cameraBodyId: Int,
    val isNativeMount: Boolean,
    val requiresAdapter: Boolean = false,
    val adapterName: String? = null,
    val autofocusSupported: Boolean = true,
    val imageStabilizationSupported: Boolean = false,
    val notes: String = ""
) : Entity() {

    init {
        require(lensId > 0) { "LensId must be greater than 0" }
        require(cameraBodyId > 0) { "CameraBodyId must be greater than 0" }
    }

    companion object {
        fun createNativeMount(lensId: Int, cameraBodyId: Int): LensCameraCompatibility {
            return LensCameraCompatibility(
                lensId = lensId,
                cameraBodyId = cameraBodyId,
                isNativeMount = true,
                requiresAdapter = false
            )
        }

        fun createWithAdapter(
            lensId: Int,
            cameraBodyId: Int,
            adapterName: String,
            autofocusSupported: Boolean = false
        ): LensCameraCompatibility {
            return LensCameraCompatibility(
                lensId = lensId,
                cameraBodyId = cameraBodyId,
                isNativeMount = false,
                requiresAdapter = true,
                adapterName = adapterName,
                autofocusSupported = autofocusSupported
            )
        }
    }

    fun getCompatibilityDescription(): String {
        return when {
            isNativeMount -> "Native mount compatibility"
            requiresAdapter && adapterName != null -> "Compatible with $adapterName adapter"
            requiresAdapter -> "Requires adapter"
            else -> "Unknown compatibility"
        }
    }
}

@Serializable
data class UserCameraBody(
    val userId: String,
    val cameraBodyId: Int,
    val isFavorite: Boolean = false,
    val dateSaved: Instant = Clock.System.now(),
    val customName: String? = null,
    val notes: String = ""
) : Entity() {

    init {
        require(userId.isNotBlank()) { "UserId cannot be blank" }
        require(cameraBodyId > 0) { "CameraBodyId must be greater than 0" }
    }

    companion object {
        fun create(userId: String, cameraBodyId: Int): UserCameraBody {
            return UserCameraBody(
                userId = userId,
                cameraBodyId = cameraBodyId
            )
        }
    }

    fun markAsFavorite(): UserCameraBody {
        return copy(isFavorite = true)
    }

    fun removeFavorite(): UserCameraBody {
        return copy(isFavorite = false)
    }

    fun updateCustomName(customName: String?): UserCameraBody {
        return copy(customName = customName)
    }

    fun updateNotes(notes: String): UserCameraBody {
        return copy(notes = notes)
    }
}

@Serializable
data class PhoneCameraProfile(
    val phoneModel: String,
    val mainLensFocalLength: Double,
    val mainLensFOV: Double,
    val ultraWideFocalLength: Double? = null,
    val telephotoFocalLength: Double? = null,
    val dateCalibrated: Instant = Clock.System.now(),
    val isActive: Boolean = true
) : Entity() {

    init {
        require(phoneModel.isNotBlank()) { "Phone model cannot be blank" }
        require(mainLensFocalLength > 0) { "Main lens focal length must be positive" }
        require(mainLensFOV > 0) { "Main lens FOV must be positive" }
    }

    companion object {
        fun create(
            phoneModel: String,
            mainLensFocalLength: Double,
            mainLensFOV: Double
        ): PhoneCameraProfile {
            return PhoneCameraProfile(
                phoneModel = phoneModel,
                mainLensFocalLength = mainLensFocalLength,
                mainLensFOV = mainLensFOV
            )
        }
    }

    fun addUltraWide(focalLength: Double): PhoneCameraProfile {
        require(focalLength > 0) { "Ultra wide focal length must be positive" }
        return copy(ultraWideFocalLength = focalLength)
    }

    fun addTelephoto(focalLength: Double): PhoneCameraProfile {
        require(focalLength > 0) { "Telephoto focal length must be positive" }
        return copy(telephotoFocalLength = focalLength)
    }

    fun deactivate(): PhoneCameraProfile {
        return copy(isActive = false)
    }

    fun activate(): PhoneCameraProfile {
        return copy(isActive = true)
    }
}

@Serializable
enum class SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    PENDING,
    GRACE_PERIOD,
    ON_HOLD
}

@Serializable
enum class SubscriptionType {
    MONTHLY,
    YEARLY,
    LIFETIME
}

@Serializable
data class Subscription(
    val userId: String,
    val productId: String,
    val transactionId: String,
    val status: SubscriptionStatus,
    val subscriptionType: SubscriptionType,
    val purchaseDate: Instant,
    val expirationDate: Instant,
    val isAutoRenewing: Boolean = true,
    val priceAmountMicros: Long = 0,
    val priceCurrencyCode: String = "USD",
    val originalJson: String = "",
    val signature: String = ""
) : Entity() {

    init {
        require(userId.isNotBlank()) { "UserId cannot be blank" }
        require(productId.isNotBlank()) { "ProductId cannot be blank" }
        require(transactionId.isNotBlank()) { "TransactionId cannot be blank" }
    }

    companion object {
        fun create(
            userId: String,
            productId: String,
            transactionId: String,
            subscriptionType: SubscriptionType,
            purchaseDate: Instant,
            expirationDate: Instant
        ): Subscription {
            return Subscription(
                userId = userId,
                productId = productId,
                transactionId = transactionId,
                status = SubscriptionStatus.ACTIVE,
                subscriptionType = subscriptionType,
                purchaseDate = purchaseDate,
                expirationDate = expirationDate
            )
        }
    }

    fun isActive(): Boolean {
        return status == SubscriptionStatus.ACTIVE && Clock.System.now() < expirationDate
    }

    fun isExpired(): Boolean {
        return status == SubscriptionStatus.EXPIRED || Clock.System.now() >= expirationDate
    }

    fun cancel(): Subscription {
        return copy(status = SubscriptionStatus.CANCELLED)
    }

    fun renew(newExpirationDate: Instant): Subscription {
        return copy(
            status = SubscriptionStatus.ACTIVE,
            expirationDate = newExpirationDate
        )
    }

    fun putOnHold(): Subscription {
        return copy(status = SubscriptionStatus.ON_HOLD)
    }

    fun enterGracePeriod(): Subscription {
        return copy(status = SubscriptionStatus.GRACE_PERIOD)
    }
}