package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IQueryHandler
import com.x3squaredcircles.pixmap.shared.application.queries.GetLocationByIdQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository

/**
 * Handler for GetLocationByIdQuery
 */
class GetLocationByIdQueryHandler(
    private val locationRepository: ILocationRepository
) : IQueryHandler<GetLocationByIdQuery, LocationDto?> {

    override suspend fun handle(query: GetLocationByIdQuery): LocationDto? {
        val location = locationRepository.getById(query.id)
        return location?.toDto()
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