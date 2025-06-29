package com.x3squaredcircles.pixmap.shared.domain.services

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEvent
import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEventDispatcher
import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEventHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Default implementation of domain event dispatcher
 */
class DomainEventDispatcher : IDomainEventDispatcher, KoinComponent {

    override suspend fun dispatch(event: IDomainEvent) {
        // In a more complex implementation, this would use reflection or a registry
        // to find the appropriate handler for each event type
        when (event::class.simpleName) {
            "LocationSavedEvent" -> {
                // Handle location saved event
            }
            "LocationDeletedEvent" -> {
                // Handle location deleted event
            }
            "PhotoAttachedEvent" -> {
                // Handle photo attached event
            }
            "WeatherUpdatedEvent" -> {
                // Handle weather updated event
            }
        }
    }

    override suspend fun dispatchAll(events: List<IDomainEvent>) {
        events.forEach { event ->
            dispatch(event)
        }
    }
}