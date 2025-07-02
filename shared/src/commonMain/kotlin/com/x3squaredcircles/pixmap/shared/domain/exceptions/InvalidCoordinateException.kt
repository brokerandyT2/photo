// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/InvalidCoordinateException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when invalid coordinates are provided
 */
class InvalidCoordinateException : LocationDomainException {

    val latitude: Double
    val longitude: Double

    /**
     * Initializes a new instance of the InvalidCoordinateException class with the specified latitude
     * and longitude values.
     * @param latitude The latitude value that caused the exception. Must be within the valid range of -90 to 90.
     * @param longitude The longitude value that caused the exception. Must be within the valid range of -180 to 180.
     */
    constructor(latitude: Double, longitude: Double) : super(
        "INVALID_COORDINATE",
        "Invalid coordinates: Latitude=$latitude, Longitude=$longitude"
    ) {
        this.latitude = latitude
        this.longitude = longitude
    }

    /**
     * Represents an exception that is thrown when an invalid geographic coordinate is encountered.
     * @param latitude The latitude value that caused the exception. Must be in the range -90 to 90.
     * @param longitude The longitude value that caused the exception. Must be in the range -180 to 180.
     * @param message A message that describes the error.
     */
    constructor(latitude: Double, longitude: Double, message: String) : super(
        "INVALID_COORDINATE",
        message
    ) {
        this.latitude = latitude
        this.longitude = longitude
    }
}