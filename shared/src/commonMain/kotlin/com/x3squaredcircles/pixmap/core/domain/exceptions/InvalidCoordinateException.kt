package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when invalid coordinates are provided
 */
class InvalidCoordinateException(
    val latitude: Double,
    val longitude: Double,
    message: String = "Invalid coordinates: Latitude=$latitude, Longitude=$longitude"
) : LocationDomainException(message, "INVALID_COORDINATE")