// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/validation/CreateTipCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.validation

import com.x3squaredcircles.pixmap.shared.application.commands.CreateTipCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources

/**
 * Provides validation rules for the CreateTipCommand object.
 *
 * This validator ensures that all required properties of the CreateTipCommand
 * object meet the specified constraints. It validates the following:
 * - TipTypeId must be greater than 0
 * - Title is required and must not exceed 100 characters
 * - Content is required and must not exceed 1000 characters
 * - Fstop, ShutterSpeed, and Iso must not exceed 20 characters each
 */
class CreateTipCommandValidator : IValidator<CreateTipCommand> {

    /**
     * Validates the properties of a CreateTipCommand object.
     *
     * This validator ensures that all required fields in the CreateTipCommand object
     * are properly populated and meet the specified constraints. Validation rules include checks for non-empty
     * fields, maximum lengths, and specific value ranges where applicable.
     */
    override fun validate(command: CreateTipCommand): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate TipTypeId
        if (command.tipTypeId <= 0) {
            errors.add(AppResources.tipTypeValidationErrorIdRequired)
        }

        // Validate Title
        if (command.title.isBlank()) {
            errors.add(AppResources.tipValidationErrorTitleRequired)
        } else if (command.title.length > 100) {
            errors.add(AppResources.tipValidationErrorTitleMaxLength)
        }

        // Validate Content
        if (command.content.isBlank()) {
            errors.add(AppResources.tipValidationErrorContentRequired)
        } else if (command.content.length > 1000) {
            errors.add(AppResources.tipValidationErrorContentMaxLength)
        }

        // Validate Fstop (optional field)
        command.fstop?.let { fstop ->
            if (fstop.length > 20) {
                errors.add(AppResources.tipValidationErrorFstopMaxLength)
            }
        }

        // Validate ShutterSpeed (optional field)
        command.shutterSpeed?.let { shutterSpeed ->
            if (shutterSpeed.length > 20) {
                errors.add(AppResources.tipValidationErrorShutterSpeedMaxLength)
            }
        }

        // Validate Iso (optional field)
        command.iso?.let { iso ->
            if (iso.length > 20) {
                errors.add(AppResources.tipValidationErrorIsoMaxLength)
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}