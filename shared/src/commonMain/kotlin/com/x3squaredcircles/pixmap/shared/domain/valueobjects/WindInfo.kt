//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/WindInfo.kt

package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * Value object representing wind information
 */
@Serializable
data class WindInfo(
    val speed: Double,
    val direction: Double,
    val gust: Double? = null
) {

    init {
        require(speed >= 0) { "Wind speed cannot be negative" }
        require(direction in 0.0..360.0) { "Wind direction must be between 0 and 360 degrees" }
        if (gust != null) {
            require(gust >= 0) { "Wind gust cannot be negative" }
        }
    }

    /**
     * Gets cardinal direction from degrees
     */
    fun getCardinalDirection(): String {
        val directions = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        val index = (round(direction / 22.5) % 16).toInt()
        return directions[index]
    }

    /**
     * Gets a more detailed cardinal direction
     */
    fun getDetailedCardinalDirection(): String {
        return when {
            direction == 0.0 || direction == 360.0 -> "North"
            direction == 90.0 -> "East"
            direction == 180.0 -> "South"
            direction == 270.0 -> "West"
            direction < 90.0 -> "Northeast"
            direction < 180.0 -> "Southeast"
            direction < 270.0 -> "Southwest"
            else -> "Northwest"
        }
    }

    /**
     * Checks if this is considered a calm wind (less than 1 mph/kph)
     */
    fun isCalm(): Boolean = speed < 1.0

    /**
     * Gets wind speed rounded to 1 decimal place
     */
    fun getSpeedRounded(): Double = round(speed * 10) / 10

    /**
     * Gets direction rounded to nearest degree
     */
    fun getDirectionRounded(): Int = round(direction).toInt()

    /**
     * Gets gust speed rounded to 1 decimal place
     */
    fun getGustRounded(): Double? = gust?.let { round(it * 10) / 10 }

    /**
     * Creates a copy with speed in different units (if needed for conversion)
     */
    fun withSpeed(newSpeed: Double): WindInfo {
        return copy(speed = newSpeed)
    }

    /**
     * Gets a formatted string representation
     */
    override fun toString(): String {
        val gustInfo = gust?.let { ", Gust: ${getGustRounded()}" } ?: ""
        return "${getSpeedRounded()} mph from ${getCardinalDirection()} (${getDirectionRounded()}°)$gustInfo"
    }

    /**
     * Gets a simple string representation
     */
    fun toSimpleString(): String {
        return "${getSpeedRounded()} mph ${getCardinalDirection()}"
    }

    /**
     * Gets wind description based on Beaufort scale
     */
    fun getBeaufortDescription(): String {
        return when {
            speed < 1 -> "Calm"
            speed < 4 -> "Light air"
            speed < 8 -> "Light breeze"
            speed < 13 -> "Gentle breeze"
            speed < 19 -> "Moderate breeze"
            speed < 25 -> "Fresh breeze"
            speed < 32 -> "Strong breeze"
            speed < 39 -> "Near gale"
            speed < 47 -> "Gale"
            speed < 55 -> "Strong gale"
            speed < 64 -> "Storm"
            speed < 73 -> "Violent storm"
            else -> "Hurricane"
        }
    }

    companion object {
        /**
         * Creates a calm wind condition
         */
        fun calm(): WindInfo = WindInfo(0.0, 0.0)

        /**
         * Creates wind info with validation
         */
        fun create(speed: Double, direction: Double, gust: Double? = null): WindInfo {
            return WindInfo(
                speed = maxOf(0.0, speed),
                direction = direction % 360.0,
                gust = gust?.let { maxOf(0.0, it) }
            )
        }
    }
}