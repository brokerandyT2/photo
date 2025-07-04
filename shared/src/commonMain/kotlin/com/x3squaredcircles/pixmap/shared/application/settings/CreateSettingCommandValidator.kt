// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/CreateSettingCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator


/**
 * Provides validation rules for the [CreateSettingCommand] class.
 *
 * This validator ensures that the [CreateSettingCommand.key] property is not empty
 * and does not exceed 50 characters, the [CreateSettingCommand.value] property is not null and does
 * not exceed 500 characters, and the [CreateSettingCommand.description] property does not exceed 200
 * characters.
 */
class CreateSettingCommandValidator : IValidator<CreateSettingCommand> {

    /**
     * Validates the properties of a [CreateSettingCommand] object to ensure they meet the required constraints.
     *
     * This validator enforces the following rules:
     * - The [key] property must not be empty and must not exceed 50 characters
     * - The [value] property must not be null and must not exceed 500 characters
     * - The [description] property, if provided, must not exceed 200 characters
     */
    override fun validate(command: CreateSettingCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate Key
        if (command.key.isBlank()) {
            errors.add("Key is blank")
        } else if (command.key.length > 50) {
            errors.add("Key too long")
        }

        // Validate Value
        if (command.value.length > 500) {
            errors.add("Value too long")
        }

        // Validate Description
        if (command.description.length > 200) {
            errors.add("Description too long")
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}