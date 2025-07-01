// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/LoggingService.kt

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.logging.Logger

/**
 * Logging service implementation with database persistence and fault tolerance
 */
class LoggingService(
    private val databaseContext: IDatabaseContext,
    private val logger: Logger
) : ILoggingService {

    private var currentLogLevel = LogLevel.INFO
    private var loggingEnabled = true
    private val loggingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun logInfo(message: String, tag: String?) {
        logMessage(LogLevel.INFO, message, null, tag)
    }

    override fun logWarning(message: String, tag: String?) {
        logMessage(LogLevel.WARNING, message, null, tag)
    }

    override fun logError(message: String, exception: Throwable?, tag: String?) {
        logMessage(LogLevel.ERROR, message, exception, tag)
    }

    override fun logDebug(message: String, tag: String?) {
        logMessage(LogLevel.DEBUG, message, null, tag)
    }

    override fun logVerbose(message: String, tag: String?) {
        logMessage(LogLevel.VERBOSE, message, null, tag)
    }

    override fun logOperationStart(operationName: String, parameters: Map<String, Any>?) {
        val parametersString = parameters?.let { params ->
            params.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } ?: ""

        val message = "Operation started: $operationName" +
                if (parametersString.isNotEmpty()) " | Parameters: [$parametersString]" else ""

        logInfo(message, "OPERATION")
    }

    override fun logOperationComplete(operationName: String, durationMs: Long?, result: String?) {
        val durationString = durationMs?.let { " | Duration: ${it}ms" } ?: ""
        val resultString = result?.let { " | Result: $it" } ?: ""

        val message = "Operation completed: $operationName$durationString$resultString"
        logInfo(message, "OPERATION")
    }

    override fun logOperationFailure(operationName: String, exception: Throwable, durationMs: Long?) {
        val durationString = durationMs?.let { " | Duration: ${it}ms" } ?: ""
        val message = "Operation failed: $operationName$durationString | Error: ${exception.message}"
        logError(message, exception, "OPERATION")
    }

    override fun setLogLevel(level: LogLevel) {
        currentLogLevel = level
        logInfo("Log level changed to $level", "LOGGING")
    }

    override fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
        if (enabled) {
            logInfo("Logging enabled", "LOGGING")
        }
    }

    // Database operations
    suspend fun logToDatabaseAsync(level: LogLevel, message: String, exception: Throwable? = null, tag: String? = null) {
        try {
            val logEntity = LogEntity(
                id = 0,
                timestamp = Clock.System.now(),
                level = level.name,
                message = message,
                exception = exception?.stackTraceToString() ?: "",
                tag = tag ?: ""
            )

            databaseContext.insertAsync(logEntity) { entity ->
                insertLog(entity)
            }
        } catch (ex: Exception) {
            // If we can't log to database, log to the standard logger
            logger.error("Failed to write log to database", ex)
            // Do NOT rethrow - this method should be fault-tolerant
        }
    }

    suspend fun getLogsAsync(count: Int = 100): List<LogEntity> {
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

    suspend fun getLogsByLevelAsync(level: LogLevel, count: Int = 100): List<LogEntity> {
        return try {
            databaseContext.queryAsync(
                "SELECT * FROM LogEntity WHERE Level = ? ORDER BY Timestamp DESC LIMIT ?",
                ::mapCursorToLogEntity,
                level.name,
                count
            )
        } catch (ex: Exception) {
            logger.error("Failed to retrieve logs by level from database", ex)
            emptyList()
        }
    }

    suspend fun getLogsByTagAsync(tag: String, count: Int = 100): List<LogEntity> {
        return try {
            databaseContext.queryAsync(
                "SELECT * FROM LogEntity WHERE Tag = ? ORDER BY Timestamp DESC LIMIT ?",
                ::mapCursorToLogEntity,
                tag,
                count
            )
        } catch (ex: Exception) {
            logger.error("Failed to retrieve logs by tag from database", ex)
            emptyList()
        }
    }

    suspend fun getLogsByDateRangeAsync(startTime: Instant, endTime: Instant): List<LogEntity> {
        return try {
            databaseContext.queryAsync(
                "SELECT * FROM LogEntity WHERE Timestamp BETWEEN ? AND ? ORDER BY Timestamp DESC",
                ::mapCursorToLogEntity,
                startTime.toString(),
                endTime.toString()
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
                olderThan.toString()
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
        if (!loggingEnabled || level.ordinal < currentLogLevel.ordinal) {
            return
        }

        // Log to standard logger immediately
        when (level) {
            LogLevel.VERBOSE -> logger.debug(formatLogMessage(message, tag))
            LogLevel.DEBUG -> logger.debug(formatLogMessage(message, tag))
            LogLevel.INFO -> logger.info(formatLogMessage(message, tag))
            LogLevel.WARNING -> logger.warning(formatLogMessage(message, tag))
            LogLevel.ERROR -> logger.error(formatLogMessage(message, tag), exception)
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

    private suspend fun insertLog(entity: LogEntity): Long {
        return databaseContext.executeAsync(
            """INSERT INTO LogEntity (Timestamp, Level, Message, Exception, Tag)
               VALUES (?, ?, ?, ?, ?)""",
            entity.timestamp.toString(),
            entity.level,
            entity.message,
            entity.exception,
            entity.tag
        ).toLong()
    }

    private fun mapCursorToLogEntity(cursor: SqlCursor): LogEntity {
        return LogEntity(
            id = cursor.getInt(0) ?: 0,
            timestamp = Instant.parse(cursor.getString(1) ?: Clock.System.now().toString()),
            level = cursor.getString(2) ?: "INFO",
            message = cursor.getString(3) ?: "",
            exception = cursor.getString(4) ?: "",
            tag = cursor.getString(5) ?: ""
        )
    }
}

// Extension functions for structured logging
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

// Performance tracking extensions
inline fun <T> ILoggingService.trackOperation(
    operationName: String,
    parameters: Map<String, Any>? = null,
    operation: () -> T
): T {
    val startTime = Clock.System.now()
    logOperationStart(operationName, parameters)

    return try {
        val result = operation()
        val duration = Clock.System.now() - startTime
        logOperationComplete(operationName, duration.inWholeMilliseconds, result.toString())
        result
    } catch (exception: Throwable) {
        val duration = Clock.System.now() - startTime
        logOperationFailure(operationName, exception, duration.inWholeMilliseconds)
        throw exception
    }
}

// Suspend version for coroutines
suspend inline fun <T> ILoggingService.trackOperationSuspend(
    operationName: String,
    parameters: Map<String, Any>? = null,
    operation: suspend () -> T
): T {
    val startTime = Clock.System.now()
    logOperationStart(operationName, parameters)

    return try {
        val result = operation()
        val duration = Clock.System.now() - startTime
        logOperationComplete(operationName, duration.inWholeMilliseconds, result.toString())
        result
    } catch (exception: Throwable) {
        val duration = Clock.System.now() - startTime
        logOperationFailure(operationName, exception, duration.inWholeMilliseconds)
        throw exception
    }
}

// Log entity data class
data class LogEntity(
    val id: Int = 0,
    val timestamp: Instant,
    val level: String,
    val message: String,
    val exception: String = "",
    val tag: String = ""
)

