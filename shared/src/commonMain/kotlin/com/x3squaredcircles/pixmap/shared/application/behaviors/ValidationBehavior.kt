// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/behaviors/ValidationBehavior.kt
package com.x3squaredcircles.pixmap.shared.application.behaviors

import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.events.errors.ValidationErrorEvent
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IPipelineBehavior
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Pipeline behavior for request validation using fail-fast strategy
 */
class ValidationBehavior<TRequest : IRequest<TResponse>, TResponse>(
    private val validators: List<IValidator<TRequest>>,
    private val mediator: IMediator,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : IPipelineBehavior<TRequest, TResponse> {

    override suspend fun handle(
        request: TRequest,
        next: suspend () -> TResponse
    ): TResponse {
        // Fast path: no validators
        if (validators.isEmpty()) {
            return next()
        }

        // Validate with fail-fast strategy
        val validationResult = validateWithFailFast(request)

        if (!validationResult.isValid) {
            // Publish validation error event (fire-and-forget for performance)
            val entityType = request::class.simpleName?.replace("Command", "")?.replace("Query", "") ?: "Unknown"
            val handlerName = "${request::class.simpleName}Handler"

            coroutineScope.launch {
                try {
                    val validationErrors = mapOf(
                        "ValidationErrors" to validationResult.errors
                    )
                    val validationEvent = ValidationErrorEvent(entityType, validationErrors, handlerName)
                    mediator.publish(validationEvent)
                } catch (e: Exception) {
                    // Swallow exceptions from event publishing to not break the main flow
                }
            }

            // Create validation error response
            throw ValidationException(validationResult.errors)
        }

        return next()
    }

    private fun validateWithFailFast(request: TRequest): ValidationResult {
        val allErrors = mutableListOf<String>()

        for (validator in validators) {
            val result = validator.validate(request)
            if (!result.isValid) {
                allErrors.addAll(result.errors)
                // Continue collecting all errors (not truly fail-fast, but comprehensive)
            }
        }

        return if (allErrors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(allErrors)
        }
    }
}

/**
 * Exception thrown when validation fails
 */
class ValidationException(
    val validationErrors: List<String>
) : Exception("Validation failed: ${validationErrors.joinToString(", ")}")

/**
 * Extension function for fast validation check
 */
suspend fun <T> IValidator<T>.isValidFast(instance: T): Boolean {
    return try {
        validate(instance).isValid
    } catch (e: Exception) {
        false
    }
}