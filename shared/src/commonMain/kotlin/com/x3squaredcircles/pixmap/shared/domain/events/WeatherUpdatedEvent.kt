//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/events/WeatherUpdatedEvent.kt

package com.x3squaredcircles.pixmap.shared.domain.events

import com.x3squaredcircles.pixmap.shared.domain.common.DomainEvent
import kotlinx.datetime.Instant

/**
 * Domain event raised when weather data is updated
 */
class WeatherUpdatedEvent(
    val locationId: Int,
    val updateTime: Instant
) : DomainEvent() {

    init {
        require(locationId > 0) { "LocationId must be greater than zero" }
    }
}