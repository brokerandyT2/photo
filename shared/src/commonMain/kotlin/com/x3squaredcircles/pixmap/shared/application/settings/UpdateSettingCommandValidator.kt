// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/UpdateSettingCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator


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
            errors.add("Key required")
        } else if (command.key.length > 50) {
            errors.add("Key must not exceed 50 characters")
        }

        // Validate Value
        if (command.value.length > 500) {
            errors.add("Value must not exceed 500 characters")
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}