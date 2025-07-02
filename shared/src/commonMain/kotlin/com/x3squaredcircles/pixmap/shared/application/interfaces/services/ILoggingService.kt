//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/ILoggingService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import kotlinx.datetime.Instant

/**
 * Interface for logging service with structured logging capabilities
 */
interface ILoggingService {

    /**
     * Logs a debug message
     */
    fun debug(message: String, vararg args: Any?)

    /**
     * Logs an informational message
     */
    fun info(message: String, vararg args: Any?)

    /**
     * Logs a warning message
     */
    fun warning(message: String, vararg args: Any?)

    /**
     * Logs an error message
     */
    fun error(message: String, vararg args: Any?)

    /**
     * Logs an error with exception details
     */
    fun error(message: String, exception: Throwable, vararg args: Any?)

    /**
     * Logs a critical error
     */
    fun critical(message: String, vararg args: Any?)

    /**
     * Logs a critical error with exception details
     */
    fun critical(message: String, exception: Throwable, vararg args: Any?)

    // Alternative method names for backward compatibility
    /**
     * Logs a debug message (alternative naming)
     */
    fun logDebug(message: String, tag: String? = null)

    /**
     * Logs an informational message (alternative naming)
     */
    fun logInfo(message: String, tag: String? = null)

    /**
     * Logs a warning message (alternative naming)
     */
    fun logWarning(message: String, tag: String? = null)

    /**
     * Logs an error message (alternative naming)
     */
    fun logError(message: String, exception: Throwable? = null, tag: String? = null)

    /**
     * Logs a verbose message
     */
    fun logVerbose(message: String, tag: String? = null)

    /**
     * Logs the start of an operation
     */
    fun logOperationStart(operationName: String, parameters: Map<String, Any>? = null)

    /**
     * Logs the completion of an operation
     */
    fun logOperationComplete(operationName: String, durationMs: Long?, result: String? = null)

    /**
     * Logs the failure of an operation
     */
    fun logOperationFailure(operationName: String, exception: Throwable, durationMs: Long? = null)

    /**
     * Logs performance metrics
     */
    fun performance(operation: String, durationMs: Long, success: Boolean = true)

    /**
     * Logs user activity for analytics
     */
    fun userActivity(activity: String, properties: Map<String, Any?> = emptyMap())

    /**
     * Logs security-related events
     */
    fun security(event: String, severity: SecuritySeverity, properties: Map<String, Any?> = emptyMap())

    /**
     * Creates a structured log entry with context
     */
    fun structured(
        level: LogLevel,
        message: String,
        category: String,
        properties: Map<String, Any?> = emptyMap(),
        exception: Throwable? = null
    )

    /**
     * Begins a timed operation for performance logging
     */
    fun beginTimedOperation(operationName: String): TimedOperation

    /**
     * Flushes any pending log entries
     */
    suspend fun flush()

    /**
     * Sets the minimum log level
     */
    fun setMinimumLevel(level: LogLevel)

    /**
     * Gets the current minimum log level
     */
    fun getMinimumLevel(): LogLevel

    /**
     * Checks if logging is enabled for a specific level
     */
    fun isEnabled(level: LogLevel): Boolean

    /**
     * Adds correlation ID for request tracking
     */
    fun withCorrelationId(correlationId: String): ILoggingService

    /**
     * Adds user context to logs
     */
    fun withUserContext(userId: String?, userEmail: String? = null): ILoggingService
}

/**
 * Log severity levels
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARNING(3),
    ERROR(4),
    CRITICAL(5),
    NONE(6);

    companion object {
        fun fromString(level: String): LogLevel {
            return entries.find { it.name.equals(level, ignoreCase = true) } ?: INFO
        }
    }
}

/**
 * Security event severity levels
 */
enum class SecuritySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Structured log entry data class
 */
data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val message: String,
    val category: String,
    val properties: Map<String, Any?>,
    val exception: Throwable? = null,
    val correlationId: String? = null,
    val userId: String? = null,
    val sessionId: String? = null
)

/**
 * Timed operation for performance logging
 */
interface TimedOperation {
    val operationName: String
    val startTime: Instant

    /**
     * Completes the timed operation and logs the result
     */
    fun complete(success: Boolean = true, additionalProperties: Map<String, Any?> = emptyMap())

    /**
     * Completes the timed operation with an error
     */
    fun completeWithError(error: Throwable, additionalProperties: Map<String, Any?> = emptyMap())

    /**
     * Adds a checkpoint within the operation
     */
    fun checkpoint(name: String, properties: Map<String, Any?> = emptyMap())

    /**
     * Gets the current duration in milliseconds
     */
    fun getCurrentDurationMs(): Long
}

/**
 * Log categories for better organization
 */
object LogCategories {
    const val APPLICATION = "Application"
    const val DATABASE = "Database"
    const val NETWORK = "Network"
    const val AUTHENTICATION = "Authentication"
    const val LOCATION = "Location"
    const val WEATHER = "Weather"
    const val CAMERA = "Camera"
    const val FILE_SYSTEM = "FileSystem"
    const val PERFORMANCE = "Performance"
    const val SECURITY = "Security"
    const val USER_ACTIVITY = "UserActivity"
    const val BUSINESS_LOGIC = "BusinessLogic"
    const val INFRASTRUCTURE = "Infrastructure"
}

/**
 * Extension functions for convenience logging
 */
fun ILoggingService.debugDatabase(message: String, vararg args: Any?) {
    structured(LogLevel.DEBUG, message, LogCategories.DATABASE, mapOf("args" to args.toList()))
}

fun ILoggingService.infoNetwork(message: String, url: String?, statusCode: Int? = null) {
    val properties = mutableMapOf<String, Any?>()
    url?.let { properties["url"] = it }
    statusCode?.let { properties["statusCode"] = it }
    structured(LogLevel.INFO, message, LogCategories.NETWORK, properties)
}

fun ILoggingService.warningLocation(message: String, latitude: Double? = null, longitude: Double? = null) {
    val properties = mutableMapOf<String, Any?>()
    latitude?.let { properties["latitude"] = it }
    longitude?.let { properties["longitude"] = it }
    structured(LogLevel.WARNING, message, LogCategories.LOCATION, properties)
}