// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/validators/AttachPhotoCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.validators

import com.x3squaredcircles.pixmap.shared.application.commands.AttachPhotoCommand
import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator

/**
 * Validator for AttachPhotoCommand
 */
class AttachPhotoCommandValidator : IValidator<AttachPhotoCommand> {

    override fun validate(command: AttachPhotoCommand): ValidationResult {
        val errors = mutableListOf<String>()

        if (command.locationId <= 0) {
            errors.add("Location ID must be greater than 0")
        }

        if (command.photoPath.isBlank()) {
            errors.add("Photo path is required")
        } else if (!isValidPath(command.photoPath)) {
            errors.add("Photo path is not valid")
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }

    /**
     * Determines whether the specified path is valid
     */
    private fun isValidPath(path: String): Boolean {
        if (path.isBlank()) return false

        // Check for invalid characters
        val invalidChars = listOf('|', '<', '>', '"', '?', '*')
        if (invalidChars.any { path.contains(it) }) {
            return false
        }

        // Basic path validation - should not be just whitespace
        if (path.trim() != path) {
            return false
        }

        // Additional validation could include:
        // - Check if path looks like a valid file path format
        // - Check file extension if needed
        // - Platform-specific validation

        return true
    }
}
