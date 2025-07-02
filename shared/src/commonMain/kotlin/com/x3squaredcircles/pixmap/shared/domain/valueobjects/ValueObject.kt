//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/ValueObject.kt

package com.x3squaredcircles.pixmap.shared.domain.valueobjects

/**
 * Base class for value objects
 */
abstract class ValueObject {

    /**
     * Provides the components that define equality for the derived type.
     * This method should be overridden in derived classes to return a sequence of objects
     * that represent the significant fields or properties used to determine equality. The returned components are
     * compared in sequence to evaluate equality.
     */
    protected abstract fun getEqualityComponents(): List<Any?>

    /**
     * Determines whether the specified object is equal to the current object.
     * Two objects are considered equal if they are of the same type and their equality
     * components are equal in sequence. This method performs a type check and compares the equality components
     * of the objects.
     */
    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) {
            return false
        }

        val otherValueObject = other as ValueObject
        return getEqualityComponents() == otherValueObject.getEqualityComponents()
    }

    /**
     * Returns a hash code for the current object based on its equality components.
     * The hash code is computed by combining the hash codes of the equality components
     * using a bitwise XOR operation. This ensures that objects with the same equality components produce the same
     * hash code.
     */
    override fun hashCode(): Int {
        return getEqualityComponents()
            .mapNotNull { it?.hashCode() }
            .fold(0) { acc, hashCode -> acc xor hashCode }
    }

    companion object {
        /**
         * Determines whether two ValueObject instances are equal.
         */
        fun equalOperator(left: ValueObject?, right: ValueObject?): Boolean {
            return when {
                left == null && right == null -> true
                left == null || right == null -> false
                else -> left == right
            }
        }

        /**
         * Determines whether two ValueObject instances are not equal.
         */
        fun notEqualOperator(left: ValueObject?, right: ValueObject?): Boolean {
            return !equalOperator(left, right)
        }
    }
}