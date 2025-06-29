// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Setting.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * User setting entity
 */
class Setting private constructor() : Entity() {

    private var _key: String = ""
    private var _value: String = ""

    override var id: Int = 0
        private set

    val key: String
        get() = _key

    val value: String
        get() = _value

    var description: String = ""
        private set

    var timestamp: Instant = Clock.System.now()
        private set

    constructor(key: String, value: String, description: String = "", id: Int = 0) : this() {
        require(key.isNotBlank()) { "Key cannot be empty" }
        if (id > 0) {
            require(id > 0) { "Id must be greater than zero" }
            this.id = id
        }
        _key = key
        _value = value
        this.description = description
        timestamp = Clock.System.now()
    }

    fun updateValue(value: String) {
        _value = value
        timestamp = Clock.System.now()
    }

    fun getBooleanValue(): Boolean {
        return value.toBooleanStrictOrNull() ?: false
    }

    fun getIntValue(defaultValue: Int = 0): Int {
        return value.toIntOrNull() ?: defaultValue
    }

    fun getDateTimeValue(): Instant? {
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            null
        }
    }
}