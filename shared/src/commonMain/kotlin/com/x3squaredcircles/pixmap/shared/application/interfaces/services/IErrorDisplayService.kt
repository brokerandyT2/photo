// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IErrorDisplayService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.application.events.errors.DomainErrorEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Service interface for displaying aggregated error messages to the user
 */
interface IErrorDisplayService {

    /**
     * Flow that emits when aggregated errors are ready to be displayed
     */
    val errorsReady: SharedFlow<ErrorDisplayEventArgs>

    /**
     * Subscribes to error events
     */
    fun subscribeToErrors(handler: (ErrorDisplayEventArgs) -> Unit)

    /**
     * Unsubscribes from error events
     */
    fun unsubscribeFromErrors(handler: (ErrorDisplayEventArgs) -> Unit)

    /**
     * Manually trigger error display (for testing)
     */
    suspend fun triggerErrorDisplayAsync(errors: List<DomainErrorEvent>)

    /**
     * Displays a single error message immediately
     */
    suspend fun displayErrorAsync(message: String)

    /**
     * Displays multiple error messages as a batch
     */
    suspend fun displayErrorsAsync(messages: List<String>)

    /**
     * Clears all pending error displays
     */
    suspend fun clearErrors()
}

/**
 * Event arguments for error display events
 */
data class ErrorDisplayEventArgs(
    /**
     * The aggregated errors to display
     */
    val errors: List<DomainErrorEvent>,

    /**
     * The localized message to display to the user
     */
    val displayMessage: String
) {
    /**
     * Whether this is a single error or multiple errors
     */
    val isSingleError: Boolean
        get() = errors.size == 1

    /**
     * Error severity level for UI treatment
     */
    val severity: ErrorSeverity
        get() = when {
            errors.any { it.isSystemError } -> ErrorSeverity.CRITICAL
            errors.any { it.isValidationError } -> ErrorSeverity.WARNING
            else -> ErrorSeverity.INFO
        }

    /**
     * Categorized error messages by type
     */
    val categorizedMessages: Map<String, List<String>>
        get() = errors.groupBy { it.category }
            .mapValues { (_, events) -> events.map { it.message } }
}

/**
 * Error severity levels for UI treatment
 */
enum class ErrorSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Error display options for customizing presentation
 */
data class ErrorDisplayOptions(
    val showToast: Boolean = true,
    val showDialog: Boolean = false,
    val autoHide: Boolean = true,
    val duration: Long = 3000L, // milliseconds
    val allowDismiss: Boolean = true,
    val priority: ErrorPriority = ErrorPriority.NORMAL
)

/**
 * Error priority levels for display ordering
 */
enum class ErrorPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * Extension properties for DomainErrorEvent to support error display service
 */
val DomainErrorEvent.isSystemError: Boolean
    get() = this.category.contains("system", ignoreCase = true) ||
            this.category.contains("database", ignoreCase = true) ||
            this.category.contains("network", ignoreCase = true)

val DomainErrorEvent.isValidationError: Boolean
    get() = this.category.contains("validation", ignoreCase = true) ||
            this.category.contains("input", ignoreCase = true)

val DomainErrorEvent.category: String
    get() = this.javaClass.simpleName.replace("Event", "").replace("Error", "")