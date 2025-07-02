//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/IValidator.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces

import com.x3squaredcircles.pixmap.shared.application.common.models.ValidationResult

/**
 * Interface for validators
 */
interface IValidator<T> {
    fun validate(instance: T): ValidationResult
}