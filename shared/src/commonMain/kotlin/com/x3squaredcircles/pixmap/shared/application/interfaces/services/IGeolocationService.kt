// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IGeolocationService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import kotlinx.datetime.Instant

/**
 * Interface for geolocation services
 */
interface IGeolocationService {

    /**
     * Gets the current location of the device
     */
    suspend fun getCurrentLocationAsync(): Result<GeolocationDto>

    /**
     * Checks if location services are enabled on the device
     */
    suspend fun isLocationEnabledAsync(): Result<Boolean>

    /**
     * Requests location permissions from the user
     */
    suspend fun requestPermissionsAsync(): Result<Boolean>

    /**
     * Starts tracking location with specified accuracy
     */
    suspend fun startTrackingAsync(accuracy: GeolocationAccuracy = GeolocationAccuracy.MEDIUM): Result<Boolean>

    /**
     * Stops location tracking
     */
    suspend fun stopTrackingAsync(): Result<Boolean>

    /**
     * Gets the last known location (cached)
     */
    suspend fun getLastKnownLocationAsync(): Result<GeolocationDto>

    /**
     * Checks if location permissions are granted
     */
    fun hasLocationPermission(): Boolean

    /**
     * Gets the current location accuracy being used
     */
    fun getCurrentAccuracy(): GeolocationAccuracy

    /**
     * Checks if location tracking is currently active
     */
    fun isTrackingActive(): Boolean

    /**
     * Sets the minimum time interval between location updates (in milliseconds)
     */
    suspend fun setUpdateInterval(intervalMs: Long): Result<Unit>

    /**
     * Sets the minimum distance change required for location updates (in meters)
     */
    suspend fun setMinimumDistance(distanceMeters: Float): Result<Unit>
}

/**
 * Data transfer object for geolocation data
 */
data class GeolocationDto(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Double? = null,
    val timestamp: Instant,
    val bearing: Double? = null,
    val speed: Double? = null
) {
    /**
     * Validates that coordinates are within valid ranges
     */
    fun isValid(): Boolean {
        return latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0 &&
                (accuracy == null || accuracy >= 0.0) &&
                (speed == null || speed >= 0.0)
    }

    /**
     * Formats coordinates for display
     */
    fun formatCoordinates(decimals: Int = 6): String {
        return "%.${decimals}f, %.${decimals}f".format(latitude, longitude)
    }

    /**
     * Gets a human-readable accuracy description
     */
    fun getAccuracyDescription(): String {
        return when {
            accuracy == null -> "Unknown accuracy"
            accuracy <= 5.0 -> "High accuracy (±${accuracy.toInt()}m)"
            accuracy <= 20.0 -> "Good accuracy (±${accuracy.toInt()}m)"
            accuracy <= 100.0 -> "Fair accuracy (±${accuracy.toInt()}m)"
            else -> "Low accuracy (±${accuracy.toInt()}m)"
        }
    }
}

/**
 * Geolocation accuracy levels
 */
enum class GeolocationAccuracy(val description: String, val typicalAccuracy: String) {
    LOWEST("Lowest accuracy", "±1000m"),
    LOW("Low accuracy", "±500m"),
    MEDIUM("Medium accuracy", "±100m"),
    HIGH("High accuracy", "±10m"),
    BEST("Best accuracy", "±3m");

    companion object {
        /**
         * Gets accuracy level by battery impact preference
         */
        fun forBatteryUsage(preserveBattery: Boolean): GeolocationAccuracy {
            return if (preserveBattery) LOW else HIGH
        }

        /**
         * Gets accuracy level for specific use case
         */
        fun forUseCase(useCase: LocationUseCase): GeolocationAccuracy {
            return when (useCase) {
                LocationUseCase.ROUGH_LOCATION -> LOW
                LocationUseCase.CITY_LEVEL -> MEDIUM
                LocationUseCase.NAVIGATION -> HIGH
                LocationUseCase.PRECISE_MAPPING -> BEST
                LocationUseCase.BACKGROUND_TRACKING -> LOWEST
            }
        }
    }
}

/**
 * Location use cases to help determine appropriate accuracy
 */
enum class LocationUseCase {
    ROUGH_LOCATION,
    CITY_LEVEL,
    NAVIGATION,
    PRECISE_MAPPING,
    BACKGROUND_TRACKING
}

/**
 * Location tracking configuration
 */
data class LocationTrackingConfig(
    val accuracy: GeolocationAccuracy = GeolocationAccuracy.MEDIUM,
    val updateIntervalMs: Long = 10000L, // 10 seconds
    val minimumDistanceMeters: Float = 5.0f,
    val timeout: Long = 30000L, // 30 seconds
    val enableBackgroundTracking: Boolean = false
) {
    companion object {
        /**
         * Configuration optimized for battery life
         */
        val BATTERY_OPTIMIZED = LocationTrackingConfig(
            accuracy = GeolocationAccuracy.LOW,
            updateIntervalMs = 60000L, // 1 minute
            minimumDistanceMeters = 50.0f
        )

        /**
         * Configuration optimized for accuracy
         */
        val ACCURACY_OPTIMIZED = LocationTrackingConfig(
            accuracy = GeolocationAccuracy.BEST,
            updateIntervalMs = 5000L, // 5 seconds
            minimumDistanceMeters = 1.0f
        )

        /**
         * Balanced configuration for general use
         */
        val BALANCED = LocationTrackingConfig(
            accuracy = GeolocationAccuracy.MEDIUM,
            updateIntervalMs = 15000L, // 15 seconds
            minimumDistanceMeters = 10.0f
        )
    }
}

/**
 * Location permission status
 */
enum class LocationPermissionStatus {
    GRANTED,
    DENIED,
    DENIED_FOREVER,
    NOT_REQUESTED,
    RESTRICTED
}

/**
 * Location service availability status
 */
enum class LocationServiceStatus {
    AVAILABLE,
    DISABLED,
    NOT_AVAILABLE,
    UNKNOWN
}