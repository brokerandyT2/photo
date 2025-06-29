package com.x3squaredcircles.pixmap.shared.application.dto

/**
 * Data transfer object for tip data
 */
data class TipDto(
    val id: Int,
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String,
    val shutterSpeed: String,
    val iso: String,
    val i8n: String
)

/**
 * Data transfer object for tip type data
 */
data class TipTypeDto(
    val id: Int,
    val name: String,
    val i8n: String,
    val tips: List<TipDto>
)