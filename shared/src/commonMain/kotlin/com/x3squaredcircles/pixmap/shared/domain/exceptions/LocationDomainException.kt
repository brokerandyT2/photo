// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/LocationDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when location domain business rules are violated
 */
open class LocationDomainException(
    code: String,
    message: String,
    cause: Throwable? = null
) : DomainException(code, message, cause) {

    override fun getUserFriendlyMessage(): String {
        return when (code) {
            "INVALID_COORDINATES" -> "The provided coordinates are invalid. Please check latitude and longitude values."
            "DUPLICATE_TITLE" -> "A location with this title already exists. Please choose a different title."
            "LOCATION_NOT_FOUND" -> "The requested location could not be found."
            "INVALID_PHOTO_PATH" -> "The photo path is invalid or the file cannot be accessed."
            "DATABASE_ERROR" -> "There was a problem saving the location. Please try again."
            "NETWORK_ERROR" -> "Network connection failed. Please check your internet connection."
            "AUTHORIZATION_ERROR" -> "You don't have permission to perform this action."
            else -> "An error occurred while processing the location: $message"
        }
    }
}