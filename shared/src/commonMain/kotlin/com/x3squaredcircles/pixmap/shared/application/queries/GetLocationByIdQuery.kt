// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetLocationByIdQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest

/**
 * Query to get a location by ID
 */
data class GetLocationByIdQuery(
    val id: Int
) : IRequest<LocationDto?>