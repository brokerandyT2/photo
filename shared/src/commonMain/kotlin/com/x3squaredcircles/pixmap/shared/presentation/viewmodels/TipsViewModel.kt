// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/TipsViewModel.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

import com.x3squaredcircles.pixmap.shared.application.dto.TipDto
import com.x3squaredcircles.pixmap.shared.application.dto.TipTypeDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IErrorDisplayService
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllTipTypesQuery
import com.x3squaredcircles.pixmap.shared.application.queries.GetTipsByTypeQuery
import kotlinx.coroutines.flow.*

/**
 * View model for displaying photography tips organized by categories
 */
class TipsViewModel(
    private val mediator: IMediator,
    errorDisplayService: IErrorDisplayService? = null
) : BaseViewModel(errorDisplayService = errorDisplayService), INavigationAware {

    // Observable collections
    private val _tips = MutableStateFlow<List<TipItemViewModel>>(emptyList())
    val tips: StateFlow<List<TipItemViewModel>> = _tips.asStateFlow()

    private val _tipTypes = MutableStateFlow<List<TipTypeItemViewModel>>(emptyList())
    val tipTypes: StateFlow<List<TipTypeItemViewModel>> = _tipTypes.asStateFlow()

    // Selection state
    private val _selectedTipTypeId = MutableStateFlow(0)
    val selectedTipTypeId: StateFlow<Int> = _selectedTipTypeId.asStateFlow()

    private val _selectedTipType = MutableStateFlow<TipTypeItemViewModel?>(null)
    val selectedTipType: StateFlow<TipTypeItemViewModel?> = _selectedTipType.asStateFlow()

    // Computed properties
    val hasTips: StateFlow<Boolean> = tips.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasTipTypes: StateFlow<Boolean> = tipTypes.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val selectedTipTypeName: StateFlow<String> = selectedTipType.map { it?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val tipsCount: StateFlow<String> = tips.map { tipList ->
        when (tipList.size) {
            0 -> "No tips"
            1 -> "1 tip"
            else -> "${tipList.size} tips"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Loads all available tip types
     */
    suspend fun loadTipTypes() = executeSafely {
        val query = GetAllTipTypesQuery()
        val result = mediator.send(query)

        when {
            result.isSuccess && result.data != null -> {
                val tipTypeViewModels = result.data.map { dto ->
                    TipTypeItemViewModel(
                        id = dto.id,
                        name = dto.name,
                        i8n = dto.i8n
                    )
                }

                _tipTypes.value = tipTypeViewModels

                // Set default selected tip type if available
                if (tipTypeViewModels.isNotEmpty()) {
                    selectTipType(tipTypeViewModels.first())
                }
            }
            else -> {
                onSystemError(result.errorMessage ?: "Failed to load tip types")
            }
        }
    }

    /**
     * Loads tips for a specific tip type
     */
    suspend fun loadTipsByType(tipTypeId: Int) = executeSafely {
        if (tipTypeId <= 0) {
            setValidationError("Please select a valid tip type")
            return@executeSafely
        }

        val query = GetTipsByTypeQuery(tipTypeId)
        val result = mediator.send(query)

        when {
            result.isSuccess && result.data != null -> {
                val tipViewModels = result.data.map { dto ->
                    TipItemViewModel(
                        id = dto.id,
                        tipTypeId = dto.tipTypeId,
                        title = dto.title,
                        content = dto.content,
                        fstop = dto.fstop,
                        shutterSpeed = dto.shutterSpeed,
                        iso = dto.iso,
                        i8n = dto.i8n
                    )
                }

                _tips.value = tipViewModels
            }
            else -> {
                onSystemError(result.errorMessage ?: "Failed to load tips")
            }
        }
    }

    /**
     * Selects a tip type and loads its associated tips
     */
    suspend fun selectTipType(tipType: TipTypeItemViewModel) {
        _selectedTipType.value = tipType
        _selectedTipTypeId.value = tipType.id
        loadTipsByType(tipType.id)
    }

    /**
     * Selects a tip type by ID
     */
    suspend fun selectTipTypeById(tipTypeId: Int) {
        val tipType = _tipTypes.value.find { it.id == tipTypeId }
        if (tipType != null) {
            selectTipType(tipType)
        }
    }

    /**
     * Refreshes both tip types and tips
     */
    suspend fun refresh() {
        loadTipTypes()
    }

    /**
     * Searches tips within the currently selected type
     */
    suspend fun searchTips(searchTerm: String) {
        val currentTips = _tips.value
        if (searchTerm.isBlank()) {
            // Reload all tips for current type
            val currentTypeId = _selectedTipTypeId.value
            if (currentTypeId > 0) {
                loadTipsByType(currentTypeId)
            }
        } else {
            // Filter current tips by search term
            val filteredTips = currentTips.filter { tip ->
                tip.title.contains(searchTerm, ignoreCase = true) ||
                        tip.content.contains(searchTerm, ignoreCase = true)
            }
            _tips.value = filteredTips
        }
    }

    /**
     * Navigation lifecycle methods
     */
    override suspend fun onNavigatedTo() {
        loadTipTypes()
    }

    override suspend fun onNavigatedFrom() {
        // No cleanup needed
    }
}

/**
 * View model for individual tip items
 */
data class TipItemViewModel(
    val id: Int,
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String,
    val shutterSpeed: String,
    val iso: String,
    val i8n: String
) {
    /**
     * Whether this tip has camera settings
     */
    val hasCameraSettings: Boolean
        get() = fstop.isNotEmpty() || shutterSpeed.isNotEmpty() || iso.isNotEmpty()

    /**
     * Formatted camera settings display
     */
    val cameraSettingsDisplay: String
        get() = buildString {
            if (fstop.isNotEmpty()) append("F: $fstop ")
            if (shutterSpeed.isNotEmpty()) append("Shutter: $shutterSpeed ")
            if (iso.isNotEmpty()) append("ISO: $iso")
        }.trim()

    /**
     * Short preview of content for list display
     */
    val contentPreview: String
        get() = if (content.length > 100) {
            content.take(97) + "..."
        } else {
            content
        }

    /**
     * Whether this tip has extended content
     */
    val hasExtendedContent: Boolean
        get() = content.length > 100

    /**
     * Formatted camera settings for detailed view
     */
    val detailedCameraSettings: List<Pair<String, String>>
        get() = buildList {
            if (fstop.isNotEmpty()) add("Aperture" to "f/$fstop")
            if (shutterSpeed.isNotEmpty()) add("Shutter Speed" to shutterSpeed)
            if (iso.isNotEmpty()) add("ISO" to iso)
        }
}

/**
 * View model for tip type items
 */
data class TipTypeItemViewModel(
    val id: Int,
    val name: String,
    val i8n: String
) {
    /**
     * Display name for the tip type
     */
    val displayName: String
        get() = name.ifEmpty { "Unknown Type" }

    /**
     * Whether this is a localized tip type
     */
    val isLocalized: Boolean
        get() = i8n != "en-US"
}