package com.x3squaredcircles.pixmap.shared.common

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Extension functions for common operations
 */

// Instant extensions for UTC to device local time conversion
fun Instant.toDeviceLocalDateTime(): LocalDateTime {
    return this.toLocalDateTime(TimeZone.currentSystemDefault())
}

fun Instant.isOlderThan(duration: kotlin.time.Duration): Boolean {
    return Clock.System.now() - this > duration
}

// String extensions
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

// Double extensions for coordinate validation
fun Double.isValidLatitude(): Boolean {
    return this >= -90.0 && this <= 90.0
}

fun Double.isValidLongitude(): Boolean {
    return this >= -180.0 && this <= 180.0
}

// Collection extensions
fun <T> List<T>.toReadOnlyList(): List<T> = this.toList()

// Result extensions
inline fun <T, R> Result<T>.mapResult(transform: (T) -> R): Result<R> {
    return when (this) {
        is Result.Success -> Result.success(transform(data))
        is Result.Error -> this
    }
}

inline fun <T> Result<T>.onSuccessResult(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

inline fun <T> Result<T>.onErrorResult(action: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(exception)
    }
    return this
}