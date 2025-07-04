//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/behaviors/LoggingBehavior.kt
package com.x3squaredcircles.pixmap.shared.application.behaviors

import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IPipelineBehavior
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.LogLevel
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
        logger.debug("Starting request: $requestName")

        return try {
            // Execute the request
            val response = next()
            val endTime = Clock.System.now()
            val duration = (endTime - startTime).inWholeMilliseconds

            // Log completion
            if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                logger.warning("Slow request completed: $requestName in ${duration}ms")

                // Async serialization for slow requests (fire-and-forget)
                coroutineScope.launch {
                    try {
                        val requestDetails = serializeRequestAsync(request)
                        logger.info("Slow request details: $requestName - $requestDetails")
                    } catch (e: Exception) {
                        logger.debug("Failed to serialize slow request details: ${e.message}")
                    }
                }
            } else {
                logger.debug("Request completed: $requestName in ${duration}ms")
            }

            response
        } catch (exception: Exception) {
            val endTime = Clock.System.now()
            val duration = (endTime - startTime).inWholeMilliseconds

            logger.error(
                "Request failed: $requestName in ${duration}ms",
                exception
            )

            // Async error details logging (fire-and-forget)
            coroutineScope.launch {
                try {
                    val requestDetails = serializeRequestAsync(request)
                    logger.error(
                        "Failed request details: $requestName - $requestDetails",
                        exception
                    )
                } catch (serializationException: Exception) {
                    logger.debug("Failed to serialize failed request details: ${serializationException.message}")
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
            // Use toString() for generic types since we can't guarantee serializability
            val serializedContent = request.toString()

            // Truncate if too long to prevent memory issues
            if (serializedContent.length > MAX_SERIALIZATION_LENGTH) {
                "${serializedContent.substring(0, MAX_SERIALIZATION_LENGTH)}... (truncated)"
            } else {
                serializedContent
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
    performance("$operationName started", 0)
    val startTime = Clock.System.now()

    return try {
        val result = operation()
        val endTime = Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        performance("$operationName completed", duration, true)
        result
    } catch (exception: Exception) {
        val endTime = Clock.System.now()
        val duration = (endTime - startTime).inWholeMilliseconds
        performance("$operationName failed", duration, false)
        error("Operation failed: $operationName", exception)
        throw exception
    }
}

/**
 * Structured logging helper
 */
fun ILoggingService.logWithStructuredData(
    message: String,
    data: Map<String, Any> = emptyMap(),
    level: LogLevel = LogLevel.INFO
) {
    val structuredMessage = if (data.isNotEmpty()) {
        val dataString = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        "$message | Data: [$dataString]"
    } else {
        message
    }

    when (level) {
        LogLevel.DEBUG -> debug(structuredMessage)
        LogLevel.INFO -> info(structuredMessage)
        LogLevel.WARNING -> warning(structuredMessage)
        LogLevel.ERROR -> error(structuredMessage)
        LogLevel.CRITICAL -> critical(structuredMessage)
        LogLevel.VERBOSE -> critical(structuredMessage)
        LogLevel.NONE -> debug(structuredMessage)
    }
}