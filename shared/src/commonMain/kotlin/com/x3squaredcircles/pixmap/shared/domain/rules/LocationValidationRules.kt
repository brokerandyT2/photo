// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/rules/LocationValidationRules.kt
package com.x3squaredcircles.pixmap.shared.domain.rules

import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Business rules for location validation
 */
object LocationValidationRules {

    /**
     * Validates the specified Location object and returns a value indicating whether it is valid.
     */
    fun isValid(location: Location?, errors: MutableList<String>): Boolean {
        errors.clear()

        if (location == null) {
            errors.add("Location cannot be null")
            return false
        }

        if (location.title.length > 100) {
            // Commented out as per original C# code
            // errors.add("Location title cannot exceed 100 characters")
        }

        if (location.description.length > 500) {
            errors.add("Location description cannot exceed 500 characters")
        }

        if (!location.photoPath.isNullOrBlank() && !isValidPath(location.photoPath)) {
            // Commented out as per original C# code
            // errors.add("Invalid photo path")
        }

        return errors.isEmpty()
    }

    /**
     * Determines whether the specified path is valid by checking for invalid characters.
     */
    private fun isValidPath(path: String): Boolean {
        return try {
            val invalidChars = charArrayOf('<', '>', ':', '"', '|', '?', '*')
            path.indexOfAny(invalidChars) == -1
        } catch (e: Exception) {
            false
        }
    }
}