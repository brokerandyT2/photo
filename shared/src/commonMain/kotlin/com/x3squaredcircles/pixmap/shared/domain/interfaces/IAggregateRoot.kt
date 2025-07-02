//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/interfaces/IAggregateRoot.kt

package com.x3squaredcircles.pixmap.shared.domain.interfaces

/**
 * Marker interface for aggregate roots
 */
interface IAggregateRoot : IEntity {
    val domainEvents: List<IDomainEvent>
    fun addDomainEvent(eventItem: IDomainEvent)
    fun removeDomainEvent(eventItem: IDomainEvent)
    fun clearDomainEvents()
}