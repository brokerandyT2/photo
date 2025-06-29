// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/ValueObject.kt
package com.x3squaredcircles.pixmap.shared.domain.valueobjects

/**
 * Base class for value objects
 */
abstract class ValueObject {

    /**
     * Provides the components that define equality for the derived type.
     */
    protected abstract fun getEqualityComponents(): List<Any?>

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) {
            return false
        }

        val otherValueObject = other as ValueObject
        return getEqualityComponents() == otherValueObject.getEqualityComponents()
    }

    override fun hashCode(): Int {
        return getEqualityComponents()
            .mapNotNull { it?.hashCode() }
            .fold(0) { acc, hashCode -> acc xor hashCode }
    }
}