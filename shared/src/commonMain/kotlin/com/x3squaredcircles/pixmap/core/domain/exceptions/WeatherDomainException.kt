package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when weather domain business rules are violated
 */
class WeatherDomainException(
    message: String,
    val code: String,
    cause: Throwable? = null
) : Exception(message, cause)