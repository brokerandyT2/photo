// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/events/LocationSavedEvent.kt
package com.x3squaredcircles.pixmap.shared.domain.events

import com.x3squaredcircles.pixmap.shared.domain.common.DomainEvent
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Domain event raised when a location is saved
 */
class LocationSavedEvent(
    val location: Location
) : DomainEvent()