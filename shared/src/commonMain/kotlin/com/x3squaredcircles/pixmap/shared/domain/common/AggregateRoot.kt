//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/common/AggregateRoot.kt

package com.x3squaredcircles.pixmap.shared.domain.common

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IAggregateRoot
import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEvent

/**
 * Represents the base class for aggregate roots in a domain-driven design (DDD) context.
 * An aggregate root is the entry point to an aggregate, which is a cluster of domain objects that are
 * treated as a single unit. This class provides functionality for managing domain events associated with the aggregate
 * root.
 */
abstract class AggregateRoot : Entity(), IAggregateRoot {
    private val _domainEvents = mutableListOf<IDomainEvent>()

    /**
     * Gets the collection of domain events associated with the current entity.
     * Domain events represent significant occurrences within the entity that may trigger
     * side effects or be handled by external systems. This property provides a read-only view of the events for
     * external consumers.
     */
    override val domainEvents: List<IDomainEvent>
        get() = _domainEvents.toList()

    /**
     * Adds a domain event to the collection of events associated with the entity.
     * @param eventItem The domain event to add.
     */
    override fun addDomainEvent(eventItem: IDomainEvent) {
        _domainEvents.add(eventItem)
    }

    /**
     * Removes a specified domain event from the collection of domain events.
     * @param eventItem The domain event to remove.
     */
    override fun removeDomainEvent(eventItem: IDomainEvent) {
        _domainEvents.remove(eventItem)
    }

    /**
     * Clears all domain events associated with the current entity.
     * This method removes all events from the internal collection of domain events.
     * It is typically used after the events have been processed or dispatched.
     */
    override fun clearDomainEvents() {
        _domainEvents.clear()
    }
}