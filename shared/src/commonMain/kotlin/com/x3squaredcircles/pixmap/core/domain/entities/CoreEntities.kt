package com.x3squaredcircles.pixmap.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Core domain entities converted from C# to Kotlin for KMM
 * Based on Location.Core.Domain entities
 */

@Serializable
abstract class Entity {
    abstract val id: Int

    fun isTransient(): Boolean = id == 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Entity

        if (isTransient() || other.isTransient()) return false

        return id == other.id
    }

    override fun hashCode(): Int {
        return if (isTransient()) super.hashCode() else id.hashCode()
    }
}

@Serializable
abstract class AggregateRoot : Entity() {
    private val _domainEvents = mutableListOf<DomainEvent>()

    val domainEvents: List<DomainEvent> get() = _domainEvents.toList()

    fun addDomainEvent(event: DomainEvent) {
        _domainEvents.add(event)
    }

    fun removeDomainEvent(event: DomainEvent) {
        _domainEvents.remove(event)
    }

    fun clearDomainEvents() {
        _domainEvents.clear()
    }
}

@Serializable
abstract class DomainEvent {
    val dateOccurred: Instant = Clock.System.now()
}

// Value Objects
@Serializable
data class Coordinate(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        require(!(latitude == 0.0 && longitude == 0.0)) { "Null Island (0,0) is not a valid location" }
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0

        fun create(latitude: Double, longitude: Double): Coordinate {
            return Coordinate(latitude, longitude)
        }
    }

    fun distanceTo(other: Coordinate): Double {
        val lat1Rad = Math.toRadians(latitude)
        val lon1Rad = Math.toRadians(longitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val lon2Rad = Math.toRadians(other.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    fun isWithinDistance(other: Coordinate, maxDistanceKm: Double): Boolean {
        return distanceTo(other) <= maxDistanceKm
    }
}

@Serializable
data class Address(
    val city: String,
    val state: String
) {
    override fun toString(): String {
        return when {
            city.isBlank() && state.isBlank() -> ""
            state.isBlank() -> city
            city.isBlank() -> state
            else -> "$city, $state"
        }
    }
}

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

// Core Entities
@Serializable
data class Location(
    override val id: Int = 0,
    val title: String,
    val description: String,
    val coordinate: Coordinate,
    val address: Address,
    val photoPath: String? = null,
    val isDeleted: Boolean = false,
    val timestamp: Instant = Clock.System.now()
) : AggregateRoot() {

    init {
        require(title.isNotBlank()) { "Title cannot be empty" }
        require(description.length <= 500) { "Description cannot exceed 500 characters" }
    }

    companion object {
        fun create(
            title: String,
            description: String,
            coordinate: Coordinate,
            address: Address
        ): Location {
            val location = Location(
                title = title,
                description = description,
                coordinate = coordinate,
                address = address
            )
            location.addDomainEvent(LocationSavedEvent(location))
            return location
        }
    }

    fun updateDetails(newTitle: String, newDescription: String): Location {
        require(newTitle.isNotBlank()) { "Title cannot be empty" }
        require(newDescription.length <= 500) { "Description cannot exceed 500 characters" }

        val updated = copy(title = newTitle, description = newDescription)
        updated.addDomainEvent(LocationSavedEvent(updated))
        return updated
    }

    fun attachPhoto(photoPath: String): Location {
        require(photoPath.isNotBlank()) { "Photo path cannot be empty" }

        val updated = copy(photoPath = photoPath)
        updated.addDomainEvent(PhotoAttachedEvent(id, photoPath))
        return updated
    }

    fun removePhoto(): Location {
        return copy(photoPath = null)
    }

    fun delete(): Location {
        val updated = copy(isDeleted = true)
        updated.addDomainEvent(LocationDeletedEvent(id))
        return updated
    }

    fun restore(): Location {
        return copy(isDeleted = false)
    }
}

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
    override val id: Int = 0,
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
    override val id: Int = 0,
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

// Domain Events
@Serializable
data class LocationSavedEvent(
    val location: Location
) : DomainEvent()

@Serializable
data class LocationDeletedEvent(
    val locationId: Int
) : DomainEvent()

@Serializable
data class PhotoAttachedEvent(
    val locationId: Int,
    val photoPath: String
) : DomainEvent()

@Serializable
data class WeatherUpdatedEvent(
    val locationId: Int,
    val updateTime: Instant
) : DomainEvent()

// Domain Exceptions
sealed class DomainException(message: String, val code: String) : Exception(message)

class LocationDomainException(message: String, code: String) : DomainException(message, code)
class WeatherDomainException(message: String, code: String) : DomainException(message, code)
class SettingDomainException(message: String, code: String) : DomainException(message, code)
class TipDomainException(message: String, code: String) : DomainException(message, code)
class TipTypeDomainException(message: String, code: String) : DomainException(message, code)