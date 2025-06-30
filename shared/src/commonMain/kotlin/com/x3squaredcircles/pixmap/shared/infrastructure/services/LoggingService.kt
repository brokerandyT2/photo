// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/LoggingService.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel
import com.x3squaredcircles.pixmap.shared.infrastructure.persistence.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.persistence.entities.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Logging service implementation with database persistence and fault tolerance
 */
class LoggingService(
    private val databaseContext: IDatabaseContext
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
        val message = "Operation failed: $operationName$durationString"

        logError(message, exception, "OPERATION")
    }

    override fun setLogLevel(level: LogLevel) {
        currentLogLevel = level
        logInfo("Log level changed to: $level", "SYSTEM")
    }

    override fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
        if (enabled) {
            logInfo("Logging enabled", "SYSTEM")
        }
    }

    /**
     * Logs message to database asynchronously with fault tolerance
     */
    suspend fun logToDatabaseAsync(level: LogLevel, message: String, exception: Throwable? = null) {
        try {
            val log = Log(
                timestamp = Clock.System.now(),
                level = level.name,
                message = message,
                exception = exception?.stackTraceToString() ?: ""
            )

            databaseContext.insertAsync(log)
        } catch (ex: Exception) {
            // If we can't log to database, log to console as fallback
            println("Failed to write log to database: ${ex.message}")
            // Do NOT rethrow - this method should be fault-tolerant
        }
    }

    /**
     * Retrieves logs from database with error handling
     */
    suspend fun getLogsAsync(count: Int = 100): List<Log> {
        return try {
            databaseContext.queryAsync<Log>(
                "SELECT * FROM Log ORDER BY Timestamp DESC LIMIT ?",
                arrayOf(count)
            )
        } catch (ex: Exception) {
            println("Failed to retrieve logs from database: ${ex.message}")
            emptyList()
        }
    }

    /**
     * Clears all logs from database
     */
    suspend fun clearLogsAsync() {
        try {
            databaseContext.executeAsync("DELETE FROM Log")
            logInfo("Cleared all logs from database", "SYSTEM")
        } catch (ex: Exception) {
            logError("Failed to clear logs from database", ex, "SYSTEM")
            throw ex
        }
    }

    /**
     * Gets logs by level with pagination
     */
    suspend fun getLogsByLevelAsync(level: LogLevel, offset: Int = 0, limit: Int = 50): List<Log> {
        return try {
            databaseContext.queryAsync<Log>(
                "SELECT * FROM Log WHERE Level = ? ORDER BY Timestamp DESC LIMIT ? OFFSET ?",
                arrayOf(level.name, limit, offset)
            )
        } catch (ex: Exception) {
            println("Failed to retrieve logs by level from database: ${ex.message}")
            emptyList()
        }
    }

    /**
     * Gets logs within a time range
     */
    suspend fun getLogsByTimeRangeAsync(
        startTime: Instant,
        endTime: Instant,
        limit: Int = 100
    ): List<Log> {
        return try {
            databaseContext.queryAsync<Log>(
                "SELECT * FROM Log WHERE Timestamp >= ? AND Timestamp <= ? ORDER BY Timestamp DESC LIMIT ?",
                arrayOf(startTime.toString(), endTime.toString(), limit)
            )
        } catch (ex: Exception) {
            println("Failed to retrieve logs by time range from database: ${ex.message}")
            emptyList()
        }
    }

    /**
     * Gets log count by level
     */
    suspend fun getLogCountByLevelAsync(level: LogLevel): Int {
        return try {
            val result = databaseContext.scalarAsync<Int>(
                "SELECT COUNT(*) FROM Log WHERE Level = ?",
                arrayOf(level.name)
            )
            result ?: 0
        } catch (ex: Exception) {
            println("Failed to get log count by level: ${ex.message}")
            0
        }
    }

    /**
     * Deletes old logs beyond a certain count to prevent database bloat
     */
    suspend fun pruneOldLogsAsync(keepRecentCount: Int = 1000) {
        try {
            databaseContext.executeAsync(
                """
                DELETE FROM Log 
                WHERE Id NOT IN (
                    SELECT Id FROM Log 
                    ORDER BY Timestamp DESC 
                    LIMIT ?
                )
                """.trimIndent(),
                arrayOf(keepRecentCount)
            )
            logInfo("Pruned old logs, keeping $keepRecentCount recent entries", "SYSTEM")
        } catch (ex: Exception) {
            logError("Failed to prune old logs", ex, "SYSTEM")
        }
    }

    private fun logMessage(level: LogLevel, message: String, exception: Throwable?, tag: String?) {
        if (!loggingEnabled || level < currentLogLevel) {
            return
        }

        val taggedMessage = tag?.let { "[$it] $message" } ?: message

        // Log to console immediately for debugging
        when (level) {
            LogLevel.VERBOSE -> println("VERBOSE: $taggedMessage")
            LogLevel.DEBUG -> println("DEBUG: $taggedMessage")
            LogLevel.INFO -> println("INFO: $taggedMessage")
            LogLevel.WARNING -> println("WARNING: $taggedMessage")
            LogLevel.ERROR -> {
                println("ERROR: $taggedMessage")
                exception?.let { println("Exception: ${it.stackTraceToString()}") }
            }
            LogLevel.NONE -> { /* Do nothing */ }
        }

        // Asynchronously log to database with fault tolerance
        loggingScope.launch {
            logToDatabaseAsync(level, taggedMessage, exception)
        }
    }

    /**
     * Extension for batch logging operations
     */
    suspend fun logBatchAsync(logs: List<LogEntry>) {
        try {
            val logEntities = logs.map { entry ->
                Log(
                    timestamp = entry.timestamp,
                    level = entry.level.name,
                    message = entry.message,
                    exception = entry.exception?.stackTraceToString() ?: ""
                )
            }

            databaseContext.insertBatchAsync(logEntities)
        } catch (ex: Exception) {
            println("Failed to write batch logs to database: ${ex.message}")
        }
    }
}

