// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetAllLocationsQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationListDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.mappers.LocationMapper
import com.x3squaredcircles.pixmap.shared.application.mappers.toLocationListDtoPagedList
import kotlinx.coroutines.CancellationException

/**
 * Represents a query to retrieve a paginated list of locations, optionally filtered by a search term and including
 * deleted entries.
 *
 * This query is used to request a paginated list of locations from the data source. The
 * results can be filtered by a search term and can optionally include deleted locations.
 */

/**
 * Handles the query to retrieve a paginated list of locations based on the specified criteria.
 *
 * This handler processes the [GetLocationsQuery] to retrieve location data,
 * optionally including deleted locations, filtering by a search term, and returning the results in a paginated
 * format.
 */
class GetLocationsQueryHandler(
    private val unitOfWork: IUnitOfWork,
    private val mapper: LocationMapper
) : IRequestHandler<GetLocationsQuery, PagedList<LocationListDto>> {

    /**
     * Handles the retrieval of a paginated list of locations based on the specified query parameters.
     *
     * The method retrieves locations from the data source, optionally filters them based on
     * a search term, and maps them to DTOs.
     *
     * @param request The query containing pagination parameters, search term, and inclusion settings.
     * @return A [PagedList] of [LocationListDto] if successful; otherwise, throws an exception.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: GetLocationsQuery): PagedList<LocationListDto> {
        return try {
            // Push all filtering and pagination to database level
            val pagedLocationsResult = unitOfWork.locations.getPagedAsync(
                pageNumber = request.pageNumber,
                pageSize = request.pageSize,
                searchTerm = request.searchTerm,
                includeDeleted = request.includeDeleted
            )

            if (!pagedLocationsResult.isSuccess || pagedLocationsResult.data == null) {
                throw RuntimeException(
                    pagedLocationsResult.errorMessage ?: "Failed to retrieve locations"
                )
            }

            // Use mapper for efficient bulk mapping
            val pagedLocations = pagedLocationsResult.data!!
            val locationDtos = pagedLocations.toLocationListDtoPagedList()

            locationDtos
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            throw RuntimeException("Failed to retrieve locations: ${ex.message ?: "Unknown error"}")
        }
    }
}