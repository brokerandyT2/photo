// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/DeleteSettingCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources

/**
 * Provides validation logic for the [DeleteSettingCommand].
 *
 * This validator ensures that the [DeleteSettingCommand.key] property is not empty.
 */
class DeleteSettingCommandValidator : IValidator<DeleteSettingCommand> {

    /**
     * Validates the [DeleteSettingCommand] to ensure it meets the required criteria.
     *
     * This validator enforces that the [key] property of the [DeleteSettingCommand]
     * is not empty. If the validation fails, an appropriate error message is provided.
     */
    override fun validate(command: DeleteSettingCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate Key
        if (command.key.isBlank()) {
            errors.add(AppResources.settingValidationErrorKeyRequired)
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}