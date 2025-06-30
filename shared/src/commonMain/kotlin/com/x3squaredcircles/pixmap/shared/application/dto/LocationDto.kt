// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/dto/LocationDto.kt
package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Data transfer object for location information
 */
@Serializable
data class LocationDto(
    /**
     * Unique identifier
     */
    val id: Int,

    /**
     * Location title
     */
    val title: String,

    /**
     * Location description
     */
    val description: String,

    /**
     * Latitude coordinate
     */
    val latitude: Double,

    /**
     * Longitude coordinate
     */
    val longitude: Double,

    /**
     * City name
     */
    val city: String,

    /**
     * State name
     */
    val state: String,

    /**
     * Path to attached photo
     */
    val photoPath: String? = null,

    /**
     * Creation/update timestamp
     */
    val timestamp: Instant,

    /**
     * Indicates if the location is deleted
     */
    val isDeleted: Boolean = false
)

/**
 * Data transfer object for location list items
 */
@Serializable
data class LocationListDto(
    /**
     * Unique identifier
     */
    val id: Int,

    /**
     * Location title
     */
    val title: String,

    /**
     * City name
     */
    val city: String,

    /**
     * State name
     */
    val state: String,

    /**
     * Latitude coordinate
     */
    val latitude: Double,

    /**
     * Longitude coordinate
     */
    val longitude: Double,

    /**
     * Path to attached photo
     */
    val photoPath: String? = null,

    /**
     * Creation/update timestamp
     */
    val timestamp: Instant,

    /**
     * Indicates if the location is deleted
     */
    val isDeleted: Boolean = false
)