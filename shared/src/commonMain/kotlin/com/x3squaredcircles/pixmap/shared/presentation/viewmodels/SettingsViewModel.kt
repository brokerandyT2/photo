// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/SettingsViewModel.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

import com.x3squaredcircles.pixmap.shared.application.commands.CreateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.commands.UpdateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetAllSettingsQuery
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IErrorDisplayService

import com.x3squaredcircles.pixmap.shared.application.queries.GetSettingByKeyQuery
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * View model for application settings management
 */
class SettingsViewModel(
    private val mediator: IMediator,
    errorDisplayService: IErrorDisplayService? = null
) : BaseViewModel(errorDisplayService = errorDisplayService), INavigationAware {

    // Observable collections
    private val _settings = MutableStateFlow<List<SettingItemViewModel>>(emptyList())
    val settings: StateFlow<List<SettingItemViewModel>> = _settings.asStateFlow()

    // Search and filter state
    private val _searchTerm = MutableStateFlow("")
    val searchTerm: StateFlow<String> = _searchTerm.asStateFlow()

    private val _filteredSettings = MutableStateFlow<List<SettingItemViewModel>>(emptyList())
    val filteredSettings: StateFlow<List<SettingItemViewModel>> = _filteredSettings.asStateFlow()

    // Edit state
    private val _editingSettings = MutableStateFlow<Map<String, String>>(emptyMap())
    val editingSettings: StateFlow<Map<String, String>> = _editingSettings.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    // Computed properties
    val hasSettings: StateFlow<Boolean> = settings.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val settingsCount: StateFlow<String> = filteredSettings.map { settingsList ->
        when (settingsList.size) {
            0 -> "No settings"
            1 -> "1 setting"
            else -> "${settingsList.size} settings"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val canSave: StateFlow<Boolean> = hasUnsavedChanges
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Combine search term and settings to create filtered list
        viewModelScope.launch {
            combine(settings, searchTerm) { settingsList, search ->
                if (search.isBlank()) {
                    settingsList
                } else {
                    settingsList.filter { setting ->
                        setting.key.contains(search, ignoreCase = true) ||
                                setting.value.contains(search, ignoreCase = true) ||
                                setting.description.contains(search, ignoreCase = true)
                    }
                }
            }.collect { filtered ->
                _filteredSettings.value = filtered
            }
        }
    }

    /**
     * Loads all settings from the data store
     */
    suspend fun loadSettings() = executeSafely(
        operation = {
            val query = GetAllSettingsQuery()
            val result = mediator.send(query)

            if (result != null) {
                val settingViewModels = result.map { setting ->
                    SettingItemViewModel(
                        id = setting.id,
                        key = setting.key,
                        value = setting.value,
                        description = setting.description,
                        timestamp = setting.timestamp
                    )
                }

                _settings.value = settingViewModels
                clearUnsavedChanges()
            } else {
                onSystemError("Failed to load settings")
            }
        }
    )

    /**
     * Gets a specific setting by key
     */
    suspend fun getSetting(key: String): SettingItemViewModel? {
        val query = GetSettingByKeyQuery(key)
        val result = mediator.send(query)

        return if (result != null) {
            SettingItemViewModel(
                id = result.id,
                key = result.key,
                value = result.value,
                description = result.description,
                timestamp = result.timestamp
            )
        } else {
            null
        }
    }

    /**
     * Updates a setting value temporarily (before save)
     */
    fun updateSettingValue(key: String, value: String) {
        val currentEditing = _editingSettings.value.toMutableMap()
        currentEditing[key] = value
        _editingSettings.value = currentEditing
        _hasUnsavedChanges.value = true
    }

    /**
     * Creates a new setting
     */
    suspend fun createSetting(key: String, value: String, description: String = "") = executeSafely(
        operation = {
            if (key.isBlank()) {
                setValidationError("Setting key cannot be empty")
                return@executeSafely
            }

            val command = CreateSettingCommand(
                key = key,
                value = value,
                description = description
            )

            val result = mediator.send(command)

            if (result != null) {
                // Reload settings to include the new one
                viewModelScope.launch { loadSettings() }
            } else {
                onSystemError("Failed to create setting")
            }
        }
    )

    /**
     * Updates an existing setting
     */
    suspend fun updateSetting(key: String, value: String) = executeSafely(
        operation = {
            val command = UpdateSettingCommand(
                key = key,
                value = value
            )

            val result = mediator.send(command)

            if (result != null) {
                // Reload settings to reflect the change
                viewModelScope.launch { loadSettings() }
            } else {
                onSystemError("Failed to update setting")
            }
        }
    )

    /**
     * Saves all pending changes
     */
    suspend fun saveChanges() {
        val edits = _editingSettings.value
        for ((key, value) in edits) {
            updateSetting(key, value)
        }
        clearUnsavedChanges()
    }

    /**
     * Discards all pending changes
     */
    fun discardChanges() {
        clearUnsavedChanges()
    }

    /**
     * Clears all unsaved changes
     */
    private fun clearUnsavedChanges() {
        _editingSettings.value = emptyMap()
        _hasUnsavedChanges.value = false
    }

    /**
     * Sets the search term for filtering settings
     */
    fun setSearchTerm(term: String) {
        _searchTerm.value = term
    }

    /**
     * Clears the search term
     */
    fun clearSearch() {
        _searchTerm.value = ""
    }

    /**
     * Gets the current value of a setting (either edited or original)
     */
    fun getCurrentValue(key: String): String {
        return _editingSettings.value[key]
            ?: _settings.value.find { it.key == key }?.value
            ?: ""
    }

    /**
     * Checks if a setting has unsaved changes
     */
    fun hasUnsavedChanges(key: String): Boolean {
        return _editingSettings.value.containsKey(key)
    }

    /**
     * Refreshes settings from the data store
     */
    suspend fun refresh() {
        loadSettings()
    }

    /**
     * Navigation lifecycle methods
     */
    override suspend fun onNavigatedTo() {
        loadSettings()
    }

    override suspend fun onNavigatedFrom() {
        // Could show confirmation dialog if there are unsaved changes
    }
}

/**
 * View model for individual setting items
 */
data class SettingItemViewModel(
    val id: Int,
    val key: String,
    val value: String,
    val description: String,
    val timestamp: Instant
) {
    /**
     * Display name for the setting (formatted key)
     */
    val displayName: String
        get() = key.replace("_", " ").split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    /**
     * Whether this setting has a description
     */
    val hasDescription: Boolean
        get() = description.isNotEmpty()

    /**
     * Formatted timestamp for display
     */
    val formattedTimestamp: String
        get() {
            val localDateTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
            return "${localDateTime.date} ${localDateTime.time}"
        }

    /**
     * Short value for list display (truncated if too long)
     */
    val shortValue: String
        get() = if (value.length > 50) {
            value.take(47) + "..."
        } else {
            value
        }

    /**
     * Whether the value is truncated in short display
     */
    val isValueTruncated: Boolean
        get() = value.length > 50

    /**
     * Whether this appears to be a boolean setting
     */
    val isBooleanSetting: Boolean
        get() = value.lowercase() in listOf("true", "false", "yes", "no", "1", "0")

    /**
     * Whether this appears to be a numeric setting
     */
    val isNumericSetting: Boolean
        get() = value.toIntOrNull() != null || value.toDoubleOrNull() != null

    /**
     * Setting category based on key prefix
     */
    val category: String
        get() = when {
            key.startsWith("app_", ignoreCase = true) -> "Application"
            key.startsWith("ui_", ignoreCase = true) -> "User Interface"
            key.startsWith("location_", ignoreCase = true) -> "Location"
            key.startsWith("weather_", ignoreCase = true) -> "Weather"
            key.startsWith("camera_", ignoreCase = true) -> "Camera"
            key.startsWith("sync_", ignoreCase = true) -> "Synchronization"
            key.startsWith("privacy_", ignoreCase = true) -> "Privacy"
            else -> "General"
        }
}