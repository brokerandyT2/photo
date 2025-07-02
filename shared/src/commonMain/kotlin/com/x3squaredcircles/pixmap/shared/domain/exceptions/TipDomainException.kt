// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/TipDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when tip domain business rules are violated
 */
class TipDomainException(
    code: String,
    message: String,
    cause: Throwable? = null
) : DomainException(code, message, cause) {

    override fun getUserFriendlyMessage(): String {
        return when (code) {
            "INVALID_TIP_TITLE" -> "Tip title cannot be empty."
            "INVALID_TIP_TYPE" -> "Invalid tip type specified."
            "TIP_NOT_FOUND" -> "The requested tip could not be found."
            "DUPLICATE_TIP" -> "A tip with this title already exists."
            "DATABASE_ERROR" -> "There was a problem saving the tip. Please try again."
            else -> "An error occurred while managing tips: $message"
        }
    }
}
