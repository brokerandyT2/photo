package com.x3squaredcircles.pixmap.photography.domain.entities

import com.x3squaredcircles.pixmap.core.domain.entities.Entity
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
    override val id: Int = 0,
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
                "crop", "aps-c" -> 1.5 // Approximate
                "micro four thirds" -> 2.0
                "medium format" -> 0.79 // Approximate
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

    fun getDiagonalMm(): Double {
        return sqrt(sensorWidth * sensorWidth + sensorHeight * sensorHeight)
    }

    fun getAspectRatio(): Double {
        return sensorWidth / sensorHeight
    }
}

@Serializable
data class Lens(
    override val id: Int = 0,
    val name: String,
    val minFocalLength: Double,
    val maxFocalLength: Double,
    val maxAperture: Double,
    val minAperture: Double = 22.0,
    val mountType: MountType,
    val isUserCreated: Boolean = false,
    val manufacturer: String = "",
    val lensType: LensType = LensType.PRIME
) : Entity() {

    init {
        require(name.isNotBlank()) { "Lens name cannot be blank" }
        require(minFocalLength > 0) { "Min focal length must be positive" }
        require(maxFocalLength >= minFocalLength) { "Max focal length must be >= min focal length" }
        require(maxAperture > 0) { "Max aperture must be positive" }
    }

    companion object {
        fun createPrime(
            name: String,
            focalLength: Double,
            maxAperture: Double,
            mountType: MountType,
            isUserCreated: Boolean = false
        ): Lens {
            return Lens(
                name = name,
                minFocalLength = focalLength,
                maxFocalLength = focalLength,
                maxAperture = maxAperture,
                mountType = mountType,
                isUserCreated = isUserCreated,
                lensType = LensType.PRIME
            )
        }

        fun createZoom(
            name: String,
            minFocalLength: Double,
            maxFocalLength: Double,
            maxAperture: Double,
            mountType: MountType,
            isUserCreated: Boolean = false
        ): Lens {
            return Lens(
                name = name,
                minFocalLength = minFocalLength,
                maxFocalLength = maxFocalLength,
                maxAperture = maxAperture,
                mountType = mountType,
                isUserCreated = isUserCreated,
                lensType = LensType.ZOOM
            )
        }
    }

    fun isPrime(): Boolean = lensType == LensType.PRIME
    fun isZoom(): Boolean = lensType == LensType.ZOOM

    fun getFieldOfView(cameraBody: CameraBody, focalLength: Double = minFocalLength): Double {
        val effectiveFocalLength = focalLength * cameraBody.cropFactor
        val sensorDiagonal = cameraBody.getDiagonalMm()
        return 2 * atan(sensorDiagonal / (2 * effectiveFocalLength)) * 180 / PI
    }
}

@Serializable
data class LensCameraCompatibility(
    override val id: Int = 0,
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
}

@Serializable
data class UserCameraBody(
    override val id: Int = 0,
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
    override val id: Int = 0,
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
data class Subscription(
    override val id: Int = 0,
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
        return status == SubscriptionStatus.ACTIVE &&
                Clock.System.now() < expirationDate
    }

    fun isExpired(): Boolean {
        return Clock.System.now() >= expirationDate
    }

    fun cancel(): Subscription {
        return copy(status = SubscriptionStatus.CANCELED, isAutoRenewing = false)
    }

    fun suspend(): Subscription {
        return copy(status = SubscriptionStatus.SUSPENDED)
    }

    fun reactivate(): Subscription {
        return copy(status = SubscriptionStatus.ACTIVE)
    }
}

@Serializable
enum class SubscriptionStatus {
    ACTIVE,
    CANCELED,
    EXPIRED,
    SUSPENDED,
    PENDING
}

@Serializable
enum class SubscriptionType {
    FREE,
    BASIC,
    PREMIUM,
    PRO
}