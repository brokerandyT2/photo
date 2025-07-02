//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Tip.kt

package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import kotlinx.serialization.Serializable

/**
 * Photography tip entity
 */
@Serializable
data class Tip(
    override val id: Int = 0,
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String = "",
    val shutterSpeed: String = "",
    val iso: String = "",
    val i8n: String = "en-US"
) : Entity() {

    init {
        require(title.isNotBlank()) { "Title cannot be empty" }
        require(tipTypeId > 0) { "TipTypeId must be greater than zero" }
    }

    companion object {
        /**
         * Factory method to create a new tip
         */
        fun create(tipTypeId: Int, title: String, content: String): Tip {
            return Tip(
                tipTypeId = tipTypeId,
                title = title,
                content = content
            )
        }
    }

    /**
     * Updates the photography settings for this tip
     */
    fun updatePhotographySettings(fstop: String, shutterSpeed: String, iso: String): Tip {
        return copy(
            fstop = fstop,
            shutterSpeed = shutterSpeed,
            iso = iso
        )
    }

    /**
     * Updates the title and content of this tip
     */
    fun updateContent(newTitle: String, newContent: String): Tip {
        require(newTitle.isNotBlank()) { "Title cannot be empty" }
        return copy(
            title = newTitle,
            content = newContent
        )
    }

    /**
     * Sets the localization for this tip
     */
    fun setLocalization(localization: String): Tip {
        return copy(i8n = localization.ifBlank { "en-US" })
    }

    /**
     * Gets a formatted string of the photography settings
     */
    fun getPhotographySettingsDisplay(): String {
        val parts = mutableListOf<String>()

        if (fstop.isNotBlank()) {
            parts.add("F: $fstop")
        }
        if (shutterSpeed.isNotBlank()) {
            parts.add("Shutter: $shutterSpeed")
        }
        if (iso.isNotBlank()) {
            parts.add("ISO: $iso")
        }

        return parts.joinToString(" ")
    }

    /**
     * Checks if this tip has photography settings
     */
    fun hasPhotographySettings(): Boolean {
        return fstop.isNotBlank() || shutterSpeed.isNotBlank() || iso.isNotBlank()
    }
}