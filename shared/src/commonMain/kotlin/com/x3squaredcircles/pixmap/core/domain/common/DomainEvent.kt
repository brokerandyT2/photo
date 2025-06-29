package com.x3squaredcircles.pixmap.shared.domain.common

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEvent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Base class for all domain events
 */
abstract class DomainEvent : IDomainEvent {
    override val dateOccurred: Instant = Clock.System.now()
}