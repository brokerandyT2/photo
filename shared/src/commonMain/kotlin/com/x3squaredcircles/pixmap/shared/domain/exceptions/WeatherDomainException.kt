// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/WeatherDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when weather domain business rules are violated
 */
class WeatherDomainException(
    code: String,
    message: String,
    cause: Throwable? = null
) : DomainException(code, message, cause) {

    override fun getUserFriendlyMessage(): String {
        return when (code) {
            "WEATHER_API_ERROR" -> "Unable to fetch weather data. Please try again later."
            "INVALID_LOCATION" -> "Weather data is not available for this location."
            "API_KEY_INVALID" -> "Weather service configuration error. Please contact support."
            "RATE_LIMIT_EXCEEDED" -> "Too many weather requests. Please wait a moment and try again."
            "NETWORK_ERROR" -> "Network connection failed. Please check your internet connection."
            "DATA_STALE" -> "Weather data is outdated. Refreshing..."
            else -> "An error occurred while getting weather information: $message"
        }
    }
}