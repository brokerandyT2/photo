// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/SettingDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when setting domain business rules are violated
 */
class SettingDomainException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)