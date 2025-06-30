// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/validators/DeleteLocationCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.validators

import com.x3squaredcircles.pixmap.shared.application.commands.DeleteLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult

/**
 * Validator for DeleteLocationCommand
 */
class DeleteLocationCommandValidator : IValidator<DeleteLocationCommand> {

    override fun validate(command: DeleteLocationCommand): ValidationResult {
        val errors = mutableListOf<String>()

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