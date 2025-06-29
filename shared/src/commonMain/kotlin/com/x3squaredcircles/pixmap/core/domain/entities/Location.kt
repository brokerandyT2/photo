package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.AggregateRoot
import com.x3squaredcircles.pixmap.shared.domain.events.LocationDeletedEvent
import com.x3squaredcircles.pixmap.shared.domain.events.LocationSavedEvent
import com.x3squaredcircles.pixmap.shared.domain.events.PhotoAttachedEvent
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Location aggregate root
 */
class Location : AggregateRoot {

    private var _title: String = ""
    private var _description: String = ""
    private var _coordinate: Coordinate? = null
    private var _address: Address? = null
    private var _photoPath: String? = null
    private var _isDeleted: Boolean = false
    private var _timestamp: Instant = Clock.System.now()

    var title: String
        get() = _title
        private set(value) {
            require(value.isNotBlank()) { "Title cannot be empty" }
            _title = value
        }

    var description: String
        get() = _description
        private set(value) {
            _description = value
        }

    val coordinate: Coordinate
        get() = _coordinate ?: throw IllegalStateException("Coordinate cannot be null")

    val address: Address
        get() = _address ?: throw IllegalStateException("Address cannot be null")

    var photoPath: String?
        get() = _photoPath
        private set(value) {
            _photoPath = value
        }

    var isDeleted: Boolean
        get() = _isDeleted
        private set(value) {
            _isDeleted = value
        }

    var timestamp: Instant
        get() = _timestamp
        private set(value) {
            _timestamp = value
        }

    // For ORM
    constructor()

    constructor(title: String, description: String, coordinate: Coordinate, address: Address) {
        this.title = title
        this.description = description
        this._coordinate = coordinate
        this._address = address
        this.timestamp = Clock.System.now()

        addDomainEvent(LocationSavedEvent(this))
    }

    fun updateDetails(title: String, description: String) {
        this.title = title
        this.description = description
        addDomainEvent(LocationSavedEvent(this))
    }

    fun updateCoordinate(coordinate: Coordinate) {
        this._coordinate = coordinate
        addDomainEvent(LocationSavedEvent(this))
    }

    fun attachPhoto(photoPath: String) {
        require(photoPath.isNotBlank()) { "Photo path cannot be empty" }
        this.photoPath = photoPath
        addDomainEvent(PhotoAttachedEvent(id, photoPath))
    }

    fun removePhoto() {
        this.photoPath = null
    }

    fun delete() {
        this.isDeleted = true
        addDomainEvent(LocationDeletedEvent(id))
    }

    fun restore() {
        this.isDeleted = false
    }
}