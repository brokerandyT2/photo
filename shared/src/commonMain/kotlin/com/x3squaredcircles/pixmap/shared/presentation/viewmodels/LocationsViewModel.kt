// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/LocationsViewModel.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IErrorDisplayService
import com.x3squaredcircles.pixmap.shared.application.queries.GetLocationsQuery
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

/**
 * View model for displaying a list of locations with pagination support
 */
class LocationsViewModel(
    private val mediator: IMediator,
    errorDisplayService: IErrorDisplayService? = null
) : BaseViewModel(errorDisplayService = errorDisplayService), INavigationAware {

    // Observable locations collection
    private val _locations = MutableStateFlow<List<LocationListItemViewModel>>(emptyList())
    val locations: StateFlow<List<LocationListItemViewModel>> = _locations.asStateFlow()

    // Search and pagination state
    private val _searchTerm = MutableStateFlow("")
    val searchTerm: StateFlow<String> = _searchTerm.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageSize = MutableStateFlow(100)
    val pageSize: StateFlow<Int> = _pageSize.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _includeDeleted = MutableStateFlow(false)
    val includeDeleted: StateFlow<Boolean> = _includeDeleted.asStateFlow()

    // Computed properties
    val hasLocations: StateFlow<Boolean> = locations.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasMorePages: StateFlow<Boolean> = combine(currentPage, pageSize, totalCount) { page, size, total ->
        (page * size) < total
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val formattedCount: StateFlow<String> = totalCount.map { count ->
        when {
            count == 0 -> "No locations"
            count == 1 -> "1 location"
            else -> "$count locations"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Loads locations based on current filter and pagination settings
     */
    suspend fun loadLocations() = executeSafely {
        val query = GetLocationsQuery(
            pageNumber = _currentPage.value,
            pageSize = _pageSize.value,
            searchTerm = _searchTerm.value.takeIf { it.isNotBlank() },
            includeDeleted = _includeDeleted.value
        )

        val result = mediator.send(query)

        when {
            result.isSuccess && result.data != null -> {
                val pagedData = result.data
                _totalCount.value = pagedData.totalCount

                // Map domain DTOs to view models
                val locationViewModels = pagedData.items.map { dto ->
                    LocationListItemViewModel(
                        id = dto.id,
                        title = dto.title,
                        latitude = dto.latitude,
                        longitude = dto.longitude,
                        city = dto.city,
                        state = dto.state,
                        photo = dto.photoPath ?: "",
                        timestamp = dto.timestamp,
                        isDeleted = dto.isDeleted
                    )
                }

                _locations.value = locationViewModels
            }
            else -> {
                onSystemError(result.errorMessage ?: "Failed to load locations")
            }
        }
    }

    /**
     * Refreshes the current page of locations
     */
    suspend fun refresh() {
        _currentPage.value = 1
        loadLocations()
    }

    /**
     * Searches locations by term
     */
    suspend fun search(term: String) {
        _searchTerm.value = term
        _currentPage.value = 1
        loadLocations()
    }

    /**
     * Clears the search term and reloads
     */
    suspend fun clearSearch() {
        _searchTerm.value = ""
        _currentPage.value = 1
        loadLocations()
    }

    /**
     * Loads the next page of locations (pagination)
     */
    suspend fun loadNextPage() {
        if (hasMorePages.value && !isBusy()) {
            _currentPage.value = _currentPage.value + 1
            loadLocations()
        }
    }

    /**
     * Toggles inclusion of deleted locations
     */
    suspend fun toggleIncludeDeleted() {
        _includeDeleted.value = !_includeDeleted.value
        _currentPage.value = 1
        loadLocations()
    }

    /**
     * Sets the page size for pagination
     */
    suspend fun setPageSize(size: Int) {
        if (size > 0 && size != _pageSize.value) {
            _pageSize.value = size
            _currentPage.value = 1
            loadLocations()
        }
    }

    /**
     * Navigation lifecycle methods
     */
    override suspend fun onNavigatedTo() {
        loadLocations()
    }

    override suspend fun onNavigatedFrom() {
        // No cleanup needed
    }
}

/**
 * View model for individual location list items
 */
data class LocationListItemViewModel(
    val id: Int,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String,
    val photo: String,
    val timestamp: Instant,
    val isDeleted: Boolean
) {
    /**
     * Formatted coordinates display
     */
    val formattedCoordinates: String
        get() = "${latitude.format(6)}, ${longitude.format(6)}"

    /**
     * Display name combining title and location
     */
    val displayName: String
        get() = when {
            city.isNotEmpty() && state.isNotEmpty() -> "$title - $city, $state"
            city.isNotEmpty() -> "$title - $city"
            state.isNotEmpty() -> "$title - $state"
            else -> title
        }

    /**
     * Formatted timestamp for display
     */
    val formattedTimestamp: String
        get() {
            val localDateTime = timestamp.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
            return "${localDateTime.date} ${localDateTime.time}"
        }

    /**
     * Whether this location has a photo
     */
    val hasPhoto: Boolean
        get() = photo.isNotEmpty()

    /**
     * Helper extension for formatting doubles to specific decimal places
     */
    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}