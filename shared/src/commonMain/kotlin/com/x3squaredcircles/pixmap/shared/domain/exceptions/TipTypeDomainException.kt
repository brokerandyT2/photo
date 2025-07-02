// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/TipTypeDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when tip type domain business rules are violated
 */
class TipTypeDomainException(
    code: String,
    message: String,
    cause: Throwable? = null
) : DomainException(code, message, cause) {

    override fun getUserFriendlyMessage(): String {
        return when (code) {
            "INVALID_TIP_TYPE_NAME" -> "Tip type name cannot be empty."
            "TIP_TYPE_NOT_FOUND" -> "The requested tip type could not be found."
            "DUPLICATE_TIP_TYPE" -> "A tip type with this name already exists."
            "TIP_TYPE_IN_USE" -> "This tip type cannot be deleted because it has associated tips."
            "DATABASE_ERROR" -> "There was a problem saving the tip type. Please try again."
            else -> "An error occurred while managing tip types: $message"
        }
    }
}