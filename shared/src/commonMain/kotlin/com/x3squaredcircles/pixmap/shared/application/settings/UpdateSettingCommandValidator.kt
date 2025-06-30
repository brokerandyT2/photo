// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/UpdateSettingCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources

/**
 * Provides validation rules for the [UpdateSettingCommand].
 *
 * This validator ensures that the [UpdateSettingCommand.key] is not empty and does
 * not exceed 50 characters, and that the [UpdateSettingCommand.value] is not null and does not exceed
 * 500 characters. Validation errors will include descriptive messages for any violations.
 */
class UpdateSettingCommandValidator : IValidator<UpdateSettingCommand> {

    /**
     * Validates the properties of an [UpdateSettingCommand] to ensure they meet the required constraints.
     *
     * This validator enforces the following rules:
     * - The [key] property must not be empty and must not exceed 50 characters
     * - The [value] property must not be null and must not exceed 500 characters
     */
    override fun validate(command: UpdateSettingCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate Key
        if (command.key.isBlank()) {
            errors.add(AppResources.settingValidationErrorKeyRequired)
        } else if (command.key.length > 50) {
            errors.add(AppResources.settingValidationErrorKeyMaxLength)
        }

        // Validate Value
        if (command.value.length > 500) {
            errors.add(AppResources.settingValidationErrorValueMaxLength)
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}