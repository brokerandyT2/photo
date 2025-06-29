package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when location domain business rules are violated
 */
class LocationDomainException(
    message: String,
    val code: String,
    cause: Throwable? = null
) : Exception(message, cause)