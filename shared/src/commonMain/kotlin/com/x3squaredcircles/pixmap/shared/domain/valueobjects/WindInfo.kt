// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/WindInfo.kt
package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlin.math.round

/**
 * Value object representing wind information
 */
data class WindInfo(
    val speed: Double,
    val direction: Double,
    val gust: Double? = null
) : ValueObject() {

    init {
        require(speed >= 0) { "Wind speed cannot be negative" }
        require(direction in 0.0..360.0) { "Wind direction must be between 0 and 360 degrees" }
    }

    private val roundedSpeed = (speed * 100).round() / 100
    private val roundedDirection = direction.round()
    private val roundedGust = gust?.let { (it * 100).round() / 100 }

    /**
     * Gets cardinal direction from degrees
     */
    fun getCardinalDirection(): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = (roundedDirection / 22.5).round().toInt() % 16
        return directions[index]
    }

    override fun getEqualityComponents(): List<Any?> {
        return listOf(roundedSpeed, roundedDirection, roundedGust ?: 0.0)
    }

    override fun toString(): String {
        val gustInfo = roundedGust?.let { ", Gust: ${String.format("%.1f", it)}" } ?: ""
        return "${String.format("%.1f", roundedSpeed)} mph from ${getCardinalDirection()} (${roundedDirection.toInt()}°)$gustInfo"
    }

    private fun Double.round(): Double = round(this)
}