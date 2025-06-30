// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/InfrastructureExceptionMappingService.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.exceptions.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Interface for mapping infrastructure exceptions to domain exceptions
 */
interface IInfrastructureExceptionMappingService {
    fun mapToLocationDomainException(exception: Throwable, operation: String): LocationDomainException
    fun mapToWeatherDomainException(exception: Throwable, operation: String): WeatherDomainException
    fun mapToSettingDomainException(exception: Throwable, operation: String): SettingDomainException
    fun mapToTipDomainException(exception: Throwable, operation: String): TipDomainException
    fun mapToTipTypeDomainException(exception: Throwable, operation: String): TipTypeDomainException
}

/**
 * Service for mapping infrastructure exceptions to appropriate domain exceptions
 */
class InfrastructureExceptionMappingService(
    private val logger: ILoggingService
) : IInfrastructureExceptionMappingService {

    override fun mapToLocationDomainException(exception: Throwable, operation: String): LocationDomainException {
        logger.logError("Infrastructure exception in $operation", exception, "LOCATION")

        return when {
            // Database constraint violations
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                LocationDomainException("DUPLICATE_TITLE", "A location with this title already exists", exception)

            exception.message?.contains("CHECK constraint failed", ignoreCase = true) == true ->
                LocationDomainException("INVALID_COORDINATES", "Invalid coordinate values provided", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                LocationDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Network errors
            isNetworkException(exception) ->
                LocationDomainException("NETWORK_ERROR", "Network operation failed: ${exception.message}", exception)

            // Timeout errors
            isTimeoutException(exception) ->
                LocationDomainException("NETWORK_ERROR", "Operation timed out", exception)

            // Security/Authorization errors
            isUnauthorizedException(exception) ->
                LocationDomainException("AUTHORIZATION_ERROR", "Access denied", exception)

            // Default infrastructure error
            else -> LocationDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToWeatherDomainException(exception: Throwable, operation: String): WeatherDomainException {
        logger.logError("Infrastructure exception in weather $operation", exception, "WEATHER")

        return when {
            // HTTP 401 - Unauthorized (Invalid API Key)
            exception.message?.contains("401", ignoreCase = true) == true ->
                WeatherDomainException("INVALID_API_KEY", "Weather API authentication failed", exception)

            // HTTP 429 - Too Many Requests (Rate Limit)
            exception.message?.contains("429", ignoreCase = true) == true ->
                WeatherDomainException("RATE_LIMIT_EXCEEDED", "Weather API rate limit exceeded", exception)

            // HTTP 404 - Not Found (Location not found)
            exception.message?.contains("404", ignoreCase = true) == true ->
                WeatherDomainException("LOCATION_NOT_FOUND", "Weather data not available for location", exception)

            // General HTTP errors
            isNetworkException(exception) ->
                WeatherDomainException("API_UNAVAILABLE", "Weather API error: ${exception.message}", exception)

            // Timeout errors
            isTimeoutException(exception) ->
                WeatherDomainException("NETWORK_TIMEOUT", "Weather service request timed out", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                WeatherDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Default infrastructure error
            else -> WeatherDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToSettingDomainException(exception: Throwable, operation: String): SettingDomainException {
        logger.logError("Infrastructure exception in settings $operation", exception, "SETTING")

        return when {
            // Database constraint violations
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                SettingDomainException("DUPLICATE_KEY", "A setting with this key already exists", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                SettingDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Security/Authorization errors (read-only settings)
            isUnauthorizedException(exception) ->
                SettingDomainException("READ_ONLY_SETTING", "Setting is read-only", exception)

            // Default infrastructure error
            else -> SettingDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToTipDomainException(exception: Throwable, operation: String): TipDomainException {
        logger.logError("Infrastructure exception in tips $operation", exception, "TIP")

        return when {
            // Database constraint violations
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                TipDomainException("DUPLICATE_TITLE", "A tip with this title already exists", exception)

            exception.message?.contains("FOREIGN KEY constraint failed", ignoreCase = true) == true ->
                TipDomainException("INVALID_TIP_TYPE", "Invalid tip type specified", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                TipDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Default infrastructure error
            else -> TipDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToTipTypeDomainException(exception: Throwable, operation: String): TipTypeDomainException {
        logger.logError("Infrastructure exception in tip types $operation", exception, "TIP_TYPE")

        return when {
            // Database constraint violations
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                TipTypeDomainException("DUPLICATE_NAME", "A tip type with this name already exists", exception)

            exception.message?.contains("FOREIGN KEY constraint failed", ignoreCase = true) == true ->
                TipTypeDomainException("TIP_TYPE_IN_USE", "Cannot delete tip type that is in use", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                TipTypeDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Default infrastructure error
            else -> TipTypeDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    /**
     * Helper functions to categorize exception types
     */
    private fun isSqlException(exception: Throwable): Boolean {
        return exception::class.simpleName?.contains("SQL", ignoreCase = true) == true ||
                exception::class.simpleName?.contains("Database", ignoreCase = true) == true ||
                exception.message?.contains("database", ignoreCase = true) == true ||
                exception.message?.contains("sql", ignoreCase = true) == true
    }

    private fun isNetworkException(exception: Throwable): Boolean {
        return when (exception) {
            is ClientRequestException -> true
            is ServerResponseException -> true
            is RedirectResponseException -> true
            is ConnectTimeoutException -> true
            is SocketTimeoutException -> true
            is UnresolvedAddressException -> true
            else -> exception::class.simpleName?.contains("Http", ignoreCase = true) == true ||
                    exception::class.simpleName?.contains("Network", ignoreCase = true) == true ||
                    exception.message?.contains("network", ignoreCase = true) == true ||
                    exception.message?.contains("connection", ignoreCase = true) == true
        }
    }

    private fun isTimeoutException(exception: Throwable): Boolean {
        return when (exception) {
            is TimeoutCancellationException -> true
            is ConnectTimeoutException -> true
            is SocketTimeoutException -> true
            else -> exception::class.simpleName?.contains("Timeout", ignoreCase = true) == true ||
                    exception.message?.contains("timeout", ignoreCase = true) == true ||
                    exception.message?.contains("timed out", ignoreCase = true) == true
        }
    }

    private fun isUnauthorizedException(exception: Throwable): Boolean {
        return exception::class.simpleName?.contains("Unauthorized", ignoreCase = true) == true ||
                exception::class.simpleName?.contains("Security", ignoreCase = true) == true ||
                exception.message?.contains("unauthorized", ignoreCase = true) == true ||
                exception.message?.contains("access denied", ignoreCase = true) == true ||
                exception.message?.contains("permission", ignoreCase = true) == true
    }
}

/**
 * Repository exception wrapper for consistent error handling
 */
object RepositoryExceptionWrapper {

    /**
     * Executes an operation with proper exception mapping
     */
    suspend fun <T> executeWithExceptionMappingAsync(
        operation: suspend () -> T,
        exceptionMapper: IInfrastructureExceptionMappingService,
        operationName: String,
        entityType: String,
        logger: ILoggingService
    ): T {
        return try {
            operation()
        } catch (ex: Exception) {
            logger.logError("Repository operation $operationName failed for $entityType", ex)

            val domainException = when (entityType.lowercase()) {
                "location" -> exceptionMapper.mapToLocationDomainException(ex, operationName)
                "weather" -> exceptionMapper.mapToWeatherDomainException(ex, operationName)
                "setting" -> exceptionMapper.mapToSettingDomainException(ex, operationName)
                "tip" -> exceptionMapper.mapToTipDomainException(ex, operationName)
                "tiptype" -> exceptionMapper.mapToTipTypeDomainException(ex, operationName)
                else -> ex
            }

            throw domainException
        }
    }

    /**
     * Synchronous version of the exception wrapper
     */
    fun <T> executeWithExceptionMapping(
        operation: () -> T,
        exceptionMapper: IInfrastructureExceptionMappingService,
        operationName: String,
        entityType: String,
        logger: ILoggingService
    ): T {
        return try {
            operation()
        } catch (ex: Exception) {
            logger.logError("Repository operation $operationName failed for $entityType", ex)

            val domainException = when (entityType.lowercase()) {
                "location" -> exceptionMapper.mapToLocationDomainException(ex, operationName)
                "weather" -> exceptionMapper.mapToWeatherDomainException(ex, operationName)
                "setting" -> exceptionMapper.mapToSettingDomainException(ex, operationName)
                "tip" -> exceptionMapper.mapToTipDomainException(ex, operationName)
                "tiptype" -> exceptionMapper.mapToTipTypeDomainException(ex, operationName)
                else -> ex
            }

            throw domainException
        }
    }
}

/**
 * Extension functions for common exception handling patterns
 */
fun IInfrastructureExceptionMappingService.mapDatabaseException(
    exception: Throwable,
    entityType: String,
    operation: String
): RuntimeException {
    return when (entityType.lowercase()) {
        "location" -> mapToLocationDomainException(exception, operation)
        "weather" -> mapToWeatherDomainException(exception, operation)
        "setting" -> mapToSettingDomainException(exception, operation)
        "tip" -> mapToTipDomainException(exception, operation)
        "tiptype" -> mapToTipTypeDomainException(exception, operation)
        else -> RuntimeException("Unknown entity type: $entityType", exception)
    }
}

/**
 * Batch exception mapping for multiple operations
 */
data class ExceptionMappingResult<T>(
    val result: T?,
    val exception: Throwable?
)

suspend fun <T> IInfrastructureExceptionMappingService.executeWithMappingAsync(
    operation: suspend () -> T,
    entityType: String,
    operationName: String,
    logger: ILoggingService
): ExceptionMappingResult<T> {
    return try {
        val result = operation()
        ExceptionMappingResult(result, null)
    } catch (ex: Exception) {
        val mappedException = mapDatabaseException(ex, entityType, operationName)
        ExceptionMappingResult(null, mappedException)
    }
}