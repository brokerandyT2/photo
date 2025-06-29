package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when setting domain business rules are violated
 */
class SettingDomainException(
    message: String,
    val code: String,
    cause: Throwable? = null
) : Exception(message, cause)