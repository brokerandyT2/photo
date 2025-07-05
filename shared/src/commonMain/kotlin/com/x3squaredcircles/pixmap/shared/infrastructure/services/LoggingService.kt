// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/LoggingService.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.SecuritySeverity
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogEntry
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.TimedOperation
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Concrete logger implementation for the shared module
 */
private class ConcreteLogger {
    fun debug(message: String) = println("DEBUG: $message")
    fun info(message: String) = println("INFO: $message")
    fun warning(message: String) = println("WARNING: $message")
    fun error(message: String, exception: Throwable? = null) {
        println("ERROR: $message")
        exception?.printStackTrace()
    }
}

/**
 * Logging service implementation with database persistence and fault tolerance
 */
class LoggingService(
    private val databaseContext: IDatabaseContext
) : ILoggingService {

    private val logger = ConcreteLogger()
    private var currentLogLevel = LogLevel.INFO
    private var loggingEnabled = true
    private val loggingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ILoggingService interface methods
    override fun debug(message: String, vararg args: Any?) {
        logMessage(LogLevel.DEBUG, formatMessage(message, args), null, null)
    }

    override fun info(message: String, vararg args: Any?) {
        logMessage(LogLevel.INFO, formatMessage(message, args), null, null)
    }

    override fun warning(message: String, vararg args: Any?) {
        logMessage(LogLevel.WARNING, formatMessage(message, args), null, null)
    }

    override fun error(message: String, vararg args: Any?) {
        logMessage(LogLevel.ERROR, formatMessage(message, args), null, null)
    }

    override fun error(message: String, exception: Throwable, vararg args: Any?) {
        logMessage(LogLevel.ERROR, formatMessage(message, args), exception, null)
    }

    override fun critical(message: String, vararg args: Any?) {
        logMessage(LogLevel.CRITICAL, formatMessage(message, args), null, null)
    }

    override fun critical(message: String, exception: Throwable, vararg args: Any?) {
        logMessage(LogLevel.CRITICAL, formatMessage(message, args), exception, null)
    }

    override fun logDebug(message: String, tag: String?) {
        logMessage(LogLevel.DEBUG, message, null, tag)
    }

    override fun logInfo(message: String, tag: String?) {
        logMessage(LogLevel.INFO, message, null, tag)
    }

    override fun logWarning(message: String, tag: String?) {
        logMessage(LogLevel.WARNING, message, null, tag)
    }

    override fun logError(message: String, exception: Throwable?, tag: String?) {
        logMessage(LogLevel.ERROR, message, exception, tag)
    }

    override fun logVerbose(message: String, tag: String?) {
        logMessage(LogLevel.VERBOSE, message, null, tag)
    }

    override fun logOperationStart(operationName: String, parameters: Map<String, Any>?) {
        val paramString = parameters?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""
        logMessage(LogLevel.INFO, "Operation started: $operationName | Parameters: $paramString", null, "OPERATION")
    }

    override fun logOperationComplete(operationName: String, durationMs: Long?, result: String?) {
        val duration = durationMs?.let { " | Duration: ${it}ms" } ?: ""
        val resultStr = result?.let { " | Result: $it" } ?: ""
        logMessage(LogLevel.INFO, "Operation completed: $operationName$duration$resultStr", null, "OPERATION")
    }

    override fun logOperationFailure(operationName: String, exception: Throwable, durationMs: Long?) {
        val duration = durationMs?.let { " | Duration: ${it}ms" } ?: ""
        logMessage(LogLevel.ERROR, "Operation failed: $operationName$duration", exception, "OPERATION")
    }

    override fun performance(operation: String, durationMs: Long, success: Boolean) {
        val status = if (success) "SUCCESS" else "FAILURE"
        logMessage(LogLevel.INFO, "Performance: $operation | Duration: ${durationMs}ms | Status: $status", null, "PERFORMANCE")
    }

    override fun userActivity(activity: String, properties: Map<String, Any?>) {
        val propertiesString = if (properties.isNotEmpty()) {
            " | Properties: ${properties.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else ""
        logMessage(LogLevel.INFO, "User Activity: $activity$propertiesString", null, "USER_ACTIVITY")
    }

    override fun security(event: String, severity: SecuritySeverity, properties: Map<String, Any?>) {
        val level = when (severity) {
            SecuritySeverity.LOW -> LogLevel.INFO
            SecuritySeverity.MEDIUM -> LogLevel.WARNING
            SecuritySeverity.HIGH -> LogLevel.ERROR
            SecuritySeverity.CRITICAL -> LogLevel.CRITICAL
            SecuritySeverity.INFO -> LogLevel.INFO
            SecuritySeverity.WARNING -> LogLevel.WARNING
        }
        val propertiesString = if (properties.isNotEmpty()) {
            " | Properties: ${properties.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else ""
        logMessage(level, "Security Event: $event$propertiesString", null, "SECURITY")
    }

    override fun structured(level: LogLevel, message: String, category: String, properties: Map<String, Any?>, exception: Throwable?) {
        val propertiesString = if (properties.isNotEmpty()) {
            " | Properties: ${properties.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else ""
        val structuredMessage = "[$category] $message$propertiesString"
        logMessage(level, structuredMessage, exception, category)
    }
     fun logSecurityEvent(
        message: String,
        severity: SecuritySeverity,
        userId: String?,
        ipAddress: String?,
        userAgent: String?,
        properties: Map<String, Any?>
    ) {
        val level = when (severity) {
            SecuritySeverity.INFO -> LogLevel.INFO
            SecuritySeverity.WARNING -> LogLevel.WARNING
            SecuritySeverity.CRITICAL -> LogLevel.CRITICAL
            SecuritySeverity.LOW -> LogLevel.INFO
            SecuritySeverity.MEDIUM ->  LogLevel.WARNING
            SecuritySeverity.HIGH -> LogLevel.CRITICAL
        }

        val securityContext = buildString {
            append("SECURITY")
            userId?.let { append(" | User: $it") }
            ipAddress?.let { append(" | IP: $it") }
            userAgent?.let { append(" | UserAgent: $it") }
        }

        val propertiesString = if (properties.isNotEmpty()) {
            " | Properties: ${properties.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else ""

        val securityMessage = "$message$propertiesString"
        logMessage(level, securityMessage, null, securityContext)
    }

     fun logEntry(entry: LogEntry) {
        val level = LogLevel.valueOf(entry.level.toString())
        logMessage(level, entry.message, entry.exception, entry.category)
    }

     fun logStructured(
        level: LogLevel,
        message: String,
        category: String,
        exception: Throwable?,
        properties: Map<String, Any?>
    ) {
        val propertiesString = if (properties.isNotEmpty()) {
            " | Properties: ${properties.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else ""

        val structuredMessage = "[$category] $message$propertiesString"
        logMessage(level, structuredMessage, exception, category)
    }

    override fun beginTimedOperation(operationName: String): TimedOperation {
        return TimedOperationImpl(operationName, this)
    }

    override suspend fun flush() {
        // Implementation would flush any pending log entries
        // For now, this is a no-op since we log synchronously
    }

    override fun setMinimumLevel(level: LogLevel) {
        currentLogLevel = level
        logInfo("Log level changed to $level", "LOGGING")
    }

    override fun getMinimumLevel(): LogLevel {
        return currentLogLevel
    }

    override fun isEnabled(level: LogLevel): Boolean {
        return loggingEnabled && level.priority >= currentLogLevel.priority
    }

    override fun withCorrelationId(correlationId: String): ILoggingService {
        // For now, return the same instance
        // In a full implementation, this would wrap the logger with correlation context
        return this
    }

    override fun withUserContext(userId: String?, userEmail: String?): ILoggingService {
        // For now, return the same instance
        // In a full implementation, this would wrap the logger with user context
        return this
    }

    // Database operations
    suspend fun logToDatabaseAsync(level: LogLevel, message: String, exception: Throwable? = null, tag: String? = null) {
        try {
            val logEntity = com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity(
                id = 0,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                level = level.name,
                message = message,
                exception = exception?.stackTraceToString() ?: ""
            )

            databaseContext.insertAsync(logEntity)
        } catch (ex: Exception) {
            // If we can't log to database, log to the standard logger
            logger.error("Failed to write log to database", ex)
            // Do NOT rethrow - this method should be fault-tolerant
        }
    }

    suspend fun getLogsAsync(count: Int = 100): List<com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity> {
        return try {
            databaseContext.queryAsync(
                "SELECT * FROM LogEntity ORDER BY Timestamp DESC LIMIT ?",
                ::mapCursorToLogEntity,
                count
            )
        } catch (ex: Exception) {
            logger.error("Failed to retrieve logs from database", ex)
            emptyList()
        }
    }

    suspend fun clearLogsAsync() {
        try {
            val deletedCount = databaseContext.executeAsync("DELETE FROM LogEntity")
            logger.info("Cleared $deletedCount logs from database")
        } catch (ex: Exception) {
            logger.error("Failed to clear logs from database", ex)
            throw ex
        }
    }

    suspend fun getLogsByLevelAsync(level: LogLevel, count: Int = 100): List<com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity> {
        return try {
            databaseContext.queryAsync(
                "SELECT * FROM LogEntity WHERE Level = ? ORDER BY Timestamp DESC LIMIT ?",
                ::mapCursorToLogEntity,
                level.name, count
            )
        } catch (ex: Exception) {
            logger.error("Failed to retrieve logs by level from database", ex)
            emptyList()
        }
    }

    suspend fun getLogsByTagAsync(tag: String, count: Int = 100): List<com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity> {
        return try {
            databaseContext.queryAsync(
                "SELECT * FROM LogEntity WHERE Tag = ? ORDER BY Timestamp DESC LIMIT ?",
                ::mapCursorToLogEntity,
                tag, count
            )
        } catch (ex: Exception) {
            logger.error("Failed to retrieve logs by tag from database", ex)
            emptyList()
        }
    }

    suspend fun getLogsByDateRangeAsync(startTime: Instant, endTime: Instant): List<com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity> {
        return try {
            databaseContext.queryAsync(
                "SELECT * FROM LogEntity WHERE Timestamp BETWEEN ? AND ? ORDER BY Timestamp DESC",
                ::mapCursorToLogEntity,
                startTime.toEpochMilliseconds(), endTime.toEpochMilliseconds()
            )
        } catch (ex: Exception) {
            logger.error("Failed to retrieve logs by date range from database", ex)
            emptyList()
        }
    }

    suspend fun deleteOldLogsAsync(olderThan: Instant): Int {
        return try {
            val deletedCount = databaseContext.executeAsync(
                "DELETE FROM LogEntity WHERE Timestamp < ?",
                olderThan.toEpochMilliseconds()
            )
            logger.info("Deleted $deletedCount old logs from database")
            deletedCount
        } catch (ex: Exception) {
            logger.error("Failed to delete old logs from database", ex)
            throw ex
        }
    }

    // Private helper methods
    private fun logMessage(level: LogLevel, message: String, exception: Throwable?, tag: String?) {
        if (!loggingEnabled || level.priority < currentLogLevel.priority) {
            return
        }

        // Log to standard logger immediately
        when (level) {
            LogLevel.VERBOSE -> logger.debug(formatLogMessage(message, tag))
            LogLevel.DEBUG -> logger.debug(formatLogMessage(message, tag))
            LogLevel.INFO -> logger.info(formatLogMessage(message, tag))
            LogLevel.WARNING -> logger.warning(formatLogMessage(message, tag))
            LogLevel.ERROR -> logger.error(formatLogMessage(message, tag), exception)
            LogLevel.CRITICAL -> logger.error(formatLogMessage(message, tag), exception)
            LogLevel.NONE -> { /* Do nothing */ }
        }

        // Asynchronously log to database (fire and forget)
        loggingScope.launch {
            try {
                logToDatabaseAsync(level, message, exception, tag)
            } catch (ex: Exception) {
                // Database logging failed, but we already logged to standard logger
                // Don't propagate the exception to avoid breaking the calling code
                logger.error("Database logging failed for message: $message", ex)
            }
        }
    }

    private fun formatLogMessage(message: String, tag: String?): String {
        return if (tag != null) "[$tag] $message" else message
    }

    private fun formatMessage(message: String, args: Array<out Any?>): String {
        return if (args.isEmpty()) {
            message
        } else {
            try {
                message.format(*args)
            } catch (e: Exception) {
                "$message [Args: ${args.joinToString()}]"
            }
        }
    }

    private fun mapCursorToLogEntity(cursor: app.cash.sqldelight.db.SqlCursor): com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity {
        return com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity(
            id = cursor.getLong(0)?.toInt() ?: 0,
            timestamp = cursor.getLong(1) ?: Clock.System.now().toEpochMilliseconds(),
            level = cursor.getString(2) ?: "INFO",
            message = cursor.getString(3) ?: "",
            exception = cursor.getString(4) ?: ""
        )
    }
}

/**
 * Implementation of TimedOperation
 */
private class TimedOperationImpl(
    override val operationName: String,
    private val logger: ILoggingService
) : TimedOperation {

    override val startTime: Instant = Clock.System.now()

    override fun complete(success: Boolean, additionalProperties: Map<String, Any?>) {
        val duration = getCurrentDurationMs()
        val status = if (success) "SUCCESS" else "FAILURE"
        val message = "Operation completed: $operationName | Duration: ${duration}ms | Status: $status"
        logger.logInfo(message, "TIMED_OPERATION")
    }

    override fun completeWithError(error: Throwable, additionalProperties: Map<String, Any?>) {
        val duration = getCurrentDurationMs()
        val message = "Operation failed: $operationName | Duration: ${duration}ms"
        logger.logError(message, error, "TIMED_OPERATION")
    }

    override fun checkpoint(name: String, properties: Map<String, Any?>) {
        val duration = getCurrentDurationMs()
        val message = "Operation checkpoint: $operationName | Checkpoint: $name | Duration: ${duration}ms"
        logger.logDebug(message, "TIMED_OPERATION")
    }

    override fun getCurrentDurationMs(): Long {
        return (Clock.System.now() - startTime).inWholeMilliseconds
    }
}