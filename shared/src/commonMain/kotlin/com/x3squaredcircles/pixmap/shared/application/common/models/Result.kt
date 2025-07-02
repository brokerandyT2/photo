//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/common/models/Result.kt
package com.x3squaredcircles.pixmap.shared.application.common.models

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IResult
import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IResultWithData
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException

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

open class Result<T>(
    override val isSuccess: Boolean,
    override val data: T?,
    override val errorMessage: String?,
    override val errors: List<Error>
) : IResultWithData<T> {

    companion object {
        fun <T> success(data: T): Result<T> = Result(true, data, null, emptyList())

        fun success(): Result<Unit> = Result(true, Unit, null, emptyList())

        fun <T> failure(errorMessage: String): Result<T> = Result(false, null, errorMessage, emptyList())

        fun <T> failure(errors: List<Error>): Result<T> = Result(false, null, null, errors)

        fun <T> failure(error: Error): Result<T> = Result(false, null, null, listOf(error))

        fun <T> failure(exception: LocationDomainException): Result<T> {
            val error = Error(exception.code, exception.message ?: "Domain exception occurred")
            return Result(false, null, exception.message, listOf(error))
        }
    }
}