package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant

/**
 * Data transfer object for location data
 */
data class LocationDto(
    val id: Int,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String,
    val photoPath: String?,
    val isDeleted: Boolean,
    val timestamp: Instant
)