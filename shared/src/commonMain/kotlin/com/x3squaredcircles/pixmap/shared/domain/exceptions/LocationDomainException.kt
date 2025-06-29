// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/LocationDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when location domain business rules are violated
 */
class LocationDomainException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)