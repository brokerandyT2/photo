package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlin.math.round

/**
 * Value object representing wind information
 */
class WindInfo(
    speed: Double,
    direction: Double,
    gust: Double? = null
) : ValueObject() {

    val speed: Double
    val direction: Double
    val gust: Double?

    init {
        require(speed >= 0) { "Wind speed cannot be negative" }
        require(direction in 0.0..360.0) { "Wind direction must be between 0 and 360 degrees" }

        this.speed = round(speed * 100) / 100.0
        this.direction = round(direction)
        this.gust = gust?.let { round(it * 100) / 100.0 }
    }

    fun getCardinalDirection(): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = (round(direction / 22.5) % 16).toInt()
        return directions[index]
    }

    override fun getEqualityComponents(): List<Any?> {
        return listOf(speed, direction, gust ?: 0.0)
    }

    override fun toString(): String {
        val gustInfo = gust?.let { ", Gust: %.1f".format(it) } ?: ""
        return "%.1f mph from %s (%.0f°)%s".format(speed, getCardinalDirection(), direction, gustInfo)
    }
}