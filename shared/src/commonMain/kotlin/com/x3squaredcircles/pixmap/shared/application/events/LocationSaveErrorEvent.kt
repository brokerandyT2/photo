//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/events/LocationSaveErrorEvent.kt
package com.x3squaredcircles.pixmap.shared.application.events

import com.x3squaredcircles.pixmap.shared.application.interfaces.INotification

/**
 * Error types for location operations
 */
enum class LocationErrorType {
    DatabaseError,
    ValidationError,
    NetworkError
}

/**
 * Event published when location save operation fails
 */
data class LocationSaveErrorEvent(
    val locationTitle: String,
    val errorType: LocationErrorType,
    val errorMessage: String
) : INotification