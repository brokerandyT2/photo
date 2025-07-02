//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/domain/entities/CoreEntities.kt
package com.x3squaredcircles.pixmap.core.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.DomainEvent
import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import com.x3squaredcircles.pixmap.shared.domain.common.AggregateRoot
import com.x3squaredcircles.pixmap.shared.domain.events.LocationDeletedEvent
import com.x3squaredcircles.pixmap.shared.domain.events.LocationSavedEvent
import com.x3squaredcircles.pixmap.shared.domain.events.PhotoAttachedEvent
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Core domain entities converted from C# to Kotlin for KMM
 * Based on Location.Core.Domain entities
 * Note: This file re-exports shared domain types for backward compatibility
 */

// Re-export shared domain types for backward compatibility
typealias CoreEntity = com.x3squaredcircles.pixmap.shared.domain.common.Entity
typealias CoreAggregateRoot = com.x3squaredcircles.pixmap.shared.domain.common.AggregateRoot
typealias CoreLocation = com.x3squaredcircles.pixmap.shared.domain.entities.Location
typealias CoreCoordinate = com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
typealias CoreAddress = com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address

// Additional core value objects not in shared domain
@Serializable
data class WindInfo(
    val speed: Double,
    val direction: Double,
    val gust: Double? = null
) {
    init {
        require(speed >= 0) { "Wind speed cannot be negative" }
        require(direction in 0.0..360.0) { "Wind direction must be between 0 and 360 degrees" }
    }

    fun getCardinalDirection(): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((direction / 22.5) + 0.5).toInt() % 16
        return directions[index]
    }
}

@Serializable
data class Setting(
    val key: String,
    val value: String,
    val description: String = "",
    val timestamp: Instant = Clock.System.now()
) : Entity() {

    init {
        require(key.isNotBlank()) { "Key cannot be empty" }
    }

    companion object {
        fun create(key: String, value: String, description: String = ""): Setting {
            return Setting(
                key = key,
                value = value,
                description = description
            )
        }
    }

    fun updateValue(newValue: String): Setting {
        return copy(value = newValue, timestamp = Clock.System.now())
    }

    fun getBooleanValue(): Boolean {
        return value.lowercase() == "true"
    }

    fun getIntValue(defaultValue: Int = 0): Int {
        return value.toIntOrNull() ?: defaultValue
    }

    fun getDoubleValue(defaultValue: Double = 0.0): Double {
        return value.toDoubleOrNull() ?: defaultValue
    }
}

@Serializable
data class TipType(
    val name: String,
    val i18n: String = "en-US"
) : Entity() {

    init {
        require(name.isNotBlank()) { "Name cannot be empty" }
    }

    companion object {
        fun create(name: String): TipType {
            return TipType(name = name)
        }
    }

    fun setLocalization(localization: String): TipType {
        return copy(i18n = localization)
    }
}

@Serializable
data class Tip(
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String = "",
    val shutterSpeed: String = "",
    val iso: String = "",
    val i18n: String = "en-US"
) : Entity() {

    init {
        require(title.isNotBlank()) { "Title cannot be empty" }
        require(tipTypeId > 0) { "TipTypeId must be greater than 0" }
    }

    companion object {
        fun create(tipTypeId: Int, title: String, content: String): Tip {
            return Tip(
                tipTypeId = tipTypeId,
                title = title,
                content = content
            )
        }
    }

    fun updatePhotographySettings(fstop: String, shutterSpeed: String, iso: String): Tip {
        return copy(
            fstop = fstop,
            shutterSpeed = shutterSpeed,
            iso = iso
        )
    }

    fun updateContent(newTitle: String, newContent: String): Tip {
        require(newTitle.isNotBlank()) { "Title cannot be empty" }
        return copy(title = newTitle, content = newContent)
    }

    fun setLocalization(localization: String): Tip {
        return copy(i18n = localization)
    }
}

// Core entities that use shared domain types
@Serializable
data class LocationAggregate(
    val title: String,
    val description: String,
    @Contextual val coordinate: CoreCoordinate,
    @Contextual val address: CoreAddress,
    val photoPath: String? = null,
    val isDeleted: Boolean = false,
    val timestamp: Instant = Clock.System.now()
) : CoreAggregateRoot() {

    init {
        require(title.isNotBlank()) { "Title cannot be empty" }
        require(description.length <= 500) { "Description cannot exceed 500 characters" }
    }

    companion object {
        fun create(
            title: String,
            description: String,
            coordinate: CoreCoordinate,
            address: CoreAddress
        ): LocationAggregate {
            val location = LocationAggregate(
                title = title,
                description = description,
                coordinate = coordinate,
                address = address
            )
            location.addDomainEvent(LocationSavedEvent(CoreLocation(title, description, coordinate, address)))
            return location
        }
    }

    fun updateDetails(newTitle: String, newDescription: String): LocationAggregate {
        require(newTitle.isNotBlank()) { "Title cannot be empty" }
        require(newDescription.length <= 500) { "Description cannot exceed 500 characters" }

        val updated = copy(title = newTitle, description = newDescription)
        updated.addDomainEvent(LocationSavedEvent(CoreLocation(newTitle, newDescription, coordinate, address)))
        return updated
    }

    fun attachPhoto(photoPath: String): LocationAggregate {
        require(photoPath.isNotBlank()) { "Photo path cannot be empty" }

        val updated = copy(photoPath = photoPath)
        updated.addDomainEvent(PhotoAttachedEvent(id, photoPath))
        return updated
    }

    fun removePhoto(): LocationAggregate {
        return copy(photoPath = null)
    }

    fun delete(): LocationAggregate {
        val updated = copy(isDeleted = true)
        updated.addDomainEvent(LocationDeletedEvent(id))
        return updated
    }

    fun restore(): LocationAggregate {
        return copy(isDeleted = false)
    }
}

// Domain Exceptions
sealed class DomainException(message: String, val code: String) : Exception(message)

class LocationDomainException(message: String, code: String) : DomainException(message, code)
class WeatherDomainException(message: String, code: String) : DomainException(message, code)
class SettingDomainException(message: String, code: String) : DomainException(message, code)
class TipDomainException(message: String, code: String) : DomainException(message, code)
class TipTypeDomainException(message: String, code: String) : DomainException(message, code)