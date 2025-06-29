// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IEventBus.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEvent

/**
 * Event bus interface for publishing and handling domain events
 */
interface IEventBus {
    suspend fun publishAsync(domainEvent: IDomainEvent)
    suspend fun publishAllAsync(domainEvents: List<IDomainEvent>)
    suspend fun <TEvent : Any> publishAsync(event: TEvent)
    suspend fun <TEvent : Any> subscribeAsync(handler: IEventHandler<TEvent>)
    suspend fun <TEvent : Any> unsubscribeAsync(handler: IEventHandler<TEvent>)
}

/**
 * Handler interface for events
 */
interface IEventHandler<in TEvent : Any> {
    suspend fun handleAsync(event: TEvent)
}