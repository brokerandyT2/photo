package com.x3squaredcircles.pixmap.shared.domain.interfaces

/**
 * Interface for dispatching domain events to their handlers
 */
interface IDomainEventDispatcher {
    suspend fun dispatch(event: IDomainEvent)
    suspend fun dispatchAll(events: List<IDomainEvent>)
}