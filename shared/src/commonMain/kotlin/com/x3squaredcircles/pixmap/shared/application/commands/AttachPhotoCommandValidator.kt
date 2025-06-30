// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/AttachPhotoCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources

/**
 * Validates the [AttachPhotoCommand] to ensure that all required properties meet the specified rules.
 *
 * This validator enforces the following rules:
 * - [locationId] must be greater than 0
 * - [photoPath] must be non-empty and a valid file path
 *
 * Use this validator to ensure that an [AttachPhotoCommand] instance is properly configured before processing.
 */
class AttachPhotoCommandValidator : IValidator<AttachPhotoCommand> {

    /**
     * Validates the properties of the [AttachPhotoCommand] to ensure they meet the required criteria.
     *
     * This validator enforces the following rules:
     * - [locationId] must be greater than 0
     * - [photoPath] must not be empty and must represent a valid file path
     */
    override fun validate(command: AttachPhotoCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate LocationId
        if (command.locationId <= 0) {
            errors.add(AppResources.locationValidationErrorLocationIdRequired)
        }

        // Validate PhotoPath
        if (command.photoPath.isBlank()) {
            errors.add(AppResources.locationValidationErrorPhotoPathRequired)
        } else if (!isValidPath(command.photoPath)) {
            errors.add(AppResources.locationValidationErrorPhotoPathInvalid)
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
     * A valid path is non-empty, non-whitespace, and does not contain any invalid path
     * characters such as '|', '<', '>', '"', '?', or '*'.
     *
     * @param path The path to validate.
     * @return true if the path is valid; otherwise, false.
     */
    private fun isValidPath(path: String): Boolean {
        if (path.isBlank()) return false

        val invalidChars = setOf('<', '>', '"', '|', '?', '*')

        return !path.any { char ->
            char.code < 32 || invalidChars.contains(char)
        }
    }
}