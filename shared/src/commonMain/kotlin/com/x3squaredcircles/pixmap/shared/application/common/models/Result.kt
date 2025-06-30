// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/common/models/Result.kt
package com.x3squaredcircles.pixmap.shared.application.common.models

/**
 * Represents the result of an operation that can either succeed or fail
 */
sealed class Result<out T> {

    abstract val isSuccess: Boolean
    abstract val isFailure: Boolean

    /**
     * Successful result containing data
     */
    data class Success<T>(val data: T) : Result<T>() {
        override val isSuccess: Boolean = true
        override val isFailure: Boolean = false
    }

    /**
     * Failed result containing error information
     */
    data class Failure(
        val errorMessage: String,
        val errors: List<Error> = emptyList()
    ) : Result<Nothing>() {
        override val isSuccess: Boolean = false
        override val isFailure: Boolean = true
    }

    /**
     * Gets the data if successful, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    /**
     * Gets the data if successful, throws exception otherwise
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw RuntimeException(errorMessage)
    }

    /**
     * Gets the error message if failed, null otherwise
     */
    fun getErrorOrNull(): String? = when (this) {
        is Success -> null
        is Failure -> errorMessage
    }

    companion object {
        /**
         * Creates a successful result
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * Creates a failure result with error message
         */
        fun <T> failure(errorMessage: String): Result<T> = Failure(errorMessage)

        /**
         * Creates a failure result with multiple errors
         */
        fun <T> failure(errors: List<Error>): Result<T> =
            Failure(errors.firstOrNull()?.message ?: "Unknown error", errors)

        /**
         * Creates a failure result with single error
         */
        fun <T> failure(error: Error): Result<T> = Failure(error.message, listOf(error))
    }
}

/**
 * Extension function to map the data in a successful result
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.success(transform(data))
    is Result.Failure -> this
}

/**
 * Extension function to flat map results
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
    is Result.Success -> transform(data)
    is Result.Failure -> this
}

/**
 * Extension function to perform action on success
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

/**
 * Extension function to perform action on failure
 */
inline fun <T> Result<T>.onFailure(action: (String, List<Error>) -> Unit): Result<T> {
    if (this is Result.Failure) {
        action(errorMessage, errors)
    }
    return this
}