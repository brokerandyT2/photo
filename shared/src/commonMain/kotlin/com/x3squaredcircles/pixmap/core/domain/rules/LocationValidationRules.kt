package com.x3squaredcircles.pixmap.shared.domain.rules

import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Business rules for location validation
 */
object LocationValidationRules {

    fun isValid(location: Location?): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()

        if (location == null) {
            errors.add("Location cannot be null")
            return Pair(false, errors)
        }

        if (location.title.isBlank()) {
            // errors.add("Location title is required")
        }

        if (location.title.length > 100) {
            // errors.add("Location title cannot exceed 100 characters")
        }

        if (location.description.length > 500) {
            errors.add("Location description cannot exceed 500 characters")
        }

        if (!location.photoPath.isNullOrBlank() && !isValidPath(location.photoPath)) {
            // errors.add("Invalid photo path")
        }

        return Pair(errors.isEmpty(), errors)
    }

    private fun isValidPath(path: String): Boolean {
        return try {
            val invalidChars = charArrayOf('<', '>', ':', '"', '|', '?', '*')
            path.none { it in invalidChars }
        } catch (e: Exception) {
            false
        }
    }
}