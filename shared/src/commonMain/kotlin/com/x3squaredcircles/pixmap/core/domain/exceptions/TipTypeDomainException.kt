package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when tip type domain business rules are violated
 */
class TipTypeDomainException(
    message: String,
    val code: String,
    cause: Throwable? = null
) : Exception(message, cause)