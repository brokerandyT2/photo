//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Setting.kt

package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * User setting entity
 */
@Serializable
data class Setting(
    override val id: Int = 0,
    val key: String,
    val value: String,
    val description: String = "",
    val timestamp: Instant = Clock.System.now()
) : Entity() {

    init {
        require(key.isNotBlank()) { "Key cannot be empty" }
    }

    companion object {
        /**
         * Factory method to create a new setting
         */
        fun create(key: String, value: String, description: String = ""): Setting {
            return Setting(
                key = key,
                value = value,
                description = description
            )
        }

        /**
         * Creates a setting with a specific ID (for ORM/repository use)
         */
        fun create(id: Int, key: String, value: String, description: String = ""): Setting {
            return Setting(
                id = id,
                key = key,
                value = value,
                description = description
            )
        }
    }

    /**
     * Updates the value of this setting
     */
    fun updateValue(newValue: String): Setting {
        return copy(
            value = newValue,
            timestamp = Clock.System.now()
        )
    }

    /**
     * Updates both value and description
     */
    fun updateValueAndDescription(newValue: String, newDescription: String): Setting {
        return copy(
            value = newValue,
            description = newDescription,
            timestamp = Clock.System.now()
        )
    }

    /**
     * Gets the value as a boolean
     */
    fun getBooleanValue(): Boolean {
        return value.toBooleanStrictOrNull() ?: false
    }

    /**
     * Gets the value as an integer with optional default
     */
    fun getIntValue(defaultValue: Int = 0): Int {
        return value.toIntOrNull() ?: defaultValue
    }

    /**
     * Gets the value as a double with optional default
     */
    fun getDoubleValue(defaultValue: Double = 0.0): Double {
        return value.toDoubleOrNull() ?: defaultValue
    }

    /**
     * Gets the value as a long with optional default
     */
    fun getLongValue(defaultValue: Long = 0L): Long {
        return value.toLongOrNull() ?: defaultValue
    }

    /**
     * Gets the value as a LocalDateTime (converted from device timezone)
     */
    fun getDateTimeValue(): LocalDateTime? {
        return try {
            // Try to parse as Instant first, then convert to LocalDateTime
            val instant = Instant.parse(value)
            instant.toLocalDateTime(TimeZone.currentSystemDefault())
        } catch (e: Exception) {
            try {
                // Try to parse directly as LocalDateTime
                LocalDateTime.parse(value)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Checks if the value is empty or blank
     */
    fun hasValue(): Boolean {
        return value.isNotBlank()
    }

    /**
     * Gets a formatted display string for the setting
     */
    fun getDisplayString(): String {
        return if (description.isNotBlank()) {
            "$key: $value ($description)"
        } else {
            "$key: $value"
        }
    }

    /**
     * Creates a copy with updated timestamp
     */
    fun touch(): Setting {
        return copy(timestamp = Clock.System.now())
    }
}