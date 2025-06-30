// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/INotification.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces

/**
 * Marker interface for notifications that don't return a response
 */
interface INotification

/**
 * Handler interface for notifications
 */
interface INotificationHandler<in TNotification : INotification> {
    suspend fun handle(notification: TNotification)
}

/**
 * Event handler interface for domain events
 */
interface IEventHandler<in TEvent> where TEvent : Any {
    suspend fun handleAsync(event: TEvent)
}

/**
 * Publisher interface for events and notifications
 */
interface IEventPublisher {
    suspend fun publish(event: INotification)
    suspend fun publishAll(events: List<INotification>)
}

/**
 * Subscriber interface for event handling
 */
interface IEventSubscriber {
    suspend fun <TNotification : INotification> subscribe(
        handler: INotificationHandler<TNotification>
    )

    suspend fun <TNotification : INotification> unsubscribe(
        handler: INotificationHandler<TNotification>
    )
}

/**
 * Event dispatcher interface for managing event flow
 */
interface IEventDispatcher : IEventPublisher, IEventSubscriber {
    suspend fun start()
    suspend fun stop()
    fun isRunning(): Boolean
}

/**
 * Base domain event interface
 */
interface IDomainEvent : INotification {
    val eventId: String
    val occurredOn: kotlinx.datetime.Instant
    val eventType: String
}

/**
 * Integration event interface for cross-boundary events
 */
interface IIntegrationEvent : INotification {
    val eventId: String
    val occurredOn: kotlinx.datetime.Instant
    val eventType: String
    val correlationId: String?
}

/**
 * Event store interface for event sourcing
 */
interface IEventStore {
    suspend fun saveEvent(event: IDomainEvent)
    suspend fun saveEvents(events: List<IDomainEvent>)
    suspend fun getEvents(aggregateId: String): List<IDomainEvent>
    suspend fun getEventsAfter(timestamp: kotlinx.datetime.Instant): List<IDomainEvent>
}

/**
 * Event bus interface for distributed event handling
 */
interface IEventBus {
    suspend fun publish(event: IIntegrationEvent)
    suspend fun subscribe(eventType: String, handler: suspend (IIntegrationEvent) -> Unit)
    suspend fun unsubscribe(eventType: String)
}

/**
 * Notification registry for managing handlers
 */
interface INotificationRegistry {
    fun <TNotification : INotification> registerHandler(
        notificationType: kotlin.reflect.KClass<TNotification>,
        handler: INotificationHandler<TNotification>
    )

    fun <TNotification : INotification> unregisterHandler(
        notificationType: kotlin.reflect.KClass<TNotification>,
        handler: INotificationHandler<TNotification>
    )

    fun <TNotification : INotification> getHandlers(
        notificationType: kotlin.reflect.KClass<TNotification>
    ): List<INotificationHandler<TNotification>>

    fun clear()
}

/**
 * Extension functions for notification handling
 */
suspend fun <TNotification : INotification> IMediator.publishSafely(
    notification: TNotification
) {
    try {
        publish(notification)
    } catch (e: Exception) {
        // Log error but don't rethrow to avoid breaking the main flow
        println("Error publishing notification: ${e.message}")
    }
}

suspend fun <TNotification : INotification> IEventPublisher.publishSafely(
    event: TNotification
) {
    try {
        publish(event)
    } catch (e: Exception) {
        // Log error but don't rethrow to avoid breaking the main flow
        println("Error publishing event: ${e.message}")
    }
}

/**
 * Batch notification publishing
 */
suspend fun IEventPublisher.publishBatch(
    events: List<INotification>,
    batchSize: Int = 10
) {
    events.chunked(batchSize).forEach { batch ->
        publishAll(batch)
    }
}

/**
 * Conditional notification publishing
 */
suspend inline fun <TNotification : INotification> IMediator.publishIf(
    condition: Boolean,
    notificationFactory: () -> TNotification
) {
    if (condition) {
        publish(notificationFactory())
    }
}

/**
 * Retry mechanism for event publishing
 */
suspend fun <TNotification : INotification> IEventPublisher.publishWithRetry(
    event: TNotification,
    maxRetries: Int = 3,
    delayMs: Long = 1000
) {
    var attempt = 0
    var lastException: Exception? = null

    while (attempt <= maxRetries) {
        try {
            publish(event)
            return
        } catch (e: Exception) {
            lastException = e
            attempt++

            if (attempt <= maxRetries) {
                kotlinx.coroutines.delay(delayMs * attempt)
            }
        }
    }

    throw lastException ?: Exception("Failed to publish event after $maxRetries retries")
}