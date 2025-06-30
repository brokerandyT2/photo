// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/dto/TipDto.kt
package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.serialization.Serializable

/**
 * Data transfer object for tip information
 */
@Serializable
data class TipDto(
    val id: Int,
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String,
    val shutterSpeed: String,
    val iso: String,
    val i8n: String = "en-US"
)

/**
 * Data transfer object for tip type information
 */
@Serializable
data class TipTypeDto(
    val id: Int,
    val name: String,
    val i8n: String = "en-US"
)