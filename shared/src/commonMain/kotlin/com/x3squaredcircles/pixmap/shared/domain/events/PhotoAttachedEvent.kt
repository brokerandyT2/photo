// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/events/PhotoAttachedEvent.kt
package com.x3squaredcircles.pixmap.shared.domain.events

import com.x3squaredcircles.pixmap.shared.domain.common.DomainEvent

/**
 * Domain event raised when a photo is attached to a location
 */
class PhotoAttachedEvent(
    val locationId: Int,
    val photoPath: String
) : DomainEvent()