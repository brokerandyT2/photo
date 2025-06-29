// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/WeatherDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when weather domain business rules are violated
 */
class WeatherDomainException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)