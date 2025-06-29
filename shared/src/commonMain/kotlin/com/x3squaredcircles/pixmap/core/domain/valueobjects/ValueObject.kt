package com.x3squaredcircles.pixmap.shared.domain.valueobjects

/**
 * Base class for value objects
 */
abstract class ValueObject {

    protected abstract fun getEqualityComponents(): List<Any?>

    override fun equals(other: Any?): Boolean {
        if (other == null || other::class != this::class) {
            return false
        }

        val otherValueObject = other as ValueObject
        return getEqualityComponents() == otherValueObject.getEqualityComponents()
    }

    override fun hashCode(): Int {
        return getEqualityComponents()
            .map { it?.hashCode() ?: 0 }
            .fold(0) { acc, hash -> acc xor hash }
    }
}