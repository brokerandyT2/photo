package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when tip domain business rules are violated
 */
class TipDomainException(
    message: String,
    val code: String,
    cause: Throwable? = null
) : Exception(message, cause)