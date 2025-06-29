package com.x3squaredcircles.pixmap.shared.domain.valueobjects

/**
 * Value object representing a physical address
 */
class Address(
    city: String?,
    state: String?
) : ValueObject() {

    val city: String = city ?: ""
    val state: String = state ?: ""

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