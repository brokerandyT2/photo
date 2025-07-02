//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/Temperature.kt

package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * Value object representing temperature with unit conversions
 */
@Serializable
data class Temperature private constructor(
    private val celsius: Double
) : ValueObject() {

    /**
     * Temperature in Celsius
     */
    val celsiusValue: Double = celsius

    /**
     * Temperature in Fahrenheit
     */
    val fahrenheit: Double = (celsius * 9 / 5) + 32

    /**
     * Temperature in Kelvin
     */
    val kelvin: Double = celsius + 273.15

    companion object {
        /**
         * Creates a Temperature instance from a specified temperature in degrees Celsius
         */
        fun fromCelsius(celsius: Double): Temperature {
            return Temperature(round(celsius * 100) / 100)
        }

        /**
         * Creates a Temperature instance from a temperature value in degrees Fahrenheit
         */
        fun fromFahrenheit(fahrenheit: Double): Temperature {
            val celsius = (fahrenheit - 32) * 5 / 9
            return Temperature(round(celsius * 100) / 100)
        }

        /**
         * Creates a Temperature instance from a temperature value in Kelvin
         */
        fun fromKelvin(kelvin: Double): Temperature {
            require(kelvin >= 0) { "Kelvin temperature cannot be negative" }
            val celsius = kelvin - 273.15
            return Temperature(round(celsius * 100) / 100)
        }

        /**
         * Creates a Temperature instance with validation
         */
        fun create(value: Double, unit: TemperatureUnit): Temperature {
            return when (unit) {
                TemperatureUnit.CELSIUS -> fromCelsius(value)
                TemperatureUnit.FAHRENHEIT -> fromFahrenheit(value)
                TemperatureUnit.KELVIN -> fromKelvin(value)
            }
        }
    }

    /**
     * Gets temperature in the specified unit
     */
    fun getValue(unit: TemperatureUnit): Double {
        return when (unit) {
            TemperatureUnit.CELSIUS -> celsiusValue
            TemperatureUnit.FAHRENHEIT -> fahrenheit
            TemperatureUnit.KELVIN -> kelvin
        }
    }

    /**
     * Gets a formatted temperature string in the specified unit
     */
    fun toString(unit: TemperatureUnit, decimals: Int = 1): String {
        val value = getValue(unit)
        val symbol = when (unit) {
            TemperatureUnit.CELSIUS -> "°C"
            TemperatureUnit.FAHRENHEIT -> "°F"
            TemperatureUnit.KELVIN -> "K"
        }
        return "${String.format("%.${decimals}f", value)}$symbol"
    }

    /**
     * Gets a formatted temperature string showing both Celsius and Fahrenheit
     */
    fun toDisplayString(): String {
        return "${String.format("%.1f", celsiusValue)}°C / ${String.format("%.1f", fahrenheit)}°F"
    }

    /**
     * Checks if temperature is below freezing (0°C)
     */
    fun isFreezing(): Boolean = celsiusValue <= 0.0

    /**
     * Checks if temperature is above boiling point (100°C)
     */
    fun isBoiling(): Boolean = celsiusValue >= 100.0

    /**
     * Gets a human-readable description of the temperature
     */
    fun getDescription(): String {
        return when {
            celsiusValue < -20 -> "Very Cold"
            celsiusValue < 0 -> "Cold"
            celsiusValue < 10 -> "Cool"
            celsiusValue < 20 -> "Mild"
            celsiusValue < 30 -> "Warm"
            celsiusValue < 35 -> "Hot"
            else -> "Very Hot"
        }
    }

    override fun getEqualityComponents(): List<Any?> {
        return listOf(celsiusValue)
    }

    override fun toString(): String {
        return toDisplayString()
    }
}

/**
 * Temperature units enumeration
 */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
    KELVIN
}