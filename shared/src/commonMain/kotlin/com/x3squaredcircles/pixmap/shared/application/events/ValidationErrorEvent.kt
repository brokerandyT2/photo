//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/events/errors/ValidationErrorEvent.kt
package com.x3squaredcircles.pixmap.shared.application.events

import com.x3squaredcircles.pixmap.shared.application.interfaces.INotification

/**
 * Event published when validation fails
 */
data class ValidationErrorEvent(
    val entityType: String,
    val validationErrors: Map<String, List<String>>,
    val handlerName: String
) : INotification