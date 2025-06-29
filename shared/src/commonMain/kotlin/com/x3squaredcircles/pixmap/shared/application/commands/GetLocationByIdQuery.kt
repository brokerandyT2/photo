package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IQuery

/**
 * Query to get a location by its ID
 */
data class GetLocationByIdQuery(
    val id: Int
) : IQuery<LocationDto?>