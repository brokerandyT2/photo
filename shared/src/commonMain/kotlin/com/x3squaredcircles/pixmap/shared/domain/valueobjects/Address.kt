// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/Address.kt
package com.x3squaredcircles.pixmap.shared.domain.valueobjects

/**
 * Value object representing a physical address
 */
data class Address(
    val city: String,
    val state: String
) : ValueObject() {

    override fun getEqualityComponents(): List<Any?> {
        return listOf(city.uppercase(), state.uppercase())
    }

    override fun toString(): String {
        return when {
            city.isBlank() && state.isBlank() -> ""
            state.isBlank() -> city
            city.isBlank() -> state
            else -> "$city, $state"
        }
    }
}