// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/events/InMemoryEventBus.kt

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IEventBus
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IEventHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEvent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryEventBus(
    private val logger: ILoggingService
) : IEventBus {

    private val eventHandlers = mutableMapOf<String, MutableList<Any>>()
    private val mutex = Mutex()

    override suspend fun publishAsync(domainEvent: IDomainEvent) {
        val eventType = domainEvent::class.simpleName ?: "Unknown"
        logger.info("Publishing domain event $eventType")

        mutex.withLock {
            val handlers = eventHandlers[eventType]?.toList() ?: emptyList()

            for (handler in handlers) {
                try {
                    // Use reflection to call handleAsync method
                    val handleMethod = handler::class.members.find {
                        it.name == "handleAsync" && it.parameters.size == 2
                    }

                    if (handleMethod != null) {
                        handleMethod.call(handler, domainEvent)
                        logger.debug("Domain event $eventType handled by ${handler::class.simpleName}")
                    }
                } catch (ex: Exception) {
                    logger.error("Error handling domain event $eventType with handler ${handler::class.simpleName}", ex)
                }
            }
        }
    }

    override suspend fun publishAllAsync(domainEvents: List<IDomainEvent>) {
        for (domainEvent in domainEvents) {
            publishAsync(domainEvent)
        }
    }

    override suspend fun <TEvent : Any> publishAsync(event: TEvent) {
        val eventType = event::class.simpleName ?: "Unknown"
        logger.info("Publishing event $eventType")

        mutex.withLock {
            val handlers = eventHandlers[eventType]?.toList() ?: emptyList()

            for (handler in handlers) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    if (handler is IEventHandler<*>) {
                        (handler as IEventHandler<TEvent>).handleAsync(event)
                        logger.debug("Event $eventType handled by ${handler::class.simpleName}")
                    }
                } catch (ex: Exception) {
                    logger.error("Error handling event $eventType with handler ${handler::class.simpleName}", ex)
                }
            }
        }
    }

    override suspend fun <TEvent : Any> subscribeAsync(handler: IEventHandler<TEvent>) {
        val eventType = getEventTypeFromHandler(handler)

        mutex.withLock {
            if (!eventHandlers.containsKey(eventType)) {
                eventHandlers[eventType] = mutableListOf()
            }

            eventHandlers[eventType]!!.add(handler)
            logger.info("Subscribed handler ${handler::class.simpleName} to event $eventType")
        }
    }

    override suspend fun <TEvent : Any> unsubscribeAsync(handler: IEventHandler<TEvent>) {
        val eventType = getEventTypeFromHandler(handler)

        mutex.withLock {
            val handlers = eventHandlers[eventType]
            if (handlers != null) {
                handlers.remove(handler)

                if (handlers.isEmpty()) {
                    eventHandlers.remove(eventType)
                }

                logger.info("Unsubscribed handler ${handler::class.simpleName} from event $eventType")
            }
        }
    }

    // Synchronous methods for backward compatibility
    suspend fun subscribe(eventType: String, handler: Any) {
        mutex.withLock {
            if (!eventHandlers.containsKey(eventType)) {
                eventHandlers[eventType] = mutableListOf()
            }

            eventHandlers[eventType]!!.add(handler)
            logger.info("Subscribed handler ${handler::class.simpleName} to event $eventType")
        }
    }

    suspend fun unsubscribe(eventType: String, handler: Any) {
        mutex.withLock {
            val handlers = eventHandlers[eventType]
            if (handlers != null) {
                handlers.remove(handler)

                if (handlers.isEmpty()) {
                    eventHandlers.remove(eventType)
                }

                logger.info("Unsubscribed handler ${handler::class.simpleName} from event $eventType")
            }
        }
    }

    private fun <TEvent : Any> getEventTypeFromHandler(handler: IEventHandler<TEvent>): String {
        // Extract event type from generic handler interface
        // This is a simplified approach - in production, you might want more sophisticated type extraction
        return handler::class.simpleName?.replace("Handler", "") ?: "Unknown"
    }
}