/**
 * Data class for batch logging operations
 */
data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val message: String,
    val exception: Throwable? = null,
    val tag: String? = null
)

/**
 * Log statistics for monitoring
 */
data class LogStatistics(
    val totalLogs: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val debugCount: Int,
    val verboseCount: Int,
    val oldestLogTime: Instant?,
    val newestLogTime: Instant?
)

/**
 * Extension function for getting comprehensive log statistics
 */
suspend fun LoggingService.getLogStatisticsAsync(): LogStatistics {
    return try {
        val totalLogs = databaseContext.scalarAsync<Int>("SELECT COUNT(*) FROM Log") ?: 0
        val errorCount = getLogCountByLevelAsync(LogLevel.ERROR)
        val warningCount = getLogCountByLevelAsync(LogLevel.WARNING)
        val infoCount = getLogCountByLevelAsync(LogLevel.INFO)
        val debugCount = getLogCountByLevelAsync(LogLevel.DEBUG)
        val verboseCount = getLogCountByLevelAsync(LogLevel.VERBOSE)

        val oldestLog = databaseContext.scalarAsync<String>(
            "SELECT MIN(Timestamp) FROM Log"
        )?.let { Instant.parse(it) }

        val newestLog = databaseContext.scalarAsync<String>(
            "SELECT MAX(Timestamp) FROM Log"
        )?.let { Instant.parse(it) }

        LogStatistics(
            totalLogs = totalLogs,
            errorCount = errorCount,
            warningCount = warningCount,
            infoCount = infoCount,
            debugCount = debugCount,
            verboseCount = verboseCount,
            oldestLogTime = oldestLog,
            newestLogTime = newestLog
        )
    } catch (ex: Exception) {
        println("Failed to get log statistics: ${ex.message}")
        LogStatistics(0, 0, 0, 0, 0, 0, null, null)
    }
}