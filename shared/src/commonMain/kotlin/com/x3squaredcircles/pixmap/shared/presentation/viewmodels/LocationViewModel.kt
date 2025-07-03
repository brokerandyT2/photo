// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/LocationViewModel.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

import com.x3squaredcircles.pixmap.shared.application.commands.SaveLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ICameraService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IErrorDisplayService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILocationService
import com.x3squaredcircles.pixmap.shared.application.queries.GetLocationByIdQuery
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

/**
 * View model for location management with photo capture and GPS tracking
 */
class LocationViewModel(
    private val mediator: IMediator,
    private val cameraService: ICameraService,
    private val locationService: ILocationService,
    errorDisplayService: IErrorDisplayService? = null
) : BaseViewModel(errorDisplayService = errorDisplayService), INavigationAware {

    // Observable properties using StateFlow
    private val _id = MutableStateFlow(0)
    val id: StateFlow<Int> = _id.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _latitude = MutableStateFlow(0.0)
    val latitude: StateFlow<Double> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(0.0)
    val longitude: StateFlow<Double> = _longitude.asStateFlow()

    private val _city = MutableStateFlow("")
    val city: StateFlow<String> = _city.asStateFlow()

    private val _state = MutableStateFlow("")
    val state: StateFlow<String> = _state.asStateFlow()

    private val _photo = MutableStateFlow("")
    val photo: StateFlow<String> = _photo.asStateFlow()

    private val _timestamp = MutableStateFlow(Clock.System.now())
    val timestamp: StateFlow<Instant> = _timestamp.asStateFlow()

    private val _isNewLocation = MutableStateFlow(true)
    val isNewLocation: StateFlow<Boolean> = _isNewLocation.asStateFlow()

    private val _isLocationTracking = MutableStateFlow(false)
    val isLocationTracking: StateFlow<Boolean> = _isLocationTracking.asStateFlow()

    // Computed properties
    val formattedTimestamp: StateFlow<String> = timestamp.map { instant ->
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${localDateTime.date} ${localDateTime.time}"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val formattedCoordinates: StateFlow<String> = combine(latitude, longitude) { lat, lng ->
        "${lat.format(6)}, ${lng.format(6)}"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Property setters
    fun setId(value: Int) {
        _id.value = value
    }

    fun setTitle(value: String) {
        _title.value = value
    }

    fun setDescription(value: String) {
        _description.value = value
    }

    fun setLatitude(value: Double) {
        _latitude.value = round(value * 1000000) / 1000000 // Round to 6 decimal places
    }

    fun setLongitude(value: Double) {
        _longitude.value = round(value * 1000000) / 1000000 // Round to 6 decimal places
    }

    fun setCity(value: String) {
        _city.value = value
    }

    fun setState(value: String) {
        _state.value = value
    }

    fun setPhoto(value: String) {
        _photo.value = value
    }

    /**
     * Saves the current location data
     */
    suspend fun save() = executeSafely(
        operation = {
            val command = SaveLocationCommand(
                id = if (_id.value > 0) _id.value else null,
                title = _title.value,
                description = _description.value,
                latitude = _latitude.value,
                longitude = _longitude.value,
                city = _city.value,
                state = _state.value,
                photoPath = _photo.value.takeIf { it.isNotEmpty() }
            )

            val result = mediator.send(command)

            if (result.isSuccess && result.data != null) {
                val locationDto = result.data!!
                _id.value = locationDto.id
                _timestamp.value = locationDto.timestamp
                _isNewLocation.value = false
            } else {
                onSystemError(result.errorMessage ?: "Failed to save location")
            }
        }
    )

    /**
     * Loads a location by ID
     */
    suspend fun loadLocation(locationId: Int) = executeSafely(
        operation = {
            val query = GetLocationByIdQuery(locationId)
            val result = mediator.send(query)

            if (result != null) {
                val locationDto = result
                _id.value = locationDto.id
                _title.value = locationDto.title
                _description.value = locationDto.description
                _latitude.value = locationDto.latitude
                _longitude.value = locationDto.longitude
                _city.value = locationDto.city
                _state.value = locationDto.state
                _photo.value = locationDto.photoPath ?: ""
                _timestamp.value = locationDto.timestamp
                _isNewLocation.value = false
            } else {
                onSystemError("Failed to load location")
            }
        }
    )

    /**
     * Captures a photo using the camera service
     */
    suspend fun capturePhoto() = executeSafely(
        operation = {
            val result = cameraService.capturePhoto()
            when {
                result.isSuccess() -> {
                    _photo.value = result.getOrNull() ?: ""
                }
                else -> {
                    setValidationError("Failed to capture photo")
                }
            }
        }
    )

    /**
     * Selects a photo from gallery
     */
    suspend fun selectPhoto() = executeSafely(
        operation = {
            val result = cameraService.selectFromGallery()
            when {
                result.isSuccess() -> {
                    _photo.value = result.getOrNull() ?: ""
                }
                else -> {
                    setValidationError("Failed to select photo")
                }
            }
        }
    )

    /**
     * Starts location tracking
     */
    suspend fun startLocationTracking() = executeSafely(
        operation = {
            if (_isLocationTracking.value) return@executeSafely

            when {
                !locationService.hasPermission() -> {
                    val permissionGranted = locationService.requestPermission()
                    if (!permissionGranted) {
                        setValidationError("Location permission is required")
                        return@executeSafely
                    }
                }
                !locationService.isLocationEnabled() -> {
                    setValidationError("Location services must be enabled")
                    return@executeSafely
                }
            }

            _isLocationTracking.value = true

            // Get current location immediately
            viewModelScope.launch { getCurrentLocation() }
        }
    )

    /**
     * Stops location tracking
     */
    suspend fun stopLocationTracking() {
        if (!_isLocationTracking.value) return
        _isLocationTracking.value = false
    }

    /**
     * Gets the current location
     */
    suspend fun getCurrentLocation() = executeSafely(
        operation = {
            val result = locationService.getCurrentLocation()
            when {
                result.isSuccess() && result.getOrNull() != null -> {
                    val coordinate = result.getOrNull()!!
                    setLatitude(coordinate.latitude)
                    setLongitude(coordinate.longitude)
                }
                else -> {
                    onSystemError("Failed to get current location")
                }
            }
        }
    )

    /**
     * Navigation lifecycle methods
     */
    override suspend fun onNavigatedTo() {
        startLocationTracking()
    }

    override suspend fun onNavigatedFrom() {
        stopLocationTracking()
    }

    /**
     * Helper extension for formatting doubles to specific decimal places
     */
    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}