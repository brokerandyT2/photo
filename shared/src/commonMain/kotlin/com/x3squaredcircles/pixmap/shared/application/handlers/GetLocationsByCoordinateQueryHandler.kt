package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IQueryHandler
import com.x3squaredcircles.pixmap.shared.application.queries.GetLocationsByCoordinateQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Handler for GetLocationsByCoordinateQuery
 */
class GetLocationsByCoordinateQueryHandler(
    private val locationRepository: ILocationRepository
) : IQueryHandler<GetLocationsByCoordinateQuery, List<LocationDto>> {

    override suspend fun handle(query: GetLocationsByCoordinateQuery): List<LocationDto> {
        val coordinate = Coordinate.createValidated(query.latitude, query.longitude)
        val locations = locationRepository.getByCoordinate(coordinate, query.radiusKm)
        return locations.map { it.toDto() }
    }

    private fun Location.toDto(): LocationDto {
        return LocationDto(
            id = id,
            title = title,
            description = description,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            city = address.city,
            state = address.state,
            photoPath = photoPath,
            isDeleted = isDeleted,
            timestamp = timestamp
        )
    }
}