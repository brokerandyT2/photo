//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/TipType.kt

package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.serialization.Serializable

/**
 * Tip category entity
 */
@Serializable
data class TipType(
    override val id: Int = 0,
    val name: String,
    val i8n: String = "en-US"
) : Entity() {

    init {
        require(name.isNotBlank()) { "Name cannot be empty" }
    }

    companion object {
        /**
         * Factory method to create a new tip type
         */
        fun create(name: String): TipType {
            return TipType(name = name)
        }

        /**
         * Creates a tip type with localization
         */
        fun create(name: String, localization: String): TipType {
            return TipType(
                name = name,
                i8n = localization.ifBlank { "en-US" }
            )
        }
    }

    /**
     * Sets the localization for this tip type
     */
    fun setLocalization(localization: String): TipType {
        return copy(i8n = localization.ifBlank { "en-US" })
    }

    /**
     * Updates the name of this tip type
     */
    fun updateName(newName: String): TipType {
        require(newName.isNotBlank()) { "Name cannot be empty" }
        return copy(name = newName)
    }

    /**
     * Checks if this tip type has a specific localization
     */
    fun hasLocalization(localization: String): Boolean {
        return i8n.equals(localization, ignoreCase = true)
    }

    /**
     * Gets the display name based on localization
     */
    fun getDisplayName(): String {
        return name
    }
}