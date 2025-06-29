package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IQueryHandler
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllLocationsQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository

/**
 * Handler for GetAllLocationsQuery
 */
class GetAllLocationsQueryHandler(
    private val locationRepository: ILocationRepository
) : IQueryHandler<GetAllLocationsQuery, List<LocationDto>> {

    override suspend fun handle(query: GetAllLocationsQuery): List<LocationDto> {
        val locations = locationRepository.getAll()
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