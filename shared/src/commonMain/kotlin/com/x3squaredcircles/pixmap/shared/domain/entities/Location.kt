//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Location.kt

package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.AggregateRoot
import com.x3squaredcircles.pixmap.shared.domain.events.LocationDeletedEvent
import com.x3squaredcircles.pixmap.shared.domain.events.LocationSavedEvent
import com.x3squaredcircles.pixmap.shared.domain.events.PhotoAttachedEvent
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Location aggregate root
 */
@Serializable
class Location private constructor() : AggregateRoot() {

    private var _title: String = ""
    private var _description: String = ""
    private var _coordinate: Coordinate? = null
    private var _address: Address? = null
    private var _photoPath: String? = null
    private var _isDeleted: Boolean = false
    private var _timestamp: Instant = Clock.System.now()
    private var _id: Int = 0

    override val id: Int
        get() = _id

    val title: String
        get() = _title

    val description: String
        get() = _description

    val coordinate: Coordinate
        get() = _coordinate ?: throw IllegalStateException("Coordinate is not set")

    val address: Address
        get() = _address ?: throw IllegalStateException("Address is not set")

    val photoPath: String?
        get() = _photoPath

    val isDeleted: Boolean
        get() = _isDeleted

    val timestamp: Instant
        get() = _timestamp

    constructor(title: String, description: String, coordinate: Coordinate, address: Address) : this() {
        require(title.isNotBlank()) { "Title cannot be empty" }
        _title = title
        _description = description
        _coordinate = coordinate
        _address = address
        _timestamp = Clock.System.now()

        addDomainEvent(LocationSavedEvent(this))
    }

    fun updateDetails(title: String, description: String) {
        require(title.isNotBlank()) { "Title cannot be empty" }
        _title = title
        _description = description
        addDomainEvent(LocationSavedEvent(this))
    }

    fun updateCoordinate(coordinate: Coordinate) {
        _coordinate = coordinate
        addDomainEvent(LocationSavedEvent(this))
    }

    fun attachPhoto(photoPath: String) {
        require(photoPath.isNotBlank()) { "Photo path cannot be empty" }
        _photoPath = photoPath
        addDomainEvent(PhotoAttachedEvent(id, photoPath))
    }

    fun removePhoto() {
        _photoPath = null
    }

    fun delete() {
        _isDeleted = true
        addDomainEvent(LocationDeletedEvent(id))
    }

    fun restore() {
        _isDeleted = false
    }

    /**
     * Internal method for setting ID (used by ORM/repository)
     */
    internal fun setId(newId: Int) {
        require(newId > 0) { "Id must be greater than zero" }
        _id = newId
    }

    /**
     * Internal method for setting timestamp (used by ORM/repository)
     */
    internal fun setTimestamp(timestamp: Instant) {
        _timestamp = timestamp
    }

    /**
     * Internal method for setting deleted status (used by ORM/repository)
     */
    internal fun setDeleted(deleted: Boolean) {
        _isDeleted = deleted
    }

    /**
     * Internal method for setting photo path (used by ORM/repository)
     */
    internal fun setPhotoPath(path: String?) {
        _photoPath = path
    }
}