//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/common/DomainEvent.kt

package com.x3squaredcircles.pixmap.shared.domain.common

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEvent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Base class for all domain events
 */
@Serializable
abstract class DomainEvent : IDomainEvent {

    /**
     * The UTC date and time when the event occurred
     */
    override val dateOccurred: Instant = Clock.System.now()
}