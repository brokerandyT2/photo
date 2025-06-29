package com.x3squaredcircles.pixmap.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllLocationsQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationScreenModel(
    private val mediator: IMediator
) : ScreenModel {

    private val _uiState = MutableStateFlow(LocationScreenState())
    val uiState: StateFlow<LocationScreenState> = _uiState.asStateFlow()

    init {
        loadLocations()
    }

    fun loadLocations() {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val locations = mediator.send(GetAllLocationsQuery())
                _uiState.value = _uiState.value.copy(
                    locations = locations,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refreshLocations() {
        loadLocations()
    }
}

data class LocationScreenState(
    val locations: List<LocationDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)