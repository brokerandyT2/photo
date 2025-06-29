package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * User setting entity
 */
class Setting : Entity {

    private var _key: String = ""
    private var _value: String = ""

    var key: String
        get() = _key
        private set(value) {
            require(value.isNotBlank()) { "Key cannot be empty" }
            _key = value
        }

    var value: String
        get() = _value
        private set(value) {
            _value = value
        }

    var description: String = ""
        private set

    var timestamp: Instant = Clock.System.now()
        private set

    // For ORM
    constructor()

    constructor(key: String, value: String, description: String = "", id: Int = 0) {
        if (id > 0) {
            require(id > 0) { "Id must be greater than zero" }
            this.id = id
        }
        this.key = key
        this.value = value
        this.description = description
        this.timestamp = Clock.System.now()
    }

    fun updateValue(value: String) {
        this.value = value
        this.timestamp = Clock.System.now()
    }

    fun getBooleanValue(): Boolean {
        return value.toBooleanStrictOrNull() ?: false
    }

    fun getIntValue(defaultValue: Int = 0): Int {
        return value.toIntOrNull() ?: defaultValue
    }

    fun getInstantValue(): Instant? {
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            null
        }
    }
}