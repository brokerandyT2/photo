// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/behaviors/LoggingBehavior.kt
package com.x3squaredcircles.pixmap.shared.application.behaviors

import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IPipelineBehavior
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pipeline behavior for logging request execution with optimized performance
 */
class LoggingBehavior<TRequest : IRequest<TResponse>, TResponse>(
    private val logger: ILoggingService,
    private val mediator: IMediator,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : IPipelineBehavior<TRequest, TResponse> {

    companion object {
        private const val MAX_SERIALIZATION_LENGTH = 2048
        private const val SLOW_REQUEST_THRESHOLD_MS = 500L
    }

    // Optimized JSON configuration - reused for performance
    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun handle(
        request: TRequest,
        next: suspend () -> TResponse
    ): TResponse {
        val requestName = request::class.simpleName ?: "UnknownRequest"
        val startTime = Clock.System.now()

        // Log request start with minimal overhead
        logger.logDebug("Starting request: $requestName")

        return try {
            // Execute the request
            val response = next()
            val endTime = Clock.System.now()
            val duration = (endTime - startTime).inWholeMilliseconds

            // Log completion
            if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                logger.logWarning("Slow request completed: $requestName in ${duration}ms")

                // Async serialization for slow requests (fire-and-forget)
                coroutineScope.launch {
                    try {
                        val requestDetails = serializeRequestAsync(request)
                        logger.logInfo("Slow request details: $requestName - $requestDetails")
                    } catch (e: Exception) {
                        logger.logDebug("Failed to serialize slow request details: ${e.message}")
                    }
                }
            } else {
                logger.logDebug("Request completed: $requestName in ${duration}ms")
            }

            response
        } catch (exception: Exception) {
            val endTime = Clock.System.now()
            val duration = (endTime - startTime).inWholeMilliseconds

            logger.logError(
                "Request failed: $requestName in ${duration}ms",
                exception
            )

            // Async error details logging (fire-and-forget)
            coroutineScope.launch {
                try {
                    val requestDetails = serializeRequestAsync(request)
                    logger.logError(
                        "Failed request details: $requestName - $requestDetails",
                        exception
                    )
                } catch (serializationException: Exception) {
                    logger.logDebug("Failed to serialize failed request details: ${serializationException.message}")
                }
            }

            throw exception
        }
    }

    /**
     * Efficiently serializes the request with size limits and error handling
     */
    private suspend fun serializeRequestAsync(request: TRequest): String {
        return try {
            // Use compact serialization for performance
            val json = compactJson.encodeToString(request)

            // Truncate if too long to prevent memory issues
            if (json.length > MAX_SERIALIZATION_LENGTH) {
                "${json.substring(0, MAX_SERIALIZATION_LENGTH)}... (truncated)"
            } else {
                json
            }
        } catch (exception: Exception) {
            // Fallback to type name if serialization fails
            "<${request::class.simpleName}> (serialization failed: ${exception.message})"
        }
    }
}

/**
 * Extension functions for performance tracking
 */
suspend inline fun <T> ILoggingService.trackOperation(
    operationName: String,
    operation: suspend () -> T
): T {
    logOperationStart(operationName)
    val startTime = Clock.System.now()

    return try {
        val result = operation()
        val endTime = Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        logOperationComplete(operationName, duration, "Success")
        result
    } catch (exception: Exception) {
        val endTime = Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        logOperationFailure(operationName, exception, duration)
        throw exception
    }
}

/**
 * Structured logging helper
 */
fun ILoggingService.logWithStructuredData(
    message: String,
    data: Map<String, Any> = emptyMap(),
    level: com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel = com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel.INFO
) {
    val structuredMessage = if (data.isNotEmpty()) {
        val dataString = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        "$message | Data: [$dataString]"
    } else {
        message
    }

    when (level) {
        com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel.VERBOSE -> logVerbose(structuredMessage)
        com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel.DEBUG -> logDebug(structuredMessage)
        com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel.INFO -> logInfo(structuredMessage)
        com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel.WARNING -> logWarning(structuredMessage)
        com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel.ERROR -> logError(structuredMessage)
        com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel.NONE -> { /* Do nothing */ }
    }
}