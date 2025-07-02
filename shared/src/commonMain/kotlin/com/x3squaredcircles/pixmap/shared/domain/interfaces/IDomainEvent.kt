//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/interfaces/IDomainEvent.kt

package com.x3squaredcircles.pixmap.shared.domain.interfaces

import kotlinx.datetime.Instant

/**
 * Marker interface for domain events
 */
interface IDomainEvent {
    val dateOccurred: Instant
}