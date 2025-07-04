// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/events/ErrorOccurredEvent.kt
package com.x3squaredcircles.pixmap.shared.application.events


import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents an event that occurs when an error is encountered, providing details about the error.
 *
 * This class encapsulates information about an error, including a descriptive message, the
 * source of the error, and the timestamp when the error occurred. It is typically used to log or propagate error
 * details in an application.
 */
class ErrorOccurredEvent(
    val message: String,
    val source: String
) {
    val timestamp: Instant = Clock.System.now()

    init {
        require(message.isNotBlank()) { "Validation can not be null" }
        require(source.isNotBlank()){ "Validation can not be null" }
    }
}