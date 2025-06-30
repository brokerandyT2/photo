// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetNearbyLocationsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationListDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.mappers.LocationMapper
import kotlinx.coroutines.cancellation.CancellationException

/**
 * Represents a query to retrieve a list of locations within a specified distance from a given geographic coordinate.
 *
 * This query is used to find nearby locations based on latitude, longitude, and a distance
 * radius in kilometers. The result contains a list of locations that match the criteria.
 */
data class GetNearbyLocationsQuery(
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double = 10.0
)

/**
 * Handles queries to retrieve a list of nearby locations based on geographic coordinates and a specified distance.
 *
 * This handler processes a [GetNearbyLocationsQuery] to fetch locations within a
 * given radius from the specified latitude and longitude. The results are returned as a list of
 * [LocationListDto] objects.
 */
class GetNearbyLocationsQueryHandler(
    private val unitOfWork: IUnitOfWork,
    private val mapper: LocationMapper
) : IRequestHandler<GetNearbyLocationsQuery, List<LocationListDto>> {

    /**
     * Handles the query to retrieve a list of nearby locations based on the specified geographic coordinates and distance.
     *
     * @param request The query containing the latitude, longitude, and distance in kilometers to search for nearby locations.
     * @return A [Result] containing a list of [LocationListDto] objects representing the nearby
     * locations. If no locations are found, the result contains an empty list. If an error occurs, the result
     * contains an error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: GetNearbyLocationsQuery): Result<List<LocationListDto>> {
        return try {
            val result = unitOfWork.locations.getNearbyAsync(
                latitude = request.latitude,
                longitude = request.longitude,
                distanceKm = request.distanceKm
            )

            if (!result.isSuccess) {
                return Result.failure(result.errorMessage ?: "Failed to retrieve nearby locations")
            }

            val locations = result.data
            if (locations == null) {
                return Result.success(emptyList())
            }

            val locationDtos = mapper.toLocationListDtoList(locations)
            Result.success(locationDtos)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Result.failure(AppResources.getLocationErrorNearbyRetrieveFailed(ex.message ?: "Unknown error"))
        }
    }
}