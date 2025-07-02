// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetLocationByIdQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.mappers.LocationMapper
import kotlinx.coroutines.cancellation.CancellationException

/**
 * Handles queries to retrieve a location by its unique identifier.
 *
 * This handler processes a [GetLocationByIdQuery] and returns a [Result] containing
 * a [LocationDto] if the location is found. If the location is not found, a failure result is returned.
 */
class GetLocationByIdQueryHandler(
    private val unitOfWork: IUnitOfWork,
    private val mapper: LocationMapper,
    private val mediator: IMediator
) : IRequestHandler<GetLocationByIdQuery, LocationDto?> {

    /**
     * Handles the retrieval of a location by its unique identifier.
     *
     * @param request The query containing the unique identifier of the location to retrieve.
     * @return A [Result] containing a [LocationDto] if the location is found; otherwise, a
     * failure result with an appropriate error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: GetLocationByIdQuery): Result<LocationDto?> {
        return try {
            val locationResult = unitOfWork.locations.getByIdAsync(request.id)

            if (!locationResult.isSuccess || locationResult.data == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        message = AppResources.getLocationErrorNotFoundById(request.id),
                        errorType = LocationErrorType.DatabaseError,
                        details = AppResources.locationErrorNotFound
                    )
                )
                return Result.failure(AppResources.locationErrorNotFound)
            }

            val dto = mapper.toDto(locationResult.data)
            Result.success(dto)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    message = AppResources.getLocationErrorNotFoundById(request.id),
                    errorType = LocationErrorType.DatabaseError,
                    details = ex.message ?: "Unknown error"
                )
            )
            Result.failure(AppResources.getLocationErrorRetrieveFailed(ex.message ?: "Unknown error"))
        }
    }
}