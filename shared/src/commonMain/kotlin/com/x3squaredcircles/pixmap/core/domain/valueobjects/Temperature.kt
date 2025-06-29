package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlin.math.round

/**
 * Value object representing temperature with unit conversions
 */
class Temperature private constructor(
    celsius: Double
) : ValueObject() {

    private val _celsius: Double = round(celsius * 100) / 100.0

    val celsius: Double get() = _celsius
    val fahrenheit: Double get() = (_celsius * 9 / 5) + 32
    val kelvin: Double get() = _celsius + 273.15

    companion object {
        fun fromCelsius(celsius: Double): Temperature {
            return Temperature(celsius)
        }

        fun fromFahrenheit(fahrenheit: Double): Temperature {
            val celsius = (fahrenheit - 32) * 5 / 9
            return Temperature(celsius)
        }

        fun fromKelvin(kelvin: Double): Temperature {
            val celsius = kelvin - 273.15
            return Temperature(celsius)
        }
    }

    override fun getEqualityComponents(): List<Any?> {
        return listOf(_celsius)
    }

    override fun toString(): String {
        return "%.1f°C / %.1f°F".format(_celsius, fahrenheit)
    }
}