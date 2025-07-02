//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/common/Result.kt

package com.x3squaredcircles.pixmap.shared.domain.common

import kotlinx.serialization.Serializable

/**
 * Represents the result of an operation that can either succeed or fail
 */
@Serializable
sealed class Result<out T> {

    abstract val isSuccess: Boolean
    abstract val isFailure: Boolean

    /**
     * Successful result containing data
     */
    @Serializable
    data class Success<T>(val data: T) : Result<T>() {
        override val isSuccess: Boolean = true
        override val isFailure: Boolean = false
    }

    /**
     * Failed result containing error information
     */
    @Serializable
    data class Failure<T>(val error: Error) : Result<T>() {
        override val isSuccess: Boolean = false
        override val isFailure: Boolean = true
    }

    companion object {
        /**
         * Creates a successful result with data
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * Creates a failure result with an error message
         */
        fun <T> failure(message: String, code: String = "UNKNOWN"): Result<T> =
            Failure(Error(code, message))

        /**
         * Creates a failure result with an error
         */
        fun <T> failure(error: Error): Result<T> = Failure(error)

        /**
         * Creates a failure result from an exception
         */
        fun <T> failure(exception: Exception, code: String = "EXCEPTION"): Result<T> =
            Failure(Error(code, exception.message ?: "An error occurred"))
    }

    /**
     * Returns the data if successful, or null if failed
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    /**
     * Returns the data if successful, or throws the error if failed
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw Exception("${error.code}: ${error.message}")
    }

    /**
     * Returns the data if successful, or the default value if failed
     */
    fun getOrDefault(defaultValue: T): T = when (this) {
        is Success -> data
        is Failure -> defaultValue
    }

    /**
     * Returns the error if failed, or null if successful
     */
    fun errorOrNull(): Error? = when (this) {
        is Success -> null
        is Failure -> error
    }

    /**
     * Executes the given function if the result is successful
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    /**
     * Executes the given function if the result is a failure
     */
    inline fun onFailure(action: (Error) -> Unit): Result<T> {
        if (this is Failure) {
            action(error)
        }
        return this
    }

    /**
     * Transforms the data if the result is successful
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> try {
            success(transform(data))
        } catch (e: Exception) {
            failure(e)
        }
        is Failure -> Failure(error)
    }

    /**
     * Transforms the data if the result is successful, allowing the transform to return a Result
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> try {
            transform(data)
        } catch (e: Exception) {
            failure(e)
        }
        is Failure -> Failure(error)
    }

    /**
     * Recovers from a failure by providing a default value
     */
    inline fun recover(recovery: (Error) -> T): Result<T> = when (this) {
        is Success -> this
        is Failure -> try {
            success(recovery(error))
        } catch (e: Exception) {
            failure(e)
        }
    }

    /**
     * Recovers from a failure by providing a Result
     */
    inline fun recoverWith(recovery: (Error) -> Result<T>): Result<T> = when (this) {
        is Success -> this
        is Failure -> try {
            recovery(error)
        } catch (e: Exception) {
            failure(e)
        }
    }

    /**
     * Folds the result into a single value
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (Error) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }
}

/**
 * Represents an error with a code and message
 */
@Serializable
data class Error(
    val code: String,
    val message: String
) {
    companion object {
        fun validation(message: String) = Error("VALIDATION_ERROR", message)
        fun notFound(message: String) = Error("NOT_FOUND", message)
        fun database(message: String) = Error("DATABASE_ERROR", message)
        fun network(message: String) = Error("NETWORK_ERROR", message)
        fun unauthorized(message: String) = Error("UNAUTHORIZED", message)
        fun forbidden(message: String) = Error("FORBIDDEN", message)
        fun conflict(message: String) = Error("CONFLICT", message)
        fun internal(message: String) = Error("INTERNAL_ERROR", message)
    }
}

/**
 * Extension function to convert nullable values to Result
 */
fun <T : Any> T?.toResult(errorMessage: String = "Value is null"): Result<T> {
    return this?.let { Result.success(it) } ?: Result.failure(errorMessage)
}

/**
 * Extension function to safely execute operations and return Result
 */
inline fun <T> resultOf(operation: () -> T): Result<T> {
    return try {
        Result.success(operation())
    } catch (e: Exception) {
        Result.failure(e)
    }
}