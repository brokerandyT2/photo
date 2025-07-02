// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/common/interfaces/IResult.kt
package com.x3squaredcircles.pixmap.shared.application.common.interfaces

import com.x3squaredcircles.pixmap.shared.application.common.models.Error

/**
 * Base interface for operation results
 */
interface IResult {
    /**
     * Indicates whether the operation was successful
     */
    val isSuccess: Boolean

    /**
     * Error message if the operation failed
     */
    val errorMessage: String?

    /**
     * Collection of detailed errors
     */
    val errors: List<Error>
}

/**
 * Generic result interface with data
 */
interface IResultWithData<T> : IResult {
    /**
     * The data returned by the operation
     */
    val data: T?
}