package com.x3squaredcircles.pixmap.shared.common

/**
 * Represents the result of a validation operation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun success(): ValidationResult {
            return ValidationResult(isValid = true)
        }

        fun failure(errors: List<String>): ValidationResult {
            return ValidationResult(isValid = false, errors = errors)
        }

        fun failure(error: String): ValidationResult {
            return ValidationResult(isValid = false, errors = listOf(error))
        }
    }
}