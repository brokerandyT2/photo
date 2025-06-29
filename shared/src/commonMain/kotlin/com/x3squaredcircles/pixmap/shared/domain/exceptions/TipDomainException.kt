// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/TipDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when tip domain business rules are violated
 */
class TipDomainException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)