//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/SubscriptionExceptionMappingExtensions.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SubscriptionDomainException
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Extension to add subscription exception mapping support
 */
fun IInfrastructureExceptionMappingService.mapToSubscriptionDomainException(
    exception: Throwable,
    operation: String
): SubscriptionDomainException {
    return when {
        // Database constraint violations
        exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true -> {
            when {
                exception.message?.contains("userId", ignoreCase = true) == true ->
                    SubscriptionDomainException.subscriptionAlreadyExists("", "", exception)
                exception.message?.contains("transactionId", ignoreCase = true) == true ->
                    SubscriptionDomainException.invalidTransactionId("", exception)
                exception.message?.contains("purchaseToken", ignoreCase = true) == true ->
                    SubscriptionDomainException.invalidPurchaseToken(exception)
                else ->
                    SubscriptionDomainException.databaseError("Duplicate constraint violation", exception)
            }
        }

        exception.message?.contains("CHECK constraint failed", ignoreCase = true) == true ->
            SubscriptionDomainException.invalidDateRange(exception)

        exception.message?.contains("FOREIGN KEY constraint failed", ignoreCase = true) == true ->
            SubscriptionDomainException.subscriptionNotFound("unknown", exception)

        // SQL/Database errors
        isSqlException(exception) ->
            SubscriptionDomainException.databaseError("Database operation failed: ${exception.message}", exception)

        // Network errors - store connection issues
        isNetworkException(exception) ->
            SubscriptionDomainException.storeConnectionError(exception)

        // Timeout errors
        isTimeoutException(exception) ->
            SubscriptionDomainException.networkError("Operation timed out", exception)

        // HTTP errors - store API issues
        exception.message?.contains("401", ignoreCase = true) == true ->
            SubscriptionDomainException.purchaseVerificationFailed("", exception)

        exception.message?.contains("403", ignoreCase = true) == true ->
            SubscriptionDomainException.regionalRestriction("unknown", exception)

        exception.message?.contains("404", ignoreCase = true) == true ->
            SubscriptionDomainException.subscriptionNotFound("unknown", exception)

        exception.message?.contains("429", ignoreCase = true) == true ->
            SubscriptionDomainException.billingError("Rate limit exceeded", exception)

        exception.message?.contains("500", ignoreCase = true) == true ||
                exception.message?.contains("502", ignoreCase = true) == true ||
                exception.message?.contains("503", ignoreCase = true) == true ->
            SubscriptionDomainException.storeConnectionError(exception)

        // Billing and payment specific errors
        exception.message?.contains("insufficient funds", ignoreCase = true) == true ->
            SubscriptionDomainException.insufficientFunds(exception)

        exception.message?.contains("payment", ignoreCase = true) == true &&
                exception.message?.contains("failed", ignoreCase = true) == true ->
            SubscriptionDomainException.paymentMethodInvalid(exception)

        exception.message?.contains("expired", ignoreCase = true) == true &&
                exception.message?.contains("card", ignoreCase = true) == true ->
            SubscriptionDomainException.paymentMethodInvalid(exception)

        // Concurrent modification
        exception.message?.contains("optimistic", ignoreCase = true) == true ||
                exception.message?.contains("concurrent", ignoreCase = true) == true ->
            SubscriptionDomainException.concurrentModification("unknown", exception)

        // Authorization errors
        isUnauthorizedException(exception) ->
            SubscriptionDomainException.purchaseVerificationFailed("unauthorized", exception)

        // Default infrastructure error
        else -> SubscriptionDomainException.infrastructureError(
            operation,
            exception.message ?: "Unknown error",
            exception
        )
    }
}

/**
 * Enhanced exception mapping service with subscription support
 */
