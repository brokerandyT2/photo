// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/exceptions/SettingDomainException.kt
package com.x3squaredcircles.pixmap.shared.domain.exceptions

/**
 * Exception thrown when setting domain business rules are violated
 */
class SettingDomainException(
    code: String,
    message: String,
    cause: Throwable? = null
) : DomainException(code, message, cause) {

    override fun getUserFriendlyMessage(): String {
        return when (code) {
            "INVALID_SETTING_KEY" -> "The setting key is invalid or empty."
            "SETTING_NOT_FOUND" -> "The requested setting could not be found."
            "INVALID_SETTING_VALUE" -> "The setting value is invalid or not in the correct format."
            "READONLY_SETTING" -> "This setting cannot be modified."
            "DATABASE_ERROR" -> "There was a problem saving the setting. Please try again."
            else -> "An error occurred while managing settings: $message"
        }
    }
}