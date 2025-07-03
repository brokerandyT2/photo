// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/RestoreLocationCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult

/**
 * Provides validation logic for the [RestoreLocationCommand].
 *
 * This validator ensures that the [locationId] property of the [RestoreLocationCommand]
 * meets the required constraints, such as being greater than 0.
 */
class RestoreLocationCommandValidator : IValidator<RestoreLocationCommand> {

    /**
     * Validates the properties of a command to restore a location.
     *
     * This validator ensures that the [locationId] property of the command meets the
     * required conditions. Specifically, it checks that the [locationId] is greater than 0.
     */
    override fun validate(command: RestoreLocationCommand): ValidationResult {
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