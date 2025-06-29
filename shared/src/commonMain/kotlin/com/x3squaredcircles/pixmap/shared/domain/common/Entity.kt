// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/common/Entity.kt
package com.x3squaredcircles.pixmap.shared.domain.common

/**
 * Base class for all domain entities
 */
abstract class Entity {
    private var _requestedHashCode: Int? = null

    open val id: Int = 0

    /**
     * Determines whether the entity is considered transient.
     */
    fun isTransient(): Boolean {
        return id == 0
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Entity) return false
        if (this === other) return true
        if (this::class != other::class) return false

        if (isTransient() || other.isTransient()) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        if (!isTransient()) {
            if (_requestedHashCode == null) {
                _requestedHashCode = id.hashCode() xor 31
            }
            return _requestedHashCode!!
        }
        return super.hashCode()
    }
}