// shared/core-ui/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/ui/screenmodels/AddLocationScreenModel.kt
package com.x3squaredcircles.pixmap.core.ui.screenmodels

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.x3squaredcircles.pixmap.shared.application.commands.SaveLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.*
import com.x3squaredcircles.pixmap.shared.application.queries.GetLocationByIdQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * ScreenModel for AddLocationScreen following MAUI patterns
 * Handles location creation/editing, photo capture, and geolocation
 */
class AddLocationScreenModel(
    private val mediator: IMediator,
    private val mediaService: IMediaService,
    private val geolocationService: IGeolocationService,
    private val errorDisplayService: IErrorDisplayService
) : ScreenModel {

    private val _uiState = MutableStateFlow(AddLocationUiState())
    val uiState: StateFlow<AddLocationUiState> = _uiState.asStateFlow()

    private var lastOperation: (() -> Unit)? = null

    /**
     * Update the title field
     */
    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(
            title = title,
            titleError = "" // Clear error when user starts typing
        )
    }

    /**
     * Update the description field
     */
    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    /**
     * Load existing location for editing
     */
    fun loadLocation(locationId: Int) {
        screenModelScope.launch {
            try {
                lastOperation = { loadLocation(locationId) }
                _uiState.value = _uiState.value.copy(
                    isBusy = true,
                    isError = false,
                    errorMessage = ""
                )

                val query = GetLocationByIdQuery(locationId)
                val result = mediator.send(query)

                if (result.isSuccess && result.data != null) {
                    val locationDto = result.data!!
                    _uiState.value = _uiState.value.copy(
                        id = locationDto.id,
                        title = locationDto.title,
                        description = locationDto.description,
                        latitude = locationDto.latitude,
                        longitude = locationDto.longitude,
                        city = locationDto.city,
                        state = locationDto.state,
                        photo = locationDto.photoPath,
                        timestamp = locationDto.timestamp,
                        isNewLocation = false,
                        isBusy = false
                    )
                } else {
                    handleError("Location not found")
                }

            } catch (e: Exception) {
                handleError("Error loading location: ${e.message}")
            }
        }
    }

    /**
     * Start location tracking to get current coordinates
     */
    fun startLocationTracking() {
        screenModelScope.launch {
            try {
                lastOperation = { startLocationTracking() }
                _uiState.value = _uiState.value.copy(
                    isLocationTracking = true,
                    isError = false
                )

                val result = geolocationService.getCurrentLocationAsync()

                if (result.isSuccess && result.data != null) {
                    val location = result.data!!
                    _uiState.value = _uiState.value.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        isLocationTracking = false
                    )

                    // Try to get address information if available
                    // This would typically use a reverse geocoding service
                    // For now, we'll set placeholder values
                    _uiState.value = _uiState.value.copy(
                        city = "Current City", // Would be from reverse geocoding
                        state = "Current State" // Would be from reverse geocoding
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLocationTracking = false)
                    handleError(result.errorMessage ?: "Error getting location")
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLocationTracking = false)
                handleError("Error getting location: ${e.message}")
            }
        }
    }

    /**
     * Take or select photo
     */
    fun takePhoto() {
        screenModelScope.launch {
            try {
                lastOperation = { takePhoto() }
                _uiState.value = _uiState.value.copy(
                    isCapturingPhoto = true,
                    isError = false
                )

                val result = mediaService.capturePhotoAsync()

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        photo = result.data,
                        isCapturingPhoto = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isCapturingPhoto = false)
                    handleError(result.errorMessage ?: "Error capturing photo")
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCapturingPhoto = false)
                handleError("Error capturing photo: ${e.message}")
            }
        }
    }

    /**
     * Save location (create or update)
     */
    fun saveLocation() {
        screenModelScope.launch {
            try {
                val currentState = _uiState.value

                // Validate before saving
                val validationErrors = validateLocation(currentState)
                if (validationErrors.isNotEmpty()) {
                    _uiState.value = currentState.copy(
                        titleError = validationErrors["title"] ?: "",
                        isError = true,
                        errorMessage = "Please fix the validation errors"
                    )
                    return@launch
                }

                lastOperation = { saveLocation() }
                _uiState.value = currentState.copy(
                    isSaving = true,
                    isError = false,
                    errorMessage = ""
                )

                val command = SaveLocationCommand(
                    id = if (currentState.isNewLocation) 0 else currentState.id,
                    title = currentState.title,
                    description = currentState.description,
                    latitude = currentState.latitude,
                    longitude = currentState.longitude,
                    city = currentState.city,
                    state = currentState.state,
                    photoPath = currentState.photo
                )

                val result = mediator.send(command)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveCompleted = true
                    )
                } else {
                    handleError(result.errorMessage ?: "Error saving location")
                }

            } catch (e: Exception) {
                handleError("Error saving location: ${e.message}")
            }
        }
    }

    /**
     * Reset for new location entry
     */
    fun resetForNewLocation() {
        _uiState.value = AddLocationUiState()
    }

    /**
     * Retry the last failed operation
     */
    fun retryLastOperation() {
        lastOperation?.invoke()
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            isError = false,
            errorMessage = "",
            titleError = ""
        )
    }

    /**
     * Clear error event after it's been handled
     */
    fun clearErrorEvent() {
        _uiState.value = _uiState.value.copy(errorEvent = null)
    }

    /**
     * Validate location data
     */
    private fun validateLocation(state: AddLocationUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (state.title.isBlank()) {
            errors["title"] = "Title is required"
        } else if (state.title.length > 100) {
            errors["title"] = "Title cannot exceed 100 characters"
        }

        if (state.description.length > 500) {
            errors["description"] = "Description cannot exceed 500 characters"
        }

        if (state.latitude == 0.0 && state.longitude == 0.0) {
            errors["coordinates"] = "Valid coordinates are required"
        }

        return errors
    }

    /**
     * Handle errors following MAUI pattern
     */
    private fun handleError(message: String) {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                isSaving = false,
                isCapturingPhoto = false,
                isLocationTracking = false,
                isError = true,
                errorMessage = message
            )

            // Trigger error display service for user notification
            try {
                errorDisplayService.displayErrorAsync(message)
            } catch (e: Exception) {
                // Error display service failed, but don't crash the app
                println("Error display service failed: ${e.message}")
            }
        }
    }
}

/**
 * UI State for AddLocationScreen
 */
data class AddLocationUiState(
    val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val city: String = "",
    val state: String = "",
    val photo: String? = null,
    val timestamp: kotlinx.datetime.Instant = Clock.System.now(),
    val isNewLocation: Boolean = true,

    // Loading states
    val isBusy: Boolean = false,
    val isSaving: Boolean = false,
    val isCapturingPhoto: Boolean = false,
    val isLocationTracking: Boolean = false,

    // Error states
    val isError: Boolean = false,
    val errorMessage: String = "",
    val titleError: String = "",
    val errorEvent: ErrorDisplayEventArgs? = null,

    // Completion state
    val saveCompleted: Boolean = false
) {
    /**
     * Computed property to determine if save button should be enabled
     */
    val canSave: Boolean
        get() = title.isNotBlank() &&
                latitude != 0.0 &&
                longitude != 0.0 &&
                !isBusy &&
                !isSaving &&
                titleError.isBlank()
}