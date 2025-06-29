package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant

/**
 * Data transfer object for setting data
 */
data class SettingDto(
    val id: Int,
    val key: String,
    val value: String,
    val description: String,
    val timestamp: Instant
)