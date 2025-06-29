package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IQuery

/**
 * Query to get locations within a radius of a coordinate
 */
data class GetLocationsByCoordinateQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double
) : IQuery<List<LocationDto>>