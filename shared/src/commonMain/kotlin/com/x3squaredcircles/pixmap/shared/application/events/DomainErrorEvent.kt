// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/events/errors/DomainErrorEvent.kt
package com.x3squaredcircles.pixmap.shared.application.events

import com.x3squaredcircles.pixmap.shared.application.interfaces.INotification
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

/**
 * Base class for all domain-specific error events
 */
abstract class DomainErrorEvent(
    /**
     * The source operation or handler that generated this error
     */
    val source: String
) {
    /**
     * Unique identifier for this error occurrence
     */
    val errorId: UUID = UUID.generateUUID()

    /**
     * When this error occurred
     */
    val timestamp: Instant = Clock.System.now()

    /**
     * Generic error message for this event
     */
    abstract val message: String

    /**
     * Gets the error type identifier for categorization
     */
    abstract fun getErrorType(): String

    /**
     * Gets parameters for message formatting
     */
    open fun getParameters(): Map<String, Any> = emptyMap()

    /**
     * Gets the error severity level
     */
    open val severity: ErrorSeverity = ErrorSeverity.ERROR
}

/**
 * Defines the severity levels for error events
 */
enum class ErrorSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Location-specific error event
 */


/**
 * Setting-specific error event
 */
class SettingErrorEvent(
    val settingKey: String,
    val errorType: SettingErrorType,
    val additionalContext: String? = null,
    source: String = "SettingCommandHandler"
) : DomainErrorEvent(source), INotification {

    override val message: String
        get() = "Setting error: ${errorType.name} for key '$settingKey'"

    override fun getErrorType(): String {
        return when (errorType) {
            SettingErrorType.DUPLICATE_KEY -> "Setting_Error_DuplicateKey"
            SettingErrorType.KEY_NOT_FOUND -> "Setting_Error_KeyNotFound"
            SettingErrorType.INVALID_VALUE -> "Setting_Error_InvalidValue"
            SettingErrorType.READ_ONLY_SETTING -> "Setting_Error_ReadOnlySetting"
            SettingErrorType.DATABASE_ERROR -> "Setting_Error_DatabaseError"
        }
    }

    override fun getParameters(): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>(
            "SettingKey" to settingKey
        )

        additionalContext?.let { parameters["AdditionalContext"] = it }

        return parameters
    }
}

enum class SettingErrorType {
    DUPLICATE_KEY,
    KEY_NOT_FOUND,
    INVALID_VALUE,
    READ_ONLY_SETTING,
    DATABASE_ERROR
}

/**
 * Validation-specific error event
 */


/**
 * Weather-specific error event
 */
class WeatherUpdateErrorEvent(
    val locationId: Int,
    val errorType: WeatherErrorType,
    val additionalContext: String? = null,
    source: String = "WeatherCommandHandler"
) : DomainErrorEvent(source) {

    override val message: String
        get() = "Weather update error: ${errorType.name} for location $locationId"

    override fun getErrorType(): String {
        return when (errorType) {
            WeatherErrorType.API_UNAVAILABLE -> "Weather_Error_ApiUnavailable"
            WeatherErrorType.INVALID_LOCATION -> "Weather_Error_InvalidLocation"
            WeatherErrorType.NETWORK_ERROR -> "Weather_Error_NetworkError"
            WeatherErrorType.DATABASE_ERROR -> "Weather_Error_DatabaseError"
        }
    }

    override fun getParameters(): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>(
            "LocationId" to locationId
        )

        additionalContext?.let { parameters["AdditionalContext"] = it }

        return parameters
    }
}

enum class WeatherErrorType {
    API_UNAVAILABLE,
    INVALID_LOCATION,
    NETWORK_ERROR,
    DATABASE_ERROR
}

/**
 * Tip validation error event
 */
/**
 * Tip validation error event
 */
class TipValidationErrorEvent(
    val tipId: Int,
    val validationMessage: String,
    source: String = "TipCommandHandler"
) : DomainErrorEvent(source), INotification {

    override val message: String
        get() = "Tip validation error for tip $tipId: $validationMessage"

    override fun getErrorType(): String = "Tip_Error_ValidationFailed"

    override fun getParameters(): Map<String, Any> {
        return mapOf(
            "TipId" to tipId,
            "ValidationMessage" to validationMessage
        )
    }
}

/**
 * Tip type error event
 */
class TipTypeErrorEvent(
    val tipTypeName: String,
    val errorType: TipTypeErrorType,
    val additionalContext: String? = null,
    source: String = "TipTypeCommandHandler"
) : DomainErrorEvent(source), INotification {

    override val message: String
        get() = "Tip type error: ${errorType.name} for '$tipTypeName'"

    override fun getErrorType(): String {
        return when (errorType) {
            TipTypeErrorType.ALREADY_EXISTS -> "TipType_Error_AlreadyExists"
            TipTypeErrorType.DATABASE_ERROR -> "TipType_Error_DatabaseError"
        }
    }

    override fun getParameters(): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>(
            "TipTypeName" to tipTypeName
        )

        additionalContext?.let { parameters["AdditionalContext"] = it }

        return parameters
    }
}

enum class TipTypeErrorType {
    ALREADY_EXISTS,
    DATABASE_ERROR
}