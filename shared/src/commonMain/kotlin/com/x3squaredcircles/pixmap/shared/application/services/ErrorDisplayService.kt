// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/services/ErrorDisplayService.kt
package com.x3squaredcircles.pixmap.shared.application.services

import com.x3squaredcircles.pixmap.shared.application.events.errors.DomainErrorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Service for managing and displaying error messages to users
 */
class ErrorDisplayService {
    private val errorChannel = Channel<DomainErrorEvent>(capacity = 1000)
    private val _errorsReadyFlow = MutableSharedFlow<ErrorDisplayEventArgs>()

    val errorsReady: Flow<ErrorDisplayEventArgs> = _errorsReadyFlow.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val ERROR_AGGREGATION_WINDOW_MS = 500L
        private const val MAX_ERRORS_PER_BATCH = 10
    }

    init {
        startErrorProcessing()
    }

    private fun startErrorProcessing() {
        coroutineScope.launch {
            val errorBatch = mutableListOf<DomainErrorEvent>()

            errorChannel.receiveAsFlow().collect { error ->
                errorBatch.add(error)

                if (errorBatch.size >= MAX_ERRORS_PER_BATCH) {
                    processErrorBatch(errorBatch.toList())
                    errorBatch.clear()
                } else {
                    delay(ERROR_AGGREGATION_WINDOW_MS)
                    if (errorBatch.isNotEmpty()) {
                        processErrorBatch(errorBatch.toList())
                        errorBatch.clear()
                    }
                }
            }
        }
    }

    suspend fun handleError(errorEvent: DomainErrorEvent) {
        errorChannel.trySend(errorEvent)
    }

    private suspend fun processErrorBatch(errors: List<DomainErrorEvent>) {
        val displayMessage = generateDisplayMessage(errors)
        val eventArgs = ErrorDisplayEventArgs(errors, displayMessage)
        _errorsReadyFlow.emit(eventArgs)
    }

    private fun generateDisplayMessage(errors: List<DomainErrorEvent>): String {
        return if (errors.size == 1) {
            getLocalizedErrorMessage(errors.first())
        } else {
            val errorGroups = errors.groupBy { it.getErrorType() }
            if (errorGroups.size == 1) {
                val group = errorGroups.values.first()
                "${group.size} similar errors occurred: ${getLocalizedErrorMessage(group.first())}"
            } else {
                "Multiple errors occurred (${errors.size} total), please retry"
            }
        }
    }

    private fun getLocalizedErrorMessage(errorEvent: DomainErrorEvent): String {
        return when (errorEvent.getErrorType()) {
            "Location_Error_DuplicateTitle" -> "Location already exists"
            "Location_Error_InvalidCoordinates" -> "Invalid coordinates provided"
            "Location_Error_NetworkError" -> "Network error occurred"
            "Location_Error_DatabaseError" -> "Database error occurred"
            "Weather_Error_ApiUnavailable" -> "Weather service is unavailable"
            "Setting_Error_AlreadyExists" -> "Setting already exists"
            "Tip_Error_ValidationFailed" -> "Tip validation failed"
            "TipType_Error_AlreadyExists" -> "Tip type already exists"
            else -> "An error occurred: ${errorEvent.message}"
        }
    }

    suspend fun triggerErrorDisplay(errors: List<DomainErrorEvent>) {
        processErrorBatch(errors)
    }
}

/**
 * Event arguments for error display events
 */
data class ErrorDisplayEventArgs(
    val errors: List<DomainErrorEvent>,
    val displayMessage: String
) {
    val isSingleError: Boolean = errors.size == 1
}