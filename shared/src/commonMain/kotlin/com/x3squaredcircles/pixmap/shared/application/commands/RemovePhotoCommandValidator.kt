//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/RemovePhotoCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult

/**
 * Provides validation rules for the [RemovePhotoCommand].
 *
 * This validator ensures that the [RemovePhotoCommand.locationId] property meets
 * the required constraints.
 */
class RemovePhotoCommandValidator : IValidator<RemovePhotoCommand> {

    /**
     * Validates the [RemovePhotoCommand] to ensure its properties meet the required conditions.
     *
     * This validator enforces that the [locationId] property of the command must be
     * greater than 0.
     */
    override fun validate(command: RemovePhotoCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate LocationId
        if (command.locationId <= 0) {
            errors.add("Location ID must be greater than 0")
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}