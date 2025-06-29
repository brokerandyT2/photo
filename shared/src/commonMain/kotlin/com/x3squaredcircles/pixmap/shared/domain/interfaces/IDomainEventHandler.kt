package com.x3squaredcircles.pixmap.shared.domain.interfaces

/**
 * Interface for handling domain events
 */
interface IDomainEventHandler<T : IDomainEvent> {
    suspend fun handle(event: T)
}