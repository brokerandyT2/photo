// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/common/models/Result.kt
package com.x3squaredcircles.pixmap.shared.application.common.models

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IResult
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException

/**
 * Represents an error in the application
 */
data class Error(
    val code: String,
    val message: String,
    val propertyName: String? = null
) {
    companion object {
        fun validation(propertyName: String, message: String): Error =
            Error("VALIDATION_ERROR", message, propertyName)

        fun notFound(message: String): Error =
            Error("NOT_FOUND", message)

        fun database(message: String): Error =
            Error("DATABASE_ERROR", message)

        fun domain(message: String): Error =
            Error("DOMAIN_ERROR", message)
    }
}

/**
 * Implementation of operation result
 */
open class Result(
    override val isSuccess: Boolean,
    override val errorMessage: String?,
    override val errors: List<Error>
) : IResult {

    companion object {
        fun success(): Result = Result(true, null, emptyList())

        fun failure(errorMessage: String): Result =
            Result(false, errorMessage, emptyList())

        fun failure(errors: List<Error>): Result =
            Result(false, null, errors)

        fun failure(error: Error): Result =
            Result(false, null, listOf(error))
    }
}

/**
 * Generic implementation of operation result with data
 */
class Result<T>(
    isSuccess: Boolean,
    override val data: T?,
    errorMessage: String?,
    errors: List<Error>
) : Result(isSuccess, errorMessage, errors), IResult<T> {

    companion object {
        fun <T> success(data: T): Result<T> =
            Result(true, data, null, emptyList())

        fun <T> failure(errorMessage: String): Result<T> =
            Result(false, null, errorMessage, emptyList())

        fun <T> failure(errors: List<Error>): Result<T> =
            Result(false, null, null, errors)

        fun <T> failure(error: Error): Result<T> =
            Result(false, null, null, listOf(error))

        fun <T> failure(exception: LocationDomainException): Result<T> {
            val error = Error(exception.code, exception.message ?: "Domain exception occurred")
            return Result(false, null, exception.message, listOf(error))
        }
    }
}