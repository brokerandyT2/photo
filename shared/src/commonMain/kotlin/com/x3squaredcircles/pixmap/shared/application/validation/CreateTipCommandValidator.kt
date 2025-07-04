// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/validation/CreateTipCommandValidator.kt
package com.x3squaredcircles.pixmap.shared.application.validation

import com.x3squaredcircles.pixmap.shared.application.commands.CreateTipCommand
import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator


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
            errors.add("TipTypeId must be greater than 0")
        }

        // Validate Title
        if (command.title.isBlank()) {
            errors.add("Title is required")
        } else if (command.title.length > 100) {
            errors.add("Title must not exceed 100 characters")
        }

        // Validate Content
        if (command.content.isBlank()) {
            errors.add("Content is required")
        } else if (command.content.length > 1000) {
            errors.add("Content must not exceed 1000 characters")
        }

        // Validate Fstop (optional field)
        command.fstop?.let { fstop ->
            if (fstop.length > 20) {
                errors.add("Fstop must not exceed 20 characters")
            }
        }

        // Validate ShutterSpeed (optional field)
        command.shutterSpeed?.let { shutterSpeed ->
            if (shutterSpeed.length > 20) {
                errors.add("ShutterSpeed must not exceed 20 characters")
            }
        }

        // Validate Iso (optional field)
        command.iso?.let { iso ->
            if (iso.length > 20) {
                errors.add("Iso must not exceed 20 characters")
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}