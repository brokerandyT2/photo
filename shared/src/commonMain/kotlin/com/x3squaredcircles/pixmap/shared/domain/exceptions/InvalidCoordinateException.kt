// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/InvalidCoordinateException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when invalid coordinates are provided
 */
class InvalidCoordinateException(
    val latitude: Double,
    val longitude: Double,
    message: String = "Invalid coordinates: Latitude=$latitude, Longitude=$longitude"
) : LocationDomainException("INVALID_COORDINATES", message) {

    override fun getUserFriendlyMessage(): String {
        return "The coordinates (${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}) are invalid. " +
                "Latitude must be between -90 and 90, and longitude must be between -180 and 180."
    }
}