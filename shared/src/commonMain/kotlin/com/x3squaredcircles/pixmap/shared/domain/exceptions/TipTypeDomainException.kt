// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/TipTypeDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when tip type domain business rules are violated
 */
class TipTypeDomainException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)