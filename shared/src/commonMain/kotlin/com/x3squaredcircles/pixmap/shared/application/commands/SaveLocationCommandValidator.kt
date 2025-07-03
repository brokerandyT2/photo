// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/SaveLocationCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult

/**
 * Validates the properties of a [SaveLocationCommand] to ensure they meet the required constraints.
 *
 * This validator enforces the following rules:
 * - [title] must not be empty and must not exceed 100 characters
 * - [description] must not exceed 500 characters
 * - [latitude] must be between -90 and 90 degrees
 * - [longitude] must be between -180 and 180 degrees
 * - Coordinates cannot represent Null Island (0,0)
 * - If provided, [photoPath] must be a valid file path
 */
class SaveLocationCommandValidator : IValidator<SaveLocationCommand> {

    /**
     * Validates the properties of a save location command to ensure they meet the required constraints.
     *
     * This validator enforces the following rules:
     * - [title] must not be empty and must not exceed 100 characters
     * - [description] must not exceed 500 characters
     * - [latitude] must be between -90 and 90 degrees
     * - [longitude] must be between -180 and 180 degrees
     * - Coordinates cannot represent Null Island (0,0)
     * - [photoPath], if provided, must be a valid file path
     */
    override fun validate(command: SaveLocationCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate Title
        if (command.title.isBlank()) {
            errors.add("Title is required")
        } else if (command.title.length > 100) {
            errors.add("Title cannot exceed 100 characters")
        }

        // Validate Description
        if (command.description.length > 500) {
            errors.add("Description cannot exceed 500 characters")
        }

        // Validate Latitude
        if (command.latitude !in -90.0..90.0) {
            errors.add("Latitude must be between -90 and 90 degrees")
        }

        // Validate Longitude
        if (command.longitude !in -180.0..180.0) {
            errors.add("Longitude must be between -180 and 180 degrees")
        }

        // Null Island validation (0,0 coordinates)
        if (command.latitude == 0.0 && command.longitude == 0.0) {
            errors.add("Coordinates cannot represent Null Island (0,0)")
        }

        // Validate PhotoPath (if provided)
        if (!command.photoPath.isNullOrBlank() && !isValidPath(command.photoPath)) {
            errors.add("Photo path contains invalid characters")
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }

    /**
     * Determines whether the specified path is valid.
     *
     * A path is considered valid if it does not contain invalid characters and resolves to a non-empty file name.
     * If the path is null or consists only of whitespace, it is treated as valid.
     *
     * @param path The path to validate. Can be null or empty.
     * @return true if the path is null, empty, or contains no invalid characters and resolves to a valid file name; otherwise, false.
     */
    private fun isValidPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return true

        return try {
            // Check for invalid characters directly
            val invalidChars = setOf('<', '>', '"', '|', '?', '*', ':', '\\', '/')
            if (path.any { char -> char.code < 32 || invalidChars.contains(char) }) {
                false
            } else {
                // Some paths might not throw but still return empty filenames
                val fileName = getFileName(path)
                fileName.isNotEmpty()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts the file name from a path string.
     * This is a simplified version for KMM compatibility.
     */
    private fun getFileName(path: String): String {
        val separators = setOf('/', '\\')
        val lastSeparatorIndex = path.indexOfLast { it in separators }
        return if (lastSeparatorIndex == -1) path else path.substring(lastSeparatorIndex + 1)
    }
}