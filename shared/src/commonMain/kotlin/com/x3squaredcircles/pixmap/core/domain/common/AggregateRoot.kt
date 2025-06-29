package com.x3squaredcircles.pixmap.shared.domain.common

import com.x3squaredcircles.pixmap.shared.domain.interfaces.IAggregateRoot
import com.x3squaredcircles.pixmap.shared.domain.interfaces.IDomainEvent

/**
 * Represents the base class for aggregate roots in a domain-driven design (DDD) context.
 */
abstract class AggregateRoot : Entity(), IAggregateRoot {
    private val _domainEvents = mutableListOf<IDomainEvent>()

    override val domainEvents: List<IDomainEvent>
        get() = _domainEvents.toList()

    override fun addDomainEvent(eventItem: IDomainEvent) {
        _domainEvents.add(eventItem)
    }

    override fun removeDomainEvent(eventItem: IDomainEvent) {
        _domainEvents.remove(eventItem)
    }

    override fun clearDomainEvents() {
        _domainEvents.clear()
    }
}