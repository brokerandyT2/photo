// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/ILoggingService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

/**
 * Service interface for application logging
 */
interface ILoggingService {

    /**
     * Logs an informational message
     */
    fun logInfo(message: String, tag: String? = null)

    /**
     * Logs a warning message
     */
    fun logWarning(message: String, tag: String? = null)

    /**
     * Logs an error message
     */
    fun logError(message: String, exception: Throwable? = null, tag: String? = null)

    /**
     * Logs a debug message
     */
    fun logDebug(message: String, tag: String? = null)

    /**
     * Logs a verbose message
     */
    fun logVerbose(message: String, tag: String? = null)

    /**
     * Logs operation start
     */
    fun logOperationStart(operationName: String, parameters: Map<String, Any>? = null)

    /**
     * Logs operation completion
     */
    fun logOperationComplete(operationName: String, durationMs: Long? = null, result: String? = null)

    /**
     * Logs operation failure
     */
    fun logOperationFailure(operationName: String, exception: Throwable, durationMs: Long? = null)

    /**
     * Sets the minimum log level
     */
    fun setLogLevel(level: LogLevel)

    /**
     * Enables or disables logging
     */
    fun setLoggingEnabled(enabled: Boolean)
}

/**
 * Log levels for filtering messages
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    NONE
}

/**
 * Extension functions for structured logging
 */
fun ILoggingService.logWithContext(
    level: LogLevel,
    message: String,
    context: Map<String, Any> = emptyMap(),
    exception: Throwable? = null,
    tag: String? = null
) {
    val contextString = if (context.isNotEmpty()) {
        " | Context: ${context.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
    } else ""

    val fullMessage = "$message$contextString"

    when (level) {
        LogLevel.VERBOSE -> logVerbose(fullMessage, tag)
        LogLevel.DEBUG -> logDebug(fullMessage, tag)
        LogLevel.INFO -> logInfo(fullMessage, tag)
        LogLevel.WARNING -> logWarning(fullMessage, tag)
        LogLevel.ERROR -> logError(fullMessage, exception, tag)
        LogLevel.NONE -> { /* Do nothing */ }
    }
}

/**
 * Performance tracking extensions
 */
inline fun <T> ILoggingService.trackOperation(
    operationName: String,
    parameters: Map<String, Any>? = null,
    operation: () -> T
): T {
    logOperationStart(operationName, parameters)
    val startTime = kotlinx.datetime.Clock.System.now()

    return try {
        val result = operation()
        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        logOperationComplete(operationName, duration, "Success")
        result
    } catch (e: Throwable) {
        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        logOperationFailure(operationName, e, duration)
        throw e
    }
}

/**
 * Async operation tracking
 */
suspend inline fun <T> ILoggingService.trackOperationAsync(
    operationName: String,
    parameters: Map<String, Any>? = null,
    operation: suspend () -> T
): T {
    logOperationStart(operationName, parameters)
    val startTime = kotlinx.datetime.Clock.System.now()

    return try {
        val result = operation()
        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        logOperationComplete(operationName, duration, "Success")
        result
    } catch (e: Throwable) {
        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        logOperationFailure(operationName, e, duration)
        throw e
    }
}