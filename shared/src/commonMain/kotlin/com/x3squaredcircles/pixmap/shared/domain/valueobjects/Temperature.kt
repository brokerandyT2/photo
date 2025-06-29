// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/Temperature.kt
package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlin.math.round

/**
 * Value object representing temperature with unit conversions
 */
data class Temperature private constructor(
    private val celsius: Double
) : ValueObject() {

    val celsiusValue: Double = celsius
    val fahrenheit: Double = (celsius * 9 / 5) + 32
    val kelvin: Double = celsius + 273.15

    companion object {
        fun fromCelsius(celsius: Double): Temperature {
            return Temperature((celsius * 100).round() / 100)
        }

        fun fromFahrenheit(fahrenheit: Double): Temperature {
            val celsius = (fahrenheit - 32) * 5 / 9
            return Temperature((celsius * 100).round() / 100)
        }

        fun fromKelvin(kelvin: Double): Temperature {
            val celsius = kelvin - 273.15
            return Temperature((celsius * 100).round() / 100)
        }
    }

    override fun getEqualityComponents(): List<Any?> {
        return listOf(celsius)
    }

    override fun toString(): String {
        return "${String.format("%.1f", celsius)}°C / ${String.format("%.1f", fahrenheit)}°F"
    }

    private fun Double.round(): Double = round(this)
}