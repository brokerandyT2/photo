//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/DomainExceptions.kt

package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Base class for all domain exceptions
 */
abstract class DomainException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Gets a user-friendly error message
     */
    abstract fun getUserFriendlyMessage(): String
}