class EnhancedInfrastructureExceptionMappingService(
    private val logger: ILoggingService
) : IInfrastructureExceptionMappingService {

    override fun mapToLocationDomainException(exception: Throwable, operation: String): com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException {
        logger.logError("Infrastructure exception in $operation", exception, "LOCATION")

        return when {
            // Database constraint violations
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException("DUPLICATE_TITLE", "A location with this title already exists", exception)

            exception.message?.contains("CHECK constraint failed", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException("INVALID_COORDINATES", "Invalid coordinate values provided", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Network errors
            isNetworkException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException("NETWORK_ERROR", "Network operation failed: ${exception.message}", exception)

            // Timeout errors
            isTimeoutException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException("NETWORK_ERROR", "Operation timed out", exception)

            // Security/Authorization errors
            isUnauthorizedException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException("AUTHORIZATION_ERROR", "Access denied", exception)

            // Default infrastructure error
            else -> com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToWeatherDomainException(exception: Throwable, operation: String): com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException {
        logger.logError("Infrastructure exception in weather $operation", exception, "WEATHER")

        return when {
            // HTTP 401 - Unauthorized (Invalid API Key)
            exception.message?.contains("401", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException("INVALID_API_KEY", "Weather API authentication failed", exception)

            // HTTP 429 - Too Many Requests (Rate Limit)
            exception.message?.contains("429", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException("RATE_LIMIT_EXCEEDED", "Weather API rate limit exceeded", exception)

            // HTTP 404 - Not Found (Location not found)
            exception.message?.contains("404", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException("LOCATION_NOT_FOUND", "Weather data not available for location", exception)

            // General HTTP errors
            isNetworkException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException("API_UNAVAILABLE", "Weather API error: ${exception.message}", exception)

            // Timeout errors
            isTimeoutException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException("NETWORK_TIMEOUT", "Weather service request timed out", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Default infrastructure error
            else -> com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToSettingDomainException(exception: Throwable, operation: String): com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException {
        logger.logError("Infrastructure exception in settings $operation", exception, "SETTING")

        return when {
            // Unique constraint for setting keys
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException("DUPLICATE_KEY", "A setting with this key already exists", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Read-only setting errors
            isUnauthorizedException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException("READ_ONLY_SETTING", "Setting is read-only", exception)

            // Default infrastructure error
            else -> com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToTipDomainException(exception: Throwable, operation: String): com.x3squaredcircles.pixmap.shared.domain.exceptions.TipDomainException {
        logger.logError("Infrastructure exception in tips $operation", exception, "TIP")

        return when {
            // Unique constraint for tip titles
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.TipDomainException("DUPLICATE_TITLE", "A tip with this title already exists", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.TipDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Default infrastructure error
            else -> com.x3squaredcircles.pixmap.shared.domain.exceptions.TipDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    override fun mapToTipTypeDomainException(exception: Throwable, operation: String): com.x3squaredcircles.pixmap.shared.domain.exceptions.TipTypeDomainException {
        logger.logError("Infrastructure exception in tip types $operation", exception, "TIPTYPE")

        return when {
            // Unique constraint for tip type names
            exception.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.TipTypeDomainException("DUPLICATE_NAME", "A tip type with this name already exists", exception)

            // SQL/Database errors
            isSqlException(exception) ->
                com.x3squaredcircles.pixmap.shared.domain.exceptions.TipTypeDomainException("DATABASE_ERROR", "Database operation failed: ${exception.message}", exception)

            // Default infrastructure error
            else -> com.x3squaredcircles.pixmap.shared.domain.exceptions.TipTypeDomainException(
                "INFRASTRUCTURE_ERROR",
                "Infrastructure error in $operation: ${exception.message}",
                exception
            )
        }
    }

    /**
     * Maps exceptions to subscription domain exceptions
     */
    fun mapToSubscriptionDomainException(exception: Throwable, operation: String): SubscriptionDomainException {
        logger.logError("Infrastructure exception in subscription $operation", exception, "SUBSCRIPTION")
        return mapToSubscriptionDomainException(exception, operation)
    }
}

/**
 * Utility functions for exception classification
 */
private fun isSqlException(exception: Throwable): Boolean {
    return exception::class.simpleName?.contains("SQL", ignoreCase = true) == true ||
            exception.message?.contains("sql", ignoreCase = true) == true ||
            exception.message?.contains("database", ignoreCase = true) == true
}

private fun isNetworkException(exception: Throwable): Boolean {
    return exception is ConnectTimeoutException ||
            exception is SocketTimeoutException ||
            exception is ClientRequestException ||
            exception.message?.contains("network", ignoreCase = true) == true ||
            exception.message?.contains("connection", ignoreCase = true) == true
}

private fun isTimeoutException(exception: Throwable): Boolean {
    return exception is TimeoutCancellationException ||
            exception::class.simpleName?.contains("Timeout", ignoreCase = true) == true ||
            exception.message?.contains("timeout", ignoreCase = true) == true
}

private fun isUnauthorizedException(exception: Throwable): Boolean {
    return exception::class.simpleName?.contains("Unauthorized", ignoreCase = true) == true ||
            exception.message?.contains("unauthorized", ignoreCase = true) == true ||
            exception.message?.contains("access denied", ignoreCase = true) == true
}