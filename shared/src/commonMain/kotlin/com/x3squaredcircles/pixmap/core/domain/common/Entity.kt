package com.x3squaredcircles.pixmap.shared.domain.common

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IEntity

/**
 * Base class for all domain entities
 */
abstract class Entity : IEntity {
    private var requestedHashCode: Int? = null

    override var id: Int = 0
        protected set

    fun isTransient(): Boolean {
        return id == 0
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Entity) {
            return false
        }

        if (this === other) {
            return true
        }

        if (this::class != other::class) {
            return false
        }

        if (isTransient() || other.isTransient()) {
            return false
        }

        return id == other.id
    }

    override fun hashCode(): Int {
        if (!isTransient()) {
            if (requestedHashCode == null) {
                requestedHashCode = id.hashCode() xor 31
            }
            return requestedHashCode!!
        }
        return super.hashCode()
    }
}