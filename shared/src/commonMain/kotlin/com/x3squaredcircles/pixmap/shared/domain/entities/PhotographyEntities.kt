//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/PhotographyEntities.kt

package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Photography domain entities
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
        /**
         * Factory method to create a camera body
         */
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

    /**
     * Gets the diagonal measurement of the sensor in millimeters
     */
    fun getDiagonalMm(): Double {
        return sqrt(sensorWidth * sensorWidth + sensorHeight * sensorHeight)
    }

    /**
     * Gets the aspect ratio of the sensor
     */
    fun getAspectRatio(): Double {
        return sensorWidth / sensorHeight
    }

    /**
     * Checks if this camera is full frame
     */
    fun isFullFrame(): Boolean {
        return sensorType.lowercase() == "full frame"
    }

    /**
     * Gets a display name for the camera
     */
    fun getDisplayName(): String {
        return if (manufacturer.isNotBlank() && model.isNotBlank()) {
            "$manufacturer $model"
        } else {
            name
        }
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
        /**
         * Creates a prime lens
         */
        fun createPrime(
            name: String,
            focalLength: Double,
            maxAperture: Double,
            mountType: MountType,
            manufacturer: String = ""
        ): Lens {
            return Lens(
                name = name,
                minFocalLength = focalLength,
                maxFocalLength = focalLength,
                maxAperture = maxAperture,
                mountType = mountType,
                manufacturer = manufacturer,
                lensType = LensType.PRIME
            )
        }

        /**
         * Creates a zoom lens
         */
        fun createZoom(
            name: String,
            minFocalLength: Double,
            maxFocalLength: Double,
            maxAperture: Double,
            mountType: MountType,
            manufacturer: String = ""
        ): Lens {
            return Lens(
                name = name,
                minFocalLength = minFocalLength,
                maxFocalLength = maxFocalLength,
                maxAperture = maxAperture,
                mountType = mountType,
                manufacturer = manufacturer,
                lensType = LensType.ZOOM
            )
        }
    }

    /**
     * Checks if this is a prime lens
     */
    fun isPrime(): Boolean = minFocalLength == maxFocalLength

    /**
     * Checks if this is a zoom lens
     */
    fun isZoom(): Boolean = !isPrime()

    /**
     * Gets the zoom ratio for zoom lenses
     */
    fun getZoomRatio(): Double = maxFocalLength / minFocalLength

    /**
     * Calculates field of view for a given camera body
     */
    fun calculateFieldOfView(cameraBody: CameraBody, focalLength: Double = minFocalLength): Double {
        val effectiveFocalLength = focalLength * cameraBody.cropFactor
        return 2 * atan(cameraBody.sensorWidth / (2 * effectiveFocalLength)) * 180 / PI
    }

    /**
     * Gets a formatted focal length string
     */
    fun getFocalLengthString(): String {
        return if (isPrime()) {
            "${minFocalLength.toInt()}mm"
        } else {
            "${minFocalLength.toInt()}-${maxFocalLength.toInt()}mm"
        }
    }

    /**
     * Gets a display name for the lens
     */
    fun getDisplayName(): String {
        return if (manufacturer.isNotBlank()) {
            "$manufacturer $name"
        } else {
            name
        }
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
        /**
         * Creates a native mount compatibility
         */
        fun createNativeMount(lensId: Int, cameraBodyId: Int): LensCameraCompatibility {
            return LensCameraCompatibility(
                lensId = lensId,
                cameraBodyId = cameraBodyId,
                isNativeMount = true,
                requiresAdapter = false
            )
        }

        /**
         * Creates an adapter-based compatibility
         */
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

    /**
     * Gets a compatibility description
     */
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
        /**
         * Creates a user camera body association
         */
        fun create(userId: String, cameraBodyId: Int): UserCameraBody {
            return UserCameraBody(
                userId = userId,
                cameraBodyId = cameraBodyId
            )
        }
    }

    /**
     * Marks this camera as a favorite
     */
    fun markAsFavorite(): UserCameraBody {
        return copy(isFavorite = true)
    }

    /**
     * Removes favorite status
     */
    fun removeFavorite(): UserCameraBody {
        return copy(isFavorite = false)
    }

    /**
     * Updates the custom name
     */
    fun updateCustomName(customName: String?): UserCameraBody {
        return copy(customName = customName)
    }

    /**
     * Updates the notes
     */
    fun updateNotes(notes: String): UserCameraBody {
        return copy(notes = notes)
    }
}