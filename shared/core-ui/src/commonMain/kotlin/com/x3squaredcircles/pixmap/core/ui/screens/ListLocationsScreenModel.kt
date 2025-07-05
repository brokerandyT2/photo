// shared/core-ui/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/ui/screenmodels/ListLocationsScreenModel.kt
package com.x3squaredcircles.pixmap.core.ui.screenmodels

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IErrorDisplayService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IMapService
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllLocationsQuery
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ErrorDisplayEventArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ScreenModel for ListLocationsScreen following MAUI patterns
 * Handles loading locations list, selection, and navigation to map
 */
class ListLocationsScreenModel(
    private val mediator: IMediator,
    private val errorDisplayService: IErrorDisplayService,
    private val mapService: IMapService
) : ScreenModel {

    private val _uiState = MutableStateFlow(ListLocationsUiState())
    val uiState: StateFlow<ListLocationsUiState> = _uiState.asStateFlow()

    private var lastOperation: (() -> Unit)? = null

    init {
        loadLocations()
    }

    /**
     * Load all locations from the repository
     */
    fun loadLocations() {
        screenModelScope.launch {
            try {
                lastOperation = { loadLocations() }
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    isError = false,
                    errorMessage = ""
                )

                val query = GetAllLocationsQuery()
                val result = mediator.send(query)

                if (result.isSuccess && result.data != null) {
                    val locationItems = result.data!!.map { locationDto ->
                        LocationListItem(
                            id = locationDto.id,
                            title = locationDto.title,
                            description = locationDto.description,
                            latitude = locationDto.latitude,
                            longitude = locationDto.longitude,
                            city = locationDto.city,
                            state = locationDto.state,
                            photo = locationDto.photoPath,
                            formattedCoordinates = formatCoordinates(locationDto.latitude, locationDto.longitude)
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        locations = locationItems,
                        isLoading = false
                    )
                } else {
                    handleError(result.errorMessage ?: "Failed to load locations")
                }

            } catch (e: Exception) {
                handleError("Error loading locations: ${e.message}")
            }
        }
    }

    /**
     * Handle location selection for editing
     */
    fun selectLocation(locationId: Int) {
        _uiState.value = _uiState.value.copy(selectedLocationId = locationId)
    }

    /**
     * Clear location selection
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedLocationId = null)
    }

    /**
     * Open location in map application
     */
    fun openLocationInMap(location: LocationListItem) {
        screenModelScope.launch {
            try {
                lastOperation = { openLocationInMap(location) }
                _uiState.value = _uiState.value.copy(
                    isError = false,
                    errorMessage = ""
                )

                val result = mapService.openLocationAsync(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    name = location.title
                )

                if (!result.isSuccess) {
                    handleError(result.errorMessage ?: "No Map Application available")
                }

            } catch (e: Exception) {
                handleError("Error opening map: ${e.message}")
            }
        }
    }

    /**
     * Refresh the locations list
     */
    fun refreshLocations() {
        loadLocations()
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
            errorMessage = ""
        )
    }

    /**
     * Clear error event after it's been handled
     */
    fun clearErrorEvent() {
        _uiState.value = _uiState.value.copy(errorEvent = null)
    }

    /**
     * Format coordinates for display
     */
    private fun formatCoordinates(latitude: Double, longitude: Double): String {
        return "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
    }

    /**
     * Handle errors following MAUI pattern
     */
    private fun handleError(message: String) {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
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
 * UI State for ListLocationsScreen
 */
data class ListLocationsUiState(
    val locations: List<LocationListItem> = emptyList(),
    val selectedLocationId: Int? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String = "",
    val errorEvent: ErrorDisplayEventArgs? = null
) {
    /**
     * Computed property to determine if empty state should be shown
     */
    val isEmpty: Boolean
        get() = locations.isEmpty() && !isLoading

    /**
     * Computed property to determine if retry button should be enabled
     */
    val canRetry: Boolean
        get() = isError && !isLoading
}

/**
 * Data class representing a location item in the list
 */
data class LocationListItem(
    val id: Int,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String,
    val photo: String?,
    val formattedCoordinates: String
)