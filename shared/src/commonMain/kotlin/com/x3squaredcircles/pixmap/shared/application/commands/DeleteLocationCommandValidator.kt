//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/DeleteLocationCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult

/**
 * Provides validation logic for the [DeleteLocationCommand].
 *
 * This validator ensures that the [DeleteLocationCommand.id] property meets the
 * required conditions before the command is processed.
 */
class DeleteLocationCommandValidator : IValidator<DeleteLocationCommand> {

    /**
     * Validates the [DeleteLocationCommand] to ensure it meets the required criteria.
     *
     * This validator enforces that the location ID is greater than 0.
     */
    override fun validate(command: DeleteLocationCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate Id
        if (command.id <= 0) {
            errors.add("Location ID must be greater than 0")
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